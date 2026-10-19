package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.domain.Protection
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.BASIC_INFO
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.BASIC_INFO_PROTECTION
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.CELL_VOLTAGES
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.SPEC_CELLS_MV
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.chunks
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JbdParserTest {

    @Test
    fun `basic info from the spec`() {
        val info = JbdParser.basicInfo(BASIC_INFO.payload())
        assertEquals(52.82, info.packVoltage, 1e-9)
        assertEquals(-12.40, info.current, 1e-9)
        assertEquals(78.00, info.remainingCapacityAh, 1e-9)
        assertEquals(100.00, info.nominalCapacityAh, 1e-9)
        assertEquals(42, info.cycleCount)
        assertEquals(setOf(2, 6, 13), info.balancingCells)
        assertEquals(emptyList<Protection>(), info.protections)
        assertEquals(0x10, info.softwareVersion)
        assertEquals(78, info.soc)
        assertTrue(info.chargeMosOn)
        assertTrue(info.dischargeMosOn)
        assertEquals(16, info.cellCount)
        assertEquals(listOf(31.0, 27.4, 28.1), info.temperatures)
    }

    @Test
    fun `basic info with cell overvoltage from the spec`() {
        val info = JbdParser.basicInfo(BASIC_INFO_PROTECTION.payload())
        assertEquals(56.00, info.packVoltage, 1e-9)
        assertEquals(0.0, info.current, 1e-9)
        assertEquals(100, info.soc)
        assertEquals(listOf(Protection.CELL_OVERVOLTAGE), info.protections)
        assertFalse(info.chargeMosOn)
        assertTrue(info.dischargeMosOn)
        assertEquals(emptySet<Int>(), info.balancingCells)
    }

    @Test
    fun `cell voltages from the spec`() {
        val cells = JbdParser.cellVoltages(CELL_VOLTAGES.payload())
        assertEquals(SPEC_CELLS_MV.map { it / 1000.0 }, cells)
    }

    @Test
    fun `chunked frames parse to the same snapshot`() {
        val assembler = JbdFrameAssembler()
        val responses = (BASIC_INFO + CELL_VOLTAGES).chunks(20)
            .flatMap { assembler.feed(it) }
            .filterIsInstance<JbdFrameEvent.Frame>()
            .associate { it.response.register to it.response.data }
        val snapshot = JbdParser.snapshot(
            JbdParser.basicInfo(responses.getValue(JbdRequest.BASIC_INFO.register)),
            JbdParser.cellVoltages(responses.getValue(JbdRequest.CELL_VOLTAGES.register)),
            timestamp = 1_000,
        )
        assertEquals(52.82, snapshot.packVoltage, 1e-9)
        assertEquals(-12.40, snapshot.current, 1e-9)
        assertEquals(52.82 * -12.40, snapshot.power, 1e-9)
        assertEquals(78, snapshot.soc)
        assertEquals(42, snapshot.cycleCount)
        assertEquals(16, snapshot.cellCount)
        assertEquals(SPEC_CELLS_MV.map { it / 1000.0 }, snapshot.cellVoltages)
        assertEquals(setOf(2, 6, 13), snapshot.balancingCells)
        assertEquals(listOf("T1", "T2", "T3"), snapshot.temperatures.map { it.label })
        assertEquals(listOf(31.0, 27.4, 28.1), snapshot.temperatures.map { it.celsius })
        assertTrue(snapshot.chargeMosOn && snapshot.dischargeMosOn)
        assertNull(snapshot.heaterOn)
        assertEquals(emptyList<Protection>(), snapshot.protections)
        assertEquals(1_000, snapshot.timestamp)
    }

    @Test
    fun `every protection bit maps to its message`() {
        val expected = listOf(
            Protection.CELL_OVERVOLTAGE, Protection.CELL_UNDERVOLTAGE, Protection.PACK_OVERVOLTAGE,
            Protection.PACK_UNDERVOLTAGE, Protection.CHARGE_OVER_TEMPERATURE, Protection.CHARGE_UNDER_TEMPERATURE,
            Protection.DISCHARGE_OVER_TEMPERATURE, Protection.DISCHARGE_UNDER_TEMPERATURE, Protection.CHARGE_OVERCURRENT,
            Protection.DISCHARGE_OVERCURRENT, Protection.SHORT_CIRCUIT, Protection.FRONT_END_IC_ERROR,
            Protection.MOS_SOFTWARE_LOCK,
        )
        expected.forEachIndexed { bit, protection ->
            assertEquals(listOf(protection), JbdParser.protections(1 shl bit))
        }
        assertEquals(expected, JbdParser.protections(0x1FFF))
        // Undefined bits are ignored.
        assertEquals(emptyList<Protection>(), JbdParser.protections(0xE000))
    }

    @Test
    fun `balancing bits for cells 17 to 32`() {
        val data = BASIC_INFO.payload().also {
            it[14] = 0x00
            it[15] = 0x81.toByte() // cells 17 and 24
            it[21] = 24 // cell count
        }
        val info = JbdParser.basicInfo(data)
        assertEquals(setOf(2, 6, 13, 16, 23), info.balancingCells)
        assertEquals(24, info.cellCount)
    }

    @Test
    fun `balancing on cells the snapshot has no voltage for is dropped`() {
        val data = BASIC_INFO.payload().also { it[15] = 0x01 } // cell 17 on a 16-cell list
        val snapshot = JbdParser.snapshot(JbdParser.basicInfo(data), List(16) { 3.3 }, timestamp = 0)
        assertEquals(setOf(2, 6, 13), snapshot.balancingCells)
    }

    @Test
    fun `extra bytes from newer firmware are ignored`() {
        val data = BASIC_INFO.payload() + byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val info = JbdParser.basicInfo(data)
        assertEquals(listOf(31.0, 27.4, 28.1), info.temperatures)
        assertEquals(78, info.soc)
    }

    @Test
    fun `no temperature sensors`() {
        val data = BASIC_INFO.payload().copyOfRange(0, 23).also { it[22] = 0 }
        assertEquals(emptyList<Double>(), JbdParser.basicInfo(data).temperatures)
    }

    @Test
    fun `short basic info is rejected`() {
        assertThrows(JbdFormatException::class.java) { JbdParser.basicInfo(BASIC_INFO.payload().copyOfRange(0, 22)) }
        // Says three sensors but only carries two.
        assertThrows(JbdFormatException::class.java) { JbdParser.basicInfo(BASIC_INFO.payload().copyOfRange(0, 27)) }
    }

    @Test
    fun `odd-length cell voltages are rejected`() {
        assertThrows(JbdFormatException::class.java) { JbdParser.cellVoltages(byteArrayOf(0x0C, 0xE5.toByte(), 0x0C)) }
    }

    @Test
    fun `hardware name`() {
        val data = "JBD-DB24SA08".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)
        assertEquals("JBD-DB24SA08", JbdParser.hardwareName(data))
    }
}
