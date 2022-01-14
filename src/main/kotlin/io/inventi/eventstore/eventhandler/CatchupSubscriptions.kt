package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.CatchUpSubscription
import com.github.msemys.esjc.CatchUpSubscriptionSettings
import com.github.msemys.esjc.EventStore
import io.inventi.eventstore.Subscriptions
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpoint
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.feature.EventIdempotency
import io.inventi.eventstore.eventhandler.feature.InTransaction
import io.inventi.eventstore.eventhandler.feature.StoreCheckpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.integration.leader.event.OnGrantedEvent
import org.springframework.integration.leader.event.OnRevokedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
@ConditionalOnSubscriptionsEnabled
@ConditionalOnBean(CatchupSubscriptionHandler::class)
class CatchupSubscriptions(
        handlers: List<CatchupSubscriptionHandler>,
        private val eventStore: EventStore,
        private val objectMapper: ObjectMapper,
        private val subscriptionCheckpointDao: SubscriptionCheckpointDao,
        private val transactionTemplate: TransactionTemplate,
        private val idempotencyStorage: EventIdempotencyStorage,
        private val properties: SubscriptionProperties,
) : Subscriptions<CatchupSubscriptionHandler>(handlers) {
    private val subscriptions = mutableListOf<CatchUpSubscription>()

    override fun startSubscriptions() {
        if (!properties.enableCatchupSubscriptionLeaderElection) {
            super.startSubscriptions()
        }
    }

    override fun ensureSubscription(handler: CatchupSubscriptionHandler) {
        val checkpoint = handler.initialPosition.startSubscriptionFrom(eventStore, objectMapper)
                .let { it - 1 } // -1 because catch-up subscription start event numbers are exclusive
                .takeUnless { it < 0 } // use null instead of -1 to indicate the start of the stream

        subscriptionCheckpointDao.createIfNotExists(SubscriptionCheckpoint(handler.groupName, handler.streamName, checkpoint))
    }

    override fun startSubscription(handler: CatchupSubscriptionHandler, onFailure: (EventstoreEventListener.FailureType) -> Unit) {
        logger.info("Starting catch-up subscription for handler: ${handler::class.simpleName}")
        val checkpoint = subscriptionCheckpointDao.currentCheckpoint(handler.groupName, handler.streamName)
        val settings = CatchUpSubscriptionSettings.newBuilder()
                .resolveLinkTos(true)
                .build()
        val listener = CatchupSubscriptionEventListener(
                EventstoreEventListener(
                        handler,
                        handler.initialPosition.replayEventsUntil(eventStore, objectMapper),
                        objectMapper,
                        EventIdempotency(handler, idempotencyStorage),
                        StoreCheckpoint(handler, subscriptionCheckpointDao),
                        InTransaction(transactionTemplate),
                ),
                onFailure,
        )

        subscriptions.add(eventStore.subscribeToStreamFrom(handler.streamName, checkpoint, settings, listener))
    }

    @EventListener
    fun handleEvent(event: OnGrantedEvent) {
        logger.info("Received catch-up subscription leadership for role: ${event.role}")
        super.startSubscriptions()
    }

    @EventListener
    fun handleEvent(event: OnRevokedEvent) {
        logger.info("Catch-up subscription leadership revoked for role: ${event.role}")
        subscriptions.forEach { it.stop() }
        subscriptions.clear()
    }
}