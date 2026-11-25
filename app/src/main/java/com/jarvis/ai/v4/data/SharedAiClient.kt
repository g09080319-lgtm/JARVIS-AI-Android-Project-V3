package com.jarvis.ai.v4.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SharedAiClient {
    private val http = OkHttpClient.Builder()
        .callTimeout(50, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        baseUrl: String,
        token: String,
        messages: List<ChatMessage>,
        language: String,
    ): String =
        withContext(Dispatchers.IO) {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            require(normalizedBase.startsWith("https://", ignoreCase = true)) {
                "The shared AI backend URL must use HTTPS."
            }
            val url = "$normalizedBase/v1/chat"
            val body = JSONObject()
                .put("language", language)
                .put("messages", JSONArray().apply {
                    messages.forEach { message ->
                        put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                })
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = runCatching { JSONObject(responseText).optString("error") }.getOrDefault("")
                    throw IllegalStateException(when (response.code) {
                        401 -> "Sign in again; the API access token is missing or expired."
                        429 -> "The shared AI service rate limit was reached. Try again shortly."
                        else -> code.ifBlank { "AI service request failed (${response.code})." }
                    })
                }
                val reply = JSONObject(responseText).optString("reply").trim()
                if (reply.isEmpty()) throw IllegalStateException("The AI service returned an empty reply.")
                reply
            }
        }
}
