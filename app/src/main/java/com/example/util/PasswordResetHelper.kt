package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PasswordResetRequest(
    val smsId: String,
    val uniqueKey: String,
    val senderAddress: String,
    val userPhone: String,
    val newPassword: String,
    val timestamp: Long
)

object PasswordResetHelper {

    private const val PREFS_NAME = "elqadre_password_reset_prefs"
    private const val KEY_PROCESSED_SMS = "processed_sms_ids"

    fun parsePasswordResetSms(smsId: String, senderAddress: String, body: String, timestamp: Long): PasswordResetRequest? {
        val trimmedBody = body.trim()
        val lines = trimmedBody.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3) return null

        val header = lines[0]
        if (!header.equals("CAMBIAR CONTRASEÑA", ignoreCase = true) &&
            !header.equals("CAMBIAR CONTRASENA", ignoreCase = true)) {
            return null
        }

        val phoneLine = lines[1]
        val cleanPhone = phoneLine.replace(Regex("[^0-9]"), "")
        if (cleanPhone.length < 6) return null

        val newPassword = lines[2]
        if (newPassword.isBlank()) return null

        val uniqueKey = "${smsId}_${cleanPhone}_${newPassword.hashCode()}_$timestamp"

        return PasswordResetRequest(
            smsId = smsId.ifBlank { System.currentTimeMillis().toString() },
            uniqueKey = uniqueKey,
            senderAddress = senderAddress,
            userPhone = phoneLine,
            newPassword = newPassword,
            timestamp = timestamp
        )
    }

    fun isSmsProcessed(context: Context, uniqueKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet()) ?: emptySet()
        return set.contains(uniqueKey)
    }

    fun markSmsAsProcessed(context: Context, uniqueKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(uniqueKey)
        prefs.edit().putStringSet(KEY_PROCESSED_SMS, currentSet).apply()
    }

    fun scanInboxForResetRequests(context: Context): List<PasswordResetRequest> {
        val results = mutableListOf<PasswordResetRequest>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("PasswordResetHelper", "Permiso READ_SMS no concedido. Omitiendo escaneo.")
            return results
        }

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")
            cursor?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val addressCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                val dateCol = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val smsId = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                    val address = if (addressCol >= 0) c.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol >= 0) c.getLong(dateCol) else 0L

                    val parsed = parsePasswordResetSms(smsId, address, body, date)
                    if (parsed != null && !isSmsProcessed(context, parsed.uniqueKey)) {
                        results.add(parsed)
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.w("PasswordResetHelper", "SecurityException scanning SMS inbox: ${se.message}")
        } catch (e: Exception) {
            Log.e("PasswordResetHelper", "Error scanning SMS inbox", e)
        }

        return results
    }

    fun cleanPhoneDigits(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    fun matchUserByPhone(users: List<User>, targetPhone: String): User? {
        val cleanTarget = cleanPhoneDigits(targetPhone)
        if (cleanTarget.isBlank()) return null

        return users.find { user ->
            val cleanUser = cleanPhoneDigits(user.telefono)
            cleanUser.isNotBlank() && (
                cleanUser == cleanTarget ||
                (cleanUser.length >= 8 && cleanTarget.length >= 8 && cleanUser.takeLast(8) == cleanTarget.takeLast(8))
            )
        }
    }

    fun generateQusuariosJsonString(users: List<User>, config: ConfiguracionGeneral?): String {
        val jsonObject = JSONObject()
        jsonObject.put("version", System.currentTimeMillis().toString())

        val uUrl = config?.urlUsuariosJson?.ifBlank { "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/qusuarios.json" } ?: "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/qusuarios.json"
        val cUrl = config?.urlCatalogoJson ?: ""
        val mUrl = config?.urlMercainvJson ?: ""
        val vUrl = config?.urlVersionJson ?: ""
        val ownerPhone = config?.telefonoDueno ?: ""
        val cajeroPhone = config?.telefonoCajero ?: ""
        val adminPhone = config?.telefonoAdmin ?: ""

        jsonObject.put("urlUsuariosJson", uUrl)
        jsonObject.put("urlCatalogoJson", cUrl)
        jsonObject.put("urlMercainvJson", mUrl)
        jsonObject.put("versionJsonUrl", vUrl)
        jsonObject.put("telefonoDueno", ownerPhone)
        jsonObject.put("telefonoCajero", cajeroPhone)
        jsonObject.put("telefonoAdmin", adminPhone)

        val usersArray = JSONArray()
        for (user in users) {
            val userObj = JSONObject()
            userObj.put("username", user.username)
            userObj.put("fullName", user.fullName)
            userObj.put("passwordHash", user.passwordHash)
            userObj.put("role", user.role.name)
            userObj.put("montoPorProducto", user.montoPorProducto)
            userObj.put("isActive", user.isActive)
            userObj.put("telefono", user.telefono)
            if (user.role == UserRole.DUENO) {
                val permissionsObj = JSONObject()
                permissionsObj.put("produccion", user.permisoProduccion)
                permissionsObj.put("mercancias", user.permisoMercancias)
                permissionsObj.put("personal", user.permisoPersonal)
                permissionsObj.put("controlNegocio", true)
                userObj.put("permissions", permissionsObj)
            }
            usersArray.put(userObj)
        }
        jsonObject.put("usuarios", usersArray)
        return jsonObject.toString(4)
    }

    fun saveQusuariosJsonToAppStorage(context: Context, jsonString: String): File? {
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "qusuarios.json")
            file.writeText(jsonString, Charsets.UTF_8)
            file
        } catch (e: Exception) {
            Log.e("PasswordResetHelper", "Error writing qusuarios.json", e)
            null
        }
    }
}
