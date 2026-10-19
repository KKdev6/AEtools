package com.anantenergies.bms.drivers.demo

import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.CellLimits
import com.anantenergies.bms.domain.Protection
import com.anantenergies.bms.domain.TemperatureReading
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

enum class DemoScenario(val label: String) {
    NORMAL("Normal"),
    CHARGING("Charging"),
    HIGH_SPREAD("High spread (one weak cell)"),
    OVER_TEMPERATURE("Over-temperature protection"),
    CELL_OVERVOLTAGE("Cell overvoltage protection"),
    BLUETOOTH_DROPPED("Bluetooth dropped"),
}

/**
 * A simulated 16S LiFePO4 pack. Pure and deterministic for a given seed, so tests can step it.
 * It starts at the values of the test vector in CLAUDE.md: 78 %, −12.4 A, 31.0 / 27.4 / 28.1 °C.
 */
class DemoPackModel(seed: Long) {
    private val random = Random(seed)

    var scenario = DemoScenario.NORMAL

    private var lastScenario = scenario
    private var soc = 78.0
    private var current = -12.4
    private var targetCurrent = -12.4
    private var secondsToNewTarget = 20.0
    private var ambient = 27.4
    private var boardTemp = 31.0
    private val cells = DoubleArray(CELL_COUNT)
    private var overTemperature = false
    private var protections = emptyList<Protection>()

    init {
        updateCells()
        updateProtections()
    }

    /** Advances the simulation by [seconds] of real time. */
    fun step(seconds: Double) {
        val dt = seconds.coerceIn(0.0, MAX_STEP_S)
        updateCurrent(dt)
        soc = if (scenario == DemoScenario.CELL_OVERVOLTAGE) {
            100.0
        } else {
            (soc + current * dt / 3600 / CAPACITY_AH * 100 * SPEEDUP).coerceIn(0.0, 100.0)
        }
        updateTemperatures(dt)
        updateCells()
        updateProtections()
    }

    fun snapshot(timestamp: Long): BmsSnapshot {
        val voltages = cells.toList()
        return BmsSnapshot(
            packVoltage = round(voltages.sum(), 2),
            current = round(current, 2),
            soc = soc.roundToInt(),
            cycleCount = 42,
            cellVoltages = voltages,
            balancingCells = balancing(voltages),
            temperatures = listOf(
                TemperatureReading("T1", round(boardTemp, 1)),
                TemperatureReading("T2", round(ambient, 1)),
                TemperatureReading("T3", round(ambient + 0.7, 1)),
            ),
            chargeMosOn = Protection.CELL_OVERVOLTAGE !in protections,
            dischargeMosOn = Protection.DISCHARGE_OVER_TEMPERATURE !in protections,
            heaterOn = false,
            protections = protections,
            cellCount = CELL_COUNT,
            timestamp = timestamp,
        )
    }

    private fun updateCurrent(dt: Double) {
        if (scenario != lastScenario) {
            lastScenario = scenario
            secondsToNewTarget = 0.0
        }
        secondsToNewTarget -= dt
        if (secondsToNewTarget <= 0) {
            targetCurrent = newTarget()
            secondsToNewTarget = random.nextDouble(15.0, 40.0)
        }
        // Charging tapers off as the pack fills.
        val target = if (soc > 97 && targetCurrent > 0) min(targetCurrent, 2.0) else targetCurrent
        current += (target - current) * min(1.0, dt / 5) + random.nextDouble(-0.15, 0.15)
        current = current.coerceIn(MIN_CURRENT_A, MAX_CURRENT_A)
        // A MOS that is off blocks current in its direction.
        if (Protection.CELL_OVERVOLTAGE in protections && current > 0) current = 0.0
        if (Protection.DISCHARGE_OVER_TEMPERATURE in protections && current < 0) current = 0.0
    }

    private fun newTarget(): Double = when (scenario) {
        DemoScenario.CHARGING -> random.nextDouble(8.0, MAX_CURRENT_A)
        DemoScenario.CELL_OVERVOLTAGE -> 0.0
        else -> when {
            soc < 20 -> random.nextDouble(5.0, MAX_CURRENT_A)
            soc > 90 -> random.nextDouble(MIN_CURRENT_A, -5.0)
            else -> random.nextDouble(MIN_CURRENT_A, MAX_CURRENT_A)
        }
    }

