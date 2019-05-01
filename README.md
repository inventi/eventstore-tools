# eventstore-tools 
[![Release](https://jitpack.io/v/inventi/eventstore-tools.svg)](https://jitpack.io/#inventi/eventstore-tools)

Various Kotlin tools for [EventStore](https://eventstore.org/)

## Projection Init
Automatically uploads projections to the eventstore. Can detect that projection have changed and update it if necessary.
For no it only creates projections as continuous with emit enabled.
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
