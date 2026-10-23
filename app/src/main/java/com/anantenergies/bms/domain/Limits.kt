package com.anantenergies.bms.domain

/**
 * Cell colour limits for the connected packs (Li-ion / NMC; confirmed on the real board, whose
 * cells rest near 3.83 V). A cell outside this range is shown in the warning colour.
 */
object CellLimits {
    const val MIN_V = 2.80
    const val MAX_V = 4.25

    fun isOutOfRange(volts: Double) = volts < MIN_V || volts > MAX_V
}

enum class TemperatureLevel { NORMAL, WARM, HOT }

/** Owner's colour limits for temperature readings: orange above 45 °C, red above 50 °C. */
object TemperatureLimits {
    const val WARM_C = 45.0
    const val HOT_C = 50.0

    fun level(celsius: Double): TemperatureLevel = when {
        celsius > HOT_C -> TemperatureLevel.HOT
        celsius > WARM_C -> TemperatureLevel.WARM
        else -> TemperatureLevel.NORMAL
    }
}
