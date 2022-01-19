package io.inventi.eventstore.eventhandler.dao

import io.inventi.eventstore.eventhandler.EventstoreToolsTestConfiguration
import io.inventi.eventstore.eventhandler.util.DataBuilder.groupName
import io.inventi.eventstore.eventhandler.util.DataBuilder.streamName
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [EventstoreToolsTestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class SubscriptionCheckpointDaoTest {
    @Autowired
    private lateinit var checkpointDao: SubscriptionCheckpointDao

    private val checkpoint = SubscriptionCheckpoint(
            groupName = streamName,
            streamName = groupName,
            checkpoint = 1337
    )

    @Test
    fun `stores checkpoint`() {
        // when
        val affectedRows = checkpointDao.createIfNotExists(checkpoint)

        // then
        affectedRows shouldBeEqualTo 1
        checkpointDao.currentCheckpoint(checkpoint.groupName, checkpoint.streamName) shouldBeEqualTo checkpoint.checkpoint
    }

    @Test
    fun `does nothing if checkpoint already exists`() {
        // given
        checkpointDao.createIfNotExists(checkpoint)

        // when
        val affectedRows = checkpointDao.createIfNotExists(checkpoint)

        // then
        affectedRows shouldBeEqualTo 0
        checkpointDao.currentCheckpoint(checkpoint.groupName, checkpoint.streamName) shouldBeEqualTo checkpoint.checkpoint
    }

    @Test
    fun `increments checkpoint`() {
        // given
        checkpointDao.createIfNotExists(checkpoint)

        // when
        val affectedRows = checkpointDao.incrementCheckpoint(checkpoint.groupName, checkpoint.streamName, 2000)

        // then
        affectedRows shouldBeEqualTo 1
        checkpointDao.currentCheckpoint(checkpoint.groupName, checkpoint.streamName) shouldBeEqualTo 2000
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 12, 1337])
    fun `does not increment checkpoint if it is not higher than current one`(newCheckpoint: Long) {
        // given
        checkpointDao.createIfNotExists(checkpoint)

        // when
        val affectedRows = checkpointDao.incrementCheckpoint(checkpoint.groupName, checkpoint.streamName, newCheckpoint)

        // then
        affectedRows shouldBeEqualTo 0
        checkpointDao.currentCheckpoint(checkpoint.groupName, checkpoint.streamName) shouldBeEqualTo checkpoint.checkpoint
    }

    @Test
    fun `deletes checkpoint`() {
        // given
        checkpointDao.createIfNotExists(checkpoint)

        // when
        val affectedRows = checkpointDao.delete(checkpoint.groupName, checkpoint.streamName)

        // then
        affectedRows shouldBeEqualTo 1
        checkpointDao.currentCheckpoint(checkpoint.groupName, checkpoint.streamName).shouldBeNull()
    }
}