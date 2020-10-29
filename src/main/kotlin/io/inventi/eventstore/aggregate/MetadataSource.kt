package io.inventi.eventstore.aggregate

interface MetadataSource {
    fun get(aggregateId: String): Map<String, Any?>
}

class EmptyMetadataSource : MetadataSource {
    override fun get(aggregateId: String): Map<String, Any?> = emptyMap()
}