package io.inventi.eventstore.projector

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscription
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionListener
import com.github.msemys.esjc.PersistentSubscriptionSettings
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.RetryableResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import com.github.msemys.esjc.system.SystemConsumerStrategy
import io.inventi.eventstore.projector.annotation.EventHandler
import io.inventi.eventstore.projector.exception.UnsupportedMethodException
import io.inventi.eventstore.projector.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.projector.model.MethodParametersType
import io.inventi.eventstore.util.LoggerDelegate
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.springframework.context.SmartLifecycle
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Method
import java.util.concurrent.CompletionException


abstract class IdempotentEventHandler(
        private val eventStore: EventStore,
        private val idempotentEventClassifierDao: IdempotentEventClassifierDao,
        private val streamName: String,
        private val groupName: String,
        private val tableName: String,
        private val objectMapper: ObjectMapper
) : SmartLifecycle {
    companion object {
        private val logger by LoggerDelegate()
    }

    var running: Boolean = false

    override fun start() {
        ensureSubscription()

        val value = object : PersistentSubscriptionListener {

            override fun onClose(subscription: PersistentSubscription?, reason: SubscriptionDropReason?, exception: Exception?) {
                if (exception != null) {
                    throw exception
                }

                TODO("implement on close")
            }

            @Transactional
            override fun onEvent(subscription: PersistentSubscription, eventMessage: RetryableResolvedEvent) {

                val event = eventMessage.event
                logger.trace("Received event '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")

                try {
                    val idempotentEventRecord = IdempotentEventClassifierRecord(
                            id = event.eventId.toString(),
                            eventType = event.eventType,
                            streamName = streamName,
                            groupName = groupName
                    )
                    saveEventId(idempotentEventRecord)
                } catch (e: UnableToExecuteStatementException) {
                    if ("Duplicate entry" in (e.message ?: "") || "duplicate key" in (e.message ?: "")) {
                        logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
                        subscription.acknowledge(event.eventId)
                        return
                    }
                    throw e
                }

                this@IdempotentEventHandler::class.java
                        .methods.filter { it.isAnnotationPresent(EventHandler::class.java) }
                        .forEach { hanleMethod(it, event) }
                subscription.acknowledge(event.eventId)
            }

            private fun hanleMethod(method: Method, event: RecordedEvent) {
                if (method.parameters[0].type.simpleName != event.eventType) {
                    return
                }

                val methodParametersType = extractMethodParameterTypes(method)

                val eventData = deserialize(methodParametersType.dataType, event.data)
                if (methodParametersType.metadataType != null) {
                    val metadata = deserialize(methodParametersType.metadataType, event.metadata)
                    method.invoke(this@IdempotentEventHandler, eventData, metadata)
                    return
                }

                method.invoke(this@IdempotentEventHandler, eventData)

            }

            private fun saveEventId(idempotentEventClassifierRecord: IdempotentEventClassifierRecord) {
                idempotentEventClassifierDao.insert(tableName, idempotentEventClassifierRecord)
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
        }

        eventStore.subscribeToPersistent(streamName, groupName, value)
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean {
        return running
    }

    private fun ensureSubscription() {
        val settings = PersistentSubscriptionSettings.newBuilder()
                .resolveLinkTos(true)
                .startFromBeginning()
                .namedConsumerStrategy(SystemConsumerStrategy.PINNED)
                .minCheckPointCount(1)
                .build()

        val subject = "Persistent stream for '$streamName' with groupName '$groupName'"

        try {
            logger.info("Ensuring Persistent stream for $streamName with groupName $groupName")
            val status = eventStore
                    .createPersistentSubscription(streamName, groupName, settings)
                    .join().status
            if (status == PersistentSubscriptionCreateStatus.Failure) {
                throw IllegalStateException("Failed to ensure PersistentSubscription")
            }
        } catch (e: CompletionException) {
            if ("already exists" in (e.cause?.message ?: "")) {
                logger.info("$subject already exists. Updating")
                eventStore
                        .updatePersistentSubscription(streamName, groupName, settings)
                        .join()
            } else {
                logger.error("Error when ensuring $subject", e)
                throw e
            }
        }
    }


}