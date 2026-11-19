package com.example.mistreal_mini.ui.navigation

import java.net.URLEncoder

/**
 * Route templates (for NavHost `composable(...)` registration) and builders
 * (for `navController.navigate(...)` call sites) for every destination.
 * Centralizes the encode/decode and query-string construction that used to
 * be hand-built at each call site in MainActivity.kt.
 */
object Routes {
    const val SPLASH = "splash"
    const val BOOT = "boot"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val CHAT_TEMPLATE = "chat?initialProvider={initialProvider}"
    const val SUBSCRIPTION = "subscription"
    const val ARCHIVE = "archive"
    const val CONNECTIONS = "connections"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val NEEDS = "needs"
    const val PENDING_REVIEWS = "pending_reviews"
    const val OWNER_PROFILE_TEMPLATE =
        "owner_profile/{revieweeDeviceId}?ownerName={ownerName}&ownerPhotoUrl={ownerPhotoUrl}&businessId={businessId}"
    const val MARKET = "market"
    const val WATCH_HOST_TEMPLATE = "watch_host?contextType={contextType}&contextId={contextId}"
    const val WATCH_HOST_BASE = "watch_host"
    const val WATCH_VIEWER_TEMPLATE = "watch_viewer?sessionId={sessionId}"
    const val WATCH_VIEWER_BASE = "watch_viewer"
    const val GUARDIAN_ALERTS = "guardian_alerts"
    const val BUSINESS_HUB = "business_hub"
    const val BUSINESS_MEETUPS = "business_meetups"
    const val COMFORT_MEET_TEMPLATE = "comfort_meet/{meetupId}"
    const val BUSINESS_DISCOVERY = "business_discovery"
    const val BUSINESS_DETAIL_TEMPLATE = "business_detail/{businessId}"
    const val BUSINESS_SELLER = "business_seller"
    const val BUSINESS_ADVERTISE = "business_advertise"
    const val GAMES = "games"
    const val GAME_PLAY_TEMPLATE = "game_play?title={title}&playUrl={playUrl}"
    const val CALL_TEMPLATE = "call?calleeDeviceId={calleeDeviceId}&callId={callId}&callerDeviceId={callerDeviceId}"
    const val CALL_BASE = "call"
    const val LIVE_AI = "live_ai"
    const val EMAIL = "email"
    const val EMAIL_COMPOSE = "email_compose"
    const val EMAIL_MESSAGE_TEMPLATE = "email_message/{uid}"

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

    fun chat(initialProvider: String? = null) =
        if (initialProvider != null) "chat?initialProvider=${encode(initialProvider)}" else "chat"

    fun ownerProfile(
        deviceId: String,
        ownerName: String? = null,
        ownerPhotoUrl: String? = null,
        businessId: String? = null
    ): String {
        var route = "owner_profile/${encode(deviceId)}"
        val params = mutableListOf<String>()
        businessId?.let { params += "businessId=${encode(it)}" }
        ownerName?.let { params += "ownerName=${encode(it)}" }
        ownerPhotoUrl?.let { params += "ownerPhotoUrl=${encode(it)}" }
        if (params.isNotEmpty()) route += "?" + params.joinToString("&")
        return route
    }

    fun watchHost(contextType: String = "standalone", contextId: String? = null): String {
        var route = "watch_host?contextType=${encode(contextType)}"
        contextId?.let { route += "&contextId=${encode(it)}" }
        return route
    }

    fun watchViewer(sessionId: String) = "watch_viewer?sessionId=${encode(sessionId)}"

    fun comfortMeet(meetupId: Int) = "comfort_meet/$meetupId"

    fun outgoingCall(calleeDeviceId: String) = "call?calleeDeviceId=${encode(calleeDeviceId)}"

    fun incomingCall(callId: String, callerDeviceId: String) =
        "call?callId=${encode(callId)}&callerDeviceId=${encode(callerDeviceId)}"

    fun businessDetail(businessId: String) = "business_detail/$businessId"

    fun gamePlay(title: String, playUrl: String) = "game_play?title=${encode(title)}&playUrl=${encode(playUrl)}"

    fun emailMessage(uid: Int) = "email_message/$uid"
}
