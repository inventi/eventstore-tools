package io.inventi.eventstore.eventhandler.web.internal.v1

import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.annotation.ConditionalOnSubscriptionsEnabled
import io.inventi.eventstore.eventhandler.dao.ProcessedEvent
import io.inventi.eventstore.eventhandler.dao.ProcessedEventDao
import io.inventi.eventstore.util.IdConverter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/internal/v1/idempotent-event-handlers")
@ConditionalOnSubscriptionsEnabled
class EventHandlersManagement(
        private val eventHandlers: List<EventstoreEventHandler> = emptyList(),
        private val processedEventDao: ProcessedEventDao,
) {
    val handlerNames = eventHandlers.map { it::class.simpleName }

    @GetMapping
    fun getHandlers(): List<String?> {
        return handlerNames
    }

    @PostMapping("/{handlerName}/skip-event")
    fun skipEvent(
            @PathVariable handlerName: String,
            @RequestBody request: SkipEventRequest,
    ): ResponseEntity<*> {
        val handler = eventHandlers.find { it::class.simpleName == handlerName }
                ?: return handlerNotFound(handlerName)

        val eventId = request.eventId
                ?: return missingEventId()

        val record = ProcessedEvent(eventId, handler.streamName, request.eventStreamId, handler.groupName, request.eventType)
        processedEventDao.save(record)

        return ResponseEntity.status(HttpStatus.CREATED).body(record)
    }

    fun handlerNotFound(handlerName: String) =
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Handler with name '$handlerName' not found. Available handlers: $handlerNames")

    fun missingEventId() =
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Either ${SkipEventRequest::csharpEventId.name} or ${SkipEventRequest::javaEventId.name} must be provided")
}


data class SkipEventRequest(
        val javaEventId: String? = null,
        val csharpEventId: String? = null,
        val eventStreamId: String = "UNKNOWN_MANUALLY_INSERTED",
        val eventType: String,
) {
    val eventId =
            this.javaEventId
                    ?: this.csharpEventId?.let { IdConverter.guidToUuid(it) }
}