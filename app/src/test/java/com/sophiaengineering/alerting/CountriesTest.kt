package com.sophiaengineering.alerting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {

    @Test
    fun `drapeau emoji pour la France`() {
        // 🇫🇷 = U+1F1EB U+1F1F7
        assertEquals("🇫🇷", Countries.flagEmoji("FR"))
    }

    @Test
    fun `liste non vide et contient la France`() {
        val all = Countries.all()
        assertTrue(all.size > 100)
        val fr = all.first { it.iso == "FR" }
        assertEquals(33, fr.dialCode)
        assertTrue(fr.display().contains("+33"))
    }

    @Test
    fun `index par iso est retrouve`() {
        val all = Countries.all()
        val idx = Countries.indexOfIso(all, "FR")
        assertEquals("FR", all[idx].iso)
    }

    @Test
    fun `index inconnu renvoie zero`() {
        val all = Countries.all()
        assertEquals(0, Countries.indexOfIso(all, "ZZ"))
    }
}
