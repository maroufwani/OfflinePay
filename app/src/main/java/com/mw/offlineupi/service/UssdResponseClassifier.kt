package com.mw.offlineupi.service

/**
 * Reads meaning out of the text a bank's USSD menu puts on screen.
 *
 * These functions decide whether a payment succeeded, failed, was declined for a wrong PIN, or is
 * asking for one — which makes them the highest-consequence logic in the app and the only part of
 * the USSD path that is pure. They lived on [UssdManager], which cannot be constructed off-device:
 * its initialisers touch `Looper.getMainLooper()` and `Dispatchers.Main`, so any JVM test that so
 * much as named a classifier died in the object's static init. Sitting here they are testable
 * against captured real response strings (see `UssdResponseClassifierTest`), which is the whole
 * point — a misread here does not throw, it records a failed payment as SUCCESS.
 *
 * Nothing in this file may take a `Context`, log, or hold state. [UssdManager] keeps thin
 * delegating wrappers so its own call sites read as they did before.
 */
internal object UssdResponseClassifier {

    /**
     * Failure signals, word-boundary anchored. This runs *before* the success check on every
     * path, so it must cover the common NPCI decline strings — notably "unsuccessful",
     * "not successful" and "insufficient balance", none of which the previous flat
     * `contains` list matched.
     */
    private val ERROR_PATTERNS = listOf(
        Regex("\\berror\\b"),
        Regex("\\bfail(ed|ure|s)?\\b"),
        Regex("\\bun\\s*success(ful)?\\b"),
        Regex("\\bnot\\s+success(ful)?\\b"),
        Regex("\\bno[nt]\\s*-?\\s*success"),
        Regex("\\binvalid\\b"),
        Regex("\\bunable\\s+to\\s+process\\b"),
        Regex("\\bcould\\s+not\\s+be\\s+(processed|completed)\\b"),
        Regex("\\btry\\s+again\\b"),
        Regex("\\bservice\\s+(unavailable|not\\s+available)\\b"),
        Regex("\\bnot\\s+registered\\b"),
        Regex("\\bdeclin(e|ed)\\b"),
        Regex("\\breject(ed)?\\b"),
        Regex("\\binsufficient\\b"),
        Regex("\\blow\\s+balance\\b"),
        Regex("\\bexceed(s|ed)?\\b.{0,20}\\blimit\\b"),
        Regex("\\blimit\\b.{0,20}\\bexceed(s|ed)?\\b"),
        Regex("\\bconnection\\s+problem\\b"),
        Regex("\\btimed?\\s*out\\b"),
        Regex("\\bexpired\\b"),
        Regex("\\breversed\\b")
    )

    internal fun isErrorResponse(text: String): Boolean {
        val lower = text.lowercase()
        // Don't treat wrong PIN as a generic error — it has its own handler
        if (isWrongPinResponse(text)) return false
        return ERROR_PATTERNS.any { it.containsMatchIn(lower) }
    }

    internal fun isWrongPinResponse(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("incorrect pin") ||
            lower.contains("wrong pin") ||
            lower.contains("invalid pin") ||
            lower.contains("incorrect upi pin") ||
            lower.contains("wrong upi pin") ||
            lower.contains("pin is incorrect") ||
            lower.contains("pin is wrong") ||
            (lower.contains("incorrect") && lower.contains("pin"))
    }

    /**
     * Negations that must veto a success match. `"unsuccessful".contains("successful")` is true,
     * so a plain substring scan reads a decline as a success. These are checked first and
     * unconditionally return "not a success".
     */
    private val NEGATED_SUCCESS_PATTERNS = listOf(
        Regex("\\bun\\s*success(ful)?\\b"),
        Regex("\\bnot\\s+success(ful)?\\b"),
        Regex("\\bwas\\s+not\\s+"),
        Regex("\\bcould\\s+not\\b"),
        Regex("\\bcannot\\b"),
        Regex("\\bnot\\s+completed\\b"),
        Regex("\\bnot\\s+processed\\b"),
        Regex("\\bno[nt]\\s*-?\\s*success"),
        Regex("\\bfail(ed|ure)?\\b"),
        Regex("\\bdeclin(e|ed)\\b"),
        Regex("\\breject(ed)?\\b"),
        Regex("\\binsufficient\\b"),
        Regex("\\breversed\\b"),
        Regex("\\brefunded\\b")
    )

    /**
     * Word-boundary anchored positive success signals. Word boundaries matter: `contains`
     * matches these inside longer negative words, boundaries do not.
     */
    private val SUCCESS_PATTERNS = listOf(
        Regex("\\bsuccess(ful|fully)?\\b"),
        Regex("\\bcompleted\\b"),
        Regex("\\btxn\\s*id\\b"),
        Regex("\\btransaction\\s*id\\b"),
        Regex("\\bref(erence)?\\s*(no|id)\\b"),
        Regex("\\bhas\\s+been\\s+sent\\b"),
        Regex("\\bhas\\s+been\\s+credited\\b"),
        Regex("\\bhas\\s+been\\s+debited\\b"),
        Regex("\\bhas\\s+been\\s+transferred\\b")
    )

    /**
     * True only when the text carries an affirmative completion signal and no negation.
     *
     * A balance response counts as success **only** for [UssdCommandType.CHECK_BALANCE] —
     * "Insufficient balance. Available balance Rs 100" is a decline for a payment, and
     * treating balance text as a global success signal recorded failed payments as SUCCESS.
     */
    internal fun isSuccessResponse(text: String, commandType: UssdCommandType?): Boolean {
        val lower = text.lowercase()

        // A negation anywhere in the text vetoes success outright.
        if (NEGATED_SUCCESS_PATTERNS.any { it.containsMatchIn(lower) }) return false

        if (SUCCESS_PATTERNS.any { it.containsMatchIn(lower) }) return true

        // Balance text is a completion signal for a balance enquiry only.
        return commandType == UssdCommandType.CHECK_BALANCE && isBalanceResponse(text)
    }

    internal fun isBalanceResponse(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("balance") &&
            (Regex("\\brs\\b").containsMatchIn(lower) || lower.contains("inr") || lower.contains("₹"))) ||
            lower.contains("your account balance") ||
            lower.contains("available balance") ||
            lower.contains("a/c bal")
    }

    internal fun isPinPrompt(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("enter pin") ||
            lower.contains("upi pin") ||
            lower.contains("mpin") ||
            lower.contains("enter your pin") ||
            lower.contains("transaction pin") ||
            lower.contains("enter upi pin")
    }

    /**
     * Detect USSD session expiry responses like "error-code\n1" or "error code 1".
     * These appear when the USSD menu times out (~60s of inactivity).
     */
    internal fun isSessionExpiredResponse(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("error-code") || lower.startsWith("error code")
    }

    internal fun extractReferenceId(text: String): String? {
        val patterns = listOf(
            Regex("(?i)txn\\s*id[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)ref\\s*id[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)ref[erence]*\\s*no[:\\s]*([A-Za-z0-9]+)"),
            Regex("(?i)reference[:\\s]*([A-Za-z0-9]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
