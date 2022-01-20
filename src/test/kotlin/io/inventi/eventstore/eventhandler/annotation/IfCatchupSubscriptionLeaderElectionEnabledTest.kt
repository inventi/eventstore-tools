package io.inventi.eventstore.eventhandler.annotation

import io.inventi.eventstore.eventhandler.CatchupSubscriptionHandler
import io.inventi.eventstore.eventhandler.annotation.IfCatchupSubscriptionLeaderElectionEnabledConfig.IfCatchupSubscriptionLeaderElectionEnabledBean
import io.inventi.eventstore.eventhandler.util.DataBuilder
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootTest(properties = ["eventstore.subscriptions.enableCatchupSubscriptionLeaderElection=false"], classes = [IfCatchupSubscriptionLeaderElectionEnabledConfig::class])
class IfCatchupSubscriptionLeaderElectionDisabledTest {
    @Autowired
    private var injectedBean: IfCatchupSubscriptionLeaderElectionEnabledBean? = null

    @Test
    fun `does not instantiate bean if catchup subscription leader election is disabled`() {
        injectedBean.shouldBeNull()
    }
}

@SpringBootTest(properties = ["eventstore.subscriptions.enableCatchupSubscriptionLeaderElection=true"], classes = [IfCatchupSubscriptionLeaderElectionEnabledConfig::class])
class IfCatchupSubscriptionLeaderElectionEnabledTest {
    @Autowired
    private var injectedBean: IfCatchupSubscriptionLeaderElectionEnabledBean? = null

    @Test
    fun `instantiates bean if catchup subscription leader election is enabled`() {
        injectedBean.shouldNotBeNull()
    }
}

@Configuration
class IfCatchupSubscriptionLeaderElectionEnabledConfig {

    @Bean
    fun handler(): CatchupSubscriptionHandler {
        return EmptyHandler()
    }

    @Bean
    @IfCatchupSubscriptionLeaderElectionEnabled
    fun ifCatchupSubscriptionLeaderElectionEnabledBean() = IfCatchupSubscriptionLeaderElectionEnabledBean()

    class IfCatchupSubscriptionLeaderElectionEnabledBean
    class EmptyHandler(
            override val streamName: String = DataBuilder.streamName,
            override val groupName: String = DataBuilder.groupName,
    ) : CatchupSubscriptionHandler
}