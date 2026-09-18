package com.mw.offlineupi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiQrParserTest {

    private fun success(raw: String): UpiPaymentInfo {
        val result = UpiQrParser.parseDetailed(raw)
        assertTrue("expected Success, got $result", result is QrParseResult.Success)
        return (result as QrParseResult.Success).info
    }

    private fun rejection(raw: String): String {
        val result = UpiQrParser.parseDetailed(raw)
        assertTrue("expected Rejected, got $result", result is QrParseResult.Rejected)
        return (result as QrParseResult.Rejected).reason
    }

    // ---- Well-formed codes -----------------------------------------------------------------

    @Test
    fun `a full merchant QR is parsed`() {
        val info = success(
            "upi://pay?pa=merchant@ybl&pn=Chai%20Point&am=45.50&cu=INR&tn=Order%20123&mc=5812&tr=TXN001"
        )
        assertEquals("merchant@ybl", info.payeeAddress)
        assertEquals("Chai Point", info.payeeName)
        assertEquals("45.50", info.amount)
        assertEquals("INR", info.currency)
        assertEquals("Order 123", info.transactionNote)
        assertEquals("5812", info.merchantCode)
        assertEquals("TXN001", info.referenceId)
    }

    @Test
    fun `a payee-only QR is parsed and leaves the amount open`() {
        val info = success("upi://pay?pa=john@oksbi")
        assertEquals("john@oksbi", info.payeeAddress)
        assertEquals("", info.amount)
        assertEquals("", info.payeeName)
        assertEquals("INR", info.currency)
    }

    @Test
    fun `scheme and host are matched case-insensitively`() {
        // RFC 3986 makes both case-insensitive and real generators do emit these; the previous
        // case-sensitive startsWith refused them as invalid QRs.
        assertEquals("john@ybl", success("UPI://PAY?pa=john@ybl").payeeAddress)
        assertEquals("john@ybl", success("Upi://Pay?pa=john@ybl").payeeAddress)
    }

    @Test
    fun `parameter keys are matched case-insensitively`() {
        val info = success("upi://pay?PA=john@ybl&PN=John&AM=10")
        assertEquals("john@ybl", info.payeeAddress)
        assertEquals("John", info.payeeName)
        assertEquals("10", info.amount)
    }

    @Test
    fun `surrounding whitespace from the scanner is tolerated`() {
        assertEquals("john@ybl", success("  upi://pay?pa=john@ybl  ").payeeAddress)
    }

    @Test
    fun `an empty valueless parameter does not break parsing`() {
        assertEquals("john@ybl", success("upi://pay?pa=john@ybl&am=&cu=&tn").payeeAddress)
    }

    // ---- Rejections, each with its own reason ----------------------------------------------

    @Test
    fun `a non-UPI code is rejected as such`() {
        assertEquals("Not a UPI QR code", rejection("https://example.com/pay?pa=john@ybl"))
        assertEquals("Not a UPI QR code", rejection("hello world"))
        assertEquals("Not a UPI QR code", rejection(""))
    }

    @Test
    fun `a UPI link with no query is rejected as having no details`() {
        assertEquals("UPI QR code has no payment details", rejection("upi://pay"))
    }

    @Test
    fun `a missing payee is rejected as such`() {
        assertEquals("UPI QR code has no payee address", rejection("upi://pay?am=100&cu=INR"))
        assertEquals("UPI QR code has no payee address", rejection("upi://pay?pa=&am=100"))
    }

    @Test
    fun `a malformed payee is named as the problem`() {
        // pa is the one field that goes straight into the bank's USSD dialog.
        assertEquals(
            "Payee address in this QR is not a valid UPI ID",
            rejection("upi://pay?pa=notaupiid&am=100")
        )
        assertEquals(
            "Payee address in this QR is not a valid UPI ID",
            rejection("upi://pay?pa=john%20doe@ybl")
        )
    }

    @Test
    fun `a foreign currency QR is refused rather than paid in rupees`() {
        // The silent failure this replaces: cu=USD for 50 would have been paid as ₹50.
        assertEquals(
            "This QR requests USD; offline UPI supports INR only",
            rejection("upi://pay?pa=john@ybl&am=50&cu=USD")
        )
    }

    @Test
    fun `a malformed percent escape is rejected as malformed`() {
        assertEquals("UPI QR code is malformed", rejection("upi://pay?pa=john@ybl&pn=%ZZ"))
    }

    // ---- The deliberate leniency on `am` ---------------------------------------------------

    @Test
    fun `a malformed amount is dropped rather than refusing the whole code`() {
        // The overlay re-validates before anything is sent, so a generator writing three decimal
        // places is not a reason to refuse an otherwise valid payee.
        val info = success("upi://pay?pa=john@ybl&am=10.999")
        assertEquals("john@ybl", info.payeeAddress)
        assertEquals("", info.amount)
    }

    @Test
    fun `an amount above the USSD ceiling is kept for the overlay to reject`() {
        // Deliberately not bounds-checked here: the user may legitimately pay less than a
        // merchant's suggested amount, and the payment path validates the figure that is sent.
        assertEquals("9000", success("upi://pay?pa=john@ybl&am=9000").amount)
    }

    // ---- The convenience wrapper -----------------------------------------------------------

    @Test
    fun `parse returns null for anything rejected`() {
        assertNull(UpiQrParser.parse("https://example.com"))
        assertNull(UpiQrParser.parse("upi://pay?pa=notaupiid"))
        assertNotNull(UpiQrParser.parse("upi://pay?pa=john@ybl"))
    }
}
