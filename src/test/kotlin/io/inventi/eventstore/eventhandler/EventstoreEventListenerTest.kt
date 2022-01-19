package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType.EVENTSTORE_CLIENT_ERROR
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType.UNEXPECTED_ERROR
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.annotation.Retry
import io.inventi.eventstore.eventhandler.events.EventType
import io.inventi.eventstore.eventhandler.events.IrrelevantEvent
import io.inventi.eventstore.eventhandler.events.TestEvent
import io.inventi.eventstore.eventhandler.events.TestMetadata
import io.inventi.eventstore.eventhandler.feature.EventListenerFeature
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.event
import io.inventi.eventstore.eventhandler.util.DataBuilder.eventIds
import io.inventi.eventstore.eventhandler.util.DataBuilder.metadata
import io.inventi.eventstore.eventhandler.util.DataBuilder.resolvedEvent
import io.inventi.eventstore.eventhandler.util.DataBuilder.resolvedEventWithMetadata
import io.inventi.eventstore.util.ObjectMapperFactory
import io.mockk.andThenJust
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class EventstoreEventListenerTest {
    private val handler = mockk<Handler>(relaxed = true)

    @Test
    fun `invokes annotated handler function`() {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        listener.onEvent(resolvedEvent())

        // then
        verify(exactly = 1) {
            handler.handle(event())
        }
    }

    @Test
    fun `invokes handlers with appropriate event types`() {
        // given
        val listener = eventstoreListener(EventHandlerWithIrrelevantEvent(handler))

        // when
        listener.onEvent(resolvedEvent())

        // then
        verify(exactly = 1) {
            handler.handle(event())
        }
        verify(exactly = 0) {
            handler.handle(ofType<IrrelevantEvent>())
        }
    }

    @Test
    fun `does not invoke handler if it is marked as skipped during event replay`() {
        // given
        val listener = eventstoreListener(EventHandlerMarkedForSkipping(handler), 100)

        // when
        listener.onEvent(resolvedEvent())

        // then
        verify {
            handler wasNot called
        }
    }

    @Test
    fun `invokes handler if it is not marked as skipped during event replay`() {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler), 100)

        // when
        listener.onEvent(resolvedEvent())

        // then
        verify(exactly = 1) {
            handler.handle(event())
        }
    }

    @Test
    fun `retries handler on failure`() {
        // given
        val listener = eventstoreListener(EventHandlerWithRetry(handler))
        every { handler.handle(any()) } throws HandlerException()

        // when
        invoking {
            listener.onEvent(resolvedEvent())
        } shouldThrow HandlerException::class

        // then
        verify(exactly = 3) {
            handler.handle(event())
        }
    }

    @Test
    fun `retries handler when exception is wrapped in a cause`() {
        // given
        val listener = eventstoreListener(EventHandlerWithRetry(handler))
        every { handler.handle(any()) } throws RuntimeException(HandlerException())

        // when
        invoking {
            listener.onEvent(resolvedEvent())
        } shouldThrow RuntimeException::class

        // then
        verify(exactly = 3) {
            handler.handle(event())
        }
    }

    @Test
    fun `does not retry on unknown exception`() {
        // given
        val listener = eventstoreListener(EventHandlerWithRetry(handler))
        every { handler.handle(any()) } throws RuntimeException()

        // when
        invoking {
            listener.onEvent(resolvedEvent())
        } shouldThrow RuntimeException::class

        // then
        verify(exactly = 1) {
            handler.handle(event())
        }
    }

    @Test
    fun `retries handler until it is successful`() {
        // given
        val listener = eventstoreListener(EventHandlerWithRetry(handler))
        every { handler.handle(any()) } throws HandlerException() andThenJust runs

        // when
        listener.onEvent(resolvedEvent())

        // then
        verify(exactly = 2) {
            handler.handle(event())
        }
    }

    @Test
    fun `invokes handler with event and metadata`() {
        // given
        val listener = eventstoreListener(EventHandlerWithMetadata(handler))

        // when
        listener.onEvent(resolvedEventWithMetadata())

        // then
        verify(exactly = 1) {
            handler.handle(event(), metadata())
        }
    }

    @Test
    fun `invokes handler with event and eventIds`() {
        // given
        val listener = eventstoreListener(EventHandlerWithEventIds(handler))

        // when
        listener.onEvent(resolvedEventWithMetadata())

        // then
        verify(exactly = 1) {
            handler.handle(event(), eventIds = eventIds())
        }
    }

    @Test
    fun `invokes handler with event, metadata and eventIds`() {
        // given
        val listener = eventstoreListener(EventHandlerWithMetadataAndEventIds(handler))

        // when
        listener.onEvent(resolvedEventWithMetadata())

        // then
        verify(exactly = 1) {
            handler.handle(event(), metadata(), eventIds())
        }
    }

    @Test
    fun `invokes handler with event, eventIds and metadata`() {
        // given
        val listener = eventstoreListener(EventHandlerWithEventIdsAndMetadata(handler))

        // when
        listener.onEvent(resolvedEventWithMetadata())

        // then
        verify(exactly = 1) {
            handler.handle(event(), metadata(), eventIds())
        }
    }

    @Test
    fun `invokes features before handling events`() {
        // given
        val feature1 = featureSpy()
        val feature2 = featureSpy()
        val listener = eventstoreListener(ExecutingEventHandler(handler), features = listOf(feature1, feature2))
        val resolvedEvent = resolvedEvent()

        // when
        listener.onEvent(resolvedEvent)

        // then
        verifySequence {
            feature2.wrap(resolvedEvent.event, resolvedEvent.originalEventNumber(), any())
            feature1.wrap(resolvedEvent.event, resolvedEvent.originalEventNumber(), any())
            handler.handle(event())
        }
    }

    @Test
    fun `wraps handler with extensions`() {
        // given
        val extension1 = mockk<EventHandlerExtension>()
        val extension1Cleanup = mockk<Cleanup>(relaxed = true)
        every { extension1.handle(any(), any()) } returns extension1Cleanup

        val extension2 = mockk<EventHandlerExtension>()
        val extension2Cleanup = mockk<Cleanup>(relaxed = true)
        every { extension2.handle(any(), any()) } returns extension2Cleanup

        val listener = eventstoreListener(ExecutingEventHandler(handler, listOf(extension1, extension2)))
        val resolvedEvent = resolvedEvent()

        // when
        listener.onEvent(resolvedEvent)

        // then
        verifySequence {
            extension1.handle(any(), resolvedEvent.event)
            extension2.handle(any(), resolvedEvent.event)
            handler.handle(event())
            extension2Cleanup(null)
            extension1Cleanup(null)
        }
    }

    @Test
    fun `passes error to handler extension's cleanup function`() {
        // given
        val extension = mockk<EventHandlerExtension>()
        val extensionCleanup = mockk<Cleanup>(relaxed = true)
        every { extension.handle(any(), any()) } returns extensionCleanup

        val exception = HandlerException()
        every { handler.handle(any()) } throws exception

        val listener = eventstoreListener(ExecutingEventHandler(handler, listOf(extension)))
        val resolvedEvent = resolvedEvent()

        // when
        invoking {
            listener.onEvent(resolvedEvent)
        } shouldThrow exception

        // then
        verify(exactly = 1) {
            extensionCleanup(exception)
        }
    }

    @Test
    fun `classifies eventstore exceptions as client errors`() {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        val failureType = listener.onClose(SubscriptionDropReason.NotFound, EventStoreException())

        // then
        failureType shouldBeEqualTo EVENTSTORE_CLIENT_ERROR
    }

    @Test
    fun `classifies exceptions caused by eventstore exception as client errors`() {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        val failureType = listener.onClose(SubscriptionDropReason.NotFound, RuntimeException(EventStoreException()))

        // then
        failureType shouldBeEqualTo EVENTSTORE_CLIENT_ERROR
    }

    @Test
    fun `classifies unknown exceptions as unexpected errors`() {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        val failureType = listener.onClose(SubscriptionDropReason.NotFound, RuntimeException())

        // then
        failureType shouldBeEqualTo UNEXPECTED_ERROR
    }

    @ParameterizedTest
    @EnumSource(SubscriptionDropReason::class, names = [
        "ConnectionClosed",
        "ServerError",
        "SubscribingError",
        "UserInitiated",
        "ProcessingQueueOverflow",
        "Unknown",
    ])
    fun `classifies recoverable subscription drop reasons as client errors`(dropReason: SubscriptionDropReason) {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        val failureType = listener.onClose(dropReason, RuntimeException())

        // then
        failureType shouldBeEqualTo EVENTSTORE_CLIENT_ERROR
    }

    @ParameterizedTest
    @EnumSource(SubscriptionDropReason::class, names = [
        "ConnectionClosed",
        "ServerError",
        "SubscribingError",
        "UserInitiated",
        "ProcessingQueueOverflow",
        "Unknown",
    ], mode = EnumSource.Mode.EXCLUDE)
    fun `classifies unrecoverable subscription drop reasons as unexpected errors`(dropReason: SubscriptionDropReason) {
        // given
        val listener = eventstoreListener(ExecutingEventHandler(handler))

        // when
        val failureType = listener.onClose(dropReason, RuntimeException())

        // then
        failureType shouldBeEqualTo UNEXPECTED_ERROR
    }

    private fun eventstoreListener(
            eventHandler: EventstoreEventHandler,
            firstEventNumberToHandle: Long = 0,
            features: List<EventListenerFeature> = emptyList(),
    ) = EventstoreEventListener(
            eventHandler,
            firstEventNumberToHandle,
            ObjectMapperFactory.createDefaultObjectMapper(),
            *features.toTypedArray(),
    )

    private fun featureSpy() = spyk(object : EventListenerFeature {
        override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
            block()
        }
    })

    private interface Handler {
        fun handle(event: TestEvent, metadata: TestMetadata? = null, eventIds: EventIds? = null)
    }

    private class HandlerException : RuntimeException()

    private abstract class TestEventHandler : EventstoreEventHandler {
        override val streamName get() = DataBuilder.streamName
        override val groupName get() = DataBuilder.groupName
    }

    private class ExecutingEventHandler(
            private val handler: Handler,
            override val extensions: List<EventHandlerExtension> = emptyList(),
    ) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType) {
            handler.handle(event)
        }
    }

    private class EventHandlerWithMetadata(private val handler: Handler) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType, metadata: TestMetadata) {
            handler.handle(event, metadata)
        }
    }

    private class EventHandlerWithEventIds(private val handler: Handler) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType, eventIds: EventIds) {
            handler.handle(event, eventIds = eventIds)
        }
    }

    private class EventHandlerWithMetadataAndEventIds(private val handler: Handler) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType, metadata: TestMetadata, eventIds: EventIds) {
            handler.handle(event, metadata, eventIds)
        }
    }

    private class EventHandlerWithEventIdsAndMetadata(private val handler: Handler) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType, eventIds: EventIds, metadata: TestMetadata) {
            handler.handle(event, metadata, eventIds)
        }
    }

    private class EventHandlerMarkedForSkipping(private val handler: Handler) : TestEventHandler() {
        @EventHandler(skipWhenReplaying = true)
        fun onEvent(event: EventType) {
            handler.handle(event)
        }
    }

    private class EventHandlerWithRetry(private val handler: Handler) : TestEventHandler() {
        @Retry(exceptions = [HandlerException::class], backoffDelayMillis = 10)
        @EventHandler(skipWhenReplaying = true)
        fun onEvent(event: EventType) {
            handler.handle(event)
        }
    }

    private class EventHandlerWithIrrelevantEvent(private val handler: Handler) : TestEventHandler() {
        @EventHandler
        fun onEvent(event: EventType) {
            handler.handle(event)
        }

        @EventHandler
        fun onEvent(event: IrrelevantEvent) {
            handler.handle(event)
        }
    }
}