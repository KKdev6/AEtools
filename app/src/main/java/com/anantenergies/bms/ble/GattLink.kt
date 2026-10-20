package com.anantenergies.bms.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** The GATT service and characteristics a driver talks through. */
data class GattProfile(val service: UUID, val notify: UUID, val write: UUID)

/** Every service on a board with its characteristics, as found by service discovery. */
typealias GattServices = Map<UUID, List<UUID>>

/**
 * One Bluetooth LE connection, as a plain byte pipe: it knows nothing about any BMS brand.
 * Pure Kotlin so drivers can be tested with a fake.
 */
interface GattLink {
    /**
     * Connects, discovers services and turns on notifications for the profile.
     * Returns everything discovered. Throws [GattServiceMissing] when the profile isn't there,
     * or [LinkException] when the connection fails.
     */
    suspend fun connect(): GattServices

    /** Notification chunks from the profile's notify characteristic, as they arrive. */
    val chunks: Flow<ByteArray>

    /** Becomes true once the link has dropped or been closed. A link never reopens. */
    val dropped: StateFlow<Boolean>

    /** Writes to the profile's write characteristic. Throws [LinkException] if the link is gone. */
    suspend fun write(bytes: ByteArray)

    /** Signal strength in dBm, or null if it couldn't be read. */
    suspend fun readRssi(): Int?

    fun close()
}

fun interface GattLinkFactory {
    fun create(address: String, profile: GattProfile): GattLink
}

class LinkException(message: String) : Exception(message)

/** Connected, but the board doesn't have the profile's service or characteristics. */
class GattServiceMissing(val services: GattServices) : Exception("Service not found")

/** A 16-bit Bluetooth SIG UUID in its full form, e.g. 0xFF00 → 0000ff00-0000-1000-8000-00805f9b34fb. */
fun uuid16(short: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))

/** "ff00" for 16-bit base UUIDs, the full UUID otherwise. */
fun UUID.shortLabel(): String {
    val text = toString()
    return if (text.startsWith("0000") && text.endsWith("-0000-1000-8000-00805f9b34fb")) text.substring(4, 8) else text
}

/** One line per service, e.g. "ff00: ff01, ff02". */
fun GattServices.describe(): List<String> =
    map { (service, characteristics) -> "${service.shortLabel()}: ${characteristics.joinToString { it.shortLabel() }}" }
