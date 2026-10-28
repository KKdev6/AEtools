package com.anantenergies.bms.factory.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * How a setting's value is stored in its 16-bit register. Values outside the app are in plain
 * units: volts, amps, °C, seconds, amp-hours.
 */
enum class Encoding(val unit: String) {
    /** 1 mV per step. */
    MILLIVOLTS("V"),

    /** 10 mV per step (pack voltages). */
    TEN_MILLIVOLTS("V"),

    /** 10 mA per step. */
    TEN_MILLIAMPS("A"),

    /** 10 mA per step, stored negative: `0x10000 − A × 100` (discharge over current). */
    NEGATIVE_TEN_MILLIAMPS("A"),

    /** 0.1 K per step: `2731 + 10 × °C`. */
    DECIKELVIN("°C"),
    SECONDS("s"),

    /** 10 mAh per step. */
    TEN_MILLIAMP_HOURS("Ah"),

    /** Bit flags, written as they are. */
    BITS("");

    /** The register value for [value], or throws if it doesn't fit in 16 bits. */
    fun toRaw(value: Double): Int {
        val raw = when (this) {
            MILLIVOLTS -> value * 1000
            TEN_MILLIVOLTS, TEN_MILLIAMPS, TEN_MILLIAMP_HOURS -> value * 100
            NEGATIVE_TEN_MILLIAMPS -> 0x10000 - value * 100
            DECIKELVIN -> 2731 + value * 10
            SECONDS, BITS -> value
        }.roundToInt()
        require(raw in 0..0xFFFF) { "$value $unit doesn't fit in a register" }
        return raw
    }

    /** The plain-unit value of register value [raw]. */
    fun fromRaw(raw: Int): Double = when (this) {
        MILLIVOLTS -> raw / 1000.0
        TEN_MILLIVOLTS, TEN_MILLIAMPS, TEN_MILLIAMP_HOURS -> raw / 100.0
        NEGATIVE_TEN_MILLIAMPS -> (0x10000 - raw) / 100.0
        DECIKELVIN -> (raw - 2731) / 10.0
        SECONDS, BITS -> raw.toDouble()
    }
}

/**
 * The settings registers of a JBD board on the new (`FA`) protocol, by address. From the JBD app
 * v2.0.1; see docs/jbd-write-protocol.md for which ones were confirmed on the real board.
 */
enum class JbdSetting(val address: Int, val encoding: Encoding, val label: String) {
    DESIGN_CAPACITY(0, Encoding.TEN_MILLIAMP_HOURS, "Design capacity"),
    CYCLE_CAPACITY(1, Encoding.TEN_MILLIAMP_HOURS, "Cycle capacity"),
    FULL_VOLTAGE(2, Encoding.MILLIVOLTS, "AveFullVolt"),
    EMPTY_VOLTAGE(3, Encoding.MILLIVOLTS, "AveEmptyVolt"),

    CHARGE_HIGH_TEMP(8, Encoding.DECIKELVIN, "Charge high temp protect"),
    CHARGE_HIGH_TEMP_RECOVER(9, Encoding.DECIKELVIN, "Charge high temp recover"),
    CHARGE_LOW_TEMP(10, Encoding.DECIKELVIN, "Charge low temp protect"),
    CHARGE_LOW_TEMP_RECOVER(11, Encoding.DECIKELVIN, "Charge low temp recover"),
    DISCHARGE_HIGH_TEMP(12, Encoding.DECIKELVIN, "Discharge high temp protect"),
    DISCHARGE_HIGH_TEMP_RECOVER(13, Encoding.DECIKELVIN, "Discharge high temp recover"),
    DISCHARGE_LOW_TEMP(14, Encoding.DECIKELVIN, "Discharge low temp protect"),
    DISCHARGE_LOW_TEMP_RECOVER(15, Encoding.DECIKELVIN, "Discharge low temp recover"),

    PACK_HIGH(16, Encoding.TEN_MILLIVOLTS, "Pack high protect"),
    PACK_HIGH_RECOVER(17, Encoding.TEN_MILLIVOLTS, "Pack high recover"),
    PACK_LOW(18, Encoding.TEN_MILLIVOLTS, "Pack low protect"),
    PACK_LOW_RECOVER(19, Encoding.TEN_MILLIVOLTS, "Pack low recover"),
    CELL_HIGH(20, Encoding.MILLIVOLTS, "Cell high protect"),
    CELL_HIGH_RECOVER(21, Encoding.MILLIVOLTS, "Cell high recover"),
    CELL_LOW(22, Encoding.MILLIVOLTS, "Cell low protect"),
    CELL_LOW_RECOVER(23, Encoding.MILLIVOLTS, "Cell low recover"),

