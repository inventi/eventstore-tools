package io.inventi.eventstore

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
open class EventStoreIntegrationTest {
    @Container
    private val eventStoreContainer = GenericContainer<Nothing>(DockerImageName.parse("ghcr.io/eventstore/eventstore:21.10.0-alpha-arm64v8")).apply {
        exposedPorts = listOf(1113)
    }

    protected lateinit var eventStore: EventStore

    @RegisterExtension
    @JvmField
    protected val beforeEachExtension = BeforeEachCallback {
        eventStore = EventStoreBuilder.newBuilder()
                .singleNodeAddress(eventStoreContainer.host, eventStoreContainer.firstMappedPort)
                .userCredentials("admin", "changeit")
                .maxReconnections(1)
                .build()
    }
}