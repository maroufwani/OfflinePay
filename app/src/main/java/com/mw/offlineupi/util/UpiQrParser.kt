package com.mw.offlineupi.util

data class UpiPaymentInfo(
    val payeeAddress: String = "",
    val payeeName: String = "",
    val amount: String = "",
    val transactionNote: String = "",
    val merchantCode: String = "",
    val currency: String = "INR",
    val referenceId: String = ""
)

/**
 * Outcome of reading a scanned QR.
 *
 * [Rejected] carries a reason so the scanner can say *why* a code was refused. Every failure used
 * to collapse into a single "Invalid UPI QR code", which is indistinguishable from a bad camera
 * read — the user's only option was to keep rescanning a QR that was never going to work.
 */
sealed interface QrParseResult {
    data class Success(val info: UpiPaymentInfo) : QrParseResult
    data class Rejected(val reason: String) : QrParseResult
}

object UpiQrParser {

    /** NPCI's UPI deep link. Compared case-insensitively — see [parseDetailed]. */
    private const val UPI_PAY_PREFIX = "upi://pay"

    /** Convenience wrapper for callers that do not need the rejection reason. */
    fun parse(rawData: String): UpiPaymentInfo? =
        (parseDetailed(rawData) as? QrParseResult.Success)?.info

    /**
     * Parses a `upi://pay?...` deep link.
     *
     * Notes on the validation, in the order things are checked:
     *  - **Scheme and host are matched case-insensitively.** RFC 3986 defines both as
     *    case-insensitive, and real-world generators do emit `UPI://PAY?...`; the previous
     *    case-sensitive `startsWith` rejected those as invalid QRs.
     *  - **`pa` must be a well-formed UPI ID.** It is the one field that goes straight into the
     *    USSD command, so a malformed one would otherwise be typed into the bank's dialog.
     *  - **`cu`, if present, must be INR.** A foreign-currency QR was previously read as rupees,
     *    silently: a `cu=USD` code for 50 would have been paid as ₹50.
     *  - **A malformed `am` is dropped, not fatal.** The amount is re-entered and validated in the
     *    payment overlay before anything is sent, so a generator that writes three decimal places
     *    is not a reason to refuse an otherwise valid payee. The value is deliberately *not*
     *    checked against [Validators.MAX_USSD_AMOUNT] here for the same reason — the user may
     *    legitimately pay less than a merchant's suggested amount.
     */
    fun parseDetailed(rawData: String): QrParseResult {
        val trimmed = rawData.trim()
        if (!trimmed.lowercase().startsWith(UPI_PAY_PREFIX)) {
            return QrParseResult.Rejected("Not a UPI QR code")
        }
        if (!trimmed.contains("?")) {
            return QrParseResult.Rejected("UPI QR code has no payment details")
        }

        val params = try {
            trimmed.substringAfter("?")
                .split("&")
                .filter { it.isNotBlank() }
                .associate { param ->
                    val parts = param.split("=", limit = 2)
                    val key = parts[0].lowercase()
                    val value = if (parts.size == 2) {
                        java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else {
                        ""
                    }
                    key to value
                }
        } catch (_: Exception) {
            // URLDecoder throws on a malformed %-escape, and split can hand it anything.
            return QrParseResult.Rejected("UPI QR code is malformed")
        }

        val payeeAddress = params["pa"]?.trim()
        if (payeeAddress.isNullOrEmpty()) {
            return QrParseResult.Rejected("UPI QR code has no payee address")
        }
        if (!Validators.isValidUpiId(payeeAddress)) {
            return QrParseResult.Rejected("Payee address in this QR is not a valid UPI ID")
        }

        val currency = params["cu"]?.trim()?.uppercase()?.ifEmpty { null } ?: "INR"
        if (currency != "INR") {
            return QrParseResult.Rejected("This QR requests $currency; offline UPI supports INR only")
        }

        val rawAmount = params["am"]?.trim().orEmpty()
        val amount = if (rawAmount.isEmpty() || Validators.isValidAmountFormat(rawAmount)) {
            rawAmount
        } else {
            ""
        }

        return QrParseResult.Success(
            UpiPaymentInfo(
                payeeAddress = payeeAddress,
                payeeName = params["pn"]?.trim() ?: "",
                amount = amount,
                transactionNote = params["tn"] ?: "",
                merchantCode = params["mc"] ?: "",
                currency = currency,
                referenceId = params["tr"] ?: ""
            )
        )
    }
}
