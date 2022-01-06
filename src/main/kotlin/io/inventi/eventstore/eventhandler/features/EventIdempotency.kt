package io.inventi.eventstore.eventhandler.features

import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.EventIdempotencyStorage
import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.exception.EventAlreadyHandledException
import org.slf4j.LoggerFactory

class EventIdempotency(
        private val handler: EventstoreEventHandler,
        private val idempotencyStorage: EventIdempotencyStorage,
) : EventListenerFeature {
    private val logger = LoggerFactory.getLogger(handler::class.java)

    override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
        try {
            idempotencyStorage.storeRecord(handler.streamName, handler.groupName, event)
            block()
        } catch (e: EventAlreadyHandledException) {
            logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
        }
    }
}