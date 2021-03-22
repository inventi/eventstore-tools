package io.inventi.eventstore.eventhandler.model

data class EventIds(
        val overridden: String?,
        val current: String,
) {
    val effective: String
        get() {
            return overridden ?: current
        }
}