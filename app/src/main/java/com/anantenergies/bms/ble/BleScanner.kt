package com.anantenergies.bms.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.anantenergies.bms.domain.Advertisement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ScanFailedException(val errorCode: Int) : Exception("Scan failed ($errorCode)")

/** Bluetooth LE scanning. Callers must hold the scan permission (see [BluetoothAvailability]). */
@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {

    /** Every advertisement heard while collected. Fails with [ScanFailedException]. */
    fun scan(): Flow<Advertisement> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(ScanFailedException(-1))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toAdvertisement())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toAdvertisement()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailedException(errorCode))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        // Stopping fails if Bluetooth was turned off meanwhile; nothing to do then.
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun ScanResult.toAdvertisement() = Advertisement(
        name = scanRecord?.deviceName ?: runCatching { device.name }.getOrNull(),
        address = device.address,
        rssi = rssi,
        serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() }.orEmpty(),
    )
}
