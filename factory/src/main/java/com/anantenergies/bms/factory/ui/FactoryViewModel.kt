package com.anantenergies.bms.factory.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anantenergies.bms.ble.AndroidGattLinkFactory
import com.anantenergies.bms.ble.BleScanner
import com.anantenergies.bms.ble.BluetoothAvailability
import com.anantenergies.bms.ble.BluetoothStatus
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.drivers.jbd.JbdBrand
import com.anantenergies.bms.factory.dev.FactoryDev
import com.anantenergies.bms.factory.flow.Board
import com.anantenergies.bms.factory.flow.FactorySession
import com.anantenergies.bms.factory.flow.PackDetails
import com.anantenergies.bms.factory.flow.Step
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A board heard while scanning. */
data class ScannedBoard(val board: Board, val rssi: Int, val recognised: Boolean)

class FactoryViewModel(application: Application) : AndroidViewModel(application) {
    private val availability = BluetoothAvailability(application)
    private val scanner = BleScanner(application)

    /** Raw Bluetooth traffic, so a first real apply can be checked. Internal app: kept in all builds. */
    val log = TrafficLog()

    private val session = FactorySession(
        scope = viewModelScope,
        links = FactoryDev.linkFactory(AndroidGattLinkFactory(application), viewModelScope),
        log = log,
        onPowerCycle = FactoryDev::powerCycle,
    )
    val step: StateFlow<Step> = session.step

    private val _status = MutableStateFlow(availability.status())
    val status: StateFlow<BluetoothStatus> = _status.asStateFlow()

    private val _boards = MutableStateFlow<List<ScannedBoard>>(emptyList())
    val boards: StateFlow<List<ScannedBoard>> = _boards.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _busy = MutableStateFlow(false)

    val requiredPermissions: Array<String> get() = availability.requiredPermissions

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            availability.changes().collect { refreshStatus() }
        }
    }

    fun refreshStatus() {
        _status.value = availability.status()
        if (_status.value == BluetoothStatus.READY && step.value == Step.ChooseBoard && scanJob == null) startScan()
        if (_status.value != BluetoothStatus.READY) stopScan()
    }

    fun startScan() {
        if (_status.value != BluetoothStatus.READY) return
        stopScan()
        _boards.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { log.error("Scan failed: ${it.message}") }
                .collect(::heard)
            _scanning.value = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    private fun heard(advertisement: Advertisement) {
        val board = ScannedBoard(
            board = Board(name = advertisement.name ?: "No name", address = advertisement.address),
            rssi = advertisement.rssi,
            recognised = JbdBrand.matches(advertisement),
        )
        _boards.update { list -> (list.filterNot { it.board.address == board.board.address } + board).sortedByDescending { it.rssi } }
    }

    fun connect(board: Board) = launchOne {
        stopScan()
        session.connect(board)
    }

    fun review(details: PackDetails) = launchOne { session.review(details) }

    fun editDetails() = session.editDetails()

    fun apply() = launchOne { session.apply() }

    fun readBack() = launchOne { session.readBack() }

    fun startPowerCycle() = session.startPowerCycle()

    fun verify() = launchOne { session.verifyAfterPowerCycle() }

    fun retry() = launchOne { session.retry() }

    fun reset() {
        session.reset()
        startScan()
    }

    /** One action at a time: taps while one is running are ignored. */
    private fun launchOne(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                _busy.value = false
            }
        }
    }

    override fun onCleared() {
        session.reset()
    }
}
