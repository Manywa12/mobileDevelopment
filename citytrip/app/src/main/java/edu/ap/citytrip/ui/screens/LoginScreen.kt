package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import edu.ap.citytrip.ui.theme.CitytripTheme
import androidx.compose.ui.res.stringResource
import edu.ap.citytrip.R
import androidx.compose.ui.platform.LocalContext

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    auth: FirebaseAuth,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetMessage by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null; resetMessage = null },
            label = { Text(stringResource(R.string.label_email)) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = errorMessage != null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null; resetMessage = null },
            label = { Text(stringResource(R.string.label_password)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorMessage != null
        )

        // Forgot password link
        TextButton(
            onClick = {
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank()) {
                    errorMessage = context.getString(R.string.error_reset_email_required)
                    resetMessage = null
                } else {
                    errorMessage = null
                    resetMessage = null
                    isSendingReset = true
                    auth.sendPasswordResetEmail(trimmedEmail)
                        .addOnCompleteListener { task ->
                            isSendingReset = false
                            if (task.isSuccessful) {
                                resetMessage = context.getString(R.string.reset_email_sent)
                            } else {
                                errorMessage = task.exception?.localizedMessage
                                    ?: context.getString(R.string.error_reset_failed)
                            }
                        }
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = !isLoading && !isSendingReset
        ) {
            Text(stringResource(R.string.forgot_password), color = MaterialTheme.colorScheme.primary)
        }

        // Error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (resetMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = resetMessage!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Login button
        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    resetMessage = null
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (!task.isSuccessful) {
                                errorMessage = task.exception?.localizedMessage 
                                    ?: context.getString(R.string.error_login_failed)
                            }
                        }
                } else {
                    errorMessage = context.getString(R.string.error_login_missing_fields)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) stringResource(R.string.action_login_progress) else stringResource(R.string.action_login))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Register link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.login_no_account))
            TextButton(onClick = onRegisterClick) {
                Text(stringResource(R.string.action_register), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    CitytripTheme {
        LoginScreen(
            auth = FirebaseAuth.getInstance(),
            onRegisterClick = {}
        )
    }
}


