package com.sophiaengineering.alerting

/**
 * Comparaison de versions "x.y.z" — pure, sans dépendance Android (testable JVM).
 * Tolère un préfixe "v" et des suffixes non numériques (ex. "0.0-dev.5").
 */
object VersionCompare {

    /** Vrai si [remote] désigne une version strictement plus récente que [installed]. */
    fun isNewerVersion(remote: String, installed: String): Boolean {
        val r = parse(remote)
        val i = parse(installed)
        val n = maxOf(r.size, i.size)
        for (k in 0 until n) {
            val rv = r.getOrElse(k) { 0 }
            val iv = i.getOrElse(k) { 0 }
            if (rv != iv) return rv > iv
        }
        return false
    }

    /** "v1.10" -> [1, 10] ; "0.0-dev.5" -> [0, 0, 5]. */
    private fun parse(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
