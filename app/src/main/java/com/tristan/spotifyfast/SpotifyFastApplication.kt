package com.tristan.spotifyfast

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.auth.PkceAuthManager
import com.tristan.spotifyfast.auth.SpotifyAuthInterceptor
import com.tristan.spotifyfast.auth.TokenRefreshAuthenticator
import com.tristan.spotifyfast.data.local.SpotifyFastDatabase
import com.tristan.spotifyfast.data.remote.EmptyBodyConverterFactory
import com.tristan.spotifyfast.data.remote.LrcLibApi
import com.tristan.spotifyfast.data.remote.SpotifyWebApi
import com.tristan.spotifyfast.data.sync.ArtworkPaletteExtractor
import com.tristan.spotifyfast.data.sync.LyricsRepository
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import com.tristan.spotifyfast.player.AppRemoteBridge
import com.tristan.spotifyfast.player.PlaybackRouter
import com.tristan.spotifyfast.player.PlaybackSessionService
import com.tristan.spotifyfast.player.SpotifyConnectManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ultra-light Application class (< 5 ms `onCreate`): all heavy singletons, Room, Keystore/DataStore,
 * OkHttp, and Coil disk cache are constructed lazily off the cold-start critical path.
 */
class SpotifyFastApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "coil_artwork_cache"))
                    .maxSizeBytes(250L * 1024L * 1024L) // 250 MB persistent disk cache
                    .build()
            }
            .crossfade(false)
            .build()
    }

    val tokenStore: EncryptedTokenStore by lazy {
        EncryptedTokenStore(appContext)
    }

    private val plainHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val pkceAuthManager: PkceAuthManager by lazy {
        PkceAuthManager(
            tokenStore = tokenStore,
            httpClient = plainHttpClient,
        )
    }

    private val spotifyOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(SpotifyAuthInterceptor(tokenStore))
            .addInterceptor(SpotifyRateLimitInterceptor())
            .authenticator(TokenRefreshAuthenticator(tokenStore, pkceAuthManager))
            .build()
    }

    val spotifyWebApi: SpotifyWebApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(spotifyOkHttpClient)
            .addConverterFactory(EmptyBodyConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpotifyWebApi::class.java)
    }

    val lrcLibApi: LrcLibApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(plainHttpClient)
            .addConverterFactory(EmptyBodyConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LrcLibApi::class.java)
    }

    val database: SpotifyFastDatabase by lazy {
        SpotifyFastDatabase.create(appContext)
    }

    val dao get() = database.spotifyDao()

    val paletteExtractor: ArtworkPaletteExtractor by lazy {
        ArtworkPaletteExtractor(appContext, imageLoader)
    }

    val syncEngine: OfflineFirstSyncEngine by lazy {
        OfflineFirstSyncEngine(
            dao = dao,
            api = spotifyWebApi,
            tokenStore = tokenStore,
            paletteExtractor = paletteExtractor,
        )
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            dao = dao,
            lrcLibApi = lrcLibApi,
        )
    }

    val appRemoteBridge: AppRemoteBridge by lazy {
        AppRemoteBridge(appContext)
    }

    val demoSynthesizer: com.tristan.spotifyfast.player.DemoAudioSynthesizer by lazy {
        com.tristan.spotifyfast.player.DemoAudioSynthesizer()
    }

    val connectManager: SpotifyConnectManager by lazy {
        SpotifyConnectManager(spotifyWebApi)
    }

    val appUpdateManager: com.tristan.spotifyfast.update.AppUpdateManager by lazy {
        com.tristan.spotifyfast.update.AppUpdateManager(
            context = appContext,
            httpClient = plainHttpClient,
        )
    }

    val playbackRouter: PlaybackRouter by lazy {
        PlaybackRouter(
            dao = dao,
            localTransport = appRemoteBridge,
            connectManager = connectManager,
            syncEngine = syncEngine,
            tokenStore = tokenStore,
            demoSynthesizer = demoSynthesizer,
            lyricsRepository = lyricsRepository,
            paletteExtractor = paletteExtractor,
            prefetchArtwork = { url ->
                val req = coil.request.ImageRequest.Builder(appContext)
                    .data(url)
                    .build()
                imageLoader.enqueue(req)
            },
            onRemoteSessionChanged = { isRemote ->
                PlaybackSessionService.syncRemoteSession(appContext, isRemote)
            },
        ).also { router ->
            PlaybackSessionService.sessionController = router
        }
    }
}

private class SpotifyRateLimitInterceptor(
    private val maxRetries: Int = 3,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0
        while (attempt < maxRetries && (response.code == 429 || response.code in 502..504)) {
            val retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull()
            val delayMs = if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
                (retryAfterSeconds * 1000L).coerceIn(500L, 10_000L)
            } else {
                (1000L * (attempt + 1)).coerceAtMost(5_000L)
            }
            response.close()
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.InterruptedIOException("Interrupted while waiting to retry Spotify request")
            }
            attempt++
            response = chain.proceed(request)
        }
        return response
    }
}
