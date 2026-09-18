package com.mw.offlineupi.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classifiers decide whether a payment that has already left the device succeeded. A misread
 * does not throw — it writes the wrong outcome into transaction history and tells the user their
 * money moved when it did not. So these are the tests that matter most in the module.
 *
 * The strings below are the shapes NPCI's *99# flow and the banks behind it actually put on
 * screen, including the ones that broke the previous substring-based implementation:
 * "unsuccessful" contains "successful", and "Insufficient balance. Available balance Rs 100"
 * carries balance text while being a decline.
 */
class UssdResponseClassifierTest {

    // ---- Success ---------------------------------------------------------------------------

    @Test
    fun `plain success is a success`() {
        assertTrue(
            UssdResponseClassifier.isSuccessResponse(
                "Rs.100.00 sent to JOHN DOE successfully. Txn ID 412345678901",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `a reference id alone is a success`() {
        assertTrue(
            UssdResponseClassifier.isSuccessResponse(
                "Payment to john@ybl. Ref No 987654321012",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `debited wording is a success`() {
        assertTrue(
            UssdResponseClassifier.isSuccessResponse(
                "Rs 250.00 has been debited from your A/c and sent to 9876543210",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    // ---- The negation traps a substring scan gets backwards --------------------------------

    @Test
    fun `unsuccessful is not a success`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Transaction unsuccessful. Please try again later.",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `not successful is not a success`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Your transaction was not successful.",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `could not be completed is not a success`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Transaction could not be completed. Ref No 412345678901",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `a reversal is not a success even with a reference id`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Txn ID 412345678901 has been reversed to your account.",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    // ---- Balance text is a success only for a balance enquiry ------------------------------

    @Test
    fun `balance text is a success for a balance enquiry`() {
        assertTrue(
            UssdResponseClassifier.isSuccessResponse(
                "Your A/c XX1234 available balance is Rs.1549.40",
                UssdCommandType.CHECK_BALANCE
            )
        )
    }

    @Test
    fun `insufficient balance is not a success for a payment`() {
        // The exact regression this guards: balance text used to count as a global success
        // signal, so a declined payment was written to history as SUCCESS.
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Insufficient balance. Available balance Rs 100.00",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `balance text alone is not a success for a payment`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Your A/c XX1234 available balance is Rs.1549.40",
                UssdCommandType.SEND_MONEY
            )
        )
    }

    @Test
    fun `balance text is not a success when the command type is unknown`() {
        assertFalse(
            UssdResponseClassifier.isSuccessResponse(
                "Your A/c XX1234 available balance is Rs.1549.40",
                null
            )
        )
    }

    // ---- Errors ----------------------------------------------------------------------------

    @Test
    fun `common decline strings are errors`() {
        val declines = listOf(
            "Transaction failed. Please try again.",
            "Transaction unsuccessful.",
            "Your transaction was not successful.",
            "Insufficient balance in your account.",
            "Invalid beneficiary account.",
            "Unable to process your request.",
            "Transaction declined by bank.",
            "Request rejected.",
            "Service unavailable. Try later.",
            "You are not registered for UPI.",
            "Amount exceeds per transaction limit.",
            "Daily limit exceeded.",
            "Connection problem or invalid MMI code.",
            "Session timed out.",
            "Your session has expired.",
            "Transaction reversed.",
            "Low balance."
        )
        for (text in declines) {
            assertTrue("should be an error: $text", UssdResponseClassifier.isErrorResponse(text))
        }
    }

    @Test
    fun `a success is not an error`() {
        assertFalse(
            UssdResponseClassifier.isErrorResponse(
                "Rs.100.00 sent to JOHN DOE successfully. Txn ID 412345678901"
            )
        )
    }

    @Test
    fun `a wrong pin is not reported as a generic error`() {
        // Wrong PIN has its own handler with an attempt counter; classifying it as a generic
        // error would fail the whole session on the first mistyped digit.
        assertFalse(UssdResponseClassifier.isErrorResponse("Incorrect UPI PIN. Please retry."))
        assertFalse(UssdResponseClassifier.isErrorResponse("Invalid PIN entered."))
    }

    @Test
    fun `word boundaries stop false error matches`() {
        // A bank line naming a "LIMITED" company is not a limit breach, and "reject"/"fail"
        // must not match inside ordinary words.
        assertFalse(
            UssdResponseClassifier.isErrorResponse(
                "Payment to LIMITED LIABILITY CO done. Ref No 412345678901"
            )
        )
    }

    // ---- Wrong PIN -------------------------------------------------------------------------

    @Test
    fun `wrong pin wordings are recognised`() {
        val wrong = listOf(
            "Incorrect PIN",
            "Wrong PIN entered",
            "Invalid PIN",
            "Incorrect UPI PIN. 2 attempts left",
            "Wrong UPI PIN",
            "The PIN is incorrect",
            "Your PIN is wrong",
            "You have entered an incorrect UPI PIN"
        )
        for (text in wrong) {
            assertTrue(
                "should be wrong-PIN: $text",
                UssdResponseClassifier.isWrongPinResponse(text)
            )
        }
    }

    @Test
    fun `a pin prompt is not a wrong pin`() {
        assertFalse(UssdResponseClassifier.isWrongPinResponse("Enter UPI PIN"))
    }

    // ---- PIN prompt ------------------------------------------------------------------------

    @Test
    fun `pin prompts are recognised`() {
        val prompts = listOf(
            "Enter PIN",
            "Enter UPI PIN",
            "Please enter your PIN",
            "Enter MPIN",
            "Enter transaction PIN",
            "Sending Rs.100.00 to JOHN DOE (john@bank). Enter UPI PIN"
        )
        for (text in prompts) {
            assertTrue("should be a PIN prompt: $text", UssdResponseClassifier.isPinPrompt(text))
        }
    }

    @Test
    fun `an amount prompt is not a pin prompt`() {
        assertFalse(UssdResponseClassifier.isPinPrompt("Enter amount"))
    }

    // ---- Balance detection -----------------------------------------------------------------

    @Test
    fun `balance responses are recognised`() {
        val balances = listOf(
            "Your A/c XX1234 available balance is Rs.1549.40",
            "Account balance: INR 1549.40",
            "A/c Bal 1549.40",
            "Your account balance is 1549.40",
            "Balance ₹1549.40"
        )
        for (text in balances) {
            assertTrue("should be a balance: $text", UssdResponseClassifier.isBalanceResponse(text))
        }
    }

    @Test
    fun `the word balance alone is not a balance response`() {
        assertFalse(UssdResponseClassifier.isBalanceResponse("Check balance"))
    }

    // ---- Session expiry --------------------------------------------------------------------

    @Test
    fun `session expiry codes are recognised`() {
        assertTrue(UssdResponseClassifier.isSessionExpiredResponse("error-code\n1"))
        assertTrue(UssdResponseClassifier.isSessionExpiredResponse("error code 1"))
        assertTrue(UssdResponseClassifier.isSessionExpiredResponse("  ERROR-CODE 2  "))
    }

    @Test
    fun `an error message is not a session expiry`() {
        assertFalse(UssdResponseClassifier.isSessionExpiredResponse("Transaction failed with error"))
    }

    // ---- Reference id ----------------------------------------------------------------------

    @Test
    fun `reference ids are extracted`() {
        assertEquals(
            "412345678901",
            UssdResponseClassifier.extractReferenceId("Success. Txn ID: 412345678901")
        )
        assertEquals(
            "987654321012",
            UssdResponseClassifier.extractReferenceId("Sent. Ref No 987654321012")
        )
        assertEquals(
            "ABC123",
            UssdResponseClassifier.extractReferenceId("Reference: ABC123")
        )
    }

    @Test
    fun `no reference id returns null`() {
        assertNull(UssdResponseClassifier.extractReferenceId("Payment successful"))
    }
}
