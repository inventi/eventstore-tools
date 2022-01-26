package io.inventi.eventstore.eventhandler.util

import io.inventi.eventstore.eventhandler.annotation.Retry
import org.slf4j.Logger
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun Method.withRetries(logger: Logger, invokeMethod: Method.() -> Unit) {
    val retryAnnotations = getAnnotationsByType(Retry::class.java)

    if (retryAnnotations.isNotEmpty()) {
        invokeWithRetries(logger, invokeMethod, retryAnnotations.first())
    } else {
        invokeMethod()
    }
}

private fun Method.invokeWithRetries(logger: Logger, invokeMethod: Method.() -> Unit, retryAnnotation: Retry) {
    val retryableExceptions = retryAnnotation.exceptions
    val maxAttempts = retryAnnotation.maxAttempts
    val backoffDelayMillis = retryAnnotation.backoffDelayMillis

    var caughtRetryableException: Exception? = null

    for (attempt in (maxAttempts downTo 1)) {
        try {
            return this.invokeMethod()
        } catch (e: Exception) {
            if (retryableExceptions.any { e.isAOrIsCausedBy(it) }) {
                caughtRetryableException = e
                logger.debug("Caught retryable exception, will retry in $backoffDelayMillis ms: $caughtRetryableException")
                Thread.sleep(backoffDelayMillis)
                continue
            }
            throw RuntimeException(e)
        }
    }

    if (caughtRetryableException != null && caughtRetryableException is RuntimeException) {
        throw caughtRetryableException
    }
    throw RuntimeException(caughtRetryableException)
}