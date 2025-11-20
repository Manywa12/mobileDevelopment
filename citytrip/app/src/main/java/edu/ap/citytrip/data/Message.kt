package edu.ap.citytrip.data

data class Message(
    val id: String,
    val text: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val isRead: Boolean = false,
    val type: MessageType = MessageType.TEXT
)

enum class MessageType {
    TEXT, VOICE, IMAGE
}

data class MessageThread(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val lastMessageType: MessageType = MessageType.TEXT,
    val isRead: Boolean = false
)

data class Contact(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val status: ContactStatus = ContactStatus.OFFLINE
)

enum class ContactStatus {
    ONLINE, AWAY, OFFLINE
}


