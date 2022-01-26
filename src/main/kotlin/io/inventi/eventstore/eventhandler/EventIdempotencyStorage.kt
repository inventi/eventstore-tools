package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.dao.ProcessedEvent
import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.eventhandler.exception.EventAlreadyHandledException
import io.inventi.eventstore.eventhandler.util.effectiveEventId
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnSubscriptionsEnabled
class EventIdempotencyStorage(
        private val processedEventDao: ProcessedEventDao,
        private val objectMapper: ObjectMapper,
        private val now: () -> Instant = { Instant.now() }
) {
    fun storeRecord(streamName: String, groupName: String, event: RecordedEvent) {
        val idempotentEventRecord = ProcessedEvent(
                eventId = event.effectiveEventId(objectMapper),
                eventType = event.eventType,
                streamName = streamName,
                eventStreamId = event.eventStreamId,
                groupName = groupName,
                createdAt = now(),
        )
        val affectedRows = processedEventDao.save(idempotentEventRecord)
        if (affectedRows == 0) {
            throw EventAlreadyHandledException("Event record already exists: $idempotentEventRecord")
        }
    }
}