package com.jarvis.ai.data

import com.jarvis.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls the HTTPS proxy; provider credentials stay on the server, never in the APK. */
class AiClient {
    suspend fun ask(history: List<Message>, lang: String): String = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.PROXY_URL.trim()
        if (endpoint.isBlank()) return@withContext "AI backend not configured. Set JARVIS_PROXY_URL in local.properties."
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            return@withContext "The AI backend URL must use HTTPS."
        }

        val messages = JSONArray()
        history.takeLast(30).forEach { message ->
            if (message.role in setOf("user", "assistant") && message.text.isNotBlank()) {
                messages.put(JSONObject().put("role", message.role).put("content", message.text.take(4000)))
            }
        }
        if (messages.length() == 0) return@withContext "There is no message to send to the AI service."
        val body = JSONObject().put("messages", messages).put("language", lang).toString().toByteArray(Charsets.UTF_8)

        var lastError = "I couldn't reach the AI service. Please check your connection."
        for (attempt in 0..1) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setFixedLengthStreamingMode(body.size)
                }
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(64_000) }.orEmpty()

                if (status in 200..299) {
                    val reply = runCatching { JSONObject(responseText).optString("reply").trim() }.getOrNull()
                    return@withContext if (!reply.isNullOrBlank()) reply else "The AI service returned an empty response. Please try again."
                }

                lastError = when (status) {
                    401, 403 -> "The AI proxy rejected its credentials. Check the server configuration."
                    429 -> "The AI service is busy. Please wait a moment and try again."
                    in 500..599 -> "The AI service is temporarily unavailable (HTTP $status)."
                    else -> "The AI proxy returned an error (HTTP $status)."
                }
                if (status !in 500..599 && status != 429) return@withContext lastError
            } catch (_: Exception) {
                lastError = "I couldn't reach the AI service. Please check your internet connection and proxy URL."
            } finally {
                connection?.disconnect()
            }
            if (attempt == 0) delay(700)
        }
        lastError
    }
}
