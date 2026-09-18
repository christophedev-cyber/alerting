package com.sophiaengineering.alerting

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistance des réglages. Le mot de passe d'application est chiffré au repos
 * via EncryptedSharedPreferences.
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): AlertSettings = AlertSettings(
        senderEmail = prefs.getString(KEY_SENDER, "").orEmpty(),
        appPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        recipients = prefs.getString(KEY_RECIPIENTS, "").orEmpty(),
        smtpHost = prefs.getString(KEY_SMTP_HOST, DEFAULT_HOST).orEmpty().ifBlank { DEFAULT_HOST },
        smtpPort = prefs.getInt(KEY_SMTP_PORT, DEFAULT_PORT),
        lowBatteryAlertEnabled = prefs.getBoolean(KEY_LOW_BAT_ENABLED, false),
        lowBatteryThreshold = prefs.getInt(KEY_LOW_BAT_THRESHOLD, DEFAULT_THRESHOLD),
        smsAlertEnabled = prefs.getBoolean(KEY_SMS_ENABLED, false),
        phoneCountryIso = prefs.getString(KEY_PHONE_ISO, DEFAULT_COUNTRY_ISO).orEmpty().ifBlank { DEFAULT_COUNTRY_ISO },
        phoneNumber = prefs.getString(KEY_PHONE_NUMBER, "").orEmpty(),
        monitoringEnabled = prefs.getBoolean(KEY_ENABLED, false)
    )

    fun save(settings: AlertSettings) {
        prefs.edit()
            .putString(KEY_SENDER, settings.senderEmail.trim())
            .putString(KEY_PASSWORD, settings.appPassword.trim())
            .putString(KEY_RECIPIENTS, settings.recipients.trim())
            .putString(KEY_SMTP_HOST, settings.smtpHost.trim())
            .putInt(KEY_SMTP_PORT, settings.smtpPort)
            .putBoolean(KEY_LOW_BAT_ENABLED, settings.lowBatteryAlertEnabled)
            .putInt(KEY_LOW_BAT_THRESHOLD, settings.lowBatteryThreshold)
            .putBoolean(KEY_SMS_ENABLED, settings.smsAlertEnabled)
            .putString(KEY_PHONE_ISO, settings.phoneCountryIso.trim())
            .putString(KEY_PHONE_NUMBER, settings.phoneNumber.trim())
            .putBoolean(KEY_ENABLED, settings.monitoringEnabled)
            .apply()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "alerting_settings"
        private const val KEY_SENDER = "sender_email"
        private const val KEY_PASSWORD = "app_password"
        private const val KEY_RECIPIENTS = "recipients"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_LOW_BAT_ENABLED = "low_battery_enabled"
        private const val KEY_LOW_BAT_THRESHOLD = "low_battery_threshold"
        private const val KEY_SMS_ENABLED = "sms_enabled"
        private const val KEY_PHONE_ISO = "phone_country_iso"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_ENABLED = "monitoring_enabled"

        const val DEFAULT_HOST = "smtp.gmail.com"
        const val DEFAULT_PORT = 587
        const val DEFAULT_THRESHOLD = 20
        const val DEFAULT_COUNTRY_ISO = "FR"
    }
}
