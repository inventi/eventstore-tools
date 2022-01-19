package io.inventi.eventstore.eventhandler.annotation

import io.inventi.eventstore.eventhandler.CatchupSubscriptionHandler
import io.inventi.eventstore.eventhandler.annotation.EmptyCatchupSubscriptionHandlerConfig.RandomBean
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootTest(properties = ["eventstore.subscriptions.enableCatchupSubscriptionLeaderElection=false"], classes = [EmptyCatchupSubscriptionHandlerConfig::class])
class IfCatchupSubscriptionLeaderElectionDisabledTest {
    @Autowired
    private var randomBean: RandomBean? = null

    @Test
    fun `does not instantiate bean if catchup subscription leader election is disabled`() {
        randomBean.shouldBeNull()
    }
}

@SpringBootTest(properties = ["eventstore.subscriptions.enableCatchupSubscriptionLeaderElection=true"], classes = [EmptyCatchupSubscriptionHandlerConfig::class])
class IfCatchupSubscriptionLeaderElectionEnabledTest {
    @Autowired
    private var randomBean: RandomBean? = null

    @Test
    fun `instantiates bean if catchup subscription leader election is enabled`() {
        randomBean.shouldNotBeNull()
    }
}

@Configuration
class EmptyCatchupSubscriptionHandlerConfig {

    @Bean
    fun handler(): CatchupSubscriptionHandler {
        return EmptyHandler()
    }

    @Bean
    @IfCatchupSubscriptionLeaderElectionEnabled
    fun randomBean() = RandomBean()

    class RandomBean
    class EmptyHandler(override val streamName: String = "", override val groupName: String = "") : CatchupSubscriptionHandler
}