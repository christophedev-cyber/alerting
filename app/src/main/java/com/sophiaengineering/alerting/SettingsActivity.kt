package com.sophiaengineering.alerting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private val countries by lazy { Countries.all() }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                binding.smsSwitch.isChecked = false
                binding.statusText.text = "SMS permission denied — SMS alerts disabled"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        binding.titleText.text = "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}"

        store = SettingsStore(this)
        setupCountrySpinners()
        loadIntoUi(store.load())
        attachListeners()

        maybeRequestNotificationPermission()
        checkForUpdate()
    }

    private fun setupCountrySpinners() {
        binding.country1Spinner.adapter = CountryAdapter(this, countries)
        binding.country2Spinner.adapter = CountryAdapter(this, countries)
    }

    private fun attachListeners() {
        binding.testButton.setOnClickListener { onTestClicked() }
        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }

        binding.smsSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!hasSimCard()) {
                    binding.smsSwitch.isChecked = false
                    showNoSimDialog()
                    return@setOnCheckedChangeListener
                }
                if (!hasSmsPermission()) {
                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                }
            }
        }
    }

    private fun loadIntoUi(s: AlertSettings) {
        binding.senderEmail.setText(s.senderEmail)
        binding.appPassword.setText(s.appPassword)
        binding.recipients.setText(s.recipients)
        binding.smtpHost.setText(s.smtpHost)
        binding.smtpPort.setText(s.smtpPort.toString())

        binding.smsSwitch.isChecked = s.smsAlertEnabled
        binding.country1Spinner.setSelection(Countries.indexOfIso(countries, s.phone1CountryIso))
        binding.phone1Number.setText(s.phone1Number)
        binding.country2Spinner.setSelection(Countries.indexOfIso(countries, s.phone2CountryIso))
        binding.phone2Number.setText(s.phone2Number)

        binding.lowBatterySwitch.isChecked = s.lowBatteryAlertEnabled
        binding.lowBatteryThreshold.setText(s.lowBatteryThreshold.toString())

        updateStatusBadge(s.monitoringEnabled)
    }

    private fun updateStatusBadge(active: Boolean) {
        if (active) {
            binding.statusBadge.text = "Active"
            binding.statusBadge.setTextColor(Color.parseColor("#00E676"))
        } else {
            binding.statusBadge.text = "Stopped"
            binding.statusBadge.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun readFromUi(): AlertSettings {
        val c1 = countries[binding.country1Spinner.selectedItemPosition]
        val c2 = countries[binding.country2Spinner.selectedItemPosition]
        return AlertSettings(
            senderEmail = binding.senderEmail.text.toString(),
            appPassword = binding.appPassword.text.toString(),
            recipients = binding.recipients.text.toString(),
            smtpHost = binding.smtpHost.text.toString().ifBlank { SettingsStore.DEFAULT_HOST },
            smtpPort = binding.smtpPort.text.toString().toIntOrNull() ?: 0,
            lowBatteryAlertEnabled = binding.lowBatterySwitch.isChecked,
            lowBatteryThreshold = binding.lowBatteryThreshold.text.toString().toIntOrNull() ?: 0,
            smsAlertEnabled = binding.smsSwitch.isChecked,
            phone1CountryIso = c1.iso,
            phone1Number = binding.phone1Number.text.toString(),
            phone2CountryIso = c2.iso,
            phone2Number = binding.phone2Number.text.toString(),
            monitoringEnabled = true
        )
    }

    /** Validation des réglages + du/des numéro(s) selon le pays (via libphonenumber). */
    private fun validate(settings: AlertSettings): List<String> {
        val errors = settings.validationErrors().toMutableList()
        if (settings.smsAlertEnabled) {
            settings.phoneEntries().forEach { (iso, number) ->
                if (!PhoneValidator.isValid(iso, number)) {
                    errors.add("Invalid phone number: +${PhoneValidator.dialCode(iso)} $number")
                }
            }
        }
        return errors
    }

    private fun onStartClicked() {
        val settings = readFromUi()
        val errors = validate(settings)
        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }
        store.save(settings)
        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitoringService::class.java)
        )
        updateStatusBadge(true)
        binding.statusText.text = ""
    }

    private fun onStopClicked() {
        store.setMonitoringEnabled(false)
        stopService(Intent(this, MonitoringService::class.java))
        updateStatusBadge(false)
        binding.statusText.text = ""
    }

    /** Envoie un email de test et, si l'option SMS est active, un SMS de test. */
    private fun onTestClicked() {
        val settings = readFromUi()
        val errors = validate(settings)
        if (errors.isNotEmpty()) {
            showErrors(errors)
            return
        }
        binding.testButton.isEnabled = false
        binding.statusText.text = "Sending test…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runTest(settings) }
            binding.testButton.isEnabled = true
            binding.statusText.text = result
        }
    }

    private fun runTest(settings: AlertSettings): String {
        val report = StringBuilder()

        try {
            SmtpEmailSender().send(
                settings,
                EmailMessage(
                    subject = "[TEST] Alerting",
                    body = "This is a test email sent from the Alerting app."
                )
            )
            report.append("Email sent ✓")
        } catch (e: Exception) {
            report.append("Email failed: ${e.message ?: "unknown error"}")
        }

        if (settings.smsAlertEnabled) {
            val destinations = settings.phoneEntries()
                .mapNotNull { (iso, number) -> PhoneValidator.toE164(iso, number) }
            when {
                !hasSmsPermission() -> report.append("\nSMS skipped: permission missing")
                !hasSimCard() -> report.append("\nSMS skipped: no SIM card")
                destinations.isEmpty() -> report.append("\nSMS skipped: no valid number")
                else -> {
                    var ok = 0
                    var failed = 0
                    destinations.forEach { dest ->
                        try {
                            SmsSender.send(this, dest, "[TEST] Alerting SMS")
                            ok++
                        } catch (_: Exception) {
                            failed++
                        }
                    }
                    report.append("\nSMS sent to $ok number(s)")
                    if (failed > 0) report.append(", $failed failed")
                }
            }
        }
        return report.toString()
    }

    private fun showErrors(errors: List<String>) {
        binding.statusText.text = "Errors:\n- " + errors.joinToString("\n- ")
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Vrai si une carte SIM (physique ou eSIM active) est présente. */
    private fun hasSimCard(): Boolean {
        val tm = getSystemService(TelephonyManager::class.java) ?: return false
        return when (tm.simState) {
            TelephonyManager.SIM_STATE_ABSENT,
            TelephonyManager.SIM_STATE_UNKNOWN -> false
            else -> true
        }
    }

    private fun showNoSimDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sim_required_title)
            .setMessage(R.string.sim_required_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // --- Vérification de mise à jour -----------------------------------

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() } ?: return@launch
            if (VersionCompare.isNewerVersion(latest.version, BuildConfig.VERSION_NAME)) {
                showUpdateDialog(latest)
            }
        }
    }

    private fun showUpdateDialog(latest: ReleaseInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_title))
            .setMessage(
                getString(
                    R.string.update_message,
                    latest.version.removePrefix("v"),
                    BuildConfig.VERSION_NAME
                )
            )
            .setPositiveButton(R.string.update_download) { _, _ -> downloadAndInstall(latest) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadAndInstall(latest: ReleaseInfo) {
        val url = latest.apkUrl
        if (url == null) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.pageUrl)))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            binding.statusText.text = "Allow installing unknown apps, then tap Download again"
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        binding.statusText.text = "Downloading update…"
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                ApkInstaller.download(this@SettingsActivity, url)
            }
            if (file == null) {
                binding.statusText.text = "Download failed"
                return@launch
            }
            binding.statusText.text = "Installing update…"
            startActivity(ApkInstaller.installIntent(this@SettingsActivity, file))
        }
    }
}
