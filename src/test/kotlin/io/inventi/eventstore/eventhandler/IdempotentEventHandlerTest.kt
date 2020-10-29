package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.events.a.EventA
import io.inventi.eventstore.eventhandler.events.b.EventB
import io.inventi.eventstore.eventhandler.util.ExecutingTransactionTemplate
import io.inventi.eventstore.util.ObjectMapperFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import org.springframework.transaction.support.TransactionTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IdempotentEventHandlerTest {

    companion object {
        private val STREAM_NAME = "${IdempotentEventHandlerTest::class.java.simpleName}-22"
    }

    private lateinit var eventStore: EventStore

    private lateinit var objectMapper: ObjectMapper

    private lateinit var transactionTemplate: TransactionTemplate

    private val idempotentEventClassifierDao = mock(IdempotentEventClassifierDao::class.java)

    @BeforeAll
    fun setUp() {
        eventStore = EventStoreBuilder
                .newBuilder()
                .singleNodeAddress("127.0.0.1", 1113)
                .userCredentials("admin", "changeit")
                .build()

        objectMapper = ObjectMapperFactory.createDefaultObjectMapper()
        transactionTemplate = ExecutingTransactionTemplate()
    }

    @Test
    fun `handle events from different packages`() {
        appendEvent(EventA(10))
        appendEvent(EventB(15))

        val handler = SomeHandler(idempotentEventClassifierDao, eventStore, objectMapper, transactionTemplate)
        handler.start()

        // handler.start() does actions asynchronously. If we don't sleep, handler's thread pool
        // is shut down, so it does nothing.
        Thread.sleep(2000)
    }

    private fun appendEvent(o: Any) {
        val eventData = EventData.newBuilder()
                .type(o::class.simpleName)
                .jsonData(objectMapper.writeValueAsString(o))
                .build()

        eventStore.appendToStream(STREAM_NAME, -2, eventData).join()
    }

    class SomeHandler(
            idempotentEventClassifierDao: IdempotentEventClassifierDao,
            eventStore: EventStore,
            objectMapper: ObjectMapper,
            transactionTemplate: TransactionTemplate,
    ) : IdempotentEventHandler(STREAM_NAME, "someGroup", idempotentEventClassifierDao, eventStore, objectMapper, transactionTemplate, "someTable") {
        @EventHandler
        private fun handleA(e: EventA) {
            println(e)
        }

        @EventHandler
        private fun handleB(e: EventB) {
            println(e)
        }
    }
}
