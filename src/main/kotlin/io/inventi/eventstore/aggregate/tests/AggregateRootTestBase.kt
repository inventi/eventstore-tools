package io.inventi.eventstore.aggregate.tests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.inventi.eventstore.aggregate.AggregateRoot
import io.inventi.eventstore.aggregate.Event
import io.inventi.eventstore.aggregate.EventMessage
import io.inventi.eventstore.aggregate.snapshot.AggregateRootSnapshot
import io.inventi.eventstore.aggregate.snapshot.SnapshottableAggregate
import io.inventi.eventstore.util.LoggerDelegate
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import java.time.Instant
import kotlin.reflect.KClass

abstract class AggregateRootTestBase<T : AggregateRoot> {
    companion object {
        val logger: Logger by LoggerDelegate()
    }

    protected abstract fun createAggregateRoot(): T

    protected fun test(skipSnapshotTesting: Boolean = false, block: TestBuilder<T>.() -> Unit) {
        RunTest(block = block)
        val snapshottable = createAggregateRoot() is SnapshottableAggregate<*, *>
        if (!skipSnapshotTesting && snapshottable) {
            logger.info("------- Running snapshot test")
            RunTest(loadFromSnapshot = snapshottable, block = block)
        }
    }

    protected fun <B : AggregateRootTestBase<T>> B.withThis(block: B.() -> Unit) = with(this, block)

    private inner class RunTest(val loadFromSnapshot: Boolean = false, val block: TestBuilder<T>.() -> Unit) {

        val steps = TestBuilder<T>().apply(block)
        private val dummyRepository = DummyRepository<T>()

        init {
            doTest()
        }

        fun doTest() {
            var failedWith: Exception? = null
            val aggregate: T

            val events = getEventMessages()


            // TODO: Refactor this to a function
            if (!loadFromSnapshot) {
                aggregate = createAggregateRoot()
                aggregate.loadFromHistory(events)
            } else {
                if (events.isEmpty()) return logger.info("-------- SKIPPING snapshot tests because of empty events block")

                val initialAggregate = createAggregateRoot()
                initialAggregate.loadFromHistory(events)
                initialAggregate as SnapshottableAggregate<*, *>

                val snapshot = initialAggregate.createSnapshot()

                aggregate = createAggregateRoot()

                @Suppress("UNCHECKED_CAST")
                aggregate as SnapshottableAggregate<*, AggregateRootSnapshot<*>>
                aggregate.loadFromSnapshot(snapshot, 99)
            }

            try {
                aggregate.apply(steps.action)
            } catch (e: Exception) {
                failedWith = e
                logger.error("Failed to execute ${steps.action}", e)
            }

            steps.then.assert(extractGeneratedEvents(aggregate), failedWith)
        }

        private fun extractGeneratedEvents(aggregate: T): List<Event> {
            aggregate.commitChangesTo(dummyRepository)
            return dummyRepository.savedEvents
        }

        private fun getEventMessages() = steps.givenEvents.map { EventMessage(it, Instant.now()) }
    }
}

@DslMarker
annotation class AggregateRootTestDsl

@AggregateRootTestDsl
class TestBuilder<T> {
    var givenEvents = mutableListOf<Event>()
    lateinit var action: T.() -> Unit
    var then: ThenSpec = ThenSpec.GeneratesEventsStrict()

    fun given(block: GivenBuilder.() -> Unit) {
        givenEvents = GivenBuilder().apply(block).events
    }

    fun whenever(block: T.() -> Unit) {
        action = block
    }

    fun then(block: ThenBuilder.() -> Unit) {
        then = ThenBuilder().apply(block).thenSpec
    }
}

@AggregateRootTestDsl
class GivenBuilder {
    var events = mutableListOf<Event>()

    fun event(event: Event) {
        events.add(event)
    }

    fun events(events: List<Event>) {
        this.events.addAll(events)
    }

