package com.jarvis.ai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("jarvis_settings")

data class Prefs(
    val loaded: Boolean = false,
    val language: String = "en-US",
    val saveHistory: Boolean = false,
    val consentAsked: Boolean = false,
    val background: Boolean = false,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f
)

class SettingsStore(private val ctx: Context) {
    private val LANG = stringPreferencesKey("lang")
    private val SAVE = booleanPreferencesKey("save_history")
    private val ASKED = booleanPreferencesKey("consent_asked")
    private val BG = booleanPreferencesKey("background")
    private val RATE = floatPreferencesKey("rate")
    private val PITCH = floatPreferencesKey("pitch")

    val flow = ctx.ds.data.map {
        Prefs(true, it[LANG] ?: "en-US", it[SAVE] ?: false, it[ASKED] ?: false, it[BG] ?: false, it[RATE] ?: 1.0f, it[PITCH] ?: 1.0f)
    }

    suspend fun setLanguage(v: String) { ctx.ds.edit { it[LANG] = v } }
    suspend fun setSaveHistory(v: Boolean) { ctx.ds.edit { it[SAVE] = v; it[ASKED] = true } }
    suspend fun setConsentAsked() { ctx.ds.edit { it[ASKED] = true } }
    suspend fun setRate(v: Float) { ctx.ds.edit { it[RATE] = v } }
    suspend fun setPitch(v: Float) { ctx.ds.edit { it[PITCH] = v } }
    suspend fun setBackground(v: Boolean) { ctx.ds.edit { it[BG] = v } }
}
