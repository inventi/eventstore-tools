package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.StreamPosition.END
import com.github.msemys.esjc.StreamPosition.START

sealed class InitialPosition {
    fun replayEventsUntil(eventStore: EventStore, objectMapper: ObjectMapper) = if (replayEvents) {
        eventNumber(eventStore, objectMapper)
    } else {
        START
    }

    fun startSubscriptionFrom(eventStore: EventStore, objectMapper: ObjectMapper) = if (!replayEvents) {
        eventNumber(eventStore, objectMapper)
    } else {
        START
    }

    protected abstract val replayEvents: Boolean
    protected abstract fun eventNumber(eventStore: EventStore, objectMapper: ObjectMapper): Long

    object FromBeginning : InitialPosition() {
        override val replayEvents: Boolean
            get() = false

        override fun eventNumber(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            return START
        }
    }

    class TakeOverPersistentSubscription(
            private val streamName: String,
            private val groupName: String,
            override val replayEvents: Boolean = true,
    ) : InitialPosition() {
        override fun eventNumber(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            val subscriptionCheckpointStream = "\$persistentsubscription-$streamName::$groupName-checkpoint"
            val readResult = eventStore.readEvent(subscriptionCheckpointStream, END, true).join()

            val data = readResult.event?.event?.data
            return data?.let {
                val oldPosition: Long = objectMapper.readValue(data)
                oldPosition + 1
            } ?: START
        }
    }

    class FromTheEndOfStream(
            private val streamName: String,
            override val replayEvents: Boolean = true,
    ) : InitialPosition() {
        override fun eventNumber(eventStore: EventStore, objectMapper: ObjectMapper): Long {
            val readResult = eventStore.readEvent(streamName, END, true).join()

            return readResult.event.originalEventNumber()
        }
    }
}