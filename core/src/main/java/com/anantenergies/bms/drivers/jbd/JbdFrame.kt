package com.anantenergies.bms.drivers.jbd

/**
 * The JBD (Xiaoxiang) frame format.
 *
 * Request:  `DD A5 <register> 00 <checksum hi> <checksum lo> 77`
 * Response: `DD <register> <status> <length> <data…> <checksum hi> <checksum lo> 77`
 *
 * The checksum is 0x10000 minus the sum of the bytes from the register (request) or the status
 * (response) up to the last data byte, sent as 16-bit big-endian.
 */
object JbdFrame {
    const val START = 0xDD
    const val END = 0x77
    const val READ = 0xA5
    const val STATUS_OK = 0x00

    /** Start, register, status and length, before the data. */
    const val HEADER_SIZE = 4

    /** Two checksum bytes and the end byte, after the data. */
    const val TRAILER_SIZE = 3

    /** The checksum over `bytes[from until until]`. */
    fun checksum(bytes: ByteArray, from: Int, until: Int): Int {
        var sum = 0
        for (i in from until until) sum += bytes[i].toInt() and 0xFF
        return (0x10000 - sum) and 0xFFFF
    }
}

/**
 * The only requests the app can send to a JBD BMS. All three are reads. There is deliberately no
 * way to build any other frame, so the app can never change BMS settings or MOSFET state.
 */
enum class JbdRequest(val register: Int) {
    BASIC_INFO(0x03),
    CELL_VOLTAGES(0x04),
    HARDWARE(0x05);

    /** The bytes to write, e.g. `DD A5 03 00 FF FD 77`. */
    fun encode(): ByteArray {
        val frame = byteArrayOf(
            JbdFrame.START.toByte(), JbdFrame.READ.toByte(), register.toByte(), 0x00, 0x00, 0x00, JbdFrame.END.toByte(),
        )
        // Covers the register and the zero length byte.
        val checksum = JbdFrame.checksum(frame, from = 2, until = 4)
        frame[4] = (checksum shr 8).toByte()
        frame[5] = checksum.toByte()
        return frame
    }
}

/** A complete reply whose checksum and status checked out. [data] is the payload only. */
class JbdResponse(val register: Int, val data: ByteArray)
