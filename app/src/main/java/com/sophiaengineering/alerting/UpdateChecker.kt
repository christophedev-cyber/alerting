package com.sophiaengineering.alerting

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Infos de la dernière Release GitHub. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String?,
    val pageUrl: String
)

/**
 * Interroge l'API GitHub pour la dernière Release publiée du dépôt.
 * À exécuter hors du thread principal.
 */
object UpdateChecker {

    private const val OWNER = "christophedev-cyber"
    private const val REPO = "alerting"
    private const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val page = json.optString("html_url")

            var apk: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (idx in 0 until assets.length()) {
                    val asset = assets.getJSONObject(idx)
                    if (asset.optString("name").endsWith(".apk")) {
                        apk = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            ReleaseInfo(version = tag, apkUrl = apk, pageUrl = page)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
