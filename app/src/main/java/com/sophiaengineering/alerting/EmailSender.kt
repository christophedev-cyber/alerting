package com.sophiaengineering.alerting

/** Message email à envoyer. */
data class EmailMessage(
    val subject: String,
    val body: String
)

/**
 * Contrat d'envoi d'un email. Abstrait pour permettre de tester la logique
 * sans envoi réel (implémentation factice dans les tests).
 */
interface EmailSender {
    /** Envoie le message. Lève une exception en cas d'échec. */
    fun send(settings: AlertSettings, message: EmailMessage)
}
