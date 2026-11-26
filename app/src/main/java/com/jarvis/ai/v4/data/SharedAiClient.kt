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

data class BackendSession(
    val accessToken: String,
    val expiresInSeconds: Long,
)

class SharedAiClient(private val baseUrl: String) {
    private val http = OkHttpClient.Builder()
        .callTimeout(50, TimeUnit.SECONDS)
        .build()

    suspend fun authenticateWithGoogle(idToken: String, refresh: Boolean = false): BackendSession =
        withContext(Dispatchers.IO) {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            require(normalizedBase.startsWith("https://", ignoreCase = true)) {
                "The shared AI backend URL must use HTTPS."
            }
            require(idToken.isNotBlank() && idToken.length <= 16_384) {
                "Google Sign-In returned an invalid ID token."
            }
            val route = if (refresh) "/v1/auth/refresh" else "/v1/auth/google"
            val body = JSONObject()
                .put("idToken", idToken)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$normalizedBase$route")
                .header("Accept", "application/json")
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = runCatching { JSONObject(responseText).optString("error") }.getOrDefault("")
                    throw IllegalStateException(when {
                        response.code == 401 -> "Google Sign-In could not be verified. Sign in again."
                        response.code == 429 -> "Too many sign-in attempts. Try again shortly."
                        code == "auth_not_configured" -> "Google Sign-In is not configured on the backend."
                        else -> code.ifBlank { "Authentication failed (HTTP ${response.code})." }
                    })
                }
                val result = JSONObject(responseText)
                val token = result.optString("access_token").trim()
                val expiresIn = result.optLong("expires_in", 0L)
                if (result.optString("token_type") != "Bearer" || token.isBlank() || expiresIn <= 0L) {
                    throw IllegalStateException("The authentication service returned an invalid session.")
                }
                BackendSession(token, expiresIn)
            }
        }

    suspend fun chat(token: String, messages: List<ChatMessage>, language: String): String =
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
