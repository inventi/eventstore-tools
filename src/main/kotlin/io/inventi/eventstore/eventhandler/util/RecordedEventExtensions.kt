package io.inventi.eventstore.eventhandler.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.msemys.esjc.RecordedEvent

const val OVERRIDE_EVENT_ID = "overrideEventId"

fun RecordedEvent.effectiveEventId(objectMapper: ObjectMapper) =
        overriddenEventIdOrNull(objectMapper) ?: eventId.toString()

fun RecordedEvent.overriddenEventIdOrNull(objectMapper: ObjectMapper) =
        objectMapper.runCatching {
            readTree(metadata).path(OVERRIDE_EVENT_ID).textValue()
        }.getOrNull()