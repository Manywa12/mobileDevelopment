package edu.ap.citytrip.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.ap.citytrip.ui.theme.CitytripTheme
import androidx.compose.ui.res.stringResource
import edu.ap.citytrip.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
// removed duplicate import
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper


@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Language switcher aligned to top-right
        val expanded = remember { mutableStateOf(false) }
        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentLabel = if (currentTag.startsWith("nl")) stringResource(R.string.lang_nl) else stringResource(R.string.lang_en)
        fun Context.findActivity(): Activity? {
            var ctx = this
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
        val activity = LocalContext.current.findActivity()

        Box(
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            OutlinedButton(onClick = { expanded.value = true }) {
                Text(currentLabel)
                Icon(imageVector = Icons.Default.Language, contentDescription = stringResource(R.string.cd_switch_language), modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lang_en)) },
                    onClick = {
                        expanded.value = false
                        val locales = LocaleListCompat.forLanguageTags("en")
                        AppCompatDelegate.setApplicationLocales(locales)
                        activity?.recreate()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lang_nl)) },
                    onClick = {
                        expanded.value = false
                        val locales = LocaleListCompat.forLanguageTags("nl")
                        AppCompatDelegate.setApplicationLocales(locales)
                        activity?.recreate()
                    }
                )
            }
        }

        // Center content
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Globe icon
            Box(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = stringResource(R.string.cd_app_icon),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            }
            
            Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp)
        )

            Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.action_login), style = MaterialTheme.typography.bodyLarge)
        }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
            onClick = onRegisterClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.action_register), style = MaterialTheme.typography.bodyLarge)
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomePreview() {
    CitytripTheme {
        WelcomeScreen(
            onLoginClick = {},
            onRegisterClick = {}
        )
    }
}

