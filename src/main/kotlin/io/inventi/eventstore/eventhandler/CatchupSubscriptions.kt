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
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.event.EventListener
import org.springframework.integration.leader.event.OnGrantedEvent
import org.springframework.integration.leader.event.OnRevokedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

private data class SubscriptionState(private var subscription: CatchUpSubscription? = null) {
    val isActive get() = subscription != null
    val gaugeValue get() = if (isActive) 1 else 0

    fun update(newSubscription: CatchUpSubscription) {
        drop()
        subscription = newSubscription
    }

    fun drop() {
        subscription?.stop()
        subscription = null
    }
}

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
        properties: SubscriptionProperties,
        meterRegistry: MeterRegistry? = null,
) : Subscriptions<CatchupSubscriptionHandler>(handlers) {
    private var isLeader = !properties.enableCatchupSubscriptionLeaderElection
    private val subscriptionsByHandler = mutableMapOf<CatchupSubscriptionHandler, SubscriptionState>().apply {
        handlers.forEach { handler ->
            put(handler, SubscriptionState())

            if (meterRegistry != null) {
                Gauge.builder("eventstore-tools.catchup.subscriptions.connections") { getValue(handler).gaugeValue }
                        .tag("groupName", handler.groupName)
                        .tag("streamName", handler.streamName)
                        .tag("handler", handler::class.simpleName.orEmpty())
                        .register(meterRegistry)
            }
        }
    }

    override fun startSubscriptions() {
        if (isLeader) {
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
        if (!isLeader) return logger.info("Subscription for handler ${this::class.simpleName} will not be started because current instance was not elected as a leader")

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

        subscriptionsByHandler
                .getValue(handler)
                .update(eventStore.subscribeToStreamFrom(handler.streamName, checkpoint, settings, listener))
    }

    @EventListener
    fun handleEvent(event: OnGrantedEvent) {
        logger.info("Received catch-up subscription leadership for role: ${event.role}. Starting subscriptions...")
        isLeader = true
        startSubscriptions()
    }

    @EventListener
    fun handleEvent(event: OnRevokedEvent) {
        logger.info("Catch-up subscription leadership revoked for role: ${event.role}. Stopping subscriptions...")
        isLeader = false
        subscriptionsByHandler.values.forEach { it.drop() }
    }
}