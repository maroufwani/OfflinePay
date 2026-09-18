package com.mw.offlineupi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parsed value is what gets persisted and shown as "Last Known Balance", so a miss here means
 * the app either stores the bank's whole reply (account-number fragments included) or shows nothing.
 */
class BalanceParserTest {

    @Test
    fun `currency-prefixed amounts are extracted`() {
        assertEquals("1549.40", BalanceParser.extractAmount("Your A/c bal is Rs.1549.40"))
        assertEquals("1549.40", BalanceParser.extractAmount("Available balance Rs 1,549.40"))
        assertEquals("1549.40", BalanceParser.extractAmount("Balance: INR 1549.40"))
        assertEquals("1549.40", BalanceParser.extractAmount("Balance ₹1549.40"))
        assertEquals("1549.40", BalanceParser.extractAmount("Bal ₹ 1,549.40 as on 07-Sep"))
    }

    @Test
    fun `thousands separators are dropped so the value parses as a number`() {
        assertEquals("123456.78", BalanceParser.extractAmount("Rs.1,23,456.78"))
    }

    @Test
    fun `whole rupee amounts need no decimal part`() {
        assertEquals("1549", BalanceParser.extractAmount("A/c Bal Rs 1549"))
    }

    @Test
    fun `an already bare number is accepted`() {
        // Matters because the flow reads back what it wrote: a stored "1549.40" has no Rs. left
        // in front of it, and rejecting it would blank the cached balance on every restart.
        assertEquals("1549.40", BalanceParser.extractAmount("1549.40"))
        assertEquals("1549", BalanceParser.extractAmount(" 1549 "))
        assertEquals("123456", BalanceParser.extractAmount("1,23,456"))
    }

    @Test
    fun `the amount must start with a digit`() {
        assertNull(BalanceParser.extractAmount("Rs. unavailable"))
        assertNull(BalanceParser.extractAmount("INR pending"))
    }

    @Test
    fun `text with no amount returns null`() {
        assertNull(BalanceParser.extractAmount("Balance enquiry failed. Try again."))
        assertNull(BalanceParser.extractAmount(""))
        assertNull(BalanceParser.extractAmount("   "))
    }

    @Test
    fun `an account number is not mistaken for the balance`() {
        // The account fragment comes first in the string; the currency prefix is what anchors the
        // match, so the balance is what comes back.
        assertEquals("1549.40", BalanceParser.extractAmount("A/c XX1234 available balance Rs.1549.40"))
    }

    @Test
    fun `case is ignored on the currency prefix`() {
        assertEquals("100.00", BalanceParser.extractAmount("bal rs.100.00"))
        assertEquals("100.00", BalanceParser.extractAmount("BAL INR 100.00"))
    }
}
