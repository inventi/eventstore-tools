# eventstore-tools

[![Release](https://jitpack.io/v/inventi/eventstore-tools.svg)](https://jitpack.io/#inventi/eventstore-tools)

Various Kotlin tools for [EventStore](https://eventstore.org/)

## Projection Init

Automatically uploads projections to the eventstore. Can detect that projection have changed and update it if necessary.
For now it only creates projections as continuous with emit enabled. Below is possible configuration options:

```yaml
 eventstore:
   endpoint: http://localhost:2113
   username: admin
   password: changeit
   projections-init:
     enabled: true #enable/disable projections initialization
     folder: "/js" #classpath location where to find projections
     updateOnConflict: true #update when version tags differ
     overwriteWithoutVersion: true #update if projection has no version tag
     failOnError: true #fail or continue on unknown error during the update process
```

## Subscriptions

There are two ways to consume events from EventStore using this library:

- Persistent subscriptions
- Catch-up subscriptions

### Persistent subscriptions

Persistent subscriptions use competing consumers message pattern. These subscriptions should be used when event order
does not matter, and you wish to load balance your event consumers. By default, it uses **round robin** strategy to
distribute events for given consumer group (you can override it in PersistentSubscriptionHandler). Read more about persistent subscriptions
in [EventStoreDB docs](https://developers.eventstore.com/server/v21.10/persistent-subscriptions/)

In order to register persistent subscription, you must
extend [PersistentSubscriptionHandler](src/main/kotlin/io/inventi/eventstore/eventhandler/PersistentSubscriptionHandler.kt)
and mark that class as a spring bean. e.g.:

```kotlin
@Component
class MyPersistentSubscriptionHandler(
        // this can also be a category stream, e.g.: $ce-MyCategory
        override val streamName: String = "stream_to_consume_from",
        override val groupName: String = "consumer_group",
) : PersistentSubscriptionHandler {

    @EventHandler
    fun onEvent(event: Event) {
        // handle event
    }
}
```

Each consumed event stores an event record to the database in `eventstore_subscription_processed_event` table. This
allows us to skip events and/or ignore events which were copy-replaced (e.g. if event contains already handled event id
in metadata field `overrideEventId`, that event will be skipped).

Updates to `PersistentSubscription` are supported, yet disabled by default. If a subscription settings are updated, all
current consumers are dropped.

To enable forced updates, set `eventstore.subscriptions.updateEnable` to true:

```yaml
eventstore:
  subscriptions:
    updateEnabled: true
```

### Catch-up subscriptions

These are simple subscriptions where the client requests events from certain checkpoint. This checkpoint must be managed
by the client itself (we store it in `eventstore_subscription_checkpoint` table). You should use these subscriptions
when event ordering matters. By default, this library ensures that a single catch-up subscription instance is running
within the consumer group using database based leader election. You can disable leader election and have multiple
instances consuming the same events with this setting:

```yaml
eventstore:
  subscriptions:
    enableCatchupSubscriptionLeaderElection: false
```

In order to register catch-up subscription, you must
extend [CatchupSubscriptionHandler](src/main/kotlin/io/inventi/eventstore/eventhandler/CatchupSubscriptionHandler.kt)
and mark that class as a spring bean. e.g.:

```kotlin
@Component
class MyCatchupSubscriptionHandler(
        // this can also be a category stream, e.g.: $ce-MyCategory
        override val streamName: String = "stream_to_consume_from",
        override val groupName: String = "consumer_group",
) : CatchupSubscriptionHandler {

    @EventHandler
    fun onEvent(event: Event) {
        // handle event
    }
}
```

### Event handling extensions

It is possible to hook into event handling. You can hook BEFORE and/or AFTER handling an event. You need to
implement [EventHandlerExtension](src/main/kotlin/io/inventi/eventstore/eventhandler/EventHandlerExtension.kt) by overriding `handle` method, e.g.:

Define the extension:
```kotlin
class MyHandlerExtension : EventHandlerExtension {
    override fun handle(method: Method, event: RecordedEvent): Cleanup {
        // do something before event is handler
        return {
            // do something after event is handled
        }
    }
}
```

Use the extension inside your handler:
```kotlin
@Component
class MyCatchupSubscriptionHandler(
        override val streamName: String = "stream_to_consume_from",
        override val groupName: String = "consumer_group",
        // register your extensions here
        override val extensions: List<EventHandlerExtension> = listOf(MyHandlerExtension())
) : CatchupSubscriptionHandler {

    @EventHandler
    fun onEvent(event: Event) {
        // handle event
    }
}
```

### Skipping an event

You can skip an event for specific event handler.

#### Listing event handlers

```bash
curl http://<HOST>:8080/internal/v1/idempotent-event-handlers/

["Handler1","Handler2","Handler3","Handler3","Handler5"]
```

#### Skipping an event

You must provide either JAVA UUID (obtained from ESJC - ES Java Client) or C# GUID (obtained from EventStore)

- Java

```bash
curl <HOST>:8080/internal/v1/idempotent-event-handlers/<HANDLER>/skip-event -XPOST -d '{"javaEventId": "<EVENT_ID>", "eventType": "<EVENT_TYPE>"}' -H content-type:application/json
```

- C#

```bash
curl <HOST>:8080/internal/v1/idempotent-event-handlers/<HANDLER>/skip-event -XPOST -d '{"csharpEventId": "<EVENT_ID>", "eventType": "<EVENT_TYPE>"}' -H content-type:application/json
```

## EventPublisher

Serializes and appends given object to configured stream in EventStore.

EventPublisher works asynchronously, and returns Future. Accepts eventType and Event data supply lamda which should
return event data to publish

To be able to use EventPublisher, set `eventstore.eventPublisher.streamName` in application config:

```yaml
eventstore:
  eventPublisher:
    streamName: "stream-to-publish-events-to"
```
 