package com.example.mistreal_mini.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/**
 * minSdk is 26 (Android O), so NotificationChannel is always available —
 * no Build.VERSION.SDK_INT gate needed here, unlike the pre-channel-era
 * code this replaces. A channel's sound can only ever be set at its FIRST
 * creation — Android silently ignores a different sound on a later
 * createNotificationChannel call for an id that already exists, by design
 * (so apps can't override a user's own channel-sound choice later).
 */
object NotificationChannels {
    fun ensure(
        context: Context, id: String, name: String, importance: Int,
        description: String? = null, sound: Uri? = null, audioAttributes: AudioAttributes? = null
    ) {
        val channel = NotificationChannel(id, name, importance).apply {
            if (description != null) this.description = description
            if (sound != null) setSound(sound, audioAttributes)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * The one channel ID both MistrealFirebaseMessagingService and
     * CallService create — factored out so they can't drift into two
     * different sound/importance configs for what's supposed to be the
     * same channel (the system treats the id as one channel regardless;
     * only the first caller's config actually sticks).
     */
    const val CALL_CHANNEL_ID = "CALL_CHANNEL"

    fun ensureCallChannel(context: Context) {
        ensure(
            context, CALL_CHANNEL_ID, "Mistreal Calls", NotificationManager.IMPORTANCE_HIGH,
            sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
    }
}
