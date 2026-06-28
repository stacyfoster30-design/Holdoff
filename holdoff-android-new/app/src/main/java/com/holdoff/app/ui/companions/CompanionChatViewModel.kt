package com.holdoff.app.ui.companions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.holdoff.app.api.AiEngine
import com.holdoff.app.api.CompanionMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanionChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val currentExpression: String = "neutral",  // "neutral" | "happy" | "thinking"
    val error: String? = null
)

@HiltViewModel
class CompanionChatViewModel @Inject constructor(
    private val aiEngine: AiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionChatUiState())
    val uiState: StateFlow<CompanionChatUiState> = _uiState

    fun sendMessage(persona: String, userMessage: String) {
        if (userMessage.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = userMessage)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg,
                isTyping = true,
                currentExpression = "thinking"
            )
        }

        viewModelScope.launch {
            try {
                val history = _uiState.value.messages
                    .dropLast(1) // exclude the message we just added
                    .map { CompanionMessage(role = it.role, content = it.content) }

                val response = aiEngine.companionChat(
                    persona = persona,
                    userMessage = userMessage,
                    history = history
                )

                val expression = detectExpression(response)
                val assistantMsg = ChatMessage(role = "assistant", content = response)

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + assistantMsg,
                        isTyping = false,
                        currentExpression = expression
                    )
                }

                // Reset to neutral after 3 seconds
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(currentExpression = "neutral") }

            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isTyping = false,
                        currentExpression = "neutral",
                        error = "Couldn't reach ${persona}. Try again."
                    )
                }
            }
        }
    }

    /**
     * Detect the emotional expression to show based on the AI response content.
     * Returns "happy", "thinking", or "neutral".
     */
    private fun detectExpression(response: String): String {
        val lower = response.lowercase()

        // Happy/positive signals
        val happyKeywords = listOf(
            "proud", "great", "amazing", "love", "celebrate", "win", "yes!", "exactly",
            "that's it", "you got this", "beautiful", "wonderful", "congrats", "haha",
            "lol", "😊", "❤️", "✨", "🎉", "hell yes", "absolutely"
        )

        // Thinking/concerned signals
        val thinkingKeywords = listOf(
            "hmm", "interesting", "let me think", "i wonder", "curious", "consider",
            "what if", "but", "however", "wait", "actually", "careful", "notice",
            "pattern", "attachment", "anxious", "avoidant", "spiral", "concern",
            "worried", "difficult", "hard", "pain", "hurt", "cry", "sad"
        )

        val happyScore = happyKeywords.count { lower.contains(it) }
        val thinkingScore = thinkingKeywords.count { lower.contains(it) }

        return when {
            happyScore > thinkingScore && happyScore > 0 -> "happy"
            thinkingScore > 0 -> "thinking"
            else -> "neutral"
        }
    }
}
