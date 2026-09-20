package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestor exclusivo para la verificación, descarga e instalación de actualizaciones de APK de ElQadre.
 * La URL del archivo de versión es fija y no depende de configuraciones locales ni externas.
 */
object ApkUpdateManager {

    /**
     * URL oficial fija en el código para el archivo version.json
     */
    const val OFFICIAL_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/version.json"

    data class RemoteVersionInfo(
        val product: String,
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val releaseDate: String = "",
        val notes: String = ""
    )

    sealed class VersionCheckResult {
        data class UpdateAvailable(
            val remoteInfo: RemoteVersionInfo,
            val localVersionCode: Long,
            val localVersionName: String
        ) : VersionCheckResult()

        data class AlreadyUpToDate(
            val localVersionCode: Long,
            val localVersionName: String,
            val remoteVersionCode: Long,
            val remoteVersionName: String
        ) : VersionCheckResult()

        data class Error(
            val message: String
        ) : VersionCheckResult()
    }

    /**
     * Comprueba si el dispositivo tiene conexión a Internet activa.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parsea la cadena JSON de versión devuelta por el servidor.
     */
    fun parseVersionJson(jsonString: String): RemoteVersionInfo {
        var s = jsonString.trim()
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim()
        }
        val json = JSONObject(s)
        val vCode = when {
            json.has("versionCode") -> json.optLong("versionCode", json.optString("versionCode", "0").toLongOrNull() ?: 0L)
            json.has("version_code") -> json.optLong("version_code", json.optString("version_code", "0").toLongOrNull() ?: 0L)
            json.has("versioncode") -> json.optLong("versioncode", json.optString("versioncode", "0").toLongOrNull() ?: 0L)
            json.has("code") -> json.optLong("code", json.optString("code", "0").toLongOrNull() ?: 0L)
            else -> 0L
        }
        val vName = (json.optString("versionName", "").ifBlank {
            json.optString("version_name", json.optString("version", ""))
        }).trim()
        val apkUrl = (json.optString("apkUrl", "").ifBlank {
            json.optString("apk_url", json.optString("downloadUrl", json.optString("download_url", json.optString("url", ""))))
        }).trim()

