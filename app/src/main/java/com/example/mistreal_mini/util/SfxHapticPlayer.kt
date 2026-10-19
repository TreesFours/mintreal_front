package com.example.mistreal_mini.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide short-SFX + haptic feedback — one shared instance for the whole
 * app's lifetime (same DI shape as VoiceRecorder/SosCameraCapture), backed
 * by SoundPool rather than MediaPlayer since these are short, can overlap,
 * and need low latency, unlike voice-note playback.
 *
 * Two tiers only, by design: a soft tap for primary actions, a distinct
 * "water drop" for toggle/switch flips, and a louder alert+vibration for
 * SOS/Guardian triggers. Deliberately not wired to every clickable in the
 * app — see the implementation plan for why.
 */
@Singleton
class SfxHapticPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val tapSoundId = loadSound("sfx_tap")
    private val toggleSoundId = loadSound("sfx_toggle")
    private val alertSoundId = loadSound("sfx_alert")

    private fun loadSound(rawResourceName: String): Int? {
        val resId = context.resources.getIdentifier(rawResourceName, "raw", context.packageName)
        if (resId == 0) {
            Timber.w("SfxHapticPlayer: res/raw/$rawResourceName not found — this sound will no-op until it's added")
            return null
        }
        return try {
            soundPool.load(context, resId, 1)
        } catch (e: Exception) {
            Timber.w(e, "SfxHapticPlayer: failed to load $rawResourceName")
            null
        }
    }

    private fun play(soundId: Int?) {
        if (soundId == null) return
        try {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
        } catch (e: Exception) {
            Timber.w(e, "SfxHapticPlayer: failed to play sound")
        }
    }

    private fun vibrate(durationMillis: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMillis)
            }
        } catch (e: Exception) {
            Timber.w(e, "SfxHapticPlayer: failed to vibrate")
        }
    }

    fun playTap() = play(tapSoundId)

    fun playToggle() {
        play(toggleSoundId)
        vibrate(15)
    }

    /**
     * SOS/Guardian trigger — must never throw or block the real distress
     * flow it accompanies; both play() and vibrate() already swallow their
     * own exceptions internally for exactly that reason.
     */
    fun playAlert() {
        play(alertSoundId)
        vibrate(250)
    }
}
