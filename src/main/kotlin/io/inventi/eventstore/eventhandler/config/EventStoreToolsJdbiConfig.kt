package io.inventi.eventstore.eventhandler.config

import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.dao.SubscriptionInitialPositionDao
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.kotlin.onDemand
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

@ConditionalOnSubscriptionsEnabled
@Configuration
@DependsOn(value = ["flyway", "flywayInitializer"])
class EventStoreToolsJdbiConfig {
    @ConditionalOnMissingBean
    @Bean
    fun jdbi(dataSource: DataSource): Jdbi = Jdbi.create(TransactionAwareDataSourceProxy(dataSource)).installPlugins()

    @Bean
    fun processedEventDao(jdbi: Jdbi) = jdbi.onDemand<ProcessedEventDao>()

    @Bean
    fun subscriptionCheckpointDao(jdbi: Jdbi) = jdbi.onDemand<SubscriptionCheckpointDao>()

    @Bean
    fun subscriptionInitialPositionDao(jdbi: Jdbi) = jdbi.onDemand<SubscriptionInitialPositionDao>()
}