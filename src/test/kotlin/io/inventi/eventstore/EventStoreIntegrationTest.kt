package io.inventi.eventstore

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.temporal.TemporalUnit

@Testcontainers
abstract class EventStoreIntegrationTest {
    companion object {
        // TODO use non-alpha image when it is released (https://github.com/EventStore/EventStore/issues/2380)
        private const val IMAGE_NAME = "ghcr.io/eventstore/eventstore:21.10.0-alpha-arm64v8"

        @JvmStatic
        @Container
        protected val eventStoreContainer = GenericContainer<Nothing>(DockerImageName.parse(IMAGE_NAME)).apply {
            withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            withEnv("EVENTSTORE_INSECURE", "true")
            withEnv("EVENTSTORE_ENABLE_EXTERNAL_TCP", "true")
            withEnv("EVENTSTORE_EXT_TCP_PORT", "1113")
            waitingFor(Wait.forHealthcheck())
            withStartupTimeout(Duration.ofSeconds(150))
            withExposedPorts(1113)
        }

        @JvmStatic
        @DynamicPropertySource
        protected fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eventstore.host", eventStoreContainer::getHost)
            registry.add("eventstore.port", eventStoreContainer::getFirstMappedPort)
        }
    }
}