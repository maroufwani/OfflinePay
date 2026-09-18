package com.mw.offlineupi.ui.requestmoney

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun RequestMoneyScreen(
    onBack: () -> Unit,
    viewModel: RequestMoneyViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val ussdState by viewModel.ussdState.collectAsState()
    val launchWithPermission = rememberUssdPermissionLauncher { viewModel.initiateRequest() }

    Scaffold(
        topBar = { AppTopBar(title = "Request Money", onBack = onBack) }
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
                        payeeName = s.verifiedPayeeName ?: state.recipientId,
                        amount = UssdManager.lastSubmittedAmount,
                        referenceId = s.referenceId,
                        onDone = { viewModel.reset(); onBack() },
                        title = "Request Successful!"
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
                        unrecognizedResponse = f.unrecognizedResponse,
                        onRetry = { viewModel.reset() }
                    )
                }
            }

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
                        "Enter a mobile number or UPI ID to request payment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = state.recipientId,
                        onValueChange = viewModel::updateRecipientId,
                        label = { Text("Mobile Number or UPI ID") },
                        placeholder = { Text("Enter number or user@bank") },
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
                        onClick = { launchWithPermission() },
                        enabled = state.recipientId.isNotEmpty()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
