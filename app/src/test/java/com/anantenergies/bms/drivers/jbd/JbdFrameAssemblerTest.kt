package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.drivers.jbd.JbdTestVectors.BASIC_INFO
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.BASIC_INFO_PROTECTION
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.CELL_VOLTAGES
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.chunks
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.hex
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.payload
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.response
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JbdFrameAssemblerTest {

    private fun JbdFrameAssembler.feedAll(chunks: List<ByteArray>) = chunks.flatMap { feed(it) }

    private fun List<JbdFrameEvent>.frames() = filterIsInstance<JbdFrameEvent.Frame>().map { it.response }

    private fun List<JbdFrameEvent>.rejections() = filterIsInstance<JbdFrameEvent.Rejected>().map { it.reason }

    @Test
    fun `each spec frame arrives whole`() {
        for (frame in listOf(BASIC_INFO, CELL_VOLTAGES, BASIC_INFO_PROTECTION)) {
            val events = JbdFrameAssembler().feed(frame)
            assertEquals(1, events.size)
            val response = events.frames().single()
            assertEquals(frame[1].toInt() and 0xFF, response.register)
            assertArrayEquals(frame.payload(), response.data)
        }
    }

    @Test
    fun `each spec frame split into 20-byte chunks`() {
        for (frame in listOf(BASIC_INFO, CELL_VOLTAGES, BASIC_INFO_PROTECTION)) {
            val assembler = JbdFrameAssembler()
            val chunks = frame.chunks(20)
            assertTrue(chunks.size > 1)
            // Nothing comes out until the last chunk.
            chunks.dropLast(1).forEach { assertEquals(emptyList<JbdFrameEvent>(), assembler.feed(it)) }
            val response = assembler.feed(chunks.last()).frames().single()
            assertArrayEquals(frame.payload(), response.data)
        }
    }

    @Test
    fun `frames back to back across chunk boundaries`() {
        val stream = BASIC_INFO + CELL_VOLTAGES + BASIC_INFO_PROTECTION
        val events = JbdFrameAssembler().feedAll(stream.chunks(20))
        assertEquals(emptyList<JbdRejection>(), events.rejections())
        assertEquals(listOf(0x03, 0x04, 0x03), events.frames().map { it.register })
    }

    @Test
    fun `one byte at a time`() {
        val events = JbdFrameAssembler().feedAll(CELL_VOLTAGES.chunks(1))
        assertArrayEquals(CELL_VOLTAGES.payload(), events.frames().single().data)
    }

    @Test
    fun `corrupted checksum is rejected`() {
        val corrupted = BASIC_INFO.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val events = JbdFrameAssembler().feed(corrupted)
        assertEquals(listOf(JbdRejection.BAD_CHECKSUM), events.rejections())
        assertEquals(emptyList<JbdResponse>(), events.frames())
    }

    @Test
    fun `a good frame after a corrupted one still parses`() {
        val corrupted = BASIC_INFO.copyOf().also { it[it.size - 2] = 0x00 }
        val events = JbdFrameAssembler().feedAll((corrupted + CELL_VOLTAGES).chunks(20))
        assertEquals(listOf(JbdRejection.BAD_CHECKSUM), events.rejections())
        assertEquals(listOf(0x04), events.frames().map { it.register })
    }

    @Test
    fun `status other than 00 is rejected`() {
        val error = response(register = 0x03, data = ByteArray(0), status = 0x80)
        assertEquals("DD 03 80 00 FF 80 77", error.toHex())
        val events = JbdFrameAssembler().feed(error)
        assertEquals(listOf(JbdRejection.ERROR_STATUS), events.rejections())
        assertEquals(emptyList<JbdResponse>(), events.frames())
    }

    @Test
    fun `spec frame with an error status is rejected`() {
        val error = response(register = 0x03, data = BASIC_INFO.payload(), status = 0x01)
        val events = JbdFrameAssembler().feedAll(error.chunks(20))
        assertEquals(listOf(JbdRejection.ERROR_STATUS), events.rejections())
    }

    @Test
    fun `garbage before a frame is skipped`() {
        val events = JbdFrameAssembler().feed(hex("00 12 77 FF") + CELL_VOLTAGES)
        assertEquals(listOf(JbdRejection.GARBAGE), events.rejections())
        assertEquals("00 12 77 FF", (events.first() as JbdFrameEvent.Rejected).bytes.toHex())
        assertEquals(listOf(0x04), events.frames().map { it.register })
    }

    @Test
    fun `a false start resyncs on the next frame`() {
        // A stray DD whose "frame" doesn't end in 77.
        val falseStart = hex("DD 03 00 02 11 22 33 44 55")
        val events = JbdFrameAssembler().feedAll((falseStart + BASIC_INFO).chunks(20))
        assertEquals(JbdRejection.BAD_END, events.rejections().first())
        assertEquals(listOf(0x03), events.frames().map { it.register })
        assertArrayEquals(BASIC_INFO.payload(), events.frames().single().data)
    }

    @Test
    fun `reset drops a partial frame`() {
        val assembler = JbdFrameAssembler()
        assembler.feed(BASIC_INFO.copyOfRange(0, 20))
        assembler.reset()
        val events = assembler.feed(CELL_VOLTAGES)
        assertEquals(emptyList<JbdRejection>(), events.rejections())
        assertEquals(listOf(0x04), events.frames().map { it.register })
    }
}
