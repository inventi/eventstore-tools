package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.PersistentSubscription
import io.inventi.eventstore.util.LoggerDelegate
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.TimeoutException

data class PersistentSubscriptionState(private var subscription: PersistentSubscription? = null) {
    companion object {
        private val logger: Logger by LoggerDelegate()

        private val STOPPAGE_TIMEOUT = Duration.ofSeconds(60)
    }

    internal val isActive get() = subscription != null

    fun update(newSubscription: PersistentSubscription) {
        drop()
        subscription = newSubscription
    }

    fun drop() {
        subscription?.let {
            subscription = null
            try {
                it.stop(STOPPAGE_TIMEOUT)
            } catch (e: TimeoutException) {
                logger.warn("Persistent subscription $subscription did not stop in time ($STOPPAGE_TIMEOUT)")
            }
        }
    }
}
