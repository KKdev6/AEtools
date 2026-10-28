package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.AeMark
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.theme.AeTheme

/** AE mark, app name, what the screen is doing, and the log button. */
@Composable
fun TopBar(subtitle: String, logOpen: Boolean, onLog: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        AeMark(height = 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("AEtools Factory", style = AeTheme.type.headerTitle, color = AeTheme.colors.textPrimary)
            Text(subtitle, style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        }
        OutlineButton(text = if (logOpen) "Close log" else "Log", onClick = onLog, height = 36.dp)
    }
}

/** A spinner with what the app is waiting for. */
@Composable
fun Busy(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = AeTheme.colors.brand)
        Text(title, style = AeTheme.type.headerTitle, color = AeTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = AeTheme.type.body, color = AeTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

enum class Tone { OK, WARNING, ERROR, NEUTRAL }

/** A card with a coloured title line, used for results and warnings. */
@Composable
fun MessageCard(title: String, lines: List<String>, tone: Tone, modifier: Modifier = Modifier) {
    val colors = AeTheme.colors
    val titleColor = when (tone) {
        Tone.OK -> colors.ok
        Tone.WARNING -> colors.discharge
        Tone.ERROR -> colors.error
        Tone.NEUTRAL -> colors.textPrimary
    }
    AeCard(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = AeTheme.type.headerTitle, color = titleColor)
        lines.forEach { Text(it, style = AeTheme.type.body, color = colors.textPrimary) }
    }
}

/** Short status shown under the app name. */
fun Step.title(): String = when (this) {
    Step.ChooseBoard -> "Choose the pack's BMS"
    is Step.Connecting -> "Connecting"
    is Step.ConnectFailed -> "Couldn't connect"
    is Step.UnknownBoard -> "Unknown BMS"
    is Step.EnterDetails -> "Pack details"
    is Step.Reading -> "Reading the BMS"
    is Step.Review -> "Review before writing"
    is Step.Applying -> "Writing settings"
    is Step.Applied -> "Written"
    is Step.PowerCycle -> "Power-cycle the BMS"
    is Step.Verifying -> "Checking after the restart"
    is Step.Verified -> "Result"
    is Step.Checklist -> "Before sealing the pack"
    is Step.Logged -> "Saved to the log sheet"
}

/** Back goes to the board list, except while the app is talking to the BMS. */
fun Step.canGoBack(): Boolean = when (this) {
    Step.ChooseBoard, is Step.Connecting, is Step.Reading, is Step.Applying, is Step.Verifying, is Step.Checklist -> false
    else -> true
}

/** A setting's value as staff read it, e.g. "4.200 V" or "Charge balance". */
fun JbdSetting.describe(raw: Int?): String = when {
    raw == null -> "—"
    this == JbdSetting.FUNCTIONS -> when {
        raw and JbdSetting.BALANCE_ON == 0 -> "Balance off"
        raw and JbdSetting.CHARGE_BALANCE != 0 -> "Charge balance"
        else -> "Static balance"
    }
    else -> format(raw)
}
