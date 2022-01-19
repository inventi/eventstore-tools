package io.inventi.eventstore

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
abstract class EventStoreIntegrationTest {
    companion object {
        private const val IMAGE_NAME = "ghcr.io/eventstore/eventstore:21.10.0-alpha-arm64v8"
    }

    @Container
    private val eventStoreContainer = GenericContainer<Nothing>(DockerImageName.parse(IMAGE_NAME)).apply {
        withEnv("EVENTSTORE_INSECURE", "true")
        withEnv("EVENTSTORE_ENABLE_EXTERNAL_TCP", "true")
        withEnv("EVENTSTORE_EXT_TCP_PORT", "1113")
        waitingFor(Wait.forHealthcheck())
        withExposedPorts(1113)
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