package com.anantenergies.bms.ui.devices

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anantenergies.bms.R
import com.anantenergies.bms.ble.BleScanner
import com.anantenergies.bms.ble.BluetoothAvailability
import com.anantenergies.bms.ble.BluetoothStatus
import com.anantenergies.bms.ble.ScanFailedException
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.ConnectStatus
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsBrand
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.LinkState
import com.anantenergies.bms.ui.Format
import com.anantenergies.bms.ui.appViewModel
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.AeMark
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.Wordmark
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A device heard in the scan. [brand] is the brand that recognised it, if any. */
data class FoundDevice(val advertisement: Advertisement, val brand: BmsBrand?) {
    val name: String get() = advertisement.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
    val hasName: Boolean get() = !advertisement.name.isNullOrBlank()
}

data class DevicesState(
    val bluetooth: BluetoothStatus = BluetoothStatus.READY,
    val scanning: Boolean = false,
    val scanError: String? = null,
    /** Recognised BMSs first, then other named devices, each by signal strength. */
    val devices: List<FoundDevice> = emptyList(),
    val unnamed: List<FoundDevice> = emptyList(),
    val showUnnamed: Boolean = false,
    val connect: ConnectStatus? = null,
    /** The device the app is connected to, if the link is up. */
    val connectedAddress: String? = null,
)

