package com.jarvis.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.JarvisApp
import com.jarvis.ai.data.Note
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val engine = (app as JarvisApp).engine
    val messages = engine.messages
    val notes = engine.notes
    val mode = engine.mode
    val partial = engine.partial
    val rms = engine.rms
    val error = engine.error
    val prefs = engine.prefs
    val wakeWordAvailable = engine.wakeWordAvailable

    fun send(t: String) = engine.submit(t)
    fun stopSpeaking() = engine.stopSpeaking()
    fun clearHistory() = engine.clearHistory()
    fun toggleNote(n: Note) = engine.toggleNote(n)
    fun deleteNote(n: Note) = engine.deleteNote(n)
    fun clearNotes() = engine.clearNotes()
    fun setLanguage(v: String) = viewModelScope.launch { engine.settings.setLanguage(v) }
    fun setRate(v: Float) = viewModelScope.launch { engine.settings.setRate(v) }
    fun setPitch(v: Float) = viewModelScope.launch { engine.settings.setPitch(v) }
    fun setSaveHistory(v: Boolean) = viewModelScope.launch { engine.settings.setSaveHistory(v); if (!v) engine.clearHistory() }
    fun setBackground(v: Boolean) = viewModelScope.launch { engine.settings.setBackground(v) }
    fun setAlwaysListening(v: Boolean) = engine.setAlwaysListening(v)
    fun setActivityVisible(v: Boolean) = engine.setActivityVisible(v)
    fun consentAsked() = viewModelScope.launch { engine.settings.setConsentAsked() }
}
