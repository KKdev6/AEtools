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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.anantenergies.bms.ble.shortLabel
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.ConnectStatus
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsBrand
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.LinkState
import com.anantenergies.bms.qr.MacAddress
import com.anantenergies.bms.qr.QrScanResult
import com.anantenergies.bms.qr.QrScanner
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** A device heard in the scan. [brand] is the brand that recognised it, if any. */
data class FoundDevice(val advertisement: Advertisement, val brand: BmsBrand?) {
    val name: String get() = advertisement.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
    val hasName: Boolean get() = !advertisement.name.isNullOrBlank()
}

/** Progress of connecting from the QR label on a battery pack. */
sealed interface QrStatus {
    /** Google Play services is downloading its QR scanner, once per phone. */
    data object PreparingScanner : QrStatus

    /** Scanning for the BMS whose address the label holds. */
    data class Finding(val address: String) : QrStatus

    data class NotFound(val address: String) : QrStatus

    data class Failed(val message: String) : QrStatus
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
    val qr: QrStatus? = null,
    /** The device whose connection opens the Dashboard once it's up. */
    val awaiting: String? = null,
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
    private val qr = MutableStateFlow<QrStatus?>(null)
    private val awaiting = MutableStateFlow<String?>(null)
    private var scanJob: Job? = null
    private var qrJob: Job? = null

    val permissions: Array<String> get() = bluetooth.requiredPermissions

    init {
        viewModelScope.launch { bluetooth.changes().collect { refresh() } }
    }

    val state: StateFlow<DevicesState> = combine(
        combine(status, scanning, scanError, ::Triple),
        combine(found, showUnnamed, ::Pair),
        combine(bms.connectStatus, bms.session, bms.linkState, ::Triple),
        combine(qr, awaiting, ::Pair),
    ) { (status, scanning, scanError), (found, showUnnamed), (connect, session, link), (qr, awaiting) ->
        val all = found.values
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
            qr = qr,
            awaiting = awaiting,
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
                scanError.value = scanFailedMessage(e)
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
        dismissQr()
        startConnect(BmsDevice(device.name, device.advertisement.address, device.brand?.id), device.advertisement)
    }

    /** Call once the Dashboard has opened for the awaited device, or its connection failed. */
    fun stopAwaiting() {
        awaiting.value = null
    }

    fun dismissError() = bms.clearConnectStatus()

    fun toggleUnnamed() {
        showUnnamed.update { !it }
    }

    fun qrDownloading() {
        qr.value = QrStatus.PreparingScanner
    }

    fun qrFailed(message: String) {
        qr.value = QrStatus.Failed(message)
    }

    /** Text read from a pack's QR label: find the BMS it names, then connect. */
    fun onQrRead(text: String) {
        val address = MacAddress.findIn(text) ?: DevTools.simulatedAddressIn(text)
        if (address == null) {
            qr.value = QrStatus.Failed("This QR code doesn't contain a BMS address")
        } else {
            findAndConnect(address)
        }
    }

    fun retryQr() {
        (qr.value as? QrStatus.NotFound)?.let { findAndConnect(it.address) }
    }

    fun dismissQr() {
        qrJob?.cancel()
        qrJob = null
        qr.value = null
    }

    /** Leaving the screen drops a QR search and the pending jump to the Dashboard. */
    fun leave() {
        dismissQr()
        stopAwaiting()
    }

    private fun startConnect(device: BmsDevice, ad: Advertisement) {
        // Android connects more reliably with no scan running.
        stopScan()
        DevTools.trafficLog?.info(describe(ad))
        awaiting.value = device.address
        bms.connect(device)
    }

    private fun findAndConnect(address: String) {
        qrJob?.cancel()
        val session = bms.session.value
        if (session?.device?.address == address && bms.linkState.value is LinkState.Connected) {
            // Already connected to this pack: just open the Dashboard.
            qr.value = null
            awaiting.value = address
            return
        }
        stopScan()
        qrJob = viewModelScope.launch {
            qr.value = QrStatus.Finding(address)
            val ad = try {
                DevTools.simulatedAdvertisements.firstOrNull { it.address == address }
                    ?: scanner.find(address, QR_FIND_TIMEOUT)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                qr.value = QrStatus.Failed(scanFailedMessage(e))
                return@launch
            }
            if (ad == null) {
                qr.value = QrStatus.NotFound(address)
                return@launch
            }
            qr.value = null
            val brand = brands.firstOrNull { it.matches(ad) }
            startConnect(BmsDevice(ad.name?.takeIf { it.isNotBlank() } ?: "BMS", ad.address, brand?.id), ad)
        }
    }

    /** What the board advertised, for the Bluetooth log: how a brand recognises it. */
    private fun describe(ad: Advertisement): String {
        val services = ad.serviceUuids.map { uuid -> runCatching { UUID.fromString(uuid).shortLabel() }.getOrDefault(uuid) }
        return listOf(
            "Connecting to ${ad.address}",
            "Advertised name: ${ad.name ?: "(none)"}",
            "Signal: ${ad.rssi} dBm",
            "Advertised services: ${services.joinToString().ifEmpty { "(none)" }}",
            "Recognised as: ${brands.firstOrNull { it.matches(ad) }?.name ?: "not recognised"}",
            "Raw advertisement: ${ad.raw ?: "(not captured)"}",
        ).joinToString("\n")
    }

