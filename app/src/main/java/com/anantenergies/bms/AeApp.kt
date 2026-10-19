package com.anantenergies.bms

import android.app.Application
import android.content.Context
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.SettingsRepository
import com.anantenergies.bms.dev.DevTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AeApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** App-wide singletons. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val bms = BmsRepository(scope, settings, DevTools.demo)
}
