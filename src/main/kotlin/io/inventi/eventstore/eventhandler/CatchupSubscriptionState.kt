package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.CatchUpSubscription
import java.time.Duration

data class CatchupSubscriptionState(internal var subscription: CatchUpSubscription? = null) {
    internal val isActive get() = subscription != null
    internal val gaugeValue get() = if (isActive) 1 else 0

    fun update(newSubscription: CatchUpSubscription) {
        drop()
        subscription = newSubscription
    }

    fun drop() {
        subscription?.let {
            subscription = null
            it.stop(Duration.ofSeconds(60))
        }
    }
}
