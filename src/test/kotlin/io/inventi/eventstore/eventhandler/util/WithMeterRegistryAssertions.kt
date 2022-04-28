package io.inventi.eventstore.eventhandler.util

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.amshove.kluent.shouldBeEqualTo
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.util.concurrent.TimeUnit

interface WithMeterRegistryAssertions {
    var meterRegistry: MeterRegistry

    fun assertGaugeWithTagHasValue(gaugeName: String, gaugeTag: Tag, expectedGaugeValue: Double) {
        Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted {
            valueOfGaugeWithTag(gaugeName, gaugeTag) shouldBeEqualTo expectedGaugeValue
        }
    }

    private fun valueOfGaugeWithTag(gaugeName: String, gaugeTag: Tag): Double {
        return meterRegistry.find(gaugeName)
            .tag(gaugeTag.key, gaugeTag.value)
            .gauge()!!
            .value()
    }
}