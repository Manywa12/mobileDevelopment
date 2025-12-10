package edu.ap.citytrip.ui.screens

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Location
import edu.ap.citytrip.data.Review
import edu.ap.citytrip.ui.screens.Message.ConversationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailsScreen(
    location: Location,
    onBackClick: () -> Unit,
    onViewOnMapClick: () -> Unit,
    cityId: String
) {
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var showAddRatingPopup by remember { mutableStateOf(false) }
    val firestore = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    var distance by remember { mutableStateOf<Float?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var isLoadingAddress by remember { mutableStateOf(false) }
    
    // State for messaging
    var selectedReviewUserId by remember { mutableStateOf<String?>(null) }
    var selectedReviewUserName by remember { mutableStateOf<String?>(null) }
    var selectedReviewUserPhotoUrl by remember { mutableStateOf<String?>(null) }
    var cityOwnerId by remember { mutableStateOf<String?>(null) }
    var reloadReviewsTrigger by remember { mutableStateOf(0) }
    
    // State for location creator info
    var locationCreatorName by remember { mutableStateOf<String?>(null) }
    var locationCreatorPhotoUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingCreatorInfo by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Load distance and reviews
    LaunchedEffect(location, cityId, reloadReviewsTrigger) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { userLocation: android.location.Location? ->
                if (userLocation != null) {
                    val locationPoint = android.location.Location("").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                    distance = userLocation.distanceTo(locationPoint) / 1000 // in km
                }
            }
        } catch (e: SecurityException) {
            // Handle missing location permissions
        }

        // Load reviews - try multiple paths where the location might be stored
        val allReviews = mutableListOf<Review>()
        var completed = 0
        val pathsToTry = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        
        // Try current user's path
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            pathsToTry.add(
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("cities")
                    .document(cityId)
                    .collection("locations")
                    .document(location.id)
            )
        }
        
        // Try location creator's path
        if (location.createdBy.isNotBlank() && location.createdBy != currentUserId) {
            pathsToTry.add(
                firestore.collection("users")
                    .document(location.createdBy)
                    .collection("cities")
                    .document(cityId)
                    .collection("locations")
                    .document(location.id)
            )
        }
        
        // Function to load reviews from paths
        fun loadReviewsFromPaths(paths: List<com.google.firebase.firestore.DocumentReference>) {
            val uniquePaths = paths.distinctBy { it.path }
            val total = uniquePaths.size
            
            if (total == 0) {
                reviews = emptyList()
                return
            }
            
            completed = 0
            allReviews.clear()
            
            uniquePaths.forEach { locationRef ->
                locationRef.collection("reviews")
                    .get()
                    .addOnSuccessListener { reviewsSnapshot ->
                        android.util.Log.d("LocationDetails", "Found ${reviewsSnapshot.documents.size} reviews at path: ${locationRef.path}")
                        val loadedReviews = reviewsSnapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                Review(
                                    id = doc.id,
                                    rating = (data["rating"] as? Number)?.toFloat() ?: 0f,
                                    comment = data["comment"] as? String ?: "",
                                    userId = data["userId"] as? String ?: "",
                                    userName = data["userName"] as? String ?: "User",
                                    createdAt = doc.getDate("createdAt")
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("LocationDetails", "Error parsing review: ${e.message}")
                                null
                            }
                        }
                        allReviews.addAll(loadedReviews)
                        completed++
                        
                        if (completed == total) {
                            // Remove duplicates based on review ID
                            val uniqueReviews = allReviews.distinctBy { it.id }
                            android.util.Log.d("LocationDetails", "Total unique reviews: ${uniqueReviews.size}")
                            reviews = uniqueReviews.sortedByDescending { it.createdAt?.time ?: 0L }
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("LocationDetails", "Error loading reviews from ${locationRef.path}: ${e.message}")
                        completed++
                        if (completed == total) {
                            val uniqueReviews = allReviews.distinctBy { it.id }
                            reviews = uniqueReviews.sortedByDescending { it.createdAt?.time ?: 0L }
                        }
                    }
            }
        }
        
        // Try direct paths first
        if (pathsToTry.isNotEmpty()) {
            loadReviewsFromPaths(pathsToTry)
        }
        
        // Also try to find via collectionGroup (without whereEqualTo to avoid index requirement)
        firestore.collectionGroup("locations")
            .get()
            .addOnSuccessListener { locationsSnapshot ->
                val foundPaths = locationsSnapshot.documents
                    .filter { it.id == location.id }
                    .map { it.reference }
                
                if (foundPaths.isNotEmpty()) {
                    // Add any new paths we found
                    pathsToTry.addAll(foundPaths)
                    // Reload with all paths
                    loadReviewsFromPaths(pathsToTry)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("LocationDetails", "collectionGroup query failed: ${e.message}")
                // If we have direct paths, use those
                if (pathsToTry.isNotEmpty()) {
                    loadReviewsFromPaths(pathsToTry)
                } else {
                    reviews = emptyList()
                }
            }
    }

    // Load location creator info
    LaunchedEffect(location.createdBy) {
        if (location.createdBy.isNotBlank()) {
            isLoadingCreatorInfo = true
            firestore.collection("users").document(location.createdBy).get()
                .addOnSuccessListener { doc ->
                    val displayName = doc.getString("displayName") ?: ""
                    val email = doc.getString("email") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val name = if (displayName.isNotBlank()) displayName 
                        else email.substringBefore("@").ifBlank { "Unknown User" }
                    locationCreatorName = name
                    locationCreatorPhotoUrl = photoUrl
                    isLoadingCreatorInfo = false
                }
                .addOnFailureListener {
                    locationCreatorName = "Unknown User"
                    locationCreatorPhotoUrl = ""
                    isLoadingCreatorInfo = false
                }
        } else {
            locationCreatorName = null
            locationCreatorPhotoUrl = null
            isLoadingCreatorInfo = false
        }
    }

    // Reverse geocode address from coordinates
    LaunchedEffect(location.latitude, location.longitude) {
        if (location.latitude != 0.0 && location.longitude != 0.0) {
            isLoadingAddress = true
            address = null
            try {
                val geocoder = Geocoder(context, java.util.Locale.getDefault())
                val results = withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                }
                if (results != null && results.isNotEmpty()) {
                    val addr = results[0]
                    val parts = mutableListOf<String>()
                    addr.getAddressLine(0)?.let { parts.add(it) }
                    if (parts.isEmpty()) {
                        addr.thoroughfare?.let { parts.add(it) }
                        addr.subThoroughfare?.let { parts.add(it) }
                        addr.locality?.let { parts.add(it) }
                        addr.postalCode?.let { parts.add(it) }
                        addr.countryName?.let { parts.add(it) }
                    }
                    address = parts.joinToString(", ").ifBlank { "Adres niet beschikbaar" }
                } else {
                    address = "Adres niet beschikbaar"
                }
            } catch (_: Exception) {
                address = "Adres niet beschikbaar"
            } finally {
                isLoadingAddress = false
            }
        } else {
            address = null
            isLoadingAddress = false
        }
    }

    // Show conversation screen if a user is selected for messaging
    selectedReviewUserId?.let { userId ->
        ConversationScreen(
            otherUid = userId,
            otherUserName = selectedReviewUserName ?: "User",
            otherUserPhotoUrl = selectedReviewUserPhotoUrl ?: "",
            modifier = Modifier,
            onBackClick = {
                selectedReviewUserId = null
                selectedReviewUserName = null
                selectedReviewUserPhotoUrl = null
            }
        )
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(location.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(location.imageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = location.category,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val averageRating = if (reviews.isNotEmpty()) {
                            reviews.map { it.rating }.average()
                        } else 0.0
                        Text(
                            text = "%.1f".format(averageRating),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            Button(onClick = { showAddRatingPopup = true }) {
                                Text(stringResource(R.string.add_rating_button))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = onViewOnMapClick) {
                                Text(stringResource(R.string.action_view_on_map))
                            }
                        }
                    }
                    // Distance from current location
                    distance?.let {
                        Text(
                            text = stringResource(R.string.distance_from_current_location, it),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Address under distance
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isLoadingAddress -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Adres ophalen...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        address != null -> {
                            Text(
                                text = address ?: "",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Location creator info
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.published_by),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when {
                            isLoadingCreatorInfo -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            locationCreatorName != null -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Avatar
                                    if (locationCreatorPhotoUrl?.isNotBlank() == true) {
                                        AsyncImage(
                                            model = locationCreatorPhotoUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        val initial = locationCreatorName?.take(1)?.uppercase() ?: "?"
                                        val avatarColor = remember(locationCreatorName) {
                                            getColorForUser(locationCreatorName ?: "default")
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(avatarColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = locationCreatorName ?: "Unknown User",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.user_reviews_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            items(reviews) { review ->
                ReviewItem(
                    review = review,
                    onMessageClick = {
                        // Get user info from Firestore
                        firestore.collection("users").document(review.userId).get()
                            .addOnSuccessListener { doc ->
                                val displayName = doc.getString("displayName") ?: ""
                                val email = doc.getString("email") ?: ""
                                val photoUrl = doc.getString("photoUrl") ?: ""
                                val name = if (displayName.isNotBlank()) displayName 
                                    else email.substringBefore("@").ifBlank { review.userName }
                                
                                selectedReviewUserId = review.userId
                                selectedReviewUserName = name
                                selectedReviewUserPhotoUrl = photoUrl
                            }
                            .addOnFailureListener {
                                // Fallback to review data if Firestore fetch fails
                                selectedReviewUserId = review.userId
                                selectedReviewUserName = review.userName.ifBlank { "User" }
                                selectedReviewUserPhotoUrl = ""
                            }
                    }
                )
            }
        }
    }

    if (showAddRatingPopup) {
        AddRatingPopup(
            onDismiss = { showAddRatingPopup = false },
            onSubmit = { rating, comment ->
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    android.widget.Toast.makeText(context, "Je bent niet ingelogd", android.widget.Toast.LENGTH_SHORT).show()
                    showAddRatingPopup = false
                    return@AddRatingPopup
                }
                
                val raterId = currentUser.uid
                val raterName = currentUser.displayName
                    ?: (currentUser.email?.substringBefore('@') ?: "User")
                
                val reviewData = hashMapOf(
                    "rating" to rating,
                    "comment" to comment,
                    "userId" to raterId,
                    "userName" to raterName,
                    "locationId" to location.id,
                    "cityId" to cityId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                
                // Try to save review under current user's location first
                val currentUserLocationRef = firestore.collection("users")
                    .document(raterId)
                    .collection("cities")
                    .document(cityId)
                    .collection("locations")
                    .document(location.id)
                
                // Check if location exists under current user, if not try under location creator
                currentUserLocationRef.get()
                    .addOnSuccessListener { locDoc ->
                        val locationRef = if (locDoc.exists()) {
                            currentUserLocationRef
                        } else {
                            // Try under the location creator
                            firestore.collection("users")
                                .document(location.createdBy)
                                .collection("cities")
                                .document(cityId)
                                .collection("locations")
                                .document(location.id)
                        }
                        
                        locationRef.collection("reviews")
                            .add(reviewData)
                            .addOnSuccessListener {
                                showAddRatingPopup = false
                                android.widget.Toast.makeText(context, "Beoordeling toegevoegd!", android.widget.Toast.LENGTH_SHORT).show()
                                
                                // Reload reviews from the location where we just saved
                                locationRef.collection("reviews")
                                    .get()
                                    .addOnSuccessListener { reviewsSnapshot ->
                                        val loadedReviews = reviewsSnapshot.documents.mapNotNull { doc ->
                                            try {
                                                val data = doc.data ?: return@mapNotNull null
                                                Review(
                                                    id = doc.id,
                                                    rating = (data["rating"] as? Number)?.toFloat() ?: 0f,
                                                    comment = data["comment"] as? String ?: "",
                                                    userId = data["userId"] as? String ?: "",
                                                    userName = data["userName"] as? String ?: "User",
                                                    createdAt = doc.getDate("createdAt")
                                                )
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        // Merge with existing reviews and remove duplicates
                                        val existingReviewIds = reviews.map { it.id }.toSet()
                                        val newReviews = loadedReviews.filter { it.id !in existingReviewIds }
                                        reviews = (reviews + newReviews).sortedByDescending { it.createdAt?.time ?: 0L }
                                    }
                                
                                // Also trigger full reload via LaunchedEffect
                                reloadReviewsTrigger++
                            }
                            .addOnFailureListener { e ->
                                showAddRatingPopup = false
                                android.widget.Toast.makeText(context, "Fout bij opslaan: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        // If current user location doesn't exist, try under location creator
                        firestore.collection("users")
                            .document(location.createdBy)
                            .collection("cities")
                            .document(cityId)
                            .collection("locations")
                            .document(location.id)
                            .collection("reviews")
                            .add(reviewData)
                            .addOnSuccessListener {
                                showAddRatingPopup = false
                                android.widget.Toast.makeText(context, "Beoordeling toegevoegd!", android.widget.Toast.LENGTH_SHORT).show()
                                // Trigger reload
                                reloadReviewsTrigger++
                            }
                            .addOnFailureListener { e2 ->
                                showAddRatingPopup = false
                                android.widget.Toast.makeText(context, "Fout bij opslaan: ${e2.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                    }
            }
        )
    }
}

@Composable
fun ReviewItem(
    review: Review,
    onMessageClick: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUserId = auth.currentUser?.uid ?: ""
    val isOwnReview = review.userId == currentUserId
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Add user avatar here
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val timeText = remember(review.createdAt) {
                    val now = System.currentTimeMillis()
                    val ts = review.createdAt?.time ?: now
                    val diff = kotlin.math.max(0L, now - ts)
                    val minute = 60_000L
                    val hour = 60 * minute
                    val day = 24 * hour
                    val week = 7 * day
                    when {
                        diff < minute -> "Just now"
                        diff < hour -> "${diff / minute} min ago"
                        diff < day -> "${diff / hour} h ago"
                        diff < week -> "${diff / day} d ago"
                        else -> "${diff / week} w ago"
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = review.userName.ifBlank { "User" }, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = timeText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Only show message button if it's not your own review
                        if (!isOwnReview && currentUserId.isNotBlank()) {
                            IconButton(
                                onClick = onMessageClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Message,
                                    contentDescription = stringResource(R.string.message_user),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { index ->
                        Icon(
                            painter = painterResource(id = R.drawable.ic_star),
                            contentDescription = null,
                            tint = if (index <= review.rating) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(text = review.comment, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

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
fun AddRatingPopup(
    onDismiss: () -> Unit,
    onSubmit: (rating: Float, comment: String) -> Unit
) {
    var rating by remember { mutableStateOf(0f) }
    var comment by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.add_your_rating_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.share_your_experience),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    (1..5).forEach { index ->
                        IconButton(onClick = { rating = index.toFloat() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_star),
                                contentDescription = null,
                                tint = if (index <= rating) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(R.string.comments_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_button))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSubmit(rating, comment) }) {
                        Text(stringResource(R.string.submit_button))
                    }
                }
            }
        }
    }
}
