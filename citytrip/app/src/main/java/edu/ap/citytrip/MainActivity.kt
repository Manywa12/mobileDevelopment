package edu.ap.citytrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import edu.ap.citytrip.ui.theme.CitytripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitytripTheme {
                val auth = remember { FirebaseAuth.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth: FirebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        HomeScreen(modifier = Modifier.padding(innerPadding), onSignOut = { auth.signOut() })
                    } else {
                        LoginScreen(modifier = Modifier.padding(innerPadding), auth = auth)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    auth: FirebaseAuth
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Welcome Back", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                isLoading = true
                errorMessage = null
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (!task.isSuccessful) {
                            errorMessage = task.exception?.localizedMessage ?: "Login mislukt"
                        }
                    }
            } else {
                errorMessage = "Vul email en wachtwoord in"
            }
        }) {
            Text(if (isLoading) "Bezig..." else "Inloggen")
        }

        TextButton(onClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                isLoading = true
                errorMessage = null
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (!task.isSuccessful) {
                            errorMessage = task.exception?.localizedMessage ?: "Registratie mislukt"
                        }
                    }
            } else {
                errorMessage = "Maak een account: vul email en wachtwoord in"
            }
        }) {
            Text("Account aanmaken")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    CitytripTheme {
        LoginScreen(auth = FirebaseAuth.getInstance())
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onSignOut: () -> Unit) {
    Column(modifier = modifier.padding(24.dp)) {
        Text("Welkom!")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSignOut) { Text("Sign out") }
    }
}