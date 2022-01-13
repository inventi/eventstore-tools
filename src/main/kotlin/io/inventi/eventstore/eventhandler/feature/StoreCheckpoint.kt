package io.inventi.eventstore.eventhandler.feature

import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao
import io.inventi.eventstore.eventhandler.exception.CheckpointIsOutdatedException

class StoreCheckpoint(
        private val handler: EventstoreEventHandler,
        private val checkpointDao: SubscriptionCheckpointDao,
) : EventListenerFeature {
    override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
        val affectedRows = checkpointDao.incrementCheckpoint(handler.groupName, handler.streamName, originalEventNumber)
        if (affectedRows == 0) {
            // We use event number checkpoint (which is monotonically increasing) as a fencing token against multiple subscription leaders
            throw CheckpointIsOutdatedException("Subscription (groupName: ${handler.groupName}, streamName: ${handler.streamName}) is trying to" +
                    "set an outdated checkpoint: $originalEventNumber. There might be multiple parallel subscriptions of the same type."
            )
        }
        block()
    }
}