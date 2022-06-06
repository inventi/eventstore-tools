package io.inventi.eventstore

import io.inventi.eventstore.eventhandler.CatchupSubscriptions
import io.inventi.eventstore.eventhandler.EventIdempotencyStorage
import io.inventi.eventstore.eventhandler.PersistentSubscriptions
import io.inventi.eventstore.eventhandler.config.EventStoreConfig
import io.inventi.eventstore.eventhandler.config.EventStoreToolsJdbiConfig
import io.inventi.eventstore.eventhandler.config.SubscriptionProperties
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Import

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties(EventStoreProperties::class)
@Import(
        EventStoreConfig::class,
        EventStoreToolsJdbiConfig::class,
        SubscriptionProperties::class,
        EventIdempotencyStorage::class,
        PersistentSubscriptions::class,
        CatchupSubscriptions::class,
)
@DependsOn(value = ["flywayInitializer"])
class EventStoreToolsSubscriptionsConfiguration