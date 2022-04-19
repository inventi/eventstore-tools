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
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.StreamMetadata
import com.github.msemys.esjc.StreamMetadataResult
import io.inventi.eventstore.util.ObjectMapperFactory
import java.net.InetSocketAddress
import java.time.Duration

val EMPTY_OBJECT_BYTE_ARRAY = "{}".toByteArray()

const val OVERRIDE_EVENT_ID = "overrideEventId"
const val OVERRIDE_EVENT_NUMBER = "overrideEventNumber"
const val OVERRIDE_EVENT_TYPE = "overrideEventType"
const val TRANSFORMED_FROM_ID = "transformedFromJavaEventId"
const val TRANSFORMED_FROM_NUMBER = "transformedFromEventNumber"
const val TRANSFORMED_FROM_TYPE = "transformedFromEventType"

private data class TransformedEvents(
        val expectedStreamVersion: Long,
        val events: List<EventData>
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
        val (expectedStreamVersion, events) = transformEvents(streamId) { resolvedEvent, objectMapper ->
            val event = resolvedEvent.event
            val (data, meta) = if (resolvedEvent.originalEventNumber() in eventNumbers) {
                val newData = transformFn(event, objectMapper)
                newData to copyMetadata(event)
            } else {
                event.data to copyMetadata(event)
            }

            println("#${event.eventNumber}: ${meta?.toString(Charsets.UTF_8)} --> ${data?.toString(Charsets.UTF_8)}")
            data?.let {
                EventData.newBuilder()
                        .type(event.eventType)
                        .jsonData(it)
                        .jsonMetadata(meta)
                        .build()
            }

        }
        appendEvents(streamId, events, expectedStreamVersion)
        truncateBefore(expectedStreamVersion + 1, streamId)
    }

    fun copyAndReplace(streamId: String, transformFn: (event: ResolvedEvent, mapper: ObjectMapper) -> EventData?) {
        val (expectedStreamVersion, events) = transformEvents(streamId, transformFn)
        appendEvents(streamId, events, expectedStreamVersion)
        truncateBefore(expectedStreamVersion + 1, streamId)
    }

    fun copyMetadata(event: RecordedEvent, shouldPutOverrideIds: Boolean = true): ByteArray? {
        val metaData = event.metadata.takeIf { it.isNotEmpty() } ?: EMPTY_OBJECT_BYTE_ARRAY
        val newMeta = objectMapper.readTree(metaData).apply {
            this as ObjectNode

            if (shouldPutOverrideIds) {
                val previousIdOverride = path(OVERRIDE_EVENT_ID)?.textValue()
                val previousNumberOverride = path(OVERRIDE_EVENT_NUMBER)?.textValue()
                val previousNumberType = path(OVERRIDE_EVENT_TYPE)?.textValue()
                put(OVERRIDE_EVENT_ID, previousIdOverride ?: event.eventId.toString())
                put(OVERRIDE_EVENT_NUMBER, previousNumberOverride ?: event.eventNumber.toString())
                put(OVERRIDE_EVENT_TYPE, previousNumberType ?: event.eventType)

                put(TRANSFORMED_FROM_ID, event.eventId.toString())
                put(TRANSFORMED_FROM_NUMBER, event.eventNumber)
                put(TRANSFORMED_FROM_TYPE, event.eventType)
            }
        }

        return objectMapper.writeValueAsBytes(newMeta)
    }

    private fun transformEvents(streamId: String, transformFn: (event: ResolvedEvent, mapper: ObjectMapper) -> EventData?): TransformedEvents {
        val firstEventNumber = firstEventNumber(streamId)
        var expectedStreamVersion: Long = firstEventNumber
        val eventData = eventStore.iterateStreamEventsForward(streamId, firstEventNumber, bufferSize, true)
                .asSequence()
                .mapNotNull { resolvedEvent ->
                    expectedStreamVersion = resolvedEvent.event.eventNumber
                    transformFn(resolvedEvent, objectMapper)
                }.toList()
        return TransformedEvents(expectedStreamVersion, eventData)
    }

    private fun firstEventNumber(streamId: String) = eventStore.getStreamMetadata(streamId)
            .thenApply { it.streamMetadata.truncateBefore }
            .get()
            ?: 0

    private fun appendEvents(streamId: String, events: Collection<EventData>, initialExpectedVersion: Long) {
        eventStore.startTransaction(streamId, initialExpectedVersion).get().use { transaction ->
            events.chunked(writeChunkSize).forEach { events ->
                transaction.write(events).get()
            }
            transaction.commit().get()
        }
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

