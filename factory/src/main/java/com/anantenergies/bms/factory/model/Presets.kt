package com.anantenergies.bms.factory.model

import com.anantenergies.bms.factory.model.JbdSetting.*

/**
 * A fixed set of settings for one BMS model, from one revision of a settings card. Values are in
 * plain units (V, A, °C, s). Design and cycle capacity are not part of a preset: they are the
 * tested Ah of each pack. Function bits are not either: the plan sets the balance bits.
 */
data class Preset(
    val id: String,
    val name: String,
    val model: BmsModelProfile,
    val cardRevision: String,
    val values: Map<JbdSetting, Double>,
) {
    init {
        val notWritable = values.keys.filter { it !in model.writable }
        require(notWritable.isEmpty()) { "$id writes settings ${model.id} doesn't allow: $notWritable" }
        require(DESIGN_CAPACITY !in values && CYCLE_CAPACITY !in values && FUNCTIONS !in values) {
            "$id: capacities come from the pack test and function bits from the plan"
        }
    }
}

/** The pack models staff choose from. Each picks its preset and the capacity it should test at. */
enum class PackModel(
    val label: String,
    val preset: Preset,
    /** Expected capacity at 15 A, from the settings card. Outside it the app warns. */
    val expectedAh: ClosedFloatingPointRange<Double>,
) {
    V60_23AH("60V 23Ah (16S5P)", Presets.V60_23AH, 18.9..19.7),
    V60_27AH("60V 27Ah (16S6P)", Presets.V60_27_41AH, 23.9..24.9),
    V60_32AH("60V 32Ah (16S7P)", Presets.V60_27_41AH, 28.0..29.0),
    V60_36AH("60V 36Ah (16S8P)", Presets.V60_27_41AH, 32.0..33.2),
    V60_41AH("60V 41Ah (16S9P)", Presets.V60_27_41AH, 36.0..37.4),
}

/** Settings card "60V NMC BMS Settings Card — SP17S005", Rev 4.0. */
object Presets {
    private const val CARD = "60V NMC card Rev 4.0"

    /** Everything the two 60V presets share. */
    private val shared60V: Map<JbdSetting, Double> = mapOf(
        CELL_LOW to 2.70,
        CELL_LOW_RECOVER to 2.90,
        PACK_LOW to 43.2,
        PACK_LOW_RECOVER to 46.4,
        HARDWARE_CELL_HIGH to 4.25,
        HARDWARE_CELL_LOW to 2.50,
        PACK_HIGH_DELAY to 2.0,
        PACK_LOW_DELAY to 2.0,
        CELL_HIGH_DELAY to 2.0,
        CELL_LOW_DELAY to 2.0,
        CHARGE_OVER_CURRENT to 20.0,
        CHARGE_OVER_CURRENT_DELAY to 10.0,
        CHARGE_OVER_CURRENT_RELEASE to 32.0,
        DISCHARGE_OVER_CURRENT to 60.0,
        DISCHARGE_OVER_CURRENT_DELAY to 30.0,
        DISCHARGE_OVER_CURRENT_RELEASE to 32.0,
        CHARGE_HIGH_TEMP to 54.0,
        CHARGE_HIGH_TEMP_RECOVER to 48.0,
        CHARGE_LOW_TEMP to 3.0,
        CHARGE_LOW_TEMP_RECOVER to 8.0,
        DISCHARGE_HIGH_TEMP to 60.0,
        DISCHARGE_HIGH_TEMP_RECOVER to 50.0,
        DISCHARGE_LOW_TEMP to -15.0,
        DISCHARGE_LOW_TEMP_RECOVER to -10.0,
        MOS_HIGH_TEMP to 90.0,
        CHARGE_HIGH_TEMP_DELAY to 5.0,
        CHARGE_LOW_TEMP_DELAY to 5.0,
        DISCHARGE_HIGH_TEMP_DELAY to 5.0,
        DISCHARGE_LOW_TEMP_DELAY to 5.0,
        BALANCE_ACCURACY to 0.015,
        EMPTY_VOLTAGE to 2.80,
    )

    private fun soc(vararg volts: Double): Map<JbdSetting, Double> =
        listOf(SOC_100, SOC_90, SOC_80, SOC_70, SOC_60, SOC_50, SOC_40, SOC_30, SOC_20, SOC_10).zip(volts.toList()).toMap()

    val V60_23AH = Preset(
        id = "60v-23ah",
        name = "60V 23Ah",
        model = BmsModels.SP17S005,
        cardRevision = CARD,
        values = shared60V + mapOf(
            CELL_HIGH to 4.18,
            CELL_HIGH_RECOVER to 4.10,
            PACK_HIGH to 66.8,
            PACK_HIGH_RECOVER to 65.6,
            BALANCE_VOLTAGE to 4.09,
            FULL_VOLTAGE to 4.08,
        ) + soc(4.09, 4.03, 3.96, 3.89, 3.82, 3.75, 3.68, 3.58, 3.45, 3.30),
    )

    val V60_27_41AH = Preset(
        id = "60v-27-41ah",
        name = "60V 27–41Ah",
        model = BmsModels.SP17S005,
        cardRevision = CARD,
        values = shared60V + mapOf(
            CELL_HIGH to 4.20,
            CELL_HIGH_RECOVER to 4.15,
            PACK_HIGH to 67.0,
            PACK_HIGH_RECOVER to 66.4,
            BALANCE_VOLTAGE to 4.14,
            FULL_VOLTAGE to 4.13,
        ) + soc(4.14, 4.06, 3.99, 3.92, 3.84, 3.77, 3.69, 3.60, 3.46, 3.31),
    )

    val all = listOf(V60_23AH, V60_27_41AH)
}
