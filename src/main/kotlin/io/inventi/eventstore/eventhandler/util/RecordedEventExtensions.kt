package io.inventi.eventstore.eventhandler.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.util.script.OVERRIDE_EVENT_ID
import io.inventi.eventstore.util.script.OVERRIDE_EVENT_TYPE

fun RecordedEvent.effectiveEventId(objectMapper: ObjectMapper) =
        overriddenEventIdOrNull(objectMapper) ?: eventId.toString()

fun RecordedEvent.effectiveEventType(objectMapper: ObjectMapper): String =
        overriddenEventTypeOrNull(objectMapper) ?: eventType

fun RecordedEvent.overriddenEventIdOrNull(objectMapper: ObjectMapper) =
        objectMapper.runCatching {
            readTree(metadata).path(OVERRIDE_EVENT_ID).textValue()
        }.getOrNull()

private fun RecordedEvent.overriddenEventTypeOrNull(objectMapper: ObjectMapper) =
        objectMapper.runCatching {
            readTree(metadata).path(OVERRIDE_EVENT_TYPE).textValue()
        }.getOrNull()