package io.inventi.eventstore.projector.model

data class MethodParametersType(
        val dataType: Class<*>,
        val metadataType: Class<*>? = null
)