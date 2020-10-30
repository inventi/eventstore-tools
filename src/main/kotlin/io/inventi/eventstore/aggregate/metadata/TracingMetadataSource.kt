package io.inventi.eventstore.aggregate.metadata

import io.inventi.eventstore.eventtracing.EventMetadataTraceInjector

class TracingMetadataSource(private val traceInjector: EventMetadataTraceInjector) : MetadataSource {
    override fun get(aggregateId: String): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>()
        traceInjector.injectTraceContext(metadata)
        return metadata
    }
}