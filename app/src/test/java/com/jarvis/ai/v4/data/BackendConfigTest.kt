package com.jarvis.ai.v4.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendConfigTest {
    @Test
    fun `AIVO proxy takes precedence`() {
        assertEquals(
            "https://aivo.example",
            BackendConfig.resolve(
                " https://aivo.example ",
                "https://jarvis.example",
                "https://legacy.example",
            ),
        )
    }

    @Test
    fun `legacy proxy remains supported`() {
        assertEquals(
            "https://jarvis.example",
            BackendConfig.resolve(null, "https://jarvis.example", "https://legacy.example"),
        )
    }

    @Test
    fun `legacy API base remains supported`() {
        assertEquals(
            "https://legacy.example",
            BackendConfig.resolve("", "", "https://legacy.example"),
        )
    }
}
