package com.jarvis.ai.v4.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SharedAiClientTest {
    @Test
    fun refusesNonHttpsBackendBeforeSendingCredentials() = runBlocking {
        val error = runCatching {
            SharedAiClient("http://localhost:8787").chat(
                token = "test-token",
                messages = listOf(ChatMessage("user", "hello")),
                language = "en-US",
            )
        }.exceptionOrNull()

        assertEquals("The shared AI backend URL must use HTTPS.", error?.message)
    }

    @Test
    fun chatUsesTheSecureBackendAndReturnsItsRealResponse() = runBlocking {
        val (server, http) = secureServer()
        try {
            server.enqueue(MockResponse().setBody("""{"reply":"Hello from the backend"}"""))
            val client = SharedAiClient(backendUrl(server), http)

            val reply = client.chat(
                token = "session-token",
                messages = listOf(ChatMessage("user", "hello")),
                language = "en-US",
            )

            assertEquals("Hello from the backend", reply)
            val request = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(request)
            assertEquals("Bearer session-token", request!!.getHeader("Authorization"))
            assertEquals("/v1/chat", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun googleIdTokenExchangesForBackendSessionAndRefreshesWithFreshGoogleToken() = runBlocking {
        val (server, http) = secureServer()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"access_token":"session-one","token_type":"Bearer","expires_in":900}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"access_token":"session-two","token_type":"Bearer","expires_in":900}""",
                ),
            )
            val client = SharedAiClient(backendUrl(server), http)

            val session = client.authenticateWithGoogle("signed-google-id-token")
            val refreshed = client.authenticateWithGoogle("fresh-google-id-token", refresh = true)

            assertEquals(BackendSession("session-one", 900), session)
            assertEquals(BackendSession("session-two", 900), refreshed)
            assertEquals("/v1/auth/google", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            val refreshRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/auth/refresh", refreshRequest.path)
            assertEquals(
                """{"idToken":"fresh-google-id-token"}""",
                refreshRequest.body.readUtf8(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun cancellingChatCancelsTheInFlightHttpRequest() = runBlocking {
        val (server, http) = secureServer()
        try {
            server.enqueue(
                MockResponse()
                    .setBodyDelay(4, TimeUnit.SECONDS)
                    .setBody("""{"reply":"late"}"""),
            )
            val client = SharedAiClient(backendUrl(server), http)
            val request = async(Dispatchers.IO) {
                client.chat(
                    token = "session-token",
                    messages = listOf(ChatMessage("user", "hello")),
                    language = "en-US",
                )
            }

            val recorded = withContext(Dispatchers.IO) {
                server.takeRequest(5, TimeUnit.SECONDS)
            }
            assertNotNull(recorded)
            withTimeout(2_000) { request.cancelAndJoin() }
            assertTrue(request.isCancelled)
        } finally {
            server.shutdown()
        }
    }

    private fun secureServer(): Pair<MockWebServer, OkHttpClient> {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        return server to client
    }

    private fun backendUrl(server: MockWebServer): String =
        server.url("/").newBuilder().host("localhost").build().toString().trimEnd('/')
}
