package io.inventi.eventstore.aggregate.metadata

class CompositeMetadataSource(private vararg val sources: MetadataSource) : MetadataSource {
    override fun get(aggregateId: String): Map<String, Any?> = sources.fold(emptyMap()) { metadata, nextSource ->
        metadata + nextSource.get(aggregateId)
    }
}