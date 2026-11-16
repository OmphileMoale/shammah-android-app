package com.example.shammah

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CardValidator (card checks done on the phone before a donation is recorded).
 * These run on the development machine and in Azure Pipelines (./gradlew testDebugUnitTest).
 * The test card numbers are well-known public test numbers, not real cards.
 *
 * Reference: Android Developers. Build local unit tests.
 * https://developer.android.com/training/testing/local-tests
 */
class CardValidatorTest {

    private val name = "Thabo Mokoena"

    @Test
    fun validVisa_isAcceptedAndDetected() {
        val r = CardValidator.validateCard("4111 1111 1111 1111", name, "123", false)
        assertTrue(r.isValid)
        assertEquals("Visa", r.cardType)
        assertEquals("", r.errorMessage)
    }

    @Test
    fun validMasterCard_isDetected() {
        val r = CardValidator.validateCard("5555555555554444", name, "123", false)
        assertTrue(r.isValid)
        assertEquals("MasterCard", r.cardType)
    }

    @Test
    fun validDiscover_isDetected() {
        val r = CardValidator.validateCard("6011111111111117", name, "123", false)
        assertTrue(r.isValid)
        assertEquals("Discover", r.cardType)
    }

    @Test
    fun validAmex_needsFourDigitCvv() {
        val ok = CardValidator.validateCard("378282246310005", name, "1234", false)
        assertTrue(ok.isValid)
        assertEquals("American Express", ok.cardType)

        val bad = CardValidator.validateCard("378282246310005", name, "123", false)
        assertFalse(bad.isValid)
        assertEquals("American Express requires a 4-digit CVV", bad.errorMessage)
    }

    @Test
    fun unknownCardType_stillPassesLuhn() {
        val r = CardValidator.validateCard("3530111333300000", name, "123", false)
        assertTrue(r.isValid)
        assertEquals("Credit/Debit", r.cardType)
    }

    @Test
    fun failedLuhnChecksum_isRejected() {
        val r = CardValidator.validateCard("4111111111111112", name, "123", false)
        assertFalse(r.isValid)
        assertEquals("Invalid card number (Luhn checksum failed)", r.errorMessage)
    }

    @Test
    fun tooShortNumber_isRejected() {
        val r = CardValidator.validateCard("411111", name, "123", false)
        assertFalse(r.isValid)
        assertEquals("Card number must be 13 to 19 digits", r.errorMessage)
    }

    @Test
    fun badCardholderName_isRejected() {
        val withDigit = CardValidator.validateCard("4111111111111111", "John3", "123", false)
        assertFalse(withDigit.isValid)
        assertEquals("Enter a valid cardholder name", withDigit.errorMessage)

        val tooShort = CardValidator.validateCard("4111111111111111", "A", "123", false)
        assertFalse(tooShort.isValid)
    }

    @Test
    fun shortCvv_isRejected() {
        val r = CardValidator.validateCard("4111111111111111", name, "12", false)
        assertFalse(r.isValid)
        assertEquals("Enter a valid 3 or 4-digit CVV", r.errorMessage)
    }

    @Test
    fun expiredCard_isRejected() {
        val r = CardValidator.validateCard("4111111111111111", name, "123", true)
        assertFalse(r.isValid)
        assertEquals("This card has expired", r.errorMessage)
    }
}
