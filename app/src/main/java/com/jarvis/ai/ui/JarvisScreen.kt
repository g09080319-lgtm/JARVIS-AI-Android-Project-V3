package com.jarvis.ai.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.ai.Mode

@Composable
fun JarvisScreen(
    vm: MainViewModel,
    onMic: () -> Unit,
    onBackground: (Boolean) -> Unit,
    onAlwaysListening: (Boolean) -> Unit,
    onContacts: () -> Unit
) {
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val rms by vm.rms.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.setActivityVisible(true)
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.setActivityVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        vm.setActivityVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.setActivityVisible(false)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepBlue, Navy)))
            .systemBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("J.A.R.V.I.S", style = MaterialTheme.typography.titleLarge, color = Cyan, modifier = Modifier.weight(1f))
            IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Cyan) }
        }
        Visualizer(mode, Modifier.align(Alignment.CenterHorizontally))
        Waveform(mode, rms, Modifier.padding(horizontal = 32.dp))
        Text(
            error ?: when (mode) {
                Mode.LISTENING -> partial.ifBlank { "Listening…" }
                Mode.WAKE_LISTENING -> partial.ifBlank { "Listening for “Hey JARVIS”…" }
                Mode.THINKING -> "Thinking…"
                Mode.SPEAKING -> "Speaking…"
                Mode.IDLE -> "Tap the microphone and speak"
            },
            color = if (error != null) MaterialTheme.colorScheme.error else Cyan.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs, key = { it.id }) { m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (mine) Cyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) { Text(m.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), placeholder = { Text("Type to JARVIS…") },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" })
            )
            IconButton({ vm.send(input); input = "" }) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Cyan) }
            if (mode == Mode.SPEAKING) IconButton({ vm.stopSpeaking() }) { Icon(Icons.Default.Stop, "Stop speaking", tint = Cyan) }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(64.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = if (mode == Mode.LISTENING || mode == Mode.WAKE_LISTENING) 0.7f else 0.35f), Color.Transparent)), size.minDimension) }
            ) {
                FilledIconButton(onMic, Modifier.size(52.dp).align(Alignment.Center), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = DeepBlue)) {
                    Icon(Icons.Default.Mic, "Microphone")
                }
            }
        }
    }

    if (prefs.loaded && !prefs.consentAsked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Save conversation history?") },
            text = { Text("JARVIS can remember your chats on this device only, so follow-up questions work across sessions. Text you send is processed by the AI service to answer you. You can change this or delete history anytime in Settings.") },
            confirmButton = { TextButton({ vm.setSaveHistory(true) }) { Text("Allow") } },
            dismissButton = { TextButton({ vm.setSaveHistory(false) }) { Text("No thanks") } }
        )
    }

    if (showNotes) NotesDialog(notes, vm) { showNotes = false }

    if (showSettings) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voice recognition language")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en-US" to "English", "ur-PK" to "اردو", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                            FilterChip(prefs.language == code, { vm.setLanguage(code) }, { Text(label) })
                        }
                    }
                    var rate by remember(prefs.rate) { mutableFloatStateOf(prefs.rate) }
                    var pitch by remember(prefs.pitch) { mutableFloatStateOf(prefs.pitch) }
                    Text("Speaking speed")
                    Slider(rate, { rate = it }, valueRange = 0.5f..2f, onValueChangeFinished = { vm.setRate(rate) })
                    Text("Voice pitch")
                    Slider(pitch, { pitch = it }, valueRange = 0.5f..1.5f, onValueChangeFinished = { vm.setPitch(pitch) })
                    TextButton({ showSettings = false; showNotes = true }) { Text("Notes & tasks") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Remember history", Modifier.weight(1f))
                        Switch(prefs.saveHistory, { vm.setSaveHistory(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Always listen for “Hey JARVIS”", Modifier.weight(1f))
                        Switch(prefs.alwaysListening, onAlwaysListening, enabled = vm.wakeWordAvailable)
                    }
                    Text(
                        if (vm.wakeWordAvailable) {
                            "Off by default. Wake-word detection uses on-device speech recognition and may use extra battery. Spoken commands are handled according to your selected language and AI backend."
                        } else {
                            "Unavailable: this feature requires Android 12+ and an installed on-device speech recognizer. Tap the microphone to speak instead."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Background / locked-screen listening", Modifier.weight(1f))
                        Switch(prefs.background, { onBackground(it) })
                    }
                    Text(
                        "Requires Always Listening and the ongoing microphone notification. Start it while JARVIS is open; Android or device battery policies may still stop background listening.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton({ vm.clearHistory() }) { Text("Delete conversation history") }
                    TextButton({ onContacts() }) { Text("Allow contacts (call/text by name)") }
                    TextButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery optimization settings") }
                    TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/jarvis-privacy"))) }) { Text("Privacy policy") }
                }
            },
            confirmButton = { TextButton({ showSettings = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun NotesDialog(notes: List<com.jarvis.ai.data.Note>, vm: MainViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Notes & tasks") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (notes.isEmpty()) Text("Nothing yet. Say: add note buy milk, or: add task call Ali.")
                notes.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.kind == "task") Checkbox(n.done, { vm.toggleNote(n) })
                        Text((if (n.kind == "note") "• " else "") + n.text, Modifier.weight(1f))
                        IconButton({ vm.deleteNote(n) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } },
        dismissButton = { TextButton({ vm.clearNotes() }) { Text("Delete all") } }
    )
}
