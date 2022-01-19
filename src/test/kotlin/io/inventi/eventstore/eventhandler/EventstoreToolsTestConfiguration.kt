package io.inventi.eventstore.eventhandler

import io.inventi.eventstore.eventhandler.config.JdbiConfig
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@EnableAutoConfiguration
@Import(JdbiConfig::class)
class EventstoreToolsTestConfiguration