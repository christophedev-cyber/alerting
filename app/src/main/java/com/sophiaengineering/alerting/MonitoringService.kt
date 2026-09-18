package com.sophiaengineering.alerting

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private lateinit var settingsStore: SettingsStore
    private lateinit var emailSender: EmailSender
    private val stateMachine = AlertStateMachine()

    private lateinit var powerReceiver: PowerConnectionReceiver

    /** Vrai si l'alerte batterie faible a déjà été envoyée pour la décharge en cours. */
    private var lowBatteryAlerted = false

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            handleBatteryChanged(pct, charging)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        emailSender = SmtpEmailSender()

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Monitoring active"))

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

        // Surveillance du niveau de batterie (broadcast sticky : renvoie
        // immédiatement l'état courant à l'enregistrement).
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
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
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: IllegalArgumentException) {
            // déjà désenregistré
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: IllegalArgumentException) {
            // déjà désenregistré
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Logique ---------------------------------------------------------

    private fun handlePowerEvent(newState: PowerState) {
        when (stateMachine.onPowerEvent(newState)) {
            is AlertAction.StartBatteryAlerts -> {
                updateNotification("⚠ On battery — alert sent")
                sendEmailAsync(buildBatteryMessage())
                sendSmsIfEnabled(
                    "[ALERT] ${deviceName()} on battery (${batteryLevel()}%) at ${now()}"
                )
            }
            is AlertAction.StopAndNotifyRestored -> {
                sendEmailAsync(buildRestoredMessage())
                sendSmsIfEnabled("[OK] ${deviceName()} mains power restored at ${now()}")
                updateNotification("On mains power — monitoring active")
            }
            is AlertAction.None -> {
                val label = if (stateMachine.state == PowerState.ON_BATTERY) {
                    "⚠ On battery"
                } else {
                    "On mains power — monitoring active"
                }
                updateNotification(label)
            }
        }
    }

    /**
     * Alerte batterie faible : envoi unique au franchissement du seuil, uniquement
     * lorsque le téléphone est sur batterie (pas en charge).
     */
    private fun handleBatteryChanged(pct: Int, charging: Boolean) {
        val settings = settingsStore.load()
        if (!settings.lowBatteryAlertEnabled) return

        if (charging) {
            lowBatteryAlerted = false
            return
        }
        if (pct <= settings.lowBatteryThreshold) {
            if (!lowBatteryAlerted) {
                lowBatteryAlerted = true
                sendEmailAsync(buildLowBatteryMessage(pct))
                sendSmsIfEnabled("[LOW BATTERY] ${deviceName()} $pct% at ${now()}")
            }
        } else {
            lowBatteryAlerted = false
        }
    }

    private fun sendEmailAsync(message: EmailMessage) {
        serviceScope.launch { sendEmail(message) }
    }

    /** Envoie un SMS d'alerte si l'option est activée, le numéro valide et la permission accordée. */
    private fun sendSmsIfEnabled(text: String) {
        val settings = settingsStore.load()
        if (!settings.smsAlertEnabled) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("SMS not sent: SMS permission missing")
            return
        }
        val telephony = getSystemService(TelephonyManager::class.java)
        if (telephony == null || telephony.simState != TelephonyManager.SIM_STATE_READY) {
            updateNotification("SMS not sent: no SIM card")
            return
        }
        val destinations = settings.phoneEntries()
            .mapNotNull { (iso, number) -> PhoneValidator.toE164(iso, number) }
        if (destinations.isEmpty()) {
            updateNotification("SMS not sent: invalid phone number")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            destinations.forEach { destination ->
                try {
                    SmsSender.send(this@MonitoringService, destination, text)
                } catch (e: Exception) {
                    updateNotification("SMS failed: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    private suspend fun sendEmail(message: EmailMessage) {
        val settings = settingsStore.load()
        if (!settings.isValid()) {
            updateNotification("Invalid settings — cannot send")
            return
        }
        try {
            withContext(Dispatchers.IO) { emailSender.send(settings, message) }
            updateNotification("Last email sent at ${now()}")
        } catch (e: Exception) {
            updateNotification("Send failed: ${e.message ?: "unknown error"}")
        }
    }

    // --- Contenu des emails ---------------------------------------------

    private fun buildBatteryMessage(): EmailMessage = EmailMessage(
        subject = "[ALERT] ${deviceName()} is on battery",
        body = buildString {
            appendLine("The phone is no longer powered from the mains (220V).")
            appendLine()
            appendLine("Device     : ${deviceName()}")
            appendLine("Battery    : ${batteryLevel()}%")
            appendLine("Timestamp  : ${now()}")
        }
    )

    private fun buildLowBatteryMessage(pct: Int): EmailMessage = EmailMessage(
        subject = "[LOW BATTERY] ${deviceName()} — $pct%",
        body = buildString {
            appendLine("The battery level has dropped below the configured threshold.")
            appendLine()
            appendLine("Device     : ${deviceName()}")
            appendLine("Battery    : $pct%")
            appendLine("Timestamp  : ${now()}")
        }
    )

    private fun buildRestoredMessage(): EmailMessage = EmailMessage(
        subject = "[OK] ${deviceName()} — mains power restored",
        body = buildString {
            appendLine("The phone is powered from the mains (220V) again.")
            appendLine()
            appendLine("Device     : ${deviceName()}")
            appendLine("Battery    : ${batteryLevel()}%")
            appendLine("Timestamp  : ${now()}")
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
            "Power monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for the mains-power monitoring service"
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
