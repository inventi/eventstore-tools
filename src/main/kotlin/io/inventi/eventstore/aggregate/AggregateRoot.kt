package io.inventi.eventstore.aggregate

import com.github.msemys.esjc.ExpectedVersion
import io.inventi.eventstore.util.LoggerDelegate
import io.inventi.eventstore.util.findMethods
import io.inventi.eventstore.util.thisOrSuperClass
import java.lang.reflect.Method
import java.time.Instant

private const val EVENT_HANDLER_FUNCTION = "apply"

abstract class AggregateRoot(val id: String) {
    companion object {
        @JvmStatic
        val logger by LoggerDelegate()
    }

    private val uncommittedEvents = mutableListOf<EventMessage>()
    protected var lastCommittedEventId: Long = ExpectedVersion.NO_STREAM
    protected var loadingFromHistory = false
        private set

    val expectedVersion: Long
        get() = lastCommittedEventId


    fun loadFromHistory(events: Iterable<EventMessage>) {
        loadingFromHistory = true
        events.forEach(this::apply)
        loadingFromHistory = false
    }

    fun appendEvent(event: Event) {
        appendEvent(event, emptyMap())
    }

    fun appendEvent(event: Event, metadata: Map<String, Any?>) {
        val eventMessage = EventMessage(event, Instant.now(), lastCommittedEventId, metadata)
        uncommittedEvents.add(eventMessage)
        apply(eventMessage)
    }

    fun Iterable<Event?>.appendNonNull() {
        filterNotNull().forEach(::appendEvent)
    }

    fun commitChangesTo(repository: Repository<*>) = commitChangesTo(repository, lastCommittedEventId)

    fun commitChangesTo(repository: Repository<*>, expectedVersion: Long) {
        if (uncommittedEvents.isNotEmpty()) {
            repository.save(id, uncommittedEvents, expectedVersion)
            lastCommittedEventId += uncommittedEvents.size
            uncommittedEvents.clear()
        }
    }

    fun getUncommittedEvents(): List<EventMessage> {
        return uncommittedEvents
    }

    private fun apply(eventMessage: EventMessage) {
        if (eventMessage.eventNumber >= 0) lastCommittedEventId = eventMessage.eventNumber

        findMatchingMethods(eventMessage).forEach {
            it.invoke(this, eventMessage.event)
        }
    }

    private fun findMatchingMethods(event: EventMessage): List<Method> {
        val eventType = event.event.javaClass.thisOrSuperClass()
        val handlerMethods = this.javaClass.findMethods(EVENT_HANDLER_FUNCTION)

        return handlerMethods.filter { method ->
            val methodParameterTypes = method.parameterTypes
            if (methodParameterTypes.isEmpty()) {
                logger.warn("Found apply method {} with no arguments in {}!", method, this.javaClass)
                false
            } else {
                methodParameterTypes.first().isAssignableFrom(eventType)
            }
        }
    }
}
