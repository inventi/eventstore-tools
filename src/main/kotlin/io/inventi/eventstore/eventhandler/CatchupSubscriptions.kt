package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.CatchUpSubscriptionSettings
import com.github.msemys.esjc.EventStore
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpoint
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.features.EventIdempotency
import io.inventi.eventstore.eventhandler.features.InTransaction
import io.inventi.eventstore.eventhandler.features.StoreCheckpoint
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
@ConditionalOnSubscriptionsEnabled
class CatchupSubscriptions(
        handlers: List<CatchupSubscriptionHandler>,
        private val eventStore: EventStore,
        private val objectMapper: ObjectMapper,
        private val subscriptionCheckpointDao: SubscriptionCheckpointDao,
        private val transactionTemplate: TransactionTemplate,
        private val idempotencyStorage: EventIdempotencyStorage,
) : Subscriptions<CatchupSubscriptionHandler>(handlers) {

    override fun ensureSubscription(handler: CatchupSubscriptionHandler) {
        subscriptionCheckpointDao.createIfNotExists(SubscriptionCheckpoint(handler.groupName, handler.streamName))
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
                        handler.initialPosition.getFirstEventNumberToHandle(eventStore, objectMapper),
                        objectMapper,
                        EventIdempotency(handler, idempotencyStorage),
                        StoreCheckpoint(handler, subscriptionCheckpointDao),
                        InTransaction(transactionTemplate),
                ),
                onFailure,
        )

        eventStore.subscribeToStreamFrom(handler.streamName, checkpoint, settings, listener)
    }
}