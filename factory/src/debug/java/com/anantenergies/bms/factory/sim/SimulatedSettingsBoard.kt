package com.anantenergies.bms.factory.sim

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.ble.GattServices
import com.anantenergies.bms.ble.LinkException
import com.anantenergies.bms.drivers.jbd.JbdBrand
import com.anantenergies.bms.drivers.jbd.JbdFrame
import com.anantenergies.bms.drivers.jbd.JbdRequest
import com.anantenergies.bms.factory.model.JbdSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Faults the simulated board can show while a preset is applied. */
enum class SettingsFault(val label: String) {
    NONE("Normal"),
    REFUSE_WRITE("Refuses one write"),
    SILENT_WRITE("Doesn't answer one write"),
    VALUE_DOES_NOT_STICK("One value doesn't stick"),
    FORTY_AMP_BOARD("40 A board"),
    LINK_DROP_MID_APPLY("Link drops mid-apply"),
}

/**
 * A pretend SP17S005 for debug builds and tests: answers the factory app's real frames with real
 * JBD replies, keeps a register store, and only stores writes on "exit and save".
 * It starts with the values read from the real test pack (docs/jbd-write-protocol.md), several of
 * which don't match the settings card, so a correct apply visibly changes them.
 */
class SimulatedSettingsBoard(var fault: SettingsFault = SettingsFault.NONE) {
    /** The setting the single-setting faults hit. */
    var faultySetting: JbdSetting = JbdSetting.BALANCE_VOLTAGE

    /** [SettingsFault.LINK_DROP_MID_APPLY] drops the link after this many writes. */
    var writesBeforeDrop = 10

    /** Live cell voltages, mV: a charged pack with an 8 mV spread. */
    var cells: List<Int> = List(15) { 4130 + it % 3 } + 4138

    /** Live temperatures, °C (board sensor and two probes at room temperature). */
    var temperatures: List<Double> = listOf(26.1, 25.6, 26.4)

        /** What register 0x05 answers; change it to try an unknown board. */
    var boardName = "SP17S005P17S50A"

    private val stored = INITIAL.toMutableMap()
    private val pending = mutableMapOf<Int, Int>()
    private var inSettingsMode = false
    private var writesSeen = 0

    /** Set when the board wants the link dropped (a fault). */
    @Volatile var dropLink = false
        private set

    /** What's stored at [address] (what survives a power-cycle). */
    @Synchronized
    fun stored(address: Int): Int? = stored[address]

    @get:Synchronized
    val settingsMode: Boolean get() = inSettingsMode

    /** Like unplugging the BMS: unsaved writes are lost. */
    @Synchronized
    fun powerCycle() {
        pending.clear()
        inSettingsMode = false
        dropLink = false
        writesSeen = 0
    }

    /** The whole reply to [frame], or null if the board stays silent (as a real one does to garbage). */
    @Synchronized
    fun respond(frame: ByteArray): ByteArray? {
        if (frame.contentEquals(PROBE)) return reply(0x00, 0x00, 0x01)
        if (frame.contentEquals(JbdRequest.HARDWARE.encode())) return reply(JbdRequest.HARDWARE.register, *boardName.toByteArray(Charsets.US_ASCII).map { it.toInt() }.toIntArray())
        if (frame.contentEquals(JbdRequest.BASIC_INFO.encode())) return reply(JbdRequest.BASIC_INFO.register, *basicInfo())
        if (frame.contentEquals(JbdRequest.CELL_VOLTAGES.encode())) {
            return reply(JbdRequest.CELL_VOLTAGES.register, *cells.flatMap { mv -> listOf(mv shr 8, mv and 0xFF) }.toIntArray())
        }
        if (frame.size < 7 || u(frame[0]) != JbdFrame.START || u(frame.last()) != JbdFrame.END) return null
        val length = u(frame[3])
        if (frame.size != 7 + length) return null
        val checksum = JbdFrame.checksum(frame, from = 2, until = 4 + length)
        if (checksum != (u(frame[4 + length]) shl 8 or u(frame[5 + length]))) return null
        val data = frame.copyOfRange(4, 4 + length).map { u(it) }

        return when (u(frame[1]) to u(frame[2])) {
            READ to FA -> {
                val address = data[0] shl 8 or data[1]
                val count = data[2]
                val values = (address until address + count).flatMap {
                    val value = pending[it] ?: stored[it] ?: 0
                    listOf(value shr 8, value and 0xFF)
                }
                reply(FA, data[0], data[1], count, *values.toIntArray())
            }
            WRITE to 0x00 -> if (data == listOf(0x56, 0x78)) {
                inSettingsMode = true
                reply(0x00)
            } else null
            WRITE to 0x01 -> {
                if (data == listOf(0x28, 0x28)) save() else pending.clear()
                inSettingsMode = false
                reply(0x01)
            }
            WRITE to FA -> write(data)
            else -> null
        }
    }

