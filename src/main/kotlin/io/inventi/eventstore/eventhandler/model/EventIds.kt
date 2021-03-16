package io.inventi.eventstore.eventhandler.model

data class EventIds(
        val originalId: String?,
        val effectiveId: String,
)