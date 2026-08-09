package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mistreal_mini.data.model.ChatMessage

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String, // Patch: Lock to Firebase UID
    val role: String,
    val content: String,
    val type: String,
    val provider: String,
    val isTrend: Boolean = false, // Flag for saved mini-chat sessions
    val trendTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toChatMessage() = ChatMessage(
        id = id,
        role = role,
        content = content,
        type = type,
        provider = provider,
        isTrend = isTrend,
        trendTitle = trendTitle
    )

    companion object {
        fun fromChatMessage(userId: String, msg: ChatMessage) = ChatEntity(
            id = msg.id ?: 0,
            userId = userId,
            role = msg.role,
            content = msg.content,
            type = msg.type,
            provider = msg.provider,
            isTrend = msg.isTrend,
            trendTitle = msg.trendTitle
        )
    }
}
