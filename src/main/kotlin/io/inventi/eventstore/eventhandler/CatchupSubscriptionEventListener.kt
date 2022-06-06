package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.CatchUpSubscription
import com.github.msemys.esjc.CatchUpSubscriptionListener
import com.github.msemys.esjc.ResolvedEvent
import com.github.msemys.esjc.SubscriptionDropReason

class CatchupSubscriptionEventListener(
        private val eventListener: EventstoreEventListener,
        private val subscriptionState: CatchupSubscriptionState,
        private val onFailure: (EventstoreEventListener.FailureType) -> Unit,
) : CatchUpSubscriptionListener {
    override fun onClose(subscription: CatchUpSubscription?, reason: SubscriptionDropReason, exception: Exception?) {
        if (subscriptionState.isActive) {
            onFailure(eventListener.onClose(reason, exception))
        }
    }

    override fun onEvent(subscription: CatchUpSubscription, event: ResolvedEvent) {
        eventListener.onEvent(event)
    }
}