@file:Suppress("UNUSED_PARAMETER")

package com.anantenergies.bms.dev

import androidx.compose.runtime.Composable
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.domain.Advertisement

/**
 * Release builds ship no developer tools: no demo driver, no simulated board, no traffic log,
 * nothing to show. Keep the signatures in step with the debug source set's DevTools.
 */
object DevTools {
    val demo: DemoControl? = null

    val trafficLog: TrafficLog? = null

    val simulatedAdvertisements: List<Advertisement> = emptyList()

    val buildLabel: String? = null

    const val hasToolsScreen = false

    fun linkFactory(real: GattLinkFactory): GattLinkFactory = real

    fun isSimulated(address: String) = false

    fun simulatedAddressIn(qrText: String): String? = null

    @Composable
    fun ToolsScreen(onBack: () -> Unit, onConnected: () -> Unit) {
    }
}
