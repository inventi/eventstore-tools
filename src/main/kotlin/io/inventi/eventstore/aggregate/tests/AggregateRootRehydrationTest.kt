package io.inventi.eventstore.aggregate.tests

import com.github.msemys.esjc.EventStore
import io.inventi.eventstore.aggregate.AggregateRoot
import io.inventi.eventstore.aggregate.PersistentRepository
import io.inventi.eventstore.util.LoggerDelegate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class AggregateRootRehydrationTest<T : AggregateRoot> {
    abstract val streamPrefix: String

    protected lateinit var es: EventStore
        private set
    protected lateinit var repo: PersistentRepository<T>
        private set

    val logger by LoggerDelegate()

    abstract fun buildEventStore(): EventStore
    abstract fun createRepository(): PersistentRepository<T>

    @BeforeEach
    fun setUp() {
        es = buildEventStore()
        repo = createRepository()
    }

    @Test
    fun `every aggregate can be rehydrated`() {
        val streamIds = es.iterateStreamEventsForward("\$streams", 0L, 32, true)
                .asSequence()
                .filter { (it.event?.eventStreamId) != null }
                .map { it.event.eventStreamId }
                .filter { it.startsWith(streamPrefix) }
                .toList()

        var failed = false
        val exceptions = mutableListOf<Pair<Exception, String>>()
        streamIds.forEach { streamId ->
            try {
                repo.findOne(streamId.removePrefix(streamPrefix))
            } catch (e: Exception) {
                logger.error("Error on streamId '$streamId'", e)
                failed = true
                exceptions += e to streamId
            }
        }

        assertFalse(failed, "There were aggregate roots that failed, $exceptions")
        println("Done (suitable place for breakpoint)")
    }
}