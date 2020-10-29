package io.inventi.eventstore.aggregate

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.aggregate.event.SomeEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import kotlin.reflect.KClass

class SomeAggregateRoot(id: String) : AggregateRoot(id)

@Testcontainers
class RepositoryTest {

    @Container
    private val eventStoreContainer = GenericContainer<Nothing>(DockerImageName.parse("eventstore/eventstore:release-5.0.0")).apply {
        exposedPorts = listOf(1113)
    }
    private lateinit var es: EventStore

    @BeforeEach
    fun setUp() {
        es = EventStoreBuilder.newBuilder()
                .singleNodeAddress(eventStoreContainer.host, eventStoreContainer.firstMappedPort)
                .maxReconnections(1)
                .build()
    }

    @Test
    fun `throws exception when there are newer events in the stream`() {
        // given
        val aggregateId = "125"

        val repository = object : PersistentRepository<SomeAggregateRoot>(SomeEvent::class.java.`package`.name) {
            override val eventStore: EventStore = es
            override val aggregateType: KClass<SomeAggregateRoot> = SomeAggregateRoot::class
            override val metadataSource: MetadataSource = EmptyMetadataSource()
        }

        repository.save(aggregateId, listOf(EventMessage(SomeEvent("Something"), Instant.now())), -2)

        val firstAggregateInstance = repository.findOne(aggregateId)
        val secondAggregateInstance = repository.findOne(aggregateId)

        val event = SomeEvent("Some Data")

        firstAggregateInstance?.appendEvent(event)
        secondAggregateInstance?.appendEvent(event)

        // when
        firstAggregateInstance?.commitChangesTo(repository)

        // then
        assertThrows<AggregateRootIsBehindStreamException> {
            secondAggregateInstance?.commitChangesTo(repository)
        }
    }
}
