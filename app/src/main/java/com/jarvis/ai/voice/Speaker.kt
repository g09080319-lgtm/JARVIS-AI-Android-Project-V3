package com.jarvis.ai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class Speaker(ctx: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { ready = it == TextToSpeech.SUCCESS }
    private val main = Handler(Looper.getMainLooper())

    fun speak(text: String, lang: String, rate: Float, pitch: Float, onDone: () -> Unit) {
        if (!ready) { onDone(); return }
        val res = tts.setLanguage(Locale.forLanguageTag(lang))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) { onDone(); return }
        tts.setSpeechRate(rate); tts.setPitch(pitch)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post(onDone) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post(onDone) }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}
