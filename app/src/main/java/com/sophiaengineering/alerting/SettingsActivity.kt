package com.sophiaengineering.alerting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sophiaengineering.alerting.databinding.ActivitySettingsBinding

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

        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }

        maybeRequestNotificationPermission()
    }

    private fun loadIntoUi(s: AlertSettings) {
        binding.senderEmail.setText(s.senderEmail)
        binding.appPassword.setText(s.appPassword)
        binding.recipientEmail.setText(s.recipientEmail)
        binding.frequency.setText(s.frequencyMinutes.toString())
        binding.smtpHost.setText(s.smtpHost)
        binding.smtpPort.setText(s.smtpPort.toString())
        binding.statusText.text = if (s.monitoringEnabled) {
            "Statut : surveillance active"
        } else {
            "Statut : arrêtée"
        }
    }

    private fun readFromUi(): AlertSettings = AlertSettings(
        senderEmail = binding.senderEmail.text.toString(),
        appPassword = binding.appPassword.text.toString(),
        recipientEmail = binding.recipientEmail.text.toString(),
        frequencyMinutes = binding.frequency.text.toString().toIntOrNull() ?: 0,
        smtpHost = binding.smtpHost.text.toString().ifBlank { SettingsStore.DEFAULT_HOST },
        smtpPort = binding.smtpPort.text.toString().toIntOrNull() ?: 0,
        monitoringEnabled = true
    )

    private fun onStartClicked() {
        val settings = readFromUi()
        val errors = settings.validationErrors()
        if (errors.isNotEmpty()) {
            binding.statusText.text = "Erreurs :\n- " + errors.joinToString("\n- ")
            return
        }
        store.save(settings)
        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitoringService::class.java)
        )
        binding.statusText.text = "Statut : surveillance active"
    }

    private fun onStopClicked() {
        store.setMonitoringEnabled(false)
        stopService(Intent(this, MonitoringService::class.java))
        binding.statusText.text = "Statut : arrêtée"
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
