package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.ap.citytrip.data.City
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import edu.ap.citytrip.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import edu.ap.citytrip.ui.theme.CitytripTheme
import coil.compose.AsyncImage

enum class BottomNavDestination {
    HOME, MAP, MESSAGES, PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    cities: List<City> = emptyList(),
    onSignOut: () -> Unit,
    onCityClick: (City) -> Unit = {},
    onAddCityClick: () -> Unit = {},
    onMapClick: () -> Unit = {}
) {

    Scaffold(
        modifier = modifier,
        topBar = {
            fun Context.findActivity(): Activity? {
                var ctx = this
                while (ctx is ContextWrapper) {
                    if (ctx is Activity) return ctx
                    ctx = ctx.baseContext
                }
                return null
            }
            val activity = LocalContext.current.findActivity()
            TopAppBar(
                title = { Text(stringResource(R.string.title_your_cities), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = {
                        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                        val nextTag = if (current.startsWith("nl")) "en" else "nl"
                        val locales = LocaleListCompat.forLanguageTags(nextTag)
                        AppCompatDelegate.setApplicationLocales(locales)
                        activity?.recreate()
                    }) {
                        Icon(Icons.Default.Language, contentDescription = stringResource(R.string.cd_switch_language))
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.cd_sign_out))
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedDestination = BottomNavDestination.HOME,
                onHomeClick = {},
                onMapClick = onMapClick,
                onMessagesClick = {},
                onProfileClick = {},
                onAddClick = onAddCityClick
            )
        }
    ) { paddingValues ->
        if (cities.isEmpty()) {
            EmptyCitiesView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAddCityClick = onAddCityClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(cities) { city ->
                    CityCard(
                        city = city,
                        onClick = { onCityClick(city) }
                    )
                }
            }
        }
    }
}

@Composable
fun CityCard(
    city: City,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show image if available, otherwise show placeholder
            if (!city.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = city.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder voor stad afbeelding
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
                        )
                ) {
                    // Placeholder icoon
                    Icon(
                        imageVector = Icons.Default.LocationCity,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Gradient overlay voor betere tekst leesbaarheid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Stad informatie
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pluralStringResource(R.plurals.localities_count, city.localityCount, city.localityCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedDestination: BottomNavDestination,
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIconButton(
                icon = Icons.Default.Home,
                contentDescription = stringResource(R.string.nav_home),
                onClick = onHomeClick,
                isSelected = selectedDestination == BottomNavDestination.HOME
            )
            NavigationIconButton(
                icon = Icons.Default.Map,
                contentDescription = stringResource(R.string.nav_map),
                onClick = onMapClick,
                isSelected = selectedDestination == BottomNavDestination.MAP
            )
            // Grote Add knop in het midden
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_city),
                    tint = Color.White
                )
            }
            NavigationIconButton(
                icon = Icons.Default.Message,
                contentDescription = stringResource(R.string.nav_messages),
                onClick = onMessagesClick,
                isSelected = selectedDestination == BottomNavDestination.MESSAGES
            )
            NavigationIconButton(
                icon = Icons.Default.Person,
                contentDescription = stringResource(R.string.nav_profile),
                onClick = onProfileClick,
                isSelected = selectedDestination == BottomNavDestination.PROFILE
            )
        }
    }
}

@Composable
fun NavigationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isSelected: Boolean
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyCitiesView(
    modifier: Modifier = Modifier,
    onAddCityClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationCity,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_cities_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_cities_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddCityClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_add_city))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    CitytripTheme {
        HomeScreen(
            onSignOut = {},
            onCityClick = {},
            onAddCityClick = {},
            onProfileClick = {},
            onMapClick = {},
            onMessagesClick = {}
        )
    }
}