    fun eventsFromYamlResource(path: String, eventsPackage: String) {
        val file = ClassPathResource(path).file
        val parser = yamlFactory.createParser(file)

        val eventYamls = mapper
                .readValues<ObjectNode>(parser, ObjectNodeTypeReference())
                .readAll()

        val parsedEvents = eventYamls.map { yaml ->
            val type = yaml.get("eventType").textValue()
            val dataNode = yaml.get("data")
            val className = "$eventsPackage.$type"
            mapper.treeToValue(dataNode, Class.forName(className)) as Event

        }

        this.events.addAll(parsedEvents)
    }

    fun noEvents() {}

    companion object Mappers {
        private val yamlFactory = YAMLFactory()
        private val mapper = ObjectMapper(yamlFactory).apply {
            findAndRegisterModules()

            disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            registerModule(KotlinModule())
            registerModule(JavaTimeModule())

        }

        private class ObjectNodeTypeReference : TypeReference<ObjectNode>()

    }
}

@AggregateRootTestDsl
class ThenBuilder {
    lateinit var thenSpec: ThenSpec

    inline fun <reified T : Exception> failsWith() {
        return failsWith(T::class)
    }

    fun <T : Exception> failsWith(exception: KClass<T>) {
        thenSpec = ThenSpec.FailsWith(exception)
    }

    /**
     * Checks if aggregate generates exactly the amount of events
     * @see generatesSomeEvents for lenient check
     */
    fun generatesEvents(vararg events: Event) {
        thenSpec = ThenSpec.GeneratesEventsStrict(events.toList())
    }

    /**
     * Check if aggregate generates some events. Aggregate could generate more events
     * @see generatesEvents for strict check
     */
    fun generatesSomeEvents(vararg events: Event) {
        thenSpec = ThenSpec.GeneratesEventsLenient(events.toList())
    }

    /**
     * Check if aggregate generates exact number of events that pass assertions
     * @see eventThat to help build an assertion
     * @see generatesSomeEventsThat for lenient check
     */
    fun <EVENT_TYPE : Event> generatesEventsThat(vararg holders: StrictEventThatHolder<out EVENT_TYPE>) {
        thenSpec = ThenSpec.GeneratesEventsThatStrict(holders.toList())
    }

    /**
     * Check if aggregate generates some events that pass assertions
     * @see eventThat to help build an assertion
     * @see noSuchEvent to help build an assertion
     * @see generatesEventsThat for strict check
     */
    fun <EVENT_TYPE : Event> generatesSomeEventsThat(vararg holders: LenientEventThatHolder<out EVENT_TYPE>) {
        thenSpec = ThenSpec.GeneratesEventsThatLenient(holders.toList())
    }

    /**
     * Helper function used with generatesEventsThat to create AssertedEventHolder
     * @see generatesSomeEventsThat
     * @see AssertedEventHolder
     */
    inline fun <reified EVENT_TYPE : Event> eventThat(noinline assertBlock: (EVENT_TYPE) -> Unit = {}) =
            AssertedEventHolder(EVENT_TYPE::class, assertBlock)

    /**
     * Helper function used with generatesEventsThat to create NoSuchEventHolder
     * @see generatesSomeEventsThat
     * @see NoSuchEventHolder
     */
    fun <EVENT_TYPE : Event> noSuchEvent(eventType: KClass<EVENT_TYPE>) = NoSuchEventHolder(eventType)

    /**
     * Checks if aggregate generates no events. Intended to be used for idempotency checks
     */
    fun generatesNoEvents() {
        generatesEvents()
    }
}

@AggregateRootTestDsl
interface EventThatHolder<EVENT_TYPE : Event> {
    val eventType: KClass<EVENT_TYPE>

    fun assert(generatedEvents: List<Event>)
}

interface LenientEventThatHolder<EVENT_TYPE : Event> : EventThatHolder<EVENT_TYPE>
interface StrictEventThatHolder<EVENT_TYPE : Event> : EventThatHolder<EVENT_TYPE>

class NoSuchEventHolder<EVENT_TYPE : Event>(
        override val eventType: KClass<EVENT_TYPE>,
) : LenientEventThatHolder<EVENT_TYPE> {
    override fun assert(generatedEvents: List<Event>) {
        assertTrue(
                generatedEvents.allWithClass(eventType).isEmpty(),
                "Expected to not find event of type: ${eventType.simpleName}. Actual events: ${generatedEvents.map { it::class.simpleName }}",
        )
    }
}

