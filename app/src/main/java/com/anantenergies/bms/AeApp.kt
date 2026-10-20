package com.anantenergies.bms

import android.app.Application
import android.content.Context
import com.anantenergies.bms.ble.AndroidGattLinkFactory
import com.anantenergies.bms.ble.BleScanner
import com.anantenergies.bms.ble.BluetoothAvailability
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.SettingsRepository
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.domain.BmsBrand
import com.anantenergies.bms.drivers.jbd.JbdBrand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AeApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** App-wide singletons. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val links = DevTools.linkFactory(AndroidGattLinkFactory(context))

    val settings = SettingsRepository(context)

    /** The driver registry. JK will be one more line here. */
    val brands: List<BmsBrand> = listOf(
        JbdBrand(scope, links, DevTools.trafficLog),
    )

    val bms = BmsRepository(scope, settings, brands, DevTools.demo, DevTools::isSimulated)
    val scanner = BleScanner(context)
    val bluetooth = BluetoothAvailability(context)
}
