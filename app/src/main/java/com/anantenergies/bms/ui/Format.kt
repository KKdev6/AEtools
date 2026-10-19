package com.anantenergies.bms.ui

import com.anantenergies.bms.data.TempUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Number formatting for display. Always uses "." for decimals and a true minus sign. */
object Format {
    private const val MINUS = "−"

    private fun fixed(value: Double, decimals: Int): String {
        val text = String.format(Locale.ROOT, "%.${decimals}f", value)
        // Values that round to zero shouldn't show a sign ("-0.00").
        return if (text.trimStart('-').all { it == '0' || it == '.' }) text.trimStart('-') else text.replace("-", MINUS)
    }

    /** Pack voltage, e.g. "52.82". */
    fun packVoltage(volts: Double) = fixed(volts, 2)

    /** Current with sign, e.g. "−12.4" or "3.46"; two decimals below 10 A. */
    fun current(amps: Double) = fixed(amps, if (abs(amps) < 10) 2 else 1)

    /** Power without sign (the status pill shows the direction), e.g. "655". */
    fun power(watts: Double) = fixed(abs(watts), 0)

    /** Cell voltage, e.g. "3.311". */
    fun cellVoltage(volts: Double) = fixed(volts, 3)

    /** A voltage difference in whole millivolts, e.g. "19". */
    fun millivolts(volts: Double) = (volts * 1000).roundToLong().toString()

    fun temperature(celsius: Double, unit: TempUnit): String = when (unit) {
        TempUnit.CELSIUS -> fixed(celsius, 1) + " °C"
        TempUnit.FAHRENHEIT -> fixed(celsius * 9 / 5 + 32, 1) + " °F"
    }

    fun rssi(dbm: Int) = fixed(dbm.toDouble(), 0)

    /** "A4:C1:38:12:34:56" becomes "A4:C1:38:••:••:••". */
    fun maskedAddress(address: String): String {
        val parts = address.split(':')
        if (parts.size != 6) return address
        return (parts.take(3) + List(3) { "••" }).joinToString(":")
    }

    fun age(seconds: Long): String = when {
        seconds < 1 -> "just now"
        seconds < 60 -> "$seconds s ago"
        else -> "${seconds / 60} min ago"
    }
}
