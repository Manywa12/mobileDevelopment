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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.ap.citytrip.data.City
import edu.ap.citytrip.ui.theme.CitytripTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit,
    onCityClick: (City) -> Unit = {},
    onAddCityClick: () -> Unit = {}
) {
    // Sample data - later vervangen door echte data uit database
    val cities = remember {
        listOf(
            City("1", "Antwerpen", null, 5),
            City("2", "Brugge", null, 3),
            City("3", "Paris", null, 8),
            City("4", "London", null, 12),
            City("5", "Rome", null, 7)
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Your Cities", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                onHomeClick = {},
                onMapClick = {},
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
                    text = "${city.localityCount} Localities",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
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
                contentDescription = "Home",
                onClick = onHomeClick
            )
            NavigationIconButton(
                icon = Icons.Default.Map,
                contentDescription = "Map",
                onClick = onMapClick
            )
            // Grote Add knop in het midden
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add City",
                    tint = Color.White
                )
            }
            NavigationIconButton(
                icon = Icons.Default.Message,
                contentDescription = "Messages",
                onClick = onMessagesClick
            )
            NavigationIconButton(
                icon = Icons.Default.Person,
                contentDescription = "Profile",
                onClick = onProfileClick
            )
        }
    }
}

@Composable
fun NavigationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
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
            text = "No cities yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your first city to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddCityClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add City")
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
            onAddCityClick = {}
        )
    }
}

