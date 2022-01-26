package io.inventi.eventstore.eventhandler.util

import io.inventi.eventstore.eventhandler.events.EventType
import io.mockk.verify
import org.awaitility.Awaitility
import java.util.concurrent.TimeUnit

interface WithAsyncHandlerAssertions {
    var handler: Handler

    fun assertEventWasHandled(event: EventType, eventId: String) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) { handler.handle(event, eventIds = DataBuilder.eventIds(eventId)) }
        }
    }

    fun assertEventWasNotHandled(eventId: String) {
        Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 0) { handler.handle(any(), eventIds = DataBuilder.eventIds(eventId)) }
        }
    }

    fun waitUntilEventIsHandled(eventId: String) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            verify(exactly = 1) {
                handler.handle(any(), any(), match { it.current == eventId })
            }
        }
    }
}