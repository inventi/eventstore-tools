package io.inventi.eventstore.eventhandler

import com.github.msemys.esjc.CatchUpSubscription
import io.inventi.eventstore.util.LoggerDelegate
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.TimeoutException

data class CatchupSubscriptionState(private var subscription: CatchUpSubscription? = null) {
    companion object {
        private val logger: Logger by LoggerDelegate()

        private val STOPPAGE_TIMEOUT = Duration.ofSeconds(60)
    }

    internal val isActive get() = subscription != null
    internal val gaugeValue get() = if (isActive) 1 else 0

    fun update(newSubscription: CatchUpSubscription) {
        logger.debug("Updating from catch-up subscription $subscription to new subscription $newSubscription")
        drop()
        subscription = newSubscription
    }

    fun drop() {
        logger.debug("Dropping catch-up subscription $subscription")
        subscription?.let {
            subscription = null
            try {
                it.stop(STOPPAGE_TIMEOUT)
            } catch (e: TimeoutException) {
                logger.warn("Catchup subscription $subscription did not stop in time ($STOPPAGE_TIMEOUT)")
            }
        }
    }
}
