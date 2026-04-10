package com.mw.offlineupi.ui.paymobile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
fun PayMobileScreen(
    onBack: () -> Unit,
    viewModel: PayMobileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val ussdState by viewModel.ussdState.collectAsState()
    val recentRecipients by viewModel.recentRecipients.collectAsState(initial = emptyList())
    val verifyWithPermission = rememberUssdPermissionLauncher { viewModel.verifyRecipient() }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Pay to Mobile",
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            ussdState is UssdState.Processing || ussdState is UssdState.Dialing
                    || ussdState is UssdState.WaitingForInput
                    || ussdState is UssdState.WaitingForPin -> {
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
                        payeeName = s.verifiedPayeeName ?: state.phoneNumber,
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

            else -> {
                Column(modifier = Modifier.padding(padding)) {
                    PhoneInputStep(
                        state = state,
                        recentRecipients = recentRecipients,
                        onQueryChange = viewModel::updateContactQuery,
                        onSelectContact = viewModel::selectContact,
                        onSelectRecipient = viewModel::selectRecipient,
                        onVerify = { verifyWithPermission() },
                        onRequestPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneInputStep(
    state: PayMobileState,
    recentRecipients: List<com.mw.offlineupi.data.local.entity.RecipientEntity>,
    onQueryChange: (String) -> Unit,
    onSelectContact: (ContactResult) -> Unit,
    onSelectRecipient: (com.mw.offlineupi.data.local.entity.RecipientEntity) -> Unit,
    onVerify: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter mobile number or search contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = state.contactQuery,
                onValueChange = onQueryChange,
                label = { Text("Name or mobile number") },
                placeholder = { Text("Search contacts or enter number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (!state.hasContactsPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Allow contacts to search by name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRequestPermission) {
                            Text("Allow")
                        }
                    }
                }
            }

            // Show phone number preview when searching by name
            if (state.phoneNumber.isNotEmpty() && state.contactQuery != state.phoneNumber
                && !state.contactQuery.all { it.isDigit() || it.isWhitespace() }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "+91 ${state.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (state.contactResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Contacts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn {
                    items(state.contactResults) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = { onSelectContact(contact) }
                        )
                    }
                }
            } else {
                if (recentRecipients.isNotEmpty() && state.contactQuery.isEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentRecipients) { recipient ->
                            SuggestionChip(
                                onClick = { onSelectRecipient(recipient) },
                                label = {
                                    Text(recipient.name.ifEmpty { recipient.phoneNumber })
                                }
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                StatusMessage(message = it, isError = true)
            }
        }

        // Bottom button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            PrimaryButton(
                text = "Verify & Proceed",
                onClick = onVerify,
                enabled = state.phoneNumber.length == 10
            )
        }
    }
}

@Composable
private fun ContactRow(contact: ContactResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "+91 ${contact.phoneNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
