package com.jarvis.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordDetectorTest {
    @Test
    fun extractsCommandAfterWakeWordCaseInsensitively() {
        assertEquals(
            "what time is it",
            WakeWordDetector.commandAfterWakeWord("Hey, JARVIS, what time is it")
        )
    }

    @Test
    fun doesNotActivateWithoutWakePhrase() {
        assertNull(WakeWordDetector.commandAfterWakeWord("Jarvis is a helpful assistant"))
    }
}
