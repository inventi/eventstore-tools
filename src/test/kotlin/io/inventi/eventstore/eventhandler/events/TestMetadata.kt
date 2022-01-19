package io.inventi.eventstore.eventhandler.events

data class TestMetadata(
        val overrideEventId: String,
        val overrideEventNumber: String,
        val transformedFromEventNumber: Long,
        val transformedFromJavaEventId: String,
)