package io.inventi.eventstore.eventhandler.config

import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreInitConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetSocketAddress

@ConditionalOnProperty("eventstore.subscriptions.enabled")
@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(properties: EventStoreInitConfig) =
            EventStoreBuilder.newBuilder()
                    .maxReconnections(-1)
                    .persistentSubscriptionAutoAck(false)
                    .singleNodeAddress(InetSocketAddress.createUnresolved(properties.host, properties.port))
                    .userCredentials(properties.username, properties.password)
                    .build();
}