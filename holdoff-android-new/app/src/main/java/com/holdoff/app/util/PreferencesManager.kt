package com.holdoff.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.holdoff.app.ui.settings.AppPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "holdoff_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_USE_ON_DEVICE_AI = booleanPreferencesKey("use_on_device_ai")
        val KEY_AUTO_FILTER_TEXTS = booleanPreferencesKey("auto_filter_texts")
        val KEY_ANALYZE_INCOMING_CALLS = booleanPreferencesKey("analyze_incoming_calls")
        val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        val KEY_SCREEN_READER_OPTIMIZED = booleanPreferencesKey("screen_reader_optimized")
        val KEY_SPIRAL_LOCK_ENABLED = booleanPreferencesKey("spiral_lock_enabled")
        val KEY_SPIRAL_LOCK_MINUTES = intPreferencesKey("spiral_lock_minutes")
        val KEY_SAVE_HISTORY = booleanPreferencesKey("save_history")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_AI_DISCLOSURE_ACCEPTED = booleanPreferencesKey("ai_disclosure_accepted")
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_SPIRAL_COUNT = intPreferencesKey("spiral_count")
        val KEY_SPIRAL_LOCKED_UNTIL = longPreferencesKey("spiral_locked_until")
    }

    // ── Combined AppPrefs flow ────────────────────────────────────────────────

    val appPrefsFlow: Flow<AppPrefs> = dataStore.data.map { prefs ->
        AppPrefs(
            useOnDeviceAi = prefs[KEY_USE_ON_DEVICE_AI] ?: true,
            autoFilterTexts = prefs[KEY_AUTO_FILTER_TEXTS] ?: true,
            analyzeIncomingCalls = prefs[KEY_ANALYZE_INCOMING_CALLS] ?: false,
            fontScale = prefs[KEY_FONT_SIZE_SCALE] ?: 1.0f,
            highContrast = prefs[KEY_HIGH_CONTRAST] ?: false,
            hapticFeedback = prefs[KEY_HAPTIC_FEEDBACK] ?: true,
            screenReaderOptimized = prefs[KEY_SCREEN_READER_OPTIMIZED] ?: false,
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
            spiralLockEnabled = prefs[KEY_SPIRAL_LOCK_ENABLED] ?: true,
            spiralLockMinutes = prefs[KEY_SPIRAL_LOCK_MINUTES] ?: 30,
            saveHistory = prefs[KEY_SAVE_HISTORY] ?: true
        )
    }

    // ── Synchronous reads ─────────────────────────────────────────────────────

    val useOnDeviceAi: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_USE_ON_DEVICE_AI] ?: true }

    val highContrast: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_HIGH_CONTRAST] ?: false }

    val reduceMotion: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_REDUCE_MOTION] ?: false }

    val hapticFeedback: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_HAPTIC_FEEDBACK] ?: true }

    val fontSizeScale: Float
        get() = runBlocking { dataStore.data.first()[KEY_FONT_SIZE_SCALE] ?: 1.0f }

    val onboardingDone: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_ONBOARDING_DONE] ?: false }

    val aiDisclosureAccepted: Boolean
        get() = runBlocking { dataStore.data.first()[KEY_AI_DISCLOSURE_ACCEPTED] ?: false }

    val authToken: String?
        get() = runBlocking { dataStore.data.first()[KEY_AUTH_TOKEN] }

    // ── Reactive flows ────────────────────────────────────────────────────────

    val useOnDeviceAiFlow: Flow<Boolean> = dataStore.data.map { it[KEY_USE_ON_DEVICE_AI] ?: true }
    val highContrastFlow: Flow<Boolean> = dataStore.data.map { it[KEY_HIGH_CONTRAST] ?: false }
    val reduceMotionFlow: Flow<Boolean> = dataStore.data.map { it[KEY_REDUCE_MOTION] ?: false }
    val fontSizeScaleFlow: Flow<Float> = dataStore.data.map { it[KEY_FONT_SIZE_SCALE] ?: 1.0f }
    val spiralCountFlow: Flow<Int> = dataStore.data.map { it[KEY_SPIRAL_COUNT] ?: 0 }
    val spiralLockedUntilFlow: Flow<Long> = dataStore.data.map { it[KEY_SPIRAL_LOCKED_UNTIL] ?: 0L }

    // ── Setters ───────────────────────────────────────────────────────────────

    suspend fun setOnDeviceAi(value: Boolean) = dataStore.edit { it[KEY_USE_ON_DEVICE_AI] = value }
    suspend fun setAutoFilterTexts(value: Boolean) = dataStore.edit { it[KEY_AUTO_FILTER_TEXTS] = value }
    suspend fun setAnalyzeIncomingCalls(value: Boolean) = dataStore.edit { it[KEY_ANALYZE_INCOMING_CALLS] = value }
    suspend fun setHighContrast(value: Boolean) = dataStore.edit { it[KEY_HIGH_CONTRAST] = value }
    suspend fun setReduceMotion(value: Boolean) = dataStore.edit { it[KEY_REDUCE_MOTION] = value }
    suspend fun setHapticFeedback(value: Boolean) = dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = value }
    suspend fun setFontScale(value: Float) = dataStore.edit { it[KEY_FONT_SIZE_SCALE] = value }
    suspend fun setScreenReaderOptimized(value: Boolean) = dataStore.edit { it[KEY_SCREEN_READER_OPTIMIZED] = value }
    suspend fun setSpiralLockEnabled(value: Boolean) = dataStore.edit { it[KEY_SPIRAL_LOCK_ENABLED] = value }
    suspend fun setSpiralLockMinutes(value: Int) = dataStore.edit { it[KEY_SPIRAL_LOCK_MINUTES] = value }
    suspend fun setSaveHistory(value: Boolean) = dataStore.edit { it[KEY_SAVE_HISTORY] = value }
    suspend fun setFontSizeScale(value: Float) = dataStore.edit { it[KEY_FONT_SIZE_SCALE] = value }
    suspend fun setOnboardingDone(value: Boolean) = dataStore.edit { it[KEY_ONBOARDING_DONE] = value }
    suspend fun setAiDisclosureAccepted(value: Boolean) = dataStore.edit { it[KEY_AI_DISCLOSURE_ACCEPTED] = value }
    suspend fun setAuthToken(token: String?) = dataStore.edit {
        if (token != null) it[KEY_AUTH_TOKEN] = token else it.remove(KEY_AUTH_TOKEN)
    }

    suspend fun clearHistory() {
        // Clear conversation history keys — extend as needed
        dataStore.edit { prefs ->
            prefs[KEY_SPIRAL_COUNT] = 0
            prefs[KEY_SPIRAL_LOCKED_UNTIL] = 0L
        }
    }

    // ── Spiral lock ───────────────────────────────────────────────────────────

    suspend fun incrementSpiralCount(): Int {
        var newCount = 0
        dataStore.edit { prefs ->
            val current = prefs[KEY_SPIRAL_COUNT] ?: 0
            newCount = current + 1
            prefs[KEY_SPIRAL_COUNT] = newCount
            if (newCount >= 3) {
                val lockMinutes = prefs[KEY_SPIRAL_LOCK_MINUTES] ?: 30
                prefs[KEY_SPIRAL_LOCKED_UNTIL] = System.currentTimeMillis() + (lockMinutes * 60 * 1000L)
            }
        }
        return newCount
    }

    suspend fun resetSpiralCount() {
        dataStore.edit { prefs ->
            prefs[KEY_SPIRAL_COUNT] = 0
            prefs[KEY_SPIRAL_LOCKED_UNTIL] = 0L
        }
    }

    fun isSpiralLocked(): Boolean {
        val lockedUntil = runBlocking { dataStore.data.first()[KEY_SPIRAL_LOCKED_UNTIL] ?: 0L }
        return lockedUntil > System.currentTimeMillis()
    }

    fun spiralLockedUntilMs(): Long {
        return runBlocking { dataStore.data.first()[KEY_SPIRAL_LOCKED_UNTIL] ?: 0L }
    }
}
