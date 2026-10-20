package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.ble.LinkKeeper
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.dev.toHex
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsDriver
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Talks to one JBD board: polls basic info (0x03) then cell voltages (0x04) every interval,
 * one request at a time. Only [JbdRequest] frames are ever written.
 */
class JbdDriver(
    private val scope: CoroutineScope,
    private val links: GattLinkFactory,
    private val log: TrafficLog?,
    private val clock: () -> Long = System::currentTimeMillis,
) : BmsDriver {

    private val session = MutableStateFlow<JbdSession?>(null)

    private val keeper = LinkKeeper(
        scope = scope,
        open = { address -> links.create(address, JbdBrand.PROFILE) },
        log = log,
        onOpened = { link -> session.getAndUpdate { JbdSession(link, scope, log) }?.close() },
        onLost = { session.getAndUpdate { null }?.close() },
    )

    override val linkState: StateFlow<LinkState> = keeper.state

    private val _info = MutableStateFlow<BmsInfo?>(null)
    override val info: StateFlow<BmsInfo?> = _info.asStateFlow()

    override suspend fun connect(device: BmsDevice) {
        keeper.connect(device.address)
        // The app targets LiFePO4 packs (DB24SA08).
        _info.value = BmsInfo(brand = "JBD", model = null, chemistry = "LiFePO4")
        // What register 0x05 holds on the DB24SA08 is not confirmed; use it as the model if it answers.
        val name = session.value?.request(JbdRequest.HARDWARE)?.let { JbdParser.hardwareName(it.data) }
        if (!name.isNullOrBlank()) {
            log?.info("Hardware name: $name")
            _info.value = _info.value?.copy(model = name)
        }
    }

    override fun snapshots(interval: Duration): Flow<BmsSnapshot> = flow {
        while (true) {
            val current = session.filterNotNull().first()
            val started = clock()
            read(current)?.let { emit(it) }
            current.rssi()?.let(keeper::updateRssi)
            val elapsed = clock() - started
            delay((interval.inWholeMilliseconds - elapsed).coerceAtLeast(MIN_GAP_MS))
        }
    }

    override suspend fun disconnect() {
        keeper.close()
        _info.value = null
    }

    private suspend fun read(session: JbdSession): BmsSnapshot? {
        val basic = session.request(JbdRequest.BASIC_INFO) ?: return null
        val cells = session.request(JbdRequest.CELL_VOLTAGES) ?: return null
        return try {
            JbdParser.snapshot(JbdParser.basicInfo(basic.data), JbdParser.cellVoltages(cells.data), clock())
        } catch (e: JbdFormatException) {
            log?.error("Couldn't decode reply: ${e.message}")
            null
        }
    }

    private companion object {
        /** Even with a short refresh rate, leave the board a moment between rounds. */
        const val MIN_GAP_MS = 100L
    }
}

/**
 * One open link to a JBD board: a queue that sends one request at a time and waits for its reply
 * (1 s timeout, then one retry).
 */
internal class JbdSession(private val link: GattLink, scope: CoroutineScope, private val log: TrafficLog?) {
    private val assembler = JbdFrameAssembler()
    private val assemblerLock = Mutex()
    private val responses = Channel<JbdResponse>(Channel.UNLIMITED)
    private val queue = Mutex()

    // Started undispatched so it is listening before the first request goes out.
    private val reader = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        link.chunks.collect { chunk ->
            log?.rx(chunk)
            val events = assemblerLock.withLock { assembler.feed(chunk) }
            for (event in events) {
                when (event) {
                    is JbdFrameEvent.Frame -> responses.trySend(event.response)
                    is JbdFrameEvent.Rejected -> log?.error("Discarded (${event.reason}): ${event.bytes.toHex()}")
                }
            }
        }
    }

    /** Sends [request] and returns its reply, or null if neither attempt got one. */
    suspend fun request(request: JbdRequest): JbdResponse? = queue.withLock {
        repeat(ATTEMPTS) { attempt ->
            // Forget any half-received or late reply to an earlier request.
            assemblerLock.withLock { assembler.reset() }
            while (responses.tryReceive().isSuccess) Unit

            val bytes = request.encode()
            log?.tx(bytes)
            try {
                link.write(bytes)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log?.error("Write failed: ${e.message}")
                return null
            }
            val reply = withTimeoutOrNull(TIMEOUT_MS) {
                var response = responses.receive()
                while (response.register != request.register) response = responses.receive()
                response
            }
            if (reply != null) return reply
            log?.error("No reply to ${request.name}" + if (attempt == 0) ", retrying" else "")
        }
        null
    }

    suspend fun rssi(): Int? = try {
        link.readRssi()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    fun close() {
        reader.cancel()
    }

    companion object {
        const val TIMEOUT_MS = 1_000L
        const val ATTEMPTS = 2
    }
}
