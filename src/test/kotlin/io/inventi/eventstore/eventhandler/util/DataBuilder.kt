package io.inventi.eventstore.eventhandler.util

import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.proto.EventStoreClientMessages
import com.github.msemys.esjc.util.UUIDConverter
import com.google.protobuf.ByteString
import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.dao.ProcessedEvent
import io.inventi.eventstore.eventhandler.events.EventType
import io.inventi.eventstore.eventhandler.events.TestMetadata
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.util.ObjectMapperFactory
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

object DataBuilder {
    private val objectMapper = ObjectMapperFactory.createDefaultObjectMapper()

    val createdAt: Instant = Instant.parse("2022-01-01T00:00:00Z")
    const val eventId = "11111111-1111-1111-1111-111111111111"
    const val overridenEventId = "00000000-0000-0000-0000-000000000000"
    const val streamName = "StreamName"
    const val eventStreamId = "EventStreamId"
    const val groupName = "GroupName"
    const val eventType = "EventType"
    const val eventNumber = 1L

    fun event() = EventType("random-value")

    fun metadata() = TestMetadata(
            overrideEventId = "00000000-0000-0000-0000-000000000000",
            overrideEventNumber = "0",
            transformedFromEventNumber = 0,
            transformedFromJavaEventId = "00000000-0000-0000-0000-000000000000"
    )

    fun eventIds() = EventIds(overridenEventId, eventId)

    fun resolvedEvent(): ResolvedEvent = buildResolvedEvent()

    fun recordedEvent(): RecordedEvent = resolvedEvent().event

    fun resolvedEventWithMetadata(): ResolvedEvent = buildResolvedEvent(metadata())

    fun recordedEventWithMetadata(): RecordedEvent = resolvedEventWithMetadata().event

    fun processedEvent(eventId: String = DataBuilder.eventId) = ProcessedEvent(
            eventId = eventId,
            streamName = streamName,
            eventStreamId = eventStreamId,
            groupName = groupName,
            eventType = eventType,
            createdAt = createdAt
    )

    fun eventstoreEventHandler() = object : EventstoreEventHandler {
        override val streamName get() = DataBuilder.streamName
        override val groupName get() = DataBuilder.groupName
    }

    private fun buildResolvedEvent(eventMetadata: TestMetadata? = null): ResolvedEvent {
        val jsonContentType = 1
        return ResolvedEvent(mockk<EventStoreClientMessages.ResolvedEvent>(relaxed = true) {
            every { hasLink() } returns false
            every { hasEvent() } returns true
            every { event } returns mockk {
                every { eventType } returns DataBuilder.eventType
                every { eventNumber } returns DataBuilder.eventNumber
                every { eventStreamId } returns DataBuilder.eventStreamId
                every { eventId } returns ByteString.copyFrom(UUIDConverter.toBytes(UUID.fromString(DataBuilder.eventId)))
                every { hasData() } returns true
                every { dataContentType } returns jsonContentType
                every { data } returns objectMapper.writeValueAsString(event()).toByteString()
                every { hasMetadata() } returns (eventMetadata != null)
                every { metadata } returns eventMetadata?.let { objectMapper.writeValueAsString(it).toByteString() }
                every { hasCreatedEpoch() } returns false
            }
        })
    }

    private fun String.toByteString() = ByteString.copyFrom(this, Charsets.UTF_8.name())
}