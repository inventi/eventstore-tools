package io.inventi.eventstore.aggregate.snapshot

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import io.inventi.eventstore.aggregate.AggregateRoot
import io.inventi.eventstore.aggregate.PersistentRepository
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

abstract class PersistentSnapshottingRepository<AGGREGATE_ROOT, SNAPSHOT>(eventPackage: String, objectMapper: ObjectMapper) :
        PersistentRepository<AGGREGATE_ROOT>(eventPackage, objectMapper),
        SnapshottingRepository<AGGREGATE_ROOT, SNAPSHOT>
        where AGGREGATE_ROOT : AggregateRoot,
              AGGREGATE_ROOT : SnapshottableAggregate<AGGREGATE_ROOT, SNAPSHOT>,
              SNAPSHOT : AggregateRootSnapshot<AGGREGATE_ROOT> {

    abstract val snapshotType: KClass<SNAPSHOT>
    abstract val snapshotsRepository: SnapshotsRepository

    private val adjustedObjectMapper: ObjectMapper = objectMapper.copy().apply {
        setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
    }

    override fun constructAggregateRoot(aggregateId: String): AGGREGATE_ROOT {
        val snapshot = snapshotsRepository.load(snapshotId(aggregateId))
        val aggregateRoot: AGGREGATE_ROOT = instantiate(aggregateId)
        val restoredAggregate = snapshot?.let {
            timeSnapshotLoading(aggregateRoot, snapshot.bytes, snapshot.lastEvent)
        }

        val events = loadEvents(aggregateId, restoredAggregate?.eventNumber)
        aggregateRoot.loadFromHistory(events)
        return aggregateRoot
    }

    @OptIn(ExperimentalTime::class)
    private fun timeSnapshotLoading(aggregateRoot: AGGREGATE_ROOT, snapshotBytes: ByteArray, snapshotEventNumber: Long): RestoredAggregateAndEventNumber<AGGREGATE_ROOT> {
        val (restoredAggregateAndEventNumber, duration) = measureTimedValue {
            loadSnapshot(snapshotBytes, aggregateRoot, snapshotEventNumber)
        }

        if (restoredAggregateAndEventNumber.restored) {
            logger.debug("Loaded snapshot for ${aggregateRoot.description()} in $duration")
        } else {
            logger.debug("Failed to load snapshot for ${aggregateRoot.description()} in $duration")
        }

        return restoredAggregateAndEventNumber
    }

    private fun loadSnapshot(snapshotBytes: ByteArray, aggregateRoot: AGGREGATE_ROOT, snapshotEventNumber: Long): RestoredAggregateAndEventNumber<AGGREGATE_ROOT> {
        val snapshot = deserializeSnapshot(snapshotBytes)
        val loadFrom = if (snapshot != null) {
            (snapshotEventNumber + 1).also { aggregateRoot.loadFromSnapshot(snapshot, snapshotEventNumber) }
        } else {
            null
        }

        return RestoredAggregateAndEventNumber(aggregateRoot, loadFrom, restored = snapshot != null)
    }

    private fun deserializeSnapshot(snapshot: ByteArray?): SNAPSHOT? {
        return adjustedObjectMapper.runCatching {
            readValue(snapshot, snapshotType.java)
        }
                .getOrNull()
    }

    override fun save(aggregateRoot: AGGREGATE_ROOT) {
        super.save(aggregateRoot)
        maybeSnapshot(aggregateRoot, currentEventNumber = aggregateRoot.expectedVersion)
    }

    @OptIn(ExperimentalTime::class)
    private fun maybeSnapshot(aggregateRoot: AGGREGATE_ROOT, currentEventNumber: Long) {
        if (aggregateRoot.shouldCreateSnapshot()) {
            logger.debug("Creating snapshot for aggregateRoot ${aggregateRoot.description()} at eventNumber=$currentEventNumber")
            val duration = measureTime {
                val serializedAggregate = adjustedObjectMapper.writeValueAsBytes(aggregateRoot.createSnapshot())
                snapshotsRepository.save(snapshotId(aggregateRoot.id), serializedAggregate, currentEventNumber)
            }
            logger.debug("Done creating snapshot for aggregateRoot ${aggregateRoot.description()} in $duration")
        }
    }

    private fun snapshotId(aggregateId: String): SnapshotId {
        val versionedCompanionObject: AggregateRootSnapshot.Versioned? = snapshotType.companionObjectInstance as? AggregateRootSnapshot.Versioned
        val version: Long = versionedCompanionObject?.version ?: 1L
        return SnapshotId(aggregateType.simpleName!!, version, aggregateId)
    }

    private data class RestoredAggregateAndEventNumber<AGGREGATE_ROOT>(
            val aggregate: AGGREGATE_ROOT,
            val eventNumber: Long?,
            val restored: Boolean
    )

    private fun AGGREGATE_ROOT.description(): String = "${aggregateType.simpleName}(id=${id})"
}