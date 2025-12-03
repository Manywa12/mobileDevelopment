package edu.ap.citytrip.ui.screens.Message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.ap.citytrip.R
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import edu.ap.citytrip.data.Message
import edu.ap.citytrip.data.getConversationId
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    otherUid: String,
    otherUserName: String = "Unknown",
    otherUserPhotoUrl: String = "",
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    
    val myUid = auth.currentUser?.uid ?: return
    val convId = remember(otherUid, myUid) { getConversationId(myUid, otherUid) }
    
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var otherUserInfo by remember { mutableStateOf(Pair(otherUserName, otherUserPhotoUrl)) }
    
    val listState = rememberLazyListState()
    
    // Load other user info
    LaunchedEffect(otherUid) {
        db.collection("users").document(otherUid).get()
            .addOnSuccessListener { doc ->
                val displayName = doc.getString("displayName") ?: ""
                val email = doc.getString("email") ?: ""
                val photoUrl = doc.getString("photoUrl") ?: ""
                val name = if (displayName.isNotBlank()) displayName else email.substringBefore("@").ifBlank { "Unknown" }
                otherUserInfo = Pair(name, photoUrl)
            }
    }
    
    // Create conversation if needed
    LaunchedEffect(convId) {
        val convRef = db.collection("conversations").document(convId)
        convRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                val map = hashMapOf(
                    "participants" to listOf(myUid, otherUid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to "",
                    "unreadCounts" to hashMapOf(
                        myUid to 0,
                        otherUid to 0
                    )
                )
                convRef.set(map)
            } else {
                // Reset unread count when opening chat
                val unreadCounts = (snap.get("unreadCounts") as? Map<*, *>?)?.toMutableMap() ?: mutableMapOf()
                unreadCounts[myUid] = 0
                convRef.update("unreadCounts", unreadCounts)
            }
        }
    }
    
    // Listen to messages
    DisposableEffect(convId) {
        val ref = db.collection("conversations")
            .document(convId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
        
        val listener = ref.addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("ConversationScreen", "Error listening to messages", error)
                return@addSnapshotListener
            }
            
            if (snap != null && !snap.isEmpty) {
                val newMessages = mutableListOf<Message>()
                for (doc in snap.documents) {
                    val msg = doc.toObject(Message::class.java)
                    if (msg != null) {
                        newMessages.add(msg)
                    }
                }
                messages = newMessages
            } else if (snap != null && snap.isEmpty) {
                messages = emptyList()
            }
        }
        
        onDispose {
            listener.remove()
        }
    }
    
    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val textToSend = text.trim()
        val convRef = db.collection("conversations").document(convId)
        
        convRef.get().addOnSuccessListener { convDoc ->
            if (!convDoc.exists()) {
                // Create conversation if it doesn't exist
                val map = hashMapOf(
                    "participants" to listOf(myUid, otherUid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to textToSend,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to myUid,
                    "unreadCounts" to hashMapOf(
                        myUid to 0,
                        otherUid to 1
                    )
                )
                convRef.set(map)
            }
            
            val participants = convDoc.get("participants") as? List<*> ?: listOf(myUid, otherUid)
            val otherUidInConv = participants.firstOrNull { it != myUid } as? String ?: otherUid
            
            val msg = hashMapOf(
                "senderId" to myUid,
                "text" to textToSend,
                "createdAt" to FieldValue.serverTimestamp(),
                "read" to false,
                "participants" to participants // Store participants in message for security rules
            )
            
            db.collection("conversations")
                .document(convId)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener { docRef ->
                    // Update conversation metadata
                    val unreadCounts = (convDoc.get("unreadCounts") as? Map<*, *>?)?.toMutableMap() ?: mutableMapOf()
                    val currentCount = (unreadCounts[otherUidInConv] as? Number)?.toInt() ?: 0
                    unreadCounts[otherUidInConv] = currentCount + 1
                    
                    convRef.update(
                        mapOf(
                            "lastMessage" to textToSend,
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "lastSenderId" to myUid,
                            "unreadCounts" to unreadCounts
                        )
                    )
                }
                .addOnFailureListener { error ->
                    android.util.Log.e("ConversationScreen", "Error sending message", error)
                }
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            ConversationTopBar(
                userName = otherUserInfo.first,
                userPhotoUrl = otherUserInfo.second,
                onBackClick = onBackClick,
                onMenuClick = { /* TODO: Show menu */ }
            )
        },
        bottomBar = {
            ConversationInputBar(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSendClick = {
                    sendMessage(messageText)
                    messageText = ""
                },
                onGalleryClick = { /* TODO: Open gallery */ }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_messages_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { "${it.senderId}_${it.text}_${it.createdAt?.seconds ?: 0}" }) { message ->
                        MessageBubble(
                            message = message,
                            isMyMessage = message.senderId == myUid,
                            myPhotoUrl = "", // TODO: Load current user photo
                            otherPhotoUrl = otherUserInfo.second,
                            otherUserInfo = otherUserInfo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationTopBar(
    userName: String,
    userPhotoUrl: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Profile picture
            Box {
                if (userPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = userName.take(1).uppercase().ifBlank { "?" }
                    val avatarColor = remember(userName) { getColorForUser(userName.ifBlank { "default" }) }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // User name
            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            
            // Menu button
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ConversationInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery icon
            IconButton(onClick = onGalleryClick) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Gallery",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Text input
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                placeholder = { Text(stringResource(R.string.chat_placeholder_message)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                maxLines = 5,
                trailingIcon = {
                    if (messageText.isNotBlank()) {
                        IconButton(
                            onClick = onSendClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = stringResource(R.string.chat_action_send),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
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
private fun MessageBubble(
    message: Message,
    isMyMessage: Boolean,
    myPhotoUrl: String,
    otherPhotoUrl: String,
    otherUserInfo: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    val photoUrl = if (isMyMessage) myPhotoUrl else otherPhotoUrl
    val bubbleColor = if (isMyMessage) {
        Color(0xFF9C27B0) // Purple for user messages
    } else {
        Color(0xFFE0E0E0) // Light gray for other messages
    }
    
    val textColor = if (isMyMessage) {
        Color.White
    } else {
        Color.Black
    }
    
    val timestamp = message.createdAt?.toDate()?.let {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
    } ?: ""
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isMyMessage) {
            // Other person's avatar
            MessageAvatar(
                photoUrl = otherPhotoUrl, 
                userName = otherUserInfo.first,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
        ) {
            // Message bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = 15.sp
                    )
                )
            }
            
            // Timestamp
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        
        if (isMyMessage) {
            // User's avatar - get current user name
            val currentUser = remember { FirebaseAuth.getInstance().currentUser }
            val currentUserName = currentUser?.displayName ?: 
                currentUser?.email?.substringBefore("@") ?: ""
            MessageAvatar(
                photoUrl = myPhotoUrl, 
                userName = currentUserName,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun MessageAvatar(
    photoUrl: String,
    userName: String = "",
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = userName.take(1).uppercase().ifBlank { "?" }
            val avatarColor = remember(userName) { 
                getColorForUser(userName.ifBlank { "default" })
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

