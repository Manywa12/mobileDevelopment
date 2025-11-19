package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Star
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
import com.google.firebase.firestore.FirebaseFirestore
import edu.ap.citytrip.R
import edu.ap.citytrip.data.City
import edu.ap.citytrip.data.Locality
import edu.ap.citytrip.ui.theme.CitytripTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailsScreen(
    modifier: Modifier = Modifier,
    city: City,
    userId: String,
    onBackClick: () -> Unit = {},
    onAddLocalityClick: () -> Unit = {},
    onViewOnMapClick: () -> Unit = {}
) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    var localities by remember { mutableStateOf<List<Locality>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val isPreview = LocalInspectionMode.current
    var selectedCategory by remember { mutableStateOf("All") }

    LaunchedEffect(userId, city.id, isPreview) {
        if (isPreview) {
            localities = previewLocalities()
            isLoading = false
            return@LaunchedEffect
        }

        if (userId.isBlank()) {
            localities = emptyList()
            isLoading = false
        } else {
            isLoading = true
            firestore.collection("users")
                .document(userId)
                .collection("cities")
                .document(city.id)
                .collection("localities")
                .get()
                .addOnSuccessListener { snapshot ->
                    val fetched = snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap()
                        val tags = (data["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        Locality(
                            id = doc.id,
                            name = data["name"] as? String ?: "",
                            imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                            rating = when (val ratingValue = data["rating"]) {
                                is Number -> ratingValue.toDouble()
                                else -> null
                            },
                            category = (data["category"] as? String)?.takeIf { it.isNotBlank() },
                            tags = tags,
                            description = (data["description"] as? String)?.takeIf { it.isNotBlank() }
                        )
                    }.sortedByDescending { it.rating ?: 0.0 }
                    localities = fetched
                    if (selectedCategory != "All" && fetched.none { it.category == selectedCategory }) {
                        selectedCategory = "All"
                    }
                    isLoading = false
                }
                .addOnFailureListener {
                    localities = emptyList()
                    selectedCategory = "All"
                    isLoading = false
                }
        }
    }

    val categories = remember(localities) {
        listOf("All") + localities.mapNotNull { it.category }.distinct()
    }

    val filteredLocalities = remember(localities, selectedCategory) {
        if (selectedCategory == "All") localities else localities.filter { it.category == selectedCategory }
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
                onAddLocalityClick = onAddLocalityClick
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
                    placesCount = localities.size
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
                filteredLocalities.isEmpty() -> {
                    item {
                        EmptyLocalitiesView(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth(),
                            onAddLocalityClick = onAddLocalityClick
                        )
                    }
                }
                else -> {
                    items(filteredLocalities, key = { it.id }) { locality ->
                        LocalityCard(
                            locality = locality,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
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
                AsyncImage(
                    model = city.imageUrl,
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
private fun LocalityCard(
    locality: Locality,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                if (!locality.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = locality.imageUrl,
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

                if (locality.rating != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = String.format("%.1f", locality.rating),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                        text = locality.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        locality.rating?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = String.format("%.1f", it),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        locality.category?.let { category ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(category) }
                            )
                        }
                    }
                }

                if (locality.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        locality.tags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                locality.description?.let {
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
private fun EmptyLocalitiesView(
    modifier: Modifier = Modifier,
    onAddLocalityClick: () -> Unit
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
            OutlinedButton(onClick = onAddLocalityClick) {
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
    onAddLocalityClick: () -> Unit
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
            Button(onClick = onAddLocalityClick) {
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

private fun previewLocalities(): List<Locality> = listOf(
    Locality(
        id = "1",
        name = "Cathedral of Our Lady",
        imageUrl = null,
        rating = 4.8,
        category = "Attraction",
        tags = listOf("Historic", "Landmark"),
        description = "Marvel at the stunning Gothic architecture towering over Antwerp."
    ),
    Locality(
        id = "2",
        name = "Museum aan de Stroom (MAS)",
        imageUrl = null,
        rating = 4.6,
        category = "Museum",
        tags = listOf("Culture", "Architecture"),
        description = "Discover Antwerp's history, art, and culture in an iconic riverside museum."
    )
)


