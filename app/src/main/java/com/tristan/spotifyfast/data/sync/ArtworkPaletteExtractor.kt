package com.tristan.spotifyfast.data.sync

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tristan.spotifyfast.data.local.DEFAULT_DOMINANT_COLOR
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts dominant artwork colors safely off the UI thread using low-res software bitmaps
 * (`allowHardware(false)`) so `Palette` never throws on `Bitmap.Config.HARDWARE`.
 */
class ArtworkPaletteExtractor(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val colorCache = ConcurrentHashMap<String, Int>()

    suspend fun extractDominantColor(artworkUrl: String?): Int {
        if (artworkUrl.isNullOrBlank()) return DEFAULT_DOMINANT_COLOR
        colorCache[artworkUrl]?.let { return it }

        val extracted = withContext(defaultDispatcher) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(64, 64)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request) as? SuccessResult ?: return@runCatching DEFAULT_DOMINANT_COLOR
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching DEFAULT_DOMINANT_COLOR
                val palette = Palette.from(bitmap).generate()
                palette.getVibrantColor(
                    palette.getDarkVibrantColor(
                        palette.getMutedColor(DEFAULT_DOMINANT_COLOR),
                    ),
                )
            }.getOrDefault(DEFAULT_DOMINANT_COLOR)
        }
        colorCache[artworkUrl] = extracted
        return extracted
    }
}
