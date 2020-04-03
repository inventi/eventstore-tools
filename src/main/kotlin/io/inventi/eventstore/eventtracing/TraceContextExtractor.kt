package io.inventi.eventstore.eventtracing

import brave.Tracing
import brave.propagation.TraceContextOrSamplingFlags

interface TraceContextExtractor<T> {
    fun extractTraceContext(traceCarrier: T): TraceContextOrSamplingFlags
}

private typealias ExtractorMetadataCarrier = Map<String, Any?>

class EventMetadataTraceExtractor(tracing: Tracing) : TraceContextExtractor<ExtractorMetadataCarrier> {
    private val extractor = tracing.propagation().extractor(EVENT_TRACING_GETTER)

    override fun extractTraceContext(traceCarrier: ExtractorMetadataCarrier): TraceContextOrSamplingFlags {
        return extractor.extract(traceCarrier)
    }

    companion object {
        private val EVENT_TRACING_GETTER = { carrier: ExtractorMetadataCarrier, key: String ->
            carrier[key] as? String
        }
    }
}
