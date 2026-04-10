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

object UpiQrParser {

    fun parse(rawData: String): UpiPaymentInfo? {
        if (!rawData.startsWith("upi://pay")) return null

        val params = try {
            val queryString = rawData.substringAfter("?")
            queryString.split("&").associate { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0].lowercase() to java.net.URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    parts[0].lowercase() to ""
                }
            }
        } catch (_: Exception) {
            return null
        }

        val payeeAddress = params["pa"] ?: return null

        return UpiPaymentInfo(
            payeeAddress = payeeAddress,
            payeeName = params["pn"] ?: "",
            amount = params["am"] ?: "",
            transactionNote = params["tn"] ?: "",
            merchantCode = params["mc"] ?: "",
            currency = params["cu"] ?: "INR",
            referenceId = params["tr"] ?: ""
        )
    }
}
