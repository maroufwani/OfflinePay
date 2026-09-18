package com.mw.offlineupi.util


object Validators {
    private val PHONE_REGEX = Regex("^[6-9]\\d{9}$")

    /**
     * UPI handle grammar.
     *
     * The handle (the part after `@`) may contain digits — `@paytm4`, `@axl`, `@ybl` are all live
     * NPCI handles — so restricting it to `[a-zA-Z]+` rejected valid IDs. Dots are allowed in the
     * handle for bank sub-handles (`@icici.bank`). The local part keeps its original character
     * set plus a length bound so a pathological input cannot be walked character by character.
     */
    private val UPI_ID_REGEX =
        Regex("^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z][a-zA-Z0-9.]{1,30}$")

    private val AMOUNT_REGEX = Regex("^\\d{1,7}(\\.\\d{1,2})?$")

    /**
     * Per-transaction ceiling for UPI 123PAY / `*99#`.
     *
     * NPCI caps a USSD UPI transfer at ₹5,000 — the previous ₹100,000 bound was the app-level UPI
     * limit, which does not apply on this rail, so amounts between the two were accepted here and
     * then declined by the bank *after* the user had already entered their PIN.
     */
    const val MAX_USSD_AMOUNT = 5_000.0

    /** Bank-side minimum. Below this the USSD flow errors out rather than transferring. */
    const val MIN_AMOUNT = 1.0

    fun isValidPhoneNumber(phone: String): Boolean = PHONE_REGEX.matches(phone.trim())

    fun isValidUpiId(upiId: String): Boolean = UPI_ID_REGEX.matches(upiId.trim())

    /**
     * Whether [amount] is a well-formed rupee amount within the USSD rail's limits.
     *
     * Accepts at most two decimal places (the `*99#` flow silently truncates a third) and rejects
     * anything outside [MIN_AMOUNT]..[MAX_USSD_AMOUNT].
     */
    fun isValidAmount(amount: String): Boolean = amountError(amount) == null

    /**
     * Shape-only amount check — digits with at most two decimal places, no range bound.
     *
     * For amounts that are informational rather than about to be sent, such as the `am` field of a
     * scanned QR that the user still confirms in the payment overlay. [amountError] is the check
     * that actually gates a transfer.
     */
    fun isValidAmountFormat(amount: String): Boolean = AMOUNT_REGEX.matches(amount.trim())

    /**
     * Same check as [isValidAmount] but returns a user-facing reason, or `null` when valid.
     * Used at the [com.mw.offlineupi.service.UssdManager] boundary so the failure the user sees
     * names the actual problem.
     */
    fun amountError(amount: String): String? {
        val trimmed = amount.trim()
        if (trimmed.isEmpty()) return "Enter an amount"
        if (!AMOUNT_REGEX.matches(trimmed)) return "Enter a valid amount (up to 2 decimal places)"
        val value = trimmed.toDoubleOrNull() ?: return "Enter a valid amount"
        if (value < MIN_AMOUNT) return "Minimum amount is ₹${MIN_AMOUNT.toInt()}"
        if (value > MAX_USSD_AMOUNT) {
            return "Maximum for offline UPI is ₹${MAX_USSD_AMOUNT.toInt()} per transaction"
        }
        return null
    }

    /**
     * Masks all but the last two digits.
     *
     * The previous form (`12****7890`) left 6 of 10 digits visible, and because Indian mobile
     * numbers have a small operator-assigned prefix space, the first two digits plus the last four
     * narrow a number down far more than a mask should.
     */
    fun maskPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 4) return phone
        return "•".repeat(digits.length - 2) + digits.takeLast(2)
    }

    fun maskAccountNumber(account: String): String {
        if (account.length < 4) return "****"
        return "****" + account.takeLast(4)
    }

    /**
     * Formats a rupee amount with Indian grouping and ASCII digits — `₹1,23,456.00`.
     *
     * `"%.2f".format(x)` used the default locale, so on a device set to Hindi (India) this produced
     * Devanagari digits (`₹१००.००`) inside strings that were then sent down the USSD channel or
     * compared against bank text. It also grouped nothing at all, so a five-figure amount read as
     * `₹123456.00` in an Indian payments app.
     *
     * The grouping is done by hand rather than through `NumberFormat`/`DecimalFormat`, and that is
     * deliberate:
     *  - `NumberFormat.getInstance(Locale("en-IN"))` does **not** reliably give lakh/crore
     *    grouping. It depends on the platform's locale data: Android's ICU gives `1,23,456` while
     *    the JDK's CLDR gives `123,456`. A unit test caught this, but on a device it would just
     *    have been quietly wrong.
     *  - `DecimalFormat("#,##,##0.00")` cannot express it either on the JVM, which honours only
     *    the last grouping interval in a pattern.
     *  - Neither is thread-safe, so a shared instance needed a lock.
     *
     * Doing the arithmetic here removes all three problems: the output is byte-identical on every
     * device, `Long.toString` is ASCII by definition, and there is no shared mutable state. The `₹`
     * is likewise prefixed by hand — `getCurrencyInstance` varies the symbol and its spacing across
     * ICU versions (`₹1,234.00` vs `₹ 1,234.00` vs `Rs.1,234.00`), and this string appears in
     * fixed-width UI and in text a user may read back to their bank.
     */
    fun formatAmount(amount: Double): String {
        // Round to paise first, so the integer part grouped below is the one actually displayed.
        val paise = Math.round(Math.abs(amount) * 100.0)
        val sign = if (amount < 0) "-" else ""
        val fraction = (paise % 100).toInt()
        return buildString {
            append(sign)
            append('₹')
            append(groupIndian(paise / 100))
            append('.')
            if (fraction < 10) append('0')
            append(fraction)
        }
    }

    /**
     * Groups an integer the Indian way: three digits, then twos — `1,00,00,000` for a crore.
     */
    private fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val head = digits.substring(0, digits.length - 3)
        val out = StringBuilder()
        var i = head.length
        while (i > 2) {
            out.insert(0, head.substring(i - 2, i)).insert(0, ',')
            i -= 2
        }
        if (i > 0) out.insert(0, head.substring(0, i))
        return out.append(',').append(digits.substring(digits.length - 3)).toString()
    }
}
