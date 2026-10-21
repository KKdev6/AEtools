package com.anantenergies.bms.drivers.sim

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.ble.GattServices
import com.anantenergies.bms.ble.LinkException
import com.anantenergies.bms.ble.uuid16
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.drivers.demo.DemoPackModel
import com.anantenergies.bms.drivers.demo.DemoScenario
import com.anantenergies.bms.drivers.jbd.JbdBrand
import com.anantenergies.bms.drivers.jbd.JbdFrame
import com.anantenergies.bms.drivers.jbd.JbdParser
import com.anantenergies.bms.drivers.jbd.JbdRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SimScenario(val label: String) {
    NORMAL("Normal"),
    CELL_OVERVOLTAGE("Cell overvoltage protection"),
    OVER_TEMPERATURE("Over-temperature protection"),
    DROP_REPLIES("Drop some replies"),
    CORRUPT_REPLIES("Corrupt some replies"),
    LINK_DROP("Link drops, then comes back"),
    OUT_OF_RANGE("Out of range (can't connect)"),
    SILENT("BMS stops answering"),
}

/**
 * A pretend JBD board for debug builds. It answers the app's real JBD requests with real JBD
 * frames, split into 20-byte notifications, so the whole JBD path runs without hardware.
 * Only the radio is simulated.
 */
class SimulatedJbdBoard {
    private val _scenario = MutableStateFlow(SimScenario.NORMAL)
    val scenario: StateFlow<SimScenario> = _scenario.asStateFlow()

    private val model = DemoPackModel(seed = System.nanoTime())
    private var lastStep = System.currentTimeMillis()
    private var latest: BmsSnapshot = model.snapshot(lastStep)

    @Volatile private var link: SimulatedJbdLink? = null
    @Volatile private var unreachableUntil = 0L

    fun select(scenario: SimScenario) {
        _scenario.value = scenario
        if (scenario == SimScenario.LINK_DROP) {
            unreachableUntil = System.currentTimeMillis() + OUT_OF_RANGE_MS
            link?.drop()
        }
        if (scenario == SimScenario.OUT_OF_RANGE) link?.drop()
    }

    /** Routes the simulated address here and everything else to the real Bluetooth stack. */
    fun linkFactory(real: GattLinkFactory) = GattLinkFactory { address, profile ->
        if (address == ADDRESS) SimulatedJbdLink(this) else real.create(address, profile)
    }

    internal fun canConnect() = _scenario.value != SimScenario.OUT_OF_RANGE && System.currentTimeMillis() >= unreachableUntil

    internal fun attach(link: SimulatedJbdLink) {
        this.link = link
        if (_scenario.value == SimScenario.LINK_DROP) _scenario.value = SimScenario.NORMAL
    }

    /** The whole frame the board sends back for [request], or null if it stays silent. */
    @Synchronized
    internal fun reply(request: JbdRequest): ByteArray? {
        val scenario = _scenario.value
        if (scenario == SimScenario.SILENT) return null
        if (scenario == SimScenario.DROP_REPLIES && Random.nextInt(100) < 30) return null

        if (request == JbdRequest.BASIC_INFO) {
            val now = System.currentTimeMillis()
            model.scenario = when (scenario) {
                SimScenario.CELL_OVERVOLTAGE -> DemoScenario.CELL_OVERVOLTAGE
                SimScenario.OVER_TEMPERATURE -> DemoScenario.OVER_TEMPERATURE
                else -> DemoScenario.NORMAL
            }
            model.step((now - lastStep) / 1000.0)
            lastStep = now
            latest = model.snapshot(now)
        }
        val payload = when (request) {
            JbdRequest.BASIC_INFO -> basicInfo(latest)
            JbdRequest.CELL_VOLTAGES -> cellVoltages(latest)
            JbdRequest.HARDWARE -> "DB24SA08-SIM".toByteArray(Charsets.US_ASCII)
        }
        val frame = frame(request.register, payload)
        if (scenario == SimScenario.CORRUPT_REPLIES && Random.nextInt(100) < 25) {
            frame[JbdFrame.HEADER_SIZE] = (frame[JbdFrame.HEADER_SIZE] + 1).toByte()
        }
        return frame
    }

