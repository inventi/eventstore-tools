package io.inventi.eventstore.eventhandler.dao

data class SubscriptionInitialPosition(
    val groupName: String,
    val streamName: String,
    val initialPosition: Long,
)
