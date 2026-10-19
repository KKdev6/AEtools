@file:Suppress("UNUSED_PARAMETER")

package com.anantenergies.bms.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Release builds ship no developer tools: no demo driver, no simulated data, nothing to show.
 * Keep the signatures in step with the debug source set's DevTools.
 */
object DevTools {
    val demo: DemoControl? = null

    @Composable
    fun DemoEntryCard(onStartDemo: () -> Unit, modifier: Modifier = Modifier) {
    }

    @Composable
    fun DemoBanner(onExitDemo: () -> Unit) {
    }
}
