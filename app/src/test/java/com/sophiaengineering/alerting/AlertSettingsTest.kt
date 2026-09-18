package com.sophiaengineering.alerting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsTest {

    private fun valid() = AlertSettings(
        senderEmail = "envoi@gmail.com",
        appPassword = "abcd efgh ijkl mnop",
        recipientEmail = "dest@exemple.com",
        frequencyMinutes = 15,
        smtpHost = "smtp.gmail.com",
        smtpPort = 587,
        monitoringEnabled = true
    )

    @Test
    fun `reglages complets sont valides`() {
        assertTrue(valid().isValid())
        assertTrue(valid().validationErrors().isEmpty())
    }

    @Test
    fun `email envoi invalide est rejete`() {
        assertFalse(valid().copy(senderEmail = "pas-un-email").isValid())
    }

    @Test
    fun `email destinataire invalide est rejete`() {
        assertFalse(valid().copy(recipientEmail = "dest@").isValid())
    }

    @Test
    fun `mot de passe vide est rejete`() {
        assertFalse(valid().copy(appPassword = "   ").isValid())
    }

    @Test
    fun `frequence inferieure a 1 est rejetee`() {
        assertFalse(valid().copy(frequencyMinutes = 0).isValid())
    }

    @Test
    fun `port hors plage est rejete`() {
        assertFalse(valid().copy(smtpPort = 0).isValid())
        assertFalse(valid().copy(smtpPort = 70000).isValid())
    }

    @Test
    fun `hote smtp vide est rejete`() {
        assertFalse(valid().copy(smtpHost = "").isValid())
    }

    @Test
    fun `plusieurs erreurs sont toutes remontees`() {
        val s = AlertSettings("", "", "", 0, "", 0, false)
        assertEquals(6, s.validationErrors().size)
    }

    @Test
    fun `validation email accepte les cas classiques`() {
        assertTrue(AlertSettings.isValidEmail("a.b@c.d"))
        assertTrue(AlertSettings.isValidEmail("  espace@trim.com  "))
        assertFalse(AlertSettings.isValidEmail("a@b"))
        assertFalse(AlertSettings.isValidEmail("a b@c.com"))
    }
}
