package edu.ap.citytrip.ui.screens.Message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import edu.ap.citytrip.R
import edu.ap.citytrip.data.User
import edu.ap.citytrip.data.getConversationId
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var conversations by remember { mutableStateOf<List<ConversationItem>>(emptyList()) }
    var topContacts by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedConversation by remember { mutableStateOf<ConversationItem?>(null) }

    // Load data once and listen for changes
    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect

        // Top contacts
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableListOf<User>()
                for (doc in snapshot.documents) {
                    if (doc.id == uid) continue
                    val displayName = doc.getString("displayName") ?: ""
                    val email = doc.getString("email") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    result.add(
                        User(
                            uid = doc.id,
                            displayName = displayName,
                            email = email,
                            photoUrl = photoUrl
                        )
                    )
                }
                topContacts = result
            }

        // Conversations listener
        val conversationItemsMap = mutableMapOf<String, ConversationItem>()
        
        db.collection("conversations")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MessagesScreen", "Error listening to conversations", error)
                    return@addSnapshotListener
                }
                
                if (snapshot == null) {
                    conversations = emptyList()
                    return@addSnapshotListener
                }

                // Clear the map
                conversationItemsMap.clear()
                
                // Process all documents
                snapshot.documents.forEach { doc ->
                    val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
                    val otherUid = participants.firstOrNull { it != uid } as? String ?: return@forEach

                    val lastMsg = doc.getString("lastMessage") ?: ""
                    val lastTime = doc.getTimestamp("lastMessageAt")
                    val lastTimeMillis = lastTime?.toDate()?.time ?: System.currentTimeMillis()
                    val chatId = doc.id
                    val lastSenderId = doc.getString("lastSenderId") ?: ""

                    val unreadCountMap = doc.get("unreadCounts") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val unreadCount = (unreadCountMap[uid] as? Number)?.toInt() ?: 0

                    // Create item with temporary title (will be updated with user info)
                    val tempItem = ConversationItem(
                        chatId = chatId,
                        otherUid = otherUid,
                        title = otherUid, // Temporary - will show UID until user info loads
                        photoUrl = "",
                        lastMessage = lastMsg,
                        lastMessageAt = lastTimeMillis,
                        unreadCount = unreadCount,
                        isOnline = false,
                        isMuted = false,
                        isRead = lastSenderId != uid && unreadCount == 0
                    )
                    
                    // Add to map immediately
                    conversationItemsMap[chatId] = tempItem
                    
                    // Fetch user info and update
                    db.collection("users").document(otherUid).get()
                        .addOnSuccessListener { userDoc ->
                            val displayName = userDoc.getString("displayName") ?: ""
                            val email = userDoc.getString("email") ?: ""
                            val title = if (displayName.isNotBlank()) displayName else email.substringBefore("@").ifBlank { "Unknown" }
                            val photoUrl = userDoc.getString("photoUrl") ?: ""
                            
                            // Update the item in the map
                            conversationItemsMap[chatId] = tempItem.copy(
                                title = title,
                                photoUrl = photoUrl
                            )
                            
                            // Update state - create new list from map values
                            conversations = conversationItemsMap.values.toList()
                                .sortedByDescending { it.lastMessageAt }
                        }
                        .addOnFailureListener {
                            // Keep temporary item if lookup fails
                            conversations = conversationItemsMap.values.toList()
                                .sortedByDescending { it.lastMessageAt }
                        }
                }
                
                // Update immediately with all items (including temporary ones)
                // This ensures conversations appear even before user info loads
                conversations = conversationItemsMap.values.toList()
                    .sortedByDescending { it.lastMessageAt }
            }
    }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else {
            val q = searchQuery.lowercase()
            conversations.filter {
                it.title.lowercase().contains(q) || it.lastMessage.lowercase().contains(q)
            }
        }
    }

    // Show conversation screen if one is selected
    selectedConversation?.let { convo ->
        ConversationScreen(
            otherUid = convo.otherUid,
            otherUserName = convo.title,
            otherUserPhotoUrl = convo.photoUrl,
            modifier = modifier,
            onBackClick = { selectedConversation = null }
        )
        return
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_messages),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top contacts row
            if (topContacts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topContacts) { user ->
                        TopContactChip(
                            user = user,
                            auth = auth,
                            db = db,
                            onUserClick = {
                                selectedConversation = ConversationItem(
                                    chatId = edu.ap.citytrip.data.getConversationId(auth.currentUser?.uid ?: "", user.uid),
                                    otherUid = user.uid,
                                    title = if (user.displayName.isNotBlank()) user.displayName else user.email.substringBefore("@").ifBlank { "Unknown" },
                                    photoUrl = user.photoUrl ?: "",
                                    lastMessage = "",
                                    lastMessageAt = System.currentTimeMillis()
                                )
                            }
                        )
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(text = stringResource(R.string.search_placeholder)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Conversations list
            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.start_chat_conversation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredConversations, key = { it.chatId }) { convo ->
                        ConversationRow(
                            item = convo,
                            onConversationClick = { selectedConversation = convo }
                        )
                    }
                }
            }
        }
    }
}

