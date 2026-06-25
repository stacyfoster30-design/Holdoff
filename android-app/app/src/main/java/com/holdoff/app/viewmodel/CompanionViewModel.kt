package com.holdoff.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.holdoff.app.data.network.HoldOffApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromCompanion: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class CompanionUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeCompanion: String = "sadie",   // "sadie" | "dan"
    val isTyping: Boolean = false,
    val inputText: String = "",
    val errorMessage: String? = null
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    private val _state = MutableStateFlow(
        CompanionUiState(
            messages = listOf(
                ChatMessage(
                    text = "Hey love. I'm Sadie. 💜  I see patterns in your conversations that you might be missing. What's going on?",
                    isFromCompanion = true
                )
            )
        )
    )
    val state: StateFlow<CompanionUiState> = _state

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(text = text, isFromCompanion = false)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            inputText = "",
            isTyping = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val soulName = if (_state.value.activeCompanion == "dan") "Dan" else "Sadie"

            // Build history (role, content) — exclude the typing placeholder
            val history = _state.value.messages
                .filter { it.id != userMsg.id }
                .map { msg -> Pair(if (msg.isFromCompanion) "assistant" else "user", msg.text) }

            val result = HoldOffApi.companionChat(
                ctx = ctx,
                soulName = soulName,
                message = text,
                history = history
            )

            if (result.reply != null) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(
                        text = result.reply,
                        isFromCompanion = true
                    ),
                    isTyping = false
                )
            } else {
                // Fallback so the app doesn't go silent
                val fallback = when (soulName) {
                    "Dan"  -> "Something got in the way. Try again?"
                    else   -> "Lost my train of thought — try again?"
                }
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatMessage(
                        text = fallback,
                        isFromCompanion = true
                    ),
                    isTyping = false,
                    errorMessage = result.error
                )
            }
        }
    }

    fun updateInput(text: String) { _state.value = _state.value.copy(inputText = text) }

    fun switchCompanion(id: String) {
        val greeting = when (id) {
            "dan"  -> "What's going on. I'm listening."
            else   -> "Hey love. I'm Sadie. 💜  I see patterns in your conversations that you might be missing. What's going on?"
        }
        _state.value = CompanionUiState(
            activeCompanion = id,
            messages = listOf(ChatMessage(text = greeting, isFromCompanion = true))
        )
    }
}
