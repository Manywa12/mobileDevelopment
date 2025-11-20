package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Contact
import edu.ap.citytrip.data.ContactStatus
import edu.ap.citytrip.data.MessageThread
import edu.ap.citytrip.data.MessageType
import edu.ap.citytrip.ui.theme.CitytripTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onMessageThreadClick: (MessageThread) -> Unit = {}
) {
    // Sample data - in real app, this would come from Firestore
    val contacts = remember {
        listOf(
            Contact("1", "Alex", null, ContactStatus.ONLINE),
            Contact("2", "Maria", null, ContactStatus.AWAY),
            Contact("3", "Sam", null, ContactStatus.OFFLINE),
            Contact("4", "Chloe", null, ContactStatus.ONLINE),
            Contact("5", "John", null, ContactStatus.ONLINE)
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    val messageThreads = remember {
        listOf(
            MessageThread(
                id = "1",
                name = "Sarah Johnson",
                imageUrl = null,
                lastMessage = "Sounds great! See you then.",
                timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
                unreadCount = 2,
                isRead = false
            ),
            MessageThread(
                id = "2",
                name = "Mark Lee",
                imageUrl = null,
                lastMessage = "Did you review the CityTrip pla",
                timestamp = System.currentTimeMillis() - 7200000, // 2 hours ago
                unreadCount = 0,
                lastMessageType = MessageType.VOICE,
                isRead = true
            ),
            MessageThread(
                id = "3",
                name = "CityTrip Group",
                imageUrl = null,
                lastMessage = "John: We need to finalize the Lisbon itiner",
                timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
                unreadCount = 5,
                isGroup = true,
                isRead = false
            ),
            MessageThread(
                id = "4",
                name = "Emily Chen",
                imageUrl = null,
                lastMessage = "I am excited for the new features",
                timestamp = System.currentTimeMillis() - 172800000, // 2 days ago
                unreadCount = 0,
                isRead = true
            ),
            MessageThread(
                id = "5",
                name = "Project X",
                imageUrl = null,
                lastMessage = "Meeting is rescheduled to 3 PM",
                timestamp = System.currentTimeMillis() - 259200000, // 3 days ago
                unreadCount = 0,
                lastMessageType = MessageType.VOICE,
                isRead = true
            ),
            MessageThread(
                id = "6",
                name = "Travel Buddy",
                imageUrl = null,
                lastMessage = "Don't forget your passport!",
                timestamp = System.currentTimeMillis() - 604800000, // 1 week ago
                unreadCount = 1,
                isRead = false
            ),
            MessageThread(
                id = "7",
                name = "Daniel Brown",
                imageUrl = null,
                lastMessage = "See the attached itinerary.",
                timestamp = System.currentTimeMillis() - 1209600000, // 2 weeks ago
                unreadCount = 0,
                isRead = true
            )
        )
    }

    val filteredThreads = remember(searchQuery, messageThreads) {
        if (searchQuery.isBlank()) {
            messageThreads
        } else {
            messageThreads.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_messages),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedDestination = BottomNavDestination.MESSAGES,
                onHomeClick = onHomeClick,
                onMapClick = onMapClick,
                onMessagesClick = onMessagesClick,
                onProfileClick = onProfileClick,
                onAddClick = onAddClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Contacts row
            ContactsRow(
                contacts = contacts,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Message threads list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredThreads, key = { it.id }) { thread ->
                    MessageThreadItem(
                        thread = thread,
                        onClick = { onMessageThreadClick(thread) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsRow(
    contacts: List<Contact>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(contacts, key = { it.id }) { contact ->
            ContactItem(contact = contact)
        }
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            // Profile picture
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when (contact.status) {
                            ContactStatus.ONLINE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ContactStatus.AWAY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            ContactStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!contact.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = contact.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Status dot
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        when (contact.status) {
                            ContactStatus.ONLINE -> Color(0xFF4CAF50) // Green
                            ContactStatus.AWAY -> Color(0xFFFF9800) // Orange
                            ContactStatus.OFFLINE -> Color(0xFF9E9E9E) // Grey
                        }
                    )
            )
        }
        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MessageThreadItem(
    thread: MessageThread,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (thread.unreadCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (thread.isGroup) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!thread.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thread.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = thread.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Message content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thread.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(thread.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Message type icon
                    if (thread.lastMessageType == MessageType.VOICE) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (thread.isRead && thread.lastMessageType == MessageType.TEXT) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = thread.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (thread.unreadCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Unread badge
            if (thread.unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = thread.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 86400000 -> {
            // Today - show time
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
        diff < 172800000 -> {
            // Yesterday
            "Yesterday"
        }
        diff < 604800000 -> {
            // This week - show date
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
        diff < 1209600000 -> {
            // Last week
            "Last Week"
        }
        else -> {
            // Older - show full date
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessagesScreenPreview() {
    CitytripTheme {
        MessagesScreen()
    }
}

