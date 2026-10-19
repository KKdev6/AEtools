package com.anantenergies.bms.dev

import com.anantenergies.bms.domain.BmsDevice
import com.anantenergies.bms.domain.BmsDriver

/**
 * Demo mode. Only debug builds provide one (see `DevTools` in the debug and release source sets),
 * so release builds contain no demo driver and no simulated data.
 */
interface DemoControl {
    val device: BmsDevice

    fun createDriver(): BmsDriver
}
