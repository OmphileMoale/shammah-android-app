package com.example.shammah

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for PhoneFormat.toE164, which turns local numbers into the international
 * format that the SMS service needs (for example 0821234567 becomes +27821234567).
 * The country code is passed in directly so the tests do not depend on app settings.
 *
 * Reference: Android Developers. Build local unit tests.
 * https://developer.android.com/training/testing/local-tests
 */
class PhoneFormatTest {

    private val za = "27"

    @Test
    fun localNumber_becomesInternational() {
        assertEquals("+27821234567", PhoneFormat.toE164("0821234567", za))
    }

    @Test
    fun spacesBracketsAndDashes_areIgnored() {
        assertEquals("+27821234567", PhoneFormat.toE164("082 123 4567", za))
        assertEquals("+27821234567", PhoneFormat.toE164("(082) 123-4567", za))
    }

    @Test
    fun internationalNumber_isUnchanged() {
        assertEquals("+27821234567", PhoneFormat.toE164("+27821234567", za))
    }

    @Test
    fun doubleZeroPrefix_becomesPlus() {
        assertEquals("+27821234567", PhoneFormat.toE164("0027821234567", za))
    }

    @Test
    fun invalidNumbers_returnNull() {
        assertNull(PhoneFormat.toE164("", za))
        assertNull(PhoneFormat.toE164("abc", za))
        assertNull(PhoneFormat.toE164("123", za))
        assertNull(PhoneFormat.toE164("0123", za))
        assertNull(PhoneFormat.toE164("+2782123456789012345", za))
    }
}
