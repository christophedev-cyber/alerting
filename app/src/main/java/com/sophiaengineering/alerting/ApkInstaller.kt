package com.sophiaengineering.alerting

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Télécharge un APK et lance l'écran d'installation Android.
 * [download] est à exécuter hors du thread principal.
 */
object ApkInstaller {

    /** Télécharge [url] dans le cache et renvoie le fichier, ou null en cas d'échec. */
    fun download(context: Context, url: String): File? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299) return null
            val file = File(context.cacheDir, "update.apk")
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Intent ouvrant l'installeur système pour [file] (partagé via FileProvider). */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
