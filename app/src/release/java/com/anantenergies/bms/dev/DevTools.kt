@file:Suppress("UNUSED_PARAMETER")

package com.anantenergies.bms.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    const val hasTrafficScreen = false

    fun linkFactory(real: GattLinkFactory): GattLinkFactory = real

    fun isSimulated(address: String) = false

    @Composable
    fun DemoEntryCard(onStartDemo: () -> Unit, modifier: Modifier = Modifier) {
    }

    @Composable
    fun DemoBanner(address: String, onExitDemo: () -> Unit) {
    }

    @Composable
    fun TrafficScreen(onBack: () -> Unit) {
    }
}
