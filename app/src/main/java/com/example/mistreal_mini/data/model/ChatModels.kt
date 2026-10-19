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
    val trueFeelings: String? = null, // 🧠 "True Feelings" Protocol
    val mood: String? = null, // one of: happy, excited, neutral, curious, confused, frustrated, sad, angry
    val socialMetadata: SocialMetadata? = null,
    // 🏛 Architectural Render — the AI's restated understanding of the
    // request (rooms/dimensions/scale/style/confirmed), parsed from the
    // [ARCH_UNDERSTANDING: {...}] tag. Non-null only on assistant replies
    // from that flow; drives ChatBubble's understanding card.
    val archUnderstandingJson: String? = null,
    // Only ever set in real user-to-user (social) chats — the id of the
    // specific earlier message this one is a reply to. Never used in the AI
    // main chat/minichat, where there's only ever one thread to reply into.
    val replyToId: Long? = null
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
    val error: String? = null,
    val generatedImageBase64: String? = null,
    val generatedImageMimeType: String? = null,
    val generatedImageUrl: String? = null,
    val generatedVideoUrl: String? = null,
    // Only ever set when Gemini's search-grounding tool backed the answer.
    val groundingCitations: List<GroundingCitation>? = null
)

data class GroundingCitation(
    val uri: String,
    val title: String? = null
)
