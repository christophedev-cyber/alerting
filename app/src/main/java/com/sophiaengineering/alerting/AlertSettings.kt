package com.sophiaengineering.alerting

/**
 * Réglages immuables de l'application. Ne dépend d'aucune API Android :
 * la validation est testable en JVM pure.
 *
 * [recipients] peut contenir plusieurs adresses séparées par virgule,
 * point-virgule ou retour à la ligne.
 */
data class AlertSettings(
    val senderEmail: String,
    val appPassword: String,
    val recipients: String,
    val frequencyMinutes: Int,
    val smtpHost: String,
    val smtpPort: Int,
    val lowBatteryAlertEnabled: Boolean,
    val lowBatteryThreshold: Int,
    val monitoringEnabled: Boolean
) {
    /** Liste des destinataires normalisée (adresses non vides). */
    fun recipientList(): List<String> =
        recipients.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Liste des erreurs de validation (vide si les réglages sont valides). */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (!isValidEmail(senderEmail)) errors.add("Invalid sender email")
        if (appPassword.isBlank()) errors.add("Missing app password")

        val recips = recipientList()
        if (recips.isEmpty()) {
            errors.add("At least one recipient is required")
        } else {
            recips.filterNot { isValidEmail(it) }
                .forEach { errors.add("Invalid recipient: $it") }
        }

        if (frequencyMinutes < 1) errors.add("Frequency must be at least 1 minute")
        if (smtpHost.isBlank()) errors.add("Missing SMTP server")
        if (smtpPort !in 1..65535) errors.add("Invalid SMTP port (1-65535)")
        if (lowBatteryAlertEnabled && lowBatteryThreshold !in 1..100) {
            errors.add("Battery threshold must be between 1 and 100%")
        }
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
