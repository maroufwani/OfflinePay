package com.mw.offlineupi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mw.offlineupi.util.AppUpdate

/**
 * Update prompt.
 *
 * While [downloading] is true the dialog cannot be dismissed and the buttons are disabled: the
 * download and signature verification are already in flight, and letting the user tap "Install"
 * again started a second concurrent download into the same cache file.
 */
@Composable
fun UpdateDialog(
    update: AppUpdate,
    onInstall: () -> Unit,
    onRemindLater: () -> Unit,
    onIgnore: () -> Unit,
    downloading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onRemindLater() },
        title = {
            Text(
                if (downloading) "Downloading Update" else "Update Available",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "v${update.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (downloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Downloading and verifying…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (update.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        update.releaseNotes.take(500),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                enabled = !downloading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Install Update")
            }
        },
        dismissButton = {
            if (!downloading) {
                Column {
                    OutlinedButton(
                        onClick = onRemindLater,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remind Later")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onIgnore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Ignore This Update",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}
