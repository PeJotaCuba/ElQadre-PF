package com.example.ui.screens.inicio

import android.content.Context
import android.os.Environment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SuperAdminRecord(
    val dvc: String,
    val usuario: String,
    val passwordHash: String,
    val estado: String
)

sealed class SuperAdminValidationResult {
    data class Authorized(val adminRecord: SuperAdminRecord) : SuperAdminValidationResult()
    object DeviceNotConfigured : SuperAdminValidationResult()
    object DeviceInactive : SuperAdminValidationResult()
    data class ConnectionOrFormatError(val message: String) : SuperAdminValidationResult()
}

object SuperAdminAuthService {
    const val SUPERADMIN_JSON_URL =
        "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/QDF_superadmin.json"

    private const val PREFS_NAME = "super_admin_prefs"
    private const val KEY_CONFIGURADO = "SUPER_ADMIN_CONFIGURADO"

    fun isSuperAdminConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CONFIGURADO, false)) return true

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val jsonFile = File(downloadsDir, "QDF_superadmin.json")
        if (jsonFile.exists() && jsonFile.length() > 0) {
            prefs.edit().putBoolean(KEY_CONFIGURADO, true).apply()
            return true
        }
        return false
    }

    fun setSuperAdminConfigured(context: Context, configured: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CONFIGURADO, configured).apply()
    }

    fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun verifyDeviceAuthorization(context: Context, currentDvc: String): SuperAdminValidationResult =
        withContext(Dispatchers.IO) {
            val normalizedCurrentDvc = if (currentDvc.startsWith("DVC-", ignoreCase = true)) {
                currentDvc.uppercase()
            } else {
                "DVC-${currentDvc.uppercase()}"
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL(SUPERADMIN_JSON_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext SuperAdminValidationResult.ConnectionOrFormatError(
                        "No fue posible validar la autorización (Servidor respondió con código $responseCode)."
                    )
                }

                val jsonContent = connection.inputStream.bufferedReader().use { it.readText() }
                if (jsonContent.isBlank()) {
                    return@withContext SuperAdminValidationResult.ConnectionOrFormatError(
                        "El archivo remoto de autorización está vacío o no está disponible."
                    )
                }

                val rootJson = try {
                    JSONObject(jsonContent)
                } catch (e: Exception) {
                    return@withContext SuperAdminValidationResult.ConnectionOrFormatError(
                        "El formato del archivo remoto de autorización es inválido."
                    )
                }

                if (!rootJson.has("superAdmins")) {
                    return@withContext SuperAdminValidationResult.ConnectionOrFormatError(
                        "Estructura del archivo de autorización no válida (falta la lista superAdmins)."
                    )
                }

                val superAdminsArray = rootJson.getJSONArray("superAdmins")
                if (superAdminsArray.length() == 0) {
                    return@withContext SuperAdminValidationResult.DeviceNotConfigured
                }

                var matchedRecord: SuperAdminRecord? = null

                for (i in 0 until superAdminsArray.length()) {
                    val item = superAdminsArray.getJSONObject(i)
                    val rawDvc = item.optString("dvc", "")
                    val normalizedItemDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
                        rawDvc.uppercase()
                    } else {
                        "DVC-${rawDvc.uppercase()}"
                    }

                    if (normalizedItemDvc.equals(normalizedCurrentDvc, ignoreCase = true)) {
                        val estado = item.optString("estado", "").trim().uppercase()
                        val usuario = item.optString("usuario", "").trim()
                        val passwordHash = item.optString("passwordHash", "").trim().lowercase()

                        matchedRecord = SuperAdminRecord(
                            dvc = normalizedItemDvc,
                            usuario = usuario,
                            passwordHash = passwordHash,
                            estado = estado
                        )
                        break
                    }
                }

                if (matchedRecord == null) {
                    // DVC no presente en el JSON remoto -> Dispositivo no configurado
                    return@withContext SuperAdminValidationResult.DeviceNotConfigured
                }

                if (matchedRecord.estado != "ACTIVO") {
                    return@withContext SuperAdminValidationResult.DeviceInactive
                }

                // Guardar localmente que está configurado, sincronizando el estado con el servidor
                setSuperAdminConfigured(context, true)
                return@withContext SuperAdminValidationResult.Authorized(matchedRecord)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext SuperAdminValidationResult.ConnectionOrFormatError(
                    "No fue posible validar la autorización. Compruebe su conexión a Internet e intente nuevamente."
                )
            } finally {
                connection?.disconnect()
            }
        }
}