    /** The 0x03 payload laid out as in CLAUDE.md (idle, both MOS on, no protections). */
    private fun basicInfo(): IntArray {
        val out = mutableListOf<Int>()
        fun u16(value: Int) = out.addAll(listOf(value shr 8 and 0xFF, value and 0xFF))
        u16(cells.sum() / 10) // pack voltage, 10 mV
        u16(0) // current
        u16(2850) // remaining, 10 mAh
        u16(stored[JbdSetting.DESIGN_CAPACITY.address] ?: 0)
        u16(0) // cycles
        u16(0) // production date
        u16(0)
        u16(0) // balancing
        u16(0) // protections
        out += listOf(0x10, 95, 0x03, cells.size, temperatures.size)
        temperatures.forEach { u16(2731 + (it * 10).toInt()) }
        return out.toIntArray()
    }

        private fun write(data: List<Int>): ByteArray? {
        if (!inSettingsMode) return errorReply(FA)
        val address = data[0] shl 8 or data[1]
        val value = data[3] shl 8 or data[4]
        writesSeen++
        if (fault == SettingsFault.LINK_DROP_MID_APPLY && writesSeen > writesBeforeDrop) {
            dropLink = true
            return null
        }
        val isFaulty = address == faultySetting.address
        if (isFaulty && fault == SettingsFault.REFUSE_WRITE) return errorReply(FA)
        if (isFaulty && fault == SettingsFault.SILENT_WRITE) return null
        pending[address] = value
        return reply(FA)
    }

    private fun save() {
        for ((address, value) in pending) {
            when {
                fault == SettingsFault.VALUE_DOES_NOT_STICK && address == faultySetting.address -> Unit
                fault == SettingsFault.FORTY_AMP_BOARD && address == JbdSetting.DISCHARGE_OVER_CURRENT.address ->
                    stored[address] = JbdSetting.DISCHARGE_OVER_CURRENT.encoding.toRaw(40.0)
                else -> stored[address] = value
            }
        }
        pending.clear()
    }

