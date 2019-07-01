package io.inventi.eventstore.eventhandler.config

import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreInitConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@ConditionalOnProperty("eventstore.subscriptions.enabled")
@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(properties: EventStoreInitConfig) =
            EventStoreBuilder.newBuilder()
                    .maxReconnections(-1)
                    .singleNodeAddress(properties.host, properties.port)
                    .userCredentials(properties.username, properties.password)
                    .build();
}