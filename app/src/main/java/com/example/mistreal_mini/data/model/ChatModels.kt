package com.example.mistreal_mini.data.model

data class ChatRequest(
    val prompt: String,
    val provider: String, // "gemini", "gpt4", "claude"
    val history: List<ChatMessage> = emptyList(),
    val deviceId: String? = null,
    val imageDatas: List<String>? = null,
    val audioData: String? = null
)

data class ChatMessage(
    val id: Long? = null,
    val role: String, // "user", "assistant"
    val content: String,
    val type: String = "text", // "text", "image", "video", "file", "social_draft"
    val attachmentUrl: String? = null,
    val attachmentPath: String? = null,
    val attachmentPaths: List<String>? = null, // Support for multiple local paths
    val provider: String = "gemini",
    val isTrend: Boolean = false,
    val trendTitle: String? = null,
    val socialMetadata: SocialMetadata? = null
)

data class SocialMetadata(
    val type: String,
    val platform: String,
    val targetId: String
)

data class ChatResponse(
    val content: String,
    val provider: String,
    val success: Boolean,
    val error: String? = null
)
