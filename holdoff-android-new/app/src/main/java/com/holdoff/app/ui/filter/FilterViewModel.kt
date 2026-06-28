package com.holdoff.app.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.holdoff.app.api.AiEngine
import com.holdoff.app.api.VerdictResult
import com.holdoff.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterUiState(
    val isLoading: Boolean = false,
    val result: VerdictResult? = null,
    val error: String? = null,
    val spiralCount: Int = 0,
    val spiralLocked: Boolean = false,
    val spiralLockedUntilMs: Long = 0L
)

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val aiEngine: AiEngine,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState

    init {
        viewModelScope.launch {
            // Check if spiral lock is active on init
            val locked = preferencesManager.isSpiralLocked()
            val lockedUntil = preferencesManager.spiralLockedUntilMs()
            val count = 0
            _uiState.update { it.copy(spiralLocked = locked, spiralLockedUntilMs = lockedUntil, spiralCount = count) }
        }
    }

    fun analyze(message: String, context: String?) {
        if (_uiState.value.spiralLocked) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, result = null, error = null) }
            try {
                val result = aiEngine.analyzeVerdict(message, context)
                // Handle spiral lock logic
                var spiralCount = _uiState.value.spiralCount
                var spiralLocked = false
                var spiralLockedUntilMs = 0L

                if (result.verdict == "DO NOT SEND") {
                    spiralCount = preferencesManager.incrementSpiralCount()
                    spiralLocked = preferencesManager.isSpiralLocked()
                    spiralLockedUntilMs = preferencesManager.spiralLockedUntilMs()
                } else if (result.verdict == "SEND") {
                    preferencesManager.resetSpiralCount()
                    spiralCount = 0
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        result = result,
                        spiralCount = spiralCount,
                        spiralLocked = spiralLocked,
                        spiralLockedUntilMs = spiralLockedUntilMs
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Analysis failed. Please try again.")
                }
            }
        }
    }
}
