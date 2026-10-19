package com.anantenergies.bms.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * One BMS brand. Adding a brand means adding a driver and registering it;
 * screens never change.
 */
interface BmsDriver {
    /** True when [advertisement] looks like this brand's BMS. */
    fun matches(advertisement: Advertisement): Boolean

    val linkState: StateFlow<LinkState>

    /** Model details, known once connected. */
    val info: StateFlow<BmsInfo?>

    suspend fun connect(device: BmsDevice)

    /** Reads the BMS every [interval] for as long as the flow is collected. */
    fun snapshots(interval: Duration): Flow<BmsSnapshot>

    suspend fun disconnect()
}

/** What a Bluetooth scan saw, without Android types, so drivers can be tested on the JVM. */
data class Advertisement(
    val name: String?,
    val address: String,
    val rssi: Int,
    /** Advertised service UUIDs, lower case, full 128-bit form. */
    val serviceUuids: List<String>,
)

/** A BMS the app can connect to. */
data class BmsDevice(val name: String, val address: String)

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
