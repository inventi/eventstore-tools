package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.PersistentSubscription
import java.time.Duration

data class PersistentSubscriptionState(internal var subscription: PersistentSubscription? = null) {
    internal val isActive get() = subscription != null

    fun update(newSubscription: PersistentSubscription) {
        drop()
        subscription = newSubscription
    }

    fun drop() {
        subscription?.let {
            subscription = null
            it.stop(Duration.ofSeconds(10))
        }
    }
}
