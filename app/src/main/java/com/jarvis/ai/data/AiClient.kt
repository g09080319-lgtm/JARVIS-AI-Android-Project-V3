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
        if (endpoint.isBlank()) {
            return@withContext "AI backend not configured. Deploy proxy/worker.js, set JARVIS_PROXY_URL to its HTTPS /chat URL in local.properties, then rebuild the app."
        }
        val endpointUrl = runCatching { URL(endpoint) }.getOrNull()
            ?: return@withContext "The AI backend URL is invalid."
        if (!endpointUrl.protocol.equals("https", ignoreCase = true)) {
            return@withContext "The AI backend URL must use HTTPS."
        }
        if (endpointUrl.path != "/chat") {
            return@withContext "The AI backend URL must end in /chat."
        }

        val messages = JSONArray()
        var remainingCharacters = 64_000
        val boundedHistory = history.asReversed()
            .asSequence()
            .filter { it.role in setOf("user", "assistant") && it.text.isNotBlank() }
            .take(30)
            .mapNotNull { message ->
                if (remainingCharacters == 0) return@mapNotNull null
                val text = message.text.take(minOf(4_000, remainingCharacters))
                remainingCharacters -= text.length
                if (text.isBlank()) null else JSONObject().put("role", message.role).put("content", text)
            }
            .toList()
            .asReversed()
        boundedHistory.forEach(messages::put)

        if (messages.length() == 0) return@withContext "There is no message to send to the AI service."
        val body = JSONObject().put("messages", messages).put("language", lang).toString().toByteArray(Charsets.UTF_8)

        var lastError = "I couldn't reach the AI service. Please check your connection."
        for (attempt in 0..1) {
            var connection: HttpURLConnection? = null
            try {
                connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
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

                val errorCode = runCatching { JSONObject(responseText).optString("error") }.getOrNull()
                lastError = when {
                    errorCode == "proxy_not_configured" -> "The AI backend is missing its server-side API key or rate-limit configuration."
                    status == 401 || status == 403 -> "The AI proxy rejected its credentials. Check the server configuration."
                    status == 429 -> "The AI service is busy. Please wait a moment and try again."
                    status in 500..599 -> "The AI service is temporarily unavailable (HTTP $status)."
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
