package com.anantenergies.bms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.Settings
import com.anantenergies.bms.data.SettingsRepository
import com.anantenergies.bms.data.TempUnit
import com.anantenergies.bms.data.ThemeMode
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.LinkState
import com.anantenergies.bms.ui.Format
import com.anantenergies.bms.ui.appViewModel
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.ScreenHeader
import com.anantenergies.bms.ui.components.SegmentedControl
import com.anantenergies.bms.ui.components.StatusDot
import com.anantenergies.bms.ui.components.Wordmark
import com.anantenergies.bms.ui.linkLabel
import com.anantenergies.bms.ui.theme.AeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val settings: Settings = Settings(),
    val device: BmsDevice? = null,
    val isDemo: Boolean = false,
    val link: LinkState = LinkState.Disconnected,
    val info: BmsInfo? = null,
    val cellCount: Int? = null,
)

class SettingsViewModel(private val settings: SettingsRepository, private val bms: BmsRepository) : ViewModel() {
    val state: StateFlow<SettingsState> = combine(
        settings.settings, bms.session, bms.linkState, bms.info, bms.snapshot,
    ) { prefs, session, link, info, snapshot ->
        SettingsState(prefs, session?.device, session?.isDemo == true, link, info, snapshot?.cellCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setAutoConnect(on: Boolean) {
        viewModelScope.launch { settings.setAutoConnect(on) }
    }

    fun setRefreshSeconds(seconds: Int) {
        viewModelScope.launch { settings.setRefreshSeconds(seconds) }
    }

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setTempUnit(unit: TempUnit) {
        viewModelScope.launch { settings.setTempUnit(unit) }
    }

    fun disconnect() = bms.disconnect()

    fun forget() = bms.forget()
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
    /** Opens the developer tools after 5 taps on the app version; null in release builds. */
    onOpenDebug: (() -> Unit)?,
) {
    val viewModel = appViewModel { SettingsViewModel(it.settings, it.bms) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val colors = AeTheme.colors

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            Section("Connection") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(settings.autoConnect, role = Role.Switch, onValueChange = viewModel::setAutoConnect)
                        .padding(vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-connect on launch", style = AeTheme.type.bodyLarge)
                        Text("Reconnect to the last BMS automatically", style = AeTheme.type.label, color = colors.textSecondary)
                    }
                    Switch(
                        checked = settings.autoConnect,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onBrand,
                            checkedTrackColor = colors.brand,
                            checkedBorderColor = colors.brand,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.background,
                            uncheckedBorderColor = colors.borderStrong,
                        ),
                    )
                }
                Divider()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    Column {
                        Text("Refresh rate", style = AeTheme.type.bodyLarge)
                        Text("How often the app asks the BMS for new readings", style = AeTheme.type.label, color = colors.textSecondary)
                    }
                    SegmentedControl(
                        options = Settings.REFRESH_CHOICES,
                        selected = settings.refreshSeconds,
                        label = { "$it s" },
                        onSelect = { viewModel.setRefreshSeconds(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Section("Display") {
                LabelledRow("Theme") {
                    SegmentedControl(
                        options = ThemeMode.entries,
                        selected = settings.theme,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                            }
                        },
                        onSelect = { viewModel.setTheme(it) },
                        modifier = Modifier.width(210.dp),
                    )
                }
                Divider()
                LabelledRow("Temperature unit") {
                    SegmentedControl(
                        options = TempUnit.entries,
                        selected = settings.tempUnit,
                        label = { if (it == TempUnit.CELSIUS) "°C" else "°F" },
                        onSelect = { viewModel.setTempUnit(it) },
                        modifier = Modifier.width(132.dp),
                    )
                }
            }

            Section("This BMS") {
                ThisBms(
                    state = state,
                    onDisconnect = {
                        viewModel.disconnect()
                        onDisconnected()
                    },
                    onForget = {
                        viewModel.forget()
                        onDisconnected()
                    },
                )
            }

            Section("About") {
                val context = LocalContext.current
                val version = remember {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                }
                var taps by remember { mutableIntStateOf(0) }
                LabelledRow(
                    "App version",
                    modifier = if (onOpenDebug != null) {
                        Modifier.clickable(indication = null, interactionSource = null) {
                            taps++
                            if (taps >= DEBUG_TAPS) {
                                taps = 0
                                onOpenDebug()
                            }
                        }
                    } else {
                        Modifier
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(version, style = AeTheme.type.number, color = colors.textSecondary)
                        DevTools.buildLabel?.let { Text(" · $it", style = AeTheme.type.small, color = colors.textSecondary) }
                    }
                }
                Divider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                    Wordmark(height = 13.dp)
                    Text(
                        "Empowering the future",
                        style = AeTheme.type.small,
                        color = if (colors.isDark) Color.White else Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThisBms(state: SettingsState, onDisconnect: () -> Unit, onForget: () -> Unit) {
    val colors = AeTheme.colors
    val device = state.device
    val remembered = state.settings.lastDevice
    when {
        device != null -> {
            val details = listOfNotNull(
                if (state.isDemo) "Simulated" else Format.maskedAddress(device.address),
                state.info?.model,
                state.cellCount?.let { "${it}S" },
            ).joinToString(" · ")
            val (label, dot) = linkLabel(state.link)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = AeTheme.type.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(details, style = AeTheme.type.numberSmall, color = colors.textSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(dot)
                    Text(label, style = AeTheme.type.small, color = if (state.link is LinkState.Connected) colors.ok else colors.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                OutlineButton("Disconnect", onClick = onDisconnect, modifier = Modifier.weight(1f))
                // The demo pack is never remembered, so there's nothing to forget.
                if (!state.isDemo) {
                    ForgetButton(onForget, Modifier.weight(1f))
                }
            }
        }
        remembered != null -> {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(remembered.name, style = AeTheme.type.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Format.maskedAddress(remembered.address)} · Not connected",
                    style = AeTheme.type.numberSmall,
                    color = colors.textSecondary,
                )
            }
            ForgetButton(onForget, Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        else -> Text(
            "No BMS connected",
            style = AeTheme.type.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ForgetButton(onForget: () -> Unit, modifier: Modifier) {
    OutlineButton(
        "Forget this BMS",
        onClick = onForget,
        modifier = modifier,
        contentColor = AeTheme.colors.discharge,
        borderColor = AeTheme.colors.dischargeBorder,
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    AeCard(
        // Full width even when the content is short, e.g. "No BMS connected".
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            title.uppercase(),
            style = AeTheme.type.sectionLabel,
            color = AeTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        content()
    }
}

private const val DEBUG_TAPS = 5

@Composable
private fun LabelledRow(label: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(label, style = AeTheme.type.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(thickness = 1.dp, color = AeTheme.colors.border)
}
