package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.PersistentSubscription
import com.github.msemys.esjc.PersistentSubscriptionListener
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.RetryableResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.annotation.Retry
import io.inventi.eventstore.eventhandler.exception.EventAcknowledgementFailedException
import io.inventi.eventstore.eventhandler.exception.UnsupportedMethodException
import io.inventi.eventstore.eventhandler.model.EventIds
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import org.slf4j.Logger
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf


internal class IdempotentPersistentSubscriptionListener(
        private val handlerInstance: Any,
        private val streamName: String,
        private val groupName: String,
        private val eventStore: EventStore,
        private val saveEventId: (IdempotentEventClassifierRecord) -> Boolean,
        private val shouldSkip: (ResolvedEvent) -> Boolean,
        private val handlerExtensions: List<EventHandlerExtension>,
        private val transactionTemplate: TransactionTemplate,
        private val objectMapper: ObjectMapper,
        private val logger: Logger
) : PersistentSubscriptionListener {
    companion object {
        private val RECOVERABLE_SUBSCRIPTION_DROP_REASONS = setOf(
                SubscriptionDropReason.ConnectionClosed,
                SubscriptionDropReason.ServerError,
                SubscriptionDropReason.SubscribingError,
                SubscriptionDropReason.CatchUpError,
                SubscriptionDropReason.UserInitiated
        )
    }

    override fun onClose(subscription: PersistentSubscription?, reason: SubscriptionDropReason, exception: Exception?) {
        logger.warn("Subscription StreamName: $streamName GroupName: $groupName was closed. Reason: $reason", exception)
        when {
            exception is EventStoreException -> {
                logger.warn("Eventstore connection lost: ${exception.message}", exception)
                logger.warn("Reconnecting into StreamName: $streamName, GroupName: $groupName")
                eventStore.subscribeToPersistent(streamName, groupName, this)
            }
            RECOVERABLE_SUBSCRIPTION_DROP_REASONS.contains(reason) -> {
                logger.warn("Reconnecting into StreamName: $streamName, GroupName: $groupName", exception)
                eventStore.subscribeToPersistent(streamName, groupName, this)
            }
            exception != null -> {
                throw RuntimeException(exception)
            }
        }
    }

    override fun onEvent(subscription: PersistentSubscription, eventMessage: RetryableResolvedEvent) {
        if (eventMessage.event == null) {
            logger.warn("Skipping eventMessage with empty event. Linked eventId: ${eventMessage.link.eventId}")
            subscription.acknowledgeProcessedEvent(eventMessage)
            return
        }

        transactionTemplate.execute {
            val event = eventMessage.event
            logger.trace("Received event '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")

            val idempotentEventRecord = IdempotentEventClassifierRecord(
                    eventId = event.effectiveEventId,
                    eventType = event.eventType,
                    streamName = streamName,
                    eventStreamId = event.eventStreamId,
                    groupName = groupName
            )

            if (saveEventId(idempotentEventRecord)) {
                val eventHandlerMethods = handlerInstance::class.java
                        .methods
                        .filter { it.isEventHandlerFor(event) }

                if (!shouldSkip(eventMessage)) {
                    eventHandlerMethods
                            .forEach { handleMethodWithHooks(it, event) }
                } else {
                    eventHandlerMethods
                            .filter { !it.getAnnotation(EventHandler::class.java).skipWhenReplaying }
                            .forEach { handleMethodWithHooks(it, event) }
                }
            } else {
                logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
            }
        }
        subscription.acknowledgeProcessedEvent(eventMessage)
    }

    private fun handleMethodWithHooks(method: Method, event: RecordedEvent) {
        val extensionCleanups = handlerExtensions
                .map { it.handle(method, event) }
                .asReversed()

        try {
            handleMethodWithPossibleRetry(method, event)
        } catch (error: Throwable) {
            extensionCleanups.forEach { cleanup -> cleanup(error) }
            throw error
        }
        extensionCleanups.forEach { cleanup -> cleanup(null) }
    }

    private fun handleMethodWithPossibleRetry(method: Method, event: RecordedEvent) {
        val retryAnnotations = method.getAnnotationsByType(Retry::class.java)

        if (retryAnnotations.isNotEmpty()) {
            handleMethodWithRetry(method, event, retryAnnotations.first())
        } else {
            handleMethod(method, event)
        }
    }

    private fun handleMethodWithRetry(method: Method, event: RecordedEvent, retryAnnotation: Retry) {
        val retryableExceptions = retryAnnotation.exceptions
        val maxAttempts = retryAnnotation.maxAttempts
        val backoffDelayMillis = retryAnnotation.backoffDelayMillis

        var caughtRetryableException: Exception? = null

        for (attempt in (maxAttempts downTo 1)) {
            try {
                return handleMethod(method, event)
            } catch (e: Exception) {
                if (retryableExceptions.any { e.isAOrIsCausedBy(it) }) {
                    caughtRetryableException = e
                    logger.debug("Caught retryable exception, will retry in $backoffDelayMillis ms: $caughtRetryableException")
                    Thread.sleep(backoffDelayMillis)
                    continue
                }
                throw RuntimeException(e)
            }
        }

        if (caughtRetryableException != null)
            if (caughtRetryableException is RuntimeException)
                throw caughtRetryableException
        throw RuntimeException(caughtRetryableException)
    }

    private fun handleMethod(method: Method, event: RecordedEvent) {
        try {
            if (method.parameterTypes.isNullOrEmpty() || method.parameterTypes.size > 3) {
                throw UnsupportedMethodException("Method must have 1-3 parameters")
            }

            val eventDataFirstParam = deserialize(method.parameterTypes[0], event.data)
            if (method.parameterTypes.size == 1) {
                method.invoke(handlerInstance, eventDataFirstParam)
                return
            } else if (method.parameterTypes.size == 2) {
                val secondParam: Any = event.toEventIdsOrMetadata(method, 1)
                method.invoke(handlerInstance, eventDataFirstParam, secondParam)
                return
            } else {
                val secondParam: Any = event.toEventIdsOrMetadata(method, 1)
                val thirdParam: Any = event.toEventIdsOrMetadata(method, 2)
                method.invoke(handlerInstance, eventDataFirstParam, secondParam, thirdParam)
                return
            }
        } catch (e: Exception) {
            logger.error("Failure on method invocation ${method.name}: " +
                    "eventId: ${event.eventId}, " +
                    "eventType: ${event.eventType}, " +
                    "streamName: $streamName, " +
                    "eventStreamId: ${event.eventStreamId}, " +
                    "groupName: $groupName, " +
                    "eventData: \n${String(event.data)}",
                    e)
            when (e) {
                is InvocationTargetException -> throw e.targetException
                else -> throw e
            }
        }
    }

    private fun RecordedEvent.toEventIdsOrMetadata(method: Method, parameterNumber: Int) =
            if (method.parameters[parameterNumber].type.simpleName == EventIds::class.java.simpleName) {
                EventIds(originalId = originalEventIdOrNull, effectiveId = effectiveEventId)
            } else {
                deserialize(method.parameterTypes[parameterNumber], metadata)
            }

    private fun deserialize(type: Class<*>, data: ByteArray): Any {
        return objectMapper.readValue(data, type)
    }

    private val RecordedEvent.effectiveEventId: String
        get() {
            return originalEventIdOrNull ?: eventId.toString()
        }

    private val RecordedEvent.originalEventIdOrNull: String?
        get() {
            return objectMapper.runCatching {
                readTree(metadata).path(IdempotentEventHandler.OVERRIDE_EVENT_ID).textValue()
            }.getOrNull()
        }

    private fun Exception.isAOrIsCausedBy(matchClass: KClass<*>): Boolean {
        var exception: Throwable? = this
        while (exception != null) {
            if (exception::class.isSubclassOf(matchClass)) {
                return true
            }
            exception = exception.cause
        }

        return false
    }

    private fun Method.isEventHandlerFor(event: RecordedEvent): Boolean {
        return this.isAnnotationPresent(EventHandler::class.java)
                && this.parameters[0].type.simpleName == event.eventType
    }

    private fun PersistentSubscription.acknowledgeProcessedEvent(event: ResolvedEvent) {
        try {
            this.acknowledge(event)
        } catch (e: Exception) {
            throw EventAcknowledgementFailedException("Could not acknowledge event with id: ${event.originalEvent().eventId}", e)
        }
    }
}
