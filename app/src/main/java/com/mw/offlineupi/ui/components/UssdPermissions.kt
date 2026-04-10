package com.mw.offlineupi.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val USSD_PERMISSIONS = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE
)

@Composable
fun rememberUssdPermissionLauncher(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted = rememberUpdatedState(onGranted)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            currentOnGranted.value()
        } else {
            val denied = results.entries.filter { !it.value }.map { entry ->
                when (entry.key) {
                    Manifest.permission.CALL_PHONE -> "Phone Call"
                    Manifest.permission.READ_PHONE_STATE -> "Phone State"
                    else -> entry.key
                }
            }
            Toast.makeText(
                context,
                "Permission required: ${denied.joinToString(", ")}. Please grant to continue.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    return remember(launcher) {
        {
            val allGranted = USSD_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                currentOnGranted.value()
            } else {
                launcher.launch(USSD_PERMISSIONS)
            }
        }
    }
}
