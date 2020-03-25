package io.inventi.eventstore.eventhandler.annotation

import java.lang.annotation.Inherited
import kotlin.reflect.KClass


@Inherited
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
annotation class Retry(val exceptions: Array<KClass<*>>, val maxAttempts: Int = 3, val backoffDelayMillis: Long = 1000)