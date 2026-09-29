package com.example.util

import android.content.Context
import com.example.BuildConfig
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Gestor del versionado de ElQadre según la regla:
 * MAYOR.MENOR.PARCHE
 * - MENOR: Identifica una nueva etapa de trabajo asociada a un rol (ej. 1 = DUEÑO, 2 = CAJERO).
 * - PARCHE: Identifica modificaciones sucesivas dentro de la misma etapa/rol.
 *
 * Fuente de verdad conectada directamente a BuildConfig y version.properties.
 */
object ProjectVersionManager {

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val currentVersionCode: Long
        get() = BuildConfig.VERSION_CODE.toLong()

    val currentStageRole: String
        get() = BuildConfig.CURRENT_STAGE_ROLE

    data class VersionParts(
        val major: Int,
        val minor: Int,
        val patch: Int
    )

    fun parseVersionParts(versionName: String = currentVersionName): VersionParts {
        val parts = versionName.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return VersionParts(major, minor, patch)
    }

    /**
     * Calcula la siguiente versión según el rol/etapa declarado:
     * - Mismo rol -> PARCHE + 1, VERSION_CODE + 1
     * - Nuevo rol -> MENOR + 1, PARCHE = 0, VERSION_CODE + 1
     */
    fun calculateNextVersion(
        currentMajor: Int,
        currentMinor: Int,
        currentPatch: Int,
        currentCode: Long,
        currentRole: String,
        targetRole: String
    ): Pair<String, Long> {
        val isSameRole = currentRole.trim().equals(targetRole.trim(), ignoreCase = true)
        val newMinor: Int
        val newPatch: Int

        if (isSameRole && currentMinor > 0) {
            newMinor = currentMinor
            newPatch = currentPatch + 1
        } else {
            newMinor = currentMinor + 1
            newPatch = 0
        }

        val newVersionName = "$currentMajor.$newMinor.$newPatch"
        val newVersionCode = currentCode + 1
        return Pair(newVersionName, newVersionCode)
    }

    /**
     * Genera la estructura oficial del archivo version.json
     */
    fun createVersionJson(
        productName: String = "ElQadrePF",
        versionCode: Long = currentVersionCode,
        versionName: String = currentVersionName,
        apkUrl: String = "https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v$currentVersionName/ElQadrePF.apk",
        notes: String = ""
    ): String {
        val json = JSONObject()
        json.put("product", productName)
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("apkUrl", apkUrl)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        json.put("releaseDate", sdf.format(Date()))
        json.put("notes", notes)

        return json.toString(2)
    }
}
