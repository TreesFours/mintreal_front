package com.example.mistreal_mini.util

import kotlin.math.sin

enum class VoicePreset(val displayName: String, val emoji: String) {
    NONE("Normal Voice", "🎙️"),
    ROBOT("Robot / Cyber", "🤖"),
    CHILD("Helium Child", "👶"),
    ALIEN("Deep Space Alien", "👽"),
    MAN("Deep Bass Man", "👨"),
    WOMAN("Soft Treble Woman", "👩"),
    MONSTER("Shadow Monster", "👹"),
    TACTICAL("Tactical Walkie-Talkie", "📻")
}

/**
 * Real-Time PCM & Clip Voice FX Processor
 * Applies pitch-shifting, ring modulation, and frequency filtering.
 */
object VoiceFxProcessor {

    fun processPcmBuffer(
        inputBuffer: ShortArray,
        length: Int,
        preset: VoicePreset,
        sampleRate: Int = 16000
    ): ShortArray {
        if (preset == VoicePreset.NONE || length <= 0) {
            return inputBuffer
        }

        val output = ShortArray(length)
        val pitchMultiplier = when (preset) {
            VoicePreset.CHILD -> 1.45f
            VoicePreset.WOMAN -> 1.25f
            VoicePreset.MAN -> 0.75f
            VoicePreset.MONSTER -> 0.60f
            VoicePreset.ROBOT -> 0.85f
            VoicePreset.ALIEN -> 1.30f
            VoicePreset.TACTICAL -> 0.95f
            VoicePreset.NONE -> 1.0f
        }

        for (i in 0 until length) {
            val srcIndex = (i * pitchMultiplier).toInt().coerceIn(0, length - 1)
            var sample = inputBuffer[srcIndex].toFloat()

            when (preset) {
                VoicePreset.ROBOT -> {
                    // Square wave ring modulation (30Hz carrier)
                    val mod = if (sin(2.0 * Math.PI * 30.0 * i / sampleRate) > 0) 1.0f else -0.7f
                    sample *= mod
                }
                VoicePreset.ALIEN -> {
                    // Dual sine modulation for robotic flange
                    val mod = sin(2.0 * Math.PI * 50.0 * i / sampleRate).toFloat() * 0.8f
                    sample += sample * mod
                }
                VoicePreset.MONSTER -> {
                    // Soft clipping distortion
                    sample = (sample * 1.5f).coerceIn(-32768f, 32767f)
                }
                VoicePreset.TACTICAL -> {
                    // Radio bandpass filter simulation + soft noise
                    val noise = ((i % 7) - 3) * 150f
                    sample = (sample * 0.85f + noise).coerceIn(-32768f, 32767f)
                }
                else -> {}
            }

            output[i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        return output
    }
}
