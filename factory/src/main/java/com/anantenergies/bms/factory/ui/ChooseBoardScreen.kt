package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.ble.BluetoothStatus
import com.anantenergies.bms.factory.dev.FactoryDev
import com.anantenergies.bms.factory.flow.Board
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.ChipTone
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.components.StatusChip
import com.anantenergies.bms.ui.theme.AeTheme

/** Bluetooth checks, then the list of boards heard nearby. */
@Composable
fun ChooseBoardScreen(vm: FactoryViewModel, onRequestPermissions: () -> Unit, onEnableBluetooth: () -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    when (status) {
        BluetoothStatus.READY -> BoardList(vm)
        BluetoothStatus.NEEDS_PERMISSION -> Prompt(
            "Bluetooth permission needed",
            "The app needs Bluetooth access to find and set up the pack's BMS.",
            "Allow Bluetooth",
            onRequestPermissions,
        )
        BluetoothStatus.BLUETOOTH_OFF -> Prompt("Bluetooth is off", "Turn on Bluetooth to find the pack's BMS.", "Turn on Bluetooth", onEnableBluetooth)
        BluetoothStatus.LOCATION_OFF -> Prompt(
            "Location is off",
            "On this Android version Bluetooth scanning needs Location switched on. Turn it on in quick settings.",
            "Check again",
            vm::refreshStatus,
        )
        BluetoothStatus.UNSUPPORTED -> Prompt("No Bluetooth LE", "This phone can't talk to the BMS.", null, {})
    }
}

@Composable
private fun Prompt(title: String, message: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MessageCard(title, listOf(message), Tone.WARNING)
        if (action != null) PrimaryButton(action, onAction, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BoardList(vm: FactoryViewModel) {
    val boards by vm.boards.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    var showOthers by rememberSaveable { mutableStateOf(false) }
    val shown = boards.filter { showOthers || it.recognised }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Wake the BMS (connect a load or charger) and keep the phone close. Tap the pack's BMS to start.",
                style = AeTheme.type.body,
                color = AeTheme.colors.textSecondary,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(if (scanning) "Stop scan" else "Scan again", onClick = { if (scanning) vm.stopScan() else vm.startScan() })
                Text("Show other devices", style = AeTheme.type.small, color = AeTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                Switch(checked = showOthers, onCheckedChange = { showOthers = it })
            }
        }
        FactoryDev.simulatedBoard?.let { simulated ->
            item { BoardRow(simulated, rssi = null, recognised = true, onClick = { vm.connect(simulated) }) }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (scanning) "Looking for BMS boards…" else "No BMS found. Tap Scan again.",
                    style = AeTheme.type.body,
                    color = AeTheme.colors.textSecondary,
                )
            }
        }
        items(shown, key = { it.board.address }) { scanned ->
            BoardRow(scanned.board, scanned.rssi, scanned.recognised, onClick = { vm.connect(scanned.board) })
        }
        item { FactoryDev.Panel() }
        item {
            val context = LocalContext.current
            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            Text("AEtools Factory version $version", style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun BoardRow(board: Board, rssi: Int?, recognised: Boolean, onClick: () -> Unit) {
    AeCard(onClick = onClick, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(board.name, style = AeTheme.type.bodyLarge, color = AeTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            StatusChip(if (recognised) "JBD service found" else "Not recognised", if (recognised) ChipTone.OK else ChipTone.NEUTRAL)
        }
        Text(
            board.address + (rssi?.let { " · $it dBm" } ?: ""),
            style = AeTheme.type.number,
            color = AeTheme.colors.textSecondary,
        )
    }
}
