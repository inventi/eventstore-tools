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
import io.inventi.eventstore.eventhandler.util.WithEventstoreOperations
import io.inventi.eventstore.eventhandler.util.WithMeterRegistryAssertions
import io.micrometer.core.instrument.ImmutableTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [EventStoreToolsSubscriptionsConfiguration::class])
@Import(CatchupSubscriptionsMeterRegistryIntegrationTest.Config::class)
@ActiveProfiles("test")
@DirtiesContext // ensures that eventStore bean is not reused, because each bean has a different test container port
class CatchupSubscriptionsMeterRegistryIntegrationTest : EventStoreIntegrationTest(), WithMeterRegistryAssertions, WithEventstoreOperations {

    @Autowired
    override lateinit var eventStore: EventStore

    @Autowired
    override lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var handler: Handler

    @Test
    fun `shows metric's gauge as active when handler is subscribed to stream`() {
        // expect
        assertHandlerGaugeValue(1.0)
    }

    @Test
    fun `shows metric's gauge as inactive when handler is unsubscribed from stream`() {
        // given
        val event = event()

        every { handler.handle(any(), any(), any()) } throws RuntimeException("")

        // when
        appendEvent(event, eventId)

        // then
        assertHandlerGaugeValue(0.0)
    }

    private fun assertHandlerGaugeValue(expectedGaugeValue: Double) {
        assertGaugeWithTagHasValue(
            CATCHUP_SUBSCRIPTIONS_CONNECTIONS_GAUGE_NAME,
            ImmutableTag(CATCHUP_SUBSCRIPTIONS_CONNECTIONS_GAUGE_TAG_KEY, handlerTag),
            expectedGaugeValue
        )
    }

    companion object {
        private const val CATCHUP_SUBSCRIPTIONS_CONNECTIONS_GAUGE_NAME = "eventstore-tools.catchup.subscriptions.connections"
        private const val CATCHUP_SUBSCRIPTIONS_CONNECTIONS_GAUGE_TAG_KEY = "handler"
        private val handlerTag = TestCatchupSubscriptionHandler::class.simpleName.orEmpty()
    }

    @TestConfiguration
    class Config {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

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