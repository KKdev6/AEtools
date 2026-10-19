package com.anantenergies.bms.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Highest, lowest and average cell. Indices are zero-based; ties go to the first cell. */
data class CellStats(
    val highestIndex: Int,
    val highest: Double,
    val lowestIndex: Int,
    val lowest: Double,
    val average: Double,
) {
    /** Highest minus lowest, V. */
    val spread: Double get() = highest - lowest

    companion object {
        fun of(cells: List<Double>): CellStats? {
            if (cells.isEmpty()) return null
            val highestIndex = cells.indices.maxBy { cells[it] }
            val lowestIndex = cells.indices.minBy { cells[it] }
            return CellStats(
                highestIndex = highestIndex,
                highest = cells[highestIndex],
                lowestIndex = lowestIndex,
                lowest = cells[lowestIndex],
                average = cells.average(),
            )
        }
    }
}

/** Voltage range shown by the cell bar chart, V. */
data class ChartWindow(val low: Double, val high: Double) {
    /** Where [volts] sits in the window, 0 at the bottom to 1 at the top, clamped. */
    fun fraction(volts: Double): Float = ((volts - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()

    companion object {
        private const val MIN_HALF_MV = 20
        private const val STEP_MV = 10
        private const val HEADROOM = 1.2

        /**
         * Centred on the average (rounded to 10 mV), at least ±20 mV, widened in 10 mV steps
         * until every cell fits with some headroom.
         */
        fun around(stats: CellStats): ChartWindow {
            val centreMv = (stats.average * 1000 / STEP_MV).roundToInt() * STEP_MV
            val furthestMv = max(stats.highest * 1000 - centreMv, centreMv - stats.lowest * 1000)
            val halfMv = max(MIN_HALF_MV, ceil(furthestMv * HEADROOM / STEP_MV).toInt() * STEP_MV)
            return ChartWindow(low = (centreMv - halfMv) / 1000.0, high = (centreMv + halfMv) / 1000.0)
        }
    }
}
