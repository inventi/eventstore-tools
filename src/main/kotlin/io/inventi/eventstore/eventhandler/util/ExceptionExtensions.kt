package io.inventi.eventstore.eventhandler.util

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun Exception?.isAOrIsCausedBy(matchClass: KClass<*>): Boolean {
    var exception: Throwable? = this
    while (exception != null) {
        if (exception::class.isSubclassOf(matchClass)) {
            return true
        }
        exception = exception.cause
    }

    return false
}