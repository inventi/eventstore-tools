package io.inventi.eventstore.eventhandler.model

import java.time.ZonedDateTime


data class IdempotentEventClassifierRecord(
        val eventId: String,
        val streamName: String,
        val eventStreamId: String,
        val groupName: String,
        val eventType: String,
        val createdAt: ZonedDateTime = ZonedDateTime.now(),
        val idempotencyClassifier: String = "$streamName-$groupName-$eventType"
)