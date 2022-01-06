package io.inventi.eventstore.eventhandler.features

import com.github.msemys.esjc.RecordedEvent

interface EventListenerFeature {
    fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit)
}