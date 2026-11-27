package com.example.terralock.chat

import kotlinx.serialization.Serializable

enum class ChatType {
    DIRECT, GROUP
}

@Serializable
sealed interface ChatAttachment {
    @Serializable
    data class RegionPackAttachment(
        val regionId: String,
        val name: String,
        val pointCount: Int
    ) : ChatAttachment

    @Serializable
    data class CustomPointAttachment(
        val pointId: String,
        val title: String,
        val category: String,
        val description: String
    ) : ChatAttachment
}

@Serializable
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
    val editedAtMs: Long? = null,
    val isDeleted: Boolean = false,
    val replyToMessageId: String? = null,
    val replyPreviewText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val attachment: ChatAttachment? = null
)

@Serializable
data class ChatThread(
    val id: String = "",
    val type: ChatType = ChatType.DIRECT,
    val title: String = "",
    val memberIds: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageAtMs: Long = System.currentTimeMillis(),
    val pinnedMessageId: String? = null,
    val pinnedMessagePreview: String? = null,
    val unreadCount: Int = 0,
    val isPinnedChat: Boolean = false,
    val typingUserIds: List<String> = emptyList()
)