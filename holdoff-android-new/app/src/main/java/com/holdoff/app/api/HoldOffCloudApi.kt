package com.holdoff.app.api

import android.util.Log
import com.holdoff.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HoldOffCloudApi @Inject constructor() {

    companion object {
        private const val TAG = "HoldOffCloudApi"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .build()

    private val baseUrl = BuildConfig.API_BASE_URL

    // ── tRPC mutation helper ──────────────────────────────────────────────────

    private suspend fun trpcMutation(procedure: String, input: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", input)
                })
            }
            val request = Request.Builder()
                .url("$baseUrl/api/trpc/$procedure?batch=1")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "[]"
            try {
                val arr = JSONArray(responseBody)
                arr.getJSONObject(0).getJSONObject("result").getJSONObject("data").getJSONObject("json")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tRPC response: $responseBody", e)
                JSONObject()
            }
        }

    // ── Verdict (cloud fallback) ──────────────────────────────────────────────

    suspend fun analyzeVerdict(
        message: String,
        context: String?,
        attachmentStyle: String?
    ): String {
        return try {
            val input = JSONObject().apply {
                put("message", message)
                if (!context.isNullOrBlank()) put("context", context)
                if (!attachmentStyle.isNullOrBlank()) put("attachmentStyle", attachmentStyle)
            }
            trpcMutation("filter.analyze", input).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Cloud verdict failed: ${e.message}")
            """{"verdict":"WAIT","explanation":"Unable to analyze right now. Take a breath.","patternName":"","reframe":null,"rewrite":null}"""
        }
    }

    // ── Interpret (cloud fallback) ────────────────────────────────────────────

    suspend fun interpretMessage(message: String, context: String?): String {
        return try {
            val input = JSONObject().apply {
                put("message", message)
                if (!context.isNullOrBlank()) put("context", context)
            }
            trpcMutation("interpret.analyze", input).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Cloud interpret failed: ${e.message}")
            """{"detectedStyle":"Unclear","confidence":"low","whatItMeans":"Unable to analyze right now.","howYouMightMisreadIt":"","whatTheyNeed":"","suggestedResponse":null}"""
        }
    }

    // ── Companion chat (cloud fallback) ──────────────────────────────────────

    suspend fun companionChat(
        persona: String,
        message: String,
        history: List<CompanionMessage>
    ): String {
        return try {
            val historyArr = JSONArray().apply {
                history.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
            val input = JSONObject().apply {
                put("persona", persona)
                put("message", message)
                put("history", historyArr)
            }
            val result = trpcMutation("companion.chat", input)
            result.optString("response", "I'm here. Tell me more.")
        } catch (e: Exception) {
            Log.e(TAG, "Cloud companion chat failed: ${e.message}")
            "I'm here. Tell me more."
        }
    }
}
