package com.sophiaengineering.alerting

import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Validation et formatage de numéros de téléphone via libphonenumber.
 * Bibliothèque Java pure → utilisable et testable hors Android.
 */
object PhoneValidator {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /** Vrai si [national] est un numéro valide pour le pays [iso] (ex. "FR"). */
    fun isValid(iso: String, national: String): Boolean = try {
        util.isValidNumber(util.parse(national, iso))
    } catch (_: Exception) {
        false
    }

    /** Renvoie le numéro au format E.164 (+33612345678) ou null s'il est invalide. */
    fun toE164(iso: String, national: String): String? = try {
        val parsed = util.parse(national, iso)
        if (util.isValidNumber(parsed)) {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /** Indicatif téléphonique international du pays [iso] (ex. 33 pour "FR"). */
    fun dialCode(iso: String): Int = util.getCountryCodeForRegion(iso)
}
