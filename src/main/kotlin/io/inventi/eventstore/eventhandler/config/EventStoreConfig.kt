package io.inventi.eventstore.eventhandler.config

import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreInitConfig
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.time.Duration

@ConditionalOnSubscriptionsEnabled
@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(properties: EventStoreInitConfig) =
            EventStoreBuilder.newBuilder()
                    .maxReconnections(-1)
                    .persistentSubscriptionAutoAck(false)
                    .singleNodeAddress(InetSocketAddress.createUnresolved(properties.host, properties.port))
                    .userCredentials(properties.username, properties.password)
                    .heartbeatTimeout(Duration.ofMillis(properties.heartbeatTimeoutMillis))
                    .operationTimeout(Duration.ofMillis(properties.operationTimeoutMillis))
                    .build()
}

@Component
@ConfigurationProperties("eventstore.subscriptions")
class SubscriptionProperties {
    var enabled = false
    var updateEnabled = false
}
