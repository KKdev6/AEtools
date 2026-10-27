package com.anantenergies.bms.factory.dev

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.factory.flow.Board
import com.anantenergies.bms.factory.sim.SettingsFault
import com.anantenergies.bms.factory.sim.SimulatedSettingsBoard
import com.anantenergies.bms.factory.sim.SimulatedSettingsLink
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.CardTitle
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.theme.AeTheme
import kotlinx.coroutines.CoroutineScope

/** Debug builds only: the simulated SP17S005, so the whole apply flow runs without hardware. */
object FactoryDev {
    private const val ADDRESS = "SI:MU:LA:TE:D0:02"

    private val board = SimulatedSettingsBoard()

    val simulatedBoard: Board? = Board(name = "Simulated SP17S005", address = ADDRESS)

    fun linkFactory(real: GattLinkFactory, scope: CoroutineScope) = GattLinkFactory { address, profile ->
        if (address == ADDRESS) {
            // After a "link drops" fault the screen asks for a power-cycle; reconnecting stands in for it.
            if (board.dropLink) board.powerCycle()
            SimulatedSettingsLink(board, scope)
        } else {
            real.create(address, profile)
        }
    }

    fun powerCycle(address: String) {
        if (address == ADDRESS) board.powerCycle()
    }

    /** Fault picker for the simulated board, shown under the board list. */
    @Composable
    fun Panel() {
        var fault by remember { mutableStateOf(board.fault) }
        AeCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Simulated board (debug build)")
            Text("Fault: ${fault.label}", style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsFault.entries.forEach { option ->
                    OutlineButton(
                        text = option.label,
                        onClick = {
                            board.fault = option
                            fault = option
                        },
                        borderColor = if (option == fault) AeTheme.colors.brand else AeTheme.colors.borderStrong,
                        height = 36.dp,
                    )
                }
            }
        }
    }
}
