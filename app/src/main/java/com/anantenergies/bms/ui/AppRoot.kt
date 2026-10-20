package com.anantenergies.bms.ui

import android.graphics.Color.TRANSPARENT
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anantenergies.bms.R
import com.anantenergies.bms.data.ThemeMode
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.ui.cells.CellsScreen
import com.anantenergies.bms.ui.dashboard.DashboardScreen
import com.anantenergies.bms.ui.devices.DevicesScreen
import com.anantenergies.bms.ui.settings.SettingsScreen
import com.anantenergies.bms.ui.theme.AeTheme
import com.anantenergies.bms.ui.theme.DarkColors

private object Routes {
    const val DASHBOARD = "dashboard"
    const val CELLS = "cells"
    const val DEVICES = "devices"
    const val SETTINGS = "settings"

    /** Bluetooth log; debug builds only. */
    const val DEBUG = "debug"
}

private data class Tab(val route: String, val label: String, @param:DrawableRes val icon: Int)

private val Tabs = listOf(
    Tab(Routes.DASHBOARD, "Dashboard", R.drawable.ic_gauge),
    Tab(Routes.CELLS, "Cells", R.drawable.ic_cells),
    Tab(Routes.DEVICES, "Devices", R.drawable.ic_bluetooth),
)

@Composable
fun AppRoot() {
    val viewModel = appViewModel { MainViewModel(it.settings, it.bms) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val demoAddress by viewModel.demoAddress.collectAsStateWithLifecycle()

    val loaded = settings
    if (loaded == null) {
        // Settings load in a few milliseconds; hold the window colour until then.
        Box(Modifier.fillMaxSize().background(DarkColors.background))
        return
    }
    val dark = when (loaded.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    SystemBarIcons(dark)

    AeTheme(dark = dark) {
        val nav = rememberNavController()
        val start = remember { if (loaded.lastDevice != null) Routes.DASHBOARD else Routes.DEVICES }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route

        Column(
            Modifier
                .fillMaxSize()
                .background(AeTheme.colors.background)
                .statusBarsPadding(),
        ) {
            demoAddress?.let { address ->
                DevTools.DemoBanner(address, onExitDemo = {
                    viewModel.exitDemo()
                    nav.showTab(Routes.DEVICES)
                })
            }
            NavHost(nav, startDestination = start, modifier = Modifier.weight(1f)) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenCells = { nav.showTab(Routes.CELLS) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onFindBms = { nav.showTab(Routes.DEVICES) },
                    )
                }
                composable(Routes.CELLS) {
                    CellsScreen(onBack = { nav.showTab(Routes.DASHBOARD) }, onFindBms = { nav.showTab(Routes.DEVICES) })
                }
                composable(Routes.DEVICES) {
                    DevicesScreen(onConnected = { nav.showTab(Routes.DASHBOARD) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onDisconnected = { nav.showTab(Routes.DEVICES) },
                        onOpenDebug = if (DevTools.hasTrafficScreen) {
                            { nav.navigate(Routes.DEBUG) { launchSingleTop = true } }
                        } else {
                            null
                        },
                    )
                }
                if (DevTools.hasTrafficScreen) {
                    composable(Routes.DEBUG) { DevTools.TrafficScreen(onBack = { nav.popBackStack() }) }
                }
            }
            if (Tabs.any { it.route == route }) {
                BottomBar(selected = route, onSelect = { nav.showTab(it) })
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Switches tab; Back from any tab returns to the start screen. */
private fun NavController.showTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}

@Composable
private fun SystemBarIcons(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}

@Composable
private fun BottomBar(selected: String?, onSelect: (String) -> Unit) {
    val colors = AeTheme.colors
    Column(Modifier.background(colors.navBar)) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .selectableGroup(),
        ) {
            Tabs.forEach { tab ->
                val isSelected = tab.route == selected
                val tint = if (isSelected) colors.brand else colors.textSecondary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab.route) })
                        .padding(vertical = 8.dp),
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Text(
                        tab.label,
                        color = tint,
                        style = AeTheme.type.label.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}
