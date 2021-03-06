# eventstore-tools 
[![Release](https://jitpack.io/v/inventi/eventstore-tools.svg)](https://jitpack.io/#inventi/eventstore-tools)

Various Kotlin tools for [EventStore](https://eventstore.org/)

## Projection Init
Automatically uploads projections to the eventstore. Can detect that projection have changed and update it if necessary.
For now it only creates projections as continuous with emit enabled.
Below is possible configuration options:

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

## IdempotentEventHandler

IdempotentEventHandler uses [PersistentSubscriptions](https://eventstore.org/docs/dotnet-api/competing-consumers/index.html#persistent-subscription-settings). 
Updates to `PersistentSubscription` are supported, yet disabled by default.
If a subscription settings are updated, all current consumers are dropped.

To enable forced updates, set `eventstore.subscriptions.updateEnable` to true:

```yaml
eventstore:
  subscriptions:
    updateEnabled: true
```

### Hooks

It is possible to hook into event handling. You can hook BEFORE and/or AFTER handling an event.
You need to implement `io.inventi.eventstore.eventhandler.EventHandlerExtension` by overriding `beforeHandle` and/or `afterHandle` methods and pass one or more implementations as `handlerExtensions` parameter for `IdempotentEventHandler`.
If more than one Extension is provided, `beforeHandle` is called in that order which they are provided in the array. `afterHandle`, on the other hand, is applied in reverse order.

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

EventPublisher works asynchronously, and returns Future.
Accepts eventType and Event data supply lamda which should return event data to publish  

To be able to use EventPublisher, set `eventstore.eventPublisher.streamName` in application config:

```yaml
eventstore:
  eventPublisher:
    streamName: "stream-to-publish-events-to"
```
 