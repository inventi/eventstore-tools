package io.inventi.eventstore.eventhandler.util

import io.inventi.eventstore.eventhandler.events.TestEvent
import io.inventi.eventstore.eventhandler.events.TestMetadata
import io.inventi.eventstore.eventhandler.model.EventIds

interface Handler {
    fun handle(event: TestEvent, metadata: TestMetadata? = null, eventIds: EventIds? = null)
}