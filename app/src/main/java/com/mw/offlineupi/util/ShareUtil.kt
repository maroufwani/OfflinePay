package com.mw.offlineupi.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ShareUtil"

/** Files in the share cache older than this are pruned on the next share. */
private const val SHARED_IMAGE_TTL_MS = 60 * 60 * 1000L

fun sharePaymentReceipt(
    context: Context,
    payeeName: String,
    amount: String,
    referenceId: String?
) {
    val dateTime = DateFormats.full(System.currentTimeMillis())
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

/**
 * Writes [bitmap] to the share cache and opens the system chooser for it.
 *
 * `suspend`, because PNG encoding a full-screen receipt is tens of milliseconds of CPU plus a file
 * write, and it used to run on the main thread — a visible jank right as the user taps Share. The
 * only caller is already inside a coroutine.
 *
 * The file name carries a timestamp instead of being a fixed `receipt.png`: the old name meant a
 * second share started while the first was still being read by the target app overwrote the bytes
 * underneath it. Old files are pruned by age here, since the receiving app reads the URI
 * asynchronously and the file cannot be deleted as soon as the chooser is shown.
 */
suspend fun shareReceiptScreenshot(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val imagesDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val now = System.currentTimeMillis()
        imagesDir.listFiles()?.forEach { stale ->
            if (now - stale.lastModified() > SHARED_IMAGE_TTL_MS) {
                if (!stale.delete()) Log.w(TAG, "Could not prune stale share file")
            }
        }
        val imageFile = File(imagesDir, "receipt_$now.png")
        imageFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Receipt"))
}

/**
 * Opens the system share sheet with a report of a USSD response the app could not classify.
 *
 * When a *99# session ends in text that matches neither a success nor a failure pattern, the app
 * records a failure — the safe default — and the string that caused it is gone. It is deliberately
 * not logged either: Log.w survives R8 into release builds, and bank replies carry balances and
 * account fragments. So the only way the pattern lists in UssdResponseClassifier can ever grow to
 * cover the long tail of bank wordings is for a user to hand one over on purpose. This is that
 * mechanism.
 *
 * Three choices here, none of them forced:
 *
 *  - No backend, no automatic upload. The text goes to the share sheet and the user picks the
 *    destination, which makes consent a deliberate act instead of a setting somebody has to find
 *    and switch off. It also means no analytics endpoint, no network traffic anywhere near the
 *    payment path, and no privacy policy for telemetry the app does not collect.
 *  - The report carries the app version, the Android version and which flow was running. It
 *    carries no device id, install id, phone number or UPI ID: the app attaches no identifier of
 *    any kind. That is the whole of the anonymisation within the app's power — whichever app the
 *    user shares into obviously knows who they are.
 *  - The response text goes in verbatim rather than scrubbed. A regex blanking long digit runs
 *    would take out the reference ids, amounts and balances the classifier keys on, which is the
 *    only reason to collect the string at all. Instead [com.mw.offlineupi.ui.components.FailureScreen]
 *    shows the exact text on screen before this is reachable, and the header below says plainly
 *    that it may contain account details and can be edited first. A user reading the real thing is
 *    stronger consent than a scrubber they would have to take on trust.
 */
fun shareUnrecognisedResponse(context: Context, response: String, flowLabel: String? = null) {
    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Own package missing while building report", e)
        "unknown"
    }
    val text = buildString {
        appendLine("Offline Pay could not read this UPI response")
        appendLine()
        appendLine(
            "The app could not tell whether this reply meant the payment went through or not, " +
                "so it recorded a failure. The text below is what the bank actually sent."
        )
        appendLine()
        appendLine(
            "Please read it before sending — bank replies can contain account digits and " +
                "balances, and you can edit or delete any part of this."
        )
        appendLine()
        appendLine("App version: $appVersion")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        if (!flowLabel.isNullOrBlank()) appendLine("Flow: $flowLabel")
        appendLine()
        appendLine("--- response text ---")
        appendLine(response)
        appendLine("--- end ---")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Offline Pay: unrecognised UPI response")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Report response"))
}
