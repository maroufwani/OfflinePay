package com.mw.offlineupi.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.ui.components.UpdateDialog
import com.mw.offlineupi.util.ApkInstaller
import com.mw.offlineupi.service.UssdAccessibilityService
import com.mw.offlineupi.util.DiagnosticLog
import com.mw.offlineupi.util.InstallResult
import com.mw.offlineupi.util.SecurityUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSimDropdown by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Profile Section
            val profile = state.userProfile
            if (profile != null && (profile.name.isNotEmpty() || profile.upiId.isNotEmpty())) {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                profile.name.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (profile.name.isNotEmpty()) {
                                Text(
                                    profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (profile.upiId.isNotEmpty()) {
                                Text(
                                    profile.upiId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (profile.bankName.isNotEmpty()) {
                                Text(
                                    "${profile.bankName}${if (profile.maskedAccountNumber.isNotEmpty()) " • ${profile.maskedAccountNumber}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Security Section
            SectionHeader("Security")
            SettingsCard {
                SettingsSwitchItem(
                    icon = Icons.Default.Lock,
                    title = "App Lock",
                    subtitle = if (state.appLockEnabled) "Require authentication to open app"
                    else "Anyone can open this app",
                    checked = state.appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val activity = context as? FragmentActivity
                            if (activity != null && SecurityUtil.canUseBiometric(context)) {
                                SecurityUtil.showBiometricPrompt(
                                    activity = activity,
                                    title = "Enable App Lock",
                                    subtitle = "Verify to enable app lock",
                                    onSuccess = { viewModel.setAppLock(true) },
                                    onError = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Biometric/screen lock not available", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.setAppLock(false)
                        }
                    }
                )
                SettingsDivider()
                SettingsSwitchItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric for Payments",
                    subtitle = if (state.biometricEnabled) "Biometric replaces UPI PIN entry"
                    else "Use biometric to auto-fill UPI PIN",
                    checked = state.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val activity = context as? FragmentActivity
                            if (activity != null && SecurityUtil.canUseBiometric(context)) {
                                SecurityUtil.showBiometricPrompt(
                                    activity = activity,
                                    title = "Enable Biometric",
                                    subtitle = "Verify to enable biometric for payments",
                                    onSuccess = { showPinDialog = true },
                                    onError = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Biometric not available", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.setBiometric(false)
                        }
                    }
                )
            }
            PinStorageNote()

            Spacer(modifier = Modifier.height(20.dp))

            // Appearance Section
            SectionHeader("Appearance")
            SettingsCard {
                SettingsClickItem(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = when (state.themeMode) {
                        "light" -> "Light"
                        "dark" -> "Dark"
                        else -> "System default"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // General Section  
            SectionHeader("General")
            SettingsCard {
                Box {
                    SettingsClickItem(
                        icon = Icons.Default.SimCard,
                        title = "SIM Card",
                        subtitle = "SIM ${state.selectedSim + 1} selected",
                        onClick = { showSimDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showSimDropdown,
                        onDismissRequest = { showSimDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("SIM 1") },
                            onClick = {
                                viewModel.setSim(0)
                                showSimDropdown = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SimCard, contentDescription = null,
                                    modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (state.selectedSim == 0) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SIM 2") },
                            onClick = {
                                viewModel.setSim(1)
                                showSimDropdown = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SimCard, contentDescription = null,
                                    modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (state.selectedSim == 1) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service",
                    subtitle = if (state.accessibilityEnabled) "Enabled" else "Disabled — tap to enable",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Security,
                    title = "App Permissions",
                    subtitle = "Manage phone, camera permissions",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = if (state.updateCheckInProgress) "Checking..."
                    else state.updateCheckMessage ?: "Tap to check for new versions",
                    onClick = {
                        if (!state.updateCheckInProgress) {
                            viewModel.checkForUpdates()
                        }
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.BugReport,
                    title = "Diagnostics",
                    // The bound state is the one thing that cannot be seen from system Settings:
                    // the switch there can read On while the service was never bound to the app,
                    // which is exactly the state in which nothing is automated and no overlay shows.
                    subtitle = if (UssdAccessibilityService.getInstance() != null) {
                        "Service bound. Tap to share a report"
                    } else {
                        "Service NOT bound — tap to share a report"
                    },
                    onClick = { DiagnosticLog.share(context) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Legal Section
            SectionHeader("Legal")
            SettingsCard {
                SettingsClickItem(
                    icon = Icons.Default.Description,
                    title = "Privacy Policy",
                    subtitle = "How your data is handled",
                    onClick = {
                        openUrl("https://github.com/maroufwani/OfflinePay/blob/master/PRIVACY_POLICY.md")
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Gavel,
                    title = "Terms of Service",
                    subtitle = "Usage terms and conditions",
                    onClick = {
                        openUrl("https://github.com/maroufwani/OfflinePay/blob/master/TERMS_OF_SERVICE.md")
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Warning,
                    title = "Disclaimer",
                    subtitle = "Risk acknowledgment",
                    onClick = {
                        openUrl("https://github.com/maroufwani/OfflinePay/blob/master/DISCLAIMER.md")
                    }
                )
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Policy,
                    title = "License",
                    subtitle = "Apache License 2.0",
                    onClick = {
                        openUrl("https://github.com/maroufwani/OfflinePay/blob/master/LICENSE")
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "This app is not a licensed payment service provider. " +
                    "Not endorsed by NPCI, RBI, or any bank. Use at your own risk.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "v${
                    try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0-beta" }
                    catch (_: Exception) { "0.1.0-beta" }
                }",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = state.themeMode,
            onThemeSelected = { mode ->
                viewModel.setTheme(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showPinDialog) {
        UpiPinDialog(
            onConfirm = { pin ->
                // saveUpiPin owns the array from here: it wipes it and flips the toggle itself,
                // only if the auth-bound Keystore write succeeded.
                showPinDialog = false
                viewModel.saveUpiPin(pin) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Biometric payment enabled"
                        else "Couldn't store the PIN securely. Unlock your device and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }

    state.availableUpdate?.let { update ->
        UpdateDialog(
            update = update,
            onInstall = {
                viewModel.dismissUpdate()
                scope.launch {
                    val result = ApkInstaller.installUpdate(context, update)
                    if (result is InstallResult.FallbackToBrowser ||
                        result is InstallResult.DownloadFailed
                    ) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                    // HashMismatch: do nothing — the tampered file was deleted.
                }
            },
            onRemindLater = { viewModel.remindLater() },
            onIgnore = { viewModel.ignoreUpdate(update.versionName) }
        )
    }
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose Theme", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                ThemeOption(
                    label = "System default",
                    icon = Icons.Default.PhoneAndroid,
                    selected = currentTheme == "system",
                    onClick = { onThemeSelected("system") }
                )
                ThemeOption(
                    label = "Light",
                    icon = Icons.Default.Palette,
                    selected = currentTheme == "light",
                    onClick = { onThemeSelected("light") }
                )
                ThemeOption(
                    label = "Dark",
                    icon = Icons.Default.DarkMode,
                    selected = currentTheme == "dark",
                    onClick = { onThemeSelected("dark") }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * Two-step UPI PIN capture.
 *
 * The PIN is held in fixed [CharArray] buffers rather than `String`s: `pin += digit` created a
 * fresh interned-pool string per keystroke, leaving up to six partial PINs recoverable from the
 * heap with no way to wipe them. The buffers here are zeroed on dispose, and [onConfirm]
 * receives ownership of an exact-length copy.
 *
 * Deliberately not `rememberSaveable` — PIN digits must not reach the saved-instance-state
 * bundle, so a configuration change discards the entry and the dialog restarts empty.
 */
@Composable
private fun UpiPinDialog(
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    val maxPinLength = 6
    val pin = remember { CharArray(maxPinLength) }
    val confirmPin = remember { CharArray(maxPinLength) }
    var pinLength by remember { mutableIntStateOf(0) }
    var confirmLength by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(1) } // 1 = enter, 2 = confirm

    DisposableEffect(Unit) {
        onDispose {
            pin.fill(' ')
            confirmPin.fill(' ')
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (step == 1) "Enter UPI PIN" else "Confirm UPI PIN",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            // Scrollable: the numpad plus PinStorageDisclosure is taller than a short screen, and an
            // AlertDialog text slot does not scroll on its own.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (step == 1) "Enter your UPI PIN to store it on this device for biometric payments."
                    else "Re-enter your UPI PIN to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (step == 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PinStorageDisclosure()
                }
                Spacer(modifier = Modifier.height(16.dp))
                PinDotsRow(
                    pinLength = if (step == 1) pinLength else confirmLength,
                    maxLength = maxPinLength
                )
                Spacer(modifier = Modifier.height(12.dp))
                PinNumpad(
                    onDigit = { digit ->
                        val c = digit.firstOrNull() ?: return@PinNumpad
                        if (step == 1 && pinLength < maxPinLength) {
                            pin[pinLength] = c
                            pinLength++
                            error = null
                        } else if (step == 2 && confirmLength < maxPinLength) {
                            confirmPin[confirmLength] = c
                            confirmLength++
                            error = null
                        }
                    },
                    onBackspace = {
                        if (step == 1 && pinLength > 0) {
                            pinLength--
                            pin[pinLength] = ' '
                            error = null
                        } else if (step == 2 && confirmLength > 0) {
                            confirmLength--
                            confirmPin[confirmLength] = ' '
                            error = null
                        }
                    }
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (step == 1) {
                        if (pinLength < 4) {
                            error = "PIN must be 4-6 digits"
                        } else {
                            step = 2
                            error = null
                        }
                    } else {
                        val matches = confirmLength == pinLength &&
                            (0 until pinLength).all { pin[it] == confirmPin[it] }
                        if (!matches) {
                            error = "PINs don't match"
                            confirmPin.fill(' ')
                            confirmLength = 0
                        } else {
                            // Hand over an exact-length copy; the buffers are wiped on dispose.
                            onConfirm(pin.copyOf(pinLength))
                        }
                    }
                }
            ) {
                Text(if (step == 1) "Next" else "Confirm")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (step == 2) {
                    step = 1
                    confirmPin.fill(' ')
                    confirmLength = 0
                    error = null
                } else {
                    onDismiss()
                }
            }) {
                Text(if (step == 2) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun PinDotsRow(pinLength: Int, maxLength: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(maxLength) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < pinLength) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun PinNumpad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in keys) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (key in row) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (key.isNotEmpty()) MaterialTheme.colorScheme.surfaceContainerHigh
                                else Color.Transparent
                            )
                            .then(
                                if (key.isNotEmpty()) Modifier.clickable {
                                    if (key == "⌫") onBackspace() else onDigit(key)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Backspace",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else if (key.isNotEmpty()) {
                            Text(
                                key,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plain statement of what storing the UPI PIN means, shown at the moment the user is asked for it.
 *
 * Worth the screen space rather than being left to a policy document nobody opens. A user
 * reasonably assumes a UPI PIN is only ever typed into their bank's own keypad, and NPCI's guidance
 * says the same — so an app that keeps one has to say so while the user still has the choice, not
 * afterwards. The honest version of the trade is also short enough to actually read.
 *
 * Worded as what happens rather than as reassurance. "Stored on this device" is the fact a user
 * needs in order to decide; calling it "secure" is not, and the app saying so proves nothing.
 */
@Composable
private fun PinStorageDisclosure() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Where this PIN goes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val points = listOf(
                "It is saved on this phone. It is never uploaded — this app has no account and no server that could receive it.",
                "It is encrypted with a key inside your phone's secure hardware, which refuses to decrypt it without a fresh fingerprint or screen unlock.",
                "It is used only to answer the PIN prompt of a payment you started yourself.",
                "Turning Biometric for Payments off deletes it.",
                "NPCI's guidance is that a UPI PIN should only be entered on your bank's own screen. Keeping it here is a convenience you are choosing — you can skip this and type it each time instead."
            )
            for (point in points) {
                Row {
                    Text(
                        "•  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        point,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * The same disclosure in one sentence, under the Security card, so it stays findable after the PIN
 * has been stored — [PinStorageDisclosure] is only on screen while the switch is being turned on,
 * and by definition a user who wants to check later has already dismissed it.
 */
@Composable
private fun PinStorageNote() {
    Text(
        "Biometric payments keep your UPI PIN on this device, encrypted with a key your phone will " +
            "not unlock without your fingerprint or screen lock. It is never uploaded, and turning " +
            "the switch off deletes it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}
