package com.mw.offlineupi.ui.scanpay

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mw.offlineupi.service.UssdManager
import com.mw.offlineupi.service.UssdState
import com.mw.offlineupi.ui.components.AppTopBar
import com.mw.offlineupi.ui.components.FailureScreen
import com.mw.offlineupi.ui.components.PrimaryButton
import com.mw.offlineupi.ui.components.SecondaryButton
import com.mw.offlineupi.ui.components.StatusMessage
import com.mw.offlineupi.ui.components.SuccessScreen
import com.mw.offlineupi.ui.components.UssdProgressIndicator
import com.mw.offlineupi.ui.components.rememberUssdPermissionLauncher
import com.mw.offlineupi.util.DiagnosticLog

@Composable
fun ScanPayScreen(
    onBack: () -> Unit,
    viewModel: ScanPayViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val ussdState by viewModel.ussdState.collectAsState()
    val launchWithPermission = rememberUssdPermissionLauncher { viewModel.initiatePayment() }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val image = InputImage.fromFilePath(context, it)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                if (value.startsWith("upi://")) {
                                    viewModel.onQrScanned(value)
                                }
                            }
                        }
                        if (barcodes.none { b -> b.rawValue?.startsWith("upi://") == true }) {
                            viewModel.onGalleryError("No UPI QR code found in image")
                        }
                    }
                    .addOnFailureListener {
                        viewModel.onGalleryError("Could not scan image")
                    }
            } catch (_: Exception) {
                viewModel.onGalleryError("Could not read image")
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Scan & Pay", onBack = onBack) }
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
                    val processing = ussdState as? UssdState.Processing
                    UssdProgressIndicator(
                        step = processing?.step ?: "Processing...",
                        progress = processing?.progress ?: 0f
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
                    val success = ussdState as UssdState.Success
                    val payeeName = success.verifiedPayeeName ?: state.paymentInfo?.payeeAddress ?: ""
                    SuccessScreen(
                        payeeName = payeeName,
                        amount = UssdManager.lastSubmittedAmount,
                        referenceId = success.referenceId,
                        onDone = {
                            viewModel.resetUssd()
                            onBack()
                        }
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
                    val failed = ussdState as UssdState.Failed
                    FailureScreen(
                        reason = failed.reason,
                        unrecognizedResponse = failed.unrecognizedResponse,
                        onRetry = { viewModel.rescan() }
                    )
                }
            }

            state.isScanning -> {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasCameraPermission = granted }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Point camera at a UPI QR code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        if (hasCameraPermission) {
                            var isTorchOn by remember { mutableStateOf(false) }
                            var cameraRef by remember { mutableStateOf<Camera?>(null) }
                            var cameraError by remember { mutableStateOf<String?>(null) }

                            QrScannerView(
                                onQrScanned = viewModel::onQrScanned,
                                onCameraBound = { cameraRef = it },
                                onCameraError = { cameraError = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            // A failed init leaves the preview a blank surface, so without this
                            // the screen just sits there looking broken with no way to report it.
                            cameraError?.let { err ->
                                Text(
                                    text = "Camera unavailable (" + err + "). " +
                                        "Enter the UPI ID manually, and send a report from " +
                                        "Settings > Diagnostics.",
                                    textAlign = TextAlign.Center,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(24.dp)
                                )
                            }

                            // Flashlight toggle button
                            IconButton(
                                onClick = {
                                    isTorchOn = !isTorchOn
                                    cameraRef?.cameraControl?.enableTorch(isTorchOn)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                                    contentDescription = if (isTorchOn) "Turn off flashlight" else "Turn on flashlight",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Camera permission required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Grant camera access to scan QR codes,\nor use gallery instead",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }
                    state.error?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusMessage(message = it, isError = true)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Pick from Gallery",
                        onClick = { galleryLauncher.launch("image/*") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            !state.isScanning && state.paymentInfo != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    state.paymentInfo?.let { info ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    info.payeeName.ifEmpty { "Unknown Merchant" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    info.payeeAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    PrimaryButton(
                        text = "Verify & Proceed",
                        onClick = { launchWithPermission() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Scan Again",
                        onClick = { viewModel.rescan() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScannerView(
    onQrScanned: (String) -> Unit,
    onCameraBound: (Camera) -> Unit = {},
    onCameraError: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                // Everything below runs on the main thread and every call in it can throw: get()
                // rethrows a CameraX initialisation failure, and BarcodeScanning.getClient()
                // resolves the bundled ML Kit model. Only bindToLifecycle used to be guarded, so
                // anything else throwing took the process down instead of leaving the user on a
                // screen they could back out of.
                //
                // The cause is recorded rather than swallowed. In a minified build this is the
                // only place the real class name survives, and it is what Settings -> Diagnostics
                // reports back.
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val scanner = BarcodeScanning.getClient()

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasScanned) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        if (barcode.valueType == Barcode.TYPE_TEXT ||
                                            barcode.valueType == Barcode.TYPE_URL
                                        ) {
                                            barcode.rawValue?.let { value ->
                                                if (value.startsWith("upi://") && !hasScanned) {
                                                    hasScanned = true
                                                    onQrScanned(value)
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraBound(camera)
                } catch (t: Throwable) {
                    Log.e("ScanPayScreen", "Camera initialisation failed", t)
                    DiagnosticLog.logThrowable("camera init FAILED", t)
                    onCameraError(t.javaClass.simpleName)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
