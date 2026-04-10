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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.util.SecurityUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSimDropdown by remember { mutableStateOf(false) }
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
                    subtitle = "Require biometric before sending payments",
                    checked = state.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val activity = context as? FragmentActivity
                            if (activity != null && SecurityUtil.canUseBiometric(context)) {
                                SecurityUtil.showBiometricPrompt(
                                    activity = activity,
                                    title = "Enable Biometric",
                                    subtitle = "Verify to enable biometric for payments",
                                    onSuccess = { viewModel.setBiometric(true) },
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
                "v0.1.0-beta",
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