    CHARGE_OVER_CURRENT(24, Encoding.TEN_MILLIAMPS, "Charge over current"),
    DISCHARGE_OVER_CURRENT(25, Encoding.NEGATIVE_TEN_MILLIAMPS, "Discharge over current"),
    BALANCE_VOLTAGE(26, Encoding.MILLIVOLTS, "Balance voltage"),
    BALANCE_ACCURACY(27, Encoding.MILLIVOLTS, "Balance accuracy"),

    /** Bit 1 load detect, bit 2 balance on, bit 3 charge balance (confirmed on the real board). */
    FUNCTIONS(29, Encoding.BITS, "Function bits"),

    /** One bit per enabled temperature sensor. Read only. */
    NTC_ENABLE(30, Encoding.BITS, "Temperature sensors"),

    /** Read only: fixed by how the pack is wired. */
    CELL_COUNT(31, Encoding.BITS, "Cell count"),

    SOC_80(34, Encoding.MILLIVOLTS, "SOC 80 %"),
    SOC_60(35, Encoding.MILLIVOLTS, "SOC 60 %"),
    SOC_40(36, Encoding.MILLIVOLTS, "SOC 40 %"),
    SOC_20(37, Encoding.MILLIVOLTS, "SOC 20 %"),
    HARDWARE_CELL_HIGH(38, Encoding.MILLIVOLTS, "Hardware cell high"),
    HARDWARE_CELL_LOW(39, Encoding.MILLIVOLTS, "Hardware cell low"),

    /** Board gear codes, fixed by the board rating. Read only, never written. */
    LEVEL2_OVER_CURRENT(40, Encoding.BITS, "Level2 over current"),
    SHORT_CIRCUIT(41, Encoding.BITS, "Short circuit"),

    CHARGE_LOW_TEMP_DELAY(44, Encoding.SECONDS, "Charge low temp delay"),
    CHARGE_HIGH_TEMP_DELAY(45, Encoding.SECONDS, "Charge high temp delay"),
    DISCHARGE_LOW_TEMP_DELAY(46, Encoding.SECONDS, "Discharge low temp delay"),
    DISCHARGE_HIGH_TEMP_DELAY(47, Encoding.SECONDS, "Discharge high temp delay"),
    PACK_LOW_DELAY(48, Encoding.SECONDS, "Pack low delay"),
    PACK_HIGH_DELAY(49, Encoding.SECONDS, "Pack high delay"),
    CELL_LOW_DELAY(50, Encoding.SECONDS, "Cell low delay"),

    /** Confirmed on the real board: the JBD app wrote address 0x33 when the delay changed. */
    CELL_HIGH_DELAY(51, Encoding.SECONDS, "Cell high delay"),
    CHARGE_OVER_CURRENT_DELAY(52, Encoding.SECONDS, "Charge over current delay"),
    CHARGE_OVER_CURRENT_RELEASE(53, Encoding.SECONDS, "Charge over current release"),
    DISCHARGE_OVER_CURRENT_DELAY(54, Encoding.SECONDS, "Discharge over current delay"),
    DISCHARGE_OVER_CURRENT_RELEASE(55, Encoding.SECONDS, "Discharge over current release"),

    SOC_90(106, Encoding.MILLIVOLTS, "SOC 90 %"),
    SOC_70(107, Encoding.MILLIVOLTS, "SOC 70 %"),
    SOC_50(108, Encoding.MILLIVOLTS, "SOC 50 %"),
    SOC_30(109, Encoding.MILLIVOLTS, "SOC 30 %"),
    SOC_10(110, Encoding.MILLIVOLTS, "SOC 10 %"),
    SOC_100(111, Encoding.MILLIVOLTS, "SOC 100 %"),
    MOS_HIGH_TEMP(202, Encoding.DECIKELVIN, "MOS high temp protect");

    /** "4.200 V", "60 A", "−15 °C" for [raw]. */
    fun format(raw: Int): String = when {
        this == CELL_COUNT -> raw.toString()
        // Shown in mV, as the JBD app does.
        this == BALANCE_ACCURACY -> "$raw mV"
        else -> formatByEncoding(raw)
    }

    private fun formatByEncoding(raw: Int): String = when (encoding) {
        Encoding.BITS -> "0x%04X".format(Locale.ROOT, raw)
        Encoding.MILLIVOLTS -> "%.3f V".format(Locale.ROOT, encoding.fromRaw(raw))
        Encoding.TEN_MILLIVOLTS -> "%.2f V".format(Locale.ROOT, encoding.fromRaw(raw))
        Encoding.TEN_MILLIAMP_HOURS -> "%.2f Ah".format(Locale.ROOT, encoding.fromRaw(raw))
        else -> "${plain(encoding.fromRaw(raw))} ${encoding.unit}"
    }

    companion object {
        /** Function bits: bit 2 balance on, bit 3 charge balance (static balance when clear). */
        const val BALANCE_ON = 0x04
        const val CHARGE_BALANCE = 0x08

        private fun plain(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