    companion object {
        private const val READ = 0xA5
        private const val WRITE = 0x5A
        private const val FA = 0xFA
        private val PROBE = byteArrayOf(0xDD.toByte(), 0xA5.toByte(), 0, 0, 0, 0, 0x77)

        /** Read from the real test pack "Test-60V32Ah-bbsr" where the capture shows them; typical JBD values otherwise. */
        private val INITIAL: Map<Int, Int> = buildMap {
            fun set(setting: JbdSetting, value: Double) = put(setting.address, setting.encoding.toRaw(value))
            // From the capture.
            set(JbdSetting.DESIGN_CAPACITY, 30.0)
            set(JbdSetting.PACK_HIGH, 68.0)
            set(JbdSetting.PACK_HIGH_RECOVER, 66.4)
            set(JbdSetting.PACK_LOW, 44.8)
            set(JbdSetting.PACK_LOW_RECOVER, 46.4)
            set(JbdSetting.CELL_HIGH, 4.25)
            set(JbdSetting.CELL_HIGH_RECOVER, 4.15)
            set(JbdSetting.CELL_LOW, 2.80)
            set(JbdSetting.CELL_LOW_RECOVER, 2.90)
            set(JbdSetting.BALANCE_VOLTAGE, 3.90)
            set(JbdSetting.BALANCE_ACCURACY, 0.015)
            set(JbdSetting.FUNCTIONS, 0x0006.toDouble()) // static balance
            set(JbdSetting.NTC_ENABLE, 0x0007.toDouble())
            set(JbdSetting.CELL_COUNT, 16.0)
            set(JbdSetting.HARDWARE_CELL_HIGH, 4.30)
            set(JbdSetting.HARDWARE_CELL_LOW, 2.50)
            listOf(JbdSetting.PACK_LOW_DELAY, JbdSetting.PACK_HIGH_DELAY, JbdSetting.CELL_LOW_DELAY, JbdSetting.CELL_HIGH_DELAY)
                .forEach { set(it, 2.0) }
            // Typical values, not captured.
            set(JbdSetting.CYCLE_CAPACITY, 30.0)
            set(JbdSetting.FULL_VOLTAGE, 4.15)
            set(JbdSetting.EMPTY_VOLTAGE, 3.00)
            set(JbdSetting.CHARGE_HIGH_TEMP, 55.0)
            set(JbdSetting.CHARGE_HIGH_TEMP_RECOVER, 50.0)
            set(JbdSetting.CHARGE_LOW_TEMP, 0.0)
            set(JbdSetting.CHARGE_LOW_TEMP_RECOVER, 5.0)
            set(JbdSetting.DISCHARGE_HIGH_TEMP, 70.0)
            set(JbdSetting.DISCHARGE_HIGH_TEMP_RECOVER, 60.0)
            set(JbdSetting.DISCHARGE_LOW_TEMP, -20.0)
            set(JbdSetting.DISCHARGE_LOW_TEMP_RECOVER, -10.0)
            set(JbdSetting.MOS_HIGH_TEMP, 100.0)
            set(JbdSetting.CHARGE_OVER_CURRENT, 30.0)
            set(JbdSetting.DISCHARGE_OVER_CURRENT, 60.0)
            set(JbdSetting.LEVEL2_OVER_CURRENT, 0x0F03.toDouble())
            set(JbdSetting.SHORT_CIRCUIT, 0x0A02.toDouble())
            listOf(
                JbdSetting.CHARGE_LOW_TEMP_DELAY, JbdSetting.CHARGE_HIGH_TEMP_DELAY,
                JbdSetting.DISCHARGE_LOW_TEMP_DELAY, JbdSetting.DISCHARGE_HIGH_TEMP_DELAY,
            ).forEach { set(it, 2.0) }
            set(JbdSetting.CHARGE_OVER_CURRENT_DELAY, 2.0)
            set(JbdSetting.CHARGE_OVER_CURRENT_RELEASE, 32.0)
            set(JbdSetting.DISCHARGE_OVER_CURRENT_DELAY, 2.0)
            set(JbdSetting.DISCHARGE_OVER_CURRENT_RELEASE, 32.0)
            listOf(
                JbdSetting.SOC_100 to 4.15, JbdSetting.SOC_90 to 4.05, JbdSetting.SOC_80 to 3.95, JbdSetting.SOC_70 to 3.85,
                JbdSetting.SOC_60 to 3.78, JbdSetting.SOC_50 to 3.70, JbdSetting.SOC_40 to 3.62, JbdSetting.SOC_30 to 3.55,
                JbdSetting.SOC_20 to 3.45, JbdSetting.SOC_10 to 3.35,
            ).forEach { (setting, volts) -> set(setting, volts) }
        }

        private fun u(byte: Byte) = byte.toInt() and 0xFF

        private fun reply(register: Int, vararg data: Int) = frame(register, JbdFrame.STATUS_OK, data)

        private fun errorReply(register: Int) = frame(register, 0x01, IntArray(0))

        private fun frame(register: Int, status: Int, data: IntArray): ByteArray {
            val body = byteArrayOf(JbdFrame.START.toByte(), register.toByte(), status.toByte(), data.size.toByte()) +
                ByteArray(data.size) { data[it].toByte() }
            val checksum = JbdFrame.checksum(body, from = 2, until = body.size)
            return body + byteArrayOf((checksum shr 8).toByte(), checksum.toByte(), JbdFrame.END.toByte())
        }
    }
}

/** The fake radio to a [SimulatedSettingsBoard]: replies arrive in 20-byte notifications. */
class SimulatedSettingsLink(private val board: SimulatedSettingsBoard, private val scope: CoroutineScope) : GattLink {
    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val chunks: Flow<ByteArray> = _chunks.asSharedFlow()

    private val _dropped = MutableStateFlow(false)
    override val dropped: StateFlow<Boolean> = _dropped.asStateFlow()

    override suspend fun connect(): GattServices = mapOf(JbdBrand.PROFILE.service to listOf(JbdBrand.PROFILE.notify, JbdBrand.PROFILE.write))

    override suspend fun write(bytes: ByteArray) {
        if (_dropped.value) throw LinkException("Link dropped")
        val reply = board.respond(bytes)
        if (board.dropLink) {
            _dropped.value = true
            return
        }
        reply ?: return
        scope.launch {
            delay(30)
            reply.toList().chunked(20).forEach {
                delay(8)
                _chunks.emit(it.toByteArray())
            }
        }
    }

    override suspend fun readRssi(): Int? = if (_dropped.value) null else -60

    override fun close() {
        _dropped.value = true
    }
}
