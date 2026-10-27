package com.anantenergies.bms.domain

/**
 * One reading of the whole pack, the same shape for every BMS brand.
 * Drivers build it; the UI only ever sees this.
 */
data class BmsSnapshot(
    /** Pack voltage, V. */
    val packVoltage: Double,
    /** Pack current, A. Positive while charging, negative while discharging. */
    val current: Double,
    /** State of charge, 0–100 %. */
    val soc: Int,
    val cycleCount: Int,
    /** Cell voltages in series order, V. */
    val cellVoltages: List<Double>,
    /** Zero-based indices into [cellVoltages] of the cells that are balancing. */
    val balancingCells: Set<Int>,
    val temperatures: List<TemperatureReading>,
    val chargeMosOn: Boolean,
    val dischargeMosOn: Boolean,
    /** Null when the BMS doesn't report its heater. */
    val heaterOn: Boolean?,
    val protections: List<Protection>,
    /** Cell count as reported by the BMS. */
    val cellCount: Int,
    /** When the reading was taken, epoch milliseconds. */
    val timestamp: Long,
) {
    /** Power, W. Same sign convention as [current]. */
    val power: Double get() = packVoltage * current

    val flow: CurrentFlow get() = CurrentFlow.of(current)
}

data class TemperatureReading(val label: String, val celsius: Double)

enum class CurrentFlow {
    CHARGING, DISCHARGING, IDLE;

    companion object {
        /** Below this magnitude, A, the pack counts as idle, so the status doesn't flicker around zero. */
        const val IDLE_THRESHOLD_A = 0.2

        fun of(current: Double): CurrentFlow = when {
            current >= IDLE_THRESHOLD_A -> CHARGING
            current <= -IDLE_THRESHOLD_A -> DISCHARGING
            else -> IDLE
        }
    }
}

/** Protections a BMS can report, each with the message shown to the user. */
enum class Protection(val message: String) {
    CELL_OVERVOLTAGE("Cell voltage too high"),
    CELL_UNDERVOLTAGE("Cell voltage too low"),
    PACK_OVERVOLTAGE("Battery voltage too high"),
    PACK_UNDERVOLTAGE("Battery voltage too low"),
    CHARGE_OVER_TEMPERATURE("Too hot to charge"),
    CHARGE_UNDER_TEMPERATURE("Too cold to charge"),
    DISCHARGE_OVER_TEMPERATURE("Too hot to discharge"),
    DISCHARGE_UNDER_TEMPERATURE("Too cold to discharge"),
    CHARGE_OVERCURRENT("Charge current too high"),
    DISCHARGE_OVERCURRENT("Discharge current too high"),
    SHORT_CIRCUIT("Short circuit detected"),
    FRONT_END_IC_ERROR("BMS measurement chip error"),
    MOS_SOFTWARE_LOCK("Outputs switched off by BMS software"),
}
