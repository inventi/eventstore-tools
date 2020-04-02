package io.inventi.eventstore.eventtracing

import brave.Tracing

interface TraceContextInjector<T> {
    fun injectTraceContext(traceCarrier: T)
}

private typealias InjectorMetadataCarrier = MutableMap<String, Any?>

class EventMetadataTraceInjector(private val tracing: Tracing) : TraceContextInjector<InjectorMetadataCarrier> {
    private val injector = tracing.propagation().injector(EVENT_TRACING_SETTER)

    override fun injectTraceContext(traceCarrier: InjectorMetadataCarrier) {
        injector.inject(tracing.currentTraceContext().get(), traceCarrier)
    }

    companion object {
        private val EVENT_TRACING_SETTER = { carrier: InjectorMetadataCarrier, key: String, value: String ->
            carrier[key] = value
        }
    }
}
