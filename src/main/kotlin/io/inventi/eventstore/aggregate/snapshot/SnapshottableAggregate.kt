package io.inventi.eventstore.aggregate.snapshot

import io.inventi.eventstore.aggregate.AggregateRoot

interface Snapshottable<SNAPSHOT> {
    fun snapshot(): SNAPSHOT
    fun load(snapshot: SNAPSHOT)
}

interface AggregateRootSnapshot<T : AggregateRoot> {
    interface Versioned {
        /**
         * Used to invalidate snapshots
         * Should be incremented when there are breaking changes in aggregate root
         * i.e. new fields added, bugfixes introduced which change event application logic
         */
        val version: Long
    }
}

interface SnapshottableAggregate<AGGREGATE_ROOT : AggregateRoot, SNAPSHOT : AggregateRootSnapshot<AGGREGATE_ROOT>> {
    fun createSnapshot(): SNAPSHOT
    fun loadFromSnapshot(snapshot: SNAPSHOT, eventNumber: Long)

    fun shouldCreateSnapshot(): Boolean
}


