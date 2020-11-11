package io.inventi.eventstore.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object ObjectMapperFactory {
    fun createDefaultObjectMapper() = ObjectMapper().apply {
        disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        registerModule(KotlinModule(nullIsSameAsDefault = true))
        registerModule(JavaTimeModule())
    }
}