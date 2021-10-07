/**
 * This script is intended to be used for Copy And Replace functionality for a single stream.
 * It iterates over a stream, transforms selected events and writes them back to the stream.
 * After doing so, the script will set truncate-before (`$tb`) metadata field on a stream
 * in order to soft-delete "old" events. When accessing the stream, only newly created events
 * will be visible. The data will still be available until next scavange. To access it, you
 * should reset or remove `$tb` metadata field. DO NOT DO THIS UNLESS YOU UNDERSTAND THE
 * CONSEQUENCES!!!
 *
 * Newly created events will have two additional metadata fields:
 * - "transformedFromJavaId"
 * - "transformedFromNumber"
 * which will indicate from which event ID and which event number it was created.
 * The ID is used for IdempotentEventHandlers to know that event was already processed.
 * In case you want to reapply events in event handlers, you need to replay the projection.
 *
 * TRANSFORMING EVENTS: You must call copyReplace function with streamId, eventNumbers and transformFn,
 * `transformFn()` will be called, passing `com.github.msemys.esjc.RecordedEvent` as first parameter.
 * There you can transform the event body any way you want, and you must return in serialized to `ByteArray?`
 * If the function returns null, the event is not copied to new generation (it is removed).
 *
 * CONFIGURATION:
 * `EsConfig` object is used to configure various behaviours of this script:
 * - ES_HOST -- EventStore Host where to connect
 * - ES_PORT -- EventStore port
 * - ES_USER -- EventStore Username
 * - ES_PASS -- EventStore Password
 *
 */

package io.inventi.eventstore.util.script

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import com.github.msemys.esjc.ExpectedVersion
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.StreamMetadata
import com.github.msemys.esjc.StreamMetadataResult
import io.inventi.eventstore.util.ObjectMapperFactory
import java.net.InetSocketAddress
import java.time.Duration

val EMPTY_OBJECT_BYTE_ARRAY = "{}".toByteArray()

const val OVERRIDE_EVENT_ID = "overrideEventId"
const val OVERRIDE_EVENT_NUMBER = "overrideEventNumber"
const val TRANSFORMED_FROM_ID = "transformedFromJavaEventId"
const val TRANSFORMED_FROM_NUMBER = "transformedFromEventNumber"

private data class TransformedEventsData(
        val initialExpectedVersion: Long,
        val eventsData: Map<RecordedEvent, EventData>
)

data class EsConfig(
        val esHost: String = "localhost",
        val esPort: Int = 1113,
        val esUser: String = "admin",
        val esPass: String = "changeit",
        val heartbeatTimeoutInSeconds: Long = 10,
        val operationTimeoutInSeconds: Long = 60,
)

fun withEventStore(
        esConfig: EsConfig = EsConfig(),
        bufferSize: Int = 1024,
        writeChunkSize: Int = 128,
        objectMapper: ObjectMapper = ObjectMapperFactory.createDefaultObjectMapper(),
        block: CopyAndReplaceOperation.(eventStore: EventStore) -> Unit,
) {
    val eventStore = EventStoreBuilder.newBuilder()
            .maxReconnections(3)
            .heartbeatTimeout(Duration.ofSeconds(esConfig.heartbeatTimeoutInSeconds))
            .operationTimeout(Duration.ofSeconds(esConfig.operationTimeoutInSeconds))
            .singleNodeAddress(InetSocketAddress.createUnresolved(esConfig.esHost, esConfig.esPort))
            .userCredentials(esConfig.esUser, esConfig.esPass)
            .build()
    try {
        CopyAndReplaceOperation(eventStore, objectMapper, bufferSize, writeChunkSize).block(eventStore)
    } finally {
        eventStore.shutdown()
    }
}

