package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.StreamPosition


sealed class InitialPosition {
    abstract fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long

    object FromBeginning : InitialPosition() {
        override fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            return StreamPosition.START
        }
    }

    class TakeOverPersistentSubscription(
            private val streamName: String,
            private val groupName: String,
    ) : InitialPosition() {
        override fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            val subscriptionCheckpointStream = "\$persistentsubscription-$streamName::$groupName-checkpoint"
            val readResult = eventStore.readEvent(subscriptionCheckpointStream, StreamPosition.END, true).join()

            val data = readResult.event?.event?.data
            return data?.let {
                val oldPosition: Long = objectMapper.readValue(data)
                oldPosition + 1
            } ?: DEFAULT_START_POSITION
        }

        companion object DEFAULTS {
            private const val DEFAULT_START_POSITION = StreamPosition.START
        }
    }

    class FromTheEndOfStream(
            private val streamName: String,
    ) : InitialPosition() {
        override fun getFirstEventNumberToHandle(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            val readResult = eventStore.readEvent(streamName, StreamPosition.END, true).join()

            return readResult.event.originalEventNumber()
        }
    }
}