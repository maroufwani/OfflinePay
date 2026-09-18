package com.mw.offlineupi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
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
import com.mw.offlineupi.util.ApkInstaller
import com.mw.offlineupi.util.AppUpdate
import com.mw.offlineupi.util.InstallResult
import com.mw.offlineupi.util.SecurityUtil
import com.mw.offlineupi.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    /**
     * Whether the app lock has been satisfied for the current foreground visit.
     *
     * Held on the Activity rather than in `rememberSaveable` state: saved instance state survives
     * process death, so an unlocked flag stored there meant the lock screen could be skipped
     * entirely after the system killed and restored the app. Cleared in [onStop], so returning from
     * the background always re-prompts.
     */
    private var isUnlockedForSession by mutableStateOf(false)

    /**
     * Update-dialog state, held on the Activity rather than in composition.
     *
     * A rotation recreates the composition, so `remember`ed state here previously dropped the
     * dialog and the "downloading" indicator halfway through a multi-megabyte download while the
     * coroutine kept running invisibly.
     */
    private var pendingUpdate by mutableStateOf<AppUpdate?>(null)
    private var updateDownloading by mutableStateOf(false)

    /** Guards the once-per-process update check against re-running on every recomposition. */
    private var updateCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Transaction amounts, payee names and balances are all on screen here, so keep the whole
        // activity out of screenshots, the recents thumbnail and screen recordings.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val preferences = (application as OfflineUpiApp).preferences

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = "system")

            OfflineUPITheme(themeMode = themeMode) {
                val isOnboarded by preferences.isOnboarded.collectAsState(initial = null)
                // Collected as null until DataStore answers, and gated together with isOnboarded
                // below. Defaulting this to false showed the unlocked app for one frame on every
                // cold start of a lock-enabled install.
                val appLockEnabled by preferences.appLockEnabled
                    .collectAsState(initial = null as Boolean?)
                val isUnlocked = isUnlockedForSession

                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        isOnboarded == null || appLockEnabled == null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            if (isOnboarded == true && appLockEnabled == true && !isUnlocked) {
                                // App lock screen
                                LaunchedEffect(Unit) {
                                    if (SecurityUtil.canUseBiometric(this@MainActivity)) {
                                        SecurityUtil.showBiometricPrompt(
                                            activity = this@MainActivity,
                                            title = "Unlock Offline Pay",
                                            subtitle = "Verify to access the app",
                                            onSuccess = { isUnlockedForSession = true },
                                            onError = { }
                                        )
                                    } else {
                                        isUnlockedForSession = true
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
                                                onSuccess = { isUnlockedForSession = true },
                                                onError = { }
                                            )
                                        }
                                    }) {
                                        Text("Unlock")
                                    }
                                }
                            } else {
                                val navController = rememberNavController()
                                val scope = rememberCoroutineScope()

                                // Check for updates on app start
                                LaunchedEffect(Unit) {
                                    if (updateCheckDone) return@LaunchedEffect
                                    updateCheckDone = true

                                    val now = System.currentTimeMillis()
                                    // At most one network check per 6 hours. The check ran on every
                                    // cold start before, which on a phone that is opened twenty
                                    // times a day is twenty GitHub API calls for a release that
                                    // changes every few weeks.
                                    val lastCheck = withContext(Dispatchers.IO) {
                                        preferences.getLastUpdateCheck()
                                    }
                                    if (now - lastCheck < UPDATE_CHECK_INTERVAL_MS) return@LaunchedEffect

                                    val currentVersion = packageManager
                                        .getPackageInfo(packageName, 0).versionName ?: return@LaunchedEffect
                                    val update = UpdateChecker.checkForUpdate(currentVersion)
                                    withContext(Dispatchers.IO) { preferences.setLastUpdateCheck(now) }
                                    if (update == null) return@LaunchedEffect

                                    // Check if user ignored this version or snoozed
                                    val ignoredVersion = withContext(Dispatchers.IO) {
                                        preferences.getIgnoredUpdateVersion()
                                    }
                                    if (ignoredVersion == update.versionName) return@LaunchedEffect

                                    val remindTime = withContext(Dispatchers.IO) {
                                        preferences.getRemindLaterTime()
                                    }
                                    if (remindTime > 0 && now - remindTime < REMIND_LATER_INTERVAL_MS) {
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
                                        downloading = updateDownloading,
                                        onInstall = {
                                            // The dialog stays up, switched to a progress state, so
                                            // the user is not left looking at an idle screen while
                                            // several MB download. Both flags live on the Activity
                                            // so a rotation mid-download does not lose them.
                                            updateDownloading = true
                                            scope.launch {
                                                val result = ApkInstaller.installUpdate(
                                                    this@MainActivity, update
                                                )
                                                updateDownloading = false
                                                pendingUpdate = null
                                                when (result) {
                                                    is InstallResult.FallbackToBrowser,
                                                    is InstallResult.DownloadFailed ->
                                                        openReleasePage(update)
                                                    // A hash or signature mismatch means the file
                                                    // was not what the release claimed. It has been
                                                    // deleted; say so plainly rather than silently
                                                    // doing nothing, and do not offer a retry that
                                                    // would download the same bad file.
                                                    is InstallResult.HashMismatch -> toast(
                                                        "Update verification failed — the download " +
                                                            "did not match the published checksum " +
                                                            "and was discarded."
                                                    )
                                                    is InstallResult.SignatureMismatch -> toast(
                                                        result.reason
                                                    )
                                                    is InstallResult.Success -> {}
                                                }
                                            }
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

    /**
     * Re-arms the app lock whenever the app leaves the foreground.
     *
     * `onStop` rather than `onPause`: `onPause` also fires for the biometric prompt itself and for
     * the USSD overlay, which would have locked the app in the middle of a payment.
     */
    override fun onStop() {
        super.onStop()
        isUnlockedForSession = false
    }

    private fun openReleasePage(update: AppUpdate) {
        val target = update.htmlUrl.ifEmpty { update.downloadUrl }
        if (target.isEmpty()) return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            // No browser installed, or the URL is malformed.
            toast("Couldn't open the release page.")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val REMIND_LATER_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
