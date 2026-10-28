package com.anantenergies.bms.factory.flow

import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.factory.apply.ApplyResult
import com.anantenergies.bms.factory.checklist.ChecklistInput
import com.anantenergies.bms.factory.checklist.ManualCheck
import com.anantenergies.bms.factory.checklist.PackLogEntry
import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.factory.sim.SettingsFault
import com.anantenergies.bms.factory.sim.SimulatedSettingsBoard
import com.anantenergies.bms.factory.sim.SimulatedSettingsLink
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole apply flow the screens drive, against the simulated SP17S005. */
class FactorySessionTest {
    private val board = SimulatedSettingsBoard()
    private val device = Board("Simulated SP17S005", "SI:MU:LA:TE:D0:02")
    private val details = PackDetails(PackModel.V60_32AH, packNumber = "P-0042", testedAh = 28.6)

    @Test
    fun `one pack from connect to verified after the power-cycle`() = runTest {
        val session = session()
        session.connect(device)
        val entered = session.step.value as Step.EnterDetails
        assertEquals(BmsModels.SP17S005, entered.profile)
        assertEquals("SP17S005P17S50A", entered.boardName)

        session.review(details)
        val review = session.step.value as Step.Review
        assertEquals(3900, review.plan.backup[JbdSetting.BALANCE_VOLTAGE])
        assertEquals(2860, review.plan.writes.single { it.setting == JbdSetting.DESIGN_CAPACITY }.raw)

        session.apply()
        val applied = session.step.value as Step.Applied
        assertTrue((applied.result as ApplyResult.Applied).passed)

        session.startPowerCycle()
        assertTrue(session.step.value is Step.PowerCycle)
        assertFalse(board.settingsMode)

        session.verifyAfterPowerCycle()
        val verified = session.step.value as Step.Verified
        assertTrue((verified.result as ApplyResult.Applied).passed)
        assertEquals(4140, board.stored(JbdSetting.BALANCE_VOLTAGE.address))
    }

    @Test
    fun `unknown board goes nowhere near writing`() = runTest {
        board.boardName = "DB24SA08"
        val session = session()
        session.connect(device)
        assertTrue(session.step.value is Step.UnknownBoard)
        session.review(details)
        session.apply()
        assertTrue(session.step.value is Step.UnknownBoard)
        assertEquals(3900, board.stored(JbdSetting.BALANCE_VOLTAGE.address))
    }

    @Test
    fun `refused write shows the failure and retry starts from a fresh read`() = runTest {
        board.fault = SettingsFault.REFUSE_WRITE
        val session = session()
        session.connect(device)
        session.review(details)
        session.apply()
        val failed = (session.step.value as Step.Applied).result as ApplyResult.Failed
        assertEquals(JbdSetting.BALANCE_VOLTAGE, failed.setting)
        assertFalse(failed.saved)

        board.fault = SettingsFault.NONE
        session.retry()
        assertTrue(session.step.value is Step.EnterDetails)
    }

    @Test
    fun `40 A board is not offered the power-cycle`() = runTest {
        board.fault = SettingsFault.FORTY_AMP_BOARD
        val session = session()
        session.connect(device)
        session.review(details)
        session.apply()
        val result = (session.step.value as Step.Applied).result as ApplyResult.Applied
        assertEquals(40.0, result.wrongBoardRating!!, 0.001)
        assertFalse(result.passed)
    }

    @Test
    fun `changing details from the review keeps what was typed`() = runTest {
        val session = session()
        session.connect(device)
        session.review(details)
        session.editDetails()
        assertEquals(details, (session.step.value as Step.EnterDetails).previous)
    }

    @Test
    fun `reset goes back to the board list`() = runTest {
        val session = session()
        session.connect(device)
        session.reset()
        assertEquals(Step.ChooseBoard, session.step.value)
    }

    private val logged = mutableListOf<PackLogEntry>()

    private fun TestScope.session() = FactorySession(
        scope = backgroundScope,
        links = GattLinkFactory { _, _ -> SimulatedSettingsLink(board, backgroundScope) },
        log = null,
        onPowerCycle = { board.powerCycle() },
        logSheet = { logged += it },
        clock = { 1_793_000_000_000 },
    )

    private suspend fun FactorySession.throughVerify() {
        connect(device)
        review(details)
        apply()
        startPowerCycle()
        verifyAfterPowerCycle()
    }

    @Test
    fun `checklist reads live values and a complete checklist is logged as a pass`() = runTest {
        val session = session()
        session.throughVerify()
        session.startChecklist()
        val checklist = session.step.value as Step.Checklist
        assertTrue(checklist.auto.toString(), checklist.auto.all { it.passed })
        assertEquals(8, checklist.live!!.deltaMillivolts)

        session.saveChecklist(
            ChecklistInput("Ravi", 62.0, 12, ManualCheck.entries.toSet(), details.pack.preset.setByHand.toSet()),
        )
        val entry = (session.step.value as Step.Logged).entry
        assertTrue(entry.problems.toString(), entry.passed)
        assertEquals(listOf(entry), logged)
        assertEquals("P-0042", entry.packNumber)
        assertEquals("SP17S005P17S50A", entry.bmsModel)
        assertEquals(4140, entry.readBack[JbdSetting.BALANCE_VOLTAGE])
    }

    @Test
    fun `a warm sensor and missing ticks are logged as a fail`() = runTest {
        board.temperatures = listOf(26.0, 26.0, 35.0)
        val session = session()
        session.throughVerify()
        session.startChecklist()
        session.saveChecklist(ChecklistInput("Ravi", 62.0, 12, emptySet(), emptySet()))
        val entry = logged.single()
        assertFalse(entry.passed)
        assertTrue(entry.problems.first().startsWith("Temperature sensors"))
    }
}
