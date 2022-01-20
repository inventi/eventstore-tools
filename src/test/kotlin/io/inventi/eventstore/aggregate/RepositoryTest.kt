package io.inventi.eventstore.aggregate

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreIntegrationTest
import io.inventi.eventstore.aggregate.metadata.EmptyMetadataSource
import io.inventi.eventstore.aggregate.metadata.MetadataSource
import io.inventi.eventstore.aggregate.tests.SomeEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.reflect.KClass

class SomeAggregateRoot(id: String) : AggregateRoot(id)

class RepositoryTest : EventStoreIntegrationTest() {
    private var eventStore: EventStore = EventStoreBuilder.newBuilder()
            .singleNodeAddress(eventStoreContainer.host, eventStoreContainer.firstMappedPort)
            .userCredentials("admin", "changeit")
            .maxReconnections(1)
            .build()

    @Test
    fun `throws exception when there are newer events in the stream`() {
        // given
        val aggregateId = "125"

        val repository = object : PersistentRepository<SomeAggregateRoot>(SomeEvent::class.java.`package`.name) {
            override val eventStore: EventStore = this@RepositoryTest.eventStore
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
