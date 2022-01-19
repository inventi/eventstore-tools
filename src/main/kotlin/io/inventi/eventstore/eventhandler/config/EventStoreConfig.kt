package io.inventi.eventstore.eventhandler.config

import com.github.msemys.esjc.EventStore
import com.github.msemys.esjc.EventStoreBuilder
import io.inventi.eventstore.EventStoreInitConfig
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.annotation.IfCatchupSubscriptionLeaderElectionEnabled
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.jdbc.lock.DefaultLockRepository
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.integration.jdbc.lock.LockRepository
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator
import org.springframework.integration.support.locks.LockRegistry
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.time.Duration
import javax.sql.DataSource


@ConditionalOnSubscriptionsEnabled
@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(properties: EventStoreInitConfig): EventStore = EventStoreBuilder.newBuilder()
            .maxReconnections(-1)
            .persistentSubscriptionAutoAck(false)
            .singleNodeAddress(InetSocketAddress.createUnresolved(properties.host, properties.port))
            .userCredentials(properties.username, properties.password)
            .heartbeatTimeout(Duration.ofMillis(properties.heartbeatTimeoutMillis))
            .operationTimeout(Duration.ofMillis(properties.operationTimeoutMillis))
            .build()

    @Bean
    @IfCatchupSubscriptionLeaderElectionEnabled
    fun lockRepository(dataSource: DataSource, properties: SubscriptionProperties) = DefaultLockRepository(dataSource).apply {
        setPrefix("EVENTSTORE_SUBSCRIPTION_")
        setTimeToLive(properties.leaderLockTimeToLiveMillis)
    }

    @Bean
    @IfCatchupSubscriptionLeaderElectionEnabled
    fun lockRegistry(lockRepository: LockRepository, properties: SubscriptionProperties) = JdbcLockRegistry(lockRepository).apply {
        setIdleBetweenTries(Duration.ofMillis(properties.leaderLockIdleBetweenTriesMillis))
    }

    @Bean
    @IfCatchupSubscriptionLeaderElectionEnabled
    fun lockRegistryLeaderInitiator(lockRegistry: LockRegistry, properties: SubscriptionProperties) = LockRegistryLeaderInitiator(lockRegistry).apply {
        setHeartBeatMillis(properties.leaderHeartbeatMillis)
        setBusyWaitMillis(properties.leaderBusyWaitMillis)
    }
}

@Component
@ConfigurationProperties("eventstore.subscriptions")
class SubscriptionProperties {
    var enabled = false
    var updateEnabled = false
    var messageTimeoutMillis: Long = 30_000
    var maxRetryCount = 10
    var minCheckpointCount = 10
    var enableCatchupSubscriptionLeaderElection = true
    var leaderLockTimeToLiveMillis = 10_000
    var leaderLockIdleBetweenTriesMillis: Long = 1_000
    var leaderHeartbeatMillis: Long = 2_000
    var leaderBusyWaitMillis: Long = 2_000
}
