package io.inventi.eventstore.aggregate.event

import io.inventi.eventstore.aggregate.Event
import java.time.Instant

data class SomeEvent(val data: String, override val createdAt: Instant = Instant.now()) : Event