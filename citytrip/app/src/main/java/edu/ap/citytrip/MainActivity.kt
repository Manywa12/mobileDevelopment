package edu.ap.citytrip

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import edu.ap.citytrip.ui.screens.*
import edu.ap.citytrip.ui.theme.CitytripTheme
import java.util.UUID

class MainActivity : AppCompatActivity() {
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
                val currentUserId = auth.currentUser?.uid
                var locationListeners by remember { mutableStateOf<List<ListenerRegistration>>(emptyList()) }

                DisposableEffect(isLoggedIn) {
                    val userId = auth.currentUser?.uid
                    if (isLoggedIn && userId != null) {
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
                                                    val countAny = data?.get("localityCount")
                                                    val count = when (countAny) {
                                                        is Number -> countAny.toInt()
                                                        else -> 0
                                                    }
                                                    City(
                                                        id = doc.id,
                                                        name = data?.get("name") as? String ?: "",
                                                        imageUrl = if (imageUrl.isNullOrBlank()) null else imageUrl,
                                                        localityCount = count,
                                                        createdBy = userDoc.id
                                                    )
                                                }
                                                all.addAll(citiesLoaded)
                                                cities = all
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
                                    val countAny = data?.get("localityCount")
                                    val count = when (countAny) {
                                        is Number -> countAny.toInt()
                                        else -> 0
                                    }
                                    val owner = doc.reference.parent.parent?.id ?: ""
                                    City(
                                        id = doc.id,
                                        name = data?.get("name") as? String ?: "",
                                        imageUrl = if (imageUrl.isNullOrBlank()) null else imageUrl,
                                        localityCount = count,
                                        createdBy = owner
                                    )
                                }
                                if (loadedCities.isEmpty()) {
                                    loadAllCitiesFallback()
                                } else {
                                    cities = loadedCities
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

                fun uploadImageAndSaveCity(cityName: String, imageUri: Uri?, onComplete: () -> Unit) {
                    if (imageUri == null) {
                        saveCityToFirestore(cityName, null, onComplete = onComplete)
                        return
                    }

                    val userId = auth.currentUser?.uid ?: return
                    val cityId = UUID.randomUUID().toString()
                    val imageRef = storage.reference.child("cities/$userId/$cityId.jpg")

                    val imageBytes = try {
                        contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }

                    if (imageBytes == null) {
                        saveCityToFirestore(cityName, null, cityId, onComplete)
                        return
                    }
                    
                    imageRef.putBytes(imageBytes)
                        .addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                saveCityToFirestore(cityName, downloadUri.toString(), cityId, onComplete)
                            }
                            .addOnFailureListener {
                                saveCityToFirestore(cityName, null, cityId, onComplete)
                            }
                        }
                        .addOnFailureListener {
                            saveCityToFirestore(cityName, null, cityId, onComplete)
                        }
                }

                fun saveLocationToFirestore(
                    cityId: String,
                    locationName: String,
                    description: String,
                    category: Category,
                    imageUrl: String?,
                    latitude: Double,
                    longitude: Double,
                    onComplete: () -> Unit
                ) {
                    val userId = auth.currentUser?.uid ?: return
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
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    val cityRef = firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .document(cityId)

                    firestore.runTransaction { transaction ->
                        transaction.set(cityRef.collection("locations").document(locationId), locationData)
                        transaction.update(cityRef, "localityCount", FieldValue.increment(1))
                        null
                    }
                        .addOnSuccessListener { onComplete() }
                        .addOnFailureListener {
                            cityRef.collection("locations").document(locationId)
                                .set(locationData)
                                .addOnSuccessListener { onComplete() }
                        }
                }

                fun uploadImageAndSaveLocation(
                    cityId: String,
                    locationName: String,
                    description: String,
                    category: Category,
                    imageUri: Uri?,
                    latitude: Double,
                    longitude: Double,
                    onComplete: () -> Unit
                ) {
                    if (imageUri == null) {
                        saveLocationToFirestore(cityId, locationName, description, category, null, latitude, longitude, onComplete)
                        return
                    }

                    val userId = auth.currentUser?.uid ?: return
                    val locationId = UUID.randomUUID().toString()
                    val imageRef = storage.reference.child("locations/$userId/$cityId/$locationId.jpg")

                    imageRef.putFile(imageUri)
                        .addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                saveLocationToFirestore(cityId, locationName, description, category, downloadUri.toString(), latitude, longitude, onComplete)
                            }
                            .addOnFailureListener {
                                saveLocationToFirestore(cityId, locationName, description, category, null, latitude, longitude, onComplete)
                            }
                        }
                        .addOnFailureListener {
                            saveLocationToFirestore(cityId, locationName, description, category, null, latitude, longitude, onComplete)
                        }
                }

                fun fetchAllLocations() {
                    val userId = auth.currentUser?.uid ?: return
                    val allLocations = mutableListOf<Location>()
                    val idMap = mutableMapOf<String, String>()
                    firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .get()
                        .addOnSuccessListener { citiesSnapshot ->
                            citiesSnapshot.documents.forEach { cityDoc ->
                                firestore.collection("users")
                                    .document(userId)
                                    .collection("cities")
                                    .document(cityDoc.id)
                                    .collection("locations")
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
                                            val loc = Location(
                                                id = locDoc.id,
                                                name = data["name"] as? String ?: "",
                                                description = data["description"] as? String ?: "",
                                                category = data["category"] as? String ?: "",
                                                latitude = latitude,
                                                longitude = longitude,
                                                imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                                                createdBy = userId
                                            )
                                            allLocations.add(loc)
                                            idMap[locDoc.id] = cityDoc.id
                                        }
                                        locationsForMap = allLocations
                                        locationCityIdMap = idMap
                                    }
                            }
                        }
                }

                fun fetchLocationsForCity(cityId: String) {
                    val userId = auth.currentUser?.uid ?: return
                    val idMap = mutableMapOf<String, String>()
                    firestore.collectionGroup("locations")
                        .whereEqualTo("createdBy", userId)
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        val selectedCity = cities.firstOrNull { it.id == selectedCityId }
                        when {
                            selectedLocation != null -> {
                                val loc = selectedLocation!!
                                val cid = locationCityIdMap[loc.id] ?: selectedCity?.id ?: ""
                                LocationDetailsScreen(
                                    location = loc,
                                    onBackClick = { selectedLocation = null },
                                    onViewOnMapClick = {
                                        fetchAllLocations()
                                        showMapViewScreen = true
                                    },
                                    cityId = cid
                                )
                            }
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
                            selectedCity != null && showAddLocationScreen -> {
                                AddLocationScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { showAddLocationScreen = false },
                                    onSaveLocation = { name, description, category, imageUri, latitude, longitude ->
                                        uploadImageAndSaveLocation(
                                            cityId = selectedCity.id,
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
                                            }
                                        )
                                    }
                                )
                            }
                            selectedCity != null -> {
                                CityDetailsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    city = selectedCity,
                                    userId = currentUserId.orEmpty(),
                                    onBackClick = { selectedCityId = null },
                                    onAddLocationClick = { showAddLocationScreen = true },
                                    onViewOnMapClick = {
                                        fetchLocationsForCity(selectedCity.id)
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
                                    onSaveCity = { name, imageUri, onComplete ->
                                        uploadImageAndSaveCity(name, imageUri) {
                                            onComplete()
                                        }
                                    }
                                )
                            }
                            else -> {
                                HomeScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    cities = cities,
                                    onSignOut = { 
                                        auth.signOut()
                                        currentScreen = AuthScreen.WELCOME
                                        showAddCityScreen = false
                                        selectedCityId = null
                                        cities = emptyList()
                                    },
                                    onCityClick = { city ->
                                        selectedCityId = city.id
                                    },
                                    onAddCityClick = {
                                        showAddCityScreen = true
                                    },
                                    onMapClick = {
                                        fetchAllLocations()
                                        showMapViewScreen = true
                                    }
                                )
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
}