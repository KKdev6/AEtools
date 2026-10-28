package com.anantenergies.bms.factory.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.factorySettings by preferencesDataStore(name = "factory_settings")

/** The operator's name, remembered between packs so it's typed once per shift. */
class OperatorStore(private val context: Context) {
    val operator: Flow<String> = context.factorySettings.data.map { it[OPERATOR].orEmpty() }

    suspend fun setOperator(name: String) {
        context.factorySettings.edit { it[OPERATOR] = name.trim() }
    }

    private companion object {
        val OPERATOR = stringPreferencesKey("operator")
    }
}
