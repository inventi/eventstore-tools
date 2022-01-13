package io.inventi.eventstore.eventhandler.exception

class CheckpointIsOutdatedException(message: String) : RuntimeException(message)