class DevicesViewModel(
    private val scanner: BleScanner,
    private val bluetooth: BluetoothAvailability,
    private val bms: BmsRepository,
    private val brands: List<BmsBrand>,
) : ViewModel() {
    private val status = MutableStateFlow(bluetooth.status())
    private val scanning = MutableStateFlow(false)
    private val scanError = MutableStateFlow<String?>(null)
    private val found = MutableStateFlow<Map<String, Advertisement>>(emptyMap())
    private val showUnnamed = MutableStateFlow(false)
    private var scanJob: Job? = null

    val permissions: Array<String> get() = bluetooth.requiredPermissions

    init {
        viewModelScope.launch { bluetooth.changes().collect { refresh() } }
    }

    val state: StateFlow<DevicesState> = combine(
        combine(status, scanning, scanError, ::Triple),
        combine(found, showUnnamed, ::Pair),
        combine(bms.connectStatus, bms.session, bms.linkState, ::Triple),
    ) { (status, scanning, scanError), (found, showUnnamed), (connect, session, link) ->
        val all = (DevTools.simulatedAdvertisements + found.values.filter { ad -> DevTools.simulatedAdvertisements.none { it.address == ad.address } })
            .map { ad -> FoundDevice(ad, brands.firstOrNull { it.matches(ad) }) }
            .sortedWith(compareBy<FoundDevice> { it.brand == null }.thenByDescending { it.advertisement.rssi })
        DevicesState(
            bluetooth = status,
            scanning = scanning,
            scanError = scanError,
            devices = all.filter { it.hasName || it.brand != null },
            unnamed = all.filter { !it.hasName && it.brand == null },
            showUnnamed = showUnnamed,
            connect = connect,
            connectedAddress = session?.device?.address?.takeIf { link is LinkState.Connected },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesState())

    /** Re-checks permissions, Bluetooth and Location, e.g. after returning from system settings. */
    fun refresh() {
        status.value = bluetooth.status()
        if (status.value != BluetoothStatus.READY) stopScan()
    }

    fun startScan() {
        if (status.value != BluetoothStatus.READY || scanJob?.isActive == true) return
        found.value = emptyMap()
        scanError.value = null
        scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_DURATION_MS) {
                    scanner.scan().collect { ad -> found.update { it + (ad.address to ad) } }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                scanError.value = if (e is ScanFailedException) {
                    "Scanning failed (code ${e.errorCode}). Wait a moment and scan again."
                } else {
                    "Scanning failed: ${e.message}"
                }
            } finally {
                scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun connect(device: FoundDevice) {
        // Android connects more reliably with no scan running.
        stopScan()
        bms.connect(BmsDevice(device.name, device.advertisement.address, device.brand?.id))
    }

    fun dismissError() = bms.clearConnectStatus()

    fun toggleUnnamed() {
        showUnnamed.update { !it }
    }

    fun startDemo() = bms.startDemo()

    private companion object {
        const val SCAN_DURATION_MS = 30_000L
    }
}

@Composable
fun DevicesScreen(onConnected: () -> Unit) {
    val viewModel = appViewModel { DevicesViewModel(it.scanner, it.bluetooth, it.bms, it.brands) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AeTheme.colors
    var confirm by remember { mutableStateOf<FoundDevice?>(null) }
    var awaiting by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.bluetooth) {
        if (state.bluetooth == BluetoothStatus.READY) viewModel.startScan()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }
    // Open the Dashboard once the device the user picked is connected.
    LaunchedEffect(awaiting, state.connectedAddress, state.connect) {
        if (awaiting != null && state.connect == null && state.connectedAddress == awaiting) {
            awaiting = null
            onConnected()
        }
        if (state.connect is ConnectStatus.Failed) awaiting = null
    }

    fun pick(device: FoundDevice) {
        if (device.brand == null) {
            confirm = device
        } else {
            awaiting = device.advertisement.address
            viewModel.connect(device)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    AeMark(height = 36.dp, contentDescription = null)
                    Wordmark(height = 15.dp)
                }
                Text("Connect to BMS", style = AeTheme.type.screenTitle)
                Text(
                    "Keep your phone within a few metres of the battery. The BMS sleeps after a minute with " +
                        "no current, so wake it by connecting a load or charger if it doesn't show up.",
                    style = AeTheme.type.body,
                    color = colors.textSecondary,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                if (state.bluetooth == BluetoothStatus.READY) {
                    ScanRow(state.scanning, onScan = viewModel::startScan, onStop = viewModel::stopScan)
                } else {
                    BluetoothPrompt(state.bluetooth, viewModel.permissions, onChanged = viewModel::refresh)
                }

                (state.connect as? ConnectStatus.Failed)?.let { failed ->
                    Message(failed.message, isError = true, onDismiss = viewModel::dismissError)
                }
                state.scanError?.let { Message(it, isError = true) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val visible = state.devices + if (state.showUnnamed) state.unnamed else emptyList()
                    visible.forEach { device ->
                        val address = device.advertisement.address
                        DeviceRow(
                            device = device,
                            connecting = (state.connect as? ConnectStatus.Connecting)?.device?.address == address,
                            connected = state.connectedAddress == address,
                            onClick = { pick(device) },
                        )
                    }
                }

                if (state.unnamed.isNotEmpty()) {
                    TextButton(onClick = viewModel::toggleUnnamed) {
                        Text(
                            if (state.showUnnamed) "Hide unnamed devices" else "Show ${state.unnamed.size} unnamed devices",
                            color = colors.low,
                            style = AeTheme.type.small,
                        )
                    }
                }
                if (state.bluetooth == BluetoothStatus.READY && !state.scanning && state.devices.none { it.brand != null }) {
                    Text(
                        "No BMS found. Wake it by connecting a load or charger, then scan again.",
                        style = AeTheme.type.small,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        DevTools.DemoEntryCard(
            onStartDemo = {
                viewModel.startDemo()
                onConnected()
            },
            modifier = Modifier.padding(16.dp),
        )
    }

    confirm?.let { device ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Connect to ${device.name}?") },
            text = {
                Text("This device wasn't recognised as a supported BMS. The app will connect and check what it offers.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    awaiting = device.advertisement.address
                    viewModel.connect(device)
                }) { Text("Connect", color = colors.brand) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = colors.textSecondary) } },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            shape = AeShapes.card,
        )
    }
}

@Composable
private fun ScanRow(scanning: Boolean, onScan: () -> Unit, onStop: () -> Unit) {
    val colors = AeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(16.dp), color = colors.low, strokeWidth = 2.dp)
            }
            Text(
                if (scanning) "Scanning for devices…" else "Scan finished",
                style = AeTheme.type.body,
                color = colors.textSecondary,
            )
        }
        OutlineButton(
            if (scanning) "Stop" else "Scan again",
            onClick = if (scanning) onStop else onScan,
            borderColor = colors.border,
            height = 36.dp,
        )
    }
}

