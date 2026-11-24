package com.jarvis.ai.voice

object WakeWordDetector {
    private val pattern = Regex("""\b(?:hey|ok(?:ay)?)[,\s]+jarvis\b""", RegexOption.IGNORE_CASE)

    fun commandAfterWakeWord(transcript: String): String? {
        val match = pattern.find(transcript) ?: return null
        return transcript.substring(match.range.last + 1).trim().trimStart(',', ':', '-', ' ')
    }
}
