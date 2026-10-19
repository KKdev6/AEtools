package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.drivers.jbd.JbdTestVectors.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class JbdRequestTest {

    @Test
    fun `basic info request`() {
        assertEquals("DD A5 03 00 FF FD 77", JbdRequest.BASIC_INFO.encode().toHex())
    }

    @Test
    fun `cell voltages request`() {
        assertEquals("DD A5 04 00 FF FC 77", JbdRequest.CELL_VOLTAGES.encode().toHex())
    }

    @Test
    fun `hardware name request`() {
        assertEquals("DD A5 05 00 FF FB 77", JbdRequest.HARDWARE.encode().toHex())
    }

    /** Read-only guard: these three reads are the only bytes the app can ever send to a JBD BMS. */
    @Test
    fun `the only requests that exist are the three reads`() {
        val allowed = setOf("DD A5 03 00 FF FD 77", "DD A5 04 00 FF FC 77", "DD A5 05 00 FF FB 77")
        assertEquals(allowed, JbdRequest.entries.map { it.encode().toHex() }.toSet())
        JbdRequest.entries.forEach { assertEquals(JbdFrame.READ, it.encode()[1].toInt() and 0xFF) }
    }
}
