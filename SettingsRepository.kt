package com.painite.keyboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "painite_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val KEY_ROWBAR_ORDER = stringPreferencesKey("rowbar_order")
        val KEY_ROWBAR_ENABLED = stringPreferencesKey("rowbar_enabled")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_VIBRATE = booleanPreferencesKey("vibrate")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val KEY_SHOW_SYMBOLS_ROW = booleanPreferencesKey("show_symbols_row")

        const val DEFAULT_ROWBAR_ORDER = "voice,translate,clipboard_clear,paste,copy,select_all,clipboard,cut,number_toggle,emoji,switch_keyboard"
    }

    private val safeData = context.dataStore.data.catch { emit(emptyPreferences()) }

    val theme: Flow<String> = safeData.map { it[KEY_THEME] ?: "ice_white" }
    val showNumberRow: Flow<Boolean> = safeData.map { it[KEY_SHOW_NUMBER_ROW] ?: true }
    val rowbarOrder: Flow<String> = safeData.map { it[KEY_ROWBAR_ORDER] ?: DEFAULT_ROWBAR_ORDER }
    val rowbarEnabled: Flow<String> = safeData.map { it[KEY_ROWBAR_ENABLED] ?: DEFAULT_ROWBAR_ORDER }
    val language: Flow<String> = safeData.map { it[KEY_LANGUAGE] ?: "EN" }
    val vibrate: Flow<Boolean> = safeData.map { it[KEY_VIBRATE] ?: true }
    val sound: Flow<Boolean> = safeData.map { it[KEY_SOUND] ?: false }
    val autoCapitalize: Flow<Boolean> = safeData.map { it[KEY_AUTO_CAPITALIZE] ?: true }

    suspend fun setTheme(theme: String) = context.dataStore.edit { it[KEY_THEME] = theme }
    suspend fun setShowNumberRow(show: Boolean) = context.dataStore.edit { it[KEY_SHOW_NUMBER_ROW] = show }
    suspend fun setRowbarOrder(order: String) = context.dataStore.edit { it[KEY_ROWBAR_ORDER] = order }
    suspend fun setRowbarEnabled(enabled: String) = context.dataStore.edit { it[KEY_ROWBAR_ENABLED] = enabled }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    suspend fun setVibrate(v: Boolean) = context.dataStore.edit { it[KEY_VIBRATE] = v }
    suspend fun setSound(s: Boolean) = context.dataStore.edit { it[KEY_SOUND] = s }
    suspend fun setAutoCapitalize(a: Boolean) = context.dataStore.edit { it[KEY_AUTO_CAPITALIZE] = a }
    suspend fun resetRowbarOrder() = context.dataStore.edit { it[KEY_ROWBAR_ORDER] = DEFAULT_ROWBAR_ORDER }
    suspend fun resetRowbarButtons() = context.dataStore.edit {
        it[KEY_ROWBAR_ORDER] = DEFAULT_ROWBAR_ORDER
        it[KEY_ROWBAR_ENABLED] = DEFAULT_ROWBAR_ORDER
    }
}
