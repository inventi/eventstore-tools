package io.inventi.eventstore.eventhandler.features

import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.EventstoreEventHandler
import io.inventi.eventstore.eventhandler.dao.SubscriptionCheckpointDao

class StoreCheckpoint(
        private val handler: EventstoreEventHandler,
        private val checkpointDao: SubscriptionCheckpointDao,
) : EventListenerFeature {
    override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
        checkpointDao.setCheckpoint(handler.groupName, handler.streamName, originalEventNumber)
        block()
    }
}