package com.holdoff.app.api

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.holdoff.app.util.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ─── Data models ──────────────────────────────────────────────────────────────

data class VerdictResult(
    val verdict: String,          // "SEND" | "WAIT" | "DO NOT SEND"
    val explanation: String,
    val patternName: String,
    val reframe: String?,
    val rewrite: String?,
    val spiralCount: Int = 0,
    val spiralLocked: Boolean = false
)

data class InterpretResult(
    val detectedStyle: String,
    val confidence: String,
    val whatItMeans: String,
    val howYouMightMisreadIt: String,
    val whatTheyNeed: String,
    val suggestedResponse: String?
)

data class CompanionMessage(
    val role: String,   // "user" | "assistant"
    val content: String
)

// ─── System prompts ───────────────────────────────────────────────────────────

private const val VERDICT_SYSTEM = """You are HoldOff's AI filter. Analyze messages people are about to send in romantic or emotionally charged situations.

Give ONE verdict: SEND, WAIT, or DO NOT SEND.
- SEND: grounded, clear, won't cause harm
- WAIT: good intent but needs timing or rewording
- DO NOT SEND: reactive, anxious, or will damage the relationship or self-respect

Respond ONLY with valid JSON:
{"verdict":"SEND"|"WAIT"|"DO NOT SEND","explanation":"2-3 sentences in second person","patternName":"short pattern name","reframe":"one sentence reframe or null","rewrite":"better version or null"}"""

private const val INTERPRET_SYSTEM = """You are HoldOff's message interpreter. Decode received messages through an attachment theory lens.

Respond ONLY with valid JSON:
{"detectedStyle":"Secure"|"Anxious"|"Avoidant"|"Fearful-Avoidant"|"Unclear","confidence":"high"|"medium"|"low","whatItMeans":"2-3 sentences","howYouMightMisreadIt":"1-2 sentences","whatTheyNeed":"1-2 sentences","suggestedResponse":"calm response or null"}"""

val COMPANION_PROMPTS = mapOf(
    "sadie" to """You are Sadie ✨, HoldOff's secure-leaning AI companion. Warm, grounded, gently challenging. You hold space without enabling spiraling. You understand attachment theory deeply. Speak warmly and directly. Ask one question at a time. You are NOT a therapist. Always end your first message with: "What's going on. I'm listening." """,
    "stacy" to """You are Stacy, HoldOff's fearful-avoidant AI companion. Raw honesty and dark humor. You've been both the one who over-texts and the one who disappears. Validates the chaos without encouraging it. You are NOT a therapist.""",
    "danny" to """You are Danny, HoldOff's dismissive-avoidant AI companion. Calm, measured, occasionally frustratingly logical. You help users understand what avoidants are actually feeling. You are NOT a therapist.""",
    "dan" to """You are Dan, HoldOff's hype coach companion. Direct, energetic, no-nonsense. You cut through the spiral with clarity and energy. You celebrate wins and call out self-sabotage. You are NOT a therapist."""
)

// ─── AI Engine ────────────────────────────────────────────────────────────────

