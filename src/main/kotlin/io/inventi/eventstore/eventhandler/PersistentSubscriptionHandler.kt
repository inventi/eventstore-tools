package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.system.SystemConsumerStrategy

interface PersistentSubscriptionHandler : EventstoreEventHandler {
    val consumerStrategy: SystemConsumerStrategy
        get() = SystemConsumerStrategy.ROUND_ROBIN
}