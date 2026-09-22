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
     * Sigue estrictamente la estructura oficial: product, versionCode, versionName, apkUrl, releaseDate, notes.
     */
    fun parseVersionJson(jsonString: String): RemoteVersionInfo {
        val s = jsonString.trim().removePrefix("\uFEFF")
        val json = JSONObject(s)
        
        // Validar campos obligatorios según requisito 7
        if (!json.has("versionCode")) throw Exception("Falta versionCode")
        if (!json.has("versionName")) throw Exception("Falta versionName")
        if (!json.has("apkUrl")) throw Exception("Falta apkUrl")
        
        val vCode = json.getLong("versionCode")
        val vName = json.getString("versionName").trim()
        val apkUrl = json.getString("apkUrl").trim()
        
        return RemoteVersionInfo(
            product = json.optString("product", "ElQadrePF"),
            versionCode = vCode,
            versionName = vName,
            apkUrl = apkUrl,
            releaseDate = json.optString("releaseDate", ""),
            notes = json.optString("notes", "")
        )
    }

    /**
     * Genera el contenido de version.json con la estructura oficial.
     */
    fun generateVersionJson(
        versionCode: Long,
        versionName: String,
        apkUrl: String,
        notes: String = ""
    ): String {
        val json = JSONObject()
        json.put("product", "ElQadrePF")
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("apkUrl", apkUrl)
        
        // Fecha actual en formato ISO 8601
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val releaseDate = sdf.format(java.util.Date())
        
        json.put("releaseDate", releaseDate)
        json.put("notes", notes)
        
        return json.toString(2)
    }

    /**
     * Obtiene el versionCode y versionName de la APK actualmente instalada en el dispositivo.
     * Utiliza directamente BuildConfig generado por Gradle como fuente primaria de la versión real.
     */
    fun getLocalVersionInfo(context: Context): Pair<Long, String> {
        val bCode = com.example.BuildConfig.VERSION_CODE.toLong()
        val bName = com.example.BuildConfig.VERSION_NAME
        if (bCode > 0L && bName.isNotBlank()) {
            return Pair(bCode, bName)
        }
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val localVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val localVersionName = packageInfo.versionName ?: "1.0.0"
            Pair(localVersionCode, localVersionName)
        } catch (e: Exception) {
            Pair(1L, "1.0.0")
        }
    }

    /**
     * Compara los códigos de versión numéricos.
     * Solo permite actualización si el código remoto es estrictamente superior al local.
     */
    fun isUpdateAvailable(
        localVersionCode: Long,
        remoteVersionCode: Long
    ): Boolean {
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

        if (isUpdateAvailable(localCode, remoteInfo.versionCode)) {
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
