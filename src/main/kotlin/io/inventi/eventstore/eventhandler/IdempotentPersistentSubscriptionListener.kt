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
import io.inventi.eventstore.eventhandler.exception.UnsupportedMethodException
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.eventhandler.model.MethodParametersType
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
                SubscriptionDropReason.CatchUpError
        )
    }

    override fun onClose(subscription: PersistentSubscription?, reason: SubscriptionDropReason, exception: Exception?) {
        logger.warn("Subscription StreamName: $streamName GroupName: $groupName was closed. Reason: $reason", exception)
        when {
            exception is EventStoreException -> {
                logger.warn("Eventstore connection lost: ${exception.message}")
                logger.warn("Reconnecting into StreamName: $streamName, GroupName: $groupName")
                eventStore.subscribeToPersistent(streamName, groupName, this)
            }
            RECOVERABLE_SUBSCRIPTION_DROP_REASONS.contains(reason) -> {
                logger.warn("Reconnecting into StreamName: $streamName, GroupName: $groupName")
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
            subscription.acknowledge(eventMessage)
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
            if (!saveEventId(idempotentEventRecord)) {
                logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
                subscription.acknowledge(eventMessage)
                return@execute
            }

            val eventHandlerMethods = handlerInstance::class.java
                    .methods.filter { it.isAnnotationPresent(EventHandler::class.java) }

            if (!shouldSkip(eventMessage)) {
                eventHandlerMethods
                        .forEach { handleMethodWithHooks(it, event) }
            } else {
                eventHandlerMethods
                        .filter { !it.getAnnotation(EventHandler::class.java).skipWhenReplaying }
                        .forEach { handleMethodWithHooks(it, event) }
            }
            subscription.acknowledge(eventMessage)
        }
    }

    private fun handleMethodWithHooks(method: Method, event: RecordedEvent) {
        beforeHandle(method, event)
        try {
            handleMethodWithPossibleRetry(method, event)
        } finally {
            afterHandle(method, event)
        }
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
        if (method.parameters[0].type.simpleName != event.eventType) {
            return
        }
        try {
            val methodParametersType = extractMethodParameterTypes(method)

            val eventData = deserialize(methodParametersType.dataType, event.data)
            if (methodParametersType.metadataType != null) {
                val metadata = deserialize(methodParametersType.metadataType, event.metadata)
                method.invoke(handlerInstance, eventData, metadata)
                return
            }
            method.invoke(handlerInstance, eventData)

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

    private fun beforeHandle(method: Method, event: RecordedEvent) {
        handlerExtensions.forEach { it.beforeHandle(method, event) }
    }

    private fun afterHandle(method: Method, event: RecordedEvent) {
        handlerExtensions.asReversed().forEach { it.afterHandle(method, event) }
    }

    private fun deserialize(type: Class<*>, data: ByteArray): Any {
        return objectMapper.readValue(data, type)
    }

    private fun extractMethodParameterTypes(it: Method): MethodParametersType {
        if (it.parameterTypes.isNullOrEmpty() || it.parameterTypes.size > 2) {
            throw UnsupportedMethodException("Method must have 1-2 parameters")
        }

        val eventType = it.parameterTypes[0]
        val metadataType = it.parameterTypes.getOrNull(1)

        return MethodParametersType(eventType, metadataType)
    }

    private val RecordedEvent.effectiveEventId: String
        get() {
            val originalEventId = objectMapper.runCatching {
                readTree(metadata).path(IdempotentEventHandler.OVERRIDE_EVENT_ID).textValue()
            }.getOrNull()

            return originalEventId ?: eventId.toString()
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
}


