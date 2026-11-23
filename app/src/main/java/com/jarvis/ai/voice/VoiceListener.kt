package com.jarvis.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One-shot speech capture. Only listens after an explicit user action (tap or notification button). */
class VoiceListener(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnd: (error: String?) -> Unit,
        onRms: (Float) -> Unit = {}
    ) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onEnd("Speech recognition is not available on this device."); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { onRms(v) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                if (rec === r) rec = null
                r.destroy()
                onEnd(if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) null else "Voice error ($e)")
            }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (rec === r) rec = null
                r.destroy()
                if (t.isNullOrBlank()) onEnd(null) else onFinal(t)
            }
        })
        rec = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stop() { rec?.destroy(); rec = null }
}
