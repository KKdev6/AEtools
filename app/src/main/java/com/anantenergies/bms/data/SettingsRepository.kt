package com.anantenergies.bms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anantenergies.bms.domain.BmsDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class TempUnit { CELSIUS, FAHRENHEIT }

data class Settings(
    val autoConnect: Boolean = true,
    val refreshSeconds: Int = 2,
    val theme: ThemeMode = ThemeMode.DARK,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    /** The last BMS connected to, for auto-connect. */
    val lastDevice: BmsDevice? = null,
) {
    companion object {
        val REFRESH_CHOICES = listOf(1, 2, 5)
    }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsStore)

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun setAutoConnect(on: Boolean) {
        store.edit { it[AUTO_CONNECT] = on }
    }

    suspend fun setRefreshSeconds(seconds: Int) {
        require(seconds in Settings.REFRESH_CHOICES)
        store.edit { it[REFRESH_SECONDS] = seconds }
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setTempUnit(unit: TempUnit) {
        store.edit { it[TEMP_UNIT] = unit.name }
    }

    suspend fun rememberDevice(device: BmsDevice) {
        store.edit {
            it[LAST_DEVICE_NAME] = device.name
            it[LAST_DEVICE_ADDRESS] = device.address
            val brandId = device.brandId
            if (brandId != null) it[LAST_DEVICE_BRAND] = brandId else it.remove(LAST_DEVICE_BRAND)
        }
    }

    suspend fun forgetDevice() {
        store.edit {
            it.remove(LAST_DEVICE_NAME)
            it.remove(LAST_DEVICE_ADDRESS)
            it.remove(LAST_DEVICE_BRAND)
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        val name = this[LAST_DEVICE_NAME]
        val address = this[LAST_DEVICE_ADDRESS]
        return Settings(
            autoConnect = this[AUTO_CONNECT] ?: defaults.autoConnect,
            refreshSeconds = this[REFRESH_SECONDS]?.takeIf { it in Settings.REFRESH_CHOICES } ?: defaults.refreshSeconds,
            theme = enumOrNull<ThemeMode>(this[THEME]) ?: defaults.theme,
            tempUnit = enumOrNull<TempUnit>(this[TEMP_UNIT]) ?: defaults.tempUnit,
            lastDevice = if (name != null && address != null) BmsDevice(name, address, this[LAST_DEVICE_BRAND]) else null,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private companion object {
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        val THEME = stringPreferencesKey("theme")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_BRAND = stringPreferencesKey("last_device_brand")
    }
}
