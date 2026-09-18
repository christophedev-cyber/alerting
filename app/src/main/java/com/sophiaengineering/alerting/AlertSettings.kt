package com.sophiaengineering.alerting

/**
 * Réglages immuables de l'application. Ne dépend d'aucune API Android :
 * la validation est testable en JVM pure.
 */
data class AlertSettings(
    val senderEmail: String,
    val appPassword: String,
    val recipientEmail: String,
    val frequencyMinutes: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val monitoringEnabled: Boolean
) {
    /** Liste des erreurs de validation (vide si les réglages sont valides). */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (!isValidEmail(senderEmail)) errors.add("Email d'envoi invalide")
        if (appPassword.isBlank()) errors.add("Mot de passe d'application manquant")
        if (!isValidEmail(recipientEmail)) errors.add("Email destinataire invalide")
        if (frequencyMinutes < 1) errors.add("La fréquence doit être ≥ 1 minute")
        if (smtpHost.isBlank()) errors.add("Serveur SMTP manquant")
        if (smtpPort !in 1..65535) errors.add("Port SMTP invalide (1-65535)")
        return errors
    }

    fun isValid(): Boolean = validationErrors().isEmpty()

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Validation d'email volontairement simple et sans dépendance Android. */
        fun isValidEmail(email: String): Boolean {
            val trimmed = email.trim()
            return trimmed.isNotEmpty() && EMAIL_REGEX.matches(trimmed)
        }
    }
}
