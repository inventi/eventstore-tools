package io.inventi.eventstore.eventhandler.model

data class EventData<T>(
        val eventData: T,
        val eventOperation: EventOperation
)