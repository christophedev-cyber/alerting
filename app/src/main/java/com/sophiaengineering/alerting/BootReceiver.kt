package com.sophiaengineering.alerting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Relance le service de surveillance après un redémarrage du téléphone,
 * si la surveillance était active avant l'extinction.
 *
 * Gère plusieurs actions de boot car certains constructeurs n'envoient pas
 * le BOOT_COMPLETED standard (fast boot / QuickBoot).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return

        val store = SettingsStore(context)
        if (!store.load().monitoringEnabled) return

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java)
            )
        } catch (_: Exception) {
            // Certaines surcouches interdisent le démarrage d'un service en
            // avant-plan au boot : on évite de planter le receiver. L'utilisateur
            // devra autoriser le démarrage automatique de l'app (voir README).
        }
    }

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
