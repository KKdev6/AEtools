package com.anantenergies.bms.factory.checklist

import com.anantenergies.bms.factory.apply.ApplyResult
import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the BMS reports live (registers 0x03 and 0x04). */
data class LiveReading(val cellCount: Int, val cellVoltages: List<Double>, val temperatures: List<Double>) {
    /** Highest minus lowest cell, mV. */
    val deltaMillivolts: Int?
        get() = if (cellVoltages.isEmpty()) null else ((cellVoltages.max() - cellVoltages.min()) * 1000).roundToInt()
}

/** One check the app does by itself. */
data class AutoCheck(val label: String, val passed: Boolean, val detail: String)

/** The checks staff tick by hand (settings card, "Before sealing the pack"). */
enum class ManualCheck(val label: String) {
    J2_THEN_J1("J2 plugged first, then J1 within 5 s"),
    PROTECTION_COUNTS_RESET("Protection counts reset to zero"),
    FUSE_FITTED("80 A DC fuse fitted at pack positive"),
    CHARGER_OUTPUT("Charger output in range"),
    LEADS_SECURED("Balance leads and sensors secured");

    /** The label with this pack model's numbers where the card gives them. */
    fun label(pack: PackModel): String = when (this) {
        CHARGER_OUTPUT -> "$label: ${chargerRange(pack)} open circuit"
        else -> label
    }

    companion object {
        fun chargerRange(pack: PackModel) = if (pack == PackModel.V60_23AH) "65.5–65.7 V" else "66.3–66.5 V"
    }
}

/** What the operator types or ticks on the checklist. */
data class ChecklistInput(
    val operator: String,
    val resistanceMilliohm: Double?,
    val endOfChargeDeltaMv: Int?,
    val ticked: Set<ManualCheck>,
    /** The preset's set-by-hand items the operator confirmed, by their text. */
    val setByHandDone: Set<String>,
)

object Checklist {
    /** Room temperature: every enabled sensor within this of the sensors' average. */
    const val TEMPERATURE_SPREAD_C = 3.0

    /** End-of-charge cell delta limits from the settings card. */
    const val DELTA_PASS_MV = 15
    const val DELTA_TOP_BALANCE_MV = 30

    fun automatic(profile: BmsModelProfile, verified: ApplyResult.Applied, live: LiveReading?): List<AutoCheck> {
        val cells = live?.let { "${it.cellCount} cells reported, ${it.cellVoltages.size} read" } ?: "No live reading"
        val temps = live?.temperatures.orEmpty()
        val average = temps.average()
        val functions = verified.readBack[JbdSetting.FUNCTIONS]
        return listOf(
            AutoCheck(
                "${profile.cellCount} cells",
                live != null && live.cellCount == profile.cellCount && live.cellVoltages.size == profile.cellCount,
                cells,
            ),
            AutoCheck(
                "Temperature sensors at room temperature (±${TEMPERATURE_SPREAD_C.toInt()} °C)",
                temps.isNotEmpty() && temps.all { abs(it - average) <= TEMPERATURE_SPREAD_C },
                if (temps.isEmpty()) "No temperatures read" else temps.joinToString(" / ") { "%.1f".format(Locale.ROOT, it) } + " °C",
            ),
            AutoCheck(
                "All settings read back after the power-cycle",
                verified.passed,
                if (verified.passed) "All correct" else "${verified.mismatches.size} wrong",
            ),
            AutoCheck(
                "Charge balance on",
                functions != null && functions and JbdSetting.BALANCE_ON != 0 && functions and JbdSetting.CHARGE_BALANCE != 0,
                if (functions == null) "Not read" else "Function bits 0x%04X".format(Locale.ROOT, functions),
            ),
        )
    }

    /** What still stops the pack from passing; empty when it passes. */
    fun problems(auto: List<AutoCheck>, input: ChecklistInput, setByHand: List<String>): List<String> = buildList {
        auto.filterNot { it.passed }.forEach { add(it.label) }
        ManualCheck.entries.filterNot { it in input.ticked }.forEach { add("Not ticked: ${it.label}") }
        setByHand.filterNot { it in input.setByHandDone }.forEach { add("Not confirmed: $it") }
        if (input.operator.isBlank()) add("Operator name missing")
        if (input.resistanceMilliohm == null) add("Pack resistance missing")
        when (val delta = input.endOfChargeDeltaMv) {
            null -> add("End-of-charge delta missing")
            in 0..DELTA_PASS_MV -> Unit
            else -> add(
                "End-of-charge delta $delta mV is over $DELTA_PASS_MV mV" +
                    if (delta > DELTA_TOP_BALANCE_MV) ": top-balance with the NEEY" else "",
            )
        }
    }
}
