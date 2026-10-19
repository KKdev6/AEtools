package com.anantenergies.bms.domain

/** Limits from the DB24SA08 datasheet, used for colouring. */
object CellLimits {
    const val MIN_V = 2.30
    const val MAX_V = 3.75
    const val BALANCE_START_V = 3.30
    const val BALANCE_SPREAD_V = 0.015

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
