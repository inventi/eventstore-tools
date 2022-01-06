package io.inventi.eventstore.eventhandler

interface EventstoreEventHandler {
    val streamName: String

    val groupName: String

    val initialPosition: InitialPosition
        get() = InitialPosition.FromBeginning

    val extensions: List<EventHandlerExtension>
        get() = emptyList()
}