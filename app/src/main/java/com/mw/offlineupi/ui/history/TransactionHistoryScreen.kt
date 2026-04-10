package com.mw.offlineupi.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.ui.components.AppTopBar
import com.mw.offlineupi.ui.theme.Success
import com.mw.offlineupi.util.Validators
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: TransactionHistoryViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val transactions by viewModel.transactions.collectAsState(initial = emptyList())
    val grouped = groupTransactionsByDate(transactions)

    Scaffold(
        topBar = { AppTopBar(title = "Transaction History", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search by name or ID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (transactions.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "No results found" else "No transactions yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "Try a different search term" else "Your transactions will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (dateLabel, txns) ->
                        // Date group header
                        item(key = "header_$dateLabel") {
                            Text(
                                dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                            )
                        }
                        // Transaction items
                        items(txns, key = { it.id }) { transaction ->
                            HistoryItem(
                                transaction = transaction,
                                onClick = { onTransactionClick(transaction.id) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(transaction: TransactionEntity, onClick: () -> Unit = {}) {
    val isFailed = transaction.status == "FAILED"
    val isSend = transaction.type == "SEND"
    val initial = transaction.recipientName.firstOrNull()?.uppercase() ?: "?"
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeText = timeFormat.format(Date(transaction.timestamp))

    val avatarColors = listOf(
        Color(0xFF5C6BC0) to Color(0xFFE8EAF6),
        Color(0xFF26A69A) to Color(0xFFE0F2F1),
        Color(0xFFEF5350) to Color(0xFFFFEBEE),
        Color(0xFFFF7043) to Color(0xFFFBE9E7),
        Color(0xFF66BB6A) to Color(0xFFE8F5E9),
        Color(0xFFAB47BC) to Color(0xFFF3E5F5),
        Color(0xFF42A5F5) to Color(0xFFE3F2FD),
    )
    val colorIndex = (transaction.recipientName.hashCode().and(0x7FFFFFFF)) % avatarColors.size
    val (avatarFg, avatarBg) = avatarColors[colorIndex]

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initial
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFailed) MaterialTheme.colorScheme.error else avatarFg
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            // Name & details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.recipientName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                if (transaction.referenceId.isNotEmpty()) {
                    Text(
                        "Ref: ${transaction.referenceId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Amount & status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        isFailed -> Validators.formatAmount(transaction.amount)
                        isSend -> "- ${Validators.formatAmount(transaction.amount)}"
                        else -> "+ ${Validators.formatAmount(transaction.amount)}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isSend -> MaterialTheme.colorScheme.onSurface
                        else -> Success
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (isFailed) "Failed" else if (transaction.status == "SUCCESS") "Completed" else transaction.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = when (transaction.status) {
                        "SUCCESS" -> Success
                        "FAILED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun groupTransactionsByDate(transactions: List<TransactionEntity>): List<Pair<String, List<TransactionEntity>>> {
    val now = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    return transactions.groupBy { txn ->
        when {
            txn.timestamp >= todayStart -> "Today"
            txn.timestamp >= yesterdayStart -> "Yesterday"
            else -> dateFormat.format(Date(txn.timestamp))
        }
    }.toList()
}
