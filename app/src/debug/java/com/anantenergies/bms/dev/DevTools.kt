package com.anantenergies.bms.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.drivers.demo.DemoController
import com.anantenergies.bms.drivers.demo.DemoScenario
import com.anantenergies.bms.drivers.sim.SimScenario
import com.anantenergies.bms.drivers.sim.SimulatedJbdBoard
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.ScreenHeader
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Developer tools for debug builds. The release source set has an empty stand-in with the same API. */
object DevTools {
    private val controller = DemoController()
    private val board = SimulatedJbdBoard()

    val demo: DemoControl? = controller

    /** Raw Bluetooth traffic, shown on the Bluetooth log screen. */
    val trafficLog: TrafficLog? = TrafficLog()

    /** Devices the scan list shows in addition to real ones. */
    val simulatedAdvertisements: List<Advertisement> = listOf(SimulatedJbdBoard.ADVERTISEMENT)

    const val hasTrafficScreen = true

    fun linkFactory(real: GattLinkFactory): GattLinkFactory = board.linkFactory(real)

    fun isSimulated(address: String) = address == SimulatedJbdBoard.ADDRESS

    /** The simulated board's address if a scanned QR code contains it, so the QR flow can be tested without hardware. */
    fun simulatedAddressIn(qrText: String): String? =
        SimulatedJbdBoard.ADDRESS.takeIf { qrText.contains(it, ignoreCase = true) }

    /** The "No BMS nearby?" card at the bottom of the Devices screen. */
    @Composable
    fun DemoEntryCard(onStartDemo: () -> Unit, modifier: Modifier = Modifier) {
        val colors = AeTheme.colors
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(AeShapes.card)
                .background(colors.surface)
                .dashedBorder(colors.borderStrong, 12.dp)
                .padding(16.dp),
        ) {
            Text("No BMS nearby?", style = AeTheme.type.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "Demo mode runs a simulated 16S pack so you can try every screen without hardware. " +
                    "The simulated JBD board in the list above tests the real JBD code instead.",
                style = AeTheme.type.small,
                color = colors.textSecondary,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(AeShapes.small)
                    .background(colors.textPrimary)
                    .clickable(role = Role.Button, onClick = onStartDemo),
            ) {
                Text(
                    "Start demo mode",
                    color = colors.background,
                    style = AeTheme.type.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }

    /** Shown on every screen while connected to the demo pack or the simulated board. Long-press it for scenarios. */
    @Composable
    fun DemoBanner(address: String, onExitDemo: () -> Unit) {
        if (isSimulated(address)) {
            val scenario by board.scenario.collectAsStateWithLifecycle()
            ScenarioBanner(
                name = "Simulated JBD board",
                labels = SimScenario.entries.map { it.label },
                selected = scenario.ordinal,
                onSelect = { board.select(SimScenario.entries[it]) },
                onExit = onExitDemo,
            )
        } else {
            val scenario by controller.scenario.collectAsStateWithLifecycle()
            ScenarioBanner(
                name = "Demo mode",
                labels = DemoScenario.entries.map { it.label },
                selected = scenario.ordinal,
                onSelect = { controller.select(DemoScenario.entries[it]) },
                onExit = onExitDemo,
            )
        }
    }

    /** Raw TX/RX hex, discovered services and errors, newest first. Opened with 5 taps on the app version. */
    @Composable
    fun TrafficScreen(onBack: () -> Unit) {
        val log = trafficLog ?: return
        val entries by log.entries.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val colors = AeTheme.colors
        val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Bluetooth log", onBack = onBack, subtitle = "${entries.size} entries · debug builds only")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ScenarioBanner(
        name: String,
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
        onExit: () -> Unit,
    ) {
        val colors = AeTheme.colors
        var picking by rememberSaveable { mutableStateOf(false) }

        Text(
            text = if (selected == 0) "$name · simulated data" else "$name · ${labels[selected]}",
            style = AeTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = colors.balancingText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.balancingBg)
                .combinedClickable(onLongClickLabel = "Choose scenario", onLongClick = { picking = true }, onClick = {})
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

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
                    TextButton(onClick = {
                        picking = false
                        onExit()
                    }) { Text("Disconnect", color = colors.discharge) }
                },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textPrimary,
                shape = AeShapes.card,
            )
        }
    }

    private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
        val stroke = 1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }
}
