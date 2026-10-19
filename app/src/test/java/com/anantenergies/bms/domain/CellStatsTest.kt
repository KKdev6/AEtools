package com.anantenergies.bms.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellStatsTest {

    /** The cell voltages from the test vector in CLAUDE.md. */
    private val specCells = listOf(
        3301, 3298, 3305, 3300, 3296, 3303, 3311, 3299, 3302, 3297, 3300, 3292, 3304, 3309, 3301, 3298,
    ).map { it / 1000.0 }

    @Test
    fun `highest, lowest, average and spread of the spec cells`() {
        val stats = CellStats.of(specCells)!!
        assertEquals(6, stats.highestIndex)
        assertEquals(3.311, stats.highest, 1e-9)
        assertEquals(11, stats.lowestIndex)
        assertEquals(3.292, stats.lowest, 1e-9)
        assertEquals(3.301, stats.average, 1e-9)
        assertEquals(0.019, stats.spread, 1e-9)
    }

    @Test
    fun `ties go to the first cell`() {
        val stats = CellStats.of(listOf(3.30, 3.31, 3.31, 3.29, 3.29))!!
        assertEquals(1, stats.highestIndex)
        assertEquals(3, stats.lowestIndex)
    }

    @Test
    fun `no cells gives no stats`() {
        assertNull(CellStats.of(emptyList()))
    }

    @Test
    fun `chart window for the spec cells matches the mockup`() {
        val window = ChartWindow.around(CellStats.of(specCells)!!)
        assertEquals(3.280, window.low, 1e-9)
        assertEquals(3.320, window.high, 1e-9)
    }

    @Test
    fun `chart window widens to fit a weak cell`() {
        val cells = List(15) { 3.300 } + 3.150
        val window = ChartWindow.around(CellStats.of(cells)!!)
        assertTrue(window.low < 3.150)
        assertTrue(window.high > 3.300)
        cells.forEach { assertTrue(window.fraction(it) in 0f..1f) }
    }

    @Test
    fun `chart window is at least plus or minus 20 mV`() {
        val window = ChartWindow.around(CellStats.of(List(16) { 3.300 })!!)
        assertEquals(3.280, window.low, 1e-9)
        assertEquals(3.320, window.high, 1e-9)
    }

    @Test
    fun `current flow has an idle band around zero`() {
        assertEquals(CurrentFlow.DISCHARGING, CurrentFlow.of(-12.4))
        assertEquals(CurrentFlow.CHARGING, CurrentFlow.of(0.2))
        assertEquals(CurrentFlow.IDLE, CurrentFlow.of(0.19))
        assertEquals(CurrentFlow.IDLE, CurrentFlow.of(-0.19))
        assertEquals(CurrentFlow.IDLE, CurrentFlow.of(0.0))
    }
}
