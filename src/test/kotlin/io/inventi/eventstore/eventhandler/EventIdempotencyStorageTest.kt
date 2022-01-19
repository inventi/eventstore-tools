package io.inventi.eventstore.eventhandler

import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.eventhandler.util.DataBuilder.createdAt
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.overridenEventId
import io.inventi.eventstore.eventhandler.util.DataBuilder.processedEvent
import io.inventi.eventstore.eventhandler.util.DataBuilder.recordedEvent
import io.inventi.eventstore.eventhandler.util.DataBuilder.recordedEventWithMetadata
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.inventi.eventstore.eventhandler.exception.EventAlreadyHandledException
import io.inventi.eventstore.util.ObjectMapperFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class EventIdempotencyStorageTest {
    private val processedEventDao = mockk<ProcessedEventDao>()
    private val objectMapper = ObjectMapperFactory.createDefaultObjectMapper()
    private val eventIdempotencyStorage = EventIdempotencyStorage(processedEventDao, objectMapper) { createdAt }

    @Test
    fun `stores event idempotency record`() {
        // given
        every { processedEventDao.save(any()) } returns 1

        // when
        eventIdempotencyStorage.storeRecord(streamName, groupName, recordedEvent())

        // then
        verify(exactly = 1) { processedEventDao.save(processedEvent()) }
    }

    @Test
    fun `stores idempotency record with overriden event id`() {
        // given
        every { processedEventDao.save(any()) } returns 1

        // when
        eventIdempotencyStorage.storeRecord(streamName, groupName, recordedEventWithMetadata())

        // then
        verify(exactly = 1) { processedEventDao.save(processedEvent(eventId = overridenEventId)) }
    }

    @Test
    fun `throws exception if idempotency record is already stored`() {
        // given
        every { processedEventDao.save(any()) } returns 0

        // expect
        invoking {
            eventIdempotencyStorage.storeRecord(streamName, groupName, recordedEvent())
        } shouldThrow EventAlreadyHandledException::class
    }
}