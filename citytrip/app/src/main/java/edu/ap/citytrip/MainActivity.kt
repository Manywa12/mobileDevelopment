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
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import edu.ap.citytrip.data.City
import edu.ap.citytrip.navigation.AuthScreen
import edu.ap.citytrip.ui.screens.*
import edu.ap.citytrip.ui.theme.CitytripTheme
import java.util.UUID

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitytripTheme {
                val auth = remember { FirebaseAuth.getInstance() }
                val firestore = remember { FirebaseFirestore.getInstance() }
                val storage = remember { FirebaseStorage.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                var currentScreen by remember { mutableStateOf(AuthScreen.WELCOME) }
                var showAddCityScreen by remember { mutableStateOf(false) }
                var cities by remember { mutableStateOf<List<City>>(emptyList()) }
                var selectedCity by remember { mutableStateOf<City?>(null) }
                val currentUserId = auth.currentUser?.uid

                // Functie om cities op te halen uit Firestore
                fun loadCities() {
                    val userId = auth.currentUser?.uid ?: return
                    firestore.collection("users")
                        .document(userId)
                        .collection("cities")
                        .get()
                        .addOnSuccessListener { documents ->
                            val loadedCities = documents.mapNotNull { doc ->
                                val data = doc.data
                                val imageUrl = data["imageUrl"] as? String
                                City(
                                    id = doc.id,
                                    name = data["name"] as? String ?: "",
                                    imageUrl = if (imageUrl.isNullOrBlank()) null else imageUrl,
                                    localityCount = (data["localityCount"] as? Long)?.toInt() ?: 0
                                )
                            }
                            cities = loadedCities
                            selectedCity?.let { city ->
                                selectedCity = loadedCities.firstOrNull { it.id == city.id }
                            }
                        }
                }

                // Functie om city op te slaan in Firestore
                fun saveCityToFirestore(cityName: String, imageUrl: String?, cityId: String = UUID.randomUUID().toString()) {
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
                        .addOnSuccessListener {
                            // City opgeslagen, herlaad de lijst
                            loadCities()
                        }
                }

                // Functie om afbeelding te uploaden naar Firebase Storage
                fun uploadImageAndSaveCity(cityName: String, imageUri: Uri?) {
                    if (imageUri == null) {
                        // Geen afbeelding, sla city direct op
                        saveCityToFirestore(cityName, null)
                        return
                    }

                    val userId = auth.currentUser?.uid ?: return
                    val cityId = UUID.randomUUID().toString()
                    val imageRef = storage.reference.child("cities/$userId/$cityId.jpg")
                    
                    imageRef.putFile(imageUri)
                        .addOnSuccessListener {
                            // Haal download URL op
                            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                saveCityToFirestore(cityName, downloadUri.toString(), cityId)
                            }
                            .addOnFailureListener {
                                // Upload gelukt maar download URL ophalen mislukt
                                saveCityToFirestore(cityName, null, cityId)
                            }
                        }
                        .addOnFailureListener {
                            // Upload mislukt, sla city op zonder afbeelding
                            saveCityToFirestore(cityName, null, cityId)
                        }
                }

                // Laad cities wanneer gebruiker inlogt
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        loadCities()
                    } else {
                        cities = emptyList()
                    }
                }

                // DisposableEffect luistert naar veranderingen in de authenticatiestatus
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                        if (isLoggedIn) {
                            currentScreen = AuthScreen.WELCOME // Reset naar welcome na inloggen
                        }
                    }
                    auth.addAuthStateListener(listener)

                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        if (selectedCity != null) {
                            CityDetailsScreen(
                                modifier = Modifier.padding(innerPadding),
                                city = selectedCity!!,
                                userId = currentUserId.orEmpty(),
                                onBackClick = { selectedCity = null }
                            )
                        } else if (showAddCityScreen) {
                            AddCityScreen(
                                modifier = Modifier.padding(innerPadding),
                                onBackClick = { showAddCityScreen = false },
                                onSaveCity = { name, imageUri ->
                                    if (imageUri != null) {
                                        uploadImageAndSaveCity(name, imageUri)
                                    } else {
                                        saveCityToFirestore(name, null)
                                    }
                                }
                            )
                        } else {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                cities = cities,
                                onSignOut = { 
                                    auth.signOut()
                                    currentScreen = AuthScreen.WELCOME
                                    showAddCityScreen = false
                                    selectedCity = null
                                    cities = emptyList() // Reset cities on sign out
                                },
                                onCityClick = { city ->
                                    selectedCity = city
                                },
                                onAddCityClick = {
                                    showAddCityScreen = true
                                }
                            )
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