        return RemoteVersionInfo(
            product = json.optString("product", json.optString("name", "ElQadre")),
            versionCode = vCode,
            versionName = vName,
            apkUrl = apkUrl,
            releaseDate = json.optString("releaseDate", json.optString("release_date", "")),
            notes = json.optString("notes", json.optString("changelog", ""))
        )
    }

    /**
     * Obtiene el versionCode y versionName de la APK actualmente instalada en el dispositivo.
     */
    fun getLocalVersionInfo(context: Context): Pair<Long, String> {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val localVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val localVersionName = packageInfo.versionName ?: ""
        return Pair(localVersionCode, localVersionName)
    }

    /**
     * Compara los códigos de versión numéricos y de texto (SemVer).
     */
    fun isUpdateAvailable(
        localVersionCode: Long,
        localVersionName: String,
        remoteVersionCode: Long,
        remoteVersionName: String
    ): Boolean {
        if (remoteVersionCode > localVersionCode) return true
        if (isVersionNameHigher(remoteVersionName, localVersionName)) return true
        return false
    }

    /**
     * Compatibilidad semántica para nombres de versión (ej: 1.6 > 1.5.2)
     */
    fun isVersionNameHigher(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val cleanRemote = remote.removePrefix("v").removePrefix("V").trim()
        val cleanLocal = local.removePrefix("v").removePrefix("V").trim()
        val rParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
        if (rParts.isEmpty() || lParts.isEmpty()) return false
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun isUpdateAvailable(localVersionCode: Long, remoteVersionCode: Long): Boolean {
        return remoteVersionCode > localVersionCode
    }

    /**
     * Consulta el archivo version.json desde la URL fija y compara contra la APK instalada.
     */
    suspend fun checkUpdate(
        context: Context,
        url: String = OFFICIAL_VERSION_JSON_URL
    ): VersionCheckResult = withContext(Dispatchers.IO) {
        val (localCode, localName) = try {
            getLocalVersionInfo(context)
        } catch (e: Exception) {
            Pair(0L, "")
        }

        var jsonString: String? = null

        // Si es el repositorio oficial de GitHub, intentar primero la API de GitHub (evita el caché CDN de 5 min)
        if (url.contains("PeJotaCuba/BD-Qadre-PF", ignoreCase = true)) {
            try {
                val apiUrl = "https://api.github.com/repos/PeJotaCuba/BD-Qadre-PF/contents/version.json"
                val apiConn = URL(apiUrl).openConnection() as HttpURLConnection
                apiConn.connectTimeout = 7000
                apiConn.readTimeout = 7000
                apiConn.useCaches = false
                apiConn.setRequestProperty("Accept", "application/vnd.github.v3.raw")
                apiConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")
                if (apiConn.responseCode == HttpURLConnection.HTTP_OK) {
                    jsonString = apiConn.inputStream.bufferedReader().use { it.readText() }
                }
                apiConn.disconnect()
            } catch (_: Exception) {
                // Fallback automático a la URL de descarga directa
            }
        }

        if (jsonString == null) {
            try {
                val cacheBuster = "_ts=${System.currentTimeMillis()}"
                var currentUrl = if (url.contains("?")) "$url&$cacheBuster" else "$url?$cacheBuster"
                var conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.useCaches = false
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                conn.setRequestProperty("Pragma", "no-cache")
                conn.setRequestProperty("Expires", "0")
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")

                var redirectCount = 0
                while (redirectCount < 5 && (conn.responseCode in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    ))) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) break
                    conn.disconnect()
                    currentUrl = if (location.contains("?")) "$location&$cacheBuster" else "$location?$cacheBuster"
                    conn = URL(currentUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.useCaches = false
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                    conn.setRequestProperty("Pragma", "no-cache")
                    conn.setRequestProperty("Expires", "0")
                    conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")
                    redirectCount++
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    return@withContext VersionCheckResult.Error(
                        "Error de conexión con el servidor de versiones (código HTTP ${conn.responseCode})."
                    )
                }
            } catch (e: Exception) {
                return@withContext VersionCheckResult.Error(
                    "Error de conexión: No se pudo verificar la actualización. Compruebe su conexión a Internet (WiFi o Datos Móviles)."
                )
            }
        }

        val remoteInfo = try {
            parseVersionJson(jsonString)
        } catch (e: Exception) {
            return@withContext VersionCheckResult.Error(
                "Error al procesar la información de la versión remota recibida."
            )
        }

        if (remoteInfo.versionCode <= 0L && remoteInfo.versionName.isBlank()) {
            return@withContext VersionCheckResult.Error(
                "Información de versión inválida recibida del servidor."
            )
        }

        if (isUpdateAvailable(localCode, localName, remoteInfo.versionCode, remoteInfo.versionName)) {
            VersionCheckResult.UpdateAvailable(
                remoteInfo = remoteInfo,
                localVersionCode = localCode,
                localVersionName = localName
            )
        } else {
            VersionCheckResult.AlreadyUpToDate(
                localVersionCode = localCode,
                localVersionName = localName,
                remoteVersionCode = remoteInfo.versionCode,
                remoteVersionName = remoteInfo.versionName
            )
        }
    }

    /**
     * Descarga la APK remota reportando progreso mediante un callback.
     * Maneja redireccionamientos automáticos (como los de GitHub Releases / Raw).
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            var currentUrl = apkUrl.trim()
            var conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")

            var redirectCount = 0
            while (redirectCount < 5 && (conn.responseCode in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                ))) {
                val location = conn.getHeaderField("Location")
                if (location.isNullOrBlank()) break
                conn.disconnect()
                currentUrl = location
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")
                redirectCount++
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val fileLength = conn.contentLength
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val outputFile = File(downloadDir, "ElQadre_update.apk")
            if (outputFile.exists()) outputFile.delete()

            val input = conn.inputStream
            val output = FileOutputStream(outputFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength)
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Abre el instalador del sistema operativo de Android mediante FileProvider e Intent.ACTION_VIEW.
     */
    fun launchApkInstall(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
