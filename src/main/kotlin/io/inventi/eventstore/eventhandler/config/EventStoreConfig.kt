package io.inventi.eventstore.eventhandler.config

import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreInitConfig
import io.inventi.eventstore.eventhandler.annotation.SubscriptionsEnabled
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.net.InetSocketAddress

@SubscriptionsEnabled
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

@Component
@ConfigurationProperties("eventstore.subscriptions")
class SubscriptionProperties {
    var enabled = false
    var updateEnabled = false
}