package io.inventi.eventstore.eventhandler.util

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.junit.Assert.assertEquals
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.util.concurrent.TimeUnit

interface WithMeterRegistryAssertions {
    var meterRegistry: MeterRegistry

    private val assertDelta: Double
        get() = 1e-6

    fun assertGaugeWithTagHasValue(gaugeName: String, meterTag: String, meterValue: Double) {
        Awaitility.await().pollDelay(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertEquals(valueOfGaugeWithTag(gaugeName, meterTag), meterValue, assertDelta)
        }
    }

    private fun valueOfGaugeWithTag(gaugeName: String, meterTag: String): Double {
        return meterRegistry.find(gaugeName)
            .gauges()
            .find { it.hasTag(meterTag) }!!
            .value()
    }

    private fun Gauge.hasTag(tag: String) = id.tags.find { it.value == tag } != null
}