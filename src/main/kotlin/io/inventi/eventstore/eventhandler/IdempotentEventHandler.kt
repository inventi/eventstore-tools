package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.PersistentSubscription
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionListener
import com.github.msemys.esjc.PersistentSubscriptionSettings
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.RetryableResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import com.github.msemys.esjc.system.SystemConsumerStrategy
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.exception.UnsupportedMethodException
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.eventhandler.model.MethodParametersType
import io.inventi.eventstore.util.LoggerDelegate
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.util.concurrent.CompletionException


abstract class IdempotentEventHandler(
        private val streamName: String,
        private val groupName: String
) : SmartLifecycle {
    companion object {
        private val logger by LoggerDelegate()
    }

    @field:Value("\${eventstore.idempotent-event-classifier.table-name}")
    lateinit var tableName: String

    @Autowired
    private lateinit var idempotentEventClassifierDao: IdempotentEventClassifierDao

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate


    var running: Boolean = false

    override fun start() {
        ensureSubscription()

        val persistentSubscription = object : PersistentSubscriptionListener {

            override fun onClose(subscription: PersistentSubscription?, reason: SubscriptionDropReason?, exception: Exception) {
                if (exception is EventStoreException) {
                    logger.warn("Eventstore connection lost: ${exception.message}")
                    logger.warn("Reconnecting into StreamName: $streamName, GroupName: $groupName")
                    eventStore.subscribeToPersistent(streamName, groupName, this)
                } else {
                    throw exception
                }
            }

            override fun onEvent(subscription: PersistentSubscription, eventMessage: RetryableResolvedEvent) {
                transactionTemplate.execute {
                    val event = eventMessage.event
                    logger.trace("Received event '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")

                    try {
                        val idempotentEventRecord = IdempotentEventClassifierRecord(
                                eventId = event.eventId.toString(),
                                eventType = event.eventType,
                                streamName = streamName,
                                groupName = groupName
                        )
                        saveEventId(idempotentEventRecord)
                    } catch (e: UnableToExecuteStatementException) {
                        if ("Duplicate entry" in (e.message ?: "") || "duplicate key" in (e.message ?: "")) {
                            logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
                            subscription.acknowledge(event.eventId)
                            return@execute
                        }
                        throw e
                    }

                    this@IdempotentEventHandler::class.java
                            .methods.filter { it.isAnnotationPresent(EventHandler::class.java) }
                            .forEach { handleMethod(it, event) }
                    subscription.acknowledge(event.eventId)
                }
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
                        method.invoke(this@IdempotentEventHandler, eventData, metadata)
                        return
                    }
                    method.invoke(this@IdempotentEventHandler, eventData)

                } catch (e: Exception) {
                    logger.error("Failure on method invocation. eventRecord: $event streamName: $streamName groupName: $groupName")
                    throw e
                }
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

        eventStore.subscribeToPersistent(streamName, groupName, persistentSubscription)
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