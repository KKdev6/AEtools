package com.anantenergies.bms.drivers.demo

import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.CellLimits
import com.anantenergies.bms.domain.CellStats
import com.anantenergies.bms.domain.Protection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPackModelTest {

    /** Steps [model] for [seconds] in 2 s steps and returns every snapshot. */
    private fun run(model: DemoPackModel, seconds: Int): List<BmsSnapshot> =
        (0 until seconds / 2).map {
            model.step(2.0)
            model.snapshot(timestamp = it * 2_000L)
        }

    @Test
    fun `starts at the spec test vector values`() {
        val snapshot = DemoPackModel(seed = 1).snapshot(timestamp = 0)
        assertEquals(16, snapshot.cellCount)
        assertEquals(16, snapshot.cellVoltages.size)
        assertEquals(78, snapshot.soc)
        assertEquals(-12.4, snapshot.current, 1e-9)
        assertEquals(listOf(31.0, 27.4, 28.1), snapshot.temperatures.map { it.celsius })
        assertEquals(listOf("T1", "T2", "T3"), snapshot.temperatures.map { it.label })
    }

    @Test
    fun `normal pack stays in range for an hour`() {
        for (seed in 1L..5L) {
            run(DemoPackModel(seed), seconds = 3_600).forEach { s ->
                s.cellVoltages.forEach { assertFalse("cell $it out of range", CellLimits.isOutOfRange(it)) }
                assertTrue("current ${s.current}", s.current in -20.0..15.0)
                s.temperatures.forEach { assertTrue("temp ${it.celsius}", it.celsius in 25.0..35.0) }
                assertTrue(s.soc in 0..100)
                assertTrue(s.protections.isEmpty())
                assertTrue(s.chargeMosOn && s.dischargeMosOn)
                assertEquals(false, s.heaterOn)
            }
        }
    }

    @Test
    fun `cell voltages sit around 3_30 V in the middle of the charge range`() {
        val snapshot = DemoPackModel(seed = 1).snapshot(timestamp = 0)
        snapshot.cellVoltages.forEach { assertEquals(3.30, it, 0.03) }
    }

    @Test
    fun `balancing only above 3_30 V with a spread over 15 mV`() {
        assertEquals(emptySet<Int>(), DemoPackModel.balancing(listOf(3.310, 3.320, 3.306)))
        assertEquals(emptySet<Int>(), DemoPackModel.balancing(listOf(3.280, 3.299, 3.250)))
        assertEquals(setOf(1, 2), DemoPackModel.balancing(listOf(3.300, 3.320, 3.316, 3.305)))
    }

    @Test
    fun `charging scenario charges`() {
        val model = DemoPackModel(seed = 2).apply { scenario = DemoScenario.CHARGING }
        val last = run(model, seconds = 30).last()
        assertTrue("current ${last.current}", last.current > 5)
    }

    @Test
    fun `high spread scenario has one weak cell`() {
        val model = DemoPackModel(seed = 3).apply { scenario = DemoScenario.HIGH_SPREAD }
        run(model, seconds = 120).drop(5).forEach { s ->
            val stats = CellStats.of(s.cellVoltages)!!
            assertEquals(DemoPackModel.WEAK_CELL, stats.lowestIndex)
            assertTrue("spread ${stats.spread}", stats.spread > 0.050)
        }
    }

    @Test
    fun `over-temperature scenario trips discharge protection`() {
        val model = DemoPackModel(seed = 4).apply { scenario = DemoScenario.OVER_TEMPERATURE }
        val last = run(model, seconds = 60).last()
        assertTrue(Protection.DISCHARGE_OVER_TEMPERATURE in last.protections)
        assertFalse(last.dischargeMosOn)
        assertTrue(last.chargeMosOn)
        assertTrue(last.temperatures.first().celsius > 60)
        assertTrue(last.current >= 0)
    }

    @Test
    fun `cell overvoltage scenario trips charge protection`() {
        val model = DemoPackModel(seed = 5).apply { scenario = DemoScenario.CELL_OVERVOLTAGE }
        val last = run(model, seconds = 20).last()
        assertTrue(Protection.CELL_OVERVOLTAGE in last.protections)
        assertFalse(last.chargeMosOn)
        assertTrue(last.dischargeMosOn)
        assertEquals(100, last.soc)
        assertTrue(last.current <= 0)
        assertTrue(CellLimits.isOutOfRange(last.cellVoltages[DemoPackModel.HIGH_CELL]))
        assertTrue(DemoPackModel.HIGH_CELL in last.balancingCells)
    }

    @Test
    fun `protections clear after returning to normal`() {
        val model = DemoPackModel(seed = 6).apply { scenario = DemoScenario.OVER_TEMPERATURE }
        run(model, seconds = 60)
        model.scenario = DemoScenario.NORMAL
        val last = run(model, seconds = 600).last()
        assertTrue(last.protections.isEmpty())
        assertTrue(last.dischargeMosOn)
    }
}
