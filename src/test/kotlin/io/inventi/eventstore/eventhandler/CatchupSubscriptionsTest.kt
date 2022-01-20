package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStore
import io.inventi.eventstore.EventStoreIntegrationTest
import io.inventi.eventstore.EventStoreToolsSubscriptionsConfiguration
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.events.EventType
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.event
import io.inventi.eventstore.eventhandler.util.DataBuilder.eventId
import io.inventi.eventstore.eventhandler.util.DataBuilder.eventType
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.metadata
import io.inventi.eventstore.eventhandler.util.DataBuilder.overridenEventId
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.inventi.eventstore.eventhandler.util.Handler
import io.inventi.eventstore.eventhandler.util.WithAsyncHandlerAssertions
import io.inventi.eventstore.eventhandler.util.WithEventstoreOperations
import io.mockk.mockk
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
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
@Import(CatchupSubscriptionsTest.Config::class)
@ActiveProfiles("test")
@DirtiesContext // ensures that eventStore bean is not reused, because each bean has a different test container port
class CatchupSubscriptionsTest : EventStoreIntegrationTest(), WithAsyncHandlerAssertions, WithEventstoreOperations {
    @Autowired
    private lateinit var checkpointDao: SubscriptionCheckpointDao

    @Autowired
    private lateinit var processedEventDao: ProcessedEventDao

    @Autowired
    override lateinit var eventStore: EventStore

    @Autowired
    override lateinit var handler: Handler

    @Test
    fun `handles event`() {
        // given
        val event = event()

        // when
        appendEvent(event, eventId)

        // then
        assertEventWasHandled(event, eventId)
    }

    @Test
    fun `ignores already handled event`() {
        // given
        val eventId = UUID.randomUUID().toString()
        val metadataWithOverridenId = metadata()

        appendEvent(event(), overridenEventId)
        waitUntilEventIsHandled(overridenEventId)

        // when
        appendEvent(event(), eventId, metadataWithOverridenId)

        // then
        assertEventWasNotHandled(eventId)
    }

    @Test
    fun `stores checkpoint`() {
        // given
        val eventId = UUID.randomUUID().toString()
        val checkpointBeforeEvent = checkpointDao.currentCheckpoint(groupName, streamName)!!

        // when
        appendEvent(event(), eventId)
        waitUntilEventIsHandled(eventId)

        // then
        checkpointDao.currentCheckpoint(groupName, streamName) shouldBeEqualTo checkpointBeforeEvent + 1
    }

    @Test
    fun `stores processed event`() {
        // given
        val eventId = UUID.randomUUID().toString()

        // when
        appendEvent(event(), eventId)
        waitUntilEventIsHandled(eventId)

        // then
        processedEventDao.findBy(eventId, streamName, groupName, eventType).shouldNotBeNull()
    }

    @TestConfiguration
    class Config {
        @Bean
        fun testHandler(): Handler = mockk(relaxed = true)

        @Bean
        fun catchupSubscriptionHandler(handler: Handler) = TestCatchupSubscriptionHandler(handler = handler)
    }

    class TestCatchupSubscriptionHandler(
            override val streamName: String = DataBuilder.streamName,
            override val groupName: String = DataBuilder.groupName,
            private val handler: Handler,
    ) : CatchupSubscriptionHandler {
        @EventHandler
        fun onEvent(event: EventType, eventIds: EventIds) {
            handler.handle(event, eventIds = eventIds)
        }
    }
}