// Generate a color based on user ID or name
private fun getColorForUser(identifier: String): Color {
    val colors = listOf(
        Color(0xFF6200EE), // Purple
        Color(0xFF03DAC6), // Teal
        Color(0xFF018786), // Dark Teal
        Color(0xFFBB86FC), // Light Purple
        Color(0xFF3700B3), // Dark Purple
        Color(0xFF03DAC5), // Cyan
        Color(0xFF018786), // Teal
        Color(0xFFCF6679), // Pink
        Color(0xFF6200EE), // Purple
        Color(0xFF03DAC6)  // Teal
    )
    val hash = identifier.hashCode()
    return colors[Math.abs(hash) % colors.size]
}

@Composable
private fun TopContactChip(
    user: User,
    onUserClick: () -> Unit,
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
    db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val userIdentifier = user.displayName.ifBlank { user.email.ifBlank { user.uid } }
    val avatarColor = remember(userIdentifier) { getColorForUser(userIdentifier) }
    val myUid = auth.currentUser?.uid ?: ""

    Column(
        modifier = Modifier
            .clickable {
                // Create conversation immediately when clicking on a contact
                if (myUid.isNotBlank()) {
                    val convId = edu.ap.citytrip.data.getConversationId(myUid, user.uid)
                    val convRef = db.collection("conversations").document(convId)
                    
                    convRef.get().addOnSuccessListener { snap ->
                        if (!snap.exists()) {
                            val map = hashMapOf(
                                "participants" to listOf(myUid, user.uid),
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "lastMessage" to "",
                                "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "lastSenderId" to "",
                                "unreadCounts" to hashMapOf(
                                    myUid to 0,
                                    user.uid to 0
                                )
                            )
                            convRef.set(map)
                        }
                    }
                }
                onUserClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!user.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = (if (user.displayName.isNotBlank()) user.displayName else user.email.substringBefore("@")).take(1).uppercase().ifBlank { "?" }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Text(
            text = if (user.displayName.isNotBlank()) user.displayName else user.email.substringBefore("@").ifBlank { "Unknown" },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun ConversationRow(
    item: ConversationItem,
    onConversationClick: () -> Unit
) {
    val formattedTime = remember(item.lastMessageAt) {
        formatConversationTimestamp(item.lastMessageAt)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConversationClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(56.dp)) {
            if (item.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val userIdentifier = item.title.ifBlank { item.otherUid }
                val avatarColor = remember(userIdentifier) { getColorForUser(userIdentifier) }
                val initial = item.title.take(1).uppercase().ifBlank { "?" }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Name row with muted icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isMuted) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Timestamp
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Last message row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.lastMessage.ifBlank { "No messages yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Read status checkmark (only if read and no unread messages)
                    if (item.isRead && item.unreadCount == 0) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Read",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Unread count badge
                if (item.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(20.dp)
                            .widthIn(min = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                } else {
                    // Show "0" in gray when no unread messages
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// Format timestamp like "10:35 AM", "Yesterday", "Mar 15", "Last Week"
private fun formatConversationTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val nowCalendar = Calendar.getInstance()
    
    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 24 * 60 * 60 * 1000 && calendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) -> {
            // Today - show time like "10:35 AM"
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        calendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) - 1 -> {
            // Yesterday
            "Yesterday"
        }
        diff < 7 * 24 * 60 * 60 * 1000 -> {
            // This week - show day name
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        }
        calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) -> {
            // This year - show month and day like "Mar 15"
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
        diff < 14 * 24 * 60 * 60 * 1000 -> {
            // Last week
            "Last Week"
        }
        else -> {
            // Older - show month and day
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}


