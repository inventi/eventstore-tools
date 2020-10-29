package io.inventi.eventstore.aggregate

import java.time.Instant

interface Event {
    val createdAt: Instant
}
