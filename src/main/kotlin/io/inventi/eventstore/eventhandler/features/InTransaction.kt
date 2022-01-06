package io.inventi.eventstore.eventhandler.features

import com.github.msemys.esjc.RecordedEvent
import org.springframework.transaction.support.TransactionTemplate

class InTransaction(private val transactionTemplate: TransactionTemplate) : EventListenerFeature {
    override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
        transactionTemplate.execute { block() }
    }
}