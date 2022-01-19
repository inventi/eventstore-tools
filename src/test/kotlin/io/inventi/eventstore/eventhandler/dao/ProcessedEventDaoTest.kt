package io.inventi.eventstore.eventhandler.dao

import io.inventi.eventstore.eventhandler.EventstoreToolsTestConfiguration
import io.inventi.eventstore.eventhandler.util.DataBuilder.processedEvent
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [EventstoreToolsTestConfiguration::class])
@ActiveProfiles("test")
@Transactional
class ProcessedEventDaoTest {
    @Autowired
    private lateinit var processedEventDao: ProcessedEventDao

    private val processedEvent = processedEvent()

    @Test
    fun `stores processed event`() {
        // when
        val affectedRows = processedEventDao.save(processedEvent)

        // then
        affectedRows shouldBeEqualTo 1
        processedEventDao.findBy(
                processedEvent.eventId,
                processedEvent.streamName,
                processedEvent.groupName,
                processedEvent.eventType,
        ) shouldBeEqualTo processedEvent
    }

    @Test
    fun `does nothing if event already exists`() {
        // given
        processedEventDao.save(processedEvent)

        // when

        val affectedRows = processedEventDao.save(processedEvent)

        // then
        affectedRows shouldBeEqualTo 0
    }
}