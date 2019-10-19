package io.inventi.eventstore.util

import org.junit.Test
import org.junit.jupiter.api.Assertions.*

internal class IdConverterTest {
    @Test
    fun guuidToUuid() {
        val cSharpGuuid = "35fc626d-d1a8-ff45-801f-953b34c9a459"
        val javaUuid =    "6d62fc35-a8d1-45ff-801f-953b34c9a459"

        assertEquals( javaUuid, IdConverter.guidToUuid(cSharpGuuid))
    }
}