package io.inventi.eventstore.eventhandler.tracing

import brave.Tracing
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.Cleanup
import io.inventi.eventstore.eventhandler.EventHandlerExtension
import io.inventi.eventstore.eventtracing.EventMetadataTraceExtractor
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import java.lang.reflect.Method

@Component
@ConditionalOnClass(Tracing::class)
@ConditionalOnBean(Tracing::class)
class TracingExtension(
        private val tracing: Tracing,
        private val objectMapper: ObjectMapper
) : EventHandlerExtension {
    private val extractor = EventMetadataTraceExtractor(tracing)

    override fun handle(method: Method, event: RecordedEvent): Cleanup {
        val metadata = objectMapper.readValue<Map<String, Any?>>(event.metadata)
        val tracer = tracing.tracer()

        val span = tracer.nextSpan(extractor.extractTraceContext(metadata)).start()
        val scope = tracer.withSpanInScope(span)

        return { error ->
            scope.close()

            if (error != null) {
                span.error(error)
            } else {
                span.finish()
            }
        }
    }
}