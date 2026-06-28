package com.holdoff.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.holdoff.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppPrefs(
    val useOnDeviceAi: Boolean = true,
    val autoFilterTexts: Boolean = true,
    val analyzeIncomingCalls: Boolean = false,
    val fontScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
    val screenReaderOptimized: Boolean = false,
    val reduceMotion: Boolean = false,
    val spiralLockEnabled: Boolean = true,
    val spiralLockMinutes: Int = 30,
    val saveHistory: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val prefs: StateFlow<AppPrefs> = preferencesManager.appPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())

    fun setOnDeviceAi(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setOnDeviceAi(enabled)
    }

    fun setAutoFilterTexts(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setAutoFilterTexts(enabled)
    }

    fun setAnalyzeIncomingCalls(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setAnalyzeIncomingCalls(enabled)
    }

    fun setFontScale(scale: Float) = viewModelScope.launch {
        preferencesManager.setFontScale(scale)
    }

    fun setHighContrast(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setHighContrast(enabled)
    }

    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setHapticFeedback(enabled)
    }

    fun setScreenReaderOptimized(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setScreenReaderOptimized(enabled)
    }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setReduceMotion(enabled)
    }

    fun setSpiralLockEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setSpiralLockEnabled(enabled)
    }

    fun setSpiralLockMinutes(minutes: Int) = viewModelScope.launch {
        preferencesManager.setSpiralLockMinutes(minutes)
    }

    fun setSaveHistory(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setSaveHistory(enabled)
    }

    fun clearHistory() = viewModelScope.launch {
        preferencesManager.clearHistory()
    }
}
