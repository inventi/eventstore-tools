package io.inventi.eventstore.eventhandler.dao

data class SubscriptionCheckpoint(
        val groupName: String,
        val streamName: String,
        val checkpoint: Long? = null,
)
