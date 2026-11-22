package com.example.mistreal_mini.util

import android.content.Intent
import android.net.Uri

/**
 * Messenger (native app with https://m.me fallback) and WhatsApp deep-link
 * construction, previously duplicated independently in ChatScreen.kt's
 * TopAppBar and BankChannelsSection.kt.
 *
 * [buildDeepLink] is the newer, more general sibling of [whatsApp]/
 * [openMessenger]: starting a DM with someone who has no existing Zernio
 * conversation is broken today (see ChatViewModel/zernioAdapter.ts —
 * Zernio needs a conversation id this app has no way to create, and
 * Zernio's own contact search is scoped to people who already messaged
 * you, not an open directory). This sidesteps that entirely for any
 * platform that has a plain username/phone-based web link: instead of
 * going through Zernio's API at all, hand the username/phone straight to
 * the native platform app via a web link — the OS opens it, and the
 * actual search+send happens in that app, which already has full access
 * to its own users. No Zernio call, no conversation id, no API
 * restriction.
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

    /**
     * [opensChatDirectly] is honest about what each platform's link actually
     * does: some (WhatsApp, Telegram, Snapchat, Facebook Messenger) open
     * straight into a chat/add-friend flow; others (Instagram, Twitter/X,
     * TikTok) only open the profile, and the user still has to tap that
     * platform's own message button once there — unverified against a real
     * device for any of these, but consistent with how every one of these
     * platforms' documented web link behavior worked, last confirmed.
     */
    data class DeepLink(val url: String, val opensChatDirectly: Boolean)

    /** Null if this platform has no reliable username/phone-based web link at all (e.g. Discord, LinkedIn — neither supports addressing a specific user by a plain URL). */
    fun buildDeepLink(platform: String, usernameOrPhone: String): DeepLink? {
        val handle = usernameOrPhone.trim().removePrefix("@")
        if (handle.isEmpty()) return null

        return when (platform.lowercase()) {
            "whatsapp" -> {
                val digitsOnly = handle.filter { it.isDigit() }
                if (digitsOnly.isEmpty()) null else DeepLink("https://wa.me/$digitsOnly", opensChatDirectly = true)
            }
            "telegram" -> DeepLink("https://t.me/$handle", opensChatDirectly = true)
            "snapchat" -> DeepLink("https://www.snapchat.com/add/$handle", opensChatDirectly = true)
            "facebook" -> DeepLink("https://m.me/$handle", opensChatDirectly = true)
            "instagram" -> DeepLink("https://www.instagram.com/$handle", opensChatDirectly = false)
            "twitter", "x" -> DeepLink("https://twitter.com/$handle", opensChatDirectly = false)
            "tiktok" -> DeepLink("https://www.tiktok.com/@$handle", opensChatDirectly = false)
            "pinterest" -> DeepLink("https://www.pinterest.com/$handle", opensChatDirectly = false)
            "threads" -> DeepLink("https://www.threads.net/@$handle", opensChatDirectly = false)
            "reddit" -> DeepLink("https://www.reddit.com/user/$handle", opensChatDirectly = false)
            "twitch" -> DeepLink("https://www.twitch.tv/$handle", opensChatDirectly = false)
            // Discord usernames aren't addressable by a plain web URL (no public
            // user-lookup link exists), and LinkedIn has no direct-message link
            // by public profile handle either — no honest deep link to offer.
            else -> null
        }
    }
}
