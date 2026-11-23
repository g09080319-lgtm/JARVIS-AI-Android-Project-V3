package com.jarvis.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Speech capture for tap-to-talk or the explicitly enabled on-device wake-word loop. */
class VoiceListener(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    private var generation = 0

    fun onDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnd: (error: String?) -> Unit,
        onRms: (Float) -> Unit = {},
        onDeviceOnly: Boolean = false
    ): Boolean {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onEnd("Speech recognition is not available on this device.")
            return false
        }
        val r = if (onDeviceOnly) {
            if (!onDeviceRecognitionAvailable()) {
                onEnd("On-device speech recognition is unavailable. Always listening stays off.")
                return false
            }
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        val requestGeneration = ++generation
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {
                if (requestGeneration == generation && rec === r) onRms(v)
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                if (requestGeneration != generation || rec !== r) {
                    r.destroy()
                    return
                }
                rec = null
                r.destroy()
                onEnd(if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) null else "Voice recognition error ($e).")
            }
            override fun onPartialResults(b: Bundle?) {
                if (requestGeneration != generation || rec !== r) return
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(b: Bundle?) {
                if (requestGeneration != generation || rec !== r) {
                    r.destroy()
                    return
                }
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                rec = null
                r.destroy()
                if (t.isNullOrBlank()) onEnd(null) else onFinal(t)
            }
        })
        rec = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (onDeviceOnly) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
        return true
    }

    fun stop() {
        generation++
        rec?.let {
            it.cancel()
            it.destroy()
        }
        rec = null
    }
}
