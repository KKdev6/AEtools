package com.anantenergies.bms.data

import com.anantenergies.bms.dev.DemoControl
import com.anantenergies.bms.domain.BmsBrand
import com.anantenergies.bms.domain.BmsConnectException
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsDriver
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/** The BMS the app is talking to right now. [isDemo] covers demo mode and the simulated board. */
class BmsSession(val device: BmsDevice, val driver: BmsDriver, val isDemo: Boolean)

/** Progress of the latest connection the user started. */
sealed interface ConnectStatus {
    val device: BmsDevice

    data class Connecting(override val device: BmsDevice) : ConnectStatus
    data class Failed(override val device: BmsDevice, val message: String) : ConnectStatus
}

/**
 * Owns the active driver and runs polling.
 *
 * Polling only runs while something collects [snapshot], which the screens do while they are
 * visible, so the BMS isn't polled with the app in the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BmsRepository(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    /** Every supported brand, in the order they are tried for an unrecognised device. */
    private val brands: List<BmsBrand>,
    private val demo: DemoControl?,
    /** True for the debug build's simulated board, which is never remembered. */
    private val isSimulated: (address: String) -> Boolean,
) {
    private val _session = MutableStateFlow<BmsSession?>(null)
    val session: StateFlow<BmsSession?> = _session.asStateFlow()

    private val _connectStatus = MutableStateFlow<ConnectStatus?>(null)
    val connectStatus: StateFlow<ConnectStatus?> = _connectStatus.asStateFlow()

    private var connectJob: Job? = null

    val linkState: StateFlow<LinkState> = _session
        .flatMapLatest { it?.driver?.linkState ?: flowOf(LinkState.Disconnected) }
        .stateIn(scope, SharingStarted.Eagerly, LinkState.Disconnected)

    val info: StateFlow<BmsInfo?> = _session
        .flatMapLatest { it?.driver?.info ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val interval = settings.settings.map { it.refreshSeconds.seconds }.distinctUntilChanged()

    private val readings: StateFlow<Reading?> = combine(_session, interval, ::Pair)
        .flatMapLatest { (session, interval) ->
            session?.driver?.snapshots(interval)?.map { Reading(session, it) } ?: flowOf(null)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    /** Latest reading from the current session; null until the first one arrives. */
    val snapshot: StateFlow<BmsSnapshot?> = combine(_session, readings) { session, reading ->
        reading?.takeIf { it.session === session }?.snapshot
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Connects to a scanned or remembered device. A device a brand recognised uses that brand;
     * otherwise each brand is tried until one finds its service on the board.
     */
    fun connect(device: BmsDevice) {
        connectJob?.cancel()
        connectJob = scope.launch {
            endSession()
            _connectStatus.value = ConnectStatus.Connecting(device)
            val candidates = brands.filter { it.id == device.brandId }.ifEmpty { brands }
            for (brand in candidates) {
                val session = BmsSession(device.copy(brandId = brand.id), brand.create(), isSimulated(device.address))
                _session.value = session
                try {
                    session.driver.connect(session.device)
                    _connectStatus.value = null
                    if (!session.isDemo) settings.rememberDevice(session.device)
                    return@launch
                } catch (e: BmsConnectException.NotSupported) {
                    endSession()
                } catch (e: BmsConnectException.Unreachable) {
                    endSession()
                    _connectStatus.value = ConnectStatus.Failed(
                        device,
                        "Couldn't connect to ${device.name}. Make sure the BMS is awake and nearby, then try again.",
                    )
                    return@launch
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { session.driver.disconnect() }
                    throw e
                }
            }
            _connectStatus.value = ConnectStatus.Failed(device, "${device.name} isn't a supported BMS.")
        }
    }

    fun clearConnectStatus() {
        _connectStatus.value = null
    }

    fun startDemo() {
        val demo = demo ?: return
        connectJob?.cancel()
        _connectStatus.value = null
        val session = BmsSession(demo.device, demo.createDriver(), isDemo = true)
        val previous = _session.getAndUpdate { session }
        scope.launch {
            previous?.driver?.disconnect()
            session.driver.connect(session.device)
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        _connectStatus.value = null
        scope.launch { endSession() }
    }

    /** Disconnects and stops remembering the last device, so it won't auto-connect. */
    fun forget() {
        disconnect()
        scope.launch { settings.forgetDevice() }
    }

    private suspend fun endSession() {
        _session.getAndUpdate { null }?.driver?.disconnect()
    }

    private class Reading(val session: BmsSession, val snapshot: BmsSnapshot)

    private companion object {
        /** Keeps polling through short gaps such as screen rotation. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
