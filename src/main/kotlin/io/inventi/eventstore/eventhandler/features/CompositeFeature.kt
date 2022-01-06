package io.inventi.eventstore.eventhandler.features

import com.github.msemys.esjc.RecordedEvent

class CompositeFeature(private vararg val features: EventListenerFeature) : EventListenerFeature {
    override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
        val composedFeatures = features.toList().fold({ block() }) { acc, feature ->
            { feature.wrap(event, originalEventNumber, acc) }
        }

        composedFeatures()
    }
}