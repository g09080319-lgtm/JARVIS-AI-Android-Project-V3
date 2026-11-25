package com.jarvis.ai.v4

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.v4.data.AccessTokenStore
import com.jarvis.ai.v4.data.ChatMessage
import com.jarvis.ai.v4.data.SharedAiClient
import com.jarvis.ai.v4.notifications.NotificationAnnouncementPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class BackendConnectionState {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    NOT_VERIFIED,
    CHECKING,
    VERIFIED,
    REQUEST_FAILED,
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val accessTokenStore = AccessTokenStore(application)
    private val client = SharedAiClient()
    private val endpointPreferences = application.getSharedPreferences("jarvis_v4_connection", Context.MODE_PRIVATE)
    private var accessToken = accessTokenStore.read()
    private val _apiBaseUrl = MutableStateFlow(
        endpointPreferences.getString(API_BASE_URL_KEY, null)?.trimEnd('/')
            ?: BuildConfig.API_BASE_URL.trim().trimEnd('/'),
    )
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val announcementPreferences = NotificationAnnouncementPreferences(application)
    private val _language = MutableStateFlow(announcementPreferences.language)
    val language: StateFlow<String> = _language.asStateFlow()
    private val _hasAccessToken = MutableStateFlow(!accessToken.isNullOrBlank())
    val hasAccessToken: StateFlow<Boolean> = _hasAccessToken.asStateFlow()
    private val _backendConnection = MutableStateFlow(initialBackendState())
    val backendConnection: StateFlow<BackendConnectionState> = _backendConnection.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    init {
        textToSpeech = TextToSpeech(application) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) textToSpeech?.language = Locale.forLanguageTag(_language.value)
        }
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _speaking.value = false
            }
        })
    }

    fun setAccessToken(value: String) {
        val normalized = value.trim()
        require(normalized.length <= 8_192) { "Access token is too long." }
        accessToken = normalized
        if (normalized.isEmpty()) accessTokenStore.clear() else accessTokenStore.write(normalized)
        _hasAccessToken.value = normalized.isNotEmpty()
        _backendConnection.value = initialBackendState()
        _error.value = null
    }

    fun setApiBaseUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        if (normalized.isNotEmpty()) {
            val uri = android.net.Uri.parse(normalized)
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
                "Enter the HTTPS backend origin, without /v1/chat."
            }
        }
        endpointPreferences.edit().putString(API_BASE_URL_KEY, normalized).apply()
        _apiBaseUrl.value = normalized
        _backendConnection.value = initialBackendState()
        _error.value = null
    }

    fun setLanguage(value: String) {
        _language.value = value
        announcementPreferences.language = value
        if (ttsReady) textToSpeech?.language = Locale.forLanguageTag(value)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _speaking.value = false
    }

    fun reportVoiceError(message: String) {
        _error.value = message
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _busy.value) return
        val baseUrl = _apiBaseUrl.value
        if (baseUrl.isBlank()) {
            _backendConnection.value = BackendConnectionState.NOT_CONFIGURED
            _error.value = "Configure the shared AI HTTPS endpoint in Settings."
            return
        }
        val token = accessToken
        if (token.isNullOrBlank()) {
            _backendConnection.value = BackendConnectionState.AUTH_REQUIRED
            _error.value = "Add your signed-in API access token in Settings. No API key is stored in the app."
            return
        }

        val next = _messages.value + ChatMessage(role = "user", content = message)
        _messages.value = next
        _busy.value = true
        _backendConnection.value = BackendConnectionState.CHECKING
        _error.value = null
        viewModelScope.launch {
            try {
                val reply = client.chat(baseUrl, token, next.takeLast(30), _language.value)
                _messages.value = _messages.value + ChatMessage(role = "assistant", content = reply)
                _backendConnection.value = BackendConnectionState.VERIFIED
                if (ttsReady) {
                    textToSpeech?.language = Locale.forLanguageTag(_language.value)
                    _speaking.value = true
                    val result = textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
                    if (result == TextToSpeech.ERROR) _speaking.value = false
                }
            } catch (exception: Exception) {
                _messages.value = _messages.value.dropLast(1)
                _backendConnection.value = if (
                    exception.message.orEmpty().contains("token", ignoreCase = true) ||
                    exception.message.orEmpty().contains("sign in again", ignoreCase = true)
                ) {
                    BackendConnectionState.AUTH_REQUIRED
                } else {
                    BackendConnectionState.REQUEST_FAILED
                }
                _error.value = exception.message ?: "The shared AI service could not be reached."
            } finally {
                _busy.value = false
            }
        }
    }

    override fun onCleared() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun initialBackendState(): BackendConnectionState = when {
        _apiBaseUrl.value.isBlank() -> BackendConnectionState.NOT_CONFIGURED
        accessToken.isNullOrBlank() -> BackendConnectionState.AUTH_REQUIRED
        else -> BackendConnectionState.NOT_VERIFIED
    }

    private companion object {
        const val API_BASE_URL_KEY = "api_base_url"
    }
}
