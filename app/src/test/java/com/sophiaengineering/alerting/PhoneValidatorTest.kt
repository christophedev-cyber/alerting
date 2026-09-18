package com.sophiaengineering.alerting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneValidatorTest {

    @Test
    fun `numero francais valide`() {
        assertTrue(PhoneValidator.isValid("FR", "612345678"))
        assertTrue(PhoneValidator.isValid("FR", "06 12 34 56 78"))
    }

    @Test
    fun `numero francais invalide`() {
        assertFalse(PhoneValidator.isValid("FR", "12"))
        assertFalse(PhoneValidator.isValid("FR", "abc"))
    }

    @Test
    fun `format e164 pour la France`() {
        assertEquals("+33612345678", PhoneValidator.toE164("FR", "06 12 34 56 78"))
    }

    @Test
    fun `e164 null si invalide`() {
        assertNull(PhoneValidator.toE164("FR", "12"))
    }

    @Test
    fun `indicatifs connus`() {
        assertEquals(33, PhoneValidator.dialCode("FR"))
        assertEquals(1, PhoneValidator.dialCode("US"))
        assertEquals(32, PhoneValidator.dialCode("BE"))
    }

    @Test
    fun `numero US valide`() {
        assertTrue(PhoneValidator.isValid("US", "(650) 253-0000"))
        assertEquals("+16502530000", PhoneValidator.toE164("US", "(650) 253-0000"))
    }
}