    private fun updateTemperatures(dt: Double) {
        ambient = (ambient + random.nextDouble(-0.02, 0.02) * dt).coerceIn(25.5, 28.5)
        val (target, timeConstant) = if (scenario == DemoScenario.OVER_TEMPERATURE) {
            70.0 to 8.0
        } else {
            min(ambient + 2 + abs(current) * 0.25, 35.0) to 60.0
        }
        boardTemp += (target - boardTemp) * min(1.0, dt / timeConstant)
        if (boardTemp >= 65) overTemperature = true else if (boardTemp < 55) overTemperature = false
    }

    private fun updateCells() {
        val ocv = openCircuitVoltage(soc)
        for (i in cells.indices) {
            var offset = CELL_OFFSETS_MV[i] / 1000.0
            var resistance = CELL_RESISTANCE_OHM
            if (scenario == DemoScenario.HIGH_SPREAD && i == WEAK_CELL) {
                offset -= 0.080
                resistance *= 2
            }
            cells[i] = round(ocv + current * resistance + offset + random.nextDouble(-0.001, 0.001), 3)
        }
        if (scenario == DemoScenario.CELL_OVERVOLTAGE) {
            cells[HIGH_CELL] = round(3.758 + random.nextDouble(-0.001, 0.001), 3)
        }
    }

    private fun updateProtections() {
        protections = buildList {
            if (cells.max() > CellLimits.MAX_V) add(Protection.CELL_OVERVOLTAGE)
            if (overTemperature) add(Protection.DISCHARGE_OVER_TEMPERATURE)
        }
    }

    companion object {
        const val CELL_COUNT = 16
        const val WEAK_CELL = 11
        const val HIGH_CELL = 6
        private const val CAPACITY_AH = 100.0
        /** Makes SOC move fast enough to see. */
        private const val SPEEDUP = 10.0
        private const val CELL_RESISTANCE_OHM = 0.0012
        private const val MIN_CURRENT_A = -20.0
        private const val MAX_CURRENT_A = 15.0
        private const val MAX_STEP_S = 10.0

        /** Per-cell spread, from the cell test vector in CLAUDE.md (each cell minus 3301 mV). */
        private val CELL_OFFSETS_MV = intArrayOf(0, -3, 4, -1, -5, 2, 10, -2, 1, -4, -1, -9, 3, 8, 0, -3)

        /** Balancing runs on the highest cells when they are above 3.30 V and the spread is over 15 mV. */
        fun balancing(cells: List<Double>): Set<Int> {
            val highest = cells.max()
            if (highest - cells.min() <= CellLimits.BALANCE_SPREAD_V) return emptySet()
            return cells.indices.filter { cells[it] > CellLimits.BALANCE_START_V && cells[it] >= highest - 0.006 }.toSet()
        }

        /** Resting LiFePO4 cell voltage for a state of charge, by linear interpolation. */
        fun openCircuitVoltage(soc: Double): Double {
            val points = OCV_CURVE
            val i = points.indexOfFirst { it.first >= soc }.coerceAtLeast(1)
            val (s0, v0) = points[i - 1]
            val (s1, v1) = points[i]
            return v0 + (v1 - v0) * ((soc - s0) / (s1 - s0)).coerceIn(0.0, 1.0)
        }

        private val OCV_CURVE = listOf(
            0.0 to 2.80, 5.0 to 3.05, 10.0 to 3.18, 20.0 to 3.24, 30.0 to 3.27, 40.0 to 3.28, 50.0 to 3.29,
            60.0 to 3.30, 70.0 to 3.31, 80.0 to 3.32, 90.0 to 3.33, 95.0 to 3.35, 98.0 to 3.40, 100.0 to 3.45,
        )

        private fun round(value: Double, decimals: Int): Double {
            var factor = 1.0
            repeat(decimals) { factor *= 10 }
            return (value * factor).roundToInt() / factor
        }
    }
}