    private fun scanFailedMessage(e: Exception) = if (e is ScanFailedException) {
        "Scanning failed (code ${e.errorCode}). Wait a moment and scan again."
    } else {
        "Scanning failed: ${e.message}"
    }

    private companion object {
        const val SCAN_DURATION_MS = 30_000L
        val QR_FIND_TIMEOUT = 10.seconds
    }
}

@Composable
fun DevicesScreen(onConnected: () -> Unit) {
    val viewModel = appViewModel { DevicesViewModel(it.scanner, it.bluetooth, it.bms, it.brands) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AeTheme.colors
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<FoundDevice?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.bluetooth) {
        if (state.bluetooth == BluetoothStatus.READY) viewModel.startScan()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
            // A rotation keeps a QR search going; leaving the screen drops it.
            if (activity?.isChangingConfigurations != true) viewModel.leave()
        }
    }
    // Open the Dashboard once the device the user picked is connected.
    LaunchedEffect(state.awaiting, state.connectedAddress, state.connect) {
        val awaiting = state.awaiting ?: return@LaunchedEffect
        if (state.connect == null && state.connectedAddress == awaiting) {
            viewModel.stopAwaiting()
            onConnected()
        } else if (state.connect is ConnectStatus.Failed) {
            viewModel.stopAwaiting()
        }
    }

    fun pick(device: FoundDevice) {
        if (device.brand == null) confirm = device else viewModel.connect(device)
    }

    fun scanQr() {
        val context = activity ?: return
        viewModel.dismissQr()
        scope.launch {
            when (val result = QrScanner.scan(context, onDownloading = viewModel::qrDownloading)) {
                is QrScanResult.Read -> viewModel.onQrRead(result.text)
                QrScanResult.Cancelled -> viewModel.dismissQr()
                is QrScanResult.Unavailable -> viewModel.qrFailed(result.message)
            }
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
                val visible = state.devices + if (state.showUnnamed) state.unnamed else emptyList()

                QrButton(
                    enabled = state.bluetooth == BluetoothStatus.READY &&
                        state.qr !is QrStatus.PreparingScanner && state.qr !is QrStatus.Finding,
                    onClick = { scanQr() },
                )
                when (val qr = state.qr) {
                    QrStatus.PreparingScanner ->
                        Message("Getting the QR scanner ready. Google Play services downloads it once.", busy = true)
                    is QrStatus.Finding -> Message(
                        "Looking for BMS ${Format.maskedAddress(qr.address)}…",
                        busy = true,
                        actionLabel = "Cancel",
                        onAction = viewModel::dismissQr,
                    )
                    is QrStatus.NotFound -> Message(
                        "BMS not found. Make sure it's nearby.",
                        isError = true,
                        actionLabel = "Retry",
                        onAction = viewModel::retryQr,
                    )
                    is QrStatus.Failed -> Message(qr.message, isError = true, onAction = viewModel::dismissQr)
                    null -> {}
                }
                // A device reached through its QR code may not be in the list to show its own spinner.
                (state.connect as? ConnectStatus.Connecting)?.device
                    ?.takeIf { device -> visible.none { it.advertisement.address == device.address } }
                    ?.let { Message("Connecting to ${it.name}…", busy = true) }
                Text(
                    "or pick it from the list below",
                    style = AeTheme.type.small,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.bluetooth == BluetoothStatus.READY) {
                    ScanRow(state.scanning, onScan = viewModel::startScan, onStop = viewModel::stopScan)
                } else {
                    BluetoothPrompt(state.bluetooth, viewModel.permissions, onChanged = viewModel::refresh)
                }

                (state.connect as? ConnectStatus.Failed)?.let { failed ->
                    Message(failed.message, isError = true, onAction = viewModel::dismissError)
                }
                state.scanError?.let { Message(it, isError = true) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** The main way in: the QR label on the battery pack holds its BMS's address. */
@Composable
private fun QrButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = AeTheme.colors
    val foreground = if (enabled) colors.onBrand else colors.textSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(AeShapes.card)
            .background(if (enabled) colors.brand else colors.border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_qr), contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
        Text(
            "Scan QR code on the battery pack",
            color = foreground,
            style = AeTheme.type.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Message(
    text: String,
    isError: Boolean = false,
    busy: Boolean = false,
    actionLabel: String = "OK",
    onAction: (() -> Unit)? = null,
) {
    val colors = AeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AeShapes.card)
            .background(if (isError) colors.errorBg else colors.surface)
            .border(1.dp, if (isError) colors.errorBorder else colors.border, AeShapes.card)
            .padding(start = 16.dp, end = if (onAction != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = colors.brand, strokeWidth = 2.dp)
        }
        Text(
            text,
            style = AeTheme.type.small,
            color = if (isError) colors.error else colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        if (onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = if (isError) colors.error else colors.brand) }
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
