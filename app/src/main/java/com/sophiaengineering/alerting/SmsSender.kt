package com.sophiaengineering.alerting

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * Envoi de SMS via l'API système. À exécuter hors du thread principal.
 * L'appelant doit avoir vérifié la permission SEND_SMS au préalable.
 */
object SmsSender {

    fun send(context: Context, destination: String, message: String) {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(message)
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(destination, null, parts, null, null)
        } else {
            manager.sendTextMessage(destination, null, message, null, null)
        }
    }
}
