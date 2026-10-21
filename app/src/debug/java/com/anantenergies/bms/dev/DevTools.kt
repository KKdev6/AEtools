package com.anantenergies.bms.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.AeApp
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.drivers.demo.DemoController
import com.anantenergies.bms.drivers.demo.DemoScenario
import com.anantenergies.bms.drivers.jbd.JbdBrand
import com.anantenergies.bms.drivers.sim.SimScenario
import com.anantenergies.bms.drivers.sim.SimulatedJbdBoard
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.ScreenHeader
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developer tools for debug builds. The release source set has an empty stand-in with the same API.
 *
 * Debug builds look like release builds (owner decision): nothing here shows on the normal
 * screens apart from "debug build" next to the app version. Everything for testing without
 * hardware sits on the tools screen, opened with 5 taps on the app version.
 */
object DevTools {
    private val controller = DemoController()
    private val board = SimulatedJbdBoard()

    val demo: DemoControl? = controller

    /** Raw Bluetooth traffic, shown on the tools screen. */
    val trafficLog: TrafficLog? = TrafficLog()

    /** Boards a QR code can name besides real ones. Never listed in the scan. */
    val simulatedAdvertisements: List<Advertisement> = listOf(SimulatedJbdBoard.ADVERTISEMENT)

    /** Shown next to the app version in Settings. */
    val buildLabel: String? = "debug build"

    const val hasToolsScreen = true

    fun linkFactory(real: GattLinkFactory): GattLinkFactory = board.linkFactory(real)

    fun isSimulated(address: String) = address == SimulatedJbdBoard.ADDRESS

    /** The simulated board's address if a scanned QR code contains it, so the QR flow can be tested without hardware. */
    fun simulatedAddressIn(qrText: String): String? =
        SimulatedJbdBoard.ADDRESS.takeIf { qrText.contains(it, ignoreCase = true) }

    /**
     * The simulated JBD board, demo mode, their fault scenarios and the raw Bluetooth log.
     * [onConnected] opens the Dashboard after starting the board or the demo pack.
     */
    @Composable
    fun ToolsScreen(onBack: () -> Unit, onConnected: () -> Unit) {
        val log = trafficLog ?: return
        val context = LocalContext.current
        val bms = remember { (context.applicationContext as AeApp).container.bms }
        val session by bms.session.collectAsStateWithLifecycle()
        val entries by log.entries.collectAsStateWithLifecycle()
        val colors = AeTheme.colors
        val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Developer tools", onBack = onBack, subtitle = "Debug builds only")
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SectionLabel("Without hardware")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton(
                        "Simulated board",
                        onClick = {
                            val ad = SimulatedJbdBoard.ADVERTISEMENT
                            bms.connect(BmsDevice(ad.name ?: "Simulated JBD board", ad.address, JbdBrand.ID))
                            onConnected()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton(
                        "Demo pack",
                        onClick = {
                            bms.startDemo()
                            onConnected()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Always shown, so a scenario like "Out of range" can be switched back while disconnected.
                SimulatorRow(connected = session?.device?.address?.let(::isSimulated) == true, onDisconnect = bms::disconnect)
                if (session?.device?.address == controller.device.address) DemoRow(onDisconnect = bms::disconnect)

                Spacer(Modifier.height(8.dp))
                SectionLabel("Bluetooth log · ${entries.size} entries")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton(
                        "Copy all",
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("AEtools Bluetooth log", log.export()))
                            Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlineButton("Clear", onClick = log::clear, modifier = Modifier.weight(1f))
                }
            }
            SelectionContainer(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    items(entries.asReversed()) { entry ->
                        val kindColor = when (entry.kind) {
                            TrafficLog.Kind.TX -> colors.brand
                            TrafficLog.Kind.RX -> colors.ok
                            TrafficLog.Kind.INFO -> colors.textSecondary
                            TrafficLog.Kind.ERROR -> colors.error
                        }
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(format.format(Date(entry.time)), style = AeTheme.type.numberSmall, color = colors.textSecondary)
                                Text(entry.kind.name, style = AeTheme.type.numberSmall.copy(fontWeight = FontWeight.SemiBold), color = kindColor)
                            }
                            Text(entry.text, style = AeTheme.type.numberSmall)
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.border)
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(text, style = AeTheme.type.sectionLabel, color = AeTheme.colors.textSecondary)
    }

    @Composable
    private fun SimulatorRow(connected: Boolean, onDisconnect: () -> Unit) {
        val scenario by board.scenario.collectAsStateWithLifecycle()
        ScenarioPicker(
            name = "Simulated JBD board",
            connected = connected,
            labels = SimScenario.entries.map { it.label },
            selected = scenario.ordinal,
            onSelect = { board.select(SimScenario.entries[it]) },
            onDisconnect = onDisconnect,
        )
    }

    @Composable
    private fun DemoRow(onDisconnect: () -> Unit) {
        val scenario by controller.scenario.collectAsStateWithLifecycle()
        ScenarioPicker(
            name = "Demo pack",
            connected = true,
            labels = DemoScenario.entries.map { it.label },
            selected = scenario.ordinal,
            onSelect = { controller.select(DemoScenario.entries[it]) },
            onDisconnect = onDisconnect,
        )
    }

    @Composable
    private fun ScenarioPicker(
        name: String,
        connected: Boolean,
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
        onDisconnect: () -> Unit,
    ) {
        val colors = AeTheme.colors
        var picking by rememberSaveable { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(if (connected) "$name · connected" else name, style = AeTheme.type.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text("Scenario: ${labels[selected]}", style = AeTheme.type.small, color = colors.textSecondary)
            }
            OutlineButton("Change", onClick = { picking = true }, height = 36.dp)
        }

        if (picking) {
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text("$name scenario") },
                text = {
                    Column(Modifier.selectableGroup()) {
                        labels.forEachIndexed { index, label ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = index == selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            onSelect(index)
                                            picking = false
                                        },
                                    )
                                    .padding(vertical = 8.dp),
                            ) {
                                RadioButton(
                                    selected = index == selected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.brand, unselectedColor = colors.textSecondary),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = AeTheme.type.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("Close", color = colors.brand) } },
                dismissButton = {
                    if (connected) {
                        TextButton(onClick = {
                            picking = false
                            onDisconnect()
                        }) { Text("Disconnect", color = colors.discharge) }
                    }
                },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textPrimary,
                shape = AeShapes.card,
            )
        }
    }
}
