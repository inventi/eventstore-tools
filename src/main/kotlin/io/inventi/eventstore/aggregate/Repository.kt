package io.inventi.eventstore.aggregate

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.operation.StreamNotFoundException
import io.inventi.eventstore.util.LoggerDelegate
import io.inventi.eventstore.util.ObjectMapperFactory
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import java.security.AccessController
import java.security.PrivilegedExceptionAction
import java.time.Instant
import java.util.concurrent.CompletionException
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

data class EventMessage(val event: Event, val timestamp: Instant, val eventNumber: Long = -2, val metadata: Map<String, Any?> = emptyMap())

class AggregateRootIsBehindStreamException(message: String, e: Throwable?) : Exception(message, e)

interface AggregateRootContext

interface Repository<T> {
    fun save(aggregateId: String, events: Iterable<EventMessage>, expectedVersion: Long)
    fun save(aggregateRoot: T)
    fun findOne(aggregateId: String): T?
}

abstract class PersistentRepository<T>(
        eventPackage: String,
        protected val objectMapper: ObjectMapper = ObjectMapperFactory.createDefaultObjectMapper(),
        meterRegistry: MeterRegistry? = null,
        private val eventLoadBatchSize: Int = DEFAULT_EVENT_LOAD_BATCH_SIZE,
) : Repository<T> where T : AggregateRoot {
    protected val logger: Logger by LoggerDelegate()

    protected abstract val eventStore: EventStore
    protected abstract val aggregateType: KClass<T>
    protected abstract val metadataSource: MetadataSource

    protected open val context: AggregateRootContext? = null

    private val eventDeserializer: JsonEventDeserializer = WarmedUpJacksonEventDeserializer(objectMapper, eventPackage)

    companion object {
        private const val STREAM_START_EVENT_NUMBER = 0L
        private const val DEFAULT_EVENT_LOAD_BATCH_SIZE = 4096
    }

    private val findOneTimer: Timer? by lazy {
        meterRegistry?.let {
            Timer.builder("eventstore-tools.aggregate.rehydration")
                    .tag("aggregateClass", aggregateType.qualifiedName!!)
                    .tag("repositoryClass", this::class.qualifiedName!!)
                    .publishPercentileHistogram()
                    .description("Time it takes to rehydrate (load) an aggregate root")
                    .register(it)
        }
    }

    @Throws(AggregateRootIsBehindStreamException::class)
    override fun save(aggregateRoot: T) {
        val events = aggregateRoot.getUncommittedEvents()
        val version = aggregateRoot.expectedVersion
        save(aggregateRoot.id, events, version)
    }

    @Throws(AggregateRootIsBehindStreamException::class)
    override fun save(aggregateId: String, events: Iterable<EventMessage>, expectedVersion: Long) {
        val defaultMetadata = getDefaultMetadata(aggregateId)

        val data = events.map { message ->
            val eventMetadata = mergeMetadata(message, defaultMetadata)

            val metadataJson = objectMapper.writeValueAsBytes(eventMetadata)
            EventData.newBuilder()
                    .jsonMetadata(metadataJson)
                    .jsonData(objectMapper.writeValueAsBytes(message.event))
                    .type(message.event.javaClass.simpleName)
                    .build()
        }

        try {
            eventStore.appendToStream(streamName(aggregateId), expectedVersion, data).join()
        } catch (e: CompletionException) {
            if (e.cause != null && e.cause is com.github.msemys.esjc.operation.WrongExpectedVersionException) {
                throw AggregateRootIsBehindStreamException(e.message ?: "", e)
            }
            throw e
        }
    }

    override fun findOne(aggregateId: String) = findOneTimer
            ?.let { it.recordCallable { findAndMeasure(aggregateId) } }
            ?: findAndMeasure(aggregateId)

    @OptIn(ExperimentalTime::class)
    private fun findAndMeasure(aggregateId: String): T? {
        val (aggregate: T?, duration) = measureTimedValue {
            try {
                constructAggregateRoot(aggregateId)
            } catch (e: StreamNotFoundException) {
                null
            }
        }
        logger.debug("Rehydrated ${aggregateType.simpleName}(id=$aggregateId) in $duration")
        return aggregate
    }

    protected fun loadEvents(aggregateId: String, firstEvent: Long? = null): Iterable<EventMessage> {
        val streamName = streamName(aggregateId)
        val firstEventNumber = firstEvent
                ?: eventStore.getStreamMetadata(streamName)
                        .thenApply { it.streamMetadata.truncateBefore }
                        .get()
                ?: STREAM_START_EVENT_NUMBER

        return eventStore.iterateStreamEventsForward(streamName, firstEventNumber, eventLoadBatchSize, false)
                .asSequence()
                .map { resolvedEvent ->
                    EventMessage(
                            eventDeserializer.deserialize(resolvedEvent.event.eventType, resolvedEvent.event.data),
                            resolvedEvent.event.created,
                            resolvedEvent.event.eventNumber
                    )
                }.asIterable()
    }

    protected open fun constructAggregateRoot(aggregateId: String): T {
        val events = loadEvents(aggregateId)
        val aggregateRoot: T = instantiate(aggregateId)
        aggregateRoot.loadFromHistory(events)
        return aggregateRoot
    }

    protected fun instantiate(aggregateId: String): T = AccessController.doPrivileged(PrivilegedExceptionAction {
        val clazz: Class<T> = aggregateType.java
        if (context != null) {
            val constructor = clazz.getDeclaredConstructor(String::class.java, context!!::class.java)
            constructor.isAccessible = true
            constructor.newInstance(aggregateId, context)
        } else {
            val constructor = clazz.getDeclaredConstructor(String::class.java)
            constructor.isAccessible = true
            constructor.newInstance(aggregateId)
        }
    })

    private fun streamName(aggregateId: String) = aggregateType.simpleName + "-" + normalize(aggregateId)

    protected fun getDefaultMetadata(aggregateId: String): Map<String, Any?> {
        return metadataSource.get(aggregateId)
    }

    private fun mergeMetadata(message: EventMessage, defaultMetadata: Map<String, Any?>): Map<String, Any?> {
        return (message.metadata
                .takeIf { it.isNotEmpty() }
                ?.let { defaultMetadata + it }
                ?: defaultMetadata)
    }

    private fun normalize(id: String) = id.replace("/", "__slash__")
}
