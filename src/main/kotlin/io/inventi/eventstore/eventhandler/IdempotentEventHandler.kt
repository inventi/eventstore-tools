package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionSettings
import com.github.msemys.esjc.RecordedEvent
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason
import com.github.msemys.esjc.system.SystemConsumerStrategy
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.model.IdempotentEventClassifierRecord
import io.inventi.eventstore.util.LoggerDelegate
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.util.concurrent.CompletionException


@Component
@ConditionalOnSubscriptionsEnabled
abstract class IdempotentEventHandler(
        val streamName: String,
        val groupName: String,
        private val initialPosition: InitialPosition = InitialPosition.FromBeginning(),
        private val handlerExtensions: List<EventHandlerExtension> = emptyList()
) : SmartLifecycle {
    companion object {
        private val RECOVERABLE_SUBSCRIPTION_DROP_REASONS = setOf(
                SubscriptionDropReason.ConnectionClosed,
                SubscriptionDropReason.ServerError,
                SubscriptionDropReason.SubscribingError,
                SubscriptionDropReason.CatchUpError
        )

        val OVERRIDE_EVENT_ID = "overrideEventId"
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

    private fun beforeHandle(method: Method, event: RecordedEvent) {
        handlerExtensions.forEach { it.beforeHandle(method, event) }
    }

    private fun afterHandle(method: Method, event: RecordedEvent) {
        handlerExtensions.asReversed().forEach { it.afterHandle(method, event) }
    }

    override fun start() {
        ensureSubscription()


        val persistentSubscription = IdempotentPersistentSubscriptionListener(
                handlerInstance = this,
                streamName = streamName,
                groupName = groupName,
                eventStore = eventStore,
                saveEventId = this::saveEventId,
                shouldSkip = this::shouldSkip,
                handlerExtensions = handlerExtensions,
                transactionTemplate = transactionTemplate,
                objectMapper = objectMapper,
                logger = logger
        )

        eventStore.subscribeToPersistent(streamName, groupName, persistentSubscription)
        running = true
    }

    private fun shouldSkip(resolvedEvent: ResolvedEvent): Boolean {
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

    private fun saveEventId(idempotentEventClassifierRecord: IdempotentEventClassifierRecord): Boolean {
        return try {
            idempotentEventClassifierDao.insert(tableName, idempotentEventClassifierRecord)
            true
        } catch (e: UnableToExecuteStatementException) {
            if (!e.isDuplicateEntryException)
                throw RuntimeException(e)

            false
        }
    }

    private val UnableToExecuteStatementException.isDuplicateEntryException: Boolean
        get() {
            return "Duplicate entry" in (this.message ?: "") && "duplicate key" in (this.message ?: "")
        }
}