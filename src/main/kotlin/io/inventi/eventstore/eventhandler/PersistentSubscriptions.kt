package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionSettings
import io.inventi.eventstore.Subscriptions
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.dao.SubscriptionInitialPosition
import io.inventi.eventstore.eventhandler.dao.SubscriptionInitialPositionDao
import io.inventi.eventstore.eventhandler.feature.EventIdempotency
import io.inventi.eventstore.eventhandler.feature.InTransaction
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.CompletionException

@Component
@ConditionalOnSubscriptionsEnabled
@ConditionalOnBean(PersistentSubscriptionHandler::class)
class PersistentSubscriptions(
    handlers: List<PersistentSubscriptionHandler>,
    private val eventStore: EventStore,
    private val objectMapper: ObjectMapper,
    private val subscriptionInitialPositionDao: SubscriptionInitialPositionDao,
    private val subscriptionProperties: SubscriptionProperties,
    private val transactionTemplate: TransactionTemplate,
    private val idempotencyStorage: EventIdempotencyStorage,
) : Subscriptions<PersistentSubscriptionHandler>(handlers) {
    private val subscriptionsByHandler = mutableMapOf<PersistentSubscriptionHandler, PersistentSubscriptionState>().apply {
        handlers.forEach { put(it, PersistentSubscriptionState()) }
    }

    override fun startSubscription(handler: PersistentSubscriptionHandler, onFailure: (FailureType) -> Unit) {
        logger.info("Starting persistent subscription for handler: ${handler::class.simpleName}")
        val listener = PersistentSubscriptionEventListener(
                EventstoreEventListener(
                        handler,
                        subscriptionInitialPositionDao.initialPosition(handler.groupName, handler.streamName),
                        objectMapper,
                        EventIdempotency(handler, idempotencyStorage),
                        InTransaction(transactionTemplate),
                ),
                handler.state(),
                onFailure,
        )

        val subscription = eventStore.subscribeToPersistent(handler.streamName, handler.groupName, listener).exceptionally { exception ->
            logger.error("Failed to start persistent subscription for handler: ${handler::class.simpleName}", exception)
            onFailure(FailureType.EVENTSTORE_CLIENT_ERROR)
            null
        }.join()

        subscription?.let {
            handler.state().update(it)
        }
    }

    override fun ensureSubscription(handler: PersistentSubscriptionHandler) {
        val settings = PersistentSubscriptionSettings.newBuilder()
                .resolveLinkTos(true)
                .startFrom(handler.initialPosition.startSubscriptionFrom(eventStore, objectMapper))
                .minCheckPointCount(subscriptionProperties.minCheckpointCount)
                .maxRetryCount(subscriptionProperties.maxRetryCount)
                .messageTimeout(Duration.ofMillis(subscriptionProperties.messageTimeoutMillis))
                .build()

        transactionTemplate.executeWithoutResult {
            try {
                with(handler) {
                    subscriptionInitialPositionDao.createIfNotExists(
                        SubscriptionInitialPosition(
                            groupName,
                            streamName,
                            initialPosition.replayEventsUntil(eventStore, objectMapper)
                        )
                    )
                    createSubscription(settings, streamName, groupName)
                }
            } catch (e: CompletionException) {
                if ("already exists" in (e.cause?.message.orEmpty())) {
                    updateSubscription(settings, handler.streamName, handler.groupName)
                } else {
                    logger.error("Error when ensuring persistent subscription for stream for '${handler.streamName}' with groupName '${handler.groupName}'", e)
                    throw e
                }
            }
        }
    }

    override fun dropSubscription(handler: PersistentSubscriptionHandler) {
        handler.state().drop()
    }

    private fun PersistentSubscriptionHandler.state() = subscriptionsByHandler.getValue(this)

    private fun createSubscription(settings: PersistentSubscriptionSettings, streamName: String, groupName: String) {
        logger.info("Ensuring persistent subscription for stream for '$streamName' with groupName '$groupName' with $settings")
        val status = eventStore
                .createPersistentSubscription(streamName, groupName, settings)
                .join()
                .status
        if (status == PersistentSubscriptionCreateStatus.Failure) {
            throw IllegalStateException("Failed to ensure persistent subscription for stream for '$streamName' with groupName '$groupName'")
        }
    }

    private fun updateSubscription(settings: PersistentSubscriptionSettings, streamName: String, groupName: String) {
        if (subscriptionProperties.updateEnabled) {
            logger.info("Persistent subscription for stream for '$streamName' with groupName '$groupName' already exists. Updating with $settings")
            eventStore.updatePersistentSubscription(streamName, groupName, settings).join()
        } else {
            logger.info("Persistent subscription for stream for '$streamName' with groupName already exists. Updates disabled, doing nothing")
        }
    }
}