package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType.EVENTSTORE_CLIENT_ERROR
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType.UNEXPECTED_ERROR
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.exception.UnsupportedMethodException
import io.inventi.eventstore.eventhandler.feature.CompositeFeature
import io.inventi.eventstore.eventhandler.feature.EventListenerFeature
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.eventhandler.util.overriddenEventIdOrNull
import io.inventi.eventstore.eventhandler.util.withRetries
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


private val RECOVERABLE_SUBSCRIPTION_DROP_REASONS = setOf(
        SubscriptionDropReason.ConnectionClosed,
        SubscriptionDropReason.ServerError,
        SubscriptionDropReason.SubscribingError,
        SubscriptionDropReason.UserInitiated,
        SubscriptionDropReason.ProcessingQueueOverflow,
        SubscriptionDropReason.Unknown,
)

class EventstoreEventListener(
        private val eventHandler: EventstoreEventHandler,
        private val firstEventNumberToHandle: Long,
        private val objectMapper: ObjectMapper,
        private vararg val features: EventListenerFeature,
) {
    enum class FailureType { EVENTSTORE_CLIENT_ERROR, UNEXPECTED_ERROR }

    private val handlerMethods = eventHandler::class.java.methods
    private val logger = LoggerFactory.getLogger(eventHandler::class.java)

    fun onEvent(resolvedEvent: ResolvedEvent) {
        val event = resolvedEvent.event
        if (event == null) {
            logger.debug("Skipping empty link event. EventId: ${resolvedEvent.link.eventId}")
            return
        }
        logger.trace("Received event '${event.eventType}', eventId: ${event.eventId}, eventStreamId: ${event.eventStreamId}")

        CompositeFeature(*features).wrap(event, resolvedEvent.originalEventNumber()) {
            handlerMethods
                    .filter { it.isEventHandlerFor(event) }
                    .filter { !it.shouldSkipFor(resolvedEvent) }
                    .forEach { it.handle(event) }
        }
    }

    fun onClose(reason: SubscriptionDropReason, exception: Exception?): FailureType {
        logger.error("Subscription for event handler: ${eventHandler::class.simpleName} was closed. Reason: $reason", exception)
        return if (exception is EventStoreException || RECOVERABLE_SUBSCRIPTION_DROP_REASONS.contains(reason)) {
            EVENTSTORE_CLIENT_ERROR
        } else {
            UNEXPECTED_ERROR
        }
    }

    private fun Method.handle(event: RecordedEvent) {
        val extensionCleanups = eventHandler.extensions
                .map { it.handle(this, event) }
                .asReversed()

        try {
            withRetries(logger) { invokeWithParams(event) }
        } catch (error: Throwable) {
            extensionCleanups.forEach { cleanup -> cleanup(error) }
            throw error
        }
        extensionCleanups.forEach { cleanup -> cleanup(null) }
    }

    private fun Method.invokeWithParams(event: RecordedEvent) {
        try {
            if (parameterTypes.isNullOrEmpty() || parameterTypes.size > 3) {
                throw UnsupportedMethodException("Method must have 1-3 parameters")
            }

            val eventDataFirstParam = deserialize(parameterTypes[0], event.data)
            when (parameterTypes.size) {
                1 -> {
                    invoke(eventHandler, eventDataFirstParam)
                    return
                }
                2 -> {
                    val secondParam: Any = event.toEventIdsOrMetadata(parameterTypes[1])
                    invoke(eventHandler, eventDataFirstParam, secondParam)
                    return
                }
                else -> {
                    val secondParam: Any = event.toEventIdsOrMetadata(parameterTypes[1])
                    val thirdParam: Any = event.toEventIdsOrMetadata(parameterTypes[2])
                    invoke(eventHandler, eventDataFirstParam, secondParam, thirdParam)
                    return
                }
            }
        } catch (e: Exception) {
            logger.error("Failure on method invocation $name: " +
                    "eventId: ${event.eventId}, " +
                    "eventType: ${event.eventType}, " +
                    "eventStreamId: ${event.eventStreamId}, " +
                    "handler: ${eventHandler::class.simpleName}, " +
                    "eventData: \n${String(event.data)}",
                    e
            )
            when (e) {
                is InvocationTargetException -> throw e.targetException
                else -> throw e
            }
        }
    }

    private fun RecordedEvent.toEventIdsOrMetadata(parameterType: Class<*>) =
            if (parameterType.isAssignableFrom(EventIds::class.java)) {
                EventIds(overridden = overriddenEventIdOrNull(objectMapper), current = eventId.toString())
            } else {
                deserialize(parameterType, metadata)
            }

    private fun deserialize(type: Class<*>, data: ByteArray): Any {
        return objectMapper.readValue(data, type)
    }

    private fun Method.isEventHandlerFor(event: RecordedEvent): Boolean {
        return this.isAnnotationPresent(EventHandler::class.java)
                && this.parameters[0].type.simpleName == event.eventType
    }

    private fun Method.shouldSkipFor(resolvedEvent: ResolvedEvent): Boolean {
        val eventNumber = resolvedEvent.link?.eventNumber ?: resolvedEvent.event.eventNumber
        return eventNumber < firstEventNumberToHandle && markedForSkipping()
    }

    private fun Method.markedForSkipping() = getAnnotation(EventHandler::class.java).skipWhenReplaying
}