/** Explains what's stopping the app from scanning, with a button that fixes it. */
@Composable
private fun BluetoothPrompt(status: BluetoothStatus, permissions: Array<String>, onChanged: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var deniedForGood by rememberSaveable { mutableStateOf(false) }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it } && activity != null) {
            // Android stops showing the prompt after repeated denials; then only Settings can grant it.
            deniedForGood = permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
        onChanged()
    }
    val turnOnBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }

    val (title, message, action) = when (status) {
        BluetoothStatus.NEEDS_PERMISSION -> Triple(
            "Allow Bluetooth access",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "AEtools needs permission to find and connect to your BMS over Bluetooth."
            } else {
                "This Android version only lets apps find Bluetooth devices with location permission. AEtools doesn't use your location."
            },
            if (deniedForGood) {
                "Open app settings" to {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }
            } else {
                "Allow" to { askPermissions.launch(permissions) }
            },
        )
        BluetoothStatus.BLUETOOTH_OFF -> Triple(
            "Bluetooth is off",
            "Turn on Bluetooth to find your BMS.",
            "Turn on Bluetooth" to { turnOnBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        )
        BluetoothStatus.LOCATION_OFF -> Triple(
            "Location is off",
            "On this Android version, Bluetooth scanning only works with Location switched on.",
            "Open location settings" to { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        )
        BluetoothStatus.UNSUPPORTED -> Triple(
            "Bluetooth LE not available",
            "This phone doesn't support Bluetooth Low Energy, which the BMS uses.",
            null,
        )
        BluetoothStatus.READY -> return
    }

    AeCard {
        Text(title, style = AeTheme.type.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
        Text(message, style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        if (action != null) {
            OutlineButton(action.first, onClick = action.second, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Message(text: String, isError: Boolean, onDismiss: (() -> Unit)? = null) {
    val colors = AeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeShapes.card)
            .background(if (isError) colors.errorBg else colors.surface)
            .border(1.dp, if (isError) colors.errorBorder else colors.border, AeShapes.card)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text,
            style = AeTheme.type.small,
            color = if (isError) colors.error else colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        if (onDismiss != null) {
            TextButton(onClick = onDismiss) { Text("OK", color = if (isError) colors.error else colors.brand) }
        }
    }
}

@Composable
private fun DeviceRow(device: FoundDevice, connecting: Boolean, connected: Boolean, onClick: () -> Unit) {
    val colors = AeTheme.colors
    val brand = device.brand
    val ad = device.advertisement
    val detail = when {
        brand == null -> "Not recognised"
        brand.serviceUuid in ad.serviceUuids -> "${brand.name} service found"
        else -> "${brand.name} (by name)"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeShapes.card)
            .background(colors.surface)
            .border(1.dp, if (brand != null) colors.connectedDot else colors.border, AeShapes.card)
            .clickable(enabled = !connecting && !connected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(AeShapes.small)
                .background(if (brand != null) colors.okBg else colors.border),
        ) {
            Icon(
                painterResource(if (brand != null) R.drawable.ic_battery else R.drawable.ic_bluetooth),
                contentDescription = null,
                tint = if (brand != null) colors.ok else colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = AeTheme.type.bodyLarge.copy(fontWeight = if (brand != null) FontWeight.SemiBold else FontWeight.Normal),
                color = if (brand != null) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Address in mono, the rest in the narrower sans so "· JBD service found" fits.
            Text(
                buildAnnotatedString {
                    withStyle(AeTheme.type.numberSmall.toSpanStyle()) { append(Format.maskedAddress(ad.address)) }
                    append(" · $detail")
                },
                style = AeTheme.type.label,
                color = colors.textSecondary,
                // Wraps rather than cutting off on narrow phones.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            connecting -> CircularProgressIndicator(Modifier.size(18.dp), color = colors.brand, strokeWidth = 2.dp)
            connected -> Text("Connected", style = AeTheme.type.small, color = colors.ok)
            else -> Text(
                Format.rssi(ad.rssi),
                style = AeTheme.type.number.copy(fontWeight = FontWeight.Normal),
                color = if (brand != null) colors.ok else colors.textSecondary,
            )
        }
    }
}
