package edu.ap.citytrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import edu.ap.citytrip.navigation.AuthScreen
import edu.ap.citytrip.ui.screens.*
import edu.ap.citytrip.ui.theme.CitytripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitytripTheme {
                val auth = remember { FirebaseAuth.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                var currentScreen by remember { mutableStateOf(AuthScreen.WELCOME) }

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
                        HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSignOut = { 
                                auth.signOut()
                                currentScreen = AuthScreen.WELCOME
                            },
                            onCityClick = { city ->
                                // TODO: Navigate to city details
                            },
                            onAddCityClick = {
                                // TODO: Navigate to add city screen
                            }
                        )
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