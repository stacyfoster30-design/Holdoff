package com.holdoff.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromSadie: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class CompanionUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeCompanion: String = "sadie",  // "sadie" | "ai_stacy" | "ai_danny"
    val isTyping: Boolean = false,
    val inputText: String = ""
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        CompanionUiState(
            messages = listOf(
                ChatMessage(
                    text = "Hey love. I'm Sadie. \uD83D\uDC9C  I see patterns in your conversations that you might be missing. What's going on?",
                    isFromSadie = true
                )
            )
        )
    )
    val state: StateFlow<CompanionUiState> = _state

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(text = text, isFromSadie = false)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            inputText = "",
            isTyping = true
        )
        viewModelScope.launch {
            delay(1500)
            val reply = generateReply(text, _state.value.activeCompanion)
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatMessage(text = reply, isFromSadie = true),
                isTyping = false
            )
        }
    }

    fun updateInput(text: String) { _state.value = _state.value.copy(inputText = text) }
    fun switchCompanion(id: String) { _state.value = _state.value.copy(activeCompanion = id) }

    /** TODO: call shouldiholdoff.live/api/companion. */
    private fun generateReply(input: String, companion: String): String = when (companion) {
        "ai_danny" -> "I hear you. I'm still figuring this out too \u2014 but I know pulling away isn't the answer. What are you actually feeling right now?"
        "ai_stacy" -> "I wanted to reach out but I couldn't make myself do it. The silence felt safer. Does that make sense?"
        else -> "That sounds heavy. Before you do anything \u2014 let's slow down. What does your gut actually want here?"
    }
}
