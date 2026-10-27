package com.anantenergies.bms.dev

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficLogTest {
    private fun TrafficLog.texts() = entries.value.map { it.text }

    @Test
    fun keepsEverythingUpToCapacity() {
        val log = TrafficLog(clock = { 0L }, capacity = 3)
        log.info("services")
        log.tx(byteArrayOf(0x01))
        log.rx(byteArrayOf(0x02))
        assertEquals(listOf("services", "01", "02"), log.texts())
    }

    @Test
    fun dropsOldestTrafficBeforeNotes() {
        val log = TrafficLog(clock = { 0L }, capacity = 3)
        log.info("services")
        log.tx(byteArrayOf(0x01))
        log.rx(byteArrayOf(0x02))
        log.tx(byteArrayOf(0x03))
        log.rx(byteArrayOf(0x04))
        assertEquals(listOf("services", "03", "04"), log.texts())
    }

    @Test
    fun dropsOldestNoteWhenThereIsNoTraffic() {
        val log = TrafficLog(clock = { 0L }, capacity = 2)
        log.info("a")
        log.error("b")
        log.info("c")
        assertEquals(listOf("b", "c"), log.texts())
    }
}
