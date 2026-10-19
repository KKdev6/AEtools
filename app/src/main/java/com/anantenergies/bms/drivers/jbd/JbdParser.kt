package com.anantenergies.bms.drivers.jbd

import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.Protection
import com.anantenergies.bms.domain.TemperatureReading

/** A reply whose payload doesn't have the expected shape. */
class JbdFormatException(message: String) : Exception(message)

/** The basic-info reply (register 0x03), decoded. */
data class JbdBasicInfo(
    /** V. */
    val packVoltage: Double,
    /** A; positive while charging, negative while discharging. */
    val current: Double,
    /** Ah. Parsed for completeness but never displayed (see Product decisions). */
    val remainingCapacityAh: Double,
    /** Ah. Parsed for completeness but never displayed (see Product decisions). */
    val nominalCapacityAh: Double,
    val cycleCount: Int,
    /** Packed date bits, not decoded yet. */
    val productionDate: Int,
    /** Zero-based indices of balancing cells, from both balance words (cells 1–32). */
    val balancingCells: Set<Int>,
    /** The raw protection word; see [protections]. */
    val protectionBits: Int,
    val softwareVersion: Int,
    /** %. */
    val soc: Int,
    val chargeMosOn: Boolean,
    val dischargeMosOn: Boolean,
    val cellCount: Int,
    /** °C, in the order the BMS reports them. */
    val temperatures: List<Double>,
) {
    val protections: List<Protection> get() = JbdParser.protections(protectionBits)
}

/** Decodes JBD payloads. Pure: bytes in, data out, no Android types. */
object JbdParser {
    /** Bytes before the temperatures in the basic-info reply. */
    private const val BASIC_FIXED_SIZE = 23

    /** Temperatures are in 0.1 K; °C = (raw − 2731) / 10. */
    private const val KELVIN_OFFSET = 2731

    /** Bit n of the protection word is entry n. Bits 13–15 are not defined. */
    private val PROTECTION_BITS = listOf(
        Protection.CELL_OVERVOLTAGE,
        Protection.CELL_UNDERVOLTAGE,
        Protection.PACK_OVERVOLTAGE,
        Protection.PACK_UNDERVOLTAGE,
        Protection.CHARGE_OVER_TEMPERATURE,
        Protection.CHARGE_UNDER_TEMPERATURE,
        Protection.DISCHARGE_OVER_TEMPERATURE,
        Protection.DISCHARGE_UNDER_TEMPERATURE,
        Protection.CHARGE_OVERCURRENT,
        Protection.DISCHARGE_OVERCURRENT,
        Protection.SHORT_CIRCUIT,
        Protection.FRONT_END_IC_ERROR,
        Protection.MOS_SOFTWARE_LOCK,
    )

    /** Register 0x03. Newer firmware appends fields after the temperatures; those are ignored. */
    fun basicInfo(data: ByteArray): JbdBasicInfo {
        if (data.size < BASIC_FIXED_SIZE) {
            throw JbdFormatException("Basic info has ${data.size} bytes; at least $BASIC_FIXED_SIZE expected")
        }
        val ntcCount = u8(data, 22)
        if (data.size < BASIC_FIXED_SIZE + 2 * ntcCount) {
            throw JbdFormatException("Basic info has ${data.size} bytes; too short for $ntcCount temperatures")
        }
        val balance = (u16(data, 14) shl 16) or u16(data, 12)
        val mos = u8(data, 20)
        return JbdBasicInfo(
            packVoltage = u16(data, 0) / 100.0,
            current = s16(data, 2) / 100.0,
            remainingCapacityAh = u16(data, 4) / 100.0,
            nominalCapacityAh = u16(data, 6) / 100.0,
            cycleCount = u16(data, 8),
            productionDate = u16(data, 10),
            balancingCells = (0 until 32).filter { ((balance ushr it) and 1) == 1 }.toSet(),
            protectionBits = u16(data, 16),
            softwareVersion = u8(data, 18),
            soc = u8(data, 19),
            chargeMosOn = (mos and 0x01) != 0,
            dischargeMosOn = (mos and 0x02) != 0,
            cellCount = u8(data, 21),
            temperatures = List(ntcCount) { (u16(data, BASIC_FIXED_SIZE + 2 * it) - KELVIN_OFFSET) / 10.0 },
        )
    }

    /** Register 0x04: two bytes per cell, in mV. Returns volts. */
    fun cellVoltages(data: ByteArray): List<Double> {
        if (data.size % 2 != 0) throw JbdFormatException("Cell voltages have an odd length (${data.size} bytes)")
        return List(data.size / 2) { u16(data, 2 * it) / 1000.0 }
    }

    /** Register 0x05: the board's hardware name as printable ASCII. */
    fun hardwareName(data: ByteArray): String =
        String(data, Charsets.US_ASCII).filter { it in ' '..'~' }.trim()

    fun protections(bits: Int): List<Protection> =
        PROTECTION_BITS.filterIndexed { bit, _ -> ((bits ushr bit) and 1) == 1 }

    /**
     * Combines one basic-info and one cell-voltage reply into a snapshot.
     * Temperatures are labelled T1, T2, T3… until we know which sensor is which on real hardware.
     */
    fun snapshot(basic: JbdBasicInfo, cells: List<Double>, timestamp: Long) = BmsSnapshot(
        packVoltage = basic.packVoltage,
        current = basic.current,
        soc = basic.soc,
        cycleCount = basic.cycleCount,
        cellVoltages = cells,
        balancingCells = basic.balancingCells.filter { it < cells.size }.toSet(),
        temperatures = basic.temperatures.mapIndexed { i, celsius -> TemperatureReading("T${i + 1}", celsius) },
        chargeMosOn = basic.chargeMosOn,
        dischargeMosOn = basic.dischargeMosOn,
        // Not confirmed to be in the 0x03 reply, so unknown.
        heaterOn = null,
        protections = basic.protections,
        cellCount = basic.cellCount,
        timestamp = timestamp,
    )

    private fun u8(data: ByteArray, offset: Int) = data[offset].toInt() and 0xFF

    private fun u16(data: ByteArray, offset: Int) = (u8(data, offset) shl 8) or u8(data, offset + 1)

    private fun s16(data: ByteArray, offset: Int) = u16(data, offset).toShort().toInt()
}
