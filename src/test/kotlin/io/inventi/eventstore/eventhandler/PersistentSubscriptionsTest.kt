package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.PersistentSubscriptionCreateResult
import com.github.msemys.esjc.PersistentSubscriptionCreateStatus
import com.github.msemys.esjc.PersistentSubscriptionSettings
import com.github.msemys.esjc.system.SystemConsumerStrategy
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import io.inventi.eventstore.eventhandler.dao.SubscriptionInitialPositionDao
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import io.inventi.eventstore.util.ObjectMapperFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.function.Consumer

class PersistentSubscriptionsTest {
    private val eventStore = mockk<EventStore>()
    private val transactionTemplate = mockk<TransactionTemplate>()
    private val subscriptionInitialPositionDao = mockk<SubscriptionInitialPositionDao>()

    @BeforeEach
    fun setUp() {
        every { transactionTemplate.executeWithoutResult(any()) } answers {
            firstArg<Consumer<TransactionStatus>>().accept(mockk())
        }
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
        val persistentSubscriptions = persistentSubscriptions()

        // when
        persistentSubscriptions.afterPropertiesSet()

        // then
        verify(exactly = 2) {
            eventStore.subscribeToPersistent(streamName, groupName, any())
        }
    }

    @ParameterizedTest
    @EnumSource(SystemConsumerStrategy::class)
    fun `creates eventStore subscription with consumer strategy which was defined in handler`(consumerStrategy: SystemConsumerStrategy) {
        // given
        every { eventStore.subscribeToPersistent(any(), any(), any()) } returns completedFuture(mockk())
        val persistentSubscriptions = persistentSubscriptions(Handler(consumerStrategy = consumerStrategy))

        // when
        persistentSubscriptions.afterPropertiesSet()

        // then
        verify {
            eventStore.createPersistentSubscription(any(), any(), withArg<PersistentSubscriptionSettings> {
                it.namedConsumerStrategy shouldBeEqualTo consumerStrategy
            })
        }
    }

    private fun persistentSubscriptions(
            handler: PersistentSubscriptionHandler = Handler()
    ) = PersistentSubscriptions(
            handlers = listOf(handler),
            eventStore = eventStore,
            objectMapper = ObjectMapperFactory.createDefaultObjectMapper(),
            subscriptionInitialPositionDao = subscriptionInitialPositionDao,
            subscriptionProperties = SubscriptionProperties(),
            transactionTemplate = transactionTemplate,
            idempotencyStorage = mockk()
    )

    private class Handler(
            override val streamName: String = DataBuilder.streamName,
            override val groupName: String = DataBuilder.groupName,
            override val consumerStrategy: SystemConsumerStrategy = SystemConsumerStrategy.ROUND_ROBIN
    ) : PersistentSubscriptionHandler
}