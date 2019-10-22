package io.inventi.eventstore.eventhandler.config

import io.inventi.eventstore.eventhandler.IdempotentEventClassifierDao
import io.inventi.eventstore.eventhandler.annotation.SubscriptionsEnabled
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.kotlin.onDemand
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SubscriptionsEnabled
@Configuration
class IdempotentEventClassifierJdbiConfig {
    @Bean
    fun idempotentEventClassifierDao(jdbi: Jdbi) = jdbi.onDemand<IdempotentEventClassifierDao>()
}