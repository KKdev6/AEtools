package com.anantenergies.bms.factory.apply

import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.factory.model.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WritePlanTest {
    private val profile = BmsModels.SP17S005
    private val backup = mapOf(JbdSetting.FUNCTIONS to 0x0006, JbdSetting.BALANCE_VOLTAGE to 3900)

    @Test
    fun `plan sets charge balance and keeps the other function bits`() {
        assertEquals(0x000E, plan(functions = 0x0006).raw(JbdSetting.FUNCTIONS))
        assertEquals(0x000F, plan(functions = 0x0003).raw(JbdSetting.FUNCTIONS))
        assertEquals(0x000E, plan(functions = 0x000E).raw(JbdSetting.FUNCTIONS))
    }

    @Test
    fun `design and cycle capacity are the tested Ah`() {
        val plan = plan(testedAh = 28.47)
        assertEquals(2847, plan.raw(JbdSetting.DESIGN_CAPACITY))
        assertEquals(2847, plan.raw(JbdSetting.CYCLE_CAPACITY))
        assertFalse(plan.capacityOutOfRange)
        assertTrue(plan(testedAh = 26.0).capacityOutOfRange)
    }

    @Test
    fun `implausible capacities are refused`() {
        assertThrows(IllegalArgumentException::class.java) { plan(testedAh = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { plan(testedAh = 2847.0) }
    }

    @Test
    fun `plan shows current and new values in address order`() {
        val plan = plan()
        assertEquals(plan.writes.sortedBy { it.setting.address }, plan.writes)
        val balance = plan.writes.single { it.setting == JbdSetting.BALANCE_VOLTAGE }
        assertEquals(3900, balance.current)
        assertEquals(4140, balance.raw)
        assertTrue(balance.changes)
        assertEquals(null, plan.writes.single { it.setting == JbdSetting.CELL_HIGH }.current)
    }

    @Test
    fun `read-only guard - no plan ever writes a setting its profile doesn't allow`() {
        PackModel.entries.forEach { pack ->
            val written = plan(pack = pack).writes.map { it.setting }.toSet()
            assertTrue(pack.label, written.all { it in profile.writable })
            assertTrue(pack.label, written.none { it in BmsModelProfile.NEVER_WRITTEN })
            assertEquals(pack.preset.values.size + 3, written.size)
        }
        BmsModels.all.forEach { model -> assertTrue(model.id, model.writable.none { it in BmsModelProfile.NEVER_WRITTEN }) }
    }

    @Test
    fun `a profile that would allow writing a fixed setting can't be created`() {
        assertThrows(IllegalArgumentException::class.java) {
            profile.copy(writable = profile.writable + JbdSetting.CELL_COUNT, readOnly = emptySet())
        }
    }

    @Test
    fun `a preset can't be planned for another BMS model`() {
        val other = profile.copy(id = "OTHER", boardPrefix = "OTHER")
        assertThrows(IllegalArgumentException::class.java) { WritePlan.create(other, PackModel.V60_32AH, 28.5, backup) }
    }

    @Test
    fun `plan needs the function bits from the board`() {
        assertThrows(IllegalArgumentException::class.java) { WritePlan.create(profile, PackModel.V60_32AH, 28.5, emptyMap()) }
    }

    private fun plan(pack: PackModel = PackModel.V60_32AH, testedAh: Double = 28.5, functions: Int = 0x0006) =
        WritePlan.create(profile, pack, testedAh, backup + (JbdSetting.FUNCTIONS to functions))

    private fun WritePlan.raw(setting: JbdSetting) = writes.single { it.setting == setting }.raw

    @Test
    fun `presets list is what the pack models use`() {
        assertEquals(Presets.all.toSet(), PackModel.entries.map { it.preset }.toSet())
    }
}
