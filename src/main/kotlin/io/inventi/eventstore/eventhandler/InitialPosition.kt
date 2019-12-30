package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.StreamPosition


sealed class InitialPosition {
    abstract fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long

    class FromBeginning : InitialPosition() {
        override fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            return StreamPosition.START
        }
    }

    class TakeOverPersistentSubscription(
            val streamName: String,
            val groupName: String
    ) : InitialPosition() {
        override fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            val subscriptionCheckpointStream = "\$persistentsubscription-$streamName::$groupName-checkpoint"
            val readResult = eventStore.readEvent(subscriptionCheckpointStream, StreamPosition.END, true).join()

            val oldPosition = objectMapper.readValue<Long>(readResult.event.event.data)

            return oldPosition + 1
        }
    }
}