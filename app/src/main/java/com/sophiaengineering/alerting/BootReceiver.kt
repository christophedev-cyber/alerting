package com.sophiaengineering.alerting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Relance le service de surveillance après un redémarrage du téléphone,
 * si la surveillance était active avant l'extinction.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val store = SettingsStore(context)
            if (store.load().monitoringEnabled) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitoringService::class.java)
                )
            }
        }
    }
}
