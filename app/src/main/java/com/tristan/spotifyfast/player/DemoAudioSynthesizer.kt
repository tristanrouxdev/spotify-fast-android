package com.tristan.spotifyfast.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight PCM synthesizer used strictly in offline Demo Mode so playback controls
 * produce real audible music on-device without a Spotify account.
 */
class DemoAudioSynthesizer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null

    fun start(seedUri: String = "") {
        stop()
        playJob = scope.launch {
            runCatching {
                val sampleRate = 22_050
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(2048)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val chords = listOf(
                    doubleArrayOf(220.0, 261.63, 329.63), // Am
                    doubleArrayOf(174.61, 220.0, 261.63), // F
                    doubleArrayOf(130.81, 196.00, 261.63), // C
                    doubleArrayOf(196.00, 246.94, 293.66), // G
                )
                val shift = if (seedUri.hashCode() % 2 == 0) 1.0 else 1.12246
                val buffer = ShortArray(1024)
                var sampleIndex = 0L

                track.play()
                try {
                    while (isActive) {
                        val chordIdx = ((sampleIndex / (sampleRate * 2)) % chords.size).toInt()
                        val notes = chords[chordIdx]
                        for (i in buffer.indices) {
                            val t = (sampleIndex + i).toDouble() / sampleRate
                            val env = 0.7 + 0.3 * sin(2.0 * PI * 2.0 * t)
                            var sum = 0.0
                            for (freq in notes) {
                                sum += sin(2.0 * PI * (freq * shift) * t)
                            }
                            val sample = (sum / notes.size * env * 4800.0).toInt().coerceIn(-32767, 32767)
                            buffer[i] = sample.toShort()
                        }
                        sampleIndex += buffer.size
                        track.write(buffer, 0, buffer.size)
                    }
                } finally {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
    }
}