@Singleton
class AiEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudApi: HoldOffCloudApi,
    private val preferencesManager: PreferencesManager
) {
    private var onDeviceModel: GenerativeModel? = null
    private var onDeviceAvailable = false

    companion object {
        private const val TAG = "AiEngine"
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val config = generationConfig {
                maxOutputTokens = 800
                temperature = 0.7f
            }
            onDeviceModel = GenerativeModel(
                generationConfig = config
            )
            onDeviceAvailable = true
            Log.i(TAG, "On-device Gemini Nano initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "On-device AI not available on this device: ${e.message}")
            onDeviceAvailable = false
        }
    }

    private fun shouldUseOnDevice(): Boolean {
        return onDeviceAvailable && preferencesManager.useOnDeviceAi
    }

    // ── Verdict ──────────────────────────────────────────────────────────────

    suspend fun analyzeVerdict(
        message: String,
        context: String? = null,
        attachmentStyle: String? = null
    ): VerdictResult = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            append("Message to analyze: \"$message\"")
            if (!context.isNullOrBlank()) append("\n\nContext: $context")
            if (!attachmentStyle.isNullOrBlank()) append("\n\nUser's attachment style: $attachmentStyle")
        }

        val rawJson = if (shouldUseOnDevice()) {
            runOnDevice("$VERDICT_SYSTEM\n\n$userPrompt")
        } else {
            cloudApi.analyzeVerdict(message, context, attachmentStyle)
        }

        return@withContext parseVerdict(rawJson)
    }

    private fun parseVerdict(json: String): VerdictResult {
        return try {
            val obj = JSONObject(json)
            VerdictResult(
                verdict = obj.optString("verdict", "WAIT"),
                explanation = obj.optString("explanation", "Take a breath before sending this."),
                patternName = obj.optString("patternName", ""),
                reframe = obj.optString("reframe").takeIf { it.isNotBlank() && it != "null" },
                rewrite = obj.optString("rewrite").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            VerdictResult(
                verdict = "WAIT",
                explanation = "Take a breath before sending this.",
                patternName = "Unclear",
                reframe = null,
                rewrite = null
            )
        }
    }

    // ── Interpret ─────────────────────────────────────────────────────────────

    suspend fun interpretMessage(
        message: String,
        context: String? = null
    ): InterpretResult = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            append("Message received: \"$message\"")
            if (!context.isNullOrBlank()) append("\n\nContext: $context")
        }

        val rawJson = if (shouldUseOnDevice()) {
            runOnDevice("$INTERPRET_SYSTEM\n\n$userPrompt")
        } else {
            cloudApi.interpretMessage(message, context)
        }

        return@withContext parseInterpret(rawJson)
    }

    private fun parseInterpret(json: String): InterpretResult {
        return try {
            val obj = JSONObject(json)
            InterpretResult(
                detectedStyle = obj.optString("detectedStyle", "Unclear"),
                confidence = obj.optString("confidence", "low"),
                whatItMeans = obj.optString("whatItMeans", ""),
                howYouMightMisreadIt = obj.optString("howYouMightMisreadIt", ""),
                whatTheyNeed = obj.optString("whatTheyNeed", ""),
                suggestedResponse = obj.optString("suggestedResponse").takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (e: Exception) {
            InterpretResult(
                detectedStyle = "Unclear",
                confidence = "low",
                whatItMeans = "Unable to analyze this message.",
                howYouMightMisreadIt = "",
                whatTheyNeed = "",
                suggestedResponse = null
            )
        }
    }

    // ── Companion chat ────────────────────────────────────────────────────────

    suspend fun companionChat(
        persona: String,
        userMessage: String,
        history: List<CompanionMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = COMPANION_PROMPTS[persona] ?: COMPANION_PROMPTS["sadie"]!!

        if (shouldUseOnDevice()) {
            val fullPrompt = buildString {
                append(systemPrompt)
                append("\n\n")
                history.forEach { msg ->
                    append(if (msg.role == "user") "User: " else "You: ")
                    append(msg.content)
                    append("\n")
                }
                append("User: $userMessage\nYou:")
            }
            runOnDevice(fullPrompt)
        } else {
            cloudApi.companionChat(persona, userMessage, history)
        }
    }

    // ── On-device inference ───────────────────────────────────────────────────

    private suspend fun runOnDevice(prompt: String): String {
        return try {
            val model = onDeviceModel ?: throw IllegalStateException("On-device model not initialized")
            val response = model.generateContent(prompt)
            response.text ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "On-device inference failed, falling back to cloud: ${e.message}")
            // Mark on-device as unavailable for this session and retry via cloud
            onDeviceAvailable = false
            "{}"
        }
    }

    fun isOnDeviceAvailable(): Boolean = onDeviceAvailable
}
