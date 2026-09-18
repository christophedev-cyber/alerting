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
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
        setupCountrySpinner()
        loadIntoUi(store.load())
        attachListeners()

        maybeRequestNotificationPermission()
        checkForUpdate()
    }

    private fun setupCountrySpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            countries.map { it.display() }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.countrySpinner.adapter = adapter
    }

    private fun attachListeners() {
        binding.testButton.setOnClickListener { onTestClicked() }
        binding.startButton.setOnClickListener { onStartClicked() }
        binding.stopButton.setOnClickListener { onStopClicked() }

        binding.countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                binding.dialCodeText.text = "+${countries[position].dialCode}"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

    private fun loadIntoUi(s: AlertSettings) {
        binding.senderEmail.setText(s.senderEmail)
        binding.appPassword.setText(s.appPassword)
        binding.recipients.setText(s.recipients)
        binding.lowBatterySwitch.isChecked = s.lowBatteryAlertEnabled
        binding.lowBatteryThreshold.setText(s.lowBatteryThreshold.toString())
        binding.smsSwitch.isChecked = s.smsAlertEnabled
        val pos = Countries.indexOfIso(countries, s.phoneCountryIso)
        binding.countrySpinner.setSelection(pos)
        binding.dialCodeText.text = "+${countries[pos].dialCode}"
        binding.phoneNumber.setText(s.phoneNumber)
        binding.smtpHost.setText(s.smtpHost)
        binding.smtpPort.setText(s.smtpPort.toString())
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
        val country = countries[binding.countrySpinner.selectedItemPosition]
        return AlertSettings(
            senderEmail = binding.senderEmail.text.toString(),
            appPassword = binding.appPassword.text.toString(),
            recipients = binding.recipients.text.toString(),
            smtpHost = binding.smtpHost.text.toString().ifBlank { SettingsStore.DEFAULT_HOST },
            smtpPort = binding.smtpPort.text.toString().toIntOrNull() ?: 0,
            lowBatteryAlertEnabled = binding.lowBatterySwitch.isChecked,
            lowBatteryThreshold = binding.lowBatteryThreshold.text.toString().toIntOrNull() ?: 0,
            smsAlertEnabled = binding.smsSwitch.isChecked,
            phoneCountryIso = country.iso,
            phoneNumber = binding.phoneNumber.text.toString(),
            monitoringEnabled = true
        )
    }

    /** Validation des réglages + du numéro selon le pays (via libphonenumber). */
    private fun validate(settings: AlertSettings): List<String> {
        val errors = settings.validationErrors().toMutableList()
        if (settings.smsAlertEnabled && settings.phoneNumber.isNotBlank() &&
            !PhoneValidator.isValid(settings.phoneCountryIso, settings.phoneNumber)
        ) {
            errors.add("Invalid phone number for the selected country")
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

    /** Envoie un email de test avec les valeurs actuellement saisies. */
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

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

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

    /**
     * Télécharge l'APK et ouvre directement l'écran d'installation Android.
     * Repli sur l'ouverture navigateur si l'APK n'est pas disponible.
     */
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
