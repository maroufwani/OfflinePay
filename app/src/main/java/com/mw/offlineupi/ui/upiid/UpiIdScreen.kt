package com.mw.offlineupi.ui.upiid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.data.local.entity.RecipientEntity
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.ui.components.AppTopBar
import com.mw.offlineupi.ui.components.FailureScreen
import com.mw.offlineupi.ui.components.PrimaryButton
import com.mw.offlineupi.ui.components.StatusMessage
import com.mw.offlineupi.ui.components.SuccessScreen
import com.mw.offlineupi.ui.components.UssdProgressIndicator
import com.mw.offlineupi.ui.components.rememberUssdPermissionLauncher

@Composable
fun UpiIdScreen(
    onBack: () -> Unit,
    viewModel: UpiIdViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val ussdState by viewModel.ussdState.collectAsState()
    val recentRecipients by viewModel.recentRecipients.collectAsState(initial = emptyList())
    val verifyWithPermission = rememberUssdPermissionLauncher { viewModel.verifyRecipient() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Send to UPI ID",
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            ussdState is UssdState.Processing || ussdState is UssdState.Dialing
                    || ussdState is UssdState.WaitingForInput
                    || ussdState is UssdState.WaitingForPin -> {
                // Overlay handles these states — show simple progress in-app
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    val p = ussdState as? UssdState.Processing
                    UssdProgressIndicator(
                        step = p?.step ?: "Processing...",
                        progress = p?.progress ?: 0f
                    )
                }
            }

            ussdState is UssdState.Success -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    val s = ussdState as UssdState.Success
                    SuccessScreen(
                        payeeName = s.verifiedPayeeName ?: state.upiId,
                        amount = UssdManager.lastSubmittedAmount,
                        referenceId = s.referenceId,
                        onDone = { viewModel.reset(); onBack() }
                    )
                }
            }

            ussdState is UssdState.Failed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    val f = ussdState as UssdState.Failed
                    FailureScreen(
                        reason = f.reason,
                        onRetry = { viewModel.reset() }
                    )
                }
            }

            // UPI ID input (step 1 only — overlay handles step 2)
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Enter a UPI ID to verify and send payment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = state.upiId,
                        onValueChange = viewModel::updateUpiId,
                        label = { Text("UPI ID") },
                        placeholder = { Text("example@bank") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    state.error?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusMessage(message = it, isError = true)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    PrimaryButton(
                        text = "Verify & Proceed",
                        onClick = { verifyWithPermission() },
                        enabled = state.upiId.isNotEmpty()
                    )

                    if (recentRecipients.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentRecipients) { recipient ->
                                RecentUpiChip(
                                    recipient = recipient,
                                    onClick = { viewModel.selectRecipient(recipient) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentUpiChip(
    recipient: RecipientEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .width(80.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.AlternateEmail,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                recipient.name.ifEmpty { recipient.upiId },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
            Text(
                recipient.upiId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = 9.sp
            )
        }
    }
}
