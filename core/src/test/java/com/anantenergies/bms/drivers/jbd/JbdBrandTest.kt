package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.domain.Advertisement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JbdBrandTest {

    private fun ad(name: String?, vararg services: String) = Advertisement(name, "A4:C1:38:00:00:01", -60, services.toList())

    @Test
    fun `recognised by the advertised FF00 service`() {
        assertTrue(JbdBrand.matches(ad(null, "0000ff00-0000-1000-8000-00805f9b34fb")))
        assertTrue(JbdBrand.matches(ad("Some odd name", "0000ff00-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun `recognised by JBD-style names`() {
        listOf("xiaoxiang BMS", "JBD-SP04S028", "SP04S034L4S100A", "DP04S007L4S100A", "UP16S010", "DB24SA08", " jbd bms").forEach {
            assertTrue(it, JbdBrand.matches(ad(it)))
        }
    }

    @Test
    fun `other devices are not recognised`() {
        listOf("Galaxy Buds", "Mi Band 7", "SPEAKER", "JK-B2A24S", "").forEach {
            assertFalse(it, JbdBrand.matches(ad(it)))
        }
        assertFalse(JbdBrand.matches(ad(null)))
        assertFalse(JbdBrand.matches(ad(null, "0000ffe0-0000-1000-8000-00805f9b34fb")))
    }
}
