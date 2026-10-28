package com.anantenergies.bms.factory.apply

import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.protocol.BoardChannel
import com.anantenergies.bms.factory.protocol.BoardReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAllTest {
    private val profile = BmsModels.SP17S005
    private val sent = mutableListOf<String>()

    /** Answers every read with each register holding its own address. */
    private val board = BoardChannel { frame, _ ->
        sent += frame.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val address = (frame[4].toInt() and 0xFF) shl 8 or (frame[5].toInt() and 0xFF)
        val count = frame[6].toInt() and 0xFF
        val values = (address until address + count).flatMap { listOf((it shr 8).toByte(), it.toByte()) }
        BoardReply.Ok(byteArrayOf(frame[4], frame[5], frame[6]) + values.toByteArray())
    }

    @Test
    fun `reads in the JBD app's groups, including the ones in the real-board capture`() = runTest {
        PresetApplier(board).readAll(profile)
        assertEquals(profile.readBlocks.size, sent.size)
        listOf(
            "DD A5 FA 03 00 10 08 FE EB 77",
            "DD A5 FA 03 00 30 04 FE CF 77",
            "DD A5 FA 03 00 26 02 FE DB 77",
            "DD A5 FA 03 00 1A 02 FE E7 77",
        ).forEach { assertTrue("$it not sent", it in sent) }
        // Never one read across two of the JBD app's groups, e.g. 34–41.
        assertTrue(sent.none { it.startsWith("DD A5 FA 03 00 22 08") })
    }

    @Test
    fun `every setting gets the value of its own address`() = runTest {
        val values = PresetApplier(board).readAll(profile)!!
        assertEquals(profile.writable + profile.readOnly, values.keys)
        values.forEach { (setting, value) -> assertEquals(setting.name, setting.address, value) }
    }

    @Test
    fun `a refused read fails the whole read`() = runTest {
        val refusing = BoardChannel { frame, register ->
            val address = (frame[4].toInt() and 0xFF) shl 8 or (frame[5].toInt() and 0xFF)
            if (address == JbdSetting.SOC_90.address) BoardReply.Refused(0x82) else board.send(frame, register)
        }
        assertNull(PresetApplier(refusing).readAll(profile))
    }
}
