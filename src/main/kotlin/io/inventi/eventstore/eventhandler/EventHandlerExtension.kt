package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.RecordedEvent
import java.lang.reflect.Method

typealias Cleanup = (error: Throwable?) -> Unit

interface EventHandlerExtension {
    fun handle(method: Method, event: RecordedEvent): Cleanup
}