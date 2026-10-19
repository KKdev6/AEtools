package com.anantenergies.bms.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.R
import com.anantenergies.bms.domain.CellLimits
import com.anantenergies.bms.domain.TemperatureLevel
import com.anantenergies.bms.domain.TemperatureLimits
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme

/** Cells outside the datasheet range are red; otherwise [normal]. */
@Composable
fun cellColor(volts: Double, normal: Color): Color =
    if (CellLimits.isOutOfRange(volts)) AeTheme.colors.error else normal

/** Temperatures above 45 °C are orange, above 50 °C red. */
@Composable
fun temperatureColor(celsius: Double): Color = when (TemperatureLimits.level(celsius)) {
    TemperatureLevel.NORMAL -> AeTheme.colors.textPrimary
    TemperatureLevel.WARM -> AeTheme.colors.discharge
    TemperatureLevel.HOT -> AeTheme.colors.error
}

/** A surface card with a 12 dp corner and a hairline border. */
@Composable
fun AeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AeTheme.colors
    Column(
        modifier = modifier
            .clip(AeShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, AeShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = AeTheme.type.cardTitle)
}

enum class ChipTone { OK, NEUTRAL, ERROR }

/** A fully rounded status chip. */
@Composable
fun StatusChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val colors = AeTheme.colors
    val (fg, border) = when (tone) {
        ChipTone.OK -> colors.ok to colors.okBorder
        ChipTone.NEUTRAL -> colors.textSecondary to colors.border
        ChipTone.ERROR -> colors.error to colors.errorBorder
    }
    Text(
        text = text,
        color = fg,
        style = AeTheme.type.small,
        modifier = modifier
            .border(1.dp, border, AeShapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** A filled pill with an icon, e.g. the Charging / Discharging / Idle status. */
@Composable
fun IconPill(text: String, @DrawableRes icon: Int, foreground: Color, background: Color, border: Color = background) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(AeShapes.pill)
            .background(background)
            .border(1.dp, border, AeShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        Text(text, color = foreground, style = AeTheme.type.small.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** A row of mutually exclusive options in a bordered track; the selected one is filled with the brand colour. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AeTheme.colors
    Row(
        modifier = modifier
            .clip(AeShapes.small)
            .background(colors.background)
            .border(1.dp, colors.border, AeShapes.small)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(AeShapes.small)
                    .background(if (isSelected) colors.brand else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) colors.onBrand else colors.textPrimary,
                    style = AeTheme.type.body.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                )
            }
        }
    }
}

/** Back arrow, title with optional subtitle, and the AE mark on the right. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(AeShapes.small)
                .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack),
        ) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AeTheme.type.headerTitle)
            if (subtitle != null) Text(subtitle, style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        }
        AeMark(height = 28.dp)
    }
}

/** A centred message for screens with nothing to show yet. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = AeTheme.type.headerTitle, textAlign = TextAlign.Center)
        Text(message, style = AeTheme.type.body, color = AeTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** A 44 dp tall outlined button with 4 dp corners. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = AeTheme.colors.textPrimary,
    borderColor: Color = AeTheme.colors.borderStrong,
    height: Dp = 44.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(AeShapes.small)
            .border(1.dp, borderColor, AeShapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(text, color = contentColor, style = AeTheme.type.body.copy(fontWeight = FontWeight.Medium))
    }
}
