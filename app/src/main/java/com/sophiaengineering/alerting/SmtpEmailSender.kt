package com.sophiaengineering.alerting

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Envoi SMTP via JavaMail (portage Android com.sun.mail:android-mail).
 * Utilise STARTTLS, adapté à Gmail (smtp.gmail.com:587) avec un mot de passe
 * d'application Google.
 *
 * IMPORTANT : à exécuter hors du thread principal.
 */
class SmtpEmailSender : EmailSender {

    override fun send(settings: AlertSettings, message: EmailMessage) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", settings.smtpHost)
            put("mail.smtp.port", settings.smtpPort.toString())
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(settings.senderEmail, settings.appPassword)
        })

        val mime = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.senderEmail))
            setRecipient(Message.RecipientType.TO, InternetAddress(settings.recipientEmail))
            subject = message.subject
            setText(message.body, "UTF-8")
        }

        Transport.send(mime)
    }
}
