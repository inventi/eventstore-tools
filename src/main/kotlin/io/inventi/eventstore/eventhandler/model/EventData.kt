package io.inventi.eventstore.eventhandler.model

data class EventData<T>(
        val data: T,
        val operation: EventOperation
)