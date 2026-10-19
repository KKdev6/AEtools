package com.anantenergies.bms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.anantenergies.bms.domain.LinkState
import com.anantenergies.bms.ui.theme.AeTheme
import kotlinx.coroutines.delay

/** The current time, updated every second, for "updated 3 s ago". */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Short connection label with its dot colour, e.g. "Connected". */
@Composable
fun linkLabel(link: LinkState): Pair<String, Color> {
    val colors = AeTheme.colors
    return when (link) {
        is LinkState.Connected -> "Connected" to colors.connectedDot
        LinkState.Connecting -> "Connecting…" to colors.discharge
        is LinkState.Reconnecting -> "Reconnecting…" to colors.discharge
        LinkState.Disconnected -> "Not connected" to colors.textSecondary
    }
}

/** Full status line, e.g. "Connected · −62 dBm · updated 1 s ago". */
fun linkStatusLine(link: LinkState, label: String, updatedAt: Long?, now: Long): String {
    val parts = mutableListOf(label)
    if (link is LinkState.Connected) link.rssi?.let { parts += "${Format.rssi(it)} dBm" }
    if (updatedAt != null && (link is LinkState.Connected || link is LinkState.Reconnecting)) {
        parts += "updated " + Format.age(((now - updatedAt) / 1000).coerceAtLeast(0))
    }
    return parts.joinToString(" · ")
}
