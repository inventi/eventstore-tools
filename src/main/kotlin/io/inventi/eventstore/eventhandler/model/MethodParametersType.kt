package io.inventi.eventstore.eventhandler.model

data class MethodParametersType(
        val dataType: Class<*>,
        val metadataType: Class<*>? = null
)