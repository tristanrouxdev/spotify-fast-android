package com.tristan.spotifyfast.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "spotify_fast_auth")

@Serializable
data class AuthTokenBundle(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val isDemoMode: Boolean = false,
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis(), bufferMs: Long = 60_000L): Boolean =
        nowMs + bufferMs >= expiresAtEpochMs
}

interface TokenCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertextWithIv: ByteArray): ByteArray
}

/**
 * AES-256-GCM cipher backed by AndroidKeyStore, with a deterministic persisted fallback key
 * when running under JVM/Robolectric or if Keystore is unavailable during early boot.
 */
class AndroidKeyStoreTokenCipher(
    private val keyAlias: String = "spotify_fast_aes_gcm_key",
    private val persistedFallbackKeyProvider: suspend () -> ByteArray = { DEFAULT_JVM_FALLBACK_KEY },
) : TokenCipher {

    private suspend fun resolveFallbackKey(): SecretKey {
        val raw = persistedFallbackKeyProvider()
        val keyBytes = if (raw.size == 32) raw else raw.copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun getKeyStoreKeyOrNull(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getKeyStoreKeyOrNull() ?: kotlinx.coroutines.runBlocking { resolveFallbackKey() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    override fun decrypt(ciphertextWithIv: ByteArray): ByteArray {
        require(ciphertextWithIv.size > 13) { "Malformed encrypted payload" }
        val ivLen = ciphertextWithIv[0].toInt() and 0xFF
        val iv = ciphertextWithIv.copyOfRange(1, 1 + ivLen)
        val payload = ciphertextWithIv.copyOfRange(1 + ivLen, ciphertextWithIv.size)
        val primaryKey = getKeyStoreKeyOrNull()
        if (primaryKey != null) {
            val attempt = runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, primaryKey, GCMParameterSpec(128, iv))
                cipher.doFinal(payload)
            }
            if (attempt.isSuccess) return attempt.getOrThrow()
        }
        val fallback = kotlinx.coroutines.runBlocking { resolveFallbackKey() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, fallback, GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    companion object {
        private val DEFAULT_JVM_FALLBACK_KEY =
            "spotify_fast_persistent_aes_256_key".toByteArray().copyOf(32)
    }
}

/**
 * Non-blocking DataStore + AES-256-GCM token store.
 * Exposes plaintext `isAuthenticated` immediately for Frame 1 routing while deferring
 * Keystore decryption to `Dispatchers.IO`.
 */
class EncryptedTokenStore(
    private val dataStore: DataStore<Preferences>,
    cipher: TokenCipher? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val tokenCipher: TokenCipher = cipher ?: AndroidKeyStoreTokenCipher(
        persistedFallbackKeyProvider = { getOrCreatePersistedFallbackSeed() },
    )

    constructor(context: Context) : this(
        dataStore = context.applicationContext.authDataStore,
        cipher = null,
        ioDispatcher = Dispatchers.IO,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val ioMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cachedBundle = AtomicReference<AuthTokenBundle?>(null)

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticatedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_AUTHENTICATED] == true
    }

    val isDemoModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_DEMO_MODE] == true
    }

    val clientIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CLIENT_ID].orEmpty()
    }

    val userProfileNameFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_USER_PROFILE_NAME].orEmpty()
    }

    val userProductFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_USER_PRODUCT].orEmpty()
    }

    val githubRepoFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GITHUB_REPO]?.takeIf { it.isNotBlank() } ?: DEFAULT_GITHUB_REPO
    }

    val isAuthenticatedState: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _isAuthenticated.value = prefs[KEY_IS_AUTHENTICATED] == true
            }
        }
    }

    private suspend fun getOrCreatePersistedFallbackSeed(): ByteArray {
        val existing = dataStore.data.first()[KEY_FALLBACK_SEED]
        if (!existing.isNullOrBlank()) {
            val decoded = runCatching { Base64.decode(existing, Base64.NO_WRAP) }.getOrNull()
            if (decoded != null && decoded.size == 32) return decoded
        }
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(generated, Base64.NO_WRAP)
        dataStore.edit { prefs -> prefs[KEY_FALLBACK_SEED] = encoded }
        return generated
    }

    fun getCachedBundleOrNull(): AuthTokenBundle? = cachedBundle.get()

    suspend fun loadTokenBundle(): AuthTokenBundle? = withContext(ioDispatcher) {
        cachedBundle.get()?.let { return@withContext it }
        ioMutex.withLock {
            cachedBundle.get()?.let { return@withLock it }
            val prefs = dataStore.data.first()
            val encoded = prefs[KEY_ENCRYPTED_BUNDLE] ?: return@withLock null
            val bundle = runCatching {
                val rawBytes = Base64.decode(encoded, Base64.NO_WRAP)
                val decryptedJson = tokenCipher.decrypt(rawBytes).decodeToString()
                json.decodeFromString<AuthTokenBundle>(decryptedJson)
            }.getOrNull()
            if (bundle != null) {
                cachedBundle.set(bundle)
            }
            bundle
        }
    }

    suspend fun saveTokenBundle(bundle: AuthTokenBundle) = withContext(ioDispatcher) {
        ioMutex.withLock {
            val serialized = json.encodeToString(bundle).encodeToByteArray()
            val encrypted = tokenCipher.encrypt(serialized)
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            dataStore.edit { prefs ->
                prefs[KEY_IS_AUTHENTICATED] = true
                prefs[KEY_IS_DEMO_MODE] = bundle.isDemoMode
                prefs[KEY_CLIENT_ID] = bundle.clientId
                prefs[KEY_ENCRYPTED_BUNDLE] = encoded
            }
            cachedBundle.set(bundle)
            _isAuthenticated.value = true
        }
    }

    suspend fun saveUserProfile(name: String, product: String) = withContext(ioDispatcher) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_PROFILE_NAME] = name
            prefs[KEY_USER_PRODUCT] = product
        }
    }

    suspend fun saveClientId(clientId: String) = withContext(ioDispatcher) {
        dataStore.edit { prefs ->
            prefs[KEY_CLIENT_ID] = clientId.trim()
        }
    }

    suspend fun saveGithubRepo(repoSlug: String) = withContext(ioDispatcher) {
        val clean = repoSlug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        if (clean.contains("/")) {
            dataStore.edit { prefs -> prefs[KEY_GITHUB_REPO] = clean }
        }
    }

    suspend fun getSyncSchemaVersion(): Int = withContext(ioDispatcher) {
        dataStore.data.first()[KEY_SYNC_SCHEMA_VERSION] ?: 0
    }

    suspend fun setSyncSchemaVersion(version: Int) = withContext(ioDispatcher) {
        dataStore.edit { prefs -> prefs[KEY_SYNC_SCHEMA_VERSION] = version }
    }

    suspend fun enableDemoMode(clientId: String = "demo-client") = withContext(ioDispatcher) {
        saveUserProfile("Mode Démo", "offline")
        saveTokenBundle(
            AuthTokenBundle(
                clientId = clientId,
                accessToken = "demo-access-token",
                refreshToken = "demo-refresh-token",
                expiresAtEpochMs = Long.MAX_VALUE,
                isDemoMode = true,
            ),
        )
    }

    suspend fun clear() = withContext(ioDispatcher) {
        ioMutex.withLock {
            dataStore.edit { prefs ->
                prefs.remove(KEY_IS_AUTHENTICATED)
                prefs.remove(KEY_IS_DEMO_MODE)
                prefs.remove(KEY_ENCRYPTED_BUNDLE)
                prefs.remove(KEY_USER_PROFILE_NAME)
                prefs.remove(KEY_USER_PRODUCT)
            }
            cachedBundle.set(null)
            _isAuthenticated.value = false
        }
    }

    companion object {
        private val KEY_IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
        private val KEY_IS_DEMO_MODE = booleanPreferencesKey("is_demo_mode")
        private val KEY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
        private val KEY_ENCRYPTED_BUNDLE = stringPreferencesKey("encrypted_token_bundle")
        private val KEY_FALLBACK_SEED = stringPreferencesKey("aes_fallback_seed")
        private val KEY_USER_PROFILE_NAME = stringPreferencesKey("spotify_user_name")
        private val KEY_USER_PRODUCT = stringPreferencesKey("spotify_user_product")
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo_slug")
        private val KEY_SYNC_SCHEMA_VERSION = intPreferencesKey("sync_schema_version")
        const val DEFAULT_GITHUB_REPO = "tristanrouxdev/spotify-fast-android"
    }
}
