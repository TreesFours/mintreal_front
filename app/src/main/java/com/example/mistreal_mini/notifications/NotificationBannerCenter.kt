package com.example.mistreal_mini.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a banner shown over [com.example.mistreal_mini.MainActivity]'s NavHost
 * is for. AiReply = a chat/trend reply landed while the user had navigated
 * away from that exact conversation (see ChatViewModel.performChatRequest).
 * InboundDm = a real DM arrived on a connected platform (see
 * MistrealFirebaseMessagingService's "new_dm" FCM branch).
 */
sealed class PendingBanner {
    abstract val postedAtMillis: Long

    data class AiReply(
        val partnerName: String,
        val partnerPlatform: String,
        val trendTitle: String?,
        val replyPreview: String,
        val fullReplyText: String,
        val provider: String?,
        override val postedAtMillis: Long = System.currentTimeMillis()
    ) : PendingBanner()

    data class InboundDm(
        val senderName: String,
        val platform: String,
        val contentPreview: String,
        val senderId: String?,
        override val postedAtMillis: Long = System.currentTimeMillis()
    ) : PendingBanner()
}

/**
 * App-wide holder for "a reply/DM arrived while the user was elsewhere" —
 * fed by ChatViewModel (AiReply) and MistrealFirebaseMessagingService
 * (InboundDm), observed by MainActivity to render one shared banner
 * overlay regardless of which screen is currently on top. Plain
 * zero-arg @Inject constructor, same shape as monitor/ResourceMonitor.kt —
 * no Hilt module binding needed.
 */
@Singleton
class NotificationBannerCenter @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _pending = MutableStateFlow<PendingBanner?>(null)
    val pending: StateFlow<PendingBanner?> = _pending.asStateFlow()

    // One-shot "focus the chat input now" signal consumed by ChatScreen — a
    // SharedFlow, not another StateFlow, so a collector that attaches late
    // doesn't immediately replay a stale focus request.
    private val _focusInputRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusInputRequests: SharedFlow<Unit> = _focusInputRequests.asSharedFlow()

    private var autoDismissJob: Job? = null

    private fun post(banner: PendingBanner) {
        _pending.value = banner
        autoDismissJob?.cancel()
        autoDismissJob = scope.launch {
            delay(10_000)
            _pending.value = null
        }
    }

    fun postAiReply(
        partnerName: String,
        partnerPlatform: String,
        trendTitle: String?,
        replyPreview: String,
        fullReplyText: String,
        provider: String?
    ) = post(PendingBanner.AiReply(partnerName, partnerPlatform, trendTitle, replyPreview, fullReplyText, provider))

    fun postInboundDm(senderName: String, platform: String, contentPreview: String, senderId: String?) =
        post(PendingBanner.InboundDm(senderName, platform, contentPreview, senderId))

    fun dismiss() {
        autoDismissJob?.cancel()
        _pending.value = null
    }

    fun requestInputFocus() {
        scope.launch { _focusInputRequests.emit(Unit) }
    }
}
