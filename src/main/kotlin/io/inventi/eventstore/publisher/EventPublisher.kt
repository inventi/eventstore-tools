package io.inventi.eventstore.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.ExpectedVersion
import io.inventi.eventstore.util.LoggerDelegate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Serializes and appends given object to configured stream in EventStore.
 */
@Service
@ConditionalOnProperty("eventstore.eventPublisher.enabled", matchIfMissing = false)
class EventPublisher {

    private val logger by LoggerDelegate()

    @Value("\${eventstore.eventPublisher.streamName}")
    private lateinit var streamName: String

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var eventStore: EventStore

    private val pool = Executors.newFixedThreadPool(2)

    fun <T> publish(eventType: String, eventDataSupplier: () -> T) : Future<*> {
        return pool.submit {
            addEventToStream(eventType, eventDataSupplier())
        }
    }

    fun <T> publish(eventType: String, eventData: T) : Future<*> {
        return pool.submit {
            addEventToStream(eventType, eventData)
        }
    }

    private fun <T> addEventToStream(eventType: String, eventData: T) {
        try {
            eventStore.appendToStream(
                    streamName,
                    ExpectedVersion.ANY,
                    EventData.newBuilder()
                            .type(eventType)
                            .data(objectMapper.writeValueAsString(eventData))
                            .build())
        } catch (e: Exception) {
            logger.error("Failed to publish '$eventType' to '$streamName' with eventData '$eventData'", e)
            throw e;
        }
    }
}