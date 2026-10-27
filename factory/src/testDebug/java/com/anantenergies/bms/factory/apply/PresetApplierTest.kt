package com.anantenergies.bms.factory.apply

import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.factory.protocol.GattBoardChannel
import com.anantenergies.bms.factory.sim.SettingsFault
import com.anantenergies.bms.factory.sim.SimulatedSettingsBoard
import com.anantenergies.bms.factory.sim.SimulatedSettingsLink
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole apply path, over the real frame queue and 20-byte notifications, against the simulated SP17S005. */
class PresetApplierTest {
    private val board = SimulatedSettingsBoard()
    private val profile = BmsModels.SP17S005

    @Test
    fun `identifies the SP17S005`() = runTest {
        val identity = applier().identify()
        assertEquals("SP17S005P17S50A", identity.boardName)
        assertEquals(1, identity.chipType)
        assertEquals(profile, identity.profile)
    }

    @Test
    fun `unknown board has no profile`() = runTest {
        board.boardName = "DB24SA08"
        assertNull(applier().identify().profile)
    }

    @Test
    fun `reads every setting of the profile`() = runTest {
        val values = applier().readAll(profile)!!
        assertEquals(profile.writable + profile.readOnly, values.keys)
        assertEquals(3900, values[JbdSetting.BALANCE_VOLTAGE])
        assertEquals(16, values[JbdSetting.CELL_COUNT])
    }

    @Test
    fun `applies, saves and verifies a preset`() = runTest {
        val applier = applier()
        val plan = plan(applier)
        val result = applier.apply(plan) as ApplyResult.Applied
        assertTrue(result.mismatches.toString(), result.passed)
        assertFalse(board.settingsMode)
        plan.writes.forEach { assertEquals(it.setting.name, it.raw, board.stored(it.setting.address)) }
        assertEquals(0x000E, board.stored(JbdSetting.FUNCTIONS.address))
        assertEquals(16, board.stored(JbdSetting.CELL_COUNT.address))
    }

    @Test
    fun `settings survive a power-cycle`() = runTest {
        val applier = applier()
        val plan = plan(applier)
        applier.apply(plan)
        board.powerCycle()
        assertTrue((applier.verify(plan) as ApplyResult.Applied).passed)
    }

    @Test
    fun `refused write leaves without saving and the board keeps its old values`() = runTest {
        board.fault = SettingsFault.REFUSE_WRITE
        assertNothingSavedAfter(expectedSetting = JbdSetting.BALANCE_VOLTAGE)
    }

    @Test
    fun `unanswered write leaves without saving`() = runTest {
        board.fault = SettingsFault.SILENT_WRITE
        assertNothingSavedAfter(expectedSetting = JbdSetting.BALANCE_VOLTAGE)
    }

    @Test
    fun `link drop mid-apply saves nothing`() = runTest {
        board.fault = SettingsFault.LINK_DROP_MID_APPLY
        assertNothingSavedAfter(expectedSetting = null)
        board.powerCycle()
        assertEquals(3900, board.stored(JbdSetting.BALANCE_VOLTAGE.address))
    }

    @Test
    fun `value that doesn't stick fails the read-back`() = runTest {
        board.fault = SettingsFault.VALUE_DOES_NOT_STICK
        val result = applier().let { it.apply(plan(it)) } as ApplyResult.Applied
        assertFalse(result.passed)
        assertEquals(listOf(Mismatch(JbdSetting.BALANCE_VOLTAGE, expected = 4140, actual = 3900)), result.mismatches)
    }

    @Test
    fun `40 A board is caught`() = runTest {
        board.fault = SettingsFault.FORTY_AMP_BOARD
        val result = applier().let { it.apply(plan(it)) } as ApplyResult.Applied
        assertFalse(result.passed)
        assertEquals(40.0, result.wrongBoardRating!!, 0.001)
        assertEquals(listOf(JbdSetting.DISCHARGE_OVER_CURRENT), result.mismatches.map { it.setting })
    }

    private suspend fun TestScope.assertNothingSavedAfter(expectedSetting: JbdSetting?) {
        val applier = applier()
        val plan = plan(applier)
        val before = plan.backup
        val failed = applier.apply(plan) as ApplyResult.Failed
        assertEquals(ApplyResult.Stage.WRITE, failed.stage)
        assertFalse(failed.saved)
        if (expectedSetting != null) assertEquals(expectedSetting, failed.setting) else assertNotNull(failed.setting)
        before.forEach { (setting, raw) -> assertEquals(setting.name, raw, board.stored(setting.address)) }
    }

    private suspend fun plan(applier: PresetApplier): WritePlan =
        WritePlan.create(profile, PackModel.V60_32AH, 28.5, applier.readAll(profile)!!)

    private fun TestScope.applier() =
        PresetApplier(GattBoardChannel(SimulatedSettingsLink(board, backgroundScope), backgroundScope, log = null))
}
