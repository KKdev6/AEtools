package com.anantenergies.bms.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.R
import com.anantenergies.bms.data.TempUnit
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.CellStats
import com.anantenergies.bms.domain.CurrentFlow
import com.anantenergies.bms.ui.Format
import com.anantenergies.bms.ui.LiveState
import com.anantenergies.bms.ui.LiveViewModel
import com.anantenergies.bms.ui.appViewModel
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.AeMark
import com.anantenergies.bms.ui.components.CardTitle
import com.anantenergies.bms.ui.components.ChipTone
import com.anantenergies.bms.ui.components.EmptyState
import com.anantenergies.bms.ui.components.IconPill
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.StatusChip
import com.anantenergies.bms.ui.components.StatusDot
import com.anantenergies.bms.ui.components.cellColor
import com.anantenergies.bms.ui.linkLabel
import com.anantenergies.bms.ui.linkStatusLine
import com.anantenergies.bms.ui.rememberNow
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme
import com.anantenergies.bms.ui.theme.PlexMono

@Composable
fun DashboardScreen(onOpenCells: () -> Unit, onOpenSettings: () -> Unit, onFindBms: () -> Unit) {
    val viewModel = appViewModel { LiveViewModel(it.bms, it.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    Column(Modifier.fillMaxSize()) {
        Header(state, onOpenSettings)
        when {
            !state.hasSession -> EmptyState(
                title = "No BMS connected",
                message = "Connect to your battery from the Devices tab.",
                modifier = Modifier.fillMaxSize(),
                action = { OutlineButton("Find a BMS", onClick = onFindBms) },
            )
            snapshot == null -> EmptyState(
                title = linkLabel(state.link).first,
                message = "Waiting for the first reading from the BMS.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Body(snapshot, state.tempUnit, onOpenCells)
        }
    }
}

@Composable
private fun Header(state: LiveState, onOpenSettings: () -> Unit) {
    val colors = AeTheme.colors
    val snapshot = state.snapshot
    val title = if (state.hasSession) {
        listOfNotNull(
            state.info?.model ?: state.deviceName,
            snapshot?.let { "${it.cellCount}S" },
        ).joinToString(" · ")
    } else {
        "Dashboard"
    }
    val (label, dot) = linkLabel(state.link)
    val status = linkStatusLine(state.link, label, snapshot?.timestamp, rememberNow())

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    ) {
        AeMark(height = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AeTheme.type.headerTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(dot)
                Text(status, style = AeTheme.type.small, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(AeShapes.small)
                .background(colors.surface)
                .border(1.dp, colors.border, AeShapes.small)
                .clickable(role = Role.Button, onClick = onOpenSettings),
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Body(snapshot: BmsSnapshot, unit: TempUnit, onOpenCells: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = 8.dp
        val bottom = 16.dp
        val fillHeight = maxHeight - top - bottom
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
        ) {
            FirstThenEqualColumn(minHeight = fillHeight, spacing = 12.dp) {
                BatteryCard(snapshot)
                CellsCard(snapshot, onOpenCells)
                TemperaturesCard(snapshot, unit)
                StatusCard(snapshot)
            }
        }
    }
}

/**
 * The first child at its natural height; the rest share whatever is left of [minHeight] equally.
 * A child whose content needs more than its share (short screens, large text, several protections)
 * grows instead of clipping, and the parent scrolls.
 */
@Composable
private fun FirstThenEqualColumn(minHeight: Dp, spacing: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val first = measurables.first().measure(Constraints(minWidth = width, maxWidth = width))
        val rest = measurables.drop(1)
        val gaps = gap * rest.size
        val share = if (rest.isEmpty()) 0 else (minHeight.roundToPx() - first.height - gaps) / rest.size
        val placeables = rest.map {
            it.measure(Constraints(minWidth = width, maxWidth = width, minHeight = share.coerceAtLeast(0)))
        }
        layout(width, first.height + gaps + placeables.sumOf { it.height }) {
            first.place(0, 0)
            var y = first.height + gap
            placeables.forEach {
                it.place(0, y)
                y += it.height + gap
            }
        }
    }
}

@Composable
private fun BatteryCard(snapshot: BmsSnapshot) {
    val colors = AeTheme.colors
    AeCard(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Battery", Modifier.weight(1f))
            FlowPill(snapshot.flow)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SocRing(snapshot.soc)
            Column(Modifier.weight(1f)) {
                Metric("Voltage", valueWithUnit(Format.packVoltage(snapshot.packVoltage), "V"))
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Metric(
                    "Current",
                    valueWithUnit(Format.current(snapshot.current), "A"),
                    if (snapshot.flow == CurrentFlow.DISCHARGING) colors.discharge else colors.textPrimary,
                )
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Metric("Power", valueWithUnit(Format.power(snapshot.power), "W"))
            }
        }
    }
}

@Composable
private fun FlowPill(flow: CurrentFlow) {
    val colors = AeTheme.colors
    when (flow) {
        CurrentFlow.CHARGING -> IconPill("Charging", R.drawable.ic_arrow_up, colors.ok, colors.okBg)
        CurrentFlow.DISCHARGING -> IconPill("Discharging", R.drawable.ic_arrow_down, colors.discharge, colors.dischargeBg)
        CurrentFlow.IDLE -> IconPill("Idle", R.drawable.ic_minus, colors.textSecondary, colors.background, colors.border)
    }
}

@Composable
private fun SocRing(soc: Int) {
    val colors = AeTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp)
            .clearAndSetSemantics { contentDescription = "State of charge $soc percent" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Proportions from the mockup: radius 84 and stroke 14 in a 200 box.
            val stroke = size.minDimension * 14f / 200f
            val radius = size.minDimension * 84f / 200f
            drawCircle(colors.border, radius = radius, style = Stroke(stroke))
            if (soc > 0) {
                drawArc(
                    color = colors.brand,
                    startAngle = -90f,
                    sweepAngle = 360f * soc.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                buildAnnotatedString {
                    append(soc.toString())
                    withStyle(SpanStyle(fontSize = 19.sp)) { append("%") }
                },
                style = AeTheme.type.numberHero,
            )
            Text("State of charge", style = AeTheme.type.label, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun valueWithUnit(value: String, unit: String): AnnotatedString {
    val secondary = AeTheme.colors.textSecondary
    return buildAnnotatedString {
        append(value)
        withStyle(SpanStyle(fontFamily = PlexMono, fontSize = 13.sp, color = secondary)) { append(" $unit") }
    }
}

@Composable
private fun Metric(label: String, value: AnnotatedString, valueColor: Color = AeTheme.colors.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = AeTheme.type.small, color = AeTheme.colors.textSecondary, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.weight(1f))
        Text(value, style = AeTheme.type.numberLarge, color = valueColor, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = AeTheme.type.label, color = AeTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = AeTheme.type.numberMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun CellsCard(snapshot: BmsSnapshot, onOpenCells: () -> Unit) {
    val colors = AeTheme.colors
    val stats = CellStats.of(snapshot.cellVoltages)
    AeCard(onClick = onOpenCells) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Cells", Modifier.weight(1f))
            Text(
                "${snapshot.cellCount} cells · ${snapshot.balancingCells.size} balancing ›",
                style = AeTheme.type.small,
                color = colors.low,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (stats != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(
                        "Highest · #${stats.highestIndex + 1}",
                        "${Format.cellVoltage(stats.highest)} V",
                        cellColor(stats.highest, colors.discharge),
                        Modifier.weight(1f),
                    )
                    Stat(
                        "Lowest · #${stats.lowestIndex + 1}",
                        "${Format.cellVoltage(stats.lowest)} V",
                        cellColor(stats.lowest, colors.low),
                        Modifier.weight(1f),
                    )
                    Stat("Spread", "${Format.millivolts(stats.spread)} mV", colors.textPrimary, Modifier.weight(1f))
                }
            } else {
                Text("No cell voltages yet", style = AeTheme.type.small, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TemperaturesCard(snapshot: BmsSnapshot, unit: TempUnit) {
    AeCard {
        CardTitle("Temperatures")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (snapshot.temperatures.isEmpty()) {
                Text("No sensors reported", style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                snapshot.temperatures.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach {
                            Stat(it.label, Format.temperature(it.celsius, unit), AeTheme.colors.textPrimary, Modifier.weight(1f))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(snapshot: BmsSnapshot) {
    val colors = AeTheme.colors
    val protections = snapshot.protections
    AeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Status", Modifier.weight(1f))
            val (icon, text, color) = when (protections.size) {
                0 -> Triple(R.drawable.ic_check, "No protections active", colors.ok)
                1 -> Triple(R.drawable.ic_alert, "Protection active", colors.error)
                else -> Triple(R.drawable.ic_alert, "${protections.size} protections active", colors.error)
            }
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = AeTheme.type.small, color = color)
        }
        protections.forEach {
            Text(it.message, style = AeTheme.type.body, color = colors.error)
        }
        Spacer(Modifier.weight(1f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MosChip("Charge MOS", snapshot.chargeMosOn)
            MosChip("Discharge MOS", snapshot.dischargeMosOn)
            snapshot.heaterOn?.let { MosChip("Heater", it) }
            StatusChip(if (snapshot.cycleCount == 1) "1 cycle" else "${snapshot.cycleCount} cycles", ChipTone.NEUTRAL)
        }
    }
}

@Composable
private fun MosChip(name: String, on: Boolean) {
    StatusChip(if (on) "$name on" else "$name off", if (on) ChipTone.OK else ChipTone.NEUTRAL)
}
