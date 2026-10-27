package com.anantenergies.bms.factory.protocol

import com.anantenergies.bms.drivers.jbd.JbdFrameAssembler
import com.anantenergies.bms.drivers.jbd.JbdFrameEvent
import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Every frame here is copied from the real-board capture (docs/jbd-write-protocol.md). */
class JbdSettingsFramesTest {
    private val profile = BmsModels.SP17S005

    @Test
    fun `fixed frames match the capture`() {
        assertHex("DD A5 00 00 00 00 77", JbdSettingsFrames.probeChipType)
        assertHex("DD 5A 00 02 56 78 FF 30 77", JbdSettingsFrames.enterSettings)
        assertHex("DD 5A 01 02 28 28 FF AD 77", JbdSettingsFrames.exitAndSave)
        assertHex("DD 5A 01 02 00 00 FF FD 77", JbdSettingsFrames.exitWithoutSaving)
    }

    @Test
    fun `reads match the capture`() {
        assertHex("DD A5 FA 03 00 10 08 FE EB 77", JbdSettingsFrames.read(0x10, 8))
        assertHex("DD A5 FA 03 00 30 04 FE CF 77", JbdSettingsFrames.read(0x30, 4))
        assertHex("DD A5 FA 03 00 26 02 FE DB 77", JbdSettingsFrames.read(0x26, 2))
        assertHex("DD A5 FA 03 00 1A 02 FE E7 77", JbdSettingsFrames.read(0x1A, 2))
        assertHex("DD A5 FA 03 00 1D 01 FE E5 77", JbdSettingsFrames.read(0x1D, 1))
        assertHex("DD A5 FA 03 00 00 01 FF 02 77", JbdSettingsFrames.read(0x00, 1))
    }

    @Test
    fun `writes match the capture`() {
        assertHex("DD 5A FA 05 00 33 01 00 03 FE CA 77", JbdSettingsFrames.write(profile, JbdSetting.CELL_HIGH_DELAY, 3))
        assertHex("DD 5A FA 05 00 33 01 00 02 FE CB 77", JbdSettingsFrames.write(profile, JbdSetting.CELL_HIGH_DELAY, 2))
        assertHex("DD 5A FA 05 00 1D 01 00 0E FE D5 77", JbdSettingsFrames.write(profile, JbdSetting.FUNCTIONS, 0x000E))
        assertHex("DD 5A FA 05 00 1D 01 00 06 FE DD 77", JbdSettingsFrames.write(profile, JbdSetting.FUNCTIONS, 0x0006))
    }

    @Test
    fun `settings the profile doesn't allow can't be written`() {
        listOf(JbdSetting.CELL_COUNT, JbdSetting.LEVEL2_OVER_CURRENT, JbdSetting.SHORT_CIRCUIT, JbdSetting.NTC_ENABLE).forEach {
            assertThrows(IllegalArgumentException::class.java) { JbdSettingsFrames.write(profile, it, 0) }
        }
    }

    @Test
    fun `captured voltage read decodes to the pack's values`() {
        val reply = assemble("DD FA 00 13 00 10 08 1A 90 19 F0 11 80 12 20 10 9A 10 36 0A", "F0 0B 54 FB 16 77")
        assertEquals(0xFA, reply.register)
        val values = JbdSettingsFrames.readValues(reply.data, 0x10, 8)!!
        assertEquals(listOf(6800, 6640, 4480, 4640, 4250, 4150, 2800, 2900), values)
        assertEquals("68.00 V", JbdSetting.PACK_HIGH.format(values[0]))
        assertEquals("4.250 V", JbdSetting.CELL_HIGH.format(values[4]))
    }

    @Test
    fun `read reply for a different address is not accepted`() {
        val reply = assemble("DD FA 00 07 00 1A 02 0F 3C 00 0F FF 83 77")
        assertEquals(listOf(3900, 15), JbdSettingsFrames.readValues(reply.data, 0x1A, 2))
        assertEquals(null, JbdSettingsFrames.readValues(reply.data, 0x1B, 2))
        assertEquals(null, JbdSettingsFrames.readValues(reply.data, 0x1A, 1))
    }

    @Test
    fun `captured replies to probe, enter, write and save all check out`() {
        assertEquals(listOf<Byte>(0x00, 0x01), assemble("DD 00 00 02 00 01 FF FD 77").data.toList())
        assertEquals(0x00, assemble("DD 00 00 00 00 00 77").register)
        assertEquals(0xFA, assemble("DD FA 00 00 00 00 77").register)
        assertEquals(0x01, assemble("DD 01 00 00 00 00 77").register)
    }

    private fun assemble(vararg chunks: String) = JbdFrameAssembler().let { assembler ->
        chunks.flatMap { assembler.feed(hex(it)) }.filterIsInstance<JbdFrameEvent.Frame>().single().response
    }

    private fun assertHex(expected: String, actual: ByteArray) =
        assertEquals(expected, actual.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })

    private fun hex(text: String) = text.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
