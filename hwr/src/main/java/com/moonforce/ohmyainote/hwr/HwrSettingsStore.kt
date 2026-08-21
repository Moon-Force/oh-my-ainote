package com.moonforce.ohmyainote.hwr

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hwrPreferences by preferencesDataStore("hwr_settings")

class HwrSettingsStore(private val context: Context) {
    suspend fun isEnabled(): Boolean = context.hwrPreferences.data.hwrEnabled().first()

    suspend fun setEnabled(enabled: Boolean) {
        context.hwrPreferences.edit { preferences ->
            preferences[HwrPreferencesKeys.ENABLED] = enabled
        }
    }
}

internal object HwrPreferencesKeys {
    val ENABLED = booleanPreferencesKey("handwriting_recognition_enabled")
}

internal fun Flow<Preferences>.hwrEnabled(): Flow<Boolean> = map { it[HwrPreferencesKeys.ENABLED] == true }
