package io.inventi.eventstore

import io.inventi.eventstore.eventhandler.config.EventStoreToolsJdbiConfig
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@EnableAutoConfiguration
@Import(EventStoreToolsJdbiConfig::class)
class EventStoreToolsDBConfiguration