package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.RecordedEvent
import java.lang.reflect.Method


interface EventHandlerExtension {
    fun beforeHandle(method: Method, event: RecordedEvent) {}
    fun afterHandle(method: Method, event: RecordedEvent) {}
}