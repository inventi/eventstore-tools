package io.inventi.eventstore.aggregate

open class DummyRepository<T> : Repository<T> where T : AggregateRoot {
    var savedEventMessages = listOf<EventMessage>()
        private set
    var savedEvents = listOf<Event>()
        private set

    override fun save(aggregateRoot: T) {
        with(aggregateRoot) {
            save(id, getUncommittedEvents(), expectedVersion)
        }
    }

    override fun save(aggregateId: String, events: Iterable<EventMessage>, expectedVersion: Long) {
        savedEventMessages = savedEventMessages + events.toList()
        savedEvents = savedEvents + events.map { it.event }
    }

    override fun findOne(aggregateId: String): T? {
        throw NotImplementedError("DummyRepository#findOne should not be called")
    }

    inline fun <reified TEvent : Event> getEvents(): List<TEvent> {
        return savedEvents.filterIsInstance<TEvent>()
    }
}