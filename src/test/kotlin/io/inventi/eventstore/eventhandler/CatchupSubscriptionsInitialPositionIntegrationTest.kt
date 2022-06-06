package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStore
import io.inventi.eventstore.EventStoreIntegrationTest
import io.inventi.eventstore.EventStoreToolsSubscriptionsConfiguration
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.events.EventType
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.event
import io.inventi.eventstore.eventhandler.util.DataBuilder.eventId
import io.inventi.eventstore.eventhandler.util.Handler
import io.inventi.eventstore.eventhandler.util.WithAsyncHandlerAssertions
import io.inventi.eventstore.eventhandler.util.WithEventstoreOperations
import io.inventi.eventstore.util.LoggerDelegate
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(classes = [EventStoreToolsSubscriptionsConfiguration::class])
@Import(CatchupSubscriptionsInitialPositionIntegrationTest.Config::class)
@ActiveProfiles("test")
@DirtiesContext // ensures that eventStore bean is not reused, because each bean has a different test container port
class CatchupSubscriptionsInitialPositionIntegrationTest : EventStoreIntegrationTest(), WithAsyncHandlerAssertions, WithEventstoreOperations {

    @Autowired
    override lateinit var eventStore: EventStore

    @Autowired
    override lateinit var handler: Handler

    @Autowired
    private lateinit var catchupSubscriptions: CatchupSubscriptions

    @Test
    fun `handles event added while subscription was dropped with handler's initial position set to end of stream`() {
        // given
        val irrelevantEventId = UUID.randomUUID().toString()
        val irrelevantEvent = event("previously-existed-event")
        appendEvent(irrelevantEvent, irrelevantEventId)
        waitUntilEventIsHandled(irrelevantEventId)

        catchupSubscriptions.dropSubscriptions()

        // when
        val newEvent = event("event-added-while-subscription-was-dropped")
        appendEvent(newEvent, eventId)
        assertEventWasNotHandled(eventId)

        catchupSubscriptions.startSubscriptions()

        // then
        assertEventWasHandled(newEvent, eventId)
    }

    @TestConfiguration
    class Config {
        @Bean
        fun test(): Handler = mockk(relaxed = true)

        @Bean
        fun catchupSubscriptionHandler(handler: Handler) = EndOfStreamCatchupSubscriptionHandler(handler = handler)
    }

    class EndOfStreamCatchupSubscriptionHandler(
        override val streamName: String = DataBuilder.streamName,
        override val groupName: String = DataBuilder.groupName,
        private val handler: Handler,
    ) : CatchupSubscriptionHandler {

        override val initialPosition = InitialPosition.FromTheEndOfStream(streamName)

        @EventHandler(skipWhenReplaying = true)
        fun onEvent(event: EventType, eventIds: EventIds) {
            handler.handle(event, eventIds = eventIds)
        }

        private val logger by LoggerDelegate()
        override val extensions: List<EventHandlerExtension>
            get() = listOf(LoggingExtension(logger))
    }
}