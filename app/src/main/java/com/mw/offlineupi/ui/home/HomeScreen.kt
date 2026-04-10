package com.mw.offlineupi.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.ui.navigation.Screen
import com.mw.offlineupi.ui.theme.Primary
import com.mw.offlineupi.ui.theme.Success
import com.mw.offlineupi.util.Validators
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val recentTransactions by viewModel.recentTransactions.collectAsState(initial = emptyList())
    val userProfile by viewModel.userProfile.collectAsState(initial = null)
    val context = LocalContext.current
    val isAccessibilityEnabled = viewModel.isAccessibilityEnabled()

    val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )
    var hasPermissions by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (userProfile?.name?.isNotEmpty() == true) userProfile!!.name else "Offline Pay",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (userProfile?.upiId?.isNotEmpty() == true) userProfile!!.upiId else "Pay without internet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { onNavigate(Screen.TransactionHistory.route) }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Transaction History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Scan & Pay Hero Card ──
            item {
                Card(
                    onClick = { onNavigate(Screen.ScanPay.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A2E),
                                        Color(0xFF16213E),
                                        Color(0xFF0F3460)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Scanner frame corners
                        val cornerColor = Primary.copy(alpha = 0.7f)
                        val cornerSize = 40.dp
                        val cornerWidth = 3.dp

                        // Top-left corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(cornerSize)
                                    .height(cornerWidth)
                                    .background(cornerColor)
                            )
                            Box(
                                modifier = Modifier
                                    .width(cornerWidth)
                                    .height(cornerSize)
                                    .background(cornerColor)
                            )
                        }
                        // Top-right corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .width(cornerSize)
                                    .height(cornerWidth)
                                    .background(cornerColor)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .width(cornerWidth)
                                    .height(cornerSize)
                                    .background(cornerColor)
                            )
                        }
                        // Bottom-left corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .width(cornerSize)
                                    .height(cornerWidth)
                                    .background(cornerColor)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .width(cornerWidth)
                                    .height(cornerSize)
                                    .background(cornerColor)
                            )
                        }
                        // Bottom-right corner
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .width(cornerSize)
                                    .height(cornerWidth)
                                    .background(cornerColor)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .width(cornerWidth)
                                    .height(cornerSize)
                                    .background(cornerColor)
                            )
                        }

                        // Center QR icon + label
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan & Pay",
                                modifier = Modifier.size(52.dp),
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Tap to Scan & Pay",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // ── Warnings ──
            if (!isAccessibilityEnabled) {
                item {
                    WarningBanner(
                        title = "Accessibility Service Disabled",
                        subtitle = "Tap to enable — required for USSD payments",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }
                    )
                }
            }

            if (!hasPermissions) {
                item {
                    WarningBanner(
                        title = "Permissions Required",
                        subtitle = "Tap to grant phone & call permissions",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { permissionLauncher.launch(requiredPermissions) }
                    )
                }
            }

            // ── Payment Options ──
            item {
                Text(
                    "Payment Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        icon = Icons.Default.Phone,
                        label = "To Mobile",
                        subtitle = "Pay via phone number",
                        iconBackgroundColor = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF1565C0),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.PayMobile.route) }
                    )
                    ActionCard(
                        icon = Icons.Default.AlternateEmail,
                        label = "UPI ID",
                        subtitle = "Pay via UPI address",
                        iconBackgroundColor = Color(0xFFF3E5F5),
                        iconColor = Color(0xFF7B1FA2),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.UpiId.route) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        icon = Icons.Default.AccountBalance,
                        label = "Balance",
                        subtitle = if (userProfile?.bankName?.isNotEmpty() == true)
                            userProfile!!.bankName else "Check balance",
                        iconBackgroundColor = Color(0xFFE8F5E9),
                        iconColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.BalanceCheck.route) }
                    )
                    ActionCard(
                        icon = Icons.Default.RequestPage,
                        label = "Request",
                        subtitle = "Request payment",
                        iconBackgroundColor = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFE65100),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.RequestMoney.route) }
                    )
                }
            }

            // ── Recent Transactions ──
            if (recentTransactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { onNavigate(Screen.TransactionHistory.route) }) {
                            Text("See All")
                        }
                    }
                }

                items(recentTransactions.size) { index ->
                    val transaction = recentTransactions[index]
                    TransactionItem(transaction, onClick = {
                        onNavigate(Screen.TransactionDetail.createRoute(transaction.id))
                    })
                }
            }
        }
    }
}

// ── Action Card ──
@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    iconBackgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Warning Banner ──
@Composable
private fun WarningBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Transaction Item ──
@Composable
private fun TransactionItem(transaction: TransactionEntity, onClick: () -> Unit = {}) {
    val isFailed = transaction.status == "FAILED"
    val isSend = transaction.type == "SEND"
    val initial = transaction.recipientName.firstOrNull()?.uppercase() ?: "?"
    val timeText = formatRelativeTime(transaction.timestamp)

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
            // Name & time
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

private fun formatRelativeTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val txnTime = Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(timestamp))

    return when {
        now.get(Calendar.YEAR) == txnTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == txnTime.get(Calendar.DAY_OF_YEAR) -> "Today, $time"
        now.get(Calendar.YEAR) == txnTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - txnTime.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday, $time"
        else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
