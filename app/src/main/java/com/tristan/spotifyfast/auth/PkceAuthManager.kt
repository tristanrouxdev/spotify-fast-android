package com.tristan.spotifyfast.auth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class SpotifyTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresInSeconds: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
)

data class PkceChallengePair(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
)

data class LoopbackAuthResult(
    val code: String,
    val state: String?,
)

/**
 * OAuth 2.0 / 2.1 PKCE manager compliant with Spotify 2025/2026 loopback IP rules (`127.0.0.1:8888`).
 */
class PkceAuthManager(
    private val tokenStore: EncryptedTokenStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val accountsBaseUrl: String = SPOTIFY_ACCOUNTS_BASE_URL,
    private val redirectPort: Int = DEFAULT_REDIRECT_PORT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun generatePkceChallenge(): PkceChallengePair {
        val random = SecureRandom()
        val verifierBytes = ByteArray(64).also { random.nextBytes(it) }
        val codeVerifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val stateBytes = ByteArray(16).also { random.nextBytes(it) }
        val state = Base64.encodeToString(
            stateBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return PkceChallengePair(codeVerifier, codeChallenge, state)
    }

    fun redirectUri(port: Int = redirectPort): String = "http://127.0.0.1:$port/callback"

    fun buildAuthorizationUrl(
        clientId: String,
        challenge: PkceChallengePair,
        port: Int = redirectPort,
    ): String {
        val params = mapOf(
            "client_id" to clientId.trim(),
            "response_type" to "code",
            "redirect_uri" to redirectUri(port),
            "code_challenge_method" to "S256",
            "code_challenge" to challenge.codeChallenge,
            "state" to challenge.state,
            "scope" to REQUIRED_SCOPES,
            "show_dialog" to "false",
        )
        val encoded = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "${accountsBaseUrl.trimEnd('/')}/authorize?$encoded"
    }

    /**
     * Binds `127.0.0.1` loopback socket with `SO_REUSEADDR`, opens Chrome Custom Tabs,
     * awaits the callback code, returns an auto-closing HTML page, brings the app back
     * to the foreground, and exchanges the code for tokens.
     */
    suspend fun authenticateWithCustomTabs(
        context: Context,
        clientId: String,
        timeoutMs: Int = 180_000,
    ): Result<AuthTokenBundle> = runCatching {
        val cleanClientId = clientId.trim()
        require(cleanClientId.isNotEmpty()) { "Client ID requis" }
        tokenStore.saveClientId(cleanClientId)

        val pkce = generatePkceChallenge()
        val server = createLoopbackServer(redirectPort, timeoutMs)
        val actualPort = server.localPort
        val authUrl = buildAuthorizationUrl(cleanClientId, pkce, actualPort)

        withContext(Dispatchers.Main) {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        }

        val callback = awaitLoopbackCallback(server, expectedState = pkce.state)

        // Bring app back to foreground so the Custom Tab closes seamlessly
        withContext(Dispatchers.Main) {
            val returnIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (returnIntent != null) {
                returnIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
                context.startActivity(returnIntent)
            }
        }

        val bundle = exchangeCodeForTokens(
            clientId = cleanClientId,
            code = callback.code,
            codeVerifier = pkce.codeVerifier,
            redirectUri = redirectUri(actualPort),
        )
        tokenStore.saveTokenBundle(bundle)
        bundle
    }

    fun createLoopbackServer(port: Int = redirectPort, timeoutMs: Int = 180_000): ServerSocket {
        val loopback = InetAddress.getByName("127.0.0.1")
        return ServerSocket().apply {
            reuseAddress = true
            soTimeout = timeoutMs
            bind(java.net.InetSocketAddress(loopback, port), 4)
        }
    }

    suspend fun awaitLoopbackCallback(
        serverSocket: ServerSocket,
        expectedState: String? = null,
    ): LoopbackAuthResult = withContext(ioDispatcher) {
        serverSocket.use { server ->
            while (true) {
                val socket = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    throw IllegalStateException("Authentification expirée (timeout loopback)", e)
                }
                val result = socket.use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use null
                    val target = requestLine.split(" ").getOrNull(1) ?: return@use null
                    if (!target.startsWith("/callback")) {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().write(notFound.toByteArray())
                        return@use null
                    }

                    val query = target.substringAfter("?", "")
                    val params = parseQueryParams(query)
                    val error = params["error"]
                    val code = params["code"]
                    val state = params["state"]

                    val htmlBody = if (error != null || code.isNullOrBlank()) {
                        """
                        <!doctype html><html><body style="background:#121212;color:#fff;font-family:sans-serif;text-align:center;padding:48px;">
                        <h2>Connexion Spotify interrompue</h2>
                        <p>${error ?: "Code manquant"}</p>
                        </body></html>
                        """.trimIndent()
                    } else {
                        """
                        <!doctype html><html><body style="background:#121212;color:#fff;font-family:sans-serif;text-align:center;padding:48px;">
                        <h2 style="color:#1DB954;">Connexion réussie</h2>
                        <p>Retour automatique vers Spotify Fast...</p>
                        <script>try{window.close();}catch(e){}</script>
                        </body></html>
                        """.trimIndent()
                    }

                    val response = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/html; charset=utf-8\r\n")
                        append("Content-Length: ${htmlBody.toByteArray(StandardCharsets.UTF_8).size}\r\n")
                        append("Connection: close\r\n\r\n")
                        append(htmlBody)
                    }
                    client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
                    client.getOutputStream().flush()

                    if (!error.isNullOrBlank()) {
                        throw IllegalStateException("Spotify OAuth error: $error")
                    }
                    require(!code.isNullOrBlank()) { "Code OAuth manquant dans le callback" }
                    if (expectedState != null) {
                        require(state == expectedState) { "OAuth state mismatch" }
                    }
                    LoopbackAuthResult(code = code, state = state)
                }
                if (result != null) return@withContext result
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    suspend fun exchangeCodeForTokens(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String = redirectUri(),
    ): AuthTokenBundle = withContext(ioDispatcher) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("${accountsBaseUrl.trimEnd('/')}/api/token")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Token exchange failed (${response.code}): $raw"
            }
            val dto = json.decodeFromString<SpotifyTokenResponseDto>(raw)
            AuthTokenBundle(
                clientId = clientId,
                accessToken = dto.accessToken,
                refreshToken = dto.refreshToken.orEmpty(),
                expiresAtEpochMs = System.currentTimeMillis() + dto.expiresInSeconds * 1000L,
            )
        }
    }

    suspend fun refreshAccessToken(current: AuthTokenBundle): AuthTokenBundle = withContext(ioDispatcher) {
        require(current.refreshToken.isNotBlank()) { "No refresh_token available" }
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", current.refreshToken)
            .add("client_id", current.clientId)
            .build()

        val request = Request.Builder()
            .url("${accountsBaseUrl.trimEnd('/')}/api/token")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Token refresh failed (${response.code}): $raw"
            }
            val dto = json.decodeFromString<SpotifyTokenResponseDto>(raw)
            val updated = AuthTokenBundle(
                clientId = current.clientId,
                accessToken = dto.accessToken,
                refreshToken = dto.refreshToken?.takeIf { it.isNotBlank() } ?: current.refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + dto.expiresInSeconds * 1000L,
                isDemoMode = current.isDemoMode,
            )
            tokenStore.saveTokenBundle(updated)
            updated
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
    }

    companion object {
        const val SPOTIFY_ACCOUNTS_BASE_URL = "https://accounts.spotify.com"
        const val DEFAULT_REDIRECT_PORT = 8888
        const val REQUIRED_SCOPES =
            "user-read-private user-follow-read user-library-read user-library-modify " +
                "playlist-read-private playlist-read-collaborative " +
                "user-read-playback-state user-modify-playback-state " +
                "user-read-currently-playing user-read-recently-played " +
                "streaming app-remote-control"

        fun readAppSigningSha1(context: Context): String {
            return runCatching {
                val pm = context.packageManager
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    info.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                }
                val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching "Unavailable"
                val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
                digest.joinToString(":") { "%02X".format(it) }
            }.getOrDefault("Unavailable")
        }
    }
}
