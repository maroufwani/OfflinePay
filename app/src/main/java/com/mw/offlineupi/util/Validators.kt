package com.mw.offlineupi.util

object Validators {
    private val PHONE_REGEX = Regex("^[6-9]\\d{9}$")
    private val UPI_ID_REGEX = Regex("^[a-zA-Z0-9.\\-_]+@[a-zA-Z]+$")
    private val AMOUNT_REGEX = Regex("^\\d+(\\.\\d{1,2})?$")

    fun isValidPhoneNumber(phone: String): Boolean = PHONE_REGEX.matches(phone.trim())

    fun isValidUpiId(upiId: String): Boolean = UPI_ID_REGEX.matches(upiId.trim())

    fun isValidAmount(amount: String): Boolean {
        if (!AMOUNT_REGEX.matches(amount.trim())) return false
        val value = amount.trim().toDoubleOrNull() ?: return false
        return value > 0 && value <= 100_000
    }

    fun maskPhoneNumber(phone: String): String {
        if (phone.length < 10) return phone
        return phone.take(2) + "****" + phone.takeLast(4)
    }

    fun maskAccountNumber(account: String): String {
        if (account.length < 4) return "****"
        return "****" + account.takeLast(4)
    }

    fun formatAmount(amount: Double): String = "₹%.2f".format(amount)
}
