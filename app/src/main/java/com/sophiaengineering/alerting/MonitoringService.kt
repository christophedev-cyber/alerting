package com.sophiaengineering.alerting

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service en avant-plan qui surveille l'alimentation secteur.
 *
 * - Enregistre [PowerConnectionReceiver] au runtime.
 * - Sur perte du secteur : email immédiat puis renvoi toutes les N minutes.
 * - Sur retour du secteur : arrêt des renvois + email de rétablissement.
 */
class MonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var alertJob: Job? = null

    private lateinit var settingsStore: SettingsStore
    private lateinit var emailSender: EmailSender
    private val stateMachine = AlertStateMachine()

    private lateinit var powerReceiver: PowerConnectionReceiver

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        emailSender = SmtpEmailSender()

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Surveillance active"))

        powerReceiver = PowerConnectionReceiver { newState -> handlePowerEvent(newState) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Initialise l'état courant. Si le téléphone est déjà sur batterie au
        // démarrage du service, cela déclenche immédiatement les alertes.
        handlePowerEvent(currentPowerState())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settingsStore.setMonitoringEnabled(true)
        return START_STICKY
    }

    override fun onDestroy() {
        stopBatteryAlertLoop()
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: IllegalArgumentException) {
            // déjà désenregistré
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Logique ---------------------------------------------------------

    private fun handlePowerEvent(newState: PowerState) {
        when (stateMachine.onPowerEvent(newState)) {
            is AlertAction.StartBatteryAlerts -> startBatteryAlertLoop()
            is AlertAction.StopAndNotifyRestored -> {
                stopBatteryAlertLoop()
                sendEmailAsync(buildRestoredMessage())
                updateNotification("Sur secteur — surveillance active")
            }
            is AlertAction.None -> {
                val label = if (stateMachine.state == PowerState.ON_BATTERY) {
                    "⚠ Sur batterie — alertes en cours"
                } else {
                    "Sur secteur — surveillance active"
                }
                updateNotification(label)
            }
        }
    }

    private fun startBatteryAlertLoop() {
        stopBatteryAlertLoop()
        updateNotification("⚠ Sur batterie — envoi des alertes")
        alertJob = serviceScope.launch {
            val freqMinutes = settingsStore.load().frequencyMinutes.coerceAtLeast(1)
            while (isActive) {
                sendEmail(buildBatteryMessage())
                delay(freqMinutes.toLong() * 60_000L)
            }
        }
    }

    private fun stopBatteryAlertLoop() {
        alertJob?.cancel()
        alertJob = null
    }

    private fun sendEmailAsync(message: EmailMessage) {
        serviceScope.launch { sendEmail(message) }
    }

    private suspend fun sendEmail(message: EmailMessage) {
        val settings = settingsStore.load()
        if (!settings.isValid()) {
            updateNotification("Réglages invalides — envoi impossible")
            return
        }
        try {
            withContext(Dispatchers.IO) { emailSender.send(settings, message) }
            updateNotification("Dernier email envoyé à ${now()}")
        } catch (e: Exception) {
            updateNotification("Échec d'envoi : ${e.message ?: "erreur inconnue"}")
        }
    }

    // --- Contenu des emails ---------------------------------------------

    private fun buildBatteryMessage(): EmailMessage = EmailMessage(
        subject = "[ALERTE] ${deviceName()} est sur batterie",
        body = buildString {
            appendLine("Le téléphone n'est plus alimenté par le secteur (220V).")
            appendLine()
            appendLine("Appareil     : ${deviceName()}")
            appendLine("Batterie     : ${batteryLevel()}%")
            appendLine("Horodatage   : ${now()}")
            appendLine()
            appendLine("Cet email sera renvoyé toutes les " +
                "${settingsStore.load().frequencyMinutes} minute(s) tant que le " +
                "téléphone reste sur batterie.")
        }
    )

    private fun buildRestoredMessage(): EmailMessage = EmailMessage(
        subject = "[OK] ${deviceName()} — secteur rétabli",
        body = buildString {
            appendLine("Le téléphone est de nouveau alimenté par le secteur (220V).")
            appendLine()
            appendLine("Appareil     : ${deviceName()}")
            appendLine("Batterie     : ${batteryLevel()}%")
            appendLine("Horodatage   : ${now()}")
        }
    )

    // --- Utilitaires système --------------------------------------------

    private fun currentPowerState(): PowerState {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return if (bm.isCharging) PowerState.ON_AC else PowerState.ON_BATTERY
    }

    private fun batteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun deviceName(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    // --- Notification ----------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Surveillance alimentation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification persistante du service de surveillance secteur"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Alerting")
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, SettingsActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "alerting_monitoring"
        private const val NOTIF_ID = 1001
    }
}
