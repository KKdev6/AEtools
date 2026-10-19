package com.anantenergies.bms.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsTest {

    @Test
    fun `datasheet range marks cells outside 2_30 to 3_75 V`() {
        assertTrue(CellLimits.isOutOfRange(2.299))
        assertTrue(CellLimits.isOutOfRange(3.751))
        assertFalse(CellLimits.isOutOfRange(2.30))
        assertFalse(CellLimits.isOutOfRange(3.75))
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
