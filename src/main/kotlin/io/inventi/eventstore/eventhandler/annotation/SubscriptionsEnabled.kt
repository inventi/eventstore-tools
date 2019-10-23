package io.inventi.eventstore.eventhandler.annotation

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty


@ConditionalOnProperty("eventstore.subscriptions.enabled")
annotation class SubscriptionsEnabled
