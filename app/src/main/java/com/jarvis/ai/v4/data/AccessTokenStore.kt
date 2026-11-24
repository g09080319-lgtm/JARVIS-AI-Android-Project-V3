package com.jarvis.ai.v4.data

import android.content.Context

class AccessTokenStore(context: Context) {
    private val encryptedPreferences = EncryptedPreferenceStore(
        context,
        "jarvis_v4_credentials",
        "jarvis-v4-api-token",
    )

    fun read(): String? = encryptedPreferences.read(CIPHERTEXT)

    fun write(token: String) = encryptedPreferences.write(CIPHERTEXT, token)

    fun clear() = encryptedPreferences.clear(CIPHERTEXT)

    private companion object {
        const val CIPHERTEXT = "access_token"
    }
}
