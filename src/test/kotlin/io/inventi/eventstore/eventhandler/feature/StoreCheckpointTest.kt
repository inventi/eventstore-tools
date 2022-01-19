package io.inventi.eventstore.eventhandler.feature

import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.Test

class StoreCheckpointTest {
    private val handler = DataBuilder.eventstoreEventHandler()
    private val checkpointDao = mockk<SubscriptionCheckpointDao>()
    private val storeCheckpoint = StoreCheckpoint(handler, checkpointDao)

    @Test
    fun `stores checkpoint before executing given block`() {
        // given
        every { checkpointDao.incrementCheckpoint(any(), any(), any()) } returns 1
        val event = DataBuilder.recordedEvent()
        val eventNumber = 42L
        val block = mockk<() -> Unit>(relaxed = true)

        // when
        storeCheckpoint.wrap(event, eventNumber, block)

        // then
        verifySequence {
            checkpointDao.incrementCheckpoint(groupName, streamName, eventNumber)
            block()
        }
    }
}