package com.anantenergies.bms.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class AndroidGattLinkFactory(private val context: Context) : GattLinkFactory {
    override fun create(address: String, profile: GattProfile): GattLink =
        AndroidGattLink(context.applicationContext, address, profile)
}

/**
 * [GattLink] on Android's BluetoothGatt. GATT operations run one at a time, each waiting for its
 * callback. Callers must hold BLUETOOTH_CONNECT (Android 12+) before connecting.
 */
@SuppressLint("MissingPermission")
class AndroidGattLink(
    private val context: Context,
    private val address: String,
    private val profile: GattProfile,
) : GattLink {

    private val _chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val chunks: Flow<ByteArray> = _chunks.asSharedFlow()

    private val _dropped = MutableStateFlow(false)
    override val dropped: StateFlow<Boolean> = _dropped.asStateFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val connected = CompletableDeferred<Boolean>()
    private val operations = Mutex()
    @Volatile private var pending: CompletableDeferred<Int>? = null
    @Volatile private var pendingRssi: CompletableDeferred<Int?>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Disconnected from $address (status $status)")
                connected.complete(false)
                pending?.complete(BluetoothGatt.GATT_FAILURE)
                pendingRssi?.complete(null)
                markDropped()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            pending?.complete(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            pendingRssi?.complete(if (status == BluetoothGatt.GATT_SUCCESS) rssi else null)
        }

        // Android 13+.
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == profile.notify) _chunks.tryEmit(value.copyOf())
        }

        // Android 12 and older.
        @Deprecated("Replaced by the overload with value on Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == profile.notify) {
                characteristic.value?.let { _chunks.tryEmit(it.copyOf()) }
            }
        }
    }

    override suspend fun connect(): GattServices {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw LinkException("This phone has no Bluetooth")
        if (!adapter.isEnabled) throw LinkException("Bluetooth is off")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw LinkException("Invalid address $address")
        }
        // Deprecated in API 37 in favour of a settings-object overload that older Android doesn't have;
        // on 37 this forwards to it.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw LinkException("Couldn't start a connection")

        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: false
        if (!ok) throw LinkException("No answer from the BMS")

        val discovery = operation("discover services") { it.discoverServices() }
        if (discovery != BluetoothGatt.GATT_SUCCESS) throw LinkException("Service discovery failed ($discovery)")
        val gatt = requireGatt()
        val services: GattServices = gatt.services.associate { service -> service.uuid to service.characteristics.map { it.uuid } }
        Log.i(TAG, "Services on $address: ${services.describe()}")

        val service = gatt.getService(profile.service) ?: throw GattServiceMissing(services)
        val notify = service.getCharacteristic(profile.notify) ?: throw GattServiceMissing(services)
        writeCharacteristic = service.getCharacteristic(profile.write) ?: throw GattServiceMissing(services)

        if (!gatt.setCharacteristicNotification(notify, true)) throw LinkException("Couldn't turn on notifications")
        val cccd = notify.getDescriptor(CCCD) ?: throw LinkException("Notify characteristic has no CCCD")
        val enable = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val status = operation("enable notifications") { writeDescriptor(it, cccd, enable) }
        if (status != BluetoothGatt.GATT_SUCCESS) throw LinkException("Couldn't turn on notifications ($status)")
        return services
    }

    override suspend fun write(bytes: ByteArray) {
        val characteristic = writeCharacteristic ?: throw LinkException("Not connected")
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val status = operation("write") { writeCharacteristic(it, characteristic, bytes, type) }
        if (status != BluetoothGatt.GATT_SUCCESS) throw LinkException("Write failed ($status)")
    }

    override suspend fun readRssi(): Int? = operations.withLock {
        val gatt = gatt ?: return null
        if (_dropped.value) return null
        val result = CompletableDeferred<Int?>()
        pendingRssi = result
        try {
            if (!gatt.readRemoteRssi()) return null
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) { result.await() }
        } finally {
            pendingRssi = null
        }
    }

    override fun close() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        markDropped()
    }

    private fun markDropped() {
        _dropped.value = true
        pending?.complete(BluetoothGatt.GATT_FAILURE)
    }

    private fun requireGatt(): BluetoothGatt = gatt ?: throw LinkException("Not connected")

    /** Starts one GATT operation and waits for its callback; returns the GATT status. */
    private suspend fun operation(name: String, start: (BluetoothGatt) -> Boolean): Int = operations.withLock {
        if (_dropped.value) throw LinkException("Link dropped")
        val gatt = requireGatt()
        val result = CompletableDeferred<Int>()
        pending = result
        try {
            if (!start(gatt)) throw LinkException("Couldn't $name")
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) { result.await() } ?: throw LinkException("No answer to $name")
        } finally {
            pending = null
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        type: Int,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = type
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        }

    private companion object {
        const val TAG = "AndroidGattLink"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val OPERATION_TIMEOUT_MS = 3_000L
        val CCCD: UUID = uuid16(0x2902)
    }
}
