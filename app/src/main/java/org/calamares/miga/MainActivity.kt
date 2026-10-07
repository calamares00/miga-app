package org.calamares.miga

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.share.ShoppingIntents
import org.calamares.miga.ui.navigation.Destinations
import org.calamares.miga.ui.navigation.MigaNavHost
import org.calamares.miga.ui.settings.SettingsSection
import org.calamares.miga.ui.welcome.WelcomeScreen
import org.calamares.miga.ui.security.BiometricAuthenticator
import org.calamares.miga.ui.theme.MigaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_MIN_DURATION_MILLIS = 1200L
private const val PROMPTS_PREFS = "miga_prompts"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

class MainActivity : FragmentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShoppingIntents.handle(intent)
    }

    /** True once, on Android 13+, when notifications are not allowed yet; later calls return false. */
    private fun shouldAskNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false
        val prefs = getSharedPreferences(PROMPTS_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return false
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(L10n.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) ShoppingIntents.handle(intent)
        val settingsRepository = (application as MigaApp).settingsRepository
        setContent {
            val themeMode by settingsRepository.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val colorTheme by settingsRepository.observeColorTheme().collectAsState(initial = ColorTheme.DEFAULT)
            val biometricLockEnabled by settingsRepository.observeBiometricLockEnabled().collectAsState(initial = null)
            // These start as null while the settings load; the splash stays on screen meanwhile so
            // neither the lock screen nor the content flashes.
            val onboardingDone by settingsRepository.observeOnboardingDone().collectAsState(initial = null)
            var welcomeDestination by remember { mutableStateOf<String?>(null) }
            var unlocked by remember { mutableStateOf(false) }
            var showSplash by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_DURATION_MILLIS)
                showSplash = false
            }

            // The first time an AI task runs, ask for the notification permission (Android 13+)
            // so its progress shows in the status bar and the user learns when it has finished.
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val aiRunning by AiKeepAlive.running.collectAsState()
            LaunchedEffect(aiRunning) {
                if (aiRunning && shouldAskNotificationPermission()) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            MigaTheme(darkTheme = darkTheme, colorTheme = colorTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        showSplash || onboardingDone == null || biometricLockEnabled == null -> SplashScreen()
                        onboardingDone == false -> WelcomeScreen(
                            packsRoute = Destinations.PACKS_CATALOG_ROUTE,
                            backupRoute = Destinations.settingsSection(SettingsSection.BACKUP.id),
                            onFinish = { destination ->
                                welcomeDestination = destination
                                scope.launch { settingsRepository.setOnboardingDone() }
                            }
                        )
                        biometricLockEnabled == true && !unlocked -> LockScreen(
                            onUnlockClick = {
                                scope.launch {
                                    if (BiometricAuthenticator.authenticate(this@MainActivity)) unlocked = true
                                }
                            }
                        )
                        else -> MigaNavHost(initialRoute = welcomeDestination)
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
            text = L10n.str(R.string.recipes_books_kitchen),
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
            text = L10n.str(R.string.unlock_app_continue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(onClick = onUnlockClick) { Text(L10n.str(R.string.unlock)) }
    }
}
