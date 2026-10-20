package com.anantenergies.bms.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anantenergies.bms.AeApp
import com.anantenergies.bms.AppContainer
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.data.Settings
import com.anantenergies.bms.data.SettingsRepository
import com.anantenergies.bms.data.TempUnit
import com.anantenergies.bms.domain.BmsInfo
import com.anantenergies.bms.domain.BmsSnapshot
import com.anantenergies.bms.domain.LinkState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A view model built from the app's singletons. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as AeApp).container) }
    })

private const val STOP_TIMEOUT_MS = 5_000L

class MainViewModel(settings: SettingsRepository, private val bms: BmsRepository) : ViewModel() {
    /** Null until the stored settings have loaded. */
    val settings: StateFlow<Settings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The address of the demo pack or simulated board while one is connected, for the demo banner. */
    val demoAddress: StateFlow<String?> = bms.session
        .map { session -> session?.takeIf { it.isDemo }?.device?.address }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun exitDemo() = bms.disconnect()
}

/** Everything the Dashboard and Cells screens show. */
data class LiveState(
    val hasSession: Boolean = false,
    val deviceName: String? = null,
    val link: LinkState = LinkState.Disconnected,
    val info: BmsInfo? = null,
    val snapshot: BmsSnapshot? = null,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
)

class LiveViewModel(bms: BmsRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<LiveState> = combine(
        bms.session, bms.linkState, bms.info, bms.snapshot, settings.settings,
    ) { session, link, info, snapshot, prefs ->
        LiveState(session != null, session?.device?.name, link, info, snapshot, prefs.tempUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveState())
}
