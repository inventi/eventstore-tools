package io.inventi.eventstore.aggregate.metadata

class EmptyMetadataSource : MetadataSource {
    override fun get(aggregateId: String): Map<String, Any?> = emptyMap()
}