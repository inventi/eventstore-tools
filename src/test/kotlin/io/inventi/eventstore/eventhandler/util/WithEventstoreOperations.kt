package io.inventi.eventstore.eventhandler.util

import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.ExpectedVersion
import io.inventi.eventstore.util.ObjectMapperFactory
import java.util.UUID

interface WithEventstoreOperations {
    val objectMapper get() = ObjectMapperFactory.createDefaultObjectMapper()

    var eventStore: EventStore

    fun appendEvent(event: Any, eventId: String? = null, metadata: Any? = null) {
        val eventData = EventData.newBuilder()
                .jsonMetadata(metadata?.let { objectMapper.writeValueAsBytes(it) })
                .jsonData(objectMapper.writeValueAsBytes(event))
                .eventId(eventId?.let { UUID.fromString(eventId) })
                .type(event::class.simpleName)
                .build()
        eventStore.appendToStream(DataBuilder.streamName, ExpectedVersion.ANY, eventData).join()
    }
}