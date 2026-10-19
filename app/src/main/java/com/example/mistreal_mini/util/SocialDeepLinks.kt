package com.example.mistreal_mini.util

import android.content.Intent
import android.net.Uri

/**
 * Messenger (native app with https://m.me fallback) and WhatsApp deep-link
 * construction, previously duplicated independently in ChatScreen.kt's
 * TopAppBar and BankChannelsSection.kt.
 */
object SocialDeepLinks {
    fun whatsApp(phoneNumber: String, prefilledMessage: String? = null): Intent {
        val text = Uri.encode(prefilledMessage ?: "")
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneNumber?text=$text"))
    }

    /**
     * Tries the native Messenger app first, falls back to the m.me web
     * link if it isn't installed. Takes a startActivity callback rather
     * than a Context so the try/catch works regardless of caller (Activity
     * context, onLaunch lambda, etc.).
     */
    fun openMessenger(participantId: String, startActivity: (Intent) -> Unit) {
        val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://user-thread/$participantId"))
        try {
            startActivity(nativeIntent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/$participantId")))
        }
    }
}
