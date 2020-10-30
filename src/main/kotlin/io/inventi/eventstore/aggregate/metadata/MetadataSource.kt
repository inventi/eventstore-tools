package io.inventi.eventstore.aggregate.metadata

interface MetadataSource {
    fun get(aggregateId: String): Map<String, Any?>
}