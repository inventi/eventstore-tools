package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStoreException
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.integration.leader.event.OnGrantedEvent

class CatchupSubscriptionsLeadershipElectionTest {
    private val onGrantedEvent = mockk<OnGrantedEvent> {
        every { role } returns "someRole"
        every { context.yield() } just Runs
    }

    private val catchupSubscriptions = CatchupSubscriptions(
        handlers = listOf(FailingLeaderElectionHandler()),
        eventStore = mockk(),
        objectMapper = mockk(),
        subscriptionCheckpointDao = mockk(),
        subscriptionInitialPositionDao = mockk(),
        transactionTemplate = mockk(),
        idempotencyStorage = mockk(),
        properties = SubscriptionProperties(),
        meterRegistry = null,
    )

    @Test
    fun `yields leadership when granted leadership handling fails with exceptions`() {
        // when
        catchupSubscriptions.handleEvent(onGrantedEvent)

        // then
        verify(exactly = 1) { onGrantedEvent.context.yield() }
    }

    private class FailingLeaderElectionHandler(
        override val streamName: String = DataBuilder.streamName,
        override val groupName: String = DataBuilder.groupName,
    ) : CatchupSubscriptionHandler {
        override val initialPosition = mockk<InitialPosition> {
            every { startSubscriptionFrom(any(), any()) } throws EventStoreException("Some message")
        }
    }
}