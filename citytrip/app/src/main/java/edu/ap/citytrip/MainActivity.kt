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
                val auth = remember { FirebaseAuth.getInstance() }
                val firestore = remember { FirebaseFirestore.getInstance() }
                val storage = remember { FirebaseStorage.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                var currentScreen by remember { mutableStateOf(AuthScreen.WELCOME) }
                var showAddCityScreen by remember { mutableStateOf(false) }
                var showAddLocationScreen by remember { mutableStateOf(false) }
                var showMapViewScreen by remember { mutableStateOf(false) }
                var cities by remember { mutableStateOf<List<City>>(emptyList()) }
                var locationsForMap by remember { mutableStateOf<List<Location>>(emptyList()) }
                var locationCityIdMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var selectedCityId by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedLocation by remember { mutableStateOf<Location?>(null) }
                var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedChatTitle by remember { mutableStateOf("") }
                var currentTab by rememberSaveable { mutableStateOf(BottomNavDestination.HOME) }
                val currentUserId = auth.currentUser?.uid
                var locationListeners by remember { mutableStateOf<List<ListenerRegistration>>(emptyList()) }

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
                                                
                                                val userId = auth.currentUser?.uid
                                                citiesLoaded.forEach { city ->
                                                    firestore.collectionGroup("locations")
                                                        .get()
                                                        .addOnSuccessListener { locationsSnapshot ->
                                                            val totalCount = locationsSnapshot.documents.count { it.reference.parent.parent?.id == city.id }
                                                            cities = cities.map { c ->
                                                                if (c.id == city.id) {
                                                                    c.copy(localityCount = totalCount)
                                                                } else {
                                                                    c
                                                                }
                                                            }
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
                                
                                if (loadedCities.isNotEmpty()) {
                                    cities = loadedCities
                                    loadedCities.forEach { city ->
                                        firestore.collectionGroup("locations")
                                            .get()
                                            .addOnSuccessListener { locationsSnapshot ->
                                                val totalCount = locationsSnapshot.documents.count { it.reference.parent.parent?.id == city.id }
                                                cities = cities.map { c ->
                                                    if (c.id == city.id) {
                                                        c.copy(localityCount = totalCount)
                                                    } else {
                                                        c
                                                    }
                                                }
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
                    val allLocations = mutableListOf<Location>()
                    val idMap = mutableMapOf<String, String>()
                    firestore.collectionGroup("locations")
                        .get()
                        .addOnSuccessListener { locationsSnapshot ->
                            locationsSnapshot.documents.forEach { locDoc ->
                                val data = locDoc.data ?: emptyMap()
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
                                val cityRef = locDoc.reference.parent.parent
                                val cityId = cityRef?.id ?: ""
                                val loc = Location(
                                    id = locDoc.id,
                                    name = data["name"] as? String ?: "",
                                    description = data["description"] as? String ?: "",
                                    category = data["category"] as? String ?: "",
                                    latitude = latitude,
                                    longitude = longitude,
                                    imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                                    createdBy = data["createdBy"] as? String ?: ""
                                )
                                allLocations.add(loc)
                                idMap[locDoc.id] = cityId
                            }
                            locationsForMap = allLocations
                            locationCityIdMap = idMap
                        }
                }

                fun fetchLocationsForCity(cityId: String) {
                    val idMap = mutableMapOf<String, String>()
                    firestore.collectionGroup("locations")
                        .get()
                        .addOnSuccessListener { locationsSnapshot ->
                            val locs = locationsSnapshot.documents.mapNotNull { locDoc ->
                                val parentCityRef = locDoc.reference.parent.parent
                                if (parentCityRef?.id != cityId) return@mapNotNull null
                                val data = locDoc.data ?: return@mapNotNull null
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            selectedDestination = currentTab,
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
                                                auth.signOut()
                                                currentScreen = AuthScreen.WELCOME
                                                showAddCityScreen = false
                                                selectedCityId = null
                                                cities = emptyList()
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
                                            onProfileClick = { currentTab = BottomNavDestination.PROFILE }
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
                                                auth.signOut()
                                                currentScreen = AuthScreen.WELCOME
                                                showAddCityScreen = false
                                                selectedCityId = null
                                                cities = emptyList()
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
