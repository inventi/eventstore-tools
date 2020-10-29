package io.inventi.eventstore.aggregate


/**
 * Marker interface for event objects that represent any change in domain state.
 * May contain primitive values or simple serializable value objects, but no behavior.
 * Events are serialized and stored forever, and can be replayed at any time.
 * Once in production, events cannot change but must be versioned instead.
 */
interface Command
