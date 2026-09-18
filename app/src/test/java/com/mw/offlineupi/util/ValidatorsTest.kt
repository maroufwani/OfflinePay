package com.mw.offlineupi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ValidatorsTest {

    // ---- Phone numbers ---------------------------------------------------------------------

    @Test
    fun `indian mobile numbers are accepted`() {
        assertTrue(Validators.isValidPhoneNumber("9876543210"))
        assertTrue(Validators.isValidPhoneNumber("6000000000"))
        assertTrue(Validators.isValidPhoneNumber(" 9876543210 "))
    }

    @Test
    fun `numbers outside the indian mobile range are rejected`() {
        assertFalse(Validators.isValidPhoneNumber("1234567890"))
        assertFalse(Validators.isValidPhoneNumber("5876543210"))
        assertFalse(Validators.isValidPhoneNumber("987654321"))
        assertFalse(Validators.isValidPhoneNumber("98765432101"))
        assertFalse(Validators.isValidPhoneNumber("+919876543210"))
        assertFalse(Validators.isValidPhoneNumber(""))
    }

    // ---- UPI IDs ---------------------------------------------------------------------------

    @Test
    fun `live NPCI handles are accepted`() {
        // Handles with digits are the regression: @paytm4 and @axl are real, and the previous
        // letters-only handle pattern rejected every one of them.
        assertTrue(Validators.isValidUpiId("john@ybl"))
        assertTrue(Validators.isValidUpiId("john.doe@paytm4"))
        assertTrue(Validators.isValidUpiId("9876543210@axl"))
        assertTrue(Validators.isValidUpiId("john_doe-1@icici.bank"))
        assertTrue(Validators.isValidUpiId(" john@oksbi "))
    }

    @Test
    fun `malformed UPI IDs are rejected`() {
        assertFalse(Validators.isValidUpiId("john"))
        assertFalse(Validators.isValidUpiId("@ybl"))
        assertFalse(Validators.isValidUpiId("john@"))
        assertFalse(Validators.isValidUpiId("j@ybl"))          // local part under 2 chars
        assertFalse(Validators.isValidUpiId("john@1bl"))       // handle must start with a letter
        assertFalse(Validators.isValidUpiId("john@ybl@extra"))
        assertFalse(Validators.isValidUpiId("john doe@ybl"))
        assertFalse(Validators.isValidUpiId("a".repeat(65) + "@ybl"))
    }

    // ---- Amounts ---------------------------------------------------------------------------

    @Test
    fun `amounts within the USSD rail limits are valid`() {
        assertTrue(Validators.isValidAmount("1"))
        assertTrue(Validators.isValidAmount("1.00"))
        assertTrue(Validators.isValidAmount("100.5"))
        assertTrue(Validators.isValidAmount("5000"))
        assertTrue(Validators.isValidAmount("4999.99"))
    }

    @Test
    fun `the NPCI per-transaction ceiling is five thousand`() {
        // The old bound was the app-level UPI limit of 1,00,000, which does not apply on *99#:
        // amounts in between were accepted here and declined by the bank after PIN entry.
        assertEquals(5_000.0, Validators.MAX_USSD_AMOUNT, 0.0)
        assertNull(Validators.amountError("5000"))
        assertNotNull(Validators.amountError("5000.01"))
        assertNotNull(Validators.amountError("100000"))
    }

    @Test
    fun `amountError names the actual problem`() {
        assertEquals("Enter an amount", Validators.amountError(""))
        assertEquals("Enter an amount", Validators.amountError("   "))
        assertEquals(
            "Enter a valid amount (up to 2 decimal places)",
            Validators.amountError("10.999")
        )
        assertEquals(
            "Enter a valid amount (up to 2 decimal places)",
            Validators.amountError("abc")
        )
        assertEquals("Minimum amount is ₹1", Validators.amountError("0"))
        assertEquals("Minimum amount is ₹1", Validators.amountError("0.50"))
        assertEquals(
            "Maximum for offline UPI is ₹5000 per transaction",
            Validators.amountError("5001")
        )
        assertNull(Validators.amountError("250.75"))
    }

    @Test
    fun `negative and signed amounts are rejected`() {
        assertNotNull(Validators.amountError("-100"))
        assertNotNull(Validators.amountError("+100"))
    }

    @Test
    fun `isValidAmountFormat checks shape without a range bound`() {
        // Used for the informational `am` field of a scanned QR, which the user still confirms.
        assertTrue(Validators.isValidAmountFormat("999999"))
        assertTrue(Validators.isValidAmountFormat("1.5"))
        assertFalse(Validators.isValidAmountFormat("1.555"))
        assertFalse(Validators.isValidAmountFormat("12345678"))
        assertFalse(Validators.isValidAmountFormat(""))
    }

    // ---- Masking ---------------------------------------------------------------------------

    @Test
    fun `phone masking leaves only the last two digits`() {
        // The old mask showed 6 of 10 digits, which barely narrowed anything given how small the
        // operator-assigned prefix space is.
        assertEquals("••••••••10", Validators.maskPhoneNumber("9876543210"))
        // Non-digits are stripped first, so a formatted number masks by its digit count.
        assertEquals("••••••••••10", Validators.maskPhoneNumber("+91 98765 43210"))
    }

    @Test
    fun `short inputs are returned unmasked rather than mangled`() {
        assertEquals("123", Validators.maskPhoneNumber("123"))
    }

    @Test
    fun `account masking keeps the last four`() {
        assertEquals("****3210", Validators.maskAccountNumber("9876543210"))
        assertEquals("****", Validators.maskAccountNumber("12"))
    }

    // ---- Amount formatting -----------------------------------------------------------------

    @Test
    fun `amounts use indian grouping and ascii digits regardless of device locale`() {
        // Two regressions in one. First: "%.2f".format() followed the default locale, so a Hindi
        // (India) device produced Devanagari digits inside strings that go down the USSD channel,
        // and it grouped nothing at all so five figures read as ₹123456.00 in an Indian payments
        // app. Second: the NumberFormat(en-IN) fix for that is itself platform-dependent — the
        // JDK's locale data groups en-IN in threes, so this test failed on ₹1,23,456.00 and the
        // grouping is now computed rather than looked up.
        val locales = listOf("hi-IN", "en-IN", "en-US", "de-DE", "ar-EG")
        val original = Locale.getDefault()
        try {
            for (tag in locales) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "₹1,23,456.00", Validators.formatAmount(123456.0))
                assertEquals(tag, "₹100.00", Validators.formatAmount(100.0))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `grouping follows the lakh-crore convention`() {
        assertEquals("₹0.00", Validators.formatAmount(0.0))
        assertEquals("₹1.00", Validators.formatAmount(1.0))
        assertEquals("₹999.00", Validators.formatAmount(999.0))
        assertEquals("₹1,000.00", Validators.formatAmount(1000.0))
        assertEquals("₹99,999.00", Validators.formatAmount(99999.0))
        assertEquals("₹1,00,000.00", Validators.formatAmount(100000.0))
        assertEquals("₹10,00,000.00", Validators.formatAmount(1000000.0))
        assertEquals("₹1,00,00,000.00", Validators.formatAmount(10000000.0))
        assertEquals("₹12,34,56,789.00", Validators.formatAmount(123456789.0))
    }

    @Test
    fun `amounts always carry two decimal places`() {
        assertEquals("₹100.00", Validators.formatAmount(100.0))
        assertEquals("₹100.50", Validators.formatAmount(100.5))
        assertEquals("₹100.05", Validators.formatAmount(100.05))
        assertEquals("₹1,000.00", Validators.formatAmount(1000.0))
    }

    @Test
    fun `paise are rounded before the rupees are grouped`() {
        // 99,999.999 must read as ₹1,00,000.00, not ₹99,999.100 or ₹99,999.00.
        assertEquals("₹1,00,000.00", Validators.formatAmount(99999.999))
        assertEquals("₹100.00", Validators.formatAmount(99.999))
        assertEquals("₹0.01", Validators.formatAmount(0.005))
    }

    @Test
    fun `a negative amount keeps its sign outside the symbol`() {
        assertEquals("-₹100.00", Validators.formatAmount(-100.0))
    }
}
