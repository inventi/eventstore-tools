package io.inventi.eventstore.aggregate.metadata

class AggregateIdMetadataSource : MetadataSource {
    override fun get(aggregateId: String) = mapOf(AGGREGATE_ID to aggregateId)

    companion object {
        const val AGGREGATE_ID = "aggregateId"
    }
}