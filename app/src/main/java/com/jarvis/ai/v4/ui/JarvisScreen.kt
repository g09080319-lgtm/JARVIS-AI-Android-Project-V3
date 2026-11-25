package com.jarvis.ai.v4.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.ai.v4.BackendConnectionState
import com.jarvis.ai.v4.ChatViewModel
import com.jarvis.ai.v4.data.ChatMessage
import com.jarvis.ai.v4.notifications.NotificationAnnouncementPreferences
import com.jarvis.ai.v4.notifications.WhatsAppNotificationListener

@Composable
fun JarvisScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val announcementPreferences = remember(context) { NotificationAnnouncementPreferences(context) }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val hasToken by viewModel.hasAccessToken.collectAsStateWithLifecycle()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsStateWithLifecycle()
    val backendConnection by viewModel.backendConnection.collectAsStateWithLifecycle()
    val speaking by viewModel.speaking.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var tokenDraft by remember { mutableStateOf("") }
    var endpointDraft by remember { mutableStateOf(apiBaseUrl) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var notificationAccessEnabled by remember(context) {
        mutableStateOf(WhatsAppNotificationListener.hasNotificationAccess(context))
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val voice = rememberVoiceInputController(
        language = language,
        onTranscript = { transcript ->
            input = transcript
            viewModel.send(transcript)
            input = ""
        },
        onError = viewModel::reportVoiceError,
    )
    val networkStatus = rememberNetworkStatus(context)
    val microphoneStatus = when {
        !voice.available -> "Unavailable"
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
            "Ready"
        else -> "Permission needed"
    }
    val coreState = when {
        !error.isNullOrBlank() -> CoreState.ERROR
        busy -> CoreState.THINKING
        voice.listening -> CoreState.LISTENING
        speaking -> CoreState.SPEAKING
        else -> CoreState.IDLE
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = WhatsAppNotificationListener.hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020814), Color(0xFF06162A), Color(0xFF030A16))))
            .imePadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 760.dp
        val orbSize = when {
            landscape -> 78.dp
            compact -> 154.dp
            else -> 204.dp
        }
        Column(
            Modifier.fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = if (landscape) 1.dp else 7.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) 2.dp else if (compact) 5.dp else 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(if (landscape) 34.dp else 48.dp),
            ) {
                TextButton(
                    onClick = {
                        endpointDraft = apiBaseUrl
                        tokenDraft = ""
                        settingsError = null
                        showSettings = true
                    },
                    modifier = Modifier.semantics { contentDescription = "Open JARVIS menu and settings" },
                ) {
                    Text("☰", color = JarvisPalette.cyan, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "J.A.R.V.I.S",
                        color = JarvisPalette.cyan,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Light,
                    )
                    Text("V4 · SHARED AI", color = JarvisPalette.muted, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    enabled = speaking,
                    onClick = viewModel::stopSpeaking,
                    modifier = Modifier.semantics { contentDescription = "Stop JARVIS speech" },
                ) {
                    Text(if (speaking) "◼ Stop" else "Voice", color = JarvisPalette.cyan)
                }
                IconButton(
                    onClick = {
                        endpointDraft = apiBaseUrl
                        tokenDraft = ""
                        settingsError = null
                        showSettings = true
                    },
                    modifier = Modifier.semantics { contentDescription = "Settings" },
                ) {
                    Text("⚙", color = JarvisPalette.cyan, style = MaterialTheme.typography.headlineSmall)
                }
            }

            JarvisOrb(coreState, Modifier.align(Alignment.CenterHorizontally).size(orbSize))
            VoiceWaveform(
                active = voice.listening || speaking || busy,
                Modifier.fillMaxWidth().height(if (landscape) 10.dp else if (compact) 40.dp else 54.dp),
            )
            AssistantStatusPill(
                state = coreState,
                message = error.orEmpty(),
                language = language,
                backend = backendStatusLabel(backendConnection, apiBaseUrl, hasToken),
                compact = landscape,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DashboardShortcut("▤", "Chat\nwith JARVIS", Modifier.weight(1f).heightIn(min = if (landscape) 36.dp else if (compact) 65.dp else 78.dp), compact = landscape) {
                    focusRequester.requestFocus()
                }
                DashboardShortcut("◉", "Voice\nAssistant", Modifier.weight(1f).heightIn(min = if (landscape) 36.dp else if (compact) 65.dp else 78.dp), compact = landscape) {
                    voice.toggle()
                }
                DashboardShortcut("◌", "WhatsApp\nAnnouncements", Modifier.weight(1f).heightIn(min = if (landscape) 36.dp else if (compact) 65.dp else 78.dp), compact = landscape) {
                    endpointDraft = apiBaseUrl
                    tokenDraft = ""
                    settingsError = null
                    showSettings = true
                }
                DashboardShortcut("☎", "Call\nAnnouncements", Modifier.weight(1f).heightIn(min = if (landscape) 36.dp else if (compact) 65.dp else 78.dp), compact = landscape) {
                    endpointDraft = apiBaseUrl
                    tokenDraft = ""
                    settingsError = null
                    showSettings = true
                }
            }

            SystemStatusPanel(
                backend = backendStatusLabel(backendConnection, apiBaseUrl, hasToken),
                microphone = microphoneStatus,
                language = languageLabel(language),
                network = networkStatus,
                compact = compact || landscape,
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "PRIVATE SESSION · HISTORY IS NOT SAVED",
                            color = JarvisPalette.muted.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                itemsIndexed(messages) { _, message -> MessageBubble(message) }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = JarvisPalette.cyan,
                            strokeWidth = 2.dp,
                        )
                        Text("Processing request…", color = JarvisPalette.cyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask JARVIS…", color = JarvisPalette.muted) },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisPalette.cyan,
                        unfocusedBorderColor = JarvisPalette.blue.copy(alpha = 0.85f),
                        focusedContainerColor = JarvisPalette.surface,
                        unfocusedContainerColor = JarvisPalette.surface,
                        cursorColor = JarvisPalette.cyan,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        submitPrompt(input, busy, viewModel) { input = "" }
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f)
                        .focusRequester(focusRequester)
                        .heightIn(min = if (landscape) 44.dp else 56.dp, max = if (landscape) 62.dp else 96.dp)
                        .testTag("jarvis-prompt"),
                )
                IconButton(
                    onClick = voice.toggle,
                    enabled = !busy,
                    modifier = Modifier
                        .size(if (landscape) 44.dp else 56.dp)
                        .drawBehind {
                            val tint = if (voice.listening) JarvisPalette.green else JarvisPalette.cyan
                            drawCircle(tint.copy(alpha = 0.16f), size.minDimension * 0.49f)
                            drawCircle(tint.copy(alpha = 0.82f), size.minDimension * 0.49f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        }
                        .semantics { contentDescription = if (voice.listening) "Stop voice recognition" else "Start voice recognition" },
                ) {
                    Text(if (voice.listening) "■" else "🎙", color = JarvisPalette.cyan)
                }
                Button(
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        submitPrompt(input, busy, viewModel) { input = "" }
                        focusManager.clearFocus()
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, JarvisPalette.cyan),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0756A5),
                        disabledContainerColor = Color(0xFF12243A),
                    ),
                    modifier = Modifier.height(if (landscape) 42.dp else 54.dp).semantics { contentDescription = "Send message to JARVIS" },
                ) { Text("➤ Send", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
            NeonCircuitLine(Modifier.fillMaxWidth().height(if (landscape) 6.dp else if (compact) 18.dp else 24.dp))
        }
    }

    if (showSettings) {
        AccessSettingsDialog(
            endpointDraft = endpointDraft,
            onEndpointChanged = { endpointDraft = it },
            tokenDraft = tokenDraft,
            onTokenChanged = { tokenDraft = it },
            hasToken = hasToken,
            language = language,
            onLanguage = viewModel::setLanguage,
            announcementPreferences = announcementPreferences,
            notificationAccessEnabled = notificationAccessEnabled,
            onSave = {
                try {
                    viewModel.setApiBaseUrl(endpointDraft)
                    if (tokenDraft.isNotBlank() || !hasToken) viewModel.setAccessToken(tokenDraft)
                    settingsError = null
                    showSettings = false
                } catch (exception: IllegalArgumentException) {
                    settingsError = exception.message ?: "Check the backend settings."
                }
            },
            onClear = {
                viewModel.setAccessToken("")
                tokenDraft = ""
            },
            onClose = { showSettings = false },
            error = settingsError,
        )
    }
}

