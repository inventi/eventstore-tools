package io.inventi.eventstore.eventhandler.exception

import com.github.msemys.esjc.EventStoreException

class EventAcknowledgementFailedException(message: String, cause: Throwable) : EventStoreException(message, cause)