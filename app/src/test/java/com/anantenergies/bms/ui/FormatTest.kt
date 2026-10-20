package com.anantenergies.bms.ui

import com.anantenergies.bms.data.TempUnit
import com.anantenergies.bms.domain.LinkState
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `current uses a true minus sign and fewer decimals above 10 A`() {
        assertEquals("−12.4", Format.current(-12.4))
        assertEquals("3.46", Format.current(3.456))
        assertEquals("−0.05", Format.current(-0.05))
    }

    @Test
    fun `values that round to zero have no sign`() {
        assertEquals("0.00", Format.current(-0.004))
        assertEquals("0", Format.power(-0.2))
    }

    @Test
    fun `power is shown without sign`() {
        assertEquals("655", Format.power(52.82 * -12.4))
    }

    @Test
    fun `voltages`() {
        assertEquals("52.82", Format.packVoltage(52.82))
        assertEquals("3.311", Format.cellVoltage(3.311))
        assertEquals("19", Format.millivolts(3.311 - 3.292))
    }

    @Test
    fun `temperature in both units`() {
        assertEquals("31.0 °C", Format.temperature(31.0, TempUnit.CELSIUS))
        assertEquals("87.8 °F", Format.temperature(31.0, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `address keeps the first three bytes`() {
        assertEquals("A4:C1:38:••:••:••", Format.maskedAddress("A4:C1:38:12:34:56"))
        assertEquals("not-a-mac", Format.maskedAddress("not-a-mac"))
    }

    @Test
    fun `status line gets shorter for narrow screens`() {
        val lines = linkStatusLines(LinkState.Connected(rssi = -62), "Connected", updatedAt = 1_000, now = 2_000)
        assertEquals(
            listOf("Connected · −62 dBm · updated 1 s ago", "Connected · −62 dBm · 1 s ago", "Connected · 1 s ago"),
            lines,
        )
        assertEquals(listOf("Connecting…"), linkStatusLines(LinkState.Connecting, "Connecting…", updatedAt = null, now = 0))
    }

    @Test
    fun `age`() {
        assertEquals("just now", Format.age(0))
        assertEquals("1 s ago", Format.age(1))
        assertEquals("2 min ago", Format.age(150))
    }
}
