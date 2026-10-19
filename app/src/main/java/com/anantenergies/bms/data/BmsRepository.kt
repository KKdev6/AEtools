package com.anantenergies.bms.data

import com.anantenergies.bms.dev.DemoControl
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsDriver
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.Duration.Companion.seconds

/** The BMS the app is talking to right now. */
class BmsSession(val device: BmsDevice, val driver: BmsDriver, val isDemo: Boolean)

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
    private val demo: DemoControl?,
) {
    private val _session = MutableStateFlow<BmsSession?>(null)
    val session: StateFlow<BmsSession?> = _session.asStateFlow()

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

    fun startDemo() {
        val demo = demo ?: return
        switchTo(BmsSession(demo.device, demo.createDriver(), isDemo = true))
    }

    fun disconnect() = switchTo(null)

    /** Disconnects and stops remembering the last device, so it won't auto-connect. */
    fun forget() {
        switchTo(null)
        scope.launch { settings.forgetDevice() }
    }

    private fun switchTo(session: BmsSession?) {
        val previous = _session.getAndUpdate { session }
        scope.launch {
            previous?.driver?.disconnect()
            session?.let { it.driver.connect(it.device) }
        }
    }

    private class Reading(val session: BmsSession, val snapshot: BmsSnapshot)

    private companion object {
        /** Keeps polling through short gaps such as screen rotation. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
