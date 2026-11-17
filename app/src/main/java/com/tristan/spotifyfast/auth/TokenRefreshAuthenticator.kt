package com.tristan.spotifyfast.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Injects the current access token in O(1) from memory (or loads from DataStore if needed)
 * and honors Spotify `429 Retry-After` throttling headers without blocking the UI.
 */
class SpotifyAuthInterceptor(
    private val tokenStore: EncryptedTokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val bundle = tokenStore.getCachedBundleOrNull()
            ?: runBlocking { tokenStore.loadTokenBundle() }

        val request = if (bundle != null && bundle.accessToken.isNotBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${bundle.accessToken}")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 10L)
            if (retryAfterSeconds != null) {
                response.close()
                Thread.sleep(retryAfterSeconds * 1000L)
                return chain.proceed(request)
            }
        }
        return response
    }
}

/**
 * Thread-safe OkHttp Authenticator that refreshes the Spotify OAuth token at most once
 * across concurrent `401 Unauthorized` responses using a coroutine `Mutex`.
 */
class TokenRefreshAuthenticator(
    private val tokenStore: EncryptedTokenStore,
    private val pkceAuthManager: PkceAuthManager,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val updatedBundle = runBlocking {
            refreshMutex.withLock {
                val current = tokenStore.loadTokenBundle() ?: return@withLock null
                if (current.isDemoMode) return@withLock null
                // Another concurrent call already refreshed the token
                if (failedToken != null && current.accessToken != failedToken && !current.isExpired()) {
                    return@withLock current
                }
                runCatching { pkceAuthManager.refreshAccessToken(current) }.getOrNull()
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${updatedBundle.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
