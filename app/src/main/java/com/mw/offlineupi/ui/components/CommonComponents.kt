package com.mw.offlineupi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mw.offlineupi.util.shareReceiptScreenshot
import com.mw.offlineupi.util.shareUnrecognisedResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Go back" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = text },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount"
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { value ->
            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                onAmountChange(value)
            }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        prefix = {
            Text(
                "₹ ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun ConfirmationCard(
    recipientName: String,
    recipientId: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                com.mw.offlineupi.ui.theme.PrimaryDark,
                                com.mw.offlineupi.ui.theme.Primary,
                                com.mw.offlineupi.ui.theme.PrimaryLight
                            )
                        )
                    )
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "₹ $amount",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Payment Summary",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                DetailRow("To", recipientName, isBold = true)
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow("UPI ID / Mobile", recipientId)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatusMessage(
    message: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val icon = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle
    val containerColor = if (isError)
        MaterialTheme.colorScheme.errorContainer
    else
        com.mw.offlineupi.ui.theme.SuccessContainer
    val contentColor = if (isError)
        MaterialTheme.colorScheme.error
    else
        com.mw.offlineupi.ui.theme.Success

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun UssdProgressIndicator(
    step: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Processing Payment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Please do not close the app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SuccessScreen(
    payeeName: String = "",
    amount: String = "",
    referenceId: String? = null,
    onDone: () -> Unit,
    onShare: (() -> Unit)? = null,
    title: String = "Payment Successful!",
    dateTime: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()

    val displayDateTime = remember(dateTime) {
        dateTime ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
    }

    // Checkmark animations
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }

    // Staggered content visibility
    var showTitle by remember { mutableStateOf(false) }
    var showAmount by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        circleProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        checkProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        showTitle = true
        delay(150)
        showAmount = true
        delay(150)
        showDetails = true
        delay(200)
        showButtons = true
    }

    // Pulse ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val successColor = com.mw.offlineupi.ui.theme.Success

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Receipt area (captured for screenshot — no buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated checkmark
            Box(contentAlignment = Alignment.Center) {
                // Pulse ring (only after circle completes)
                if (circleProgress.value >= 1f) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(successColor.copy(alpha = pulseAlpha))
                    )
                }

                Canvas(modifier = Modifier.size(100.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    val radius = (size.minDimension / 2) - strokeWidth

                    // Green filled circle
                    drawCircle(
                        color = successColor,
                        radius = radius * circleProgress.value,
                        alpha = circleProgress.value
                    )

                    // White checkmark path
                    if (checkProgress.value > 0f) {
                        val cx = center.x
                        val cy = center.y
                        val startX = cx - radius * 0.30f
                        val startY = cy + radius * 0.05f
                        val midX = cx - radius * 0.05f
                        val midY = cy + radius * 0.30f
                        val endX = cx + radius * 0.35f
                        val endY = cy - radius * 0.20f

                        val path = Path()
                        val cp = checkProgress.value
                        val half = 0.5f

                        path.moveTo(startX, startY)
                        if (cp <= half) {
                            val t = cp / half
                            path.lineTo(
                                startX + (midX - startX) * t,
                                startY + (midY - startY) * t
                            )
                        } else {
                            path.lineTo(midX, midY)
                            val t = (cp - half) / (1f - half)
                            path.lineTo(
                                midX + (endX - midX) * t,
                                midY + (endY - midY) * t
                            )
                        }

                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(
                                width = 4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = successColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Amount + payee
            AnimatedVisibility(
                visible = showAmount,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (amount.isNotBlank()) {
                        Text(
                            "₹$amount",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (payeeName.isNotBlank()) {
                        Text(
                            "to",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            payeeName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Details card
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Date & Time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                displayDateTime,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!referenceId.isNullOrBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Reference ID",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        referenceId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                var copied by remember { mutableStateOf(false) }
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(referenceId))
                                    copied = true
                                }) {
                                    Text(if (copied) "Copied!" else "Copy")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Branding for screenshot
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(tween(300))
            ) {
                Text(
                    "Offline Pay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // Buttons (outside screenshot area)
        AnimatedVisibility(
            visible = showButtons,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            shareReceiptScreenshot(context, bitmap)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Receipt")
                }
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(text = "Done", onClick = onDone)
            }
        }
    }
}

@Composable
fun FailureScreen(
    reason: String,
    onRetry: () -> Unit,
    title: String = "Payment Failed",
    unrecognizedResponse: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Failed",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!unrecognizedResponse.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            UnrecognisedResponseCard(response = unrecognizedResponse, flowLabel = title)
        }

        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(text = "Try Again", onClick = onRetry)
    }
}

/**
 * Shown when a payment failed only because the app could not read the bank's reply.
 *
 * Treating an unreadable reply as a failure is the safe default and stays that way — but it is also
 * the one failure the app could plausibly have got right, and the reply that caused it is
 * deliberately never written to the log (see UssdManager.handleFinalResponse). Offering it to the
 * user here is what turns the long tail of bank wordings from unfixable into fixable.
 *
 * The text is shown in full above the button rather than hidden behind it. The user is being asked
 * to pass on bank text that may name their balance, so what they consent to should be the actual
 * string in front of them, not a promise about it. Nothing is uploaded from this screen; see
 * [com.mw.offlineupi.util.shareUnrecognisedResponse] for why there is no backend.
 */
@Composable
private fun UnrecognisedResponseCard(response: String, flowLabel: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "This reply was not recognised",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your bank sent wording the app has not seen before, so it could not tell whether " +
                    "the payment went through. Check with your bank before trying again. Sharing " +
                    "the reply below is what gets it recognised next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    response,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Nothing is sent automatically. You pick where it goes and can edit it first.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = { shareUnrecognisedResponse(context, response, flowLabel) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report this response")
            }
        }
    }
}

/**
 * In-app PIN entry, used on the balance-check screen and as the fallback when the accessibility
 * overlay cannot be shown.
 *
 * The PIN is handed to [onSubmitPin] as a `CharArray` so the receiver can wipe it. The backing
 * field is unavoidably a `String`: `OutlinedTextField`'s value type is `String`, and Compose keeps
 * its own copies inside the text-field state and the IME's history. This is a known, accepted
 * limitation — the primary PIN surface is [com.mw.offlineupi.service.OverlayManager]'s custom
 * numpad, which never allocates a `String` at all. The field state is cleared on dispose, and the
 * state is deliberately not `rememberSaveable` so the PIN never reaches saved instance state.
 */
@Composable
fun PinEntryCard(
    message: String,
    onSubmitPin: (CharArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    wrongAttempts: Int = 0,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { pin = "" } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Enter UPI PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Authorize this transaction",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (wrongAttempts > 0 && errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = com.mw.offlineupi.ui.theme.WarningContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = com.mw.offlineupi.ui.theme.Warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.labelLarge,
                                color = com.mw.offlineupi.ui.theme.Warning
                            )
                            val remaining = 3 - wrongAttempts
                            Text(
                                if (remaining == 1)
                                    "LAST ATTEMPT! UPI will be suspended for 24 hours after 3 wrong attempts."
                                else
                                    "$remaining attempts left. 3 wrong attempts = 24hr suspension.",
                                style = MaterialTheme.typography.bodySmall,
                                color = com.mw.offlineupi.ui.theme.Warning.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isDigit() }) {
                        pin = value
                    }
                },
                label = { Text("UPI PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Confirm & Pay",
                onClick = {
                    val chars = pin.toCharArray()
                    pin = ""
                    onSubmitPin(chars)
                },
                enabled = pin.length in 4..6
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onCancel) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
