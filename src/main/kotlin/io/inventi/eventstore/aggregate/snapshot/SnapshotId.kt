package io.inventi.eventstore.aggregate.snapshot

data class SnapshotId(val type: String, val version: Long, val aggregateId: String)