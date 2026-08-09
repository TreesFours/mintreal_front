package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatResponse
import com.example.mistreal_mini.data.repository.AiRepository
import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.util.FileUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: AiRepository
) {
    suspend operator fun invoke(
        context: Context,
        prompt: String,
        persona: String,
        history: List<ChatMessage>,
        provider: String,
        deviceId: String?,
        imageUris: List<Uri>? = null,
        audioUri: Uri? = null
    ): Resource<ChatResponse> {
        val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        val enhancedPrompt = "[CONTEXT: Current Date/Time is $currentDate]\nPersona: $persona\nUser: $prompt"
        
        val imageParts = imageUris?.mapNotNull { uri ->
            FileUtil.uriToMultipart(context, uri, "images")
        }
        
        val audioPart = audioUri?.let { uri ->
            FileUtil.uriToMultipart(context, uri, "audio")
        }

        return repository.sendMessage(
            prompt = enhancedPrompt,
            provider = provider,
            history = history,
            deviceId = deviceId,
            images = imageParts,
            audio = audioPart
        )
    }
}