private fun submitPrompt(value: String, busy: Boolean, viewModel: ChatViewModel, clear: () -> Unit) {
    if (!busy && value.isNotBlank()) {
        viewModel.send(value)
        clear()
    }
}

@Composable
private fun AssistantStatusPill(state: CoreState, message: String, language: String, backend: String, compact: Boolean) {
    val statusText = if (state == CoreState.ERROR) message.ifBlank { "Request needs attention" } else when (state) {
        CoreState.IDLE -> when (backend) {
            "URL needed" -> "Add the deployed backend URL in Settings"
            "Token needed" -> "Add an identity access token in Settings"
            "Not checked" -> "Backend not verified · ${languageLabel(language)}"
            else -> "Ready to help · ${languageLabel(language)}"
        }
        CoreState.LISTENING -> "Listening · speak now"
        CoreState.THINKING -> "Processing your request…"
        CoreState.SPEAKING -> "Speaking · tap Stop to interrupt"
        CoreState.ERROR -> "Request needs attention"
    }
    val accent = if (state == CoreState.ERROR) JarvisPalette.red else JarvisPalette.cyan
    Surface(
        color = JarvisPalette.surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.76f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 28.dp else 38.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier.size(9.dp).background(if (state == CoreState.IDLE) JarvisPalette.green else accent, CircleShape)
            )
            Text(
                statusText,
                color = Color(0xFFD8F3FF),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun DashboardShortcut(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = JarvisPalette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, JarvisPalette.cyan.copy(alpha = 0.74f)),
        shadowElevation = 6.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp),
        ) {
            if (!compact) Text(symbol, color = JarvisPalette.cyan, style = MaterialTheme.typography.titleLarge)
            Text(
                if (compact) label.replace("\n", " ") else label,
                color = Color(0xFFE1F4FF),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 2,
            )
        }
    }
}

