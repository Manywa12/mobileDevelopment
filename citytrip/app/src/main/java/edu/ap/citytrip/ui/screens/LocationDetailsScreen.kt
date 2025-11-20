package edu.ap.citytrip.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Location
import edu.ap.citytrip.data.Review

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

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(location) {
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

        firestore.collection("users")
            .document(location.createdBy)
            .collection("cities")
            .document(cityId)
            .collection("locations")
            .document(location.id)
            .collection("reviews")
            .get()
            .addOnSuccessListener { snapshot ->
                reviews = snapshot.toObjects(Review::class.java)
            }
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
                    distance?.let {
                        Text(
                            text = stringResource(R.string.distance_from_current_location, it),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
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
                ReviewItem(review)
            }
        }
    }

    if (showAddRatingPopup) {
        AddRatingPopup(
            onDismiss = { showAddRatingPopup = false },
            onSubmit = { rating, comment ->
                val ownerId = location.createdBy
                val currentUser = FirebaseAuth.getInstance().currentUser
                val raterId = currentUser?.uid ?: ""
                val raterName = currentUser?.displayName
                    ?: (currentUser?.email?.substringBefore('@') ?: "User")
                val reviewData = hashMapOf(
                    "rating" to rating,
                    "comment" to comment,
                    "userId" to raterId,
                    "userName" to raterName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users")
                    .document(ownerId)
                    .collection("cities")
                    .document(cityId)
                    .collection("locations")
                    .document(location.id)
                    .collection("reviews")
                    .add(reviewData)
                    .addOnSuccessListener {
                        showAddRatingPopup = false
                        firestore.collection("users")
                            .document(ownerId)
                            .collection("cities")
                            .document(cityId)
                            .collection("locations")
                            .document(location.id)
                            .collection("reviews")
                            .get()
                            .addOnSuccessListener { snapshot ->
                                reviews = snapshot.toObjects(Review::class.java)
                            }
                    }
                    .addOnFailureListener {
                        showAddRatingPopup = false
                    }
                    .addOnCanceledListener {
                        showAddRatingPopup = false
                    }
            }
        )
    }
}

@Composable
fun ReviewItem(review: Review) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Add user avatar here
            Spacer(modifier = Modifier.width(16.dp))
            Column {
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
                    Text(text = timeText, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