data class CopyAndReplaceOperation(
        private val eventStore: EventStore,
        private val objectMapper: ObjectMapper,
        private val bufferSize: Int,
        private val writeChunkSize: Int,
) {
    fun copyAndReplace(streamId: String, eventNumbers: List<Long>, transformFn: (event: RecordedEvent, mapper: ObjectMapper) -> ByteArray?) {
        val newTransformedEventsData = transformEvents(streamId, eventNumbers, transformFn)
        val newEventsData = newTransformedEventsData.eventsData.values
        val initialExpectedVersion = newTransformedEventsData.initialExpectedVersion

        appendEvents(streamId, newEventsData, initialExpectedVersion)
        truncateBefore(initialExpectedVersion + 1, streamId)
    }

    private fun transformEvents(streamId: String, eventNumbers: List<Long>, transformFn: (event: RecordedEvent, mapper: ObjectMapper) -> ByteArray?): TransformedEventsData {
        val firstEventNumber = this.eventStore.getStreamMetadata(streamId)
                .thenApply { it.streamMetadata.truncateBefore }
                .get()
                ?: 0

        var initialExpectedVersion: Long = firstEventNumber
        val eventData = this.eventStore.iterateStreamEventsForward(streamId, firstEventNumber, bufferSize, true)
                .asSequence().mapNotNull { resolvedEvent ->
                    val event = resolvedEvent.event
                    initialExpectedVersion = event.eventNumber
                    val (data, meta) = if (resolvedEvent.originalEventNumber() in eventNumbers) {
                        val newData = transformFn(event, objectMapper)
                        newData to transformMetadata(event, objectMapper, shouldPutId = true)
                    } else {
                        event.data to transformMetadata(event, objectMapper, shouldPutId = true)
                    }

                    println("#${event.eventNumber}: ${meta?.toString(Charsets.UTF_8)} --> ${data?.toString(Charsets.UTF_8)}")
                    data?.let {
                        event to EventData.newBuilder()
                                .type(event.eventType)
                                .jsonData(data)
                                .jsonMetadata(meta)
                                .build()
                    }

                }.toMap()
        return TransformedEventsData(initialExpectedVersion, eventData)
    }

    private fun appendEvents(streamId: String, events: Collection<EventData>, initialExpectedVersion: Long) {
        eventStore.startTransaction(streamId,  initialExpectedVersion).get().use { transaction ->
            events.chunked(writeChunkSize).forEach { events ->
                transaction.write(events).get()
            }
            transaction.commit().get()
        }
    }

    private fun transformMetadata(event: RecordedEvent, objectMapper: ObjectMapper, shouldPutId: Boolean): ByteArray? {
        val metaData = event.metadata.takeIf { it.isNotEmpty() } ?: EMPTY_OBJECT_BYTE_ARRAY
        val newMeta = objectMapper.readTree(metaData).apply {
            this as ObjectNode

            put(TRANSFORMED_FROM_NUMBER, event.eventNumber)
            if (shouldPutId) {
                val previousIdOverride = path(OVERRIDE_EVENT_ID)?.textValue()
                val previousNumberOverride = path(OVERRIDE_EVENT_NUMBER)?.textValue()
                put(OVERRIDE_EVENT_ID, previousIdOverride ?: event.eventId.toString())
                put(OVERRIDE_EVENT_NUMBER, previousNumberOverride ?: event.eventNumber.toString())

                put(TRANSFORMED_FROM_ID, event.eventId.toString())
                put(TRANSFORMED_FROM_NUMBER, event.eventNumber)
            }
        }

        return objectMapper.writeValueAsBytes(newMeta)
    }

    private fun truncateBefore(eventNumber: Long, stream: String) {
        val readResult: StreamMetadataResult? = eventStore.getStreamMetadata(stream).join()
        val metastreamVersion = readResult?.metastreamVersion ?: ExpectedVersion.ANY
        println("metastreamVersion -> $metastreamVersion")
        val updated = (readResult?.streamMetadata?.toBuilder() ?: StreamMetadata.newBuilder())
                .truncateBefore(eventNumber)
                .build()

        eventStore.setStreamMetadata(stream, metastreamVersion, updated).join()
    }
}

