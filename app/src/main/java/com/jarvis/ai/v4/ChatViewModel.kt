package com.jarvis.ai.v4

import android.app.Application
import android.app.Activity
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.v4.data.AccessTokenStore
import com.jarvis.ai.v4.data.ChatMessage
import com.jarvis.ai.v4.data.GoogleAuthClient
import com.jarvis.ai.v4.data.SharedAiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val accessTokenStore = AccessTokenStore(application)
    private val client = SharedAiClient(BuildConfig.API_BASE_URL)
    private var accessToken = accessTokenStore.read()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _language = MutableStateFlow("en-US")
    val language: StateFlow<String> = _language.asStateFlow()
    private val _hasAccessToken = MutableStateFlow(!accessToken.isNullOrBlank())
    val hasAccessToken: StateFlow<Boolean> = _hasAccessToken.asStateFlow()
    private val _authenticating = MutableStateFlow(false)
    val authenticating: StateFlow<Boolean> = _authenticating.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var requestJob: Job? = null

    init {
        textToSpeech = TextToSpeech(application) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) textToSpeech?.language = Locale.forLanguageTag(_language.value)
        }
    }

    fun setAccessToken(value: String) {
        val normalized = value.trim()
        require(normalized.length <= 8_192) { "Access token is too long." }
        accessToken = normalized
        if (normalized.isEmpty()) accessTokenStore.clear() else accessTokenStore.write(normalized)
        _hasAccessToken.value = normalized.isNotEmpty()
        _error.value = null
    }

    fun signInWithGoogle(activity: Activity) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _error.value = "Google Sign-In is not configured for this build. Add GOOGLE_WEB_CLIENT_ID."
            return
        }
        if (_authenticating.value || _busy.value) return

        _authenticating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val googleIdToken = GoogleAuthClient(activity)
                    .getIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                val session = client.authenticateWithGoogle(googleIdToken, refresh = accessToken != null)
                setAccessToken(session.accessToken)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _error.value = exception.message ?: "Google Sign-In could not be completed."
            } finally {
                _authenticating.value = false
            }
        }
    }

    fun signOut() {
        requestJob?.cancel()
        requestJob = null
        accessToken = null
        accessTokenStore.clear()
        _hasAccessToken.value = false
        _messages.value = emptyList()
        _error.value = null
        stopSpeaking()
    }

    fun setLanguage(value: String) {
        _language.value = value
        if (ttsReady) textToSpeech?.language = Locale.forLanguageTag(value)
    }

    fun clearMessages() {
        requestJob?.cancel()
        requestJob = null
        _messages.value = emptyList()
        _error.value = null
        stopSpeaking()
    }

    fun stopResponse() {
        requestJob?.cancel()
        requestJob = null
        stopSpeaking()
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _busy.value) return
        if (BuildConfig.API_BASE_URL.isBlank()) {
            _error.value = "Configure AIVO_PROXY_URL in local.properties to connect the shared AI backend."
            return
        }
        val token = accessToken
        if (token.isNullOrBlank()) {
            _error.value = "Add your signed-in API access token in Settings. No API key is stored in the app."
            return
        }

        val next = _messages.value + ChatMessage(role = "user", content = message)
        _messages.value = next
        _busy.value = true
        _error.value = null
        requestJob = viewModelScope.launch {
            try {
                val reply = client.chat(token, next.takeLast(30), _language.value)
                _messages.value = _messages.value + ChatMessage(role = "assistant", content = reply)
                if (ttsReady) {
                    textToSpeech?.language = Locale.forLanguageTag(_language.value)
                    textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "aivo-reply")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _messages.value = _messages.value.dropLast(1)
                _error.value = exception.message ?: "The shared AI service could not be reached."
            } finally {
                _busy.value = false
                requestJob = null
            }
        }
    }

    override fun onCleared() {
        requestJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
