package com.mw.offlineupi.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun sharePaymentReceipt(
    context: Context,
    payeeName: String,
    amount: String,
    referenceId: String?
) {
    val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
    val text = buildString {
        appendLine("✅ Payment Successful!")
        appendLine()
        if (amount.isNotBlank()) appendLine("Amount: ₹$amount")
        if (payeeName.isNotBlank()) appendLine("To: $payeeName")
        if (!referenceId.isNullOrBlank()) appendLine("Reference ID: $referenceId")
        appendLine("Date: $dateTime")
        appendLine()
        appendLine("— Offline Pay")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Receipt"))
}

fun shareReceiptScreenshot(context: Context, bitmap: Bitmap) {
    val imagesDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val imageFile = File(imagesDir, "receipt.png")
    imageFile.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Receipt"))
}
