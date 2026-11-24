package com.jarvis.ai.v4.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.ai.v4.BuildConfig
import com.jarvis.ai.v4.ChatViewModel
import com.jarvis.ai.v4.data.ChatMessage

@Composable
fun JarvisScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val hasToken by viewModel.hasAccessToken.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var tokenDraft by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize()
            .systemBarsPadding()
            .background(Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF0D1C2E))))
            .imePadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("J.A.R.V.I.S", color = Color(0xFF61E6FF), style = MaterialTheme.typography.headlineSmall)
                Text("V4 · shared AI", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = {
                tokenDraft = ""
                showSettings = true
            }) { Text("Settings") }
        }

        Text(
            text = when {
                busy -> "Thinking…"
                !error.isNullOrBlank() -> error.orEmpty()
                !hasToken -> "Add an identity access token in Settings to connect securely."
                BuildConfig.API_BASE_URL.isBlank() -> "Set JARVIS_API_BASE_URL in local.properties."
                else -> "Ready · ${languageLabel(language)} · this conversation stays in memory only"
            },
            color = if (error.isNullOrBlank()) Color(0xFF9EDDEA) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(messages) { _, message -> MessageBubble(message) }
            if (busy) item {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF61E6FF))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask JARVIS…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.send(input)
                    input = ""
                }),
            )
            VoiceInputButton(
                enabled = !busy,
                language = language,
                onTranscript = { transcript ->
                    input = transcript
                    viewModel.send(transcript)
                    input = ""
                },
                onError = viewModel::reportVoiceError,
            )
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
            ) { Text("Send") }
        }
    }

    if (showSettings) {
        AccessSettingsDialog(
            tokenDraft = tokenDraft,
            onTokenChanged = { tokenDraft = it },
            hasToken = hasToken,
            language = language,
            onLanguage = viewModel::setLanguage,
            onSave = {
                if (tokenDraft.isNotBlank() || !hasToken) viewModel.setAccessToken(tokenDraft)
                showSettings = false
            },
            onClear = {
                viewModel.setAccessToken("")
                tokenDraft = ""
            },
            onClose = { showSettings = false },
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) Color(0xFF173B52) else Color(0xFF152438),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                text = message.content,
                color = Color.White,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AccessSettingsDialog(
    tokenDraft: String,
    onTokenChanged: (String) -> Unit,
    hasToken: Boolean,
    language: String,
    onLanguage: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Secure connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backend: ${BuildConfig.API_BASE_URL.ifBlank { "not configured" }}")
                Text("Paste a short-lived access token from your configured identity provider. The app encrypts it with Android Keystore; it is not an Anthropic API key.")
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = onTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasToken) "Replace access token" else "Access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("Voice language")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("en-US" to "English", "ur-PK" to "اردو", "hi-IN" to "हिन्दी").forEach { (tag, label) ->
                        FilterChip(
                            selected = language == tag,
                            onClick = { onLanguage(tag) },
                            label = { Text(label) },
                        )
                    }
                }
                Text("Chat history is session-only and is cleared when the app process ends.")
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row {
                if (hasToken) TextButton(onClick = onClear) { Text("Clear token") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
    )
}

@Composable
private fun VoiceInputButton(
    enabled: Boolean,
    language: String,
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val transcriptCallback by rememberUpdatedState(onTranscript)
    val errorCallback by rememberUpdatedState(onError)
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition(recognizer, language) { listening = it }
        else errorCallback("Microphone permission is required for voice input.")
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    errorCallback("Speech recognition failed ($error).")
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?.let(transcriptCallback)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    IconButton(
        enabled = enabled && recognizer != null,
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecognition(recognizer, language) { listening = it }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = Modifier.size(52.dp).drawBehind {
            if (listening) drawCircle(Color(0xFF61E6FF).copy(alpha = 0.3f), size.minDimension)
        },
    ) {
        Text(if (listening) "…" else "Mic", color = Color(0xFF61E6FF))
    }
}

private fun startRecognition(recognizer: SpeechRecognizer?, language: String, onListening: (Boolean) -> Unit) {
    if (recognizer == null) return
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    runCatching { recognizer.startListening(intent) }
        .onSuccess { onListening(true) }
        .onFailure { onListening(false) }
}

private fun languageLabel(language: String) = when (language) {
    "ur-PK" -> "Urdu"
    "hi-IN" -> "Hindi"
    else -> "English"
}
