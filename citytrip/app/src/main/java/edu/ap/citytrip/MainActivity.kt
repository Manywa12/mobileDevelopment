package edu.ap.citytrip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import edu.ap.citytrip.data.City
import edu.ap.citytrip.data.Category
import edu.ap.citytrip.data.Location
import edu.ap.citytrip.navigation.AuthScreen
import edu.ap.citytrip.ui.screens.BottomNavDestination
import edu.ap.citytrip.ui.screens.AddCityScreen
import edu.ap.citytrip.ui.screens.AddLocationScreen

import edu.ap.citytrip.ui.screens.CityDetailsScreen
import edu.ap.citytrip.ui.screens.HomeScreen
import edu.ap.citytrip.ui.screens.LocationDetailsScreen
import edu.ap.citytrip.ui.screens.LoginScreen
import edu.ap.citytrip.ui.screens.MapViewScreen
import edu.ap.citytrip.ui.screens.Message.ChatActivity
import edu.ap.citytrip.ui.screens.Message.MessagesListActivity
import edu.ap.citytrip.ui.screens.Message.UserListActivity

import edu.ap.citytrip.ui.screens.ProfileScreen
import edu.ap.citytrip.ui.screens.RegisterScreen
import edu.ap.citytrip.ui.screens.WelcomeScreen
import edu.ap.citytrip.ui.theme.CitytripTheme
import java.util.UUID
import androidx.activity.ComponentActivity
import edu.ap.citytrip.ui.screens.BottomNavigationBar
import edu.ap.citytrip.ui.screens.Message.MessagesScreen
import edu.ap.citytrip.R
import edu.ap.citytrip.data.AppDatabase
import edu.ap.citytrip.data.LocationRepository
import edu.ap.citytrip.data.LocationEntity
import edu.ap.citytrip.data.LocationDataState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContext = applicationContext
        val contentResolver = appContext.contentResolver

        try {
            val firestoreInit = FirebaseFirestore.getInstance()
            firestoreInit.clearPersistence()
            firestoreInit.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        } catch (_: Exception) { }

        setContent {
            CitytripTheme {
                val context = LocalContext.current
                val auth = remember { FirebaseAuth.getInstance() }
                val firestore = remember { FirebaseFirestore.getInstance() }
                val storage = remember { FirebaseStorage.getInstance() }
                val database = remember { AppDatabase.getDatabase(context) }
                val locationRepository = remember { LocationRepository(database.locationDao(), firestore) }
                val repositoryScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                var currentScreen by remember { mutableStateOf(AuthScreen.WELCOME) }
                var showAddCityScreen by remember { mutableStateOf(false) }
                var showAddLocationScreen by remember { mutableStateOf(false) }
                var showMapViewScreen by remember { mutableStateOf(false) }
                var cities by remember { mutableStateOf<List<City>>(emptyList()) }
                var locationsForMap by remember { mutableStateOf<List<Location>>(emptyList()) }
                var locationCityIdMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var locationDataState by remember { mutableStateOf<LocationDataState>(LocationDataState.Loading()) }
                var selectedCityId by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedLocation by remember { mutableStateOf<Location?>(null) }
                var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedChatTitle by remember { mutableStateOf("") }
                var currentTab by rememberSaveable { mutableStateOf(BottomNavDestination.HOME) }
                val currentUserId = auth.currentUser?.uid
                var locationListeners by remember { mutableStateOf<List<ListenerRegistration>>(emptyList()) }
                var unreadMessageCount by remember { mutableStateOf(0) }

                DisposableEffect(isLoggedIn) {
                    val userId = auth.currentUser?.uid
                    if (isLoggedIn && userId != null) {
                        // Ensure user document exists
                        val user = auth.currentUser
                        firestore.collection("users").document(userId).get()
                            .addOnSuccessListener { doc ->
                                if (!doc.exists() && user != null) {
                                    val profile = hashMapOf(
                                        "name" to (user.displayName ?: user.email?.substringBefore('@') ?: "User"),
                                        "email" to (user.email ?: "")
                                    )
                                    firestore.collection("users").document(userId).set(profile)
                                }
                            }
                        
                        fun loadAllCitiesFallback() {
                            firestore.collection("users")
                                .get()
                                .addOnSuccessListener { usersSnap ->
                                    val all = mutableListOf<City>()
                                    usersSnap.documents.forEach { userDoc ->
                                        firestore.collection("users")
                                            .document(userDoc.id)
                                            .collection("cities")
                                            .get()
                                            .addOnSuccessListener { citySnap ->
                                                val citiesLoaded = citySnap.documents.mapNotNull { doc ->
                                                    val data = doc.data
                                                    val imageUrl = data?.get("imageUrl") as? String
                                                    City(
                                                        id = doc.id,
                                                        name = data?.get("name") as? String ?: "",
                                                        imageUrl = if (imageUrl.isNullOrBlank()) null else imageUrl,
                                                        localityCount = 0, // Will be updated below
                                                        createdBy = userDoc.id
                                                    )
                                                }
                                                all.addAll(citiesLoaded)
                                                cities = all
                                                
                                                // Count locations for each city
                                                val userId = auth.currentUser?.uid
                                                citiesLoaded.forEach { city ->
                                                    var countFromOwner = 0
                                                    var countFromUser = 0
                                                    var ownerDone = false
                                                    var userDone = false
                                                    
                                                    fun updateCount() {
                                                        if (ownerDone && (userId == null || userId == city.createdBy || userDone)) {
                                                            val totalCount = countFromOwner + countFromUser
                                                            cities = cities.map { c ->
                                                                if (c.id == city.id) {
                                                                    c.copy(localityCount = totalCount)
                                                                } else {
                                                                    c
                                                                }
                                                            }
                                                        }
                                                    }
                                                    
                                                    // Count from city owner
                                                    firestore.collection("users")
                                                        .document(city.createdBy)
                                                        .collection("cities")
                                                        .document(city.id)
                                                        .collection("locations")
                                                        .get()
                                                        .addOnSuccessListener { locationsSnapshot ->
                                                            countFromOwner = locationsSnapshot.documents.size
                                                            ownerDone = true
                                                            updateCount()
                                                        }
                                                    
                                                    // Also count from current user if different
                                                    if (userId != null && userId != city.createdBy) {
                                                        firestore.collection("users")
                                                            .document(userId)
                                                            .collection("cities")
                                                            .document(city.id)
                                                            .collection("locations")
                                                            .get()
                                                            .addOnSuccessListener { userLocationsSnapshot ->
                                                                countFromUser = userLocationsSnapshot.documents
                                                                    .count { it.data?.get("cityId") == city.id }
                                                                userDone = true
                                                                updateCount()
                                                            }
                                                    } else {
                                                        userDone = true
                                                    }
                                                }
                                            }
                                    }
                                }
                        }
                        val listener = firestore.collectionGroup("cities")
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    loadAllCitiesFallback()
                                    return@addSnapshotListener
                                }
                                if (snapshot == null) {
                                    loadAllCitiesFallback()
                                    return@addSnapshotListener
                                }
                                val loadedCities = snapshot.documents.mapNotNull { doc ->
                                    val data = doc.data
                                    val imageUrl = data?.get("imageUrl") as? String
                                    val owner = doc.reference.parent.parent?.id ?: ""
                                    City(
                                        id = doc.id,
                                        name = data?.get("name") as? String ?: "",
                                        imageUrl = if (imageUrl.isNullOrBlank()) null else imageUrl,
                                        localityCount = 0, // Will be updated below
                                        createdBy = owner
                                    )
                                }
                                
                                // Calculate actual location counts for each city
                                if (loadedCities.isNotEmpty()) {
                                    cities = loadedCities
                                    val userId = auth.currentUser?.uid
                                    // Count locations for each city
                                    loadedCities.forEach { city ->
                                        var countFromOwner = 0
                                        var countFromUser = 0
                                        var ownerDone = false
                                        var userDone = false
                                        
                                        fun updateCount() {
                                            if (ownerDone && (userId == null || userId == city.createdBy || userDone)) {
                                                val totalCount = countFromOwner + countFromUser
                                                cities = cities.map { c ->
                                                    if (c.id == city.id) {
                                                        c.copy(localityCount = totalCount)
                                                    } else {
                                                        c
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Count from city owner
                                        firestore.collection("users")
                                            .document(city.createdBy)
                                            .collection("cities")
                                            .document(city.id)
                                            .collection("locations")
                                            .get()
                                            .addOnSuccessListener { locationsSnapshot ->
                                                countFromOwner = locationsSnapshot.documents.size
                                                ownerDone = true
                                                updateCount()
                                            }
                                        
                                        // Also count from current user if different
                                        if (userId != null && userId != city.createdBy) {
                                            firestore.collection("users")
                                                .document(userId)
                                                .collection("cities")
                                                .document(city.id)
                                                .collection("locations")
                                                .get()
                                                .addOnSuccessListener { userLocationsSnapshot ->
                                                    countFromUser = userLocationsSnapshot.documents
                                                        .count { it.data?.get("cityId") == city.id }
                                                    userDone = true
                                                    updateCount()
                                                }
                                        } else {
                                            userDone = true
                                        }
                                    }
                                } else {
                                    loadAllCitiesFallback()
                                }
                            }
                        onDispose {
                            listener.remove()
                            locationListeners.forEach { it.remove() }
                        }
                    } else {
                        cities = emptyList()
                        onDispose { }
                    }
                }

                fun checkCityExists(cityName: String, onResult: (Boolean) -> Unit) {
                    val userId = auth.currentUser?.uid ?: run {
                        onResult(false)
                        return
                    }
                    
                    firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val exists = snapshot.documents.any { doc ->
                                val existingName = doc.getString("name") ?: ""
                                existingName.equals(cityName, ignoreCase = true)
                            }
                            onResult(exists)
                        }
                        .addOnFailureListener {
                            Log.e("MainActivity", "Error checking city existence", it)
                            onResult(false)
                        }
                }

                fun saveCityToFirestore(cityName: String, imageUrl: String?, cityId: String = UUID.randomUUID().toString(), onComplete: () -> Unit) {
                    val userId = auth.currentUser?.uid ?: return
                    val cityData = hashMapOf(
                        "name" to cityName,
                        "imageUrl" to (imageUrl ?: ""),
                        "localityCount" to 0L
                    )
                    
                    firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .document(cityId)
                        .set(cityData)
                        .addOnSuccessListener { onComplete() }
                }

                fun uploadImageAndSaveCity(cityName: String, imageUri: Uri?, onComplete: () -> Unit, onError: (String) -> Unit) {
                    // Check if city already exists
                    checkCityExists(cityName) { exists ->
                        if (exists) {
                            onError(getString(R.string.error_city_already_exists))
                            return@checkCityExists
                        }
                        
                        if (imageUri == null) {
                            saveCityToFirestore(cityName, null, onComplete = onComplete)
                            return@checkCityExists
                        }

                        val userId = auth.currentUser?.uid ?: return@checkCityExists
                        val cityId = UUID.randomUUID().toString()
                        val imageRef = storage.reference.child("cities/$userId/$cityId.jpg")

                        val imageBytes = try {
                            contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        } catch (e: Exception) {
                            null
                        }

                        if (imageBytes == null) {
                            saveCityToFirestore(cityName, null, cityId, onComplete)
                            return@checkCityExists
                        }
                        
                        imageRef.putBytes(imageBytes)
                            .addOnSuccessListener {
                                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                    Log.d("MainActivity", "City image uploaded successfully: $downloadUri")
                                    saveCityToFirestore(cityName, downloadUri.toString(), cityId, onComplete)
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Failed to get download URL for city image", e)
                                    Toast.makeText(this@MainActivity, "Fout bij ophalen image URL: ${e.message}", Toast.LENGTH_LONG).show()
                                    saveCityToFirestore(cityName, null, cityId, onComplete)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainActivity", "Failed to upload city image", e)
                                Toast.makeText(this@MainActivity, "Fout bij uploaden image: ${e.message}", Toast.LENGTH_LONG).show()
                                saveCityToFirestore(cityName, null, cityId, onComplete)
                            }
                    }
                }

                fun checkLocationExists(cityId: String, locationName: String, onResult: (Boolean) -> Unit) {
                    val userId = auth.currentUser?.uid ?: run {
                        onResult(false)
                        return
                    }
                    
                    firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .document(cityId)
                        .collection("locations")
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val exists = snapshot.documents.any { doc ->
                                val existingName = doc.getString("name") ?: ""
                                existingName.equals(locationName, ignoreCase = true)
                            }
                            onResult(exists)
                        }
                        .addOnFailureListener {
                            Log.e("MainActivity", "Error checking location existence", it)
                            onResult(false)
                        }
                }

                fun saveLocationToFirestore(
                    cityId: String,
                    cityOwnerId: String,
                    locationName: String,
                    description: String,
                    category: Category,
                    imageUrl: String?,
                    latitude: Double,
                    longitude: Double,
                    onComplete: () -> Unit
                ) {
                    val userId = auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("MainActivity", "User not logged in in saveLocationToFirestore")
                        Toast.makeText(this@MainActivity, "Je bent niet ingelogd", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    Log.d("MainActivity", "saveLocationToFirestore: cityId=$cityId, cityOwnerId=$cityOwnerId, userId=$userId")
                    val locationId = UUID.randomUUID().toString()
                    val locationData = hashMapOf(
                        "name" to locationName,
                        "description" to description,
                        "category" to category.name,
                        "imageUrl" to (imageUrl ?: ""),
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "createdBy" to userId,
                        "cityId" to cityId,
                        "cityOwnerId" to cityOwnerId, // Keep track of the original city owner
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    // Save location under the current user who added it, not the city owner
                    val userRef = firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .document(cityId)
                        .collection("locations")
                        .document(locationId)

                    userRef.set(locationData)
                        .addOnSuccessListener { 
                            Log.d("MainActivity", "Location saved successfully under user $userId")
                            Toast.makeText(this@MainActivity, "Locatie opgeslagen!", Toast.LENGTH_SHORT).show()
                            onComplete() 
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainActivity", "Failed to save location", e)
                            Toast.makeText(this@MainActivity, "Fout bij opslaan: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }

                fun uploadImageAndSaveLocation(
                    cityId: String,
                    cityOwnerId: String,
                    locationName: String,
                    description: String,
                    category: Category,
                    imageUri: Uri?,
                    latitude: Double,
                    longitude: Double,
                    onComplete: () -> Unit,
                    onError: (String) -> Unit
                ) {
                    Log.d("MainActivity", "uploadImageAndSaveLocation called: name=$locationName, cityId=$cityId")
                    val userId = auth.currentUser?.uid
                    if (userId == null) {
                        Log.e("MainActivity", "User not logged in, cannot save location")
                        Toast.makeText(this@MainActivity, "Je bent niet ingelogd", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Check if location already exists
                    checkLocationExists(cityId, locationName) { exists ->
                        if (exists) {
                            onError(getString(R.string.error_location_already_exists))
                            return@checkLocationExists
                        }

                        if (imageUri == null) {
                            Log.d("MainActivity", "No image, saving directly to Firestore")
                            saveLocationToFirestore(cityId, cityOwnerId, locationName, description, category, null, latitude, longitude, onComplete)
                            return@checkLocationExists
                        }

                        val locationId = UUID.randomUUID().toString()
                        // Store image under the current user who is adding the location
                        val imageRef = storage.reference.child("locations/$userId/$cityId/$locationId.jpg")

                        imageRef.putFile(imageUri)
                            .addOnSuccessListener {
                                Log.d("MainActivity", "Image uploaded successfully")
                                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                    Log.d("MainActivity", "Got download URL: $downloadUri")
                                    saveLocationToFirestore(cityId, cityOwnerId, locationName, description, category, downloadUri.toString(), latitude, longitude, onComplete)
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Failed to get download URL", e)
                                    saveLocationToFirestore(cityId, cityOwnerId, locationName, description, category, null, latitude, longitude, onComplete)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainActivity", "Failed to upload image", e)
                                saveLocationToFirestore(cityId, cityOwnerId, locationName, description, category, null, latitude, longitude, onComplete)
                            }
                    }
                }

                fun fetchAllLocations() {
                    android.util.Log.d("MainActivity", "🗺️ fetchAllLocations called - loading from cache first")
                    // FIRST: Load from cache immediately (works offline)
                    repositoryScope.launch(Dispatchers.IO) {
                        try {
                            android.util.Log.d("MainActivity", "🗺️ Querying database for cached locations...")
                            val cachedLocations = database.locationDao().getAllLocationsSync()
                            android.util.Log.d("MainActivity", "🗺️ Cache has ${cachedLocations.size} locations")
                            
                            if (cachedLocations.isNotEmpty()) {
                                cachedLocations.take(3).forEach { entity ->
                                    android.util.Log.d("MainActivity", "   - Map cache item: ${entity.name} (lat: ${entity.latitude}, lng: ${entity.longitude})")
                                }
                                
                                val cachedLocs = cachedLocations.map { it.toLocation() }
                                val cachedMap = cachedLocations.associate { it.id to it.cityId }
                                withContext(Dispatchers.Main) {
                                    locationsForMap = cachedLocs
                                    locationCityIdMap = cachedMap
                                    android.util.Log.d("MainActivity", "✅ Map locations loaded from cache: ${cachedLocs.size}, locationsForMap size: ${locationsForMap.size}")
                                }
                            } else {
                                android.util.Log.w("MainActivity", "⚠️ No cache available for map - database is empty!")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "❌ Error loading cache for map", e)
                            e.printStackTrace()
                        }
                    }
                    
                    // THEN: Try to sync with Firebase (optional, cache already loaded)
                    repositoryScope.launch {
                        try {
                            locationRepository.getAllLocations().collect { locations ->
                                val allEntities = database.locationDao().getAllLocationsSync()
                                val idMap = allEntities.associate { it.id to it.cityId }
                                locationsForMap = locations
                                locationCityIdMap = idMap
                                Log.d("MainActivity", "✅ Map locations updated: ${locations.size}")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error syncing locations for map", e)
                            // Keep cache if Firebase fails
                        }
                    }
                }
                
                fun refreshLocations(onComplete: (Boolean) -> Unit = {}) {
                    Log.d("MainActivity", "🔄 Manual refresh triggered")
                    repositoryScope.launch {
                        // First show cache if available (immediate feedback)
                        val cachedLocations = database.locationDao().getAllLocationsSync()
                        if (cachedLocations.isNotEmpty()) {
                            Log.d("MainActivity", "📦 Showing ${cachedLocations.size} cached locations while refreshing...")
                            val cachedLocs = cachedLocations.map { it.toLocation() }
                            val cachedMap = cachedLocations.associate { it.id to it.cityId }
                            locationsForMap = cachedLocs
                            locationCityIdMap = cachedMap
                        }
                        
                        // Then try to refresh from Firebase
                        val state = locationRepository.refreshLocationsWithState()
                        locationDataState = state
                        when (state) {
                            is LocationDataState.Success -> {
                                val allEntities = database.locationDao().getAllLocationsSync()
                                val idMap = allEntities.associate { it.id to it.cityId }
                                locationsForMap = state.locations
                                locationCityIdMap = idMap
                                Log.d("MainActivity", "✅ Refresh successful: ${state.locations.size} locations")
                                onComplete(true)
                            }
                            is LocationDataState.Error -> {
                                // Use cached locations if Firebase fails
                                state.cachedLocations?.let { cached ->
                                    val allEntities = database.locationDao().getAllLocationsSync()
                                    val idMap = allEntities.associate { it.id to it.cityId }
                                    locationsForMap = cached
                                    locationCityIdMap = idMap
                                    Log.d("MainActivity", "📦 Refresh failed, using ${cached.size} cached locations")
                                } ?: run {
                                    // No cache - try to load from database one more time
                                    val dbCache = database.locationDao().getAllLocationsSync()
                                    if (dbCache.isNotEmpty()) {
                                        val dbLocs = dbCache.map { it.toLocation() }
                                        val dbMap = dbCache.associate { it.id to it.cityId }
                                        locationsForMap = dbLocs
                                        locationCityIdMap = dbMap
                                        Log.d("MainActivity", "📦 Fallback: Using ${dbLocs.size} locations from database")
                                    }
                                }
                                onComplete(false)
                            }
                            is LocationDataState.Loading -> {
                                onComplete(false)
                            }
                        }
                    }
                }

                fun fetchLocationsForCity(cityId: String) {
                    val idMap = mutableMapOf<String, String>()
                    firestore.collectionGroup("locations")
                        .whereEqualTo("cityId", cityId)
                        .get()
                        .addOnSuccessListener { locationsSnapshot ->
                            val locs = locationsSnapshot.documents.mapNotNull { locDoc ->
                                val data = locDoc.data ?: return@mapNotNull null
                                val cityRef = locDoc.reference.parent.parent
                                if (cityRef?.id != cityId) return@mapNotNull null
                                val latAny = data["latitude"]
                                val lonAny = data["longitude"]
                                val latitude = when (latAny) {
                                    is Number -> latAny.toDouble()
                                    else -> 0.0
                                }
                                val longitude = when (lonAny) {
                                    is Number -> lonAny.toDouble()
                                    else -> 0.0
                                }
                                idMap[locDoc.id] = cityId
                                Location(
                                    id = locDoc.id,
                                    name = data["name"] as? String ?: "",
                                    description = data["description"] as? String ?: "",
                                    category = data["category"] as? String ?: "",
                                    latitude = latitude,
                                    longitude = longitude,
                                    imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                                    createdBy = data["createdBy"] as? String ?: ""
                                )
                            }
                            locationsForMap = locs
                            locationCityIdMap = idMap
                        }
                }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                        if (isLoggedIn) {
                            currentScreen = AuthScreen.WELCOME
                        }
                    }
                    auth.addAuthStateListener(listener)

                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                }

                // Listen to unread message count
                DisposableEffect(isLoggedIn, currentUserId) {
                    val userId = currentUserId
                    if (isLoggedIn && userId != null) {
                        val listener = firestore.collection("conversations")
                            .whereArrayContains("participants", userId)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null || snapshot == null) {
                                    unreadMessageCount = 0
                                    return@addSnapshotListener
                                }
                                
                                var totalUnread = 0
                                snapshot.documents.forEach { doc ->
                                    val unreadCountMap = doc.get("unreadCounts") as? Map<*, *> ?: emptyMap<Any, Any>()
                                    val unreadCount = (unreadCountMap[userId] as? Number)?.toInt() ?: 0
                                    totalUnread += unreadCount
                                }
                                unreadMessageCount = totalUnread
                            }
                        
                        onDispose {
                            listener.remove()
                        }
                    } else {
                        unreadMessageCount = 0
                        onDispose {}
                    }
                }

                // Load locations from cache and sync with Firebase on startup
                LaunchedEffect(isLoggedIn) {
                    android.util.Log.d("MainActivity", "🔵 LaunchedEffect triggered - isLoggedIn: $isLoggedIn")
                    if (isLoggedIn) {
                        android.util.Log.d("MainActivity", "🔵 User is logged in, loading cache...")
                        // FIRST: Always try to load from cache immediately (works offline)
                        repositoryScope.launch(Dispatchers.IO) {
                            try {
                                android.util.Log.d("MainActivity", "🔵 Starting cache query...")
                                val cachedLocations = database.locationDao().getAllLocationsSync()
                                android.util.Log.d("MainActivity", "🔵 Cache check complete - found ${cachedLocations.size} locations")
                                
                                // Log first few locations for debugging
                                if (cachedLocations.isNotEmpty()) {
                                    cachedLocations.take(3).forEach { entity ->
                                        android.util.Log.d("MainActivity", "   - Cache item: ${entity.name} (id: ${entity.id}, cityId: ${entity.cityId})")
                                    }
                                }
                                
                                if (cachedLocations.isNotEmpty()) {
                                    android.util.Log.d("MainActivity", "📦 Immediately loading ${cachedLocations.size} locations from CACHE")
                                    val cachedLocs = cachedLocations.map { it.toLocation() }
                                    val cachedMap = cachedLocations.associate { it.id to it.cityId }
                                    withContext(Dispatchers.Main) {
                                        locationsForMap = cachedLocs
                                        locationCityIdMap = cachedMap
                                        android.util.Log.d("MainActivity", "✅ Cache loaded into UI - ${cachedLocs.size} locations, map size: ${locationCityIdMap.size}")
                                    }
                                } else {
                                    android.util.Log.w("MainActivity", "⚠️ No cache found on startup - cache is empty!")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "❌ Error loading cache", e)
                                e.printStackTrace()
                            }
                        }
                        
                        // THEN: Try to sync with Firebase (will fail if offline, but cache is already loaded)
                        repositoryScope.launch {
                            Log.d("MainActivity", "🔵 Starting Firebase sync attempt...")
                            try {
                                locationRepository.getAllLocationsWithState().collect { state ->
                                    Log.d("MainActivity", "🔵 Location state received: ${state::class.simpleName}")
                                    locationDataState = state
                                    when (state) {
                                        is LocationDataState.Success -> {
                                            val allEntities = database.locationDao().getAllLocationsSync()
                                            val newMap = allEntities.associate { it.id to it.cityId }
                                            locationsForMap = state.locations
                                            locationCityIdMap = newMap
                                            Log.d("MainActivity", "✅ Updated locations: ${state.locations.size} (fromCache: ${state.isFromCache})")
                                        }
                                        is LocationDataState.Error -> {
                                            // Always use cached locations if available, even on error
                                            state.cachedLocations?.let { cached ->
                                                Log.d("MainActivity", "📦 Using ${cached.size} cached locations (Firebase unavailable)")
                                                val allEntities = database.locationDao().getAllLocationsSync()
                                                val newMap = allEntities.associate { it.id to it.cityId }
                                                locationsForMap = cached
                                                locationCityIdMap = newMap
                                            } ?: run {
                                                // No cache available - but we already loaded cache above, so keep it
                                                Log.w("MainActivity", "⚠️ Firebase error, but keeping existing cache if available")
                                                // Don't clear - keep what we have
                                            }
                                        }
                                        is LocationDataState.Loading -> {
                                            // Keep current data while loading
                                            Log.d("MainActivity", "⏳ Loading locations...")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "❌ Error in location flow", e)
                                // On error, make sure we at least have cache
                                val cachedLocations = database.locationDao().getAllLocationsSync()
                                if (cachedLocations.isNotEmpty() && locationsForMap.isEmpty()) {
                                    Log.d("MainActivity", "📦 Fallback: Loading ${cachedLocations.size} locations from cache")
                                    val cachedLocs = cachedLocations.map { it.toLocation() }
                                    val cachedMap = cachedLocations.associate { it.id to it.cityId }
                                    locationsForMap = cachedLocs
                                    locationCityIdMap = cachedMap
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            selectedDestination = currentTab,
                            unreadMessageCount = unreadMessageCount,
                            onHomeClick = { currentTab = BottomNavDestination.HOME },
                            onMapClick = {
                                fetchAllLocations()
                                showMapViewScreen = true
                            },
                            onMessagesClick = { currentTab = BottomNavDestination.MESSAGES },
                            onProfileClick = { currentTab = BottomNavDestination.PROFILE },
                            onAddClick = { showAddCityScreen = true }
                        )
                    }
                ) { innerPadding ->
                    if (isLoggedIn) {
                        val selectedCity = cities.firstOrNull { it.id == selectedCityId }
                        when {
                            showMapViewScreen -> {
                                MapViewScreen(
                                    locations = locationsForMap,
                                    onBackClick = { showMapViewScreen = false },
                                    onViewDetailsClick = { location ->
                                        selectedLocation = location
                                        showMapViewScreen = false
                                    }
                                )
                            }
                            selectedLocation != null -> {
                                val loc = selectedLocation!!
                                val cid = locationCityIdMap[loc.id] ?: selectedCity?.id ?: ""
                                LocationDetailsScreen(
                                    location = loc,
                                    onBackClick = { selectedLocation = null },
                                    onViewOnMapClick = {
                                        fetchAllLocations()
                                        selectedLocation = null // Reset selected location first
                                        showMapViewScreen = true
                                    },
                                    cityId = cid
                                )
                            }
                            selectedChatId != null -> {
                                val context = LocalContext.current

                                // Start legacy ChatActivity
                                LaunchedEffect(selectedChatId) {
                                    val intent = Intent(context, ChatActivity::class.java).apply {
                                        putExtra("chatId", selectedChatId)
                                        putExtra("chatTitle", selectedChatTitle)
                                    }
                                    context.startActivity(intent)

                                    // Reset Compose state
                                    selectedChatId = null
                                    selectedChatTitle = ""
                                }

                                // Optional: leeg scherm tijdens navigatie
                                Box(Modifier.fillMaxSize()) {}
                            }
                            selectedCity != null && showAddLocationScreen -> {
                                val city = selectedCity!!
                                AddLocationScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { showAddLocationScreen = false },
                                    onSaveLocation = { name, description, category, imageUri, latitude, longitude, onError ->
                                        uploadImageAndSaveLocation(
                                            cityId = city.id,
                                            cityOwnerId = city.createdBy,
                                            locationName = name,
                                            description = description,
                                            category = category,
                                            imageUri = imageUri,
                                            latitude = latitude,
                                            longitude = longitude,
                                            onComplete = {
                                                showAddLocationScreen = false
                                                // trigger city details refresh
                                                selectedCityId = selectedCityId
                                            },
                                            onError = onError
                                        )
                                    }
                                )
                            }
                            selectedCity != null -> {
                                val city = selectedCity!!
                                CityDetailsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    city = city,
                                    userId = currentUserId.orEmpty(),
                                    onBackClick = { selectedCityId = null },
                                    onAddLocationClick = { showAddLocationScreen = true },
                                    onViewOnMapClick = {
                                        fetchLocationsForCity(city.id)
                                        showMapViewScreen = true
                                    },
                                    onLocationClick = { loc ->
                                        selectedLocation = loc
                                    },
                                    onLocationAdded = {
                                        // Force refresh city details
                                        selectedCityId = selectedCityId
                                    }
                                )
                            }
                            showAddCityScreen -> {
                                AddCityScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { showAddCityScreen = false },
                                    onSaveCity = { name, imageUri, onComplete, onError ->
                                        uploadImageAndSaveCity(name, imageUri, {
                                            onComplete()
                                        }, onError)
                                    }
                                )
                            }
                            else -> {
                                when (currentTab) {
                                    BottomNavDestination.HOME -> {
                                        HomeScreen(
                                            modifier = Modifier.padding(innerPadding),
                                            cities = cities,
                                            onSignOut = {
                                                android.util.Log.d("MainActivity", "🚪 Logging out - cache will remain in database")
                                                auth.signOut()
                                                currentScreen = AuthScreen.WELCOME
                                                showAddCityScreen = false
                                                selectedCityId = null
                                                cities = emptyList()
                                                locationsForMap = emptyList() // Clear UI state, but cache stays in DB
                                                locationCityIdMap = emptyMap()
                                                selectedChatId = null
                                                selectedChatTitle = ""
                                                currentTab = BottomNavDestination.HOME
                                            },
                                            onCityClick = { city ->
                                                selectedCityId = city.id
                                            },
                                            onAddCityClick = { showAddCityScreen = true },
                                            onHomeClick = { currentTab = BottomNavDestination.HOME },
                                            onMapClick = {
                                                fetchAllLocations()
                                                showMapViewScreen = true
                                            },
                                            onMessagesClick = { currentTab = BottomNavDestination.MESSAGES },
                                            onProfileClick = { currentTab = BottomNavDestination.PROFILE },
                                            onRefreshLocations = {
                                                // Check cache first and show info
                                                repositoryScope.launch(Dispatchers.IO) {
                                                    try {
                                                        android.util.Log.d("MainActivity", "🔄 Refresh button clicked - checking cache...")
                                                        val cacheCount = database.locationDao().getLocationCount()
                                                        android.util.Log.d("MainActivity", "🔄 Cache has $cacheCount locations")
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            if (cacheCount > 0) {
                                                                Toast.makeText(context, "Cache: $cacheCount locaties. Vernieuwen...", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "Geen cache! Eerst met internet gebruiken.", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        
                                                        refreshLocations { success ->
                                                            repositoryScope.launch(Dispatchers.IO) {
                                                                if (success) {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Locaties vernieuwd", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } else {
                                                                    val finalCacheCount = database.locationDao().getLocationCount()
                                                                    withContext(Dispatchers.Main) {
                                                                        if (finalCacheCount > 0) {
                                                                            Toast.makeText(context, "Gebruikt gecachte data ($finalCacheCount locaties)", Toast.LENGTH_LONG).show()
                                                                        } else {
                                                                            Toast.makeText(context, "Geen data beschikbaar", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("MainActivity", "❌ Error in refresh", e)
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    BottomNavDestination.MESSAGES -> {
                                        MessagesScreen(
                                            modifier = Modifier.padding(innerPadding),
                                            showBackButton = false
                                        )
                                    }
                                    BottomNavDestination.PROFILE -> {
                                        ProfileScreen(
                                            modifier = Modifier.padding(innerPadding),
                                            userName = auth.currentUser?.displayName,
                                            userEmail = auth.currentUser?.email,
                                            photoUrl = auth.currentUser?.photoUrl?.toString(),
                                            onMyCitiesClick = { currentTab = BottomNavDestination.HOME },
                                            onSettingsClick = { /* TODO */ },
                                            onLogoutClick = {
                                                android.util.Log.d("MainActivity", "🚪 Logging out from profile - cache will remain in database")
                                                auth.signOut()
                                                currentScreen = AuthScreen.WELCOME
                                                showAddCityScreen = false
                                                selectedCityId = null
                                                cities = emptyList()
                                                locationsForMap = emptyList() // Clear UI state, but cache stays in DB
                                                locationCityIdMap = emptyMap()
                                                selectedChatId = null
                                                selectedChatTitle = ""
                                                currentTab = BottomNavDestination.HOME
                                            },
                                            onHomeClick = { currentTab = BottomNavDestination.HOME },
                                            onMapClick = {
                                                fetchAllLocations()
                                                showMapViewScreen = true
                                            },
                                            onMessagesClick = { currentTab = BottomNavDestination.MESSAGES },
                                            onProfileClick = {},
                                            onAddClick = { showAddCityScreen = true }
                                        )
                                    }
                                    else -> {
                                        currentTab = BottomNavDestination.HOME
                                    }
                                }
                            }
                        }
                    } else {
                        when (currentScreen) {
                            AuthScreen.WELCOME -> WelcomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                onLoginClick = { currentScreen = AuthScreen.LOGIN },
                                onRegisterClick = { currentScreen = AuthScreen.REGISTER }
                            )
                            AuthScreen.LOGIN -> LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                auth = auth,
                                onRegisterClick = { currentScreen = AuthScreen.REGISTER }
                            )
                            AuthScreen.REGISTER -> RegisterScreen(
                                modifier = Modifier.padding(innerPadding),
                                auth = auth,
                                onLoginClick = { currentScreen = AuthScreen.LOGIN }
                            )
                        }
                    }
                }
            }
        }
    }

    // LegacyMessagesEntry no longer needed now that MessagesScreen is integrated with bottom navigation.
}