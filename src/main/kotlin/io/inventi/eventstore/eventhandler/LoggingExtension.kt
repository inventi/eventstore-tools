package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.RecordedEvent
import org.slf4j.Logger
import java.lang.reflect.Method

class LoggingExtension(private val logger: Logger) : EventHandlerExtension {
    override fun handle(method: Method, event: RecordedEvent): Cleanup {
        logger.info("Handling ${event.eventType} event: ${String(event.data)}")
        return {}
    }
}