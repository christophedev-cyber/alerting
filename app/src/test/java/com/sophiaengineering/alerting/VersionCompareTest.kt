package com.sophiaengineering.alerting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `version superieure est detectee`() {
        assertTrue(VersionCompare.isNewerVersion("1.2", "1.1"))
        assertTrue(VersionCompare.isNewerVersion("v1.2", "1.1"))
        assertTrue(VersionCompare.isNewerVersion("2.0", "1.9"))
    }

    @Test
    fun `comparaison numerique et non lexicale`() {
        // 1.10 > 1.9 numériquement (lexicalement ce serait faux)
        assertTrue(VersionCompare.isNewerVersion("1.10", "1.9"))
    }

    @Test
    fun `version egale n'est pas plus recente`() {
        assertFalse(VersionCompare.isNewerVersion("1.1", "1.1"))
        assertFalse(VersionCompare.isNewerVersion("v1.1", "1.1"))
    }

    @Test
    fun `version inferieure n'est pas plus recente`() {
        assertFalse(VersionCompare.isNewerVersion("1.0", "1.1"))
    }

    @Test
    fun `build de dev est considere plus ancien qu'une release`() {
        assertTrue(VersionCompare.isNewerVersion("1.1", "0.0-dev.5"))
    }

    @Test
    fun `longueurs differentes gerees`() {
        assertTrue(VersionCompare.isNewerVersion("1.1.1", "1.1"))
        assertFalse(VersionCompare.isNewerVersion("1.1", "1.1.1"))
    }
}
