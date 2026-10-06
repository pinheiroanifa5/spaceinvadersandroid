package com.spaceinvaders.spaceinvaders

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine {
    private val sampleRate = 22050
    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val track = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBuffer * 2,
        AudioTrack.MODE_STREAM
    )

    init {
        track.play()
    }

    fun playPlayerShot() = playTone(700f, 110, 0.18f)

    fun playInvaderExplosion() = playTone(1800f, 90, 0.22f)

    fun playPlayerExplosion() = playNoise(220, 0.35f)

    fun playMotherShipTone() = playTone(1500f, 80, 0.12f)

    fun playBeat(frequency: Float) = playTone(frequency, 80, 0.09f)

    fun release() {
        track.stop()
        track.release()
    }

    private fun playTone(frequency: Float, durationMs: Int, volume: Float) {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)
        val base = 2.0 * PI * frequency / sampleRate
        val decay = 0.92

        for (i in buffer.indices) {
            val t = i.toDouble()
            val envelope = (1.0 - (i.toDouble() / totalSamples.toDouble())) * decay
            val square = if (sin(base * t) >= 0.0) 1.0 else -1.0
            val sample = (square * volume * envelope * Short.MAX_VALUE).toInt()
            buffer[i] = sample.toInt().toShort()
        }
        track.write(buffer, 0, buffer.size)
    }

    private fun playNoise(durationMs: Int, volume: Float) {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)
        var previous = 0

        for (i in buffer.indices) {
            val random = Random.nextInt(-32768, 32767)
            previous = ((previous * 0.7f) + (random * 0.3f)).toInt().coerceIn(-32768, 32767)
            buffer[i] = ((previous * volume).toDouble()).coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        track.write(buffer, 0, buffer.size)
    }
}
