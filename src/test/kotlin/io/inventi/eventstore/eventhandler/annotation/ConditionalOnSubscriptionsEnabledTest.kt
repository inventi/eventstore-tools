package io.inventi.eventstore.eventhandler.annotation

import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootTest(properties = ["eventstore.subscriptions.enabled=false"], classes = [ConditionalOnSubscriptionsEnabledConfig::class])
class ConditionalOnSubscriptionsEnabledTrueTest {
    @Autowired
    private var injectedBean: ConditionalOnSubscriptionsEnabledConfig.ConditionalOnSubscriptionsEnabledBean? = null

    @Test
    fun `does not instantiate bean if eventstore subscriptions enabled is false`() {
        injectedBean.shouldBeNull()
    }
}

@SpringBootTest(properties = ["eventstore.subscriptions.enabled=true"], classes = [ConditionalOnSubscriptionsEnabledConfig::class])
class ConditionalOnSubscriptionsEnabledFalseTest {
    @Autowired
    private var injectedBean: ConditionalOnSubscriptionsEnabledConfig.ConditionalOnSubscriptionsEnabledBean? = null

    @Test
    fun `instantiates bean if eventstore subscriptions enabled is true`() {
        injectedBean.shouldNotBeNull()
    }
}

@Configuration
class ConditionalOnSubscriptionsEnabledConfig {
    @Bean
    @ConditionalOnSubscriptionsEnabled
    fun conditionalOnSubscriptionsEnabledBean(): ConditionalOnSubscriptionsEnabledBean {
        return ConditionalOnSubscriptionsEnabledBean()
    }

    class ConditionalOnSubscriptionsEnabledBean
}

