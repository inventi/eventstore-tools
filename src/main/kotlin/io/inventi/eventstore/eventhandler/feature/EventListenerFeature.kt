package io.inventi.eventstore.eventhandler.feature

import com.github.msemys.esjc.RecordedEvent

interface EventListenerFeature {
    fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit)
}