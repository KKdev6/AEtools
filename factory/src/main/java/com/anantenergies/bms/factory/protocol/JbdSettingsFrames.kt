package com.anantenergies.bms.factory.protocol

import com.anantenergies.bms.drivers.jbd.JbdFrame
import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.JbdSetting

/**
 * The frames the factory app sends to a JBD board on the new (`FA`) protocol, as confirmed on
 * the real SP17S005 (docs/jbd-write-protocol.md). Writes can only be built for a setting the
 * board's profile lists as writable.
 */
object JbdSettingsFrames {
    /** Register byte of the new-protocol read and write replies. */
    const val FA = 0xFA

    /** Register bytes of the enter and exit replies. */
    const val ENTER_REPLY = 0x00
    const val EXIT_REPLY = 0x01

    private const val WRITE = 0x5A

    /** `DD A5 00 00 00 00 77`, exactly as the JBD app sends it (zero checksum). Reply byte 5 is the chip type. */
    val probeChipType: ByteArray = bytes(0xDD, 0xA5, 0x00, 0x00, 0x00, 0x00, 0x77)

    /** `DD 5A 00 02 56 78 FF 30 77`: settings mode. */
    val enterSettings: ByteArray = frame(WRITE, 0x00, 0x56, 0x78)

    /** `DD 5A 01 02 28 28 FF AD 77`: leave settings mode and store the new values. */
    val exitAndSave: ByteArray = frame(WRITE, 0x01, 0x28, 0x28)

    /** `DD 5A 01 02 00 00 FF FD 77`: leave settings mode and throw the new values away. */
    val exitWithoutSaving: ByteArray = frame(WRITE, 0x01, 0x00, 0x00)

    /** Reads [count] registers from [address], e.g. `DD A5 FA 03 00 10 08 FE EB 77`. Needs no settings mode. */
    fun read(address: Int, count: Int): ByteArray {
        require(address in 0..0xFFFF && count in 1..MAX_READ) { "Bad read: $address × $count" }
        return frame(JbdFrame.READ, FA, address shr 8, address, count)
    }

    /**
     * Writes one register, e.g. cell high delay to 3 s: `DD 5A FA 05 00 33 01 00 03 FE CA 77`.
     * Only in settings mode, and only for a setting [profile] allows.
     */
    fun write(profile: BmsModelProfile, setting: JbdSetting, raw: Int): ByteArray {
        require(setting in profile.writable) { "${profile.id} doesn't allow writing ${setting.name}" }
        require(raw in 0..0xFFFF) { "Bad value for ${setting.name}: $raw" }
        return frame(WRITE, FA, setting.address shr 8, setting.address, 1, raw shr 8, raw)
    }

    /**
     * Payload of a new-protocol read reply (`DD FA 00 <len> <addr hi> <addr lo> <count> <values…> …`):
     * the register values from [address], or null if it answers a different read.
     */
    fun readValues(data: ByteArray, address: Int, count: Int): List<Int>? {
        if (data.size != 3 + 2 * count) return null
        val replyAddress = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (replyAddress != address || (data[2].toInt() and 0xFF) != count) return null
        return (0 until count).map { i -> ((data[3 + 2 * i].toInt() and 0xFF) shl 8) or (data[4 + 2 * i].toInt() and 0xFF) }
    }

    /** The most registers one read asks for (the JBD app reads up to 8 at a time). */
    const val MAX_READ = 8

    /** `DD <command> <register> <length> <data…> <checksum> 77`, checksum from the register on. */
    private fun frame(command: Int, register: Int, vararg data: Int): ByteArray {
        val body = bytes(JbdFrame.START, command, register, data.size, *data)
        val checksum = JbdFrame.checksum(body, from = 2, until = body.size)
        return body + bytes(checksum shr 8, checksum, JbdFrame.END)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
