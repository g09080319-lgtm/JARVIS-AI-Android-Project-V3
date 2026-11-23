package com.jarvis.ai

import android.app.Application
import android.content.pm.PackageManager
import androidx.room.Room
import com.jarvis.ai.actions.CommandRouter
import com.jarvis.ai.data.*
import com.jarvis.ai.voice.Speaker
import com.jarvis.ai.voice.VoiceListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }

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

    val prefs: StateFlow<Prefs> = settings.flow.stateIn(scope, SharingStarted.Eagerly, Prefs())
    val messages: StateFlow<List<Message>> = dao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<Note>> = noteDao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val reminders: StateFlow<List<Reminder>> = reminderDao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val mode = MutableStateFlow(Mode.IDLE)
    val partial = MutableStateFlow("")
    val rms = MutableStateFlow(0f)
    val error = MutableStateFlow<String?>(null)

    init {
        // Without the user's consent, chat history never survives an app restart.
        scope.launch(Dispatchers.IO) { if (!settings.flow.first().saveHistory) dao.clear() }
    }

    fun hasMic() = app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun toggleListen() {
        when (mode.value) {
            Mode.LISTENING -> stopListening()
            Mode.SPEAKING -> stopSpeaking()
            Mode.THINKING -> Unit
            Mode.IDLE -> startListening()
        }
    }

    fun stopListening() {
        listener.stop()
        partial.value = ""
        rms.value = 0f
        if (mode.value == Mode.LISTENING) mode.value = Mode.IDLE
    }

    fun stopSpeaking() { speaker.stop(); if (mode.value == Mode.SPEAKING) mode.value = Mode.IDLE }

    fun startListening() {
        if (!hasMic()) { error.value = "Microphone permission is required."; return }
        speaker.stop()
        error.value = null; partial.value = ""
        mode.value = Mode.LISTENING
        listener.start(prefs.value.language,
            onPartial = { partial.value = it },
            onFinal = { partial.value = ""; rms.value = 0f; submit(it) },
            onEnd = { err -> partial.value = ""; rms.value = 0f; if (mode.value == Mode.LISTENING) mode.value = Mode.IDLE; err?.let { error.value = it } },
            onRms = { rms.value = ((it + 2f) / 12f).coerceIn(0f, 1f) })
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
            speaker.speak(text2, langFor(text2), p.rate, p.pitch) { if (mode.value == Mode.SPEAKING) mode.value = Mode.IDLE }
        }
    }

    fun clearHistory() { scope.launch(Dispatchers.IO) { dao.clear() } }
    fun toggleNote(n: Note) { scope.launch(Dispatchers.IO) { noteDao.update(n.copy(done = !n.done)) } }
    fun deleteNote(n: Note) { scope.launch(Dispatchers.IO) { noteDao.delete(n) } }
    fun clearNotes() { scope.launch(Dispatchers.IO) { noteDao.clear() } }
}
