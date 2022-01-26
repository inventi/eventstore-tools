package io.inventi.eventstore.eventhandler.dao

import java.time.Instant

data class ProcessedEvent(
        val eventId: String,
        val streamName: String,
        val eventStreamId: String,
        val groupName: String,
        val eventType: String,
        val createdAt: Instant = Instant.now(),
)
