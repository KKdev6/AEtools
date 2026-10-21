package com.anantenergies.bms.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anantenergies.bms.AeApp
import com.anantenergies.bms.AppContainer
import com.anantenergies.bms.ble.BluetoothAvailability
import com.anantenergies.bms.ble.BluetoothStatus
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.ConnectStatus
import com.anantenergies.bms.data.Settings
import com.anantenergies.bms.data.SettingsRepository
import com.anantenergies.bms.data.TempUnit
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A view model built from the app's singletons. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as AeApp).container) }
    })

private const val STOP_TIMEOUT_MS = 5_000L

/** Lives as long as the activity, so it starts auto-connect once per launch, not on rotation. */
class MainViewModel(settings: SettingsRepository, bms: BmsRepository, bluetooth: BluetoothAvailability) : ViewModel() {
    /** Null until the stored settings have loaded. */
    val settings: StateFlow<Settings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { bms.autoConnect(bluetoothReady = bluetooth.status() == BluetoothStatus.READY) }
        // Auto-connect continues as soon as Bluetooth is switched on, whichever screen is open.
        viewModelScope.launch {
            bluetooth.changes().collect { if (bluetooth.status() == BluetoothStatus.READY) bms.bluetoothReady() }
        }
    }
}

/** Everything the Dashboard and Cells screens show. */
data class LiveState(
    val hasSession: Boolean = false,
    val deviceName: String? = null,
    val link: LinkState = LinkState.Disconnected,
    val info: BmsInfo? = null,
    val snapshot: BmsSnapshot? = null,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    /** Progress of the latest connection, e.g. auto-connect on launch. */
    val connect: ConnectStatus? = null,
    val bluetooth: BluetoothStatus = BluetoothStatus.READY,
)

class LiveViewModel(
    private val bms: BmsRepository,
    settings: SettingsRepository,
    private val bluetooth: BluetoothAvailability,
) : ViewModel() {
    private val bluetoothStatus = MutableStateFlow(bluetooth.status())

    val permissions: Array<String> get() = bluetooth.requiredPermissions

    val state: StateFlow<LiveState> = combine(
        combine(bms.session, bms.linkState, bms.info, ::Triple),
        bms.snapshot,
        settings.settings,
        bms.connectStatus,
        bluetoothStatus,
    ) { (session, link, info), snapshot, prefs, connect, bluetooth ->
        LiveState(session != null, session?.device?.name, link, info, snapshot, prefs.tempUnit, connect, bluetooth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveState())

    init {
        viewModelScope.launch { bluetooth.changes().collect { refreshBluetooth() } }
    }

    /** Re-checks Bluetooth, e.g. after a permission prompt, and lets a waiting auto-connect go ahead. */
    fun refreshBluetooth() {
        bluetoothStatus.value = bluetooth.status()
        if (bluetoothStatus.value == BluetoothStatus.READY) bms.bluetoothReady()
    }

    fun retry(device: BmsDevice) {
        if (bluetooth.status() == BluetoothStatus.READY) bms.connect(device) else bms.waitForBluetooth(device)
    }

    /** Stops connecting (or waiting to), e.g. before choosing another BMS. */
    fun stopConnecting() = bms.disconnect()
}
