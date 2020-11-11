package io.inventi.eventstore.aggregate.snapshot

class NoopSnapshotsRepository : SnapshotsRepository {
    override fun save(id: SnapshotId, serializedSnapshot: ByteArray, eventNumber: Long) {
        println("Saving $id $serializedSnapshot, $eventNumber")
    }

    override fun load(id: SnapshotId): SnapshotBytesAndLastEventNumber? {
        return null
    }
}