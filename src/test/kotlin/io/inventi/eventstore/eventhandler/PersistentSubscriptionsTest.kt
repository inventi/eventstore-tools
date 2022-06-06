package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscriptionCreateResult
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionSettings
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.dao.SubscriptionInitialPositionDao
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.inventi.eventstore.util.ObjectMapperFactory
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture

class PersistentSubscriptionsTest {
    private val eventStore = mockk<EventStore>()
    private val transactionTemplate = mockk<TransactionTemplate>()
    private val subscriptionInitialPositionDao = mockk<SubscriptionInitialPositionDao>()
    private val persistentSubscriptions = PersistentSubscriptions(
            handlers = listOf(Handler()),
            eventStore = eventStore,
            objectMapper = ObjectMapperFactory.createDefaultObjectMapper(),
            subscriptionInitialPositionDao = subscriptionInitialPositionDao,
            subscriptionProperties = SubscriptionProperties(),
            transactionTemplate = transactionTemplate,
            idempotencyStorage = mockk()
    )

    @BeforeEach
    fun setUp() {
        every { transactionTemplate.executeWithoutResult(any()) } just Runs
        every { subscriptionInitialPositionDao.createIfNotExists(any()) } returns 1
        every { subscriptionInitialPositionDao.initialPosition(any(), any()) } returns 0
        every { eventStore.createPersistentSubscription(any(), any(), any<PersistentSubscriptionSettings>()) } returns
                completedFuture(PersistentSubscriptionCreateResult(PersistentSubscriptionCreateStatus.Success))
    }

    @Test
    fun `retries subscription if it cannot be started`() {
        // given
        every { eventStore.subscribeToPersistent(any(), any(), any()) } returns
                failedFuture(RuntimeException()) andThen
                completedFuture(mockk())

        // when
        persistentSubscriptions.afterPropertiesSet()

        // then
        verify(exactly = 2) {
            eventStore.subscribeToPersistent(streamName, groupName, any())
        }
    }

    private class Handler(
            override val streamName: String = DataBuilder.streamName,
            override val groupName: String = DataBuilder.groupName,
    ) : PersistentSubscriptionHandler
}