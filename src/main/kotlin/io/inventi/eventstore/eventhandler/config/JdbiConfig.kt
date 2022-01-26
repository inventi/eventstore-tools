package io.inventi.eventstore.eventhandler.config

import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.kotlin.onDemand
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

@ConditionalOnSubscriptionsEnabled
@Configuration
class JdbiConfig {
    @Bean
    fun jdbi(dataSource: DataSource): Jdbi = Jdbi.create(TransactionAwareDataSourceProxy(dataSource)).installPlugins()

    @Bean
    fun processedEventDao(jdbi: Jdbi) = jdbi.onDemand<ProcessedEventDao>()

    @Bean
    fun subscriptionCheckpointDao(jdbi: Jdbi) = jdbi.onDemand<SubscriptionCheckpointDao>()
}