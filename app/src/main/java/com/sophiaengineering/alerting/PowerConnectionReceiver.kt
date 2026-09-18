package com.sophiaengineering.alerting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver enregistré au runtime par [MonitoringService].
 * (Les broadcasts ACTION_POWER_* ne sont pas fiables via le manifeste sur
 * Android récent : on les enregistre donc dynamiquement pendant que le service
 * en avant-plan est vivant.)
 */
class PowerConnectionReceiver(
    private val onPowerChanged: (PowerState) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_DISCONNECTED -> onPowerChanged(PowerState.ON_BATTERY)
            Intent.ACTION_POWER_CONNECTED -> onPowerChanged(PowerState.ON_AC)
        }
    }
}
