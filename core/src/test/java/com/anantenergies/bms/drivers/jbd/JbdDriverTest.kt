package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.ble.LinkKeeper
import com.anantenergies.bms.domain.BmsConnectException
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.LinkState
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.toHex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class JbdDriverTest {

    private val device = BmsDevice("JBD-DB24SA08", "A4:C1:38:00:00:01", JbdBrand.ID)

    private fun TestScope.driver(board: FakeBoard) =
        JbdDriver(backgroundScope, board.factory(backgroundScope), log = null, clock = { testScheduler.currentTime })

    @Test
    fun `connects, reads the board name, then polls basic info and cells`() = runTest {
        val board = FakeBoard()
        val driver = driver(board)
        driver.connect(device)
        assertEquals(LinkState.Connected(rssi = null), driver.linkState.value)
        assertEquals("DB24SA08", driver.info.value?.model)
        assertEquals("Li-ion", driver.info.value?.chemistry)

        val snapshot = driver.snapshots(2.seconds).first()
        assertEquals(52.82, snapshot.packVoltage, 1e-9)
        assertEquals(-12.40, snapshot.current, 1e-9)
        assertEquals(78, snapshot.soc)
        assertEquals(16, snapshot.cellVoltages.size)
        assertEquals(setOf(2, 6, 13), snapshot.balancingCells)
        assertEquals(
            listOf(JbdRequest.HARDWARE, JbdRequest.BASIC_INFO, JbdRequest.CELL_VOLTAGES).map { it.encode().toHex() },
            board.writes.map { it.toHex() },
        )
        driver.disconnect()
    }

    @Test
    fun `polls once per interval`() = runTest {
        val driver = driver(FakeBoard())
        driver.connect(device)
        val times = driver.snapshots(2.seconds).take(3).toList().map { it.timestamp }
        assertEquals(listOf(2_000L, 2_000L), times.zipWithNext { a, b -> b - a })
        driver.disconnect()
    }

    @Test
    fun `only the three read requests are ever written`() = runTest {
        val board = FakeBoard().apply {
            // Mix in timeouts and retries too.
            reply = { request, n -> if (n % 3 == 0) null else FakeBoard.specFrame(request) }
        }
        val driver = driver(board)
        driver.connect(device)
        driver.snapshots(1.seconds).take(5).toList()
        val allowed = JbdRequest.entries.map { it.encode().toHex() }.toSet()
        assertTrue(board.writes.isNotEmpty())
        board.writes.forEach { assertTrue(it.toHex() in allowed) }
        driver.disconnect()
    }

    @Test
    fun `no reply within 1 s is retried once`() = runTest {
        val board = FakeBoard().apply {
            reply = { request, n -> if (request == JbdRequest.BASIC_INFO && n == 1) null else FakeBoard.specFrame(request) }
        }
        val driver = driver(board)
        driver.connect(device)
        val start = testScheduler.currentTime
        val snapshot = driver.snapshots(2.seconds).first()
        assertEquals(78, snapshot.soc)
        assertEquals(2, board.sent(JbdRequest.BASIC_INFO))
        // Retried straight after the 1 s timeout, not in the next polling round 2 s later.
        val waited = snapshot.timestamp - start
        assertTrue("waited $waited ms", waited >= JbdSession.TIMEOUT_MS && waited < 1_500)
        driver.disconnect()
    }

    @Test
    fun `two missed replies skip the round and the next round tries again`() = runTest {
        val board = FakeBoard().apply {
            reply = { request, n -> if (request == JbdRequest.BASIC_INFO && n <= 2) null else FakeBoard.specFrame(request) }
        }
        val driver = driver(board)
        driver.connect(device)
        val start = testScheduler.currentTime
        val snapshot = driver.snapshots(2.seconds).first()
        assertEquals(3, board.sent(JbdRequest.BASIC_INFO))
        assertEquals(1, board.sent(JbdRequest.CELL_VOLTAGES))
        // Both misses happen in the first round (2 × 1 s); the second round starts right after.
        val waited = snapshot.timestamp - start
        assertTrue("waited $waited ms", waited in 2_000 until 2_500)
        driver.disconnect()
    }

    @Test
    fun `a corrupted reply is discarded and retried`() = runTest {
        val board = FakeBoard().apply {
            reply = { request, n ->
                val frame = FakeBoard.specFrame(request)
                if (request == JbdRequest.BASIC_INFO && n == 1) frame.copyOf().also { it[10] = (it[10] + 1).toByte() } else frame
            }
        }
        val driver = driver(board)
        driver.connect(device)
        val start = testScheduler.currentTime
        val snapshot = driver.snapshots(2.seconds).first()
        assertEquals(52.82, snapshot.packVoltage, 1e-9)
        assertEquals(2, board.sent(JbdRequest.BASIC_INFO))
        assertTrue("retried in the same round", snapshot.timestamp - start < 1_500)
        driver.disconnect()
    }

    @Test
    fun `a reply for another register is ignored`() = runTest {
        val board = FakeBoard().apply {
            reply = { request, _ ->
                if (request == JbdRequest.BASIC_INFO) JbdTestVectors.CELL_VOLTAGES + JbdTestVectors.BASIC_INFO else FakeBoard.specFrame(request)
            }
        }
        val driver = driver(board)
        driver.connect(device)
        assertEquals(78, driver.snapshots(2.seconds).first().soc)
        assertEquals(1, board.sent(JbdRequest.BASIC_INFO))
        driver.disconnect()
    }

    @Test
    fun `a board without the JBD service is not supported`() = runTest {
        val board = FakeBoard().apply { services = FakeBoard.JBD_SERVICES.filterKeys { it != JbdBrand.PROFILE.service } }
        val driver = driver(board)
        try {
            driver.connect(device)
            fail("Expected NotSupported")
        } catch (e: BmsConnectException.NotSupported) {
            assertEquals(listOf("180a: 2a29"), e.services)
        }
        assertEquals(1, board.links.size)
        assertEquals(LinkState.Disconnected, driver.linkState.value)
    }

    @Test
    fun `gives up after three failed attempts`() = runTest {
        val board = FakeBoard().apply { failingConnects = 3 }
        val driver = driver(board)
        try {
            driver.connect(device)
            fail("Expected Unreachable")
        } catch (e: BmsConnectException.Unreachable) {
            assertEquals("No answer from the BMS", e.message)
        }
        assertEquals(LinkKeeper.INITIAL_ATTEMPTS, board.links.size)
        // Three connection attempts with 1 s and 2 s pauses between them.
        assertEquals(3 * FakeGattLink.CONNECT_MS + 1_000 + 2_000, testScheduler.currentTime)
    }

    @Test
    fun `connects on a later attempt`() = runTest {
        val board = FakeBoard().apply { failingConnects = 2 }
        val driver = driver(board)
        driver.connect(device)
        assertEquals(LinkState.Connected(rssi = null), driver.linkState.value)
        assertEquals(3, board.links.size)
        driver.disconnect()
    }

    @Test
    fun `reconnects with backoff after the link drops`() = runTest {
        val board = FakeBoard()
        val driver = driver(board)
        driver.connect(device)

        board.failingConnects = 2
        board.links.last().dropped.value = true
        runCurrent()
        assertEquals(LinkState.Reconnecting(attempt = 1), driver.linkState.value)

        advanceTimeBy(1_000 + FakeGattLink.CONNECT_MS + 1)
        assertEquals(LinkState.Reconnecting(attempt = 2), driver.linkState.value)
        advanceTimeBy(2_000 + FakeGattLink.CONNECT_MS)
        assertEquals(LinkState.Reconnecting(attempt = 3), driver.linkState.value)
        advanceTimeBy(4_000 + FakeGattLink.CONNECT_MS)
        assertEquals(LinkState.Connected(rssi = null), driver.linkState.value)
        assertEquals(4, board.links.size)

        // Polling carries on over the new link.
        assertEquals(78, driver.snapshots(2.seconds).first().soc)
        driver.disconnect()
        assertEquals(LinkState.Disconnected, driver.linkState.value)
    }

    @Test
    fun `backoff grows to 15 s`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), (1..6).map { LinkKeeper.backoffMs(it) })
    }
}
