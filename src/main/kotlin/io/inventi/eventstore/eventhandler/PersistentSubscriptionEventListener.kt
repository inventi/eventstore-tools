package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.*
import io.inventi.eventstore.eventhandler.exception.EventAcknowledgementFailedException

class PersistentSubscriptionEventListener(
        private val eventListener: EventstoreEventListener,
        private val onFailure: (EventstoreEventListener.FailureType) -> Unit,
) : PersistentSubscriptionListener {
    override fun onClose(subscription: PersistentSubscription?, reason: SubscriptionDropReason, exception: Exception?) {
        onFailure(eventListener.onClose(reason, exception))
    }

    override fun onEvent(subscription: PersistentSubscription, event: RetryableResolvedEvent) {
        eventListener.onEvent(event)
        subscription.acknowledgeProcessedEvent(event)
    }

    private fun PersistentSubscription.acknowledgeProcessedEvent(event: ResolvedEvent) {
        try {
            this.acknowledge(event)
        } catch (e: Exception) {
            throw EventAcknowledgementFailedException("Could not acknowledge event with id: ${event.originalEvent().eventId}", e)
        }
    }
}