package io.inventi.eventstore.eventhandler.model

data class EventOperation(
        val status: EventOperationStatus,
        val code: String? = null
)