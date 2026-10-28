package com.anantenergies.bms.factory.checklist

import com.anantenergies.bms.factory.apply.ApplyResult
import com.anantenergies.bms.factory.apply.Mismatch
import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.model.PackModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChecklistTest {
    @get:Rule val folder = TemporaryFolder()

    private val profile = BmsModels.SP17S005
    private val verified = ApplyResult.Applied(mapOf(JbdSetting.FUNCTIONS to 0x000E), emptyList(), null)
    private val live = LiveReading(16, List(15) { 4.130 } + 4.138, listOf(26.1, 25.4, 27.0))
    private val setByHand = PackModel.V60_32AH.preset.setByHand
    private val allDone = ChecklistInput(
        operator = "Ravi",
        resistanceMilliohm = 62.0,
        endOfChargeDeltaMv = 12,
        ticked = ManualCheck.entries.toSet(),
        setByHandDone = setByHand.toSet(),
    )

    @Test
    fun `a good pack passes every automatic check`() {
        val auto = Checklist.automatic(profile, verified, live)
        assertTrue(auto.toString(), auto.all { it.passed })
        assertEquals(8, live.deltaMillivolts)
        assertEquals(emptyList<String>(), Checklist.problems(auto, allDone, setByHand))
    }

    @Test
    fun `wrong cell count, warm sensor, static balance and failed read-back are caught`() {
        val bad = ApplyResult.Applied(mapOf(JbdSetting.FUNCTIONS to 0x0006), listOf(Mismatch(JbdSetting.CELL_HIGH, 4200, 4250)), null)
        val auto = Checklist.automatic(profile, bad, LiveReading(15, List(15) { 4.13 }, listOf(26.0, 26.0, 34.0)))
        assertEquals(listOf(false, false, false, false), auto.map { it.passed })
    }

    @Test
    fun `no live reading fails the cell and temperature checks`() {
        val auto = Checklist.automatic(profile, verified, null)
        assertFalse(auto[0].passed)
        assertFalse(auto[1].passed)
    }

    @Test
    fun `missing ticks, inputs and a high delta are listed`() {
        val auto = Checklist.automatic(profile, verified, live)
        val problems = Checklist.problems(
            auto,
            ChecklistInput("", null, 34, ManualCheck.entries.toSet() - ManualCheck.FUSE_FITTED, emptySet()),
            setByHand,
        )
        assertEquals(
            listOf(
                "Not ticked: 80 A DC fuse fitted at pack positive",
                "Not confirmed: MOS high temperature protect: 90 °C",
                "Operator name missing",
                "Pack resistance missing",
                "End-of-charge delta 34 mV is over 15 mV: top-balance with the NEEY",
            ),
            problems,
        )
        assertEquals(listOf("End-of-charge delta 20 mV is over 15 mV"), Checklist.problems(auto, allDone.copy(endOfChargeDeltaMv = 20), setByHand))
    }

    @Test
    fun `charger range follows the pack model`() {
        assertEquals("Charger output in range: 65.5–65.7 V open circuit", ManualCheck.CHARGER_OUTPUT.label(PackModel.V60_23AH))
        assertEquals("Charger output in range: 66.3–66.5 V open circuit", ManualCheck.CHARGER_OUTPUT.label(PackModel.V60_41AH))
    }

    @Test
    fun `log sheet rows line up with the header and read back`() {
        val store = PackLogStore(folder.root.resolve("pack-log.csv"))
        val entry = entry(packNumber = "P-0042, \"A\"", passed = true)
        store.append(entry)
        store.append(entry(packNumber = "P-0043", passed = false))

        val lines = store.file.readLines()
        assertEquals("\uFEFF" + PackLogCsv.line(PackLogCsv.header), lines[0])
        lines.forEach { assertEquals(PackLogCsv.header.size, PackLogCsv.parse(it).size) }
        val row = PackLogCsv.parse(lines[1])
        assertEquals("P-0042, \"A\"", row[2])
        assertEquals("4.140 V", row[PackLogCsv.header.indexOf("Balance voltage")])
        assertEquals("15 mV", row[PackLogCsv.header.indexOf("Balance accuracy")])
        assertEquals("16", row[PackLogCsv.header.indexOf("Cell count")])

        val packs = store.packs()
        assertEquals(listOf("P-0043", "P-0042, \"A\""), packs.map { it.packNumber })
        assertEquals(listOf("FAIL", "PASS"), packs.map { it.result })
    }

    private fun entry(packNumber: String, passed: Boolean) = PackLogEntry(
        time = 1_793_000_000_000, operator = "Ravi", packNumber = packNumber, packModel = "60V 32Ah (16S7P)",
        bmsModel = "SP17S005", bmsAddress = "A4:C1:38:12:34:56", preset = "60V 27–41Ah", cardRevision = "60V NMC card Rev 4.0",
        testedAh = 28.6, resistanceMilliohm = 62.0, endOfChargeDeltaMv = 12, liveDeltaMv = 8,
        auto = Checklist.automatic(profile, verified, live), ticked = ManualCheck.entries.toSet(),
        setByHand = setByHand, setByHandDone = setByHand.toSet(), passed = passed, problems = emptyList(),
        readBack = mapOf(
            JbdSetting.BALANCE_VOLTAGE to 4140, JbdSetting.BALANCE_ACCURACY to 15, JbdSetting.CELL_COUNT to 16, JbdSetting.FUNCTIONS to 0x000E,
        ),
    )
}
