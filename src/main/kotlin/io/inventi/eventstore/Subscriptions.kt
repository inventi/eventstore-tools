package io.inventi.eventstore

import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.EventstoreEventListener.FailureType
import io.inventi.eventstore.util.LoggerDelegate
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean

abstract class Subscriptions<T : EventstoreEventHandler>(private val handlers: List<T>) : InitializingBean, DisposableBean {
    protected val logger by LoggerDelegate()

    override fun afterPropertiesSet() {
        startSubscriptions()
    }

    override fun destroy() {
        dropSubscriptions()
    }

    internal open fun startSubscriptions() {
        handlers.forEach {
            ensureSubscription(it)
            startSubscription(it, onSubscriptionFailure(it))
        }
    }

    internal open fun dropSubscriptions() {
        handlers.forEach {
            dropSubscription(it)
        }
    }

    protected abstract fun ensureSubscription(handler: T)

    protected abstract fun startSubscription(handler: T, onFailure: (FailureType) -> Unit)

    protected abstract fun dropSubscription(handler: T)

    private fun onSubscriptionFailure(handler: T): (FailureType) -> Unit = {
        when (it) {
            FailureType.EVENTSTORE_CLIENT_ERROR -> {
                logger.info("Resubscribing failed handler ${this::class.simpleName}")
                startSubscription(handler, onSubscriptionFailure(handler))
            }
            FailureType.UNEXPECTED_ERROR -> {
                logger.error("Handler ${handler::class.simpleName} failed. It will not be resubscribed.")
                dropSubscription(handler)
            }
        }
    }
}