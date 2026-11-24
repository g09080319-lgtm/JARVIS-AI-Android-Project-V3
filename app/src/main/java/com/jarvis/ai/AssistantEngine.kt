package com.jarvis.ai

import android.app.Application
import android.content.pm.PackageManager
import androidx.room.Room
import com.jarvis.ai.actions.CommandRouter
import com.jarvis.ai.data.*
import com.jarvis.ai.voice.Speaker
import com.jarvis.ai.voice.VoiceListener
import com.jarvis.ai.voice.WakeWordDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Mode { IDLE, LISTENING, WAKE_LISTENING, THINKING, SPEAKING }

/** Single shared brain used by the UI (via ViewModel) and the foreground service. */
class AssistantEngine(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "jarvis.db").addMigrations(AppDatabase.MIGRATION_2_3).build()
    private val dao = db.dao()
    private val noteDao = db.notes()
    private val reminderDao = db.reminders()
    val settings = SettingsStore(app)
    private val ai = AiClient()
    private val router = CommandRouter(app, noteDao, reminderDao)
    private val speaker = Speaker(app)
    private val listener = VoiceListener(app)
    val wakeWordAvailable = listener.onDeviceRecognitionAvailable()

    val prefs: StateFlow<Prefs> = settings.flow.stateIn(scope, SharingStarted.Eagerly, Prefs())
    val messages: StateFlow<List<Message>> = dao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<Note>> = noteDao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val reminders: StateFlow<List<Reminder>> = reminderDao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val mode = MutableStateFlow(Mode.IDLE)
    val partial = MutableStateFlow("")
    val rms = MutableStateFlow(0f)
    val error = MutableStateFlow<String?>(null)

    private var activityVisible = false
    private var backgroundServiceActive = false
    private var alwaysListeningEnabled = false
    private var followUpListening = false
    private var wakeGeneration = 0
    private var wakeRetryDelay = 1_000L
    private var wakeFailureCount = 0
    private var wakeRestartJob: Job? = null

    init {
        // Without the user's consent, chat history never survives an app restart.
        scope.launch(Dispatchers.IO) { if (!settings.flow.first().saveHistory) dao.clear() }
        scope.launch {
            prefs.collect {
                alwaysListeningEnabled = it.alwaysListening
                refreshWakeListening()
            }
        }
    }

    fun hasMic() = app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun toggleListen() {
        when (mode.value) {
            Mode.LISTENING, Mode.WAKE_LISTENING -> startListening()
            Mode.SPEAKING -> stopSpeaking()
            Mode.THINKING -> Unit
            Mode.IDLE -> startListening()
        }
    }

    fun stopListening() {
        wakeGeneration++
        wakeRestartJob?.cancel()
        wakeRestartJob = null
        listener.stop()
        partial.value = ""
        rms.value = 0f
        followUpListening = false
        if (mode.value == Mode.LISTENING || mode.value == Mode.WAKE_LISTENING) mode.value = Mode.IDLE
        refreshWakeListening()
    }

    fun stopSpeaking() {
        speaker.stop()
        if (mode.value == Mode.SPEAKING) mode.value = Mode.IDLE
        refreshWakeListening()
    }

    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
        refreshWakeListening()
    }

    fun setBackgroundServiceActive(active: Boolean) {
        backgroundServiceActive = active
        refreshWakeListening()
    }

    fun setAlwaysListening(enabled: Boolean) {
        if (enabled && !listener.onDeviceRecognitionAvailable()) {
            error.value = "Always listening requires Android 12+ with on-device speech recognition available. Tap the microphone to speak instead."
            return
        }
        alwaysListeningEnabled = enabled
        if (!enabled) {
            if (followUpListening) stopListening() else stopWakeWordListening()
        }
        error.value = null
        scope.launch {
            settings.setAlwaysListening(enabled)
            refreshWakeListening()
        }
    }

    fun startListening(onDeviceOnly: Boolean = false) {
        if (!hasMic()) { error.value = "Microphone permission is required."; return }
        wakeGeneration++
        wakeRestartJob?.cancel()
        wakeRestartJob = null
        followUpListening = onDeviceOnly
        speaker.stop()
        error.value = null; partial.value = ""
        mode.value = Mode.LISTENING
        val started = listener.start(prefs.value.language,
            onPartial = { partial.value = it },
            onFinal = { partial.value = ""; rms.value = 0f; followUpListening = false; submit(it) },
            onEnd = {
                partial.value = ""; rms.value = 0f
                followUpListening = false
                if (mode.value == Mode.LISTENING) mode.value = Mode.IDLE
                it?.let { message -> error.value = message }
                refreshWakeListening()
            },
            onRms = { rms.value = ((it + 2f) / 12f).coerceIn(0f, 1f) },
            onDeviceOnly = onDeviceOnly)
        if (!started) {
            mode.value = Mode.IDLE
            refreshWakeListening()
        }
    }

    private fun canWakeListen() =
        alwaysListeningEnabled && hasMic() && (activityVisible || backgroundServiceActive)

    private fun refreshWakeListening() {
        if (canWakeListen()) {
            if (mode.value == Mode.IDLE) scheduleWakeRestart(250L)
        } else {
            if (mode.value == Mode.WAKE_LISTENING) {
                stopWakeWordListening()
            } else if (!activityVisible && !backgroundServiceActive && mode.value == Mode.LISTENING) {
                wakeGeneration++
                wakeRestartJob?.cancel()
                wakeRestartJob = null
                listener.stop()
                followUpListening = false
                partial.value = ""
                rms.value = 0f
                mode.value = Mode.IDLE
            }
        }
    }

    private fun stopWakeWordListening() {
        wakeGeneration++
        wakeRestartJob?.cancel()
        wakeRestartJob = null
        if (mode.value == Mode.WAKE_LISTENING) {
            listener.stop()
            partial.value = ""
            rms.value = 0f
            mode.value = Mode.IDLE
        }
    }

    private fun scheduleWakeRestart(delayMs: Long = wakeRetryDelay) {
        if (!canWakeListen() || mode.value != Mode.IDLE) return
        wakeRestartJob?.cancel()
        val expectedGeneration = wakeGeneration
        wakeRetryDelay = (delayMs * 2).coerceAtMost(2_000L)
        wakeRestartJob = scope.launch {
            delay(delayMs)
            if (expectedGeneration == wakeGeneration && canWakeListen() && mode.value == Mode.IDLE) {
                listenForWakeWord(expectedGeneration)
            }
        }
    }

    private fun listenForWakeWord(generation: Int) {
        if (generation != wakeGeneration || !canWakeListen() || mode.value != Mode.IDLE) return
        if (!wakeWordAvailable) {
            error.value = "On-device speech recognition is unavailable. Always listening has stopped."
            alwaysListeningEnabled = false
            scope.launch { settings.setAlwaysListening(false) }
            return
        }
        partial.value = ""
        error.value = null
        mode.value = Mode.WAKE_LISTENING
        val started = listener.start(
            lang = "en-US",
            onPartial = { partial.value = it },
            onFinal = { transcript -> handleWakeTranscript(generation, transcript) },
            onEnd = { message ->
                if (generation == wakeGeneration) {
                    partial.value = ""
                    rms.value = 0f
                    if (mode.value == Mode.WAKE_LISTENING) mode.value = Mode.IDLE
                    if (message == null) {
                        wakeFailureCount = 0
                    } else {
                        error.value = message
                        wakeFailureCount++
                        if (wakeFailureCount >= 3) {
                            error.value = "$message Always listening stopped; check that the on-device English speech model is installed."
                            alwaysListeningEnabled = false
                            scope.launch { settings.setAlwaysListening(false) }
                            return@start
                        }
                    }
                    scheduleWakeRestart(if (message == null) 500L else wakeRetryDelay)
                }
            },
            onRms = { rms.value = ((it + 2f) / 12f).coerceIn(0f, 1f) },
            onDeviceOnly = true
        )
        if (!started && generation == wakeGeneration) {
            mode.value = Mode.IDLE
            alwaysListeningEnabled = false
            scope.launch { settings.setAlwaysListening(false) }
        }
    }

    private fun handleWakeTranscript(generation: Int, transcript: String) {
        if (generation != wakeGeneration || !canWakeListen()) return
        partial.value = ""
        rms.value = 0f
        wakeFailureCount = 0
        mode.value = Mode.IDLE
        val command = WakeWordDetector.commandAfterWakeWord(transcript)
        if (command == null) {
            scheduleWakeRestart(500L)
        } else {
            wakeRetryDelay = 1_000L
            if (command.isBlank()) startListening(onDeviceOnly = true) else submit(command)
        }
    }

    /** Speak in the script of the reply, so a Hindi/Urdu answer is read with a matching voice. */
    private fun langFor(text: String) = when {
        text.any { it in '\u0600'..'\u06FF' } -> "ur-PK"
        text.any { it in '\u0900'..'\u097F' } -> "hi-IN"
        else -> "en-US"
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        speaker.stop()
        mode.value = Mode.THINKING
        scope.launch {
            withContext(Dispatchers.IO) { dao.insert(Message(role = "user", text = text)) }
            val reply = router.route(text) ?: ai.ask(withContext(Dispatchers.IO) { dao.last(30) }, prefs.value.language)
            val text2 = reply.ifBlank { "Done." }
            withContext(Dispatchers.IO) { dao.insert(Message(role = "assistant", text = text2)) }
            mode.value = Mode.SPEAKING
            val p = prefs.value
            speaker.speak(text2, langFor(text2), p.rate, p.pitch) {
                if (mode.value == Mode.SPEAKING) mode.value = Mode.IDLE
                if (canWakeListen()) startListening(onDeviceOnly = true) else refreshWakeListening()
            }
        }
    }

    fun clearHistory() { scope.launch(Dispatchers.IO) { dao.clear() } }
    fun toggleNote(n: Note) { scope.launch(Dispatchers.IO) { noteDao.update(n.copy(done = !n.done)) } }
    fun deleteNote(n: Note) { scope.launch(Dispatchers.IO) { noteDao.delete(n) } }
    fun clearNotes() { scope.launch(Dispatchers.IO) { noteDao.clear() } }
}
