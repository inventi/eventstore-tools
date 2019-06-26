package io.inventi.eventstore.projector.config

import com.github.msemys.esjc.EventStoreBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotEmpty

@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(properties: EventStoreProperties) =
            EventStoreBuilder.newBuilder()
                    .maxReconnections(-1)
                    .singleNodeAddress(properties.host, properties.port)
                    .userCredentials(properties.username, properties.password)
                    .build();
}

@ConfigurationProperties("eventstore")
@Configuration
@Validated
class EventStoreProperties {

    @field:NotEmpty
    lateinit var host: String

    @field:Min(1)
    @field:Max(65535)
    var port: Int = 0

    @field:NotEmpty
    lateinit var username: String
    @field:NotEmpty
    lateinit var password: String
}