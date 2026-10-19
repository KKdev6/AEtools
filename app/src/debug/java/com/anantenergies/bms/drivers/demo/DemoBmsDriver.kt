package com.anantenergies.bms.drivers.demo

import com.anantenergies.bms.dev.DemoControl
import com.anantenergies.bms.domain.Advertisement
import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsDriver
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration

/** Debug-only demo mode: one simulated pack plus the scenario picked from the demo banner. */
class DemoController : DemoControl {
    private val _scenario = MutableStateFlow(DemoScenario.NORMAL)
    val scenario: StateFlow<DemoScenario> = _scenario.asStateFlow()

    override val device = BmsDevice(name = "Demo pack", address = "00:00:00:00:00:00")

    fun select(scenario: DemoScenario) {
        _scenario.value = scenario
    }

    override fun createDriver(): BmsDriver {
        _scenario.value = DemoScenario.NORMAL
        return DemoBmsDriver(scenario)
    }
}

/** A [BmsDriver] backed by [DemoPackModel] instead of Bluetooth. */
class DemoBmsDriver(
    private val scenario: StateFlow<DemoScenario>,
    seed: Long = System.nanoTime(),
) : BmsDriver {
    private val model = DemoPackModel(seed)
    private val random = Random(seed)
    private var connected = false

    private val _linkState = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _info = MutableStateFlow<BmsInfo?>(null)
    override val info: StateFlow<BmsInfo?> = _info.asStateFlow()

    override fun matches(advertisement: Advertisement) = false

    override suspend fun connect(device: BmsDevice) {
        _linkState.value = LinkState.Connecting
        delay(700)
        _info.value = BmsInfo(brand = "Demo", model = "DB24SA08", chemistry = "LiFePO4")
        connected = true
        _linkState.value = LinkState.Connected(rssi = -62)
    }

    override fun snapshots(interval: Duration): Flow<BmsSnapshot> = flow {
        var last = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val active = scenario.value
            model.scenario = active
            model.step((now - last) / 1000.0)
            last = now
            if (connected) {
                if (active == DemoScenario.BLUETOOTH_DROPPED) {
                    _linkState.value = LinkState.Reconnecting(attempt = 1)
                } else {
                    _linkState.value = LinkState.Connected(rssi = -62 + random.nextInt(-3, 4))
                    emit(model.snapshot(now))
                }
            }
            // Wake early when the scenario changes so the screens react at once.
            withTimeoutOrNull(interval) { scenario.first { it != active } }
        }
    }

    override suspend fun disconnect() {
        connected = false
        _info.value = null
        _linkState.value = LinkState.Disconnected
    }
}