@Composable
private fun SystemStatusPanel(
    backend: String,
    microphone: String,
    language: String,
    network: String,
    compact: Boolean,
) {
    Surface(
        color = JarvisPalette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, JarvisPalette.blue.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp)) {
            Text("SYSTEM STATUS", color = JarvisPalette.cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusValue("Backend", backend, Modifier.weight(1f))
                StatusValue("Mic", microphone, Modifier.weight(1f))
                StatusValue("Language", language, Modifier.weight(1f))
                StatusValue("Network", network, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = JarvisPalette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, color = if (value == "Ready" || value == "Connected") JarvisPalette.green else Color(0xFFD5E9F8), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun backendStatusLabel(state: BackendConnectionState, endpoint: String, hasToken: Boolean): String = when {
    endpoint.isBlank() -> "URL needed"
    !hasToken -> "Token needed"
    state == BackendConnectionState.CHECKING -> "Checking"
    state == BackendConnectionState.VERIFIED -> "Connected"
    state == BackendConnectionState.REQUEST_FAILED -> "Request failed"
    else -> "Not checked"
}

@Composable
private fun rememberNetworkStatus(context: Context): String {
    var status by remember(context) { mutableStateOf(readNetworkStatus(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                status = "Checking"
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                status = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Online" else "Offline"
            }

            override fun onLost(network: Network) {
                status = "Offline"
            }
        }
        manager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return status
}

private fun readNetworkStatus(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
    val network = manager.activeNetwork ?: return "Offline"
    val capabilities = manager.getNetworkCapabilities(network) ?: return "Checking"
    return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Online" else "Offline"
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
    endpointDraft: String,
    onEndpointChanged: (String) -> Unit,
    tokenDraft: String,
    onTokenChanged: (String) -> Unit,
    hasToken: Boolean,
    language: String,
    onLanguage: (String) -> Unit,
    announcementPreferences: NotificationAnnouncementPreferences,
    notificationAccessEnabled: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    error: String?,
) {
    val context = LocalContext.current
    var announcementsEnabled by remember { mutableStateOf(announcementPreferences.enabled) }
    var includePreview by remember { mutableStateOf(announcementPreferences.includeMessagePreview) }
    var announceVoiceMessages by remember { mutableStateOf(announcementPreferences.announceVoiceMessages) }
    var announceCalls by remember { mutableStateOf(announcementPreferences.announceCalls) }
    var quietHoursEnabled by remember { mutableStateOf(announcementPreferences.quietHoursEnabled) }
    var quietStart by remember { mutableStateOf(announcementPreferences.quietStart) }
    var quietEnd by remember { mutableStateOf(announcementPreferences.quietEnd) }
    var mutedSenders by remember {
        mutableStateOf(announcementPreferences.mutedSenders.filterNot { it == "*" }.joinToString(", "))
    }
    var notificationSettingsError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Connect to your deployed JARVIS Worker using the HTTPS origin and a short-lived OIDC access token.")
                OutlinedTextField(
                    value = endpointDraft,
                    onValueChange = onEndpointChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backend HTTPS origin") },
                    placeholder = { Text("https://your-worker.example.workers.dev") },
                    singleLine = true,
                )
                Text("The endpoint is saved on this device. The token is encrypted with Android Keystore; it is not an Anthropic API key.")
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = onTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasToken) "Replace access token" else "Access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
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
                Text("WhatsApp announcements", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (notificationAccessEnabled) {
                        "Notification access is enabled. Android can expose only the details WhatsApp places in its notifications."
                    } else {
                        "Notification access is not enabled. Allow JARVIS in Android's Notification access settings to receive announcements."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    notificationSettingsError = null
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }.onFailure {
                        notificationSettingsError = "Android notification access settings could not be opened."
                    }
                }) {
                    Text(if (notificationAccessEnabled) "Review notification access" else "Open notification access settings")
                }
                notificationSettingsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                NotificationSettingSwitch(
                    label = "Enable announcements",
                    checked = announcementsEnabled,
                    testTag = "announcements-enabled",
                    onCheckedChange = {
                        announcementsEnabled = it
                        announcementPreferences.enabled = it
                    },
                )
                NotificationSettingSwitch(
                    label = "Read message previews (off by default)",
                    checked = includePreview,
                    testTag = "announcement-preview",
                    onCheckedChange = {
                        includePreview = it
                        announcementPreferences.includeMessagePreview = it
                    },
                )
                NotificationSettingSwitch(
                    label = "Announce voice messages",
                    checked = announceVoiceMessages,
                    testTag = "announcement-voice-messages",
                    onCheckedChange = {
                        announceVoiceMessages = it
                        announcementPreferences.announceVoiceMessages = it
                    },
                )
                NotificationSettingSwitch(
                    label = "Announce calls and missed calls",
                    checked = announceCalls,
                    testTag = "announcement-calls",
                    onCheckedChange = {
                        announceCalls = it
                        announcementPreferences.announceCalls = it
                    },
                )
                NotificationSettingSwitch(
                    label = "Quiet hours",
                    checked = quietHoursEnabled,
                    testTag = "announcement-quiet-hours",
                    onCheckedChange = {
                        quietHoursEnabled = it
                        announcementPreferences.quietHoursEnabled = it
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quietStart,
                        onValueChange = { value ->
                            quietStart = value
                            if (value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) {
                                announcementPreferences.quietStart = value
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Quiet from") },
                        supportingText = { Text("HH:mm") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = quietEnd,
                        onValueChange = { value ->
                            quietEnd = value
                            if (value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) {
                                announcementPreferences.quietEnd = value
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Quiet until") },
                        supportingText = { Text("HH:mm") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = mutedSenders,
                    onValueChange = { value ->
                        mutedSenders = value
                        try {
                            announcementPreferences.mutedSenders = value.split(',', '\n').toSet()
                            notificationSettingsError = null
                        } catch (exception: Exception) {
                            notificationSettingsError = "Could not securely save the muted-sender list on this device."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Muted senders (comma separated)") },
                    supportingText = { Text("Matches the sender text exposed by WhatsApp; no contacts are read.") },
                    minLines = 1,
                    maxLines = 3,
                )
                if (announcementPreferences.mutedSenderFiltersUnreadable) {
                    Text(
                        "Encrypted muted-sender settings could not be read. Announcements are suppressed until you save a replacement list.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Previews are hidden by default. Announcements depend on WhatsApp notification content and Android access. Speech uses the selected TTS language and current system audio volume. No WhatsApp messages or call audio are read from private storage.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
private fun NotificationSettingSwitch(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

private data class VoiceInputController(
    val listening: Boolean,
    val available: Boolean,
    val toggle: () -> Unit,
)

@Composable
private fun rememberVoiceInputController(
    language: String,
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit,
): VoiceInputController {
    val context = LocalContext.current
    val transcriptCallback by rememberUpdatedState(onTranscript)
    val errorCallback by rememberUpdatedState(onError)
    val currentLanguage by rememberUpdatedState(language)
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition(recognizer, currentLanguage, { listening = it }, errorCallback)
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

    val toggle: () -> Unit = {
        when {
            recognizer == null -> errorCallback("Speech recognition is unavailable on this device.")
            listening -> {
                recognizer.stopListening()
                recognizer.cancel()
                listening = false
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                startRecognition(recognizer, currentLanguage, { listening = it }, errorCallback)
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return VoiceInputController(listening = listening, available = recognizer != null, toggle = toggle)
}

private fun startRecognition(
    recognizer: SpeechRecognizer?,
    language: String,
    onListening: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    if (recognizer == null) {
        onListening(false)
        return
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    try {
        recognizer.startListening(intent)
        onListening(true)
    } catch (exception: IllegalStateException) {
        onListening(false)
        onError("Speech recognition could not start. Try again.")
    } catch (exception: SecurityException) {
        onListening(false)
        onError("Microphone permission is required for voice input.")
    }
}

private fun languageLabel(language: String) = when (language) {
    "ur-PK" -> "Urdu"
    "hi-IN" -> "Hindi"
    else -> "English"
}
