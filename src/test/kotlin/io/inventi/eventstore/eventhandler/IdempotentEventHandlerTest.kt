package io.inventi.eventstore.eventhandler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.msemys.esjc.EventData
import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.eventhandler.annotation.EventHandler
import io.inventi.eventstore.eventhandler.events.a.EventA
import io.inventi.eventstore.eventhandler.events.b.EventB
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IdempotentEventHandlerTest {

    companion object {
        private val STREAM_NAME = "${IdempotentEventHandlerTest::class.java.simpleName}-1"
    }

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var appContext: ApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var transactionTemplate: TransactionTemplate

    @MockBean
    private lateinit var idempotentEventClassifierDao: IdempotentEventClassifierDao

    @BeforeAll
    fun setUp() {
        `when`(transactionTemplate.execute(any<TransactionCallback<Unit>>()))
                .thenAnswer {
                    val first = it.arguments.first()
                    first as TransactionCallback<Unit>
                    first.doInTransaction(SimpleTransactionStatus())
                }
    }

    @Test
    fun t() {
        appendEvent(EventA(10))
        appendEvent(EventB(15))

        val handler = SomeHandler()
        appContext.autowireCapableBeanFactory.autowireBean(handler)
        handler.start()
    }

    private fun appendEvent(o: Any) {
        val eventData = EventData.newBuilder()
                .type(o::class.simpleName)
                .jsonData(objectMapper.writeValueAsString(o))
                .build()

        eventStore.appendToStream(STREAM_NAME, -2, eventData).join()
    }

    private fun createHandler(): IdempotentEventHandler {
        return object : IdempotentEventHandler(STREAM_NAME, "someGroup", idempotentEventClassifierDao, eventStore, objectMapper, transactionTemplate) {
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

    class SomeHandler() : IdempotentEventHandler(STREAM_NAME, "someGroup") {
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

@Configuration
private class TestConfig {
    @Bean
    fun objectMapperBean() =
            ObjectMapper().apply {
                disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(KotlinModule())
                registerModule(JavaTimeModule())
            }

    @Bean
    fun eventstoreBean() =
            EventStoreBuilder.newBuilder().singleNodeAddress("127.0.0.1", 1113).build()

}