package com.anantenergies.bms.ui.cells

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.CellStats
import com.anantenergies.bms.domain.ChartWindow
import com.anantenergies.bms.ui.Format
import com.anantenergies.bms.ui.LiveViewModel
import com.anantenergies.bms.ui.appViewModel
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.EmptyState
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.ScreenHeader
import com.anantenergies.bms.ui.components.cellColor
import com.anantenergies.bms.ui.linkLabel
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme
import kotlin.math.max

@Composable
fun CellsScreen(onBack: () -> Unit, onFindBms: () -> Unit) {
    val viewModel = appViewModel { LiveViewModel(it.bms, it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val stats = snapshot?.let { CellStats.of(it.cellVoltages) }

    Column(Modifier.fillMaxSize()) {
        val subtitle = snapshot?.let { listOfNotNull("${it.cellCount} in series", state.info?.chemistry).joinToString(" · ") }
        ScreenHeader(title = "Cells", onBack = onBack, subtitle = subtitle)
        when {
            !state.hasSession -> EmptyState(
                title = "No BMS connected",
                message = "Connect to your battery from the Devices tab to see its cells.",
                modifier = Modifier.fillMaxSize(),
                action = { OutlineButton("Find a BMS", onClick = onFindBms) },
            )
            snapshot == null || stats == null -> EmptyState(
                title = linkLabel(state.link).first,
                message = "Waiting for cell voltages from the BMS.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                Tiles(stats)
                CellChart(snapshot.cellVoltages, stats)
                CellList(snapshot, stats)
            }
        }
    }
}

@Composable
private fun Tiles(stats: CellStats) {
    val colors = AeTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile("Highest", Format.cellVoltage(stats.highest), cellColor(stats.highest, colors.discharge), Modifier.weight(1f))
        Tile("Lowest", Format.cellVoltage(stats.lowest), cellColor(stats.lowest, colors.low), Modifier.weight(1f))
        Tile("Average", Format.cellVoltage(stats.average), colors.textPrimary, Modifier.weight(1f))
        Tile("Spread", "${Format.millivolts(stats.spread)} mV", colors.textPrimary, Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    val colors = AeTheme.colors
    Column(
        modifier
            .clip(AeShapes.small)
            .background(colors.surface)
            .border(1.dp, colors.border, AeShapes.small)
            .padding(10.dp),
    ) {
        Text(label, style = AeTheme.type.label, color = colors.textSecondary, maxLines = 1)
        Text(value, style = AeTheme.type.number.copy(fontSize = 15.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun CellChart(cells: List<Double>, stats: CellStats) {
    val colors = AeTheme.colors
    val window = ChartWindow.around(stats)
    val description = "Bar chart of ${cells.size} cell voltages, highest cell ${stats.highestIndex + 1} at " +
        "${Format.cellVoltage(stats.highest)} volts, lowest cell ${stats.lowestIndex + 1} at ${Format.cellVoltage(stats.lowest)} volts"

    AeCard(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AxisLabels("${Format.cellVoltage(window.high)} V", "window around average")
        Column {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 4.dp)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                val gap = (if (cells.size > 16) 2.dp else 4.dp).toPx()
                val barWidth = (size.width - gap * (cells.size - 1)) / cells.size
                val minBar = 4.dp.toPx()
                cells.forEachIndexed { i, volts ->
                    val height = max(minBar, window.fraction(volts) * size.height)
                    val color = when (volts) {
                        stats.highest -> colors.barHigh
                        stats.lowest -> colors.barLow
                        else -> colors.barOther
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * (barWidth + gap), size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }
        AxisLabels("${Format.cellVoltage(window.low)} V", "cell 1 → ${cells.size}")
    }
}

@Composable
private fun AxisLabels(start: String, end: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(start, style = AeTheme.type.label, color = AeTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(end, style = AeTheme.type.label, color = AeTheme.colors.textSecondary)
    }
}

@Composable
private fun CellList(snapshot: BmsSnapshot, stats: CellStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        snapshot.cellVoltages.withIndex().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (index, volts) ->
                    CellRow(index, volts, balancing = index in snapshot.balancingCells, stats, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CellRow(index: Int, volts: Double, balancing: Boolean, stats: CellStats, modifier: Modifier) {
    val colors = AeTheme.colors
    val normal = when (volts) {
        stats.highest -> colors.discharge
        stats.lowest -> colors.low
        else -> colors.textPrimary
    }
    val voltage = Format.cellVoltage(volts)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(AeShapes.small)
            .background(colors.surface)
            .border(1.dp, colors.border, AeShapes.small)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clearAndSetSemantics {
                contentDescription = "Cell ${index + 1}, $voltage volts" + if (balancing) ", balancing" else ""
            },
    ) {
        Text("#${index + 1}", style = AeTheme.type.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
        if (balancing) {
            Text(
                "BAL",
                style = AeTheme.type.tag,
                color = colors.balancingText,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.balancingBg)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(voltage, style = AeTheme.type.number, color = cellColor(volts, normal))
    }
}
