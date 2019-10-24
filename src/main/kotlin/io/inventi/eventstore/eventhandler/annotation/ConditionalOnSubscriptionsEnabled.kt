package io.inventi.eventstore.eventhandler.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.lang.annotation.Inherited


@Inherited
@ConditionalOnProperty("eventstore.subscriptions.enabled")
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionalOnSubscriptionsEnabled

