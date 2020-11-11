package io.inventi.eventstore.aggregate.snapshot

import io.inventi.eventstore.util.LoggerDelegate
import org.slf4j.Logger
import org.springframework.data.redis.core.RedisOperations
import java.io.Serializable

class RedisSnapshotsRepository(private val redisOperations: RedisOperations<Any, Any>) : SnapshotsRepository {
    private val logger: Logger by LoggerDelegate()

    override fun save(id: SnapshotId, serializedSnapshot: ByteArray, eventNumber: Long) {
        val name = snapshotName(id)
        runCatching { saveSnapshot(name, id.aggregateId, serializedSnapshot, eventNumber) }
                .onFailure { e -> logger.warn("Failed to save snapshot for $id", e) }
    }

    override fun load(id: SnapshotId): SnapshotBytesAndLastEventNumber? {
        val serializedAggregate: SerializedSnapshot? = runCatching { loadSnapshot(snapshotName(id), id.aggregateId) }
                .onFailure { e -> logger.warn("Failed to load snapshot for $id", e) }
                .getOrNull()

        return serializedAggregate?.let {
            SnapshotBytesAndLastEventNumber(it.serializedAggregate, it.eventNumber)
        }
    }

    private fun saveSnapshot(snapshotName: String, aggregateId: String, seralizedSnapshot: ByteArray, eventNumber: Long) {
        redisOperations.opsForHash<String, SerializedSnapshot>()
                .put(snapshotName, aggregateId, SerializedSnapshot(aggregateId, seralizedSnapshot, eventNumber))
    }

    private fun loadSnapshot(snapshotName: String, aggregateId: String): SerializedSnapshot? {
        return redisOperations.opsForHash<String, SerializedSnapshot>()
                .get(snapshotName, aggregateId)
    }

    private fun snapshotName(id: SnapshotId) = with(id) { "Snapshot-${type}-v${version}" }

    private data class SerializedSnapshot(val id: String, val serializedAggregate: ByteArray, val eventNumber: Long) : Serializable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SerializedSnapshot) return false

            if (id != other.id) return false
            if (!serializedAggregate.contentEquals(other.serializedAggregate)) return false
            if (eventNumber != other.eventNumber) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + serializedAggregate.contentHashCode()
            result = 31 * result + eventNumber.hashCode()
            return result
        }
    }
}