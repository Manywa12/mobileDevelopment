package edu.ap.citytrip.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import edu.ap.citytrip.R
import edu.ap.citytrip.data.Location
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewScreen(
    locations: List<Location>,
    onBackClick: () -> Unit,
    onViewDetailsClick: (Location) -> Unit
) {
    var selectedLocation by remember { mutableStateOf<Location?>(null) }
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                try {
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { location: android.location.Location? ->
                            if (location != null) {
                                userLocation = GeoPoint(location.latitude, location.longitude)
                                mapView.controller.animateTo(userLocation)
                            } else {
                                fusedLocationClient.lastLocation.addOnSuccessListener { last: android.location.Location? ->
                                    if (last != null) {
                                        userLocation = GeoPoint(last.latitude, last.longitude)
                                        mapView.controller.animateTo(userLocation)
                                    }
                                }
                            }
                        }
                } catch (_: SecurityException) { }
            }
        }
    )

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_view_title)) },
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
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            AndroidView(
                factory = {
                    Configuration.getInstance().load(context, context.getSharedPreferences("osm", 0))
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(12.0)

                        locations.forEach { location ->
                            val marker = Marker(this)
                            marker.position = GeoPoint(location.latitude, location.longitude)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.setOnMarkerClickListener { _, _ ->
                                selectedLocation = location
                                true
                            }
                            overlays.add(marker)
                        }
                    }
                }
            )

            FloatingActionButton(
                onClick = {
                    if (userLocation != null) {
                        mapView.controller.animateTo(userLocation)
                    } else {
                        requestPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }

            if (selectedLocation != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color.Gray)
                        ) {
                            if (selectedLocation?.imageUrl != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedLocation?.imageUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            IconButton(
                                onClick = { selectedLocation = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.7f))
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Close"
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = selectedLocation?.name ?: "",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedLocation?.description ?: "",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { selectedLocation?.let(onViewDetailsClick) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.view_details_button))
                            }
                        }
                    }
                }
            }
        }
    }
}