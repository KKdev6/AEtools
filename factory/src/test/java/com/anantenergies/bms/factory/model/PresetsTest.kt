package com.anantenergies.bms.factory.model

import com.anantenergies.bms.factory.model.JbdSetting.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun `encodings match the board's units`() {
        assertEquals(4200, Encoding.MILLIVOLTS.toRaw(4.20))
        assertEquals(6700, Encoding.TEN_MILLIVOLTS.toRaw(67.0))
        assertEquals(2000, Encoding.TEN_MILLIAMPS.toRaw(20.0))
        assertEquals(0xE890, Encoding.NEGATIVE_TEN_MILLIAMPS.toRaw(60.0))
        assertEquals(3271, Encoding.DECIKELVIN.toRaw(54.0))
        assertEquals(2581, Encoding.DECIKELVIN.toRaw(-15.0))
        assertEquals(2850, Encoding.TEN_MILLIAMP_HOURS.toRaw(28.5))
        assertEquals(15, Encoding.MILLIVOLTS.toRaw(0.015))
    }

    @Test
    fun `encodings round-trip`() {
        Encoding.entries.forEach { encoding ->
            listOf(0, 15, 2581, 3900, 59536).forEach { raw -> assertEquals(raw, encoding.toRaw(encoding.fromRaw(raw))) }
        }
        assertEquals(60.0, Encoding.NEGATIVE_TEN_MILLIAMPS.fromRaw(0xE890), 0.0)
        assertEquals(-15.0, Encoding.DECIKELVIN.fromRaw(2581), 0.0)
    }

    @Test
    fun `values that don't fit a register are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Encoding.MILLIVOLTS.toRaw(70.0) }
        assertThrows(IllegalArgumentException::class.java) { Encoding.TEN_MILLIVOLTS.toRaw(-1.0) }
    }

    @Test
    fun `both 60V presets cover every item on the settings card`() {
        val card = setOf(
            CELL_HIGH, CELL_HIGH_RECOVER, CELL_LOW, CELL_LOW_RECOVER,
            PACK_HIGH, PACK_HIGH_RECOVER, PACK_LOW, PACK_LOW_RECOVER,
            HARDWARE_CELL_HIGH, HARDWARE_CELL_LOW,
            PACK_HIGH_DELAY, PACK_LOW_DELAY, CELL_HIGH_DELAY, CELL_LOW_DELAY,
            CHARGE_OVER_CURRENT, CHARGE_OVER_CURRENT_DELAY, CHARGE_OVER_CURRENT_RELEASE,
            DISCHARGE_OVER_CURRENT, DISCHARGE_OVER_CURRENT_DELAY, DISCHARGE_OVER_CURRENT_RELEASE,
            CHARGE_HIGH_TEMP, CHARGE_HIGH_TEMP_RECOVER, CHARGE_LOW_TEMP, CHARGE_LOW_TEMP_RECOVER,
            DISCHARGE_HIGH_TEMP, DISCHARGE_HIGH_TEMP_RECOVER, DISCHARGE_LOW_TEMP, DISCHARGE_LOW_TEMP_RECOVER,
            MOS_HIGH_TEMP, CHARGE_HIGH_TEMP_DELAY, CHARGE_LOW_TEMP_DELAY, DISCHARGE_HIGH_TEMP_DELAY, DISCHARGE_LOW_TEMP_DELAY,
            BALANCE_VOLTAGE, BALANCE_ACCURACY, FULL_VOLTAGE, EMPTY_VOLTAGE,
            SOC_100, SOC_90, SOC_80, SOC_70, SOC_60, SOC_50, SOC_40, SOC_30, SOC_20, SOC_10,
        )
        Presets.all.forEach { assertEquals(it.name, card, it.values.keys) }
    }

    @Test
    fun `27-41Ah preset encodes to the card's values`() {
        val raw = Presets.V60_27_41AH.values.mapValues { (setting, value) -> setting.encoding.toRaw(value) }
        val expected = mapOf(
            CELL_HIGH to 4200, CELL_HIGH_RECOVER to 4150, CELL_LOW to 2700, CELL_LOW_RECOVER to 2900,
            PACK_HIGH to 6700, PACK_HIGH_RECOVER to 6640, PACK_LOW to 4320, PACK_LOW_RECOVER to 4640,
            HARDWARE_CELL_HIGH to 4250, HARDWARE_CELL_LOW to 2500,
            PACK_HIGH_DELAY to 2, PACK_LOW_DELAY to 2, CELL_HIGH_DELAY to 2, CELL_LOW_DELAY to 2,
            CHARGE_OVER_CURRENT to 2000, CHARGE_OVER_CURRENT_DELAY to 10, CHARGE_OVER_CURRENT_RELEASE to 32,
            DISCHARGE_OVER_CURRENT to 0xE890, DISCHARGE_OVER_CURRENT_DELAY to 30, DISCHARGE_OVER_CURRENT_RELEASE to 32,
            CHARGE_HIGH_TEMP to 3271, CHARGE_HIGH_TEMP_RECOVER to 3211, CHARGE_LOW_TEMP to 2761, CHARGE_LOW_TEMP_RECOVER to 2811,
            DISCHARGE_HIGH_TEMP to 3331, DISCHARGE_HIGH_TEMP_RECOVER to 3231, DISCHARGE_LOW_TEMP to 2581, DISCHARGE_LOW_TEMP_RECOVER to 2631,
            MOS_HIGH_TEMP to 3631,
            CHARGE_HIGH_TEMP_DELAY to 5, CHARGE_LOW_TEMP_DELAY to 5, DISCHARGE_HIGH_TEMP_DELAY to 5, DISCHARGE_LOW_TEMP_DELAY to 5,
            BALANCE_VOLTAGE to 4140, BALANCE_ACCURACY to 15, FULL_VOLTAGE to 4130, EMPTY_VOLTAGE to 2800,
            SOC_100 to 4140, SOC_90 to 4060, SOC_80 to 3990, SOC_70 to 3920, SOC_60 to 3840,
            SOC_50 to 3770, SOC_40 to 3690, SOC_30 to 3600, SOC_20 to 3460, SOC_10 to 3310,
        )
        assertEquals(expected, raw)
    }

    @Test
    fun `23Ah preset differs only where the card says`() {
        val a = Presets.V60_23AH.values
        val b = Presets.V60_27_41AH.values
        val different = a.keys.filter { a[it] != b[it] }.toSet()
        assertEquals(
            setOf(CELL_HIGH, CELL_HIGH_RECOVER, PACK_HIGH, PACK_HIGH_RECOVER, BALANCE_VOLTAGE, FULL_VOLTAGE) +
                setOf(SOC_100, SOC_90, SOC_80, SOC_70, SOC_60, SOC_50, SOC_40, SOC_30, SOC_20, SOC_10),
            different,
        )
        assertEquals(4.18, a[CELL_HIGH]!!, 0.0)
        assertEquals(4.10, a[CELL_HIGH_RECOVER]!!, 0.0)
        assertEquals(66.8, a[PACK_HIGH]!!, 0.0)
        assertEquals(65.6, a[PACK_HIGH_RECOVER]!!, 0.0)
        assertEquals(4.09, a[BALANCE_VOLTAGE]!!, 0.0)
        assertEquals(4.08, a[FULL_VOLTAGE]!!, 0.0)
        assertEquals(listOf(4.09, 4.03, 3.96, 3.89, 3.82, 3.75, 3.68, 3.58, 3.45, 3.30),
            listOf(SOC_100, SOC_90, SOC_80, SOC_70, SOC_60, SOC_50, SOC_40, SOC_30, SOC_20, SOC_10).map { a[it]!! })
    }

    @Test
    fun `pack models pick the right preset and expected capacity`() {
        assertEquals(Presets.V60_23AH, PackModel.V60_23AH.preset)
        listOf(PackModel.V60_27AH, PackModel.V60_32AH, PackModel.V60_36AH, PackModel.V60_41AH)
            .forEach { assertEquals(Presets.V60_27_41AH, it.preset) }
        assertEquals(28.0..29.0, PackModel.V60_32AH.expectedAh)
        assertTrue(PackModel.entries.all { it.preset.cardRevision == "60V NMC card Rev 4.0" })
    }

    @Test
    fun `the SP17S005 is recognised only with its board string and the new protocol`() {
        assertEquals(BmsModels.SP17S005, BmsModels.identify("SP17S005P17S50A", 1))
        assertEquals(null, BmsModels.identify("SP17S005P17S50A", 0))
        assertEquals(null, BmsModels.identify("SP17S005P17S50A", null))
        assertEquals(null, BmsModels.identify("DB24SA08", 1))
    }
}
