package io.inventi.eventstore.projector.model

import java.time.ZonedDateTime


data class IdempotentEventClassifierRecord(
        val id: String,
        private val streamName: String,
        private val groupName: String,
        private val eventType: String,
        val createdAt: ZonedDateTime = ZonedDateTime.now(),
        val idempotencyClassifier: String = "$streamName-$groupName-$eventType"
)