package com.sophiaengineering.alerting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsTest {

    private fun valid() = AlertSettings(
        senderEmail = "envoi@gmail.com",
        appPassword = "abcd efgh ijkl mnop",
        recipients = "dest@exemple.com",
        smtpHost = "smtp.gmail.com",
        smtpPort = 587,
        lowBatteryAlertEnabled = false,
        lowBatteryThreshold = 20,
        smsAlertEnabled = false,
        phoneCountryIso = "FR",
        phoneNumber = "",
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
    fun `mot de passe vide est rejete`() {
        assertFalse(valid().copy(appPassword = "   ").isValid())
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

    // --- Destinataires multiples ---------------------------------------

    @Test
    fun `aucun destinataire est rejete`() {
        assertFalse(valid().copy(recipients = "   ").isValid())
    }

    @Test
    fun `plusieurs destinataires valides sont acceptes`() {
        val s = valid().copy(recipients = "a@x.com, b@y.com; c@z.com")
        assertTrue(s.isValid())
        assertEquals(listOf("a@x.com", "b@y.com", "c@z.com"), s.recipientList())
    }

    @Test
    fun `destinataires separes par retour a la ligne`() {
        val s = valid().copy(recipients = "a@x.com\nb@y.com\n")
        assertEquals(listOf("a@x.com", "b@y.com"), s.recipientList())
    }

    @Test
    fun `un destinataire invalide dans la liste est rejete`() {
        assertFalse(valid().copy(recipients = "ok@x.com, pas-bon").isValid())
    }

    // --- Seuil batterie faible -----------------------------------------

    @Test
    fun `seuil ignore si alerte batterie desactivee`() {
        // seuil hors plage mais toggle off -> valide
        assertTrue(valid().copy(lowBatteryAlertEnabled = false, lowBatteryThreshold = 0).isValid())
    }

    @Test
    fun `seuil hors plage rejete si alerte batterie activee`() {
        assertFalse(valid().copy(lowBatteryAlertEnabled = true, lowBatteryThreshold = 0).isValid())
        assertFalse(valid().copy(lowBatteryAlertEnabled = true, lowBatteryThreshold = 101).isValid())
    }

    @Test
    fun `seuil valide accepte si alerte batterie activee`() {
        assertTrue(valid().copy(lowBatteryAlertEnabled = true, lowBatteryThreshold = 15).isValid())
    }

    // --- Alerte SMS ----------------------------------------------------

    @Test
    fun `sms active sans numero est rejete`() {
        assertFalse(valid().copy(smsAlertEnabled = true, phoneNumber = "  ").isValid())
    }

    @Test
    fun `sms active avec numero renseigne passe la validation de base`() {
        // La validation de format (libphonenumber) est faite au niveau UI ;
        // ici on vérifie seulement la règle "numéro non vide".
        assertTrue(valid().copy(smsAlertEnabled = true, phoneNumber = "612345678").isValid())
    }

    @Test
    fun `sms desactive ignore le numero`() {
        assertTrue(valid().copy(smsAlertEnabled = false, phoneNumber = "").isValid())
    }

    // --- Validation email ----------------------------------------------

    @Test
    fun `validation email accepte les cas classiques`() {
        assertTrue(AlertSettings.isValidEmail("a.b@c.d"))
        assertTrue(AlertSettings.isValidEmail("  espace@trim.com  "))
        assertFalse(AlertSettings.isValidEmail("a@b"))
        assertFalse(AlertSettings.isValidEmail("a b@c.com"))
    }
}
