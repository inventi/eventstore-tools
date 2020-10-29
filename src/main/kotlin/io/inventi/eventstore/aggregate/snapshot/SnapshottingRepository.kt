package io.inventi.eventstore.aggregate.snapshot

import io.inventi.eventstore.aggregate.AggregateRoot
import io.inventi.eventstore.aggregate.Repository

interface SnapshottingRepository<AGGREGATE_ROOT, SNAPSHOT> : Repository<AGGREGATE_ROOT>
        where AGGREGATE_ROOT : AggregateRoot,
              AGGREGATE_ROOT : SnapshottableAggregate<AGGREGATE_ROOT, SNAPSHOT>,
              SNAPSHOT : AggregateRootSnapshot<AGGREGATE_ROOT>