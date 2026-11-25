package com.jarvis.ai.v4.data

object BackendConfig {
    fun resolve(
        aivoProxyUrl: String?,
        jarvisProxyUrl: String?,
        jarvisApiBaseUrl: String?,
    ): String = sequenceOf(aivoProxyUrl, jarvisProxyUrl, jarvisApiBaseUrl)
        .map { it?.trim().orEmpty() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
