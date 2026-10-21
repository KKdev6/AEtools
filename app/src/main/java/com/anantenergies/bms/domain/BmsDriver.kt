package com.anantenergies.bms.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * One BMS brand: recognises its boards and makes a driver for each connection.
 * Adding a brand means adding one of these to the registry; screens never change.
 */
interface BmsBrand {
    /** Stored with the remembered device, e.g. "jbd". */
    val id: String

    /** Shown to the user, e.g. "JBD". */
    val name: String

    /** The GATT service that identifies this brand, lower-case full UUID. */
    val serviceUuid: String

    /** True when [advertisement] looks like this brand's BMS. */
    fun matches(advertisement: Advertisement): Boolean

    fun create(): BmsDriver
}

/** One connection to one BMS. */
interface BmsDriver {
    val linkState: StateFlow<LinkState>

    /** Model details, known once connected. */
    val info: StateFlow<BmsInfo?>

    /**
     * Returns once connected, or throws [BmsConnectException]. After that the driver keeps the
     * link open, reconnecting with backoff when it drops, until [disconnect].
     */
    suspend fun connect(device: BmsDevice)

    /** Reads the BMS every [interval] for as long as the flow is collected. */
    fun snapshots(interval: Duration): Flow<BmsSnapshot>

    suspend fun disconnect()
}

sealed class BmsConnectException(message: String) : Exception(message) {
    /** Connected, but the board doesn't offer this brand's service. [services] lists what it does offer. */
    class NotSupported(val services: List<String>) : BmsConnectException("Not a supported BMS")

    /** Couldn't connect at all, e.g. out of range or asleep. */
    class Unreachable(reason: String) : BmsConnectException(reason)
}

/** What a Bluetooth scan saw, without Android types, so drivers can be tested on the JVM. */
data class Advertisement(
    val name: String?,
    val address: String,
    val rssi: Int,
    /** Advertised service UUIDs, lower case, full 128-bit form. */
    val serviceUuids: List<String>,
    /** The whole advertisement as hex, for the Bluetooth log; null when not captured. */
    val raw: String? = null,
)

/** A BMS the app can connect to. [brandId] is null until a brand has recognised it. */
data class BmsDevice(val name: String, val address: String, val brandId: String? = null)

/** Details that don't change while connected. */
data class BmsInfo(
    val brand: String,
    /** Board model, e.g. "DB24SA08", when the BMS reports it. */
    val model: String?,
    /** Cell chemistry, e.g. "LiFePO4", when known. */
    val chemistry: String?,
)

sealed interface LinkState {
    data object Disconnected : LinkState
    data object Connecting : LinkState
    data class Connected(val rssi: Int?) : LinkState
    data class Reconnecting(val attempt: Int) : LinkState
}
