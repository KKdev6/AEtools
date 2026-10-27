package com.anantenergies.bms.factory.apply

import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel

/** One register the plan writes: [current] is what the board had before, if it was read. */
data class PlannedWrite(val setting: JbdSetting, val raw: Int, val current: Int?) {
    val changes: Boolean get() = current != raw
}

/**
 * Everything that will be written to one pack, worked out from its pack model, its tested
 * capacity and the board's current values. Nothing is sent until it is applied.
 */
class WritePlan private constructor(
    val profile: BmsModelProfile,
    val pack: PackModel,
    val testedAh: Double,
    /** All of the board's settings before anything was written. */
    val backup: Map<JbdSetting, Int>,
    val writes: List<PlannedWrite>,
) {
    /** True when the tested capacity is outside what this pack model should give. */
    val capacityOutOfRange: Boolean get() = testedAh !in pack.expectedAh

    companion object {
        /** Tested capacities the app accepts at all (a typo guard; the expected range only warns). */
        val PLAUSIBLE_AH = 1.0..200.0

        fun create(profile: BmsModelProfile, pack: PackModel, testedAh: Double, backup: Map<JbdSetting, Int>): WritePlan {
            val preset = pack.preset
            require(preset.model == profile) { "${preset.name} is for ${preset.model.id}, not ${profile.id}" }
            require(testedAh in PLAUSIBLE_AH) { "Tested capacity $testedAh Ah isn't plausible" }
            val functions = requireNotNull(backup[JbdSetting.FUNCTIONS]) { "Function bits weren't read" }

            val values = preset.values.mapValues { (setting, value) -> setting.encoding.toRaw(value) } +
                mapOf(
                    JbdSetting.DESIGN_CAPACITY to JbdSetting.DESIGN_CAPACITY.encoding.toRaw(testedAh),
                    JbdSetting.CYCLE_CAPACITY to JbdSetting.CYCLE_CAPACITY.encoding.toRaw(testedAh),
                    // Only the balance bits change: balance on, charge balance (never static).
                    JbdSetting.FUNCTIONS to (functions or JbdSetting.BALANCE_ON or JbdSetting.CHARGE_BALANCE),
                )
            val writes = values.entries
                .sortedBy { it.key.address }
                .map { (setting, raw) -> PlannedWrite(setting, raw, backup[setting]) }
            return WritePlan(profile, pack, testedAh, backup, writes)
        }
    }
}
