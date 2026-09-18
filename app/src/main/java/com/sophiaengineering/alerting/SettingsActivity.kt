package com.sophiaengineering.alerting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sophiaengineering.alerting.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsStore

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        loadIntoUi(store.load())

        binding.testButton.setOnClickListener { onTestClicked() }
        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }

        maybeRequestNotificationPermission()
    }

    private fun loadIntoUi(s: AlertSettings) {
        binding.senderEmail.setText(s.senderEmail)
        binding.appPassword.setText(s.appPassword)
        binding.recipients.setText(s.recipients)
        binding.frequency.setText(s.frequencyMinutes.toString())
        binding.lowBatterySwitch.isChecked = s.lowBatteryAlertEnabled
        binding.lowBatteryThreshold.setText(s.lowBatteryThreshold.toString())
        binding.smtpHost.setText(s.smtpHost)
        binding.smtpPort.setText(s.smtpPort.toString())
        binding.statusText.text = if (s.monitoringEnabled) {
            "Status: monitoring active"
        } else {
            "Status: stopped"
        }
    }

    private fun readFromUi(): AlertSettings = AlertSettings(
        senderEmail = binding.senderEmail.text.toString(),
        appPassword = binding.appPassword.text.toString(),
        recipients = binding.recipients.text.toString(),
        frequencyMinutes = binding.frequency.text.toString().toIntOrNull() ?: 0,
        smtpHost = binding.smtpHost.text.toString().ifBlank { SettingsStore.DEFAULT_HOST },
        smtpPort = binding.smtpPort.text.toString().toIntOrNull() ?: 0,
        lowBatteryAlertEnabled = binding.lowBatterySwitch.isChecked,
        lowBatteryThreshold = binding.lowBatteryThreshold.text.toString().toIntOrNull() ?: 0,
        monitoringEnabled = true
    )

    private fun onStartClicked() {
        val settings = readFromUi()
        val errors = settings.validationErrors()
        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }
        store.save(settings)
        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitoringService::class.java)
        )
        binding.statusText.text = "Status: monitoring active"
    }

    private fun onStopClicked() {
        store.setMonitoringEnabled(false)
        stopService(Intent(this, MonitoringService::class.java))
        binding.statusText.text = "Status: stopped"
    }

    /** Envoie un email de test avec les valeurs actuellement saisies. */
    private fun onTestClicked() {
        val settings = readFromUi()
        val errors = settings.validationErrors()
        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }
        binding.testButton.isEnabled = false
        binding.statusText.text = "Sending test…"
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    SmtpEmailSender().send(
                        settings,
                        EmailMessage(
                            subject = "[TEST] Alerting",
                            body = "This is a test email sent from the Alerting app."
                        )
                    )
                    null
                } catch (e: Exception) {
                    e.message ?: "unknown error"
                }
            }
            binding.testButton.isEnabled = true
            binding.statusText.text = if (error == null) {
                "Test sent successfully ✓"
            } else {
                "Test failed: $error"
            }
        }
    }

    private fun showErrors(errors: List<String>) {
        binding.statusText.text = "Errors:\n- " + errors.joinToString("\n- ")
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
