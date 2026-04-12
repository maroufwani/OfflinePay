package com.mw.offlineupi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.mw.offlineupi.ui.components.UpdateDialog
import com.mw.offlineupi.ui.navigation.AppNavGraph
import com.mw.offlineupi.ui.navigation.Screen
import com.mw.offlineupi.ui.theme.OfflineUPITheme
import com.mw.offlineupi.util.AppUpdate
import com.mw.offlineupi.util.SecurityUtil
import com.mw.offlineupi.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = (application as OfflineUpiApp).preferences

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = "system")

            OfflineUPITheme(themeMode = themeMode) {
                val isOnboarded by preferences.isOnboarded.collectAsState(initial = null)
                val appLockEnabled by preferences.appLockEnabled.collectAsState(initial = false)
                var isUnlocked by rememberSaveable { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (isOnboarded) {
                        null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            if (isOnboarded == true && appLockEnabled && !isUnlocked) {
                                // App lock screen
                                LaunchedEffect(Unit) {
                                    if (SecurityUtil.canUseBiometric(this@MainActivity)) {
                                        SecurityUtil.showBiometricPrompt(
                                            activity = this@MainActivity,
                                            title = "Unlock Offline Pay",
                                            subtitle = "Verify to access the app",
                                            onSuccess = { isUnlocked = true },
                                            onError = { }
                                        )
                                    } else {
                                        isUnlocked = true
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "App Locked",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Verify your identity to continue",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    TextButton(onClick = {
                                        if (SecurityUtil.canUseBiometric(this@MainActivity)) {
                                            SecurityUtil.showBiometricPrompt(
                                                activity = this@MainActivity,
                                                title = "Unlock Offline Pay",
                                                subtitle = "Verify to access the app",
                                                onSuccess = { isUnlocked = true },
                                                onError = { }
                                            )
                                        }
                                    }) {
                                        Text("Unlock")
                                    }
                                }
                            } else {
                                val navController = rememberNavController()
                                var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }
                                val scope = rememberCoroutineScope()

                                // Check for updates on app start
                                LaunchedEffect(Unit) {
                                    val currentVersion = packageManager
                                        .getPackageInfo(packageName, 0).versionName ?: return@LaunchedEffect
                                    val update = UpdateChecker.checkForUpdate(currentVersion)
                                        ?: return@LaunchedEffect

                                    // Check if user ignored this version or snoozed
                                    val ignoredVersion = withContext(Dispatchers.IO) {
                                        preferences.getIgnoredUpdateVersion()
                                    }
                                    if (ignoredVersion == update.versionName) return@LaunchedEffect

                                    val remindTime = withContext(Dispatchers.IO) {
                                        preferences.getRemindLaterTime()
                                    }
                                    val remindInterval = 24 * 60 * 60 * 1000L // 24 hours
                                    if (remindTime > 0 && System.currentTimeMillis() - remindTime < remindInterval) {
                                        return@LaunchedEffect
                                    }

                                    pendingUpdate = update
                                }

                                AppNavGraph(
                                    navController = navController,
                                    startDestination = if (isOnboarded == true) Screen.Home.route
                                    else Screen.Onboarding.route
                                )

                                pendingUpdate?.let { update ->
                                    UpdateDialog(
                                        update = update,
                                        onInstall = {
                                            pendingUpdate = null
                                            val url = update.downloadUrl.ifEmpty { update.htmlUrl }
                                            startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                            )
                                        },
                                        onRemindLater = {
                                            pendingUpdate = null
                                            scope.launch {
                                                preferences.setRemindLaterTime(System.currentTimeMillis())
                                            }
                                        },
                                        onIgnore = {
                                            pendingUpdate = null
                                            scope.launch {
                                                preferences.setIgnoredUpdateVersion(update.versionName)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
