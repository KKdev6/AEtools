package com.anantenergies.bms.dev

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.drivers.demo.DemoController
import com.anantenergies.bms.drivers.demo.DemoScenario
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme

/** Developer tools for debug builds. The release source set has an empty stand-in. */
object DevTools {
    private val controller = DemoController()

    val demo: DemoControl? = controller

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
                "Demo mode runs a simulated 16S pack so you can try every screen without hardware.",
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

    /** Shown on every screen in demo mode. Long-press it to pick a fault scenario. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DemoBanner(onExitDemo: () -> Unit) {
        val colors = AeTheme.colors
        val scenario by controller.scenario.collectAsStateWithLifecycle()
        var picking by rememberSaveable { mutableStateOf(false) }

        Text(
            text = if (scenario == DemoScenario.NORMAL) "Demo mode · simulated data" else "Demo mode · ${scenario.label}",
            style = AeTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = colors.balancingText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.balancingBg)
                .combinedClickable(onLongClickLabel = "Choose demo scenario", onLongClick = { picking = true }, onClick = {})
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        if (picking) {
            ScenarioDialog(
                current = scenario,
                onPick = {
                    controller.select(it)
                    picking = false
                },
                onExit = {
                    picking = false
                    onExitDemo()
                },
                onDismiss = { picking = false },
            )
        }
    }

    @Composable
    private fun ScenarioDialog(
        current: DemoScenario,
        onPick: (DemoScenario) -> Unit,
        onExit: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        val colors = AeTheme.colors
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Demo scenario") },
            text = {
                Column(Modifier.selectableGroup()) {
                    DemoScenario.entries.forEach { scenario ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(scenario == current, role = Role.RadioButton, onClick = { onPick(scenario) })
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = scenario == current,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brand, unselectedColor = colors.textSecondary),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(scenario.label, style = AeTheme.type.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.brand) } },
            dismissButton = { TextButton(onClick = onExit) { Text("Exit demo", color = colors.discharge) } },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            shape = AeShapes.card,
        )
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
