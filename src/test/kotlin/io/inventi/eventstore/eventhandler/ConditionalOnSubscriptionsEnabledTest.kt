package io.inventi.eventstore.eventhandler

import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@SpringBootTest(properties = ["eventstore.subscriptions.enabled=false"], classes = [SomeHandlerConfiguration::class])
@MockBeans(
        MockBean(IdempotentEventClassifierDao::class)
)
class ConditionalOnSubscriptionsEnabledTest {
    @Autowired
    private var handler: IdempotentEventHandlerTest.SomeHandler? = null

    @Test
    fun `does not instantiate bean if eventstore subscriptions enabled is false`() {
        assertNull(handler)
    }
}

@Configuration
class SomeHandlerConfiguration {
    @Bean
    @ConditionalOnSubscriptionsEnabled
    fun handler(): EmptyHandler {
        return EmptyHandler()
    }

    class EmptyHandler() : IdempotentEventHandler("a", "b")
}

