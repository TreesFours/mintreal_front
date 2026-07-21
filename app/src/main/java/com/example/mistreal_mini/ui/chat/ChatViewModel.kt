package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatRequest
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AiRepository,
    private val infoRepository: InfoRepository,
    private val preferenceManager: PreferenceManager,
    private val voiceManager: VoiceManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    private val _selectedProvider = mutableStateOf("google/gemini-pro-1.5")
    val selectedProvider: State<String> = _selectedProvider

    private val _availableProviders = mutableStateListOf("google/gemini-pro-1.5", "google/gemini-flash-1.5", "openai/gpt-4-turbo", "anthropic/claude-3.5-sonnet")
    val availableProviders: List<String> = _availableProviders

    private val _currentPersona = mutableStateOf("Shadow")
    val currentPersona: State<String> = _currentPersona

    private val _currentChatPartner = mutableStateOf("AI")
    val currentChatPartner: State<String> = _currentChatPartner

    init {
        viewModelScope.launch {
            _currentPersona.value = preferenceManager.aiPersona.first()
        }
    }

    fun switchChat(partner: String) {
        _currentChatPartner.value = partner
        // In a real app, clear and load historical messages for this partner
    }

    fun setProvider(provider: String) {
        _selectedProvider.value = provider
    }

    fun sendMessage(text: String, attachmentUri: Uri? = null, attachmentType: String = "text") {
        if (text.isBlank() && attachmentUri == null) return
        
        val userMessage = ChatMessage(
            role = "user", 
            content = text, 
            type = attachmentType,
            attachmentPath = attachmentUri?.toString(),
            provider = _selectedProvider.value
        )
        _messages.add(userMessage)
        
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        
        // If there's an attachment, we might want to convert it to Base64 or upload it
        val attachmentBase64 = attachmentUri?.let { uri ->
            // Simple placeholder for base64 conversion
            "BASE64_STUB" 
        }
        
        performChatRequest(text, attachmentBase64, deviceId)
    }

    fun sendAttachment(uri: Uri, type: String = "image") {
        if (type == "audio") {
            sendMessage("Voice message", uri, type)
        } else {
            sendMessage("", uri, type)
        }
    }

    fun syncSocials() {
        _isLoading.value = true
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.syncSocials(deviceId)) {
                is Resource.Success -> {
                    if (result.data?.summary == "CONNECTION_REQUIRED") {
                        _messages.add(ChatMessage(role = "assistant", content = "I don't have access to your social accounts yet. Please go to Settings and connect your profiles so I can sync your data.", provider = "system"))
                    } else {
                        _messages.add(ChatMessage(role = "assistant", content = "Sync Complete: ${result.data?.summary}", provider = "system"))
                    }
                }
                is Resource.Error -> {
                    if (result.message?.contains("CONNECTION_REQUIRED", ignoreCase = true) == true) {
                        _messages.add(ChatMessage(role = "assistant", content = "It looks like your social accounts aren't connected. Head over to Settings to link them.", provider = "system"))
                    } else {
                        _messages.add(ChatMessage(role = "assistant", content = "Error syncing: ${result.message}", provider = "error"))
                        _errorEvents.emit("Sync failed: ${result.message}")
                    }
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private fun performChatRequest(prompt: String, attachmentBase64: String?, deviceId: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            // Include Persona in the prompt instructions
            val enhancedPrompt = "Persona: ${_currentPersona.value}\nUser: $prompt"
            val request = ChatRequest(enhancedPrompt, _selectedProvider.value, _messages.toList(), deviceId)
            when (val result = repository.sendMessage(request)) {
                is Resource.Success -> {
                    result.data?.let { 
                        _messages.add(ChatMessage(role = "assistant", content = it.content, provider = it.provider)) 
                        voiceManager.speak(it.content)
                    }
                }
                is Resource.Error -> {
                    _messages.add(ChatMessage(role = "assistant", content = "Mission Delayed: ${result.message}", provider = "system"))
                    _errorEvents.emit("Chat error: ${result.message}")
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun approveSocialAction(draft: ChatMessage) {
        viewModelScope.launch {
            _isLoading.value = true
            // Mocking action details from message content for now
            val result = infoRepository.performSocialAction(
                deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID),
                type = "Post",
                platform = "Twitter",
                content = draft.content,
                targetId = "self"
            )
            if (result is Resource.Success) {
                _messages.add(ChatMessage(role = "assistant", content = "Action Approved & Executed.", provider = "system"))
            } else {
                _errorEvents.emit("Action failed: ${(result as Resource.Error).message}")
            }
            _isLoading.value = false
        }
    }

    fun discardSocialAction(draft: ChatMessage) {
        _messages.add(ChatMessage(role = "assistant", content = "Action Discarded.", provider = "system"))
    }
}
