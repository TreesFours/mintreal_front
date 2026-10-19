package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.SocialContact
import com.example.mistreal_mini.data.api.SocialPlatformResponse
import com.example.mistreal_mini.data.api.UnreadItem
import com.example.mistreal_mini.data.local.dao.SocialContactDao
import com.example.mistreal_mini.data.local.entity.SocialContactEntity
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import com.example.mistreal_mini.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Social contact list/search/unread-count/image-send management — extracted
 * out of ChatViewModel. Deliberately does NOT include switchChat,
 * contactBusiness, or fetchSocialHistory: those read/write _isSocialChat,
 * _activeSocialContact and _messages directly inside the core AI-chat
 * send/error-handling pipeline, so they stay in ChatViewModel rather than
 * crossing a controller boundary mid-pipeline.
 */
class SocialContactsController(
    private val infoRepository: InfoRepository,
    private val socialContactDao: SocialContactDao,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val context: Context,
    private val scope: CoroutineScope,
    private val emitEvent: suspend (String) -> Unit
) {
    private val _socialContacts = mutableStateOf<List<SocialContact>>(emptyList())
    val socialContacts: State<List<SocialContact>> = _socialContacts

    private val _unreadMessages = mutableStateOf<List<UnreadItem>>(emptyList())
    val unreadMessages: State<List<UnreadItem>> = _unreadMessages

    private val _availablePlatforms = mutableStateListOf<SocialPlatformResponse>()
    val availablePlatforms: List<SocialPlatformResponse> = _availablePlatforms

    private val _totalUnreadCount = mutableIntStateOf(0)
    val totalUnreadCount: State<Int> = _totalUnreadCount

    private val _isSendingToContact = mutableStateOf(false)
    val isSendingToContact: State<Boolean> = _isSendingToContact

    private fun deviceId() = com.example.mistreal_mini.util.DeviceIdProvider(context).deviceId

    fun fetchContacts(platform: String) {
        if (platform == "ai") return
        scope.launch {
            val normalizedPlatform = platform.lowercase()
            when (val result = infoRepository.getContacts(deviceId(), normalizedPlatform)) {
                is Resource.Success -> {
                    val rawContacts = result.data ?: emptyList()
                    _socialContacts.value = rawContacts

                    val entities = rawContacts.map { c ->
                        SocialContactEntity(
                            contactId = "${normalizedPlatform}_${c.id}", // 🛡️ Prefixed ID
                            platform = normalizedPlatform,
                            name = c.name,
                            avatarUrl = c.avatar,
                            lastInteractionTime = System.currentTimeMillis(),
                            isEmergency = false,
                            platformUserId = c.id
                        )
                    }
                    // Clean up legacy non-prefixed records for this platform
                    socialContactDao.deleteByPlatform(normalizedPlatform)
                    socialContactDao.upsertAll(entities)
                }
                else -> {}
            }
        }
    }

    fun evictFromHotSlot(contactId: String) {
        scope.launch {
            socialContactDao.updateInteractionTime(contactId, 0L)
        }
    }

    fun searchContacts(platform: String, query: String) {
        scope.launch {
            when (val result = infoRepository.searchContacts(deviceId(), platform, query)) {
                is Resource.Success<List<SocialContact>> -> _socialContacts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun fetchAvailablePlatforms() {
        scope.launch { fetchAvailablePlatformsAwait() }
    }

    // Exposed (not just the fire-and-forget fetchAvailablePlatforms() above)
    // so ChatViewModel.onAuthenticated() can await it directly as part of
    // its sequential boot-loading steps.
    suspend fun fetchAvailablePlatformsAwait() {
        when (val result = infoRepository.getAvailablePlatforms(deviceId())) {
            is Resource.Success -> {
                _availablePlatforms.clear()
                result.data?.let { platforms -> _availablePlatforms.addAll(platforms) }
            }
            else -> {}
        }
    }

    fun fetchUnread() {
        scope.launch {
            when (val result = infoRepository.getUnreadMessages(deviceId())) {
                is Resource.Success -> {
                    _unreadMessages.value = result.data ?: emptyList()
                    _totalUnreadCount.intValue = _unreadMessages.value.size
                }
                else -> {}
            }
        }
    }

    fun refreshSocialContacts() {
        scope.launch {
            val deviceId = deviceId()

            // 🔄 Step 1: Sync with Zernio Cloud First
            syncSocialsUseCase(deviceId)

            // 📥 Step 2: Refresh local metadata, then pull contacts only for platforms
            // the user actually has connected (previously a hardcoded 6-platform list
            // that silently excluded TikTok and anything else not in it).
            val platformsResult = infoRepository.getAvailablePlatforms(deviceId)
            if (platformsResult is Resource.Success) {
                _availablePlatforms.clear()
                platformsResult.data?.let { _availablePlatforms.addAll(it) }
            }
            fetchUnread()
            _availablePlatforms.filter { it.isConnected }.forEach {
                fetchContacts(it.id)
            }
        }
    }

    fun sendImageToContact(imageUri: Uri, platform: String, targetId: String, caption: String, onResult: (Boolean) -> Unit) {
        _isSendingToContact.value = true
        scope.launch {
            val base64 = FileUtil.uriToBase64(context, imageUri)
            if (base64 == null) {
                _isSendingToContact.value = false
                emitEvent("Couldn't read that image.")
                onResult(false)
                return@launch
            }
            val mimeType = FileUtil.getMimeType(context, imageUri) ?: "image/jpeg"
            val result = infoRepository.performSocialAction(
                deviceId = deviceId(),
                type = "Direct Message",
                platform = platform,
                content = caption,
                targetId = targetId,
                mediaBase64 = base64,
                mediaMimeType = mimeType
            )
            _isSendingToContact.value = false
            when (result) {
                is Resource.Success -> {
                    emitEvent("Sent to $targetId on $platform")
                    onResult(true)
                }
                is Resource.Error -> {
                    emitEvent("Send failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }
}
