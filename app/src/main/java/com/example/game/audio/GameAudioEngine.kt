package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

class GameAudioEngine {

    private val audioScope = CoroutineScope(Dispatchers.Default)

    /**
     * Synthesizes and plays a custom audio waveform on a background thread.
     */
    fun playSound(
        type: SoundType,
        volume: Float = 0.5f
    ) {
        audioScope.launch {
            try {
                val sampleRate = 22050
                val data = when (type) {
                    SoundType.LASER -> generateLaser(sampleRate)
                    SoundType.EXPLOSION -> generateExplosion(sampleRate)
                    SoundType.PICKUP -> generatePickup(sampleRate)
                    SoundType.HIT -> generateHit(sampleRate)
                    SoundType.LEVEL_UP -> generateLevelUp(sampleRate)
                    SoundType.BOSS_WARNING -> generateBossWarning(sampleRate)
                    SoundType.SHIELD_UP -> generateShieldUp(sampleRate)
                }

                // Apply master volume scaling
                for (i in data.indices) {
                    data[i] = (data[i] * volume).coerceIn(-1f, 1f)
                }

                playPcm(data, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playPcm(floatBuffer: FloatArray, sampleRate: Int) {
        val shortBuffer = ShortArray(floatBuffer.size)
        for (i in floatBuffer.indices) {
            shortBuffer[i] = (floatBuffer[i] * Short.MAX_VALUE).toInt().toShort()
        }

        val bufferSize = shortBuffer.size * 2
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(shortBuffer, 0, shortBuffer.size)
        audioTrack.play()
        // Release resources dynamically
        CoroutineScope(Dispatchers.IO).launch {
            delayMs((shortBuffer.size * 1000L) / sampleRate + 150)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun delayMs(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    private fun generateLaser(sampleRate: Int): FloatArray {
        val duration = 0.15f // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)
        val fStart = 1100f
        val fEnd = 200f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = t / duration
            // Logarithmic/linear pitch slide
            val freq = fStart - (fStart - fEnd) * progress
            val phase = 2 * Math.PI * freq * t
            // Add square harmonics for sharp "retro laser" bite
            val raw = sin(phase).toFloat()
            val squareMod = if (raw > 0) 0.5f else -0.5f
            val sample = raw * 0.7f + squareMod * 0.3f
            
            // Fast attack, linear decay envelope
            val env = 1.0f - progress
            buffer[i] = sample * env
        }
        return buffer
    }

    private fun generateExplosion(sampleRate: Int): FloatArray {
        val duration = 0.4f // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)
        val random = Random()
        
        var filterCoeff = 0.15f // simulate a simple lowpass filter over time
        var lastOut = 0.2f

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // White noise source
            val white = random.nextFloat() * 2f - 1f
            
            // Lowpass decay simulation
            lastOut = lastOut + filterCoeff * (white - lastOut)
            
            // Envelope decays slowly
            val env = (1.0f - progress) * (1.0f - progress)
            buffer[i] = lastOut * env * 0.9f
            
            // Slide lowpass filter frequency down to sound like rumble
            filterCoeff = (0.22f * (1.0f - progress)).coerceAtLeast(0.02f)
        }
        return buffer
    }

    private fun generatePickup(sampleRate: Int): FloatArray {
        val duration = 0.22f // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)

        // Play an ascending arpeggio (C Major chord: E5, G5, C6)
        val notes = floatArrayOf(659.25f, 783.99f, 1046.50f)
        val numNotes = notes.size
        val samplesPerNote = numSamples / numNotes

        for (i in 0 until numSamples) {
            val noteIndex = (i / samplesPerNote).coerceAtMost(numNotes - 1)
            val baseFreq = notes[noteIndex]
            val t = i.toFloat() / sampleRate
            
            // Pulse wave for arcade warmth
            val phase = 2 * Math.PI * baseFreq * t
            val sine = sin(phase).toFloat()
            val sample = if (sine > 0.1f) 0.3f else -0.3f
            
            // Small envelope for inside note transitions
            val noteProgress = (i % samplesPerNote).toFloat() / samplesPerNote
            val env = 1.0f - noteProgress * 0.4f
            
            // Overall global fade-out transition
            val globalEnv = 1.0f - (i.toFloat() / numSamples)

            buffer[i] = sample * env * globalEnv
        }
        return buffer
    }

    private fun generateHit(sampleRate: Int): FloatArray {
        val duration = 0.2f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)
        val random = Random()

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val freq = 120f - 80f * progress
            val phase = 2 * Math.PI * freq * (i.toFloat() / sampleRate)
            // Harsh square wave modulated with random crackles for raw dynamic impact
            val raw = sin(phase).toFloat()
            val square = if (raw > 0) 0.5f else -0.5f
            val crackle = (random.nextFloat() * 2f - 1f) * 0.2f
            
            val env = 1.0f - progress
            buffer[i] = (square + crackle) * env
        }
        return buffer
    }

    private fun generateLevelUp(sampleRate: Int): FloatArray {
        val duration = 0.6f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)
        
        // Glorious Chiptune Arpeggio (C Major 7 to C Octave)
        val chord = floatArrayOf(261.63f, 329.63f, 392.00f, 493.88f, 523.25f, 659.25f, 783.99f, 1046.50f)
        val numNotes = chord.size
        val samplesPerNote = numSamples / numNotes

        for (i in 0 until numSamples) {
            val noteIndex = (i / samplesPerNote).coerceAtMost(numNotes - 1)
            val freq = chord[noteIndex]
            val t = i.toFloat() / sampleRate
            
            // Stack dual sine/triangle oscillators for shiny chiptune texture
            val phase = 2 * Math.PI * freq * t
            val mainSig = sin(phase).toFloat()
            val triSig = (abs((phase % (2 * Math.PI)) / Math.PI - 1) - 0.5).toFloat() * 2f
            val sample = mainSig * 0.6f + triSig * 0.4f
            
            val globalEnv = 1.0f - (i.toFloat() / numSamples) * 0.2f
            val noteProgress = (i % samplesPerNote).toFloat() / samplesPerNote
            val clickEnv = if (noteProgress < 0.15f) noteProgress / 0.15f else 1.0f - (noteProgress - 0.15f) / 0.85f

            buffer[i] = sample * clickEnv * globalEnv
        }
        return buffer
    }

    private fun generateBossWarning(sampleRate: Int): FloatArray {
        val duration = 0.8f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / sampleRate
            
            // Low industrial alarm drone - Dual square wave oscillators modulating at 55Hz and 56Hz
            val ph1 = 2 * Math.PI * 55f * t
            val ph2 = 2 * Math.PI * 56f * t
            val s1 = if (sin(ph1) > 0) 0.3f else -0.3f
            val s2 = if (sin(ph2) > 0) 0.3f else -0.3f
            val drone = s1 + s2
            
            // Pulse envelope wobbling back and forth 2 times
            val wobble = sin(2 * Math.PI * 4f * t).toFloat() * 0.2f + 0.8f
            val env = sin(Math.PI * progress).toFloat() // rise and fall curve
            
            buffer[i] = drone * env * wobble
        }
        return buffer
    }

    private fun generateShieldUp(sampleRate: Int): FloatArray {
        val duration = 0.3f
        val numSamples = (sampleRate * duration).toInt()
        val buffer = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val t = i.toFloat() / sampleRate
            // Ascending frequency sweep from 300Hz to 1200Hz
            val freq = 300f + 900f * progress * progress
            val phase = 2 * Math.PI * freq * t
            
            // Clean sine wave modulated with a tremolo for an energy shimmer
            val tremolo = sin(2 * Math.PI * 18f * t).toFloat() * 0.3f + 0.7f
            buffer[i] = sin(phase).toFloat() * tremolo * (1.0f - progress)
        }
        return buffer
    }

    private fun abs(value: Double): Double {
        return if (value < 0.0) -value else value
    }
}

enum class SoundType {
    LASER,
    EXPLOSION,
    PICKUP,
    HIT,
    LEVEL_UP,
    BOSS_WARNING,
    SHIELD_UP
}
