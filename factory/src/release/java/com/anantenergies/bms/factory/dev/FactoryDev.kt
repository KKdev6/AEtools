@file:Suppress("UNUSED_PARAMETER")

package com.anantenergies.bms.factory.dev

import androidx.compose.runtime.Composable
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.factory.flow.Board
import kotlinx.coroutines.CoroutineScope

/** Release builds have no simulated board. Keep the signatures in step with the debug source set. */
object FactoryDev {
    val simulatedBoard: Board? = null

    fun linkFactory(real: GattLinkFactory, scope: CoroutineScope): GattLinkFactory = real

    fun powerCycle(address: String) = Unit

    @Composable
    fun Panel() = Unit
}
