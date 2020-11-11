package io.inventi.eventstore.aggregate

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.reflect.ClassPath

interface JsonEventDeserializer {
    fun deserialize(eventType: String, event: ByteArray?): Event
}

class JacksonEventDeserializer(private val objectMapper: ObjectMapper, private val eventPackage: String) : JsonEventDeserializer {
    override fun deserialize(eventType: String, event: ByteArray?): Event {
        return objectMapper.readValue(event, Class.forName("$eventPackage.$eventType")) as Event
    }
}

class WarmedUpJacksonEventDeserializer(objectMapper: ObjectMapper, eventPackage: String) : JsonEventDeserializer {
    private val objectReaders = ClassPath.from(ClassLoader.getSystemClassLoader())
            .getTopLevelClasses(eventPackage)
            .map { it.simpleName to objectMapper.readerFor(it.load()) }
            .toMap()

    override fun deserialize(eventType: String, event: ByteArray?): Event {
        return objectReaders[eventType]?.readValue(event)
                ?: throw IllegalArgumentException("Unknown event type: $eventType")
    }
}
