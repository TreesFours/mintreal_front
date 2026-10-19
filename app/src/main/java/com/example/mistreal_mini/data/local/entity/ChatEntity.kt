package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mistreal_mini.data.model.ChatMessage

// Covers every ChatDao query's WHERE clause (all filter on a prefix of
// userId/isTrend/trendTitle, see Migration 16->17) — previously a full
// table scan on every message send/receive as history grows.
@Entity(
    tableName = "chats",
    indices = [Index(
        value = ["userId", "isTrend", "trendTitle", "timestamp"],
        name = "index_chats_userId_isTrend_trendTitle_timestamp"
    )]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String, // Patch: Lock to Firebase UID
    val role: String,
    val content: String,
    val type: String,
    val provider: String,
    val isTrend: Boolean = false, // Flag for saved mini-chat sessions
    val trendTitle: String? = null,
    val trueFeelings: String? = null,
    val mood: String? = null,
    val archUnderstandingJson: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    // Null = visible immediately. A future epoch-millis timestamp hides this
    // row from every message query until RevealDelayedReplyWorker clears it
    // back to null — not a live time comparison, so nothing needs to poll.
    val revealAt: Long? = null,
    // Only ever set in real user-to-user (social) chats — see ChatMessage.replyToId.
    val replyToId: Long? = null
) {
    fun toChatMessage() = ChatMessage(
        id = id,
        role = role,
        content = content,
        type = type,
        provider = provider,
        isTrend = isTrend,
        trendTitle = trendTitle,
        trueFeelings = trueFeelings,
        mood = mood,
        archUnderstandingJson = archUnderstandingJson,
        replyToId = replyToId
    )

    companion object {
        fun fromChatMessage(userId: String, msg: ChatMessage, revealAt: Long? = null) = ChatEntity(
            id = msg.id ?: 0,
            userId = userId,
            role = msg.role,
            content = msg.content,
            type = msg.type,
            provider = msg.provider,
            isTrend = msg.isTrend,
            trendTitle = msg.trendTitle,
            trueFeelings = msg.trueFeelings,
            mood = msg.mood,
            archUnderstandingJson = msg.archUnderstandingJson,
            revealAt = revealAt,
            replyToId = msg.replyToId
        )
    }
}
