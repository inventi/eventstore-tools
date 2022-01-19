package io.inventi.eventstore.eventhandler.feature

import io.inventi.eventstore.eventhandler.EventIdempotencyStorage
import io.inventi.eventstore.eventhandler.exception.EventAlreadyHandledException
import io.inventi.eventstore.eventhandler.util.DataBuilder.eventstoreEventHandler
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.recordedEvent
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.mockk.Called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.Test

class EventIdempotencyTest {
    private val handler = eventstoreEventHandler()
    private val eventIdempotencyStorage = mockk<EventIdempotencyStorage>()
    private val eventIdempotency = EventIdempotency(handler, eventIdempotencyStorage)

    @Test
    fun `ensures idempotency record is stored before executing given block`() {
        // given
        every { eventIdempotencyStorage.storeRecord(any(), any(), any()) } just runs
        val event = recordedEvent()
        val block = mockk<() -> Unit>(relaxed = true)

        // when
        eventIdempotency.wrap(event, 1, block)

        // then
        verifySequence {
            eventIdempotencyStorage.storeRecord(streamName, groupName, event)
            block()
        }
    }

    @Test
    fun `does nothing if event is already handled`() {
        // given
        every { eventIdempotencyStorage.storeRecord(any(), any(), any()) } throws EventAlreadyHandledException("")
        val event = recordedEvent()
        val block = mockk<() -> Unit>(relaxed = true)

        // when
        eventIdempotency.wrap(event, 1, block)

        // then
        verify {
            block wasNot Called
        }
    }
}