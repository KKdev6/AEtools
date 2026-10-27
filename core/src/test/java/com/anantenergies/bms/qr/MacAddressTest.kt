package com.anantenergies.bms.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacAddressTest {
    private val expected = "A4:C1:38:12:34:56"

    @Test
    fun colons() {
        assertEquals(expected, MacAddress.findIn("A4:C1:38:12:34:56"))
        assertEquals(expected, MacAddress.findIn("a4:c1:38:12:34:56"))
    }

    @Test
    fun dashes() {
        assertEquals(expected, MacAddress.findIn("A4-C1-38-12-34-56"))
        assertEquals(expected, MacAddress.findIn("a4-c1-38-12-34-56"))
    }

    @Test
    fun bare() {
        assertEquals(expected, MacAddress.findIn("a4c138123456"))
        assertEquals(expected, MacAddress.findIn("A4C138123456"))
    }

    @Test
    fun surroundingWhitespace() {
        assertEquals(expected, MacAddress.findIn("  A4:C1:38:12:34:56\n"))
    }

    @Test
    fun insideUrls() {
        assertEquals(expected, MacAddress.findIn("https://example.com/bms?mac=A4:C1:38:12:34:56&model=DB24SA08"))
        assertEquals(expected, MacAddress.findIn("https://example.com/bms/A4-C1-38-12-34-56"))
        assertEquals(expected, MacAddress.findIn("https://example.com/bms/a4c138123456?ref=label"))
        assertEquals(expected, MacAddress.findIn("https://example.com/bms?mac=A4%3AC1%3A38%3A12%3A34%3A56"))
        assertEquals(expected, MacAddress.findIn("https://example.com/bms?mac%3Da4c138123456"))
    }

    @Test
    fun insideText() {
        assertEquals(expected, MacAddress.findIn("MAC:A4:C1:38:12:34:56"))
        assertEquals(expected, MacAddress.findIn("mac-a4-c1-38-12-34-56"))
        assertEquals(expected, MacAddress.findIn("Model DB24SA08\nMAC: a4-c1-38-12-34-56\nSN 12345"))
        assertEquals(expected, MacAddress.findIn("BMS mac=a4c138123456;"))
    }

    @Test
    fun separatedFormBeatsBareDigits() {
        assertEquals(expected, MacAddress.findIn("SN 202410210001 MAC A4:C1:38:12:34:56"))
    }

    @Test
    fun skipsPlaceholderAddresses() {
        assertEquals(expected, MacAddress.findIn("00:00:00:00:00:00 A4:C1:38:12:34:56"))
        assertNull(MacAddress.findIn("FF:FF:FF:FF:FF:FF"))
        assertNull(MacAddress.findIn("000000000000"))
    }

    @Test
    fun rejectsTextWithoutAnAddress() {
        assertNull(MacAddress.findIn(""))
        assertNull(MacAddress.findIn("Anant Energies · Empowering the future"))
        assertNull(MacAddress.findIn("https://example.com/products/db24sa08"))
    }

    @Test
    fun rejectsWrongLengths() {
        assertNull(MacAddress.findIn("A4:C1:38:12:34"))
        assertNull(MacAddress.findIn("a4c13812345"))
        assertNull(MacAddress.findIn("a4c1381234567"))
        assertNull(MacAddress.findIn("a4c138123456a4c138123456"))
    }

    @Test
    fun rejectsInvalidHexAndMixedSeparators() {
        assertNull(MacAddress.findIn("G4:C1:38:12:34:56"))
        assertNull(MacAddress.findIn("A4:C1-38:12-34:56"))
        assertNull(MacAddress.findIn("A4.C1.38.12.34.56"))
    }

    @Test
    fun ignoresTheLastGroupOfAUuid() {
        assertNull(MacAddress.findIn("https://example.com/item/123e4567-e89b-12d3-a456-426614174000"))
    }
}
