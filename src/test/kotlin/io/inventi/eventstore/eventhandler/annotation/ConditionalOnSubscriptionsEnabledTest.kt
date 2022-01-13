package io.inventi.eventstore.eventhandler.annotation

import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootTest(properties = ["eventstore.subscriptions.enabled=false"], classes = [SomeHandlerConfiguration::class])
class ConditionalOnSubscriptionsEnabledTrueTest {
    @Autowired
    private var handler: EventstoreEventHandler? = null

    @Test
    fun `does not instantiate bean if eventstore subscriptions enabled is false`() {
        handler.shouldBeNull()
    }
}

@SpringBootTest(properties = ["eventstore.subscriptions.enabled=true"], classes = [SomeHandlerConfiguration::class])
class ConditionalOnSubscriptionsEnabledFalseTest {
    @Autowired
    private var handler: EventstoreEventHandler? = null

    @Test
    fun `instantiates bean if eventstore subscriptions enabled is true`() {
        handler.shouldNotBeNull()
    }
}

@Configuration
class SomeHandlerConfiguration {
    @Bean
    @ConditionalOnSubscriptionsEnabled
    fun handler(): EventstoreEventHandler {
        return EmptyHandler()
    }

    class EmptyHandler(override val streamName: String = "", override val groupName: String = "") : EventstoreEventHandler
}

