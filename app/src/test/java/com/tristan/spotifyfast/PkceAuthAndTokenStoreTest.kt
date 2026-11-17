package com.tristan.spotifyfast.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class PkceAuthAndTokenStoreTest {

    @Test
    fun `pkce S256 challenge matches RFC 7636 digest of verifier`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatcher = StandardTestDispatcher()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "pkce_test_${System.nanoTime()}.preferences_pb")
        }
        val store = EncryptedTokenStore(dataStore, ioDispatcher = dispatcher)
        val authManager = PkceAuthManager(store, ioDispatcher = dispatcher)

        val pair = authManager.generatePkceChallenge()
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(pair.codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        val expectedChallenge = Base64.encodeToString(
            expectedDigest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        assertEquals(expectedChallenge, pair.codeChallenge)
        assertFalse(pair.codeChallenge.contains("="))
        assertFalse(pair.codeChallenge.contains("+"))
        assertFalse(pair.codeChallenge.contains("/"))
    }

    @Test
    fun `loopback server captures code with SO_REUSEADDR and allows immediate port rebind`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "loopback_test_${System.nanoTime()}.preferences_pb")
        }
        val store = EncryptedTokenStore(dataStore)
        val authManager = PkceAuthManager(store)

        val socket1 = authManager.createLoopbackServer(port = 0, timeoutMs = 5_000)
        val boundPort = socket1.localPort

        val callbackJob = async(kotlinx.coroutines.Dispatchers.IO) {
            authManager.awaitLoopbackCallback(socket1, expectedState = "xyz123")
        }

        val (statusCode, html) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = URL("http://127.0.0.1:$boundPort/callback?code=auth_code_999&state=xyz123")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
            }
            conn.responseCode to conn.inputStream.bufferedReader().readText()
        }
        assertEquals(200, statusCode)
        assertTrue(html.contains("window.close()"))

        val result = callbackJob.await()
        assertEquals("auth_code_999", result.code)
        assertEquals("xyz123", result.state)

        // Immediate rebind on same port verifies SO_REUSEADDR
        val socket2 = authManager.createLoopbackServer(port = boundPort, timeoutMs = 1_000)
        socket2.close()
    }

    @Test
    fun `encrypted datastore roundtrip and concurrent 401 refresh fires only once`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.cacheDir, "store_test_${System.nanoTime()}.preferences_pb")
        }
        val store = EncryptedTokenStore(dataStore)
        val bundle = AuthTokenBundle(
            clientId = "client_abc",
            accessToken = "expired_token",
            refreshToken = "refresh_xyz",
            expiresAtEpochMs = System.currentTimeMillis() - 10_000L,
        )
        store.saveTokenBundle(bundle)

        // Simulate app restart with a brand new EncryptedTokenStore instance reading same DataStore
        val restartedStore = EncryptedTokenStore(dataStore)
        val reloadedAfterRestart = restartedStore.loadTokenBundle()
        assertNotNull(reloadedAfterRestart)
        assertEquals("expired_token", reloadedAfterRestart?.accessToken)
        assertEquals("refresh_xyz", reloadedAfterRestart?.refreshToken)
        assertEquals("client_abc", reloadedAfterRestart?.clientId)

        val server = MockWebServer()
        val refreshCount = AtomicInteger(0)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/token" -> {
                        val count = refreshCount.incrementAndGet()
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"access_token":"fresh_token_$count","expires_in":3600,"refresh_token":"refresh_xyz_2"}""")
                    }
                    "/v1/me" -> {
                        val auth = request.getHeader("Authorization")
                        if (auth?.startsWith("Bearer fresh_token_") == true) {
                            MockResponse().setResponseCode(200).setBody("""{"id":"user1"}""")
                        } else {
                            MockResponse().setResponseCode(401)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        try {
            val plainClient = OkHttpClient()
            val pkce = PkceAuthManager(
                tokenStore = store,
                httpClient = plainClient,
                accountsBaseUrl = server.url("/").toString(),
            )
            val authedClient = OkHttpClient.Builder()
                .addInterceptor(SpotifyAuthInterceptor(store))
                .authenticator(TokenRefreshAuthenticator(store, pkce))
                .build()

            val codes = (1..4).map {
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val req = Request.Builder().url(server.url("/v1/me")).build()
                    authedClient.newCall(req).execute().use { it.code }
                }
            }.awaitAll()

            assertTrue(codes.all { it == 200 })
            assertEquals(1, refreshCount.get())
            assertEquals("fresh_token_1", store.loadTokenBundle()?.accessToken)
        } finally {
            server.shutdown()
        }
    }
}
