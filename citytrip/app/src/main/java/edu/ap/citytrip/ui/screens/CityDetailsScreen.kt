package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.ap.citytrip.utils.ImageCache
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import edu.ap.citytrip.R
import edu.ap.citytrip.data.City
import edu.ap.citytrip.data.Location
import edu.ap.citytrip.ui.theme.CitytripTheme
import edu.ap.citytrip.data.AppDatabase
import edu.ap.citytrip.data.LocationEntity
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailsScreen(
    modifier: Modifier = Modifier,
    city: City,
    userId: String,
    onBackClick: () -> Unit = {},
    onAddLocationClick: () -> Unit = {},
    onViewOnMapClick: () -> Unit = {},
    onLocationClick: (Location) -> Unit = {},
    onLocationAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    val firestore = remember { FirebaseFirestore.getInstance() }
    val database = remember { AppDatabase.getDatabase(context) }
    val repositoryScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var locations by remember { mutableStateOf<List<Location>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val isPreview = LocalInspectionMode.current
    var selectedCategory by remember { mutableStateOf("All") }

    DisposableEffect(userId, city.id, isPreview, onLocationAdded) {
        var listener: ListenerRegistration? = null

        when {
            isPreview -> {
                locations = previewLocations()
                isLoading = false
            }
            userId.isBlank() -> {
                locations = emptyList()
                isLoading = false
            }
            else -> {
                isLoading = true
                val locationMap = mutableMapOf<String, Location>()

                fun updateLocationsList() {
                    val uniqueLocations = locationMap.values.toList().sortedByDescending { it.name }
                    locations = uniqueLocations
                    if (selectedCategory != "All" && uniqueLocations.none { it.category == selectedCategory }) {
                        selectedCategory = "All"
                    }
                    isLoading = false
                }

                // FIRST: Load from cache immediately (works offline)
                repositoryScope.launch(Dispatchers.IO) {
                    try {
                        Log.d("CityDetailsScreen", "🏙️ Loading locations for city ${city.id} from cache...")
                        // Use direct sync method for this city
                        val cachedForCity = database.locationDao().getLocationsByCityIdSync(city.id)
                        
                        if (cachedForCity.isNotEmpty()) {
                            Log.d("CityDetailsScreen", "📦 Found ${cachedForCity.size} cached locations for city ${city.id}")
                            val cachedLocs = cachedForCity.map { it.toLocation() }
                            withContext(Dispatchers.Main) {
                                cachedLocs.forEach { loc ->
                                    locationMap[loc.id] = loc
                                }
                                updateLocationsList()
                                isLoading = false
                                Log.d("CityDetailsScreen", "✅ Cache loaded into UI - ${cachedLocs.size} locations visible")
                            }
                        } else {
                            Log.d("CityDetailsScreen", "⚠️ No cache found for city ${city.id}")
                            withContext(Dispatchers.Main) {
                                isLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CityDetailsScreen", "❌ Error loading cache", e)
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            isLoading = false
                        }
                    }
                }

                // THEN: Try to sync with Firebase (will fail if offline, but cache is already loaded)
                listener = firestore.collectionGroup("locations")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w("CityDetailsScreen", "⚠️ Firebase error for city ${city.id}, keeping cache: ${error.message}")
                            // Don't clear locationMap - keep cache that was already loaded
                            // Only update if we have no locations yet
                            if (locationMap.isEmpty()) {
                                // Try to reload from cache as fallback
                                repositoryScope.launch(Dispatchers.IO) {
                                    try {
                                        val allCached = database.locationDao().getAllLocationsSync()
                                        val cachedForCity = allCached.filter { it.cityId == city.id }
                                        if (cachedForCity.isNotEmpty()) {
                                            val cachedLocs = cachedForCity.map { it.toLocation() }
                                            withContext(Dispatchers.Main) {
                                                cachedLocs.forEach { loc ->
                                                    locationMap[loc.id] = loc
                                                }
                                                updateLocationsList()
                                                Log.d("CityDetailsScreen", "📦 Fallback: Loaded ${cachedLocs.size} locations from cache")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CityDetailsScreen", "Error in fallback cache load", e)
                                    }
                                }
                            }
                            return@addSnapshotListener
                        }
                        
                        // Only clear and update if we got valid data from Firebase
                        if (snapshot != null && !snapshot.isEmpty) {
                            val newLocationMap = mutableMapOf<String, Location>()
                            snapshot.documents.forEach { doc ->
                                val parentCityRef = doc.reference.parent.parent
                                if (parentCityRef?.id != city.id) return@forEach
                                val data = doc.data ?: return@forEach
                                val locationId = doc.id
                                // Parse createdAt from Firebase timestamp
                                val createdAtTimestamp = data["createdAt"] as? Timestamp
                                val createdAt = createdAtTimestamp?.toDate()
                                
                                val location = Location(
                                    id = locationId,
                                    name = data["name"] as? String ?: "",
                                    imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                                    category = (data["category"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                                    description = (data["description"] as? String)?.takeIf { it.isNotBlank() } ?: "",
                                    latitude = ((data["latitude"] as? Number)?.toDouble()) ?: 0.0,
                                    longitude = ((data["longitude"] as? Number)?.toDouble()) ?: 0.0,
                                    createdBy = data["createdBy"] as? String ?: "",
                                    createdAt = createdAt
                                )
                                newLocationMap[locationId] = location
                                
                                // Update cache
                                repositoryScope.launch(Dispatchers.IO) {
                                    try {
                                        val entity = LocationEntity.fromLocation(location, city.id)
                                        database.locationDao().insert(entity)
                                    } catch (e: Exception) {
                                        Log.e("CityDetailsScreen", "Error caching location", e)
                                    }
                                }
                            }
                            
                            // Only update if we got new data
                            if (newLocationMap.isNotEmpty()) {
                                locationMap.clear()
                                locationMap.putAll(newLocationMap)
                                updateLocationsList()
                                Log.d("CityDetailsScreen", "✅ Updated locations from Firebase: ${locationMap.size} for city ${city.id}")
                            } else {
                                Log.d("CityDetailsScreen", "⚠️ Firebase returned empty for city ${city.id}, keeping cache")
                            }
                        } else {
                            Log.d("CityDetailsScreen", "⚠️ Firebase snapshot is null or empty, keeping cache")
                        }
                    }
            }
        }

        onDispose {
            listener?.remove()
        }
    }

    val categories = remember(locations) {
        listOf("All") + locations.mapNotNull { it.category }.distinct()
    }

    val filteredLocations = remember(locations, selectedCategory) {
        if (selectedCategory == "All") locations else locations.filter { it.category == selectedCategory }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        city.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            CityDetailsBottomBar(
                onViewOnMapClick = onViewOnMapClick,
                onAddLocationClick = onAddLocationClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            item { HeaderCard(city = city) }
            item {
                CityMetaSection(
                    cityName = city.name,
                    placesCount = locations.size
                )
            }

            if (categories.size > 1) {
                item {
                    CategoryFilterRow(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )
                }
            }

            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                filteredLocations.isEmpty() -> {
                    item {
                        EmptyLocationsView(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth(),
                            onAddLocationClick = onAddLocationClick
                        )
                    }
                }
                else -> {
                    items(filteredLocations, key = { it.id }) { location ->
                        LocationCard(
                            location = location,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            onClick = { onLocationClick(location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun HeaderCard(city: City) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!city.imageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageLoader = remember { ImageCache.getImageLoader(context) }
                AsyncImage(
                    model = ImageCache.createImageRequest(context, city.imageUrl),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            ),
                            startY = 120f
                        )
                    )
            )

            Text(
                text = city.name,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun CityMetaSection(
    cityName: String,
    placesCount: Int
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.city_details_intro, cityName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pluralStringResource(
                id = R.plurals.localities_count,
                count = placesCount,
                placesCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LocationCard(
    location: Location,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                if (!location.imageUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    val imageLoader = remember { ImageCache.getImageLoader(context) }
                    AsyncImage(
                        model = ImageCache.createImageRequest(context, location.imageUrl),
                        imageLoader = imageLoader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(location.category) }
                        )
                    }
                }

                location.description.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLocationsView(
    modifier: Modifier = Modifier,
    onAddLocationClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.city_details_no_places_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = stringResource(R.string.city_details_no_places_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAddLocationClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_add_place))
            }
        }
    }
}

@Composable
private fun CityDetailsBottomBar(
    onViewOnMapClick: () -> Unit,
    onAddLocationClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onViewOnMapClick) {
                Text(text = stringResource(R.string.action_view_on_map))
            }
            Button(onClick = onAddLocationClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_add_place))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CityDetailsScreenPreview() {
    CitytripTheme {
        CityDetailsScreen(
            city = City(
                id = "1",
                name = "Antwerp",
                imageUrl = null,
                localityCount = 6
            ),
            userId = "preview"
        )
    }
}

private fun previewLocations(): List<Location> = listOf(
    Location(
        id = "1",
        name = "Cathedral of Our Lady",
        imageUrl = null,
        category = "Attraction",
        description = "Marvel at the stunning Gothic architecture towering over Antwerp."
    ),
    Location(
        id = "2",
        name = "Museum aan de Stroom (MAS)",
        imageUrl = null,
        category = "Museum",
        description = "Discover Antwerp's history, art, and culture in an iconic riverside museum."
    )
)
