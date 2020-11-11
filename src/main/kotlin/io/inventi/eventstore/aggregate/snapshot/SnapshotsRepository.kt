package io.inventi.eventstore.aggregate.snapshot

interface SnapshotsRepository {
    fun save(id: SnapshotId, serializedSnapshot: ByteArray, eventNumber: Long)
    fun load(id: SnapshotId): SnapshotBytesAndLastEventNumber?
}

data class SnapshotBytesAndLastEventNumber(val bytes: ByteArray, val lastEvent: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotBytesAndLastEventNumber) return false

        if (!bytes.contentEquals(other.bytes)) return false
        if (lastEvent != other.lastEvent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + lastEvent.hashCode()
        return result
    }
}