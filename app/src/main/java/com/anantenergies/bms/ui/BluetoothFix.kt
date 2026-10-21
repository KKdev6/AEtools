package com.anantenergies.bms.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.anantenergies.bms.ble.BluetoothStatus

/** What's stopping the app from using Bluetooth, and the button that fixes it (none if it can't be fixed). */
data class BluetoothFix(val title: String, val message: String, val actionLabel: String?, val action: () -> Unit)

/**
 * The fix for [status], or null when Bluetooth is ready. [onChanged] runs after the user comes back
 * from a permission prompt or the "turn on Bluetooth" dialog, to re-check.
 */
@Composable
fun rememberBluetoothFix(status: BluetoothStatus, permissions: Array<String>, onChanged: () -> Unit): BluetoothFix? {
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

    return when (status) {
        BluetoothStatus.NEEDS_PERMISSION -> BluetoothFix(
            "Allow Bluetooth access",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "AEtools needs permission to find and connect to your BMS over Bluetooth."
            } else {
                "This Android version only lets apps find Bluetooth devices with location permission. AEtools doesn't use your location."
            },
            if (deniedForGood) "Open app settings" else "Allow",
        ) {
            if (deniedForGood) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            } else {
                askPermissions.launch(permissions)
            }
        }
        BluetoothStatus.BLUETOOTH_OFF -> BluetoothFix(
            "Bluetooth is off",
            "Turn on Bluetooth to find your BMS.",
            "Turn on Bluetooth",
        ) { turnOnBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        BluetoothStatus.LOCATION_OFF -> BluetoothFix(
            "Location is off",
            "On this Android version, Bluetooth scanning only works with Location switched on.",
            "Open location settings",
        ) { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        BluetoothStatus.UNSUPPORTED -> BluetoothFix(
            "Bluetooth LE not available",
            "This phone doesn't support Bluetooth Low Energy, which the BMS uses.",
            null,
        ) {}
        BluetoothStatus.READY -> null
    }
}
