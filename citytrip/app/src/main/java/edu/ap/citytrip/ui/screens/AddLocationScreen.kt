package edu.ap.citytrip.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import edu.ap.citytrip.data.Category
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onSaveLocation: (name: String, description: String, category: Category, imageUri: Uri?, latitude: Double, longitude: Double, (String) -> Unit) -> Unit
) {
    var locationName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var duplicateError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }
    var address by remember { mutableStateOf<String?>(null) }
    var isLoadingAddress by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                try {
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc: Location? ->
                            if (loc != null) {
                                latitude = loc.latitude
                                longitude = loc.longitude
                            } else {
                                fusedLocationClient.lastLocation.addOnSuccessListener { last: Location? ->
                                    if (last != null) {
                                        latitude = last.latitude
                                        longitude = last.longitude
                                    }
                                }
                            }
                        }
                } catch (_: SecurityException) { }
            }
        }
    )

    LaunchedEffect(Unit) {
        requestPermissionsLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_location_title)) },
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
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.add_location_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Button to use current location
            OutlinedButton(
                onClick = {
                    requestPermissionsLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_location),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.use_current_location))
            }
            
            // Show map preview if location is set
            if (latitude != 0.0 && longitude != 0.0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Locatie preview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Fetch address when location changes
                LaunchedEffect(latitude, longitude) {
                    if (latitude != 0.0 && longitude != 0.0) {
                        isLoadingAddress = true
                        address = null
                        try {
                            val geocoder = Geocoder(context, java.util.Locale.getDefault())
                            val addresses = withContext(Dispatchers.IO) {
                                geocoder.getFromLocation(latitude, longitude, 1)
                            }
                            if (addresses != null && addresses.isNotEmpty()) {
                                val addressLine = addresses[0]
                                val addressParts = mutableListOf<String>()
                                
                                // Build address from available parts
                                addressLine.getAddressLine(0)?.let { addressParts.add(it) }
                                if (addressParts.isEmpty()) {
                                    addressLine.thoroughfare?.let { addressParts.add(it) }
                                    addressLine.subThoroughfare?.let { addressParts.add(it) }
                                    addressLine.locality?.let { addressParts.add(it) }
                                    addressLine.postalCode?.let { addressParts.add(it) }
                                    addressLine.countryName?.let { addressParts.add(it) }
                                }
                                
                                address = addressParts.joinToString(", ").ifBlank { 
                                    "Adres niet beschikbaar" 
                                }
                            } else {
                                address = "Adres niet beschikbaar"
                            }
                        } catch (e: Exception) {
                            address = "Adres niet beschikbaar"
                        } finally {
                            isLoadingAddress = false
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                ) {
                    val previewMapView = remember { MapView(context) }
                    
                    // Update map when location changes
                    LaunchedEffect(latitude, longitude) {
                        if (latitude != 0.0 && longitude != 0.0) {
                            Configuration.getInstance().load(context, context.getSharedPreferences("osm", 0))
                            previewMapView.apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(latitude, longitude))
                                
                                // Add marker for the location
                                overlays.clear()
                                val marker = Marker(this)
                                marker.position = GeoPoint(latitude, longitude)
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                overlays.add(marker)
                            }
                        }
                    }
                    
                    AndroidView(
                        factory = {
                            Configuration.getInstance().load(context, context.getSharedPreferences("osm", 0))
                            previewMapView.apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                if (latitude != 0.0 && longitude != 0.0) {
                                    controller.setZoom(15.0)
                                    controller.setCenter(GeoPoint(latitude, longitude))
                                    
                                    // Add marker for the location
                                    overlays.clear()
                                    val marker = Marker(this)
                                    marker.position = GeoPoint(latitude, longitude)
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    overlays.add(marker)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = {
                            if (latitude != 0.0 && longitude != 0.0) {
                                it.controller.setCenter(GeoPoint(latitude, longitude))
                                it.overlays.clear()
                                val marker = Marker(it)
                                marker.position = GeoPoint(latitude, longitude)
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                it.overlays.add(marker)
                            }
                        }
                    )
                }
                
                // Display address below map preview
                Spacer(modifier = Modifier.height(12.dp))
                if (isLoadingAddress) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                } else if (address != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = address ?: "Adres niet beschikbaar",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Location Name
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { 
                        locationName = it
                        duplicateError = null
                    },
                    label = { Text(stringResource(R.string.location_name_label)) },
                    placeholder = { Text(stringResource(R.string.location_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = duplicateError != null
                )
                if (duplicateError != null) {
                    Text(
                        text = duplicateError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description_label)) },
                placeholder = { Text(stringResource(R.string.description_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Category
            Box {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_label)) },
                    placeholder = { Text(stringResource(R.string.category_placeholder)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            Modifier.clickable { showCategoryMenu = true }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Category.values().forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Upload Photo
            Text(
                text = stringResource(R.string.upload_photo_label),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.tap_to_select_image), color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (locationName.isNotBlank() && latitude != 0.0 && longitude != 0.0) {
                        val cat = selectedCategory ?: Category.SIGHTSEEING
                        duplicateError = null
                        Toast.makeText(context, "Locatie wordt opgeslagen...", Toast.LENGTH_SHORT).show()
                        onSaveLocation(locationName.trim(), description.trim(), cat, imageUri, latitude, longitude) { errorMessage ->
                            duplicateError = errorMessage
                        }
                    } else {
                        when {
                            locationName.isBlank() -> Toast.makeText(context, "Vul eerst een naam in", Toast.LENGTH_SHORT).show()
                            latitude == 0.0 || longitude == 0.0 -> Toast.makeText(context, "Zoek eerst een adres of gebruik je huidige locatie", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = locationName.isNotBlank() && latitude != 0.0 && longitude != 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(stringResource(R.string.save_location_button), fontSize = 18.sp)
            }
        }
    }
}
