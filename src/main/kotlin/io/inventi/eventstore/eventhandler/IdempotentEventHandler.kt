package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreException
import com.github.msemys.esjc.PersistentSubscription
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionListener
import com.github.msemys.esjc.PersistentSubscriptionSettings
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.RetryableResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import com.github.msemys.esjc.system.SystemConsumerStrategy
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.exception.UnsupportedMethodException
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.eventhandler.model.MethodParametersType
import io.inventi.eventstore.util.LoggerDelegate
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.util.concurrent.CompletionException


@ConditionalOnSubscriptionsEnabled
abstract class IdempotentEventHandler(
        val streamName: String,
        val groupName: String,
        private val initialPosition: InitialPosition = InitialPosition.FromBeginning()
) : SmartLifecycle {
    companion object {
        private val RECOVERABLE_SUBSCRIPTION_DROP_REASONS = setOf(
                SubscriptionDropReason.ConnectionClosed,
                SubscriptionDropReason.ServerError,
                SubscriptionDropReason.SubscribingError,
                SubscriptionDropReason.CatchUpError
        )
    }


    private val logger by LoggerDelegate()

    @field:Value("\${spring.flyway.placeholders.idempotency}")
    lateinit var tableName: String

    @Autowired
    private lateinit var idempotentEventClassifierDao: IdempotentEventClassifierDao

    @Autowired
    protected lateinit var eventStore: EventStore

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private var subscriptionProperties: SubscriptionProperties = SubscriptionProperties()

    @Autowired
    @Suppress("UNUSED")
    // We should make sure we start AFTER flyway has done it's job
    private lateinit var flyway: Flyway

    private var running: Boolean = false

    private val firstEventNumberToHandle: Long by lazy {
        initialPosition.getFirstEventNumberToHandle(eventStore, objectMapper)
    }

    constructor(
            streamName: String,
            groupName: String,
            idempotentEventClassifierDao: IdempotentEventClassifierDao,
            eventStore: EventStore,
            objectMapper: ObjectMapper,
            transactionTemplate: TransactionTemplate
    ) : this(streamName, groupName) {

        this.idempotentEventClassifierDao = idempotentEventClassifierDao
        this.eventStore = eventStore
        this.objectMapper = objectMapper
        this.transactionTemplate = transactionTemplate
    }

    override fun start() {
        ensureSubscription()


        val persistentSubscription = object : PersistentSubscriptionListener {

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

                    try {
                        val idempotentEventRecord = IdempotentEventClassifierRecord(
                                eventId = event.eventId.toString(),
                                eventType = event.eventType,
                                streamName = streamName,
                                eventStreamId = event.eventStreamId,
                                groupName = groupName
                        )
                        saveEventId(idempotentEventRecord)
                    } catch (e: UnableToExecuteStatementException) {
                        if ("Duplicate entry" in (e.message ?: "") || "duplicate key" in (e.message ?: "")) {
                            logger.warn("Event already handled '${event.eventType}': ${String(event.metadata)}; ${String(event.data)}")
                            subscription.acknowledge(eventMessage)
                            return@execute
                        }
                        throw RuntimeException(e)
                    }

                    val eventHandlerMethods = this@IdempotentEventHandler::class.java
                            .methods.filter { it.isAnnotationPresent(EventHandler::class.java) }

                    if (!shouldSkip(eventMessage, firstEventNumberToHandle)) {
                        eventHandlerMethods
                                .forEach { handleMethod(it, event) }
                    } else {
                        eventHandlerMethods
                                .filter { !it.getAnnotation(EventHandler::class.java).skipWhenReplaying }
                                .forEach { handleMethod(it, event) }
                    }
                    subscription.acknowledge(eventMessage)
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
                    logger.error("Failure on method invocation ${method.name}: " +
                            "eventId: ${event.eventId}, " +
                            "eventType: ${event.eventType}, " +
                            "streamName: $streamName, " +
                            "eventStreamId: ${event.eventStreamId}, " +
                            "groupName: $groupName, " +
                            "eventData: \n${String(event.data)}",
                            e)
                    throw RuntimeException(e)
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

    private fun shouldSkip(resolvedEvent: ResolvedEvent, firstEventNumberToHandle: Long): Boolean {
        val eventNumber = resolvedEvent.link?.eventNumber ?: resolvedEvent.event.eventNumber
        return eventNumber < firstEventNumberToHandle
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
            createSubscription(subject, settings)
        } catch (e: CompletionException) {
            if ("already exists" in (e.cause?.message.orEmpty())) {
                updateSubscription(subject, settings)
            } else {
                logger.error("Error when ensuring $subject", e)
                throw e
            }
        }
    }

    private fun createSubscription(subject: String, settings: PersistentSubscriptionSettings?) {
        logger.info("Ensuring $subject")
        val status = eventStore
                .createPersistentSubscription(streamName, groupName, settings)
                .join().status
        if (status == PersistentSubscriptionCreateStatus.Failure) {
            throw IllegalStateException("Failed to ensure PersistentSubscription")
        }
    }

    private fun updateSubscription(subject: String, settings: PersistentSubscriptionSettings?) {
        if (subscriptionProperties.updateEnabled) {
            logger.info("$subject already exists. Updating")
            eventStore
                    .updatePersistentSubscription(streamName, groupName, settings)
                    .join()
        } else {
            logger.info("$subject already exists. Updates disabled, doing nothing")
        }
    }
}