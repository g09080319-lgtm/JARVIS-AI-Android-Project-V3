package com.jarvis.ai.v4.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthClient(private val activity: Activity) {
    suspend fun getIdToken(serverClientId: String): String {
        require(serverClientId.isNotBlank()) { "Google Sign-In is not configured for this build." }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (exception: NoCredentialException) {
            throw IllegalStateException(
                "No Google account is available. Add an account to this device and try again.",
                exception,
            )
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google Sign-In did not return a Google ID token.")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
