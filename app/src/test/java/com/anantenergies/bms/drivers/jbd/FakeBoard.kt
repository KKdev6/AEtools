package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.ble.GattServiceMissing
import com.anantenergies.bms.ble.GattServices
import com.anantenergies.bms.ble.LinkException
import com.anantenergies.bms.ble.uuid16
import com.anantenergies.bms.drivers.jbd.JbdTestVectors.chunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * A pretend JBD board behind fake Bluetooth links, for driver tests in virtual time.
 * By default it answers every request with the spec frames, in 20-byte notifications.
 */
class FakeBoard {
    var services: GattServices = JBD_SERVICES

    /** The next this many connection attempts fail. */
    var failingConnects = 0

    /** Every frame the app wrote, in order, across all links. */
    val writes = mutableListOf<ByteArray>()

    val links = mutableListOf<FakeGattLink>()

    /** The frame to send back for the [n]th (1-based) [request] of its kind; null stays silent. */
    var reply: (request: JbdRequest, n: Int) -> ByteArray? = { request, _ -> specFrame(request) }

    private val counts = mutableMapOf<JbdRequest, Int>()

    fun sent(request: JbdRequest) = counts[request] ?: 0

    fun factory(scope: CoroutineScope) = GattLinkFactory { _, _ -> FakeGattLink(scope, this).also { links += it } }

    internal fun respond(bytes: ByteArray): ByteArray? {
        writes += bytes
        val request = JbdRequest.entries.firstOrNull { it.encode().contentEquals(bytes) } ?: return null
        val n = (counts[request] ?: 0) + 1
        counts[request] = n
        return reply(request, n)
    }

    companion object {
        val JBD_SERVICES: GattServices = mapOf(
            uuid16(0x180A) to listOf(uuid16(0x2A29)),
            JbdBrand.PROFILE.service to listOf(JbdBrand.PROFILE.notify, JbdBrand.PROFILE.write),
        )

        fun specFrame(request: JbdRequest): ByteArray = when (request) {
            JbdRequest.BASIC_INFO -> JbdTestVectors.BASIC_INFO
            JbdRequest.CELL_VOLTAGES -> JbdTestVectors.CELL_VOLTAGES
            JbdRequest.HARDWARE -> JbdTestVectors.response(0x05, "DB24SA08".toByteArray(Charsets.US_ASCII))
        }
    }
}

class FakeGattLink(private val scope: CoroutineScope, private val board: FakeBoard) : GattLink {
    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val chunks: Flow<ByteArray> = _chunks
    override val dropped = MutableStateFlow(false)

    override suspend fun connect(): GattServices {
        delay(CONNECT_MS)
        if (board.failingConnects > 0) {
            board.failingConnects--
            throw LinkException("No answer from the BMS")
        }
        if (JbdBrand.PROFILE.service !in board.services) throw GattServiceMissing(board.services)
        return board.services
    }

    override suspend fun write(bytes: ByteArray) {
        if (dropped.value) throw LinkException("Link dropped")
        val frame = board.respond(bytes) ?: return
        scope.launch {
            delay(20)
            frame.chunks(20).forEach {
                delay(5)
                _chunks.emit(it)
            }
        }
    }

    override suspend fun readRssi(): Int = -61

    override fun close() {
        dropped.value = true
    }

    companion object {
        const val CONNECT_MS = 100L
    }
}
