package io.inventi.eventstore.eventhandler.feature

import io.inventi.eventstore.EventStoreToolsDBConfiguration
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpoint
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.mockk.mockk
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldThrow
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(classes = [EventStoreToolsDBConfiguration::class])
@ActiveProfiles("test")
class InTransactionTest {
    private val groupName = "groupName"
    private val streamName = "streamName"

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var checkpointDao: SubscriptionCheckpointDao

    @Autowired
    private lateinit var jdbi: Jdbi

    @Test
    fun `executes and commits given block in transaction`() {
        // given
        val inTransaction = InTransaction(transactionTemplate)

        // when
        inTransaction.wrap(mockk(), 1) {
            checkpointDao.createIfNotExists(SubscriptionCheckpoint(groupName, streamName, 42))
        }
        checkpointDao.currentCheckpoint(groupName, streamName) shouldBeEqualTo 42

        // cleanup
        checkpointDao.delete(groupName, streamName)
    }

    @Test
    fun `rolls back database changes if given block throws an exception`() {
        // given
        val inTransaction = InTransaction(transactionTemplate)

        // when
        invoking {
            inTransaction.wrap(mockk(), 1) {
                checkpointDao.createIfNotExists(SubscriptionCheckpoint(groupName, streamName, 42))
                throw RuntimeException("Something bad happened")
            }
        } shouldThrow RuntimeException::class

        // then
        checkpointDao.currentCheckpoint(groupName, streamName).shouldBeNull()
    }
}