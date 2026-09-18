package com.mw.offlineupi.ui.balance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.ui.components.AppTopBar
import com.mw.offlineupi.ui.components.FailureScreen
import com.mw.offlineupi.ui.components.PinEntryCard
import com.mw.offlineupi.ui.components.PrimaryButton
import com.mw.offlineupi.ui.components.UssdProgressIndicator
import com.mw.offlineupi.ui.components.rememberUssdPermissionLauncher
import com.mw.offlineupi.util.BalanceParser
import com.mw.offlineupi.util.DateFormats

@Composable
fun BalanceCheckScreen(
    onBack: () -> Unit,
    viewModel: BalanceCheckViewModel = viewModel()
) {
    val ussdState by viewModel.ussdState.collectAsState()
    val lastBalance by viewModel.lastBalance.collectAsState()
    val lastBalanceTime by viewModel.lastBalanceTime.collectAsState()
    val launchWithPermission = rememberUssdPermissionLauncher { viewModel.checkBalance() }

    Scaffold(
        topBar = { AppTopBar(title = "Balance Check", onBack = onBack) }
    ) { padding ->
        when (ussdState) {
            is UssdState.Processing, is UssdState.Dialing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    val p = ussdState as? UssdState.Processing
                    UssdProgressIndicator(
                        step = p?.step ?: "Checking balance...",
                        progress = p?.progress ?: 0f
                    )
                }
            }

            is UssdState.Success -> {
                val success = ussdState as UssdState.Success
                viewModel.saveBalance(success.message)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    BalanceSuccessContent(
                        rawMessage = success.message,
                        onDone = { viewModel.reset(); onBack() }
                    )
                }
            }

            is UssdState.Failed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    val f = ussdState as UssdState.Failed
                    FailureScreen(
                        reason = f.reason,
                        unrecognizedResponse = f.unrecognizedResponse,
                        onRetry = { viewModel.reset() },
                        title = "Balance Check Failed"
                    )
                }
            }

            is UssdState.WaitingForPin -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val wp = ussdState as UssdState.WaitingForPin
                    PinEntryCard(
                        message = wp.message,
                        onSubmitPin = { viewModel.sendPin(it) },
                        onCancel = { viewModel.reset() },
                        wrongAttempts = wp.wrongAttempts,
                        errorMessage = wp.errorMessage
                    )
                }
            }

            else -> {
                var balanceVisible by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Hero gradient card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            com.mw.offlineupi.ui.theme.PrimaryDark,
                                            com.mw.offlineupi.ui.theme.Primary,
                                            com.mw.offlineupi.ui.theme.PrimaryLight
                                        )
                                    )
                                )
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Account Balance",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Check via USSD service",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (lastBalance.isNotEmpty()) {
                        val parsedAmount = remember(lastBalance) { BalanceParser.extractAmount(lastBalance) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Last Known Balance",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { balanceVisible = !balanceVisible },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (balanceVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                if (balanceVisible) {
                                    if (parsedAmount != null) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text(
                                                "₹",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                parsedAmount,
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    } else {
                                        // Only reachable for a value written by a build that
                                        // stored the bank's whole USSD reply and could not be
                                        // reduced to a number on load. The response text itself is
                                        // deliberately not shown here.
                                        Text(
                                            "—",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Text(
                                        "₹ ••••••",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }

                                if (lastBalanceTime > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Updated: ${DateFormats.full(lastBalanceTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    PrimaryButton(
                        text = "Check Balance",
                        onClick = { launchWithPermission() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceSuccessContent(
    rawMessage: String,
    onDone: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val balanceAmount = remember(rawMessage) { BalanceParser.extractAmount(rawMessage) }
    val dateTime = remember { DateFormats.full(System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(tween(500)) + fadeIn(tween(500))
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(com.mw.offlineupi.ui.theme.SuccessContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(48.dp),
                    tint = com.mw.offlineupi.ui.theme.Success
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Balance Retrieved!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = com.mw.offlineupi.ui.theme.Success
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Balance card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Available Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (balanceAmount != null) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "₹",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            balanceAmount,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    // Fallback: show raw message if parsing fails
                    Text(
                        rawMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date & time
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Checked at",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dateTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        PrimaryButton(text = "Done", onClick = onDone)
    }
}
