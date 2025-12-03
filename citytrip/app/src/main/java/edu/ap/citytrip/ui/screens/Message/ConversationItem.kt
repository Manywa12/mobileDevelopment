package edu.ap.citytrip.ui.screens.Message

data class ConversationItem(
    val chatId: String,
    val otherUid: String,
    val title: String,
    val photoUrl: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isMuted: Boolean = false,
    val isRead: Boolean = false
)