    internal fun rssi() = -58 + Random.nextInt(-4, 5)

    companion object {
        /** Not a real MAC, so it can never reach the Bluetooth stack. */
        const val ADDRESS = "SI:MU:LA:TE:D0:01"

        val ADVERTISEMENT = Advertisement(
            name = "JBD-DB24SA08-SIM",
            address = ADDRESS,
            rssi = -58,
            serviceUuids = listOf(JbdBrand.PROFILE.service.toString()),
        )

        private const val OUT_OF_RANGE_MS = 8_000L

        fun frame(register: Int, payload: ByteArray): ByteArray {
            val body = byteArrayOf(JbdFrame.START.toByte(), register.toByte(), JbdFrame.STATUS_OK.toByte(), payload.size.toByte()) + payload
            val checksum = JbdFrame.checksum(body, from = 2, until = body.size)
            return body + byteArrayOf((checksum shr 8).toByte(), checksum.toByte(), JbdFrame.END.toByte())
        }

        /** The 0x03 payload for [s], laid out as in CLAUDE.md. */
        fun basicInfo(s: BmsSnapshot): ByteArray {
            val balance = s.balancingCells.sumOf { 1 shl it }
            val protectionBits = (0 until 16).filter { bit ->
                JbdParser.protections(1 shl bit).any { it in s.protections }
            }.sumOf { 1 shl it }
            val mos = (if (s.chargeMosOn) 0x01 else 0) or (if (s.dischargeMosOn) 0x02 else 0)
            val out = mutableListOf<Byte>()
            fun u8(value: Int) = out.add(value.toByte())
            fun u16(value: Int) {
                u8(value shr 8)
                u8(value)
            }
            u16((s.packVoltage * 100).roundToInt())
            u16((s.current * 100).roundToInt())
            u16(s.soc * 100) // remaining capacity of a 100 Ah pack, in 10 mAh
            u16(10_000) // nominal 100 Ah
            u16(s.cycleCount)
            u16(0) // production date
            u16(balance and 0xFFFF)
            u16(balance ushr 16)
            u16(protectionBits)
            u8(0x10) // software version
            u8(s.soc)
            u8(mos)
            u8(s.cellCount)
            u8(s.temperatures.size)
            s.temperatures.forEach { u16((it.celsius * 10).roundToInt() + 2731) }
            return out.toByteArray()
        }

        fun cellVoltages(s: BmsSnapshot): ByteArray =
            s.cellVoltages.flatMap {
                val mv = (it * 1000).roundToInt()
                listOf((mv shr 8).toByte(), mv.toByte())
            }.toByteArray()
    }
}

/** The fake radio between the app and [SimulatedJbdBoard]. */
class SimulatedJbdLink(private val board: SimulatedJbdBoard) : GattLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val chunks: Flow<ByteArray> = _chunks.asSharedFlow()

    private val _dropped = MutableStateFlow(false)
    override val dropped: StateFlow<Boolean> = _dropped.asStateFlow()

    override suspend fun connect(): GattServices {
        delay(600)
        if (!board.canConnect()) throw LinkException("Simulated board is out of range")
        board.attach(this)
        return SERVICES
    }

    override suspend fun write(bytes: ByteArray) {
        if (_dropped.value) throw LinkException("Link dropped")
        // Like a real board, ignore anything that isn't one of the read requests.
        val request = JbdRequest.entries.firstOrNull { it.encode().contentEquals(bytes) } ?: return
        scope.launch {
            val frame = board.reply(request) ?: return@launch
            delay(30)
            frame.toList().chunked(20).forEach {
                delay(8)
                _chunks.emit(it.toByteArray())
            }
        }
    }

    override suspend fun readRssi(): Int? = if (_dropped.value) null else board.rssi()

    fun drop() {
        _dropped.value = true
        scope.cancel()
    }

    override fun close() = drop()

    private companion object {
        val SERVICES: GattServices = mapOf(
            uuid16(0x180A) to listOf(uuid16(0x2A29)),
            JbdBrand.PROFILE.service to listOf(JbdBrand.PROFILE.notify, JbdBrand.PROFILE.write),
        )
    }
}