class AssertedEventHolder<EVENT_TYPE : Event>(
        override val eventType: KClass<EVENT_TYPE>,
        val assertBlock: (EVENT_TYPE) -> Unit,
) : LenientEventThatHolder<EVENT_TYPE>, StrictEventThatHolder<EVENT_TYPE> {
    @Suppress("UNCHECKED_CAST")
    override fun assert(generatedEvents: List<Event>) {
        val eventsWithClass = generatedEvents.allWithClass(eventType) as List<EVENT_TYPE>
        val noEventsFoundResult = Result.failure<Unit>(
                AssertionError("No events of type $eventType was found. Found events: $generatedEvents")
        )

        eventsWithClass.fold(noEventsFoundResult) { assertionResult, eventType ->
            assertionResult.recoverCatching { assertBlock(eventType) }
        }.getOrThrow()
    }
}

interface EventHolder {
    val events: List<Event>

    fun assertEvents(generatedEvents: List<Event>) {
        events.forEach { event ->
            val possibleMatches = generatedEvents.allWithClass(event::class)
            val allEventNames = generatedEvents.map { it::class.simpleName }
            assertTrue(
                    possibleMatches.isNotEmpty(),
                    "Event of type ${event::class.simpleName} not found. Events found: $allEventNames",
            )
            assertThat(possibleMatches, hasItem(event))
        }
    }
}

@AggregateRootTestDsl
sealed class ThenSpec {
    abstract fun assert(generatedEvents: List<Event>, thrownException: Exception? = null)

    class GeneratesEventsStrict(override val events: List<Event> = emptyList()) : ThenSpec(), EventHolder {
        override fun assert(generatedEvents: List<Event>, thrownException: Exception?) {
            thrownException.assertNull()
            assertThat(
                    """
                    |Expected events: ${events.map { it::class.name() }}
                    |Actual events: ${generatedEvents.map { it::class.name() }}
                    """.trimMargin(),
                    generatedEvents,
                    hasSize(events.size)
            )
            assertEvents(generatedEvents)
        }
    }

    class GeneratesEventsLenient(override val events: List<Event> = emptyList()) : ThenSpec(), EventHolder {
        override fun assert(generatedEvents: List<Event>, thrownException: Exception?) {
            thrownException.assertNull()
            assertEvents(generatedEvents)
        }
    }

    class GeneratesEventsThatStrict<E : Event>(private val assertionHolders: List<StrictEventThatHolder<in E>>) : ThenSpec() {
        override fun assert(generatedEvents: List<Event>, thrownException: Exception?) {
            thrownException.assertNull()
            assertThat(
                    """
                    |Expected events: ${assertionHolders.map { it.eventType.name() }}
                    |Actual events: ${generatedEvents.map { it::class.name() }}
                    """.trimMargin(),
                    generatedEvents,
                    hasSize(assertionHolders.size)
            )
            assertionHolders.forEach { it.assert(generatedEvents) }
        }
    }

    class GeneratesEventsThatLenient<E : Event>(private val assertionHolders: List<LenientEventThatHolder<in E>>) : ThenSpec() {
        override fun assert(generatedEvents: List<Event>, thrownException: Exception?) {
            thrownException.assertNull()
            assertionHolders.forEach { it.assert(generatedEvents) }
        }
    }

    class FailsWith<T : Exception>(private val exception: KClass<T>) : ThenSpec() {
        override fun assert(generatedEvents: List<Event>, thrownException: Exception?) {
            val msg = "Exception '${thrownException}' was not thrown; Events generated: $generatedEvents"
            assertNotNull(thrownException, msg)
            assertThat(msg, thrownException, isA(exception.java))
        }
    }

    protected fun Exception?.assertNull() {
        assertNull(this, "Handler threw an exception: ${this?.let { it::class.simpleName }}, $this")
    }
}

private fun KClass<*>.name() = this.simpleName ?: "<unknown>"

private fun <E : Any> List<E>.allWithClass(clazz: KClass<*>) = filter { it::class == clazz }
