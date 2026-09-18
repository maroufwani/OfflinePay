package com.mw.offlineupi.util

/**
 * Pulls the rupee figure out of a bank's balance USSD response.
 *
 * This lives outside the UI because the value it produces is persisted: the app used to store the
 * bank's entire response as "the balance" and render it verbatim, which put account-number
 * fragments and the bank's own message text into encrypted prefs and onto the home screen. Only the
 * number is worth keeping.
 */
object BalanceParser {

    /**
     * Currency-prefixed forms, e.g. `Rs.1549.40`, `Rs 1,549.40`, `INR 1549.40`, `₹1549.40`.
     *
     * A trailing `/-` (common in Indian bank text) and thousands separators are tolerated. The
     * amount is required to start with a digit so `Rs.` followed by a word does not match.
     */
    private val PREFIXED_PATTERNS = listOf(
        Regex("(?i)\\bRs\\.?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)"),
        Regex("(?i)\\bINR\\.?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)"),
        Regex("₹\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)")
    )

    /** A value that is already just a number — what [extractAmount] itself stored last time. */
    private val BARE_AMOUNT = Regex("^\\d[\\d,]*(?:\\.\\d{1,2})?$")

    /**
     * Returns the balance as a plain number string (`"1549.40"`), or null if [rawMessage] carries
     * no recognisable amount.
     *
     * Accepting an already-bare number matters for values persisted by an earlier run: the flow
     * reads back what it wrote, and a stored `"1549.40"` has no `Rs.` left in front of it.
     */
    fun extractAmount(rawMessage: String): String? {
        val text = rawMessage.trim()
        if (text.isEmpty()) return null
        if (BARE_AMOUNT.matches(text)) return text.replace(",", "")
        for (pattern in PREFIXED_PATTERNS) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1].replace(",", "")
        }
        return null
    }
}
