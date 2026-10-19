package com.anantenergies.bms.drivers.jbd

/** The synthetic test frames from CLAUDE.md, plus helpers. */
object JbdTestVectors {
    /** 16S, 52.82 V, −12.40 A, SOC 78 %, 42 cycles, cells 3/7/14 balancing, both MOS on, 31.0 / 27.4 / 28.1 °C. */
    val BASIC_INFO = hex(
        "DD 03 00 1D 14 A2 FB 28 1E 78 27 10 00 2A 00 00 20 44 00 00 00 00 10 4E 03 10 03 0B E1 0B BD 0B C4 F9 B8 77",
    )

    /** 3301 3298 3305 3300 3296 3303 3311 3299 3302 3297 3300 3292 3304 3309 3301 3298 mV. */
    val CELL_VOLTAGES = hex(
        "DD 04 00 20 0C E5 0C E2 0C E9 0C E4 0C E0 0C E7 0C EF 0C E3 0C E6 0C E1 0C E4 0C DC 0C E8 0C ED 0C E5 0C E2 F0 D0 77",
    )

    /** Cell overvoltage protection, charge MOS off, discharge MOS on, SOC 100 %, 56.00 V, 0 A. */
    val BASIC_INFO_PROTECTION = hex(
        "DD 03 00 1D 15 E0 00 00 27 10 27 10 00 2A 00 00 00 00 00 00 00 01 10 64 02 10 03 0B E1 0B BD 0B C4 FB 49 77",
    )

    val SPEC_CELLS_MV = listOf(3301, 3298, 3305, 3300, 3296, 3303, 3311, 3299, 3302, 3297, 3300, 3292, 3304, 3309, 3301, 3298)

    fun hex(text: String): ByteArray =
        text.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** The payload of a whole response frame. */
    fun ByteArray.payload(): ByteArray = copyOfRange(JbdFrame.HEADER_SIZE, size - JbdFrame.TRAILER_SIZE)

    /** Splits into BLE-sized notifications. */
    fun ByteArray.chunks(size: Int = 20): List<ByteArray> = toList().chunked(size).map { it.toByteArray() }

    /** Builds a well-formed response frame, for cases the spec vectors don't cover. */
    fun response(register: Int, data: ByteArray, status: Int = 0): ByteArray {
        val body = byteArrayOf(JbdFrame.START.toByte(), register.toByte(), status.toByte(), data.size.toByte()) + data
        val checksum = JbdFrame.checksum(body, from = 2, until = body.size)
        return body + byteArrayOf((checksum shr 8).toByte(), checksum.toByte(), JbdFrame.END.toByte())
    }
}
