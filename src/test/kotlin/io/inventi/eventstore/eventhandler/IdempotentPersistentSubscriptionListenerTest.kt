package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscription
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.RetryableResolvedEvent
import com.google.protobuf.ByteString
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.events.a.EventA
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.eventhandler.util.ExecutingTransactionTemplate
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.springframework.transaction.support.TransactionTemplate

@ExtendWith(MockKExtension::class)
internal class IdempotentPersistentSubscriptionListenerTest {

    private val streamName = "SomeStream"
    private val groupName = "SomeGroup"

    @MockK
    private lateinit var handler: Handler

    @MockK
    private lateinit var eventStore: EventStore

    @MockK
    private lateinit var saveEventId: (IdempotentEventClassifierRecord) -> Boolean

    @MockK
    private lateinit var shouldSkip: (ResolvedEvent) -> Boolean

    @MockK
    private lateinit var handlerExtension: EventHandlerExtension

    private val transactionTemplate: TransactionTemplate = ExecutingTransactionTemplate()

    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        registerModule(KotlinModule())
        registerModule(JavaTimeModule())
    }


    @RelaxedMockK
    private lateinit var logger: Logger

    @MockK
    private lateinit var persistentSubscription: PersistentSubscription

    private lateinit var listener: IdempotentPersistentSubscriptionListener


    @BeforeEach
    fun setUp() {
        listener = IdempotentPersistentSubscriptionListener(
                EventHandlerImplementation(handler),
                streamName, groupName, eventStore, saveEventId, shouldSkip,
                listOf(handlerExtension), transactionTemplate, objectMapper, logger
        )
        every { persistentSubscription.acknowledge(any<RetryableResolvedEvent>()) } just runs
    }

    @Test
    fun `calls handler method on unseen event`() {
        // given
        every { handler.handle() } just runs
        eventIsUnseen(true)
        eventShuoldBeSkipped(false)

        every { handlerExtension.beforeHandle(any(), any()) } just runs
        every { handlerExtension.afterHandle(any(), any()) } just runs

        val eventMessage = retryableResolvedEvent(
                uuid = "11111111-1111-1111-1111-111111111111",
                number = 0L,
                eventData = """{"x": 5}""",
                eventMetadata = null
        )

        // when
        listener.onEvent(persistentSubscription, eventMessage)

        // then
        verify { handler.handle() }
    }

    private fun eventIsUnseen(isUnseen: Boolean) {
        every { saveEventId.invoke(any()) } returns isUnseen
    }

    private fun eventShuoldBeSkipped(skip: Boolean) {
        every { shouldSkip.invoke(any()) } returns skip
    }

    private fun retryableResolvedEvent(uuid: String, number: Long, eventData: String, eventMetadata: String?): RetryableResolvedEvent {
        val JSON_CONTENT_TYPE = 1
        return RetryableResolvedEvent(mockk() {
            every { hasLink() } returns false
            every { hasEvent() } returns true
            every { event } returns mockk() {
                every { eventType } returns "EventA"
                every { eventNumber } returns number
                every { eventStreamId } returns streamName
                every { eventId } returns uuid.toByteString()
                every { hasData() } returns true
                every { dataContentType } returns JSON_CONTENT_TYPE
                every { data } returns eventData.toByteString()
                every { hasMetadata() } returns (eventMetadata != null)
                every { metadata } returns eventMetadata?.toByteString()
                every { hasCreatedEpoch() } returns false
            }
        }, 0)
    }

    private fun String.toByteString() = ByteString.copyFrom(this, Charsets.UTF_8.name())

    private class EventHandlerImplementation(private val handler: Handler) {
        @EventHandler
        fun onEvent(event: EventA) {
            handler.handle()
        }
    }

    interface Handler {
        fun handle()
    }
}