package com.jarvis.ai.v4.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedAiClientTest {
    @Test
    fun refusesNonHttpsBackendBeforeSendingCredentials() = runBlocking {
        val error = runCatching {
            SharedAiClient().chat(
                baseUrl = "http://localhost:8787",
                token = "test-token",
                messages = listOf(ChatMessage("user", "hello")),
                language = "en-US",
            )
        }.exceptionOrNull()

        assertEquals("The shared AI backend URL must use HTTPS.", error?.message)
    }
}
