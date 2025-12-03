package edu.ap.citytrip.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

// ===========================
//   DATA CLASSES
// ===========================

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null
)

data class Message(
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)

data class Conversation(
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)



// ===========================
//   HELPER FUNCTIES
// ===========================

fun getConversationId(uid1: String, uid2: String): String {
    return listOf(uid1, uid2).sorted().joinToString("_")
}
