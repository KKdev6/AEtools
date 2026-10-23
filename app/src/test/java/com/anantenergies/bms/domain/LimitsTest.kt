package com.anantenergies.bms.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsTest {

    @Test
    fun `Li-ion range marks cells outside 2_80 to 4_25 V`() {
        assertTrue(CellLimits.isOutOfRange(2.799))
        assertTrue(CellLimits.isOutOfRange(4.251))
        assertFalse(CellLimits.isOutOfRange(2.80))
        assertFalse(CellLimits.isOutOfRange(4.25))
        // A healthy ~3.83 V cell from the real board is in range.
        assertFalse(CellLimits.isOutOfRange(3.83))
    }

    @Test
    fun `temperatures turn orange above 45 C and red above 50 C`() {
        assertEquals(TemperatureLevel.NORMAL, TemperatureLimits.level(-10.0))
        assertEquals(TemperatureLevel.NORMAL, TemperatureLimits.level(45.0))
        assertEquals(TemperatureLevel.WARM, TemperatureLimits.level(45.1))
        assertEquals(TemperatureLevel.WARM, TemperatureLimits.level(50.0))
        assertEquals(TemperatureLevel.HOT, TemperatureLimits.level(50.1))
        assertEquals(TemperatureLevel.HOT, TemperatureLimits.level(68.9))
    }
}
