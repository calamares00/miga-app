package com.bmo00.miga

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.ui.navigation.RecetarioNavHost
import com.bmo00.miga.ui.security.BiometricAuthenticator
import com.bmo00.miga.ui.theme.RecetarioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_MIN_DURATION_MILLIS = 1200L

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepository = (application as RecetarioApp).settingsRepository
        setContent {
            val themeMode by settingsRepository.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val biometricLockEnabled by settingsRepository.observeBiometricLockEnabled().collectAsState(initial = false)
            var unlocked by remember { mutableStateOf(false) }
            var showSplash by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_DURATION_MILLIS)
                showSplash = false
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            RecetarioTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        showSplash -> SplashScreen()
                        biometricLockEnabled && !unlocked -> LockScreen(
                            onUnlockClick = {
                                scope.launch {
                                    if (BiometricAuthenticator.authenticate(this@MainActivity)) unlocked = true
                                }
                            }
                        )
                        else -> RecetarioNavHost()
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Text(
            text = "Miga",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Tus recetas. Tus libros. Tu cocina.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LockScreen(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )
        Text("Miga", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Desbloquea la app para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(onClick = onUnlockClick) { Text("Desbloquear") }
    }
}
