package com.tristan.spotifyfast.data.sync

data class SyncedLyricLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * Parses `.lrc` synced lyrics (including multi-timestamp lines), normalizes track titles for LRCLIB,
 * and locates the active lyric line via binary search.
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val REMASTER_DASH_REGEX = Regex("""\s*-\s*(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?.*$""", RegexOption.IGNORE_CASE)
    private val PAREN_FEAT_REGEX = Regex("""\s*[\(\[](?:feat\.?|ft\.?|remaster(?:ed)?|live|deluxe|radio edit|mono|stereo)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE)

    fun normalizeTrackTitle(rawTitle: String): String {
        return rawTitle
            .replace(PAREN_FEAT_REGEX, "")
            .replace(REMASTER_DASH_REGEX, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    fun parse(lrcContent: String?): List<SyncedLyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val parsed = mutableListOf<SyncedLyricLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.replace(TIMESTAMP_REGEX, "").trim()
            if (text.isBlank()) return@forEach
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = minutes * 60_000L + seconds * 1_000L + millis
                parsed += SyncedLyricLine(timestampMs = totalMs, text = text)
            }
        }
        return parsed.sortedBy { it.timestampMs }
    }

    fun activeLineIndexAt(lines: List<SyncedLyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines.first().timestampMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
