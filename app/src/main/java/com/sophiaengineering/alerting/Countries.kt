package com.sophiaengineering.alerting

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** Un pays pour le sélecteur : ISO, nom localisé, indicatif, drapeau emoji. */
data class Country(
    val iso: String,
    val name: String,
    val dialCode: Int,
    val flag: String
) {
    /** Ex. "🇫🇷  France  (+33)". */
    fun display(): String = "$flag  $name  (+$dialCode)"
}

object Countries {

    /** Liste de tous les pays supportés, triés par nom. */
    fun all(): List<Country> {
        val util = PhoneNumberUtil.getInstance()
        return util.supportedRegions
            .mapNotNull { iso ->
                val code = util.getCountryCodeForRegion(iso)
                if (code == 0) null
                else Country(iso, countryName(iso), code, flagEmoji(iso))
            }
            .sortedBy { it.name }
    }

    fun indexOfIso(list: List<Country>, iso: String): Int =
        list.indexOfFirst { it.iso.equals(iso, ignoreCase = true) }.coerceAtLeast(0)

    private fun countryName(iso: String): String {
        val name = Locale("", iso).displayCountry
        return if (name.isBlank()) iso else name
    }

    /** Convertit un code ISO à deux lettres en drapeau emoji (indicateurs régionaux). */
    fun flagEmoji(iso: String): String {
        if (iso.length != 2) return ""
        val base = 0x1F1E6 // 🇦
        val first = base + (iso[0].uppercaseChar() - 'A')
        val second = base + (iso[1].uppercaseChar() - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
