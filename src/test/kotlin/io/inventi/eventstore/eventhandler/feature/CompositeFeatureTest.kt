package io.inventi.eventstore.eventhandler.feature

import com.github.msemys.esjc.RecordedEvent
import io.inventi.eventstore.eventhandler.util.DataBuilder
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verifySequence
import org.junit.jupiter.api.Test

class CompositeFeatureTest {

    @Test
    fun `composes features`() {
        // given
        val feature1 = featureSpy()
        val feature2 = featureSpy()
        val feature3 = featureSpy()

        val event = DataBuilder.recordedEvent()
        val eventNumber = 42L
        val block = mockk<() -> Unit>(relaxed = true)

        // when
        CompositeFeature(feature1, feature2, feature3).wrap(event, eventNumber, block)

        // then
        verifySequence {
            feature3.wrap(event, eventNumber, any())
            feature2.wrap(event, eventNumber, any())
            feature1.wrap(event, eventNumber, any())
            block()
        }
    }

    private fun featureSpy() = spyk(object : EventListenerFeature {
        override fun wrap(event: RecordedEvent, originalEventNumber: Long, block: () -> Unit) {
            block()
        }
    })
}