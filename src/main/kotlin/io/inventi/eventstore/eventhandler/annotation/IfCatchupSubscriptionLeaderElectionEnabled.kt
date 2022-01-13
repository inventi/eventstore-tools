package io.inventi.eventstore.eventhandler.annotation

import io.inventi.eventstore.eventhandler.CatchupSubscriptionHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.lang.annotation.Inherited


@Inherited
@ConditionalOnProperty("eventstore.subscriptions.enableCatchupSubscriptionLeaderElection", matchIfMissing = true)
@ConditionalOnBean(CatchupSubscriptionHandler::class)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IfCatchupSubscriptionLeaderElectionEnabled

