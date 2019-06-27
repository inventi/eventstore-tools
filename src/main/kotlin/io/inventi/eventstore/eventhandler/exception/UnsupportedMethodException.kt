package io.inventi.eventstore.eventhandler.exception

import java.lang.RuntimeException

class UnsupportedMethodException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)