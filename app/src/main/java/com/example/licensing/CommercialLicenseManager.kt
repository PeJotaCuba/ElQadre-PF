package com.example.licensing

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.example.util.toSha256
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

enum class CommercialStatus {
    SIN_AUTORIZACION,
    PRUEBA_ACTIVA,
    PRUEBA_VENCIDA,
    LICENCIA_ACTIVA,
    LICENCIA_VENCIDA,
    LICENCIA_REVOCADA
}

data class CommercialLicenseInfo(
    val status: CommercialStatus = CommercialStatus.SIN_AUTORIZACION,
    val businessName: String = "",
    val phoneNumber: String = "",
    val licenseType: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val endTimestamp: Long = 0L,
    val lastCheckedTimestamp: Long = 0L,
    val urlUsuarios: String = "",
    val businessCode: String = "",
    val motivoRevocacion: String = "",
    val fechaRevocacion: String = "",
    val token: String = "",
    val ownerUsername: String = "",
    val ownerPassword: String = ""
)

sealed class LicenseUpdateResult {
    data class Success(val newStatus: CommercialStatus, val message: String) : LicenseUpdateResult()
    data class NoChange(val currentStatus: CommercialStatus, val message: String) : LicenseUpdateResult()
    data class Error(val message: String) : LicenseUpdateResult()
}

class CommercialLicenseManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _licenseInfo = MutableStateFlow(loadLicenseInfo())
    val licenseInfo: StateFlow<CommercialLicenseInfo> = _licenseInfo.asStateFlow()

    companion object {
        const val URL_PRUEBA =
            "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_preuba.json"
        const val URL_LICENCIAS =
            "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_licencias.json"

        private const val PREFS_NAME = "elqadre_commercial_license_prefs"
        private const val KEY_STATUS = "commercial_status"
        private const val KEY_BUSINESS_NAME = "business_name"
        private const val KEY_PHONE = "phone_number"
        private const val KEY_LICENSE_TYPE = "license_type"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_END_DATE = "end_date"
        private const val KEY_END_TIMESTAMP = "end_timestamp"
        private const val KEY_LAST_CHECKED = "last_checked"
        private const val KEY_URL_USUARIOS = "url_usuarios"
        private const val KEY_BUSINESS_CODE = "business_code"
        private const val KEY_MOTIVO_REVOCACION = "motivo_revocacion"
        private const val KEY_FECHA_REVOCACION = "fecha_revocacion"
        private const val KEY_TOKEN = "commercial_token"

        const val OFFLINE_GRACE_PERIOD_MILLIS = 7L * 24L * 60L * 60L * 1000L // 7 días completos sin comprobación

        @Volatile
        private var INSTANCE: CommercialLicenseManager? = null

        fun getInstance(context: Context): CommercialLicenseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CommercialLicenseManager(context).also { INSTANCE = it }
            }
        }

        fun parseConfirmationSms(body: String): ParsedConfirmationSms? {
            try {
                if (body.isBlank()) return null
                val normalizedBody = body.replace("\r\n", "\n").replace('\r', '\n')
                val rawLines = normalizedBody.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (rawLines.isEmpty()) return null

                var businessName = ""
                var dueno = ""
                var mp = ""
                var ma = ""
                var dvc = ""
                var businessCode = ""
                var token = ""
                var ownerUsername = ""
                var ownerPassword = ""
                var startDateStr = ""
                var endDateStr = ""
                var estado = ""
                var ciudad = ""

                var isElQadreSms = false

                for (line in rawLines) {
                    val upperLine = line.uppercase()
                    if (upperLine.contains("CONFIRMACIÓN PRUEBA ELQADRE") ||
                        upperLine.contains("CONFIRMACION PRUEBA ELQADRE") ||
                        upperLine.contains("CONFIRMACIÓN LICENCIA ELQADRE") ||
                        upperLine.contains("CONFIRMACION LICENCIA ELQADRE") ||
                        upperLine.contains("CONFIRMACION DE PRUEBA ELQADRE") ||
                        upperLine.contains("CONFIRMACIÓN DE PRUEBA ELQADRE") ||
                        upperLine.contains("CONFIRMACION DE LICENCIA ELQADRE") ||
                        upperLine.contains("CONFIRMACIÓN DE LICENCIA ELQADRE")
                    ) {
                        isElQadreSms = true
                        val headerName = when {
                            upperLine.contains("CONFIRMACIÓN PRUEBA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACIÓN PRUEBA ELQADRE") + "CONFIRMACIÓN PRUEBA ELQADRE".length)
                            upperLine.contains("CONFIRMACION PRUEBA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACION PRUEBA ELQADRE") + "CONFIRMACION PRUEBA ELQADRE".length)
                            upperLine.contains("CONFIRMACIÓN LICENCIA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACIÓN LICENCIA ELQADRE") + "CONFIRMACIÓN LICENCIA ELQADRE".length)
                            upperLine.contains("CONFIRMACION LICENCIA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACION LICENCIA ELQADRE") + "CONFIRMACION LICENCIA ELQADRE".length)
                            upperLine.contains("CONFIRMACIÓN DE PRUEBA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACIÓN DE PRUEBA ELQADRE") + "CONFIRMACIÓN DE PRUEBA ELQADRE".length)
                            upperLine.contains("CONFIRMACION DE PRUEBA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACION DE PRUEBA ELQADRE") + "CONFIRMACION DE PRUEBA ELQADRE".length)
                            upperLine.contains("CONFIRMACIÓN DE LICENCIA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACIÓN DE LICENCIA ELQADRE") + "CONFIRMACIÓN DE LICENCIA ELQADRE".length)
                            upperLine.contains("CONFIRMACION DE LICENCIA ELQADRE") -> line.substring(upperLine.indexOf("CONFIRMACION DE LICENCIA ELQADRE") + "CONFIRMACION DE LICENCIA ELQADRE".length)
                            else -> ""
                        }.trim().trimStart(':', '-', '(', '<', '[').trimEnd(')', '>', ']').trim()
                        if (headerName.isNotEmpty() && businessName.isEmpty()) {
                            businessName = headerName
                        }
                        continue
                    }

                    val colonIdx = line.indexOf(':')
                    val equalsIdx = if (colonIdx == -1) line.indexOf('=') else -1
                    val sepIdx = if (colonIdx != -1) colonIdx else equalsIdx
                    if (sepIdx == -1) continue

                    val prefix = line.substring(0, sepIdx).trim().uppercase()
                    val value = line.substring(sepIdx + 1).trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")

                    when {
                        prefix == "DUEÑO" || prefix == "DUENO" || prefix == "PROPIETARIO" || prefix == "TITULAR" || prefix == "NOMBRE DUEÑO" || prefix == "NOMBRE DUENO" || prefix == "NOMBRE DEL DUEÑO" || prefix == "NOMBRE DEL DUENO" || prefix == "CLIENTE" -> {
                            if (value.isNotEmpty()) dueno = value
                        }
                        prefix == "MP" || prefix == "MÓVIL" || prefix == "MOVIL" || prefix == "MÓVIL PRINCIPAL" || prefix == "MOVIL PRINCIPAL" || prefix == "TELÉFONO" || prefix == "TELEFONO" || prefix == "TEL" || prefix == "TELÉFONO PRINCIPAL" || prefix == "TELEFONO PRINCIPAL" || prefix == "CELULAR" || prefix == "CEL" -> {
                            if (value.isNotEmpty()) mp = value
                        }
                        prefix == "MA" || prefix == "MÓVIL ALT" || prefix == "MOVIL ALT" || prefix == "MÓVIL ALTERNATIVO" || prefix == "MOVIL ALTERNATIVO" || prefix == "TELÉFONO ALT" || prefix == "TELEFONO ALT" || prefix == "TEL ALT" || prefix == "TELÉFONO ALTERNATIVO" || prefix == "TELEFONO ALTERNATIVO" || prefix == "CEL ALT" -> {
                            ma = value
                        }
                        prefix == "DVC" || prefix == "DISPOSITIVO" || prefix == "ID DISPOSITIVO" || prefix == "DEVICE" || prefix == "DISPOSITIVO AUTORIZADO" || prefix == "ID DVC" || prefix == "CODIGO DISPOSITIVO" || prefix == "CÓDIGO DISPOSITIVO" -> {
                            if (value.isNotEmpty()) dvc = value
                        }
                        prefix == "NEGOCIO" || prefix == "CÓDIGO" || prefix == "CODIGO" || prefix == "CÓDIGO NEGOCIO" || prefix == "CODIGO NEGOCIO" || prefix == "NÚMERO" || prefix == "NUMERO" || prefix == "NÚMERO NEGOCIO" || prefix == "NUMERO NEGOCIO" || prefix == "NO" || prefix == "NO." || prefix == "NUM" || prefix == "ID NEGOCIO" -> {
                            if (value.isNotEmpty()) {
                                if (value.all { it.isDigit() } || value.length <= 6) {
                                    businessCode = value
                                } else if (businessName.isEmpty()) {
                                    businessName = value
                                } else {
                                    businessCode = value
                                }
                            }
                        }
                        prefix == "NOMBRE NEGOCIO" || prefix == "NOMBRE DEL NEGOCIO" || prefix == "NOMBRE" || prefix == "LOCAL" || prefix == "ESTABLECIMIENTO" -> {
                            if (value.isNotEmpty()) businessName = value
                        }
                        prefix == "TOKEN" || prefix == "TOKEN ACCESO" || prefix == "TOKEN_ACCESO" || prefix == "TOKEN COMERCIAL" || prefix == "TOKEN ENTRADA" || prefix == "TOKEN DE ENTRADA" || prefix == "TOKEN PRUEBA" || prefix == "TOKEN LICENCIA" -> {
                            if (value.isNotEmpty()) token = value
                        }
                        prefix == "USUARIO" || prefix == "USER" || prefix == "USUARIO INICIAL" || prefix == "CUENTA" || prefix == "USUARIO DUEÑO" || prefix == "USUARIO DUENO" || prefix == "USUARIO DUEÑO INICIAL" || prefix == "USUARIO DUENO INICIAL" || prefix == "LOGIN" || prefix == "USER DUEÑO" || prefix == "USER DUENO" || prefix == "USUARIO ACCESO" -> {
                            if (value.isNotEmpty()) ownerUsername = value
                        }
                        prefix == "CONTRASEÑA" || prefix == "CONTRASENA" || prefix == "PASSWORD" || prefix == "CLAVE" || prefix == "CONTRASEÑA INICIAL" || prefix == "CONTRASENA INICIAL" || prefix == "PASS" || prefix == "CLAVE INICIAL" || prefix == "PIN" || prefix == "CONTRASEÑA ACCESO" || prefix == "CONTRASENA ACCESO" -> {
                            if (value.isNotEmpty()) ownerPassword = value
                        }
                        prefix == "INICIO" || prefix == "FECHA INICIO" || prefix == "FECHA_INICIO" || prefix == "DESDE" || prefix == "FECHA DE INICIO" || prefix == "EMISIÓN" || prefix == "EMISION" || prefix == "FECHA" || prefix == "FECHA EMISIÓN" || prefix == "FECHA EMISION" -> {
                            if (value.isNotEmpty()) startDateStr = value
                        }
                        prefix == "VENCIMIENTO" || prefix == "FIN" || prefix == "FECHA FIN" || prefix == "FECHA_FIN" || prefix == "HASTA" || prefix == "FECHA VENCIMIENTO" || prefix == "FECHA DE VENCIMIENTO" || prefix == "EXPIRACIÓN" || prefix == "EXPIRACION" || prefix == "VENCE" || prefix == "FECHA VENCE" || prefix == "VALIDO HASTA" || prefix == "VÁLIDO HASTA" || prefix == "HABILITADA HASTA" || prefix == "HABILITADO HASTA" -> {
                            if (value.isNotEmpty()) endDateStr = value
                        }
                        prefix == "ESTADO" || prefix == "STATUS" || prefix == "CONDICION" || prefix == "CONDICIÓN" -> {
                            if (value.isNotEmpty()) estado = value
                        }
                        prefix == "CIUDAD" || prefix == "MUNICIPIO" || prefix == "PROVINCIA" || prefix == "UBICACION" || prefix == "UBICACIÓN" -> {
                            if (value.isNotEmpty()) ciudad = value
                        }
                    }
                }

                if (!isElQadreSms && !body.uppercase().contains("ELQADRE")) {
                    return null
                }

                if (estado.isNotBlank() && (estado.equals("REVOCADA", ignoreCase = true) || estado.equals("REVOCADO", ignoreCase = true) || estado.equals("INACTIVO", ignoreCase = true))) {
                    return null
                }

                if (businessName.isBlank() && businessCode.isNotBlank()) {
                    businessName = "Negocio $businessCode"
                }
                if (businessCode.isBlank()) {
                    businessCode = "001"
                }
                if (dueno.isBlank()) {
                    dueno = if (ownerUsername.isNotBlank()) ownerUsername.replaceFirstChar { it.uppercase() } else "Dueño $businessName"
                }

                if (startDateStr.isBlank()) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    startDateStr = sdf.format(Date())
                }

                if (dvc.isBlank() || token.isBlank() || ownerUsername.isBlank() || ownerPassword.isBlank() || endDateStr.isBlank()) {
                    return null
                }

                return ParsedConfirmationSms(
                    businessName = businessName,
                    dueno = dueno,
                    mp = mp,
                    ma = ma,
                    dvc = dvc,
                    businessCode = businessCode,
                    token = token,
                    ownerUsername = ownerUsername,
                    ownerPassword = ownerPassword,
                    startDateStr = startDateStr,
                    endDateStr = endDateStr
                )
            } catch (_: Exception) {
                return null
            }
        }

        fun parseDateToTimestamp(dateStr: String, isEndOfDay: Boolean): Long {
            if (dateStr.isBlank()) return 0L
            val trimmed = dateStr.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")

            if (trimmed.equals("PERMANENTE", ignoreCase = true) ||
                trimmed.equals("INDEFINIDO", ignoreCase = true) ||
                trimmed.equals("ILIMITADO", ignoreCase = true)
            ) {
                return Long.MAX_VALUE
            }

            // Normalización para fechas en español (ej: "24 de septiembre de 2026")
            var normalized = trimmed.lowercase(Locale.ROOT)
                .replace("a las", " ")
                .replace("hrs", "")
                .replace("horas", "")
                .replace("hs", "")
                .replace(" de ", "/")
                .replace(" del ", "/")
                .replace(" de", "/")
                .trim()

            val spanishMonths = listOf(
                "septiembre" to "09", "setiembre" to "09", "sept" to "09", "sep" to "09",
                "enero" to "01", "ene" to "01",
                "febrero" to "02", "feb" to "02",
                "marzo" to "03", "mar" to "03",
                "abril" to "04", "abr" to "04",
                "mayo" to "05", "may" to "05",
                "junio" to "06", "jun" to "06",
                "julio" to "07", "jul" to "07",
                "agosto" to "08", "ago" to "08",
                "octubre" to "10", "oct" to "10",
                "noviembre" to "11", "nov" to "11",
                "diciembre" to "12", "dic" to "12"
            )

            for ((mName, mNum) in spanishMonths) {
                if (normalized.contains(mName)) {
                    normalized = normalized.replace(mName, mNum)
                    break
                }
            }

            // Patrones ordenados estrictamente (Día primero, luego Año primero) con isLenient = false
            // para evitar que "24-09-2026" se interprete erróneamente como año 24 d.C.
            val patterns = listOf(
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd-MM-yyyy",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd",
                "dd/MM/yy HH:mm:ss",
                "dd/MM/yy HH:mm",
                "dd/MM/yy",
                "dd-MM-yy HH:mm:ss",
                "dd-MM-yy HH:mm",
                "dd-MM-yy",
                "d/M/yyyy HH:mm:ss",
                "d/M/yyyy HH:mm",
                "d/M/yyyy",
                "d-M-yyyy HH:mm:ss",
                "d-M-yyyy HH:mm",
                "d-M-yyyy"
            )

            val candidates = if (normalized != trimmed) listOf(trimmed, normalized) else listOf(trimmed)

            for (str in candidates) {
                for (pattern in patterns) {
                    try {
                        val sdf = SimpleDateFormat(pattern, Locale.US)
                        sdf.isLenient = false
                        val date = sdf.parse(str)
                        if (date != null) {
                            val cal = Calendar.getInstance()
                            cal.time = date
                            val year = cal.get(Calendar.YEAR)
                            val adjustedYear = if (year in 0..99) year + 2000 else year
                            cal.set(Calendar.YEAR, adjustedYear)

                            if (adjustedYear in 2000..2100) {
                                val hasTime = pattern.contains("HH") || pattern.contains("H") || pattern.contains("mm")
                                if (isEndOfDay && !hasTime) {
                                    cal.set(Calendar.HOUR_OF_DAY, 23)
                                    cal.set(Calendar.MINUTE, 59)
                                    cal.set(Calendar.SECOND, 59)
                                    cal.set(Calendar.MILLISECOND, 999)
                                }
                                return cal.timeInMillis
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Prueba de timestamp numérico
            try {
                val num = trimmed.toLongOrNull()
                if (num != null) {
                    val ts = if (num < 10000000000L) num * 1000 else num
                    if (ts > 946684800000L) { // > año 2000
                        return ts
                    }
                }
            } catch (_: Exception) {}

            return 0L
        }

        fun evaluateCommercialRecord(
            matchedObject: JSONObject?,
            isTrial: Boolean,
            currentInfo: CommercialLicenseInfo,
            now: Long = System.currentTimeMillis()
        ): CommercialLicenseInfo {
            if (matchedObject == null) {
                return if (isTrial) {
                    currentInfo.copy(
                        status = CommercialStatus.PRUEBA_VENCIDA,
                        lastCheckedTimestamp = now
                    )
                } else {
                    currentInfo.copy(
                        status = CommercialStatus.LICENCIA_VENCIDA,
                        lastCheckedTimestamp = now
                    )
                }
            }

            val estado = matchedObject.optString("estado", "").trim().uppercase()
            val nombreNegocio = matchedObject.optString("nombreNegocio", "").trim()
            val numeroMovil = (matchedObject.optString("numeroMovil", "").ifBlank {
                matchedObject.optString("telefono", "")
            }).trim()
            val fechaInicio = (matchedObject.optString("fechaInicio", "").ifBlank {
                matchedObject.optString("fecha_inicio", "")
            }).trim()
            val fechaFin = (matchedObject.optString("fechaFin", "").ifBlank {
                matchedObject.optString("fecha_fin", "")
            }).trim()
            val tipoLicencia = (matchedObject.optString("tipoLicencia", "").ifBlank {
                matchedObject.optString("tipo", "COMERCIAL")
            }).trim()
            val urlUsuarios = matchedObject.optString("urlUsuarios", matchedObject.optString("url_usuarios", "")).trim()
            val businessCode = matchedObject.optString("codigo", matchedObject.optString("codigoNegocio", "")).trim()
            val motivoRevocacion = (matchedObject.optString("motivoRevocacion", "").ifBlank {
                matchedObject.optString("motivo", "")
            }).trim()
            val fechaRevocacion = (matchedObject.optString("fechaRevocacion", "").ifBlank {
                matchedObject.optString("fecha_revocacion", "")
            }).trim()

            val endTimestamp = parseDateToTimestamp(fechaFin, isEndOfDay = true)

            if (estado == "REVOCADA" || estado == "REVOCADO") {
                return currentInfo.copy(
                    status = CommercialStatus.LICENCIA_REVOCADA,
                    businessName = nombreNegocio.ifBlank { currentInfo.businessName },
                    phoneNumber = numeroMovil.ifBlank { currentInfo.phoneNumber },
                    licenseType = tipoLicencia.ifBlank { currentInfo.licenseType },
                    startDate = fechaInicio.ifBlank { currentInfo.startDate },
                    endDate = fechaFin.ifBlank { currentInfo.endDate },
                    endTimestamp = endTimestamp,
                    lastCheckedTimestamp = now,
                    urlUsuarios = urlUsuarios.ifBlank { currentInfo.urlUsuarios },
                    businessCode = businessCode.ifBlank { currentInfo.businessCode },
                    motivoRevocacion = motivoRevocacion,
                    fechaRevocacion = fechaRevocacion
                )
            }

            if (estado != "ACTIVO") {
                return if (isTrial) {
                    currentInfo.copy(
                        status = CommercialStatus.PRUEBA_VENCIDA,
                        lastCheckedTimestamp = now
                    )
                } else {
                    currentInfo.copy(
                        status = CommercialStatus.LICENCIA_VENCIDA,
                        lastCheckedTimestamp = now
                    )
                }
            }

            // Record is ACTIVO
            val isExpired = endTimestamp > 0 && endTimestamp < Long.MAX_VALUE && now > endTimestamp

            return if (isTrial) {
                currentInfo.copy(
                    status = if (isExpired) CommercialStatus.PRUEBA_VENCIDA else CommercialStatus.PRUEBA_ACTIVA,
                    businessName = nombreNegocio.ifBlank { currentInfo.businessName },
                    phoneNumber = numeroMovil.ifBlank { currentInfo.phoneNumber },
                    startDate = fechaInicio.ifBlank { currentInfo.startDate },
                    endDate = fechaFin.ifBlank { currentInfo.endDate },
                    endTimestamp = endTimestamp,
                    lastCheckedTimestamp = now,
                    urlUsuarios = urlUsuarios.ifBlank { currentInfo.urlUsuarios },
                    businessCode = businessCode.ifBlank { currentInfo.businessCode },
                    motivoRevocacion = "",
                    fechaRevocacion = ""
                )
            } else {
                currentInfo.copy(
                    status = if (isExpired) CommercialStatus.LICENCIA_VENCIDA else CommercialStatus.LICENCIA_ACTIVA,
                    businessName = nombreNegocio.ifBlank { currentInfo.businessName },
                    phoneNumber = numeroMovil.ifBlank { currentInfo.phoneNumber },
                    licenseType = tipoLicencia.ifBlank { currentInfo.licenseType },
                    startDate = fechaInicio.ifBlank { currentInfo.startDate },
                    endDate = fechaFin.ifBlank { currentInfo.endDate },
                    endTimestamp = endTimestamp,
                    lastCheckedTimestamp = now,
                    urlUsuarios = urlUsuarios.ifBlank { currentInfo.urlUsuarios },
                    businessCode = businessCode.ifBlank { currentInfo.businessCode },
                    motivoRevocacion = "",
                    fechaRevocacion = ""
                )
            }
        }
    }

    private fun loadLicenseInfo(): CommercialLicenseInfo {
        val statusStr = prefs.getString(KEY_STATUS, CommercialStatus.SIN_AUTORIZACION.name)
        val status = try {
            CommercialStatus.valueOf(statusStr ?: CommercialStatus.SIN_AUTORIZACION.name)
        } catch (e: Exception) {
            CommercialStatus.SIN_AUTORIZACION
        }

        val businessName = prefs.getString(KEY_BUSINESS_NAME, "") ?: ""
        val phoneNumber = prefs.getString(KEY_PHONE, "") ?: ""
        val licenseType = prefs.getString(KEY_LICENSE_TYPE, "") ?: ""
        val startDate = prefs.getString(KEY_START_DATE, "") ?: ""
        val endDate = prefs.getString(KEY_END_DATE, "") ?: ""
        val endTimestamp = prefs.getLong(KEY_END_TIMESTAMP, 0L)
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        val urlUsuarios = prefs.getString(KEY_URL_USUARIOS, "") ?: ""
        val businessCode = prefs.getString(KEY_BUSINESS_CODE, "") ?: ""
        val motivoRevocacion = prefs.getString(KEY_MOTIVO_REVOCACION, "") ?: ""
        val fechaRevocacion = prefs.getString(KEY_FECHA_REVOCACION, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val ownerUsername = prefs.getString("owner_username", "") ?: ""
        val ownerPassword = prefs.getString("owner_password", "") ?: ""

        val info = CommercialLicenseInfo(
            status = status,
            businessName = businessName,
            phoneNumber = phoneNumber,
            licenseType = licenseType,
            startDate = startDate,
            endDate = endDate,
            endTimestamp = endTimestamp,
            lastCheckedTimestamp = lastChecked,
            urlUsuarios = urlUsuarios,
            businessCode = businessCode,
            motivoRevocacion = motivoRevocacion,
            fechaRevocacion = fechaRevocacion,
            token = token,
            ownerUsername = ownerUsername,
            ownerPassword = ownerPassword
        )

        // Evaluate local expiration
        return checkLocalExpiration(info)
    }

    /**
     * Checks if active trial or license has expired based on current local timestamp.
     */
    fun checkCurrentExpiration(): CommercialLicenseInfo {
        val current = _licenseInfo.value
        val updated = checkLocalExpiration(current)
        if (updated.status != current.status) {
            saveLicenseInfo(updated)
        }
        return updated
    }

    /**
     * Requisito 4: Si transcurren 7 días completos sin una comprobación satisfactoria,
     * se requiere verificación obligatoria por Internet.
     */
    fun isOfflineCheckRequired(): Boolean {
        val info = _licenseInfo.value
        if (info.status == CommercialStatus.SIN_AUTORIZACION) return false
        if (info.lastCheckedTimestamp <= 0L) return false
        val now = System.currentTimeMillis()
        return (now - info.lastCheckedTimestamp) > OFFLINE_GRACE_PERIOD_MILLIS
    }

    fun getDaysSinceLastCheck(): Long {
        val info = _licenseInfo.value
        if (info.lastCheckedTimestamp <= 0L) return 0L
        val diff = System.currentTimeMillis() - info.lastCheckedTimestamp
        return java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0L)
    }

    /**
     * Requisito 2: Comprobación en segundo plano cada vez que se abre la aplicación con conexión.
     * Si no hay conexión, conserva el estado local válido sin bloquear inmediatamente (hasta 7 días).
     */
    suspend fun executeBackgroundCheck(context: Context, rawDvc: String): LicenseUpdateResult = withContext(Dispatchers.IO) {
        val currentInfo = checkCurrentExpiration()
        if (currentInfo.status == CommercialStatus.SIN_AUTORIZACION) {
            return@withContext LicenseUpdateResult.NoChange(
                CommercialStatus.SIN_AUTORIZACION,
                "Sin vinculación comercial."
            )
        }

        if (!isNetworkAvailable(context)) {
            return@withContext LicenseUpdateResult.Error(
                "Sin conexión a Internet. Conservando estado comercial local."
            )
        }

        return@withContext executeActualizar(rawDvc)
    }

    private fun checkLocalExpiration(info: CommercialLicenseInfo): CommercialLicenseInfo {
        val now = System.currentTimeMillis()

        // Revoked licenses stay revoked and do not automatically expire or revert
        if (info.status == CommercialStatus.LICENCIA_REVOCADA || info.status == CommercialStatus.SIN_AUTORIZACION) {
            return info
        }

        val calculatedEndTimestamp = if (info.endDate.isNotBlank()) {
            parseDateToTimestamp(info.endDate, isEndOfDay = true)
        } else {
            info.endTimestamp
        }

        val effectiveEndTimestamp = if (calculatedEndTimestamp > 0L) calculatedEndTimestamp else info.endTimestamp

        if (info.status == CommercialStatus.PRUEBA_ACTIVA || info.status == CommercialStatus.PRUEBA_VENCIDA) {
            if (effectiveEndTimestamp > 0L && effectiveEndTimestamp < Long.MAX_VALUE) {
                if (now > effectiveEndTimestamp) {
                    if (info.status != CommercialStatus.PRUEBA_VENCIDA || info.endTimestamp != effectiveEndTimestamp) {
                        val expired = info.copy(status = CommercialStatus.PRUEBA_VENCIDA, endTimestamp = effectiveEndTimestamp)
                        saveLicenseInfo(expired)
                        return expired
                    }
                } else {
                    // Trial is STILL VALID and NOT EXPIRED!
                    if (info.status == CommercialStatus.PRUEBA_VENCIDA || info.endTimestamp != effectiveEndTimestamp) {
                        val active = info.copy(status = CommercialStatus.PRUEBA_ACTIVA, endTimestamp = effectiveEndTimestamp)
                        saveLicenseInfo(active)
                        return active
                    }
                }
            }
        } else if (info.status == CommercialStatus.LICENCIA_ACTIVA || info.status == CommercialStatus.LICENCIA_VENCIDA) {
            if (effectiveEndTimestamp > 0L && effectiveEndTimestamp < Long.MAX_VALUE) {
                if (now > effectiveEndTimestamp) {
                    if (info.status != CommercialStatus.LICENCIA_VENCIDA || info.endTimestamp != effectiveEndTimestamp) {
                        val expired = info.copy(status = CommercialStatus.LICENCIA_VENCIDA, endTimestamp = effectiveEndTimestamp)
                        saveLicenseInfo(expired)
                        return expired
                    }
                } else {
                    // License is STILL VALID and NOT EXPIRED!
                    if (info.status == CommercialStatus.LICENCIA_VENCIDA || info.endTimestamp != effectiveEndTimestamp) {
                        val active = info.copy(status = CommercialStatus.LICENCIA_ACTIVA, endTimestamp = effectiveEndTimestamp)
                        saveLicenseInfo(active)
                        return active
                    }
                }
            }
        }
        return info
    }

    fun saveLicenseInfo(info: CommercialLicenseInfo) {
        prefs.edit()
            .putString(KEY_STATUS, info.status.name)
            .putString(KEY_BUSINESS_NAME, info.businessName)
            .putString(KEY_PHONE, info.phoneNumber)
            .putString(KEY_LICENSE_TYPE, info.licenseType)
            .putString(KEY_START_DATE, info.startDate)
            .putString(KEY_END_DATE, info.endDate)
            .putLong(KEY_END_TIMESTAMP, info.endTimestamp)
            .putLong(KEY_LAST_CHECKED, info.lastCheckedTimestamp)
            .putString(KEY_URL_USUARIOS, info.urlUsuarios)
            .putString(KEY_BUSINESS_CODE, info.businessCode)
            .putString(KEY_MOTIVO_REVOCACION, info.motivoRevocacion)
            .putString(KEY_FECHA_REVOCACION, info.fechaRevocacion)
            .putString(KEY_TOKEN, info.token)
            .putString("owner_username", info.ownerUsername)
            .putString("owner_password", info.ownerPassword)
            .apply()
        _licenseInfo.value = info
    }

    /**
     * FASE 5: Checks whether trial or license is close to expiring, and sends an automatic warning SMS
     * to the official Super Admin number (54413935). Avoids sending duplicates for the same period.
     */
    fun checkAndSendExpiryWarnings(context: Context, rawDvc: String) {
        val current = checkCurrentExpiration()
        val now = System.currentTimeMillis()

        // Do not send warnings if status is revoked, unauthorized, or already expired
        if (current.status == CommercialStatus.LICENCIA_REVOCADA ||
            current.status == CommercialStatus.SIN_AUTORIZACION ||
            current.status == CommercialStatus.PRUEBA_VENCIDA ||
            current.status == CommercialStatus.LICENCIA_VENCIDA
        ) {
            return
        }

        val normalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
            rawDvc.uppercase()
        } else {
            "DVC-${rawDvc.uppercase()}"
        }

        if (current.status == CommercialStatus.PRUEBA_ACTIVA && current.endTimestamp > 0 && current.endTimestamp > now) {
            val remainingMillis = current.endTimestamp - now
            val remainingDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(remainingMillis)

            // Close to trial ending (e.g. <= 2 days / 48 hours remaining)
            if (remainingDays in 0..2) {
                val warningKey = "warn_trial_${current.businessCode.ifBlank { normalizedDvc }}_${current.endTimestamp}"
                val warningPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val alreadySent = warningPrefs.getBoolean(warningKey, false)
                if (!alreadySent) {
                    val smsBody = SuperAdminSmsHelper.buildAdvertenciaVencimientoSms(
                        negocio = current.businessName.ifBlank { "Negocio" },
                        dvc = normalizedDvc,
                        movil = current.phoneNumber,
                        tipoAcceso = "PRUEBA (7D)",
                        fechaVencimiento = current.endDate,
                        diasRestantes = remainingDays.coerceAtLeast(0)
                    )
                    SuperAdminSmsHelper.sendSmsDirectOrIntent(context, smsBody)
                    warningPrefs.edit().putBoolean(warningKey, true).apply()
                }
            }
        } else if (current.status == CommercialStatus.LICENCIA_ACTIVA &&
            !current.licenseType.equals("PERMANENTE", ignoreCase = true) &&
            current.endTimestamp > 0 && current.endTimestamp < Long.MAX_VALUE &&
            current.endTimestamp > now
        ) {
            val remainingMillis = current.endTimestamp - now
            val remainingDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(remainingMillis)

            // Close to license ending (e.g. <= 5 days remaining)
            if (remainingDays in 0..5) {
                val warningKey = "warn_lic_${current.businessCode.ifBlank { normalizedDvc }}_${current.endTimestamp}"
                val warningPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val alreadySent = warningPrefs.getBoolean(warningKey, false)
                if (!alreadySent) {
                    val smsBody = SuperAdminSmsHelper.buildAdvertenciaVencimientoSms(
                        negocio = current.businessName.ifBlank { "Negocio" },
                        dvc = normalizedDvc,
                        movil = current.phoneNumber,
                        tipoAcceso = current.licenseType.ifBlank { "LICENCIA" },
                        fechaVencimiento = current.endDate,
                        diasRestantes = remainingDays.coerceAtLeast(0)
                    )
                    SuperAdminSmsHelper.sendSmsDirectOrIntent(context, smsBody)
                    warningPrefs.edit().putBoolean(warningKey, true).apply()
                }
            }
        }
    }

    /**
     * AJUSTE FASE 5: Envia avisos de vencimiento automáticamente al número móvil registrado
     * del propio cliente cuando su período activo (PRUEBA o LICENCIA) esté próximo a vencer.
     * Envía exactamente 3 avisos: 72h, 48h, y 24h antes del vencimiento.
     * Evita duplicados por cada período y previene el envío de avisos atrasados acumulados.
     */
    fun checkAndSendClientExpiryWarnings(context: Context, rawDvc: String) {
        val current = checkCurrentExpiration()
        val now = System.currentTimeMillis()

        // Do not send warnings if status is revoked, unauthorized, or already expired
        if (current.status == CommercialStatus.LICENCIA_REVOCADA ||
            current.status == CommercialStatus.SIN_AUTORIZACION ||
            current.status == CommercialStatus.PRUEBA_VENCIDA ||
            current.status == CommercialStatus.LICENCIA_VENCIDA
        ) {
            return
        }

        val normalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
            rawDvc.uppercase()
        } else {
            "DVC-${rawDvc.uppercase()}"
        }

        if (current.endTimestamp <= 0 || current.endTimestamp == Long.MAX_VALUE) {
            return
        }

        val remainingMillis = current.endTimestamp - now
        val remainingHours = remainingMillis.toDouble() / (1000.0 * 60.0 * 60.0)

        // If already expired, don't send warnings
        if (remainingHours <= 0) {
            return
        }

        val periodKey = "${current.businessCode.ifBlank { normalizedDvc }}_${current.endTimestamp}"
        val warningPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 24 HOURS warning (<= 24h remaining)
        if (remainingHours > 0.0 && remainingHours <= 24.0) {
            val key24 = "client_warn_24h_$periodKey"
            val alreadySent24 = warningPrefs.getBoolean(key24, false)
            if (!alreadySent24) {
                val timeRemainingText = "${remainingHours.toInt().coerceAtLeast(1)} horas"
                val smsBody = buildClientWarningSms(current, normalizedDvc, "24h", timeRemainingText)
                if (current.phoneNumber.isNotBlank()) {
                    SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, current.phoneNumber, smsBody)
                }
                // Mark all three alerts as sent to prevent any delayed/older warnings from firing in the future
                warningPrefs.edit()
                    .putBoolean("client_warn_72h_$periodKey", true)
                    .putBoolean("client_warn_48h_$periodKey", true)
                    .putBoolean(key24, true)
                    .apply()
            }
        }
        // 48 HOURS warning (> 24h and <= 48h remaining)
        else if (remainingHours > 24.0 && remainingHours <= 48.0) {
            val key48 = "client_warn_48h_$periodKey"
            val alreadySent48 = warningPrefs.getBoolean(key48, false)
            if (!alreadySent48) {
                val timeRemainingText = "aproximadamente 48 horas"
                val smsBody = buildClientWarningSms(current, normalizedDvc, "48h", timeRemainingText)
                if (current.phoneNumber.isNotBlank()) {
                    SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, current.phoneNumber, smsBody)
                }
                // Mark 72h and 48h alerts as sent
                warningPrefs.edit()
                    .putBoolean("client_warn_72h_$periodKey", true)
                    .putBoolean(key48, true)
                    .apply()
            }
        }
        // 72 HOURS warning (> 48h and <= 72h remaining)
        else if (remainingHours > 48.0 && remainingHours <= 72.0) {
            val key72 = "client_warn_72h_$periodKey"
            val alreadySent72 = warningPrefs.getBoolean(key72, false)
            if (!alreadySent72) {
                val timeRemainingText = "aproximadamente 72 horas"
                val smsBody = buildClientWarningSms(current, normalizedDvc, "72h", timeRemainingText)
                if (current.phoneNumber.isNotBlank()) {
                    SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, current.phoneNumber, smsBody)
                }
                // Mark 72h alert as sent
                warningPrefs.edit()
                    .putBoolean(key72, true)
                    .apply()
            }
        }
    }

    private fun buildClientWarningSms(
        current: CommercialLicenseInfo,
        normalizedDvc: String,
        warningType: String,
        timeRemainingText: String
    ): String {
        val accessTypeLabel = if (current.status == CommercialStatus.PRUEBA_ACTIVA) "PRUEBA" else "LICENCIA"
        return """
            [ElQadre] AVISO VENCIMIENTO DE ACCESO
            Negocio: ${current.businessName.ifBlank { "Negocio" }}
            DVC: $normalizedDvc
            Tipo de Acceso: $accessTypeLabel
            Fecha de Vencimiento: ${current.endDate}
            Tiempo Restante: $timeRemainingText
            *ADVERTENCIA*: Por favor gestione la activación o renovación con la administración para mantener su acceso continuo.
        """.trimIndent()
    }

    /**
     * Executes the ACTUALIZAR button logic according to Section 7 & 8 rules.
     * Determines whether to query Q_preuba.json or Q_licencias.json based on current state.
     */
    suspend fun executeActualizar(rawDvc: String): LicenseUpdateResult = withContext(Dispatchers.IO) {
        val normalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
            rawDvc.uppercase()
        } else {
            "DVC-${rawDvc.uppercase()}"
        }

        val currentInfo = checkCurrentExpiration()
        val currentStatus = currentInfo.status

        val targetUrl = when (currentStatus) {
            CommercialStatus.SIN_AUTORIZACION,
            CommercialStatus.PRUEBA_ACTIVA -> URL_PRUEBA
            CommercialStatus.PRUEBA_VENCIDA,
            CommercialStatus.LICENCIA_ACTIVA,
            CommercialStatus.LICENCIA_VENCIDA,
            CommercialStatus.LICENCIA_REVOCADA -> URL_LICENCIAS
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
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
                return@withContext LicenseUpdateResult.Error(
                    "No fue posible comprobar la autorización (Servidor respondió con código $responseCode)."
                )
            }

            val jsonContent = connection.inputStream.bufferedReader().use { it.readText() }
            if (jsonContent.isBlank()) {
                return@withContext handleRecordNotFound(currentStatus, targetUrl)
            }

            val items = parseRecordsFromJson(jsonContent)
            var matchedObject = findDvcRecord(items, normalizedDvc)
            if (matchedObject == null && _licenseInfo.value.token.isNotBlank()) {
                matchedObject = findRecordByToken(items, _licenseInfo.value.token)
            }

            if (matchedObject == null) {
                return@withContext handleRecordNotFound(currentStatus, targetUrl)
            }

            // Record found - evaluate fields
            val estado = matchedObject.optString("estado", "").trim().uppercase()
            val nombreNegocio = matchedObject.optString("nombreNegocio", "").trim()
            val numeroMovil = (matchedObject.optString("numeroMovil", "").ifBlank {
                matchedObject.optString("telefono", "")
            }).trim()
            val fechaInicio = (matchedObject.optString("fechaInicio", "").ifBlank {
                matchedObject.optString("fecha_inicio", "")
            }).trim()
            val fechaFin = (matchedObject.optString("fechaFin", "").ifBlank {
                matchedObject.optString("fecha_fin", "")
            }).trim()
            val tipoLicencia = (matchedObject.optString("tipoLicencia", "").ifBlank {
                matchedObject.optString("tipo", "COMERCIAL")
            }).trim()
            val urlUsuarios = matchedObject.optString("urlUsuarios", matchedObject.optString("url_usuarios", "")).trim()
            val businessCode = matchedObject.optString("codigo", matchedObject.optString("codigoNegocio", "")).trim()
            val motivoRevocacion = (matchedObject.optString("motivoRevocacion", "").ifBlank {
                matchedObject.optString("motivo", "")
            }).trim()
            val fechaRevocacion = (matchedObject.optString("fechaRevocacion", "").ifBlank {
                matchedObject.optString("fecha_revocacion", "")
            }).trim()

            val endTimestamp = parseDateToTimestamp(fechaFin, isEndOfDay = true)
            val now = System.currentTimeMillis()

            // FASE 5: Explicit REVOCADA status handling
            if (estado == "REVOCADA" || estado == "REVOCADO") {
                val revokedInfo = currentInfo.copy(
                    status = CommercialStatus.LICENCIA_REVOCADA,
                    businessName = nombreNegocio,
                    phoneNumber = numeroMovil,
                    licenseType = tipoLicencia,
                    startDate = fechaInicio,
                    endDate = fechaFin,
                    endTimestamp = endTimestamp,
                    lastCheckedTimestamp = now,
                    urlUsuarios = urlUsuarios,
                    businessCode = businessCode,
                    motivoRevocacion = motivoRevocacion,
                    fechaRevocacion = fechaRevocacion
                )
                saveLicenseInfo(revokedInfo)
                val msg = if (motivoRevocacion.isNotBlank()) {
                    "Licencia REVOCADA por la administración.\nMotivo: $motivoRevocacion"
                } else {
                    "Licencia REVOCADA por la administración."
                }
                return@withContext LicenseUpdateResult.NoChange(CommercialStatus.LICENCIA_REVOCADA, msg)
            }

            if (estado != "ACTIVO") {
                return@withContext handleInactiveRecord(currentStatus)
            }

            // Target is Q_preuba.json (Trial)
            if (targetUrl == URL_PRUEBA) {
                if (endTimestamp > 0 && now > endTimestamp) {
                    // Trial has expired
                    val newInfo = currentInfo.copy(
                        status = CommercialStatus.PRUEBA_VENCIDA,
                        businessName = nombreNegocio,
                        phoneNumber = numeroMovil,
                        startDate = fechaInicio,
                        endDate = fechaFin,
                        endTimestamp = endTimestamp,
                        lastCheckedTimestamp = now,
                        urlUsuarios = urlUsuarios,
                        businessCode = businessCode,
                        motivoRevocacion = "",
                        fechaRevocacion = ""
                    )
                    saveLicenseInfo(newInfo)
                    return@withContext LicenseUpdateResult.NoChange(
                        CommercialStatus.PRUEBA_VENCIDA,
                        "El período de prueba ha vencido el $fechaFin. Requiere activación de licencia."
                    )
                } else {
                    // Active trial
                    val newInfo = currentInfo.copy(
                        status = CommercialStatus.PRUEBA_ACTIVA,
                        businessName = nombreNegocio,
                        phoneNumber = numeroMovil,
                        startDate = fechaInicio,
                        endDate = fechaFin,
                        endTimestamp = endTimestamp,
                        lastCheckedTimestamp = now,
                        urlUsuarios = urlUsuarios,
                        businessCode = businessCode,
                        motivoRevocacion = "",
                        fechaRevocacion = ""
                    )
                    saveLicenseInfo(newInfo)
                    return@withContext LicenseUpdateResult.Success(
                        CommercialStatus.PRUEBA_ACTIVA,
                        "¡Prueba autorizada y activa hasta el $fechaFin!"
                    )
                }
            } else {
                // Target is Q_licencias.json (Commercial License)
                if (endTimestamp > 0 && now > endTimestamp) {
                    // License has expired
                    val newInfo = currentInfo.copy(
                        status = CommercialStatus.LICENCIA_VENCIDA,
                        businessName = nombreNegocio,
                        phoneNumber = numeroMovil,
                        licenseType = tipoLicencia,
                        startDate = fechaInicio,
                        endDate = fechaFin,
                        endTimestamp = endTimestamp,
                        lastCheckedTimestamp = now,
                        urlUsuarios = urlUsuarios,
                        businessCode = businessCode,
                        motivoRevocacion = "",
                        fechaRevocacion = ""
                    )
                    saveLicenseInfo(newInfo)
                    return@withContext LicenseUpdateResult.NoChange(
                        CommercialStatus.LICENCIA_VENCIDA,
                        "La licencia comercial ha vencido el $fechaFin. Requiere activación."
                    )
                } else {
                    // Active License
                    val newInfo = currentInfo.copy(
                        status = CommercialStatus.LICENCIA_ACTIVA,
                        businessName = nombreNegocio,
                        phoneNumber = numeroMovil,
                        licenseType = tipoLicencia,
                        startDate = fechaInicio,
                        endDate = fechaFin,
                        endTimestamp = endTimestamp,
                        lastCheckedTimestamp = now,
                        urlUsuarios = urlUsuarios,
                        businessCode = businessCode,
                        motivoRevocacion = "",
                        fechaRevocacion = ""
                    )
                    saveLicenseInfo(newInfo)
                    val formattedMsg = if (fechaFin.isNotBlank() && !fechaFin.equals("PERMANENTE", ignoreCase = true)) {
                        "¡Licencia $tipoLicencia activa hasta el $fechaFin!"
                    } else {
                        "¡Licencia $tipoLicencia activa!"
                    }
                    return@withContext LicenseUpdateResult.Success(
                        CommercialStatus.LICENCIA_ACTIVA,
                        formattedMsg
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext LicenseUpdateResult.Error(
                "No fue posible comprobar la autorización. Compruebe la conexión a Internet."
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Completes the commercial check sequence:
     * Evaluates device status & queries Q_preuba.json or Q_licencias.json for commercial license/trial validity.
     * Does NOT download or sync users (user accounts are managed locally by the business owner).
     */
    suspend fun executeActualizarFull(context: Context, rawDvc: String): LicenseUpdateResult = withContext(Dispatchers.IO) {
        return@withContext executeActualizar(rawDvc)
    }

    private fun handleRecordNotFound(currentStatus: CommercialStatus, targetUrl: String): LicenseUpdateResult {
        return when (currentStatus) {
            CommercialStatus.SIN_AUTORIZACION -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.SIN_AUTORIZACION,
                    "El dispositivo todavía no cuenta con autorización en Q_preuba.json."
                )
            }
            CommercialStatus.PRUEBA_ACTIVA -> {
                val updated = _licenseInfo.value.copy(
                    status = CommercialStatus.PRUEBA_VENCIDA,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                saveLicenseInfo(updated)
                LicenseUpdateResult.NoChange(
                    CommercialStatus.PRUEBA_VENCIDA,
                    "No se encontró autorización activa en Q_preuba.json. Estado: Prueba Vencida."
                )
            }
            CommercialStatus.PRUEBA_VENCIDA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.PRUEBA_VENCIDA,
                    "No se encontró una licencia activa en Q_licencias.json. Requiere activación."
                )
            }
            CommercialStatus.LICENCIA_ACTIVA -> {
                val updated = _licenseInfo.value.copy(
                    status = CommercialStatus.LICENCIA_VENCIDA,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                saveLicenseInfo(updated)
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_VENCIDA,
                    "No se encontró la licencia en Q_licencias.json. Estado: Licencia Vencida."
                )
            }
            CommercialStatus.LICENCIA_VENCIDA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_VENCIDA,
                    "No se encontró una licencia activa en Q_licencias.json."
                )
            }
            CommercialStatus.LICENCIA_REVOCADA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_REVOCADA,
                    "La licencia continúa REVOCADA por la administración."
                )
            }
        }
    }

    private fun handleInactiveRecord(currentStatus: CommercialStatus): LicenseUpdateResult {
        return when (currentStatus) {
            CommercialStatus.SIN_AUTORIZACION -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.SIN_AUTORIZACION,
                    "El registro del dispositivo no se encuentra en estado ACTIVO."
                )
            }
            CommercialStatus.PRUEBA_ACTIVA -> {
                val updated = _licenseInfo.value.copy(
                    status = CommercialStatus.PRUEBA_VENCIDA,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                saveLicenseInfo(updated)
                LicenseUpdateResult.NoChange(
                    CommercialStatus.PRUEBA_VENCIDA,
                    "El registro de prueba ya no se encuentra ACTIVO."
                )
            }
            CommercialStatus.PRUEBA_VENCIDA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.PRUEBA_VENCIDA,
                    "La licencia comercial no se encuentra en estado ACTIVO."
                )
            }
            CommercialStatus.LICENCIA_ACTIVA -> {
                val updated = _licenseInfo.value.copy(
                    status = CommercialStatus.LICENCIA_VENCIDA,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
                saveLicenseInfo(updated)
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_VENCIDA,
                    "La licencia comercial ya no se encuentra en estado ACTIVO."
                )
            }
            CommercialStatus.LICENCIA_VENCIDA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_VENCIDA,
                    "La licencia comercial no se encuentra en estado ACTIVO."
                )
            }
            CommercialStatus.LICENCIA_REVOCADA -> {
                LicenseUpdateResult.NoChange(
                    CommercialStatus.LICENCIA_REVOCADA,
                    "La licencia se encuentra REVOCADA por la administración."
                )
            }
        }
    }

    private fun parseRecordsFromJson(jsonString: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val trimmed = jsonString.trim()
        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) result.add(obj)
            }
        } else if (trimmed.startsWith("{")) {
            val rootObj = JSONObject(trimmed)
            val possibleKeys = listOf("pruebas", "licencias", "autorizaciones", "dispositivos", "items", "data")
            var found = false
            for (key in possibleKeys) {
                if (rootObj.has(key)) {
                    val array = rootObj.optJSONArray(key)
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i)
                            if (obj != null) result.add(obj)
                        }
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                // Check if the root object itself is a record
                if (rootObj.has("dvc")) {
                    result.add(rootObj)
                }
            }
        }
        return result
    }

    private fun findDvcRecord(records: List<JSONObject>, normalizedDvc: String): JSONObject? {
        for (record in records) {
            val rawDvc = record.optString("dvc", "").trim()
            val itemNormalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
                rawDvc.uppercase()
            } else {
                "DVC-${rawDvc.uppercase()}"
            }
            if (itemNormalizedDvc.equals(normalizedDvc, ignoreCase = true)) {
                return record
            }
        }
        return null
    }

    /**
     * Queries Q_licencias.json and Q_preuba.json to retrieve the urlUsuarios for a specific DVC.
     */
    suspend fun fetchUrlUsuariosForDvc(rawDvc: String): String? = withContext(Dispatchers.IO) {
        val normalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
            rawDvc.uppercase()
        } else {
            "DVC-${rawDvc.uppercase()}"
        }

        // Check stored license info first
        if (_licenseInfo.value.urlUsuarios.isNotBlank()) {
            return@withContext _licenseInfo.value.urlUsuarios
        }

        // Check Q_licencias.json first, then Q_preuba.json
        val urlsToTry = listOf(URL_LICENCIAS, URL_PRUEBA)
        for (targetUrl in urlsToTry) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(targetUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    useCaches = false
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonContent = connection.inputStream.bufferedReader().use { it.readText() }
                    if (jsonContent.isNotBlank()) {
                        val items = parseRecordsFromJson(jsonContent)
                        val matched = findDvcRecord(items, normalizedDvc)
                        if (matched != null) {
                            val urlU = matched.optString("urlUsuarios", matched.optString("url_usuarios", "")).trim()
                            if (urlU.isNotBlank()) {
                                return@withContext urlU
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            finally {
                connection?.disconnect()
            }
        }
        return@withContext null
    }

    /**
     * Validates a 6-character TOKEN against remote Q_licencias.json and Q_preuba.json.
     * Checks network connection, matches token, verifies status (ACTIVO) and expiration date.
     * If valid, updates local binding (CommercialLicenseInfo) and returns Success.
     */
    suspend fun validateToken(context: Context, rawToken: String, rawDvc: String): LicenseUpdateResult = withContext(Dispatchers.IO) {
        val cleanToken = rawToken.trim().uppercase()
        if (cleanToken.length != 6 || !cleanToken.all { it.isLetterOrDigit() }) {
            return@withContext LicenseUpdateResult.Error(
                "El TOKEN debe tener exactamente 6 caracteres alfanuméricos."
            )
        }

        // Check internet connection
        val isOnline = isNetworkAvailable(context)
        if (!isOnline) {
            return@withContext LicenseUpdateResult.Error(
                "Se requiere conexión a Internet (Wi-Fi o datos móviles) para validar el token."
            )
        }

        val normalizedDvc = if (rawDvc.startsWith("DVC-", ignoreCase = true)) {
            rawDvc.uppercase()
        } else if (rawDvc.isNotBlank()) {
            "DVC-${rawDvc.uppercase()}"
        } else {
            ""
        }

        val sources = listOf(
            Pair(URL_LICENCIAS, false),
            Pair(URL_PRUEBA, true)
        )

        var connectionFailed = false

        for ((targetUrl, isTrial) in sources) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(targetUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connectionFailed = true
                    continue
                }

                val jsonContent = connection.inputStream.bufferedReader().use { it.readText() }
                if (jsonContent.isBlank()) continue

                val records = parseRecordsFromJson(jsonContent)
                val matchedRecord = findRecordByToken(records, cleanToken)

                if (matchedRecord != null) {
                    val estado = matchedRecord.optString("estado", "").trim().uppercase()
                    val nombreNegocio = (matchedRecord.optString("nombreNegocio", "").ifBlank {
                        matchedRecord.optString("nombre", "")
                    }).trim()
                    val numeroMovil = (matchedRecord.optString("numeroMovil", "").ifBlank {
                        matchedRecord.optString("telefono", "")
                    }).trim()
                    val fechaInicio = (matchedRecord.optString("fechaInicio", "").ifBlank {
                        matchedRecord.optString("fecha_inicio", "")
                    }).trim()
                    val fechaFin = (matchedRecord.optString("fechaFin", "").ifBlank {
                        matchedRecord.optString("fecha_fin", "")
                    }).trim()
                    val tipoLicencia = (matchedRecord.optString("tipoLicencia", "").ifBlank {
                        matchedRecord.optString("tipo", if (isTrial) "PRUEBA" else "COMERCIAL")
                    }).trim()
                    val urlUsuarios = (matchedRecord.optString("urlUsuarios", "").ifBlank {
                        matchedRecord.optString("url_usuarios", "")
                    }).trim()
                    val businessCode = (matchedRecord.optString("codigo", "").ifBlank {
                        matchedRecord.optString("codigoNegocio", "")
                    }).trim()

                    val endTimestamp = parseDateToTimestamp(fechaFin, isEndOfDay = true)
                    val now = System.currentTimeMillis()

                    if (estado == "REVOCADA" || estado == "REVOCADO") {
                        return@withContext LicenseUpdateResult.Error(
                            "El token ingresado corresponde a una autorización REVOCADA por la administración."
                        )
                    }

                    if (estado != "ACTIVO") {
                        return@withContext LicenseUpdateResult.Error(
                            "El token ingresado no corresponde a una autorización activa."
                        )
                    }

                    if (endTimestamp > 0 && endTimestamp < Long.MAX_VALUE && now > endTimestamp) {
                        return@withContext LicenseUpdateResult.Error(
                            "La autorización asociada al token ha vencido el $fechaFin."
                        )
                    }

                    val newStatus = if (isTrial || tipoLicencia.contains("PRUEBA", ignoreCase = true)) {
                        CommercialStatus.PRUEBA_ACTIVA
                    } else {
                        CommercialStatus.LICENCIA_ACTIVA
                    }

                    val newInfo = CommercialLicenseInfo(
                        status = newStatus,
                        businessName = nombreNegocio.ifBlank { "Negocio ElQadre" },
                        phoneNumber = numeroMovil,
                        licenseType = tipoLicencia,
                        startDate = fechaInicio,
                        endDate = fechaFin,
                        endTimestamp = endTimestamp,
                        lastCheckedTimestamp = now,
                        urlUsuarios = urlUsuarios,
                        businessCode = businessCode,
                        motivoRevocacion = "",
                        fechaRevocacion = "",
                        token = cleanToken
                    )

                    saveLicenseInfo(newInfo)

                    return@withContext LicenseUpdateResult.Success(
                        newStatus,
                        "Dispositivo vinculado correctamente con $nombreNegocio."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                connectionFailed = true
            } finally {
                connection?.disconnect()
            }
        }

        // Check local files as fallback (e.g., generated locally by SuperAdmin)
        val localFiles: List<Pair<File, Boolean>> = listOf(
            Pair(File(context.filesDir, "Q_licencias.json"), false),
            Pair(File(context.filesDir, "Q_preuba.json"), true)
        )

        for (item in localFiles) {
            val file = item.first
            val isTrial = item.second
            if (file.exists()) {
                try {
                    val jsonContent = file.readText()
                    if (jsonContent.isNotBlank()) {
                        val records = parseRecordsFromJson(jsonContent)
                        val matchedRecord = findRecordByToken(records, cleanToken)
                        if (matchedRecord != null) {
                            val estado = matchedRecord.optString("estado", "").trim().uppercase()
                            val nombreNegocio = (matchedRecord.optString("nombreNegocio", "").ifBlank {
                                matchedRecord.optString("nombre", "")
                            }).trim()
                            val numeroMovil = (matchedRecord.optString("numeroMovil", "").ifBlank {
                                matchedRecord.optString("telefono", "")
                            }).trim()
                            val fechaInicio = (matchedRecord.optString("fechaInicio", "").ifBlank {
                                matchedRecord.optString("fecha_inicio", "")
                            }).trim()
                            val fechaFin = (matchedRecord.optString("fechaFin", "").ifBlank {
                                matchedRecord.optString("fecha_fin", "")
                            }).trim()
                            val tipoLicencia = (matchedRecord.optString("tipoLicencia", "").ifBlank {
                                matchedRecord.optString("tipo", if (isTrial) "PRUEBA" else "COMERCIAL")
                            }).trim()
                            val urlUsuarios = (matchedRecord.optString("urlUsuarios", "").ifBlank {
                                matchedRecord.optString("url_usuarios", "")
                            }).trim()
                            val businessCode = (matchedRecord.optString("codigo", "").ifBlank {
                                matchedRecord.optString("codigoNegocio", "")
                            }).trim()

                            val endTimestamp = parseDateToTimestamp(fechaFin, isEndOfDay = true)
                            val now = System.currentTimeMillis()

                            if (estado == "REVOCADA" || estado == "REVOCADO") {
                                return@withContext LicenseUpdateResult.Error(
                                    "El token ingresado corresponde a una autorización REVOCADA por la administración."
                                )
                            }

                            if (estado != "ACTIVO") {
                                return@withContext LicenseUpdateResult.Error(
                                    "El token ingresado no corresponde a una autorización activa."
                                )
                            }

                            if (endTimestamp > 0 && endTimestamp < Long.MAX_VALUE && now > endTimestamp) {
                                return@withContext LicenseUpdateResult.Error(
                                    "La autorización asociada al token ha vencido el $fechaFin."
                                )
                            }

                            val newStatus = if (isTrial || tipoLicencia.contains("PRUEBA", ignoreCase = true)) {
                                CommercialStatus.PRUEBA_ACTIVA
                            } else {
                                CommercialStatus.LICENCIA_ACTIVA
                            }

                            val newInfo = CommercialLicenseInfo(
                                status = newStatus,
                                businessName = nombreNegocio.ifBlank { "Negocio ElQadre" },
                                phoneNumber = numeroMovil,
                                licenseType = tipoLicencia,
                                startDate = fechaInicio,
                                endDate = fechaFin,
                                endTimestamp = endTimestamp,
                                lastCheckedTimestamp = now,
                                urlUsuarios = urlUsuarios,
                                businessCode = businessCode,
                                motivoRevocacion = "",
                                fechaRevocacion = "",
                                token = cleanToken
                            )

                            saveLicenseInfo(newInfo)

                            return@withContext LicenseUpdateResult.Success(
                                newStatus,
                                "Dispositivo vinculado correctamente con $nombreNegocio."
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (connectionFailed && !isNetworkAvailable(context)) {
            return@withContext LicenseUpdateResult.Error(
                "Se requiere conexión a Internet (Wi-Fi o datos móviles) para validar el token."
            )
        }

        return@withContext LicenseUpdateResult.Error(
            "El token introducido no existe, es inválido o no corresponde a una autorización vigente."
        )
    }

    private fun findRecordByToken(records: List<JSONObject>, cleanToken: String): JSONObject? {
        for (record in records) {
            val fieldsToTest = listOf(
                record.optString("token", ""),
                record.optString("tokenAcceso", ""),
                record.optString("token_acceso", ""),
                record.optString("tokenPrueba", ""),
                record.optString("tokenLicencia", ""),
                record.optString("codigo", ""),
                record.optString("codigoNegocio", "")
            )
            for (field in fieldsToTest) {
                if (field.trim().equals(cleanToken, ignoreCase = true)) {
                    return record
                }
            }
        }
        return null
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    data class ParsedConfirmationSms(
        val businessName: String,
        val dueno: String,
        val mp: String,
        val ma: String,
        val dvc: String,
        val businessCode: String,
        val token: String,
        val ownerUsername: String,
        val ownerPassword: String,
        val startDateStr: String,
        val endDateStr: String
    )

    fun parseConfirmationSms(body: String): ParsedConfirmationSms? = Companion.parseConfirmationSms(body)

    suspend fun processIncomingConfirmationSms(context: Context, body: String): Boolean = withContext(Dispatchers.IO) {
        val parsed = parseConfirmationSms(body) ?: return@withContext false

        // 1. DVC Check
        val rawLocalDvc = com.example.util.DeviceIdentity.getDeterministicDeviceId(context)
        fun normDvc(d: String) = d.trim().uppercase()
            .removePrefix("DVC-")
            .removePrefix("DVC -")
            .removePrefix("DVC:")
            .removePrefix("DVC ")
            .trim()

        val cleanDeviceDvc = "DVC-${normDvc(rawLocalDvc)}"
        val cleanSmsDvc = "DVC-${normDvc(parsed.dvc)}"
        if (normDvc(rawLocalDvc) != normDvc(parsed.dvc)) {
            android.util.Log.e("CommercialLicenseManager", "DVC mismatch: local=$cleanDeviceDvc, sms=$cleanSmsDvc")
            return@withContext false
        }

        // 1b. Business Code Match Check (Un SMS de otro negocio debe rechazarse)
        try {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val curBizConfig = db.configuracionNegocioDao().getConfigSync()
            val curBizCode = BusinessCodeHelper.resolveBusinessCode(
                context = context,
                configNegocio = curBizConfig,
                configGeneral = null,
                licenseInfo = _licenseInfo.value
            ).let { BusinessCodeHelper.formatCode(it) }

            val smsBizCode = BusinessCodeHelper.formatCode(parsed.businessCode)
            if (curBizCode.isNotBlank() && curBizCode != "000" && smsBizCode.isNotBlank() && smsBizCode != "000" && smsBizCode != curBizCode) {
                android.util.Log.e("CommercialLicenseManager", "Business code mismatch: device=$curBizCode, sms=$smsBizCode")
                return@withContext false
            }
        } catch (e: Exception) {
            android.util.Log.e("CommercialLicenseManager", "Error checking business code matching", e)
        }

        // 2. Token Check
        val cleanToken = parsed.token.trim().uppercase()
        if (cleanToken.length < 4 || !cleanToken.all { it.isLetterOrDigit() }) {
            android.util.Log.e("CommercialLicenseManager", "Invalid token: $cleanToken")
            return@withContext false
        }

        // 3. Dates validation
        val startTs = parseDateToTimestamp(parsed.startDateStr, isEndOfDay = false).let {
            if (it <= 0L) System.currentTimeMillis() else it
        }
        var endTs = parseDateToTimestamp(parsed.endDateStr, isEndOfDay = true).let {
            if (it <= 0L) startTs + 7L * 24 * 60 * 60 * 1000L else it
        }
        if (endTs <= startTs && endTs != Long.MAX_VALUE) {
            endTs = startTs + 7L * 24 * 60 * 60 * 1000L
        }

        // 4. Credentials validation
        val ownerUsername = parsed.ownerUsername.trim().lowercase()
        val ownerPasswordPlain = parsed.ownerPassword.trim()
        if (ownerUsername.isBlank() || ownerPasswordPlain.isBlank()) {
            android.util.Log.e("CommercialLicenseManager", "Invalid owner credentials in SMS")
            return@withContext false
        }

        val upperBody = body.uppercase()
        val isLicense = upperBody.contains("LICENCIA")
        val status = if (isLicense) CommercialStatus.LICENCIA_ACTIVA else CommercialStatus.PRUEBA_ACTIVA
        val licenseType = if (isLicense) "COMERCIAL" else "PRUEBA"

        // 5. Save the license info
        val newInfo = CommercialLicenseInfo(
            status = status,
            businessName = parsed.businessName.trim().ifBlank { "Negocio ElQadre" },
            phoneNumber = parsed.mp.trim(),
            licenseType = licenseType,
            startDate = parsed.startDateStr.trim(),
            endDate = parsed.endDateStr.trim(),
            endTimestamp = endTs,
            lastCheckedTimestamp = System.currentTimeMillis(),
            urlUsuarios = "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_${parsed.businessCode.trim()}usuarios.json",
            businessCode = parsed.businessCode.trim().ifBlank { "001" },
            token = cleanToken,
            ownerUsername = ownerUsername,
            ownerPassword = ownerPasswordPlain
        )
        saveLicenseInfo(newInfo)

        // 6. Save/Register Owner user locally in SQLite with all required associations
        try {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val ownerPasswordHash = ownerPasswordPlain.toSha256()
            val ownerFullName = parsed.dueno.trim().ifBlank { "Dueño ${parsed.businessName.trim()}" }
            val ownerPhone = parsed.mp.trim()

            // Clean up any conflicting old DUEÑO users or casing variations
            val allUsers = db.userDao().getAllUsersSync()
            for (u in allUsers) {
                if (u.role == com.example.data.local.model.UserRole.DUENO && !u.username.equals(ownerUsername, ignoreCase = true)) {
                    db.userDao().deleteUser(u.username)
                }
                if (u.username.equals(ownerUsername, ignoreCase = true) && u.username != ownerUsername) {
                    db.userDao().deleteUser(u.username)
                }
            }

            // A. Registrar o actualizar usuario DUEÑO en users
            val ownerUser = com.example.data.local.model.User(
                username = ownerUsername,
                fullName = ownerFullName,
                passwordHash = ownerPasswordHash,
                role = com.example.data.local.model.UserRole.DUENO,
                authorizedDeviceId = null, // El DVC pertenece a la autorización comercial del dispositivo; no crear dependencia usuario->DVC
                telefono = ownerPhone,
                isActive = true,
                permisoProduccion = true,
                permisoMercancias = true,
                permisoPersonal = true,
                permisoControlNegocio = true
            )
            db.userDao().insertUser(ownerUser)

            // Clean up any conflicting old DUEÑO in personal_contratado
            val allPersonal = db.personalContratadoDao().getAllPersonalSync()
            for (p in allPersonal) {
                if (p.role.equals("DUENO", ignoreCase = true) && !p.username.equals(ownerUsername, ignoreCase = true)) {
                    db.personalContratadoDao().deletePersonal(p)
                }
            }

            // B. Registrar o actualizar en personal_contratado
            val existingPersonal = db.personalContratadoDao().getPersonalByUsername(ownerUsername)
            val personalOwner = com.example.data.local.model.PersonalContratado(
                id = existingPersonal?.id ?: 0,
                nombreCompleto = ownerFullName,
                carnetIdentidad = existingPersonal?.carnetIdentidad ?: "",
                movil = ownerPhone,
                formaPago = existingPersonal?.formaPago ?: "Efectivo",
                tieneAccesoApp = true,
                username = ownerUsername,
                passwordHash = ownerPasswordHash,
                passwordPlain = ownerPasswordPlain,
                role = "DUENO",
                dependienteTipo = "SALON",
                montoPorProducto = 0.0,
                isActive = true,
                permisoProduccion = true,
                permisoMercancias = true,
                permisoPersonal = true,
                permisoControlNegocio = true
            )
            if (existingPersonal != null) {
                db.personalContratadoDao().updatePersonal(personalOwner)
            } else {
                db.personalContratadoDao().insertPersonal(personalOwner)
            }

            // C. Actualizar configuración general
            val currentGenConfig = db.configuracionGeneralDao().getConfigSync() ?: com.example.data.local.model.ConfiguracionGeneral()
            db.configuracionGeneralDao().updateConfig(
                currentGenConfig.copy(
                    telefonoDueno = ownerPhone,
                    lastUserUpdateStatus = com.example.util.UserUpdateManager.STATUS_SUCCESS,
                    lastUserUpdateDate = System.currentTimeMillis()
                )
            )

            // D. Actualizar configuración del negocio
            val currentBizConfig = db.configuracionNegocioDao().getConfigSync()
            if (currentBizConfig != null) {
                db.configuracionNegocioDao().updateConfig(
                    currentBizConfig.copy(
                        nombreNegocio = parsed.businessName.trim().ifBlank { currentBizConfig.nombreNegocio },
                        codigoNegocio = parsed.businessCode.trim().ifBlank { currentBizConfig.codigoNegocio },
                        telefono = ownerPhone.ifBlank { currentBizConfig.telefono }
                    )
                )
            } else {
                db.configuracionNegocioDao().insertConfig(
                    com.example.data.local.model.ConfiguracionNegocio(
                        nombreNegocio = parsed.businessName.trim().ifBlank { "Negocio ElQadre" },
                        codigoNegocio = parsed.businessCode.trim().ifBlank { "001" },
                        telefono = ownerPhone
                    )
                )
            }

            // Mark that this is the first owner access to trigger the dialog
            context.getSharedPreferences("elqadre_commercial_license_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("first_owner_access", true)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("CommercialLicenseManager", "Error saving owner user and business config locally", e)
        }
        return@withContext true
    }

    suspend fun checkAndProcessInboxTrialConfirmation(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse("content://sms/inbox")
        val projection = arrayOf("body", "date")
        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC LIMIT 100"
            )
            cursor?.use { c ->
                val bodyCol = c.getColumnIndex("body")
                while (c.moveToNext()) {
                    val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                    val upper = body.uppercase()
                    if (upper.contains("CONFIRMACIÓN PRUEBA ELQADRE") ||
                        upper.contains("CONFIRMACION PRUEBA ELQADRE") ||
                        upper.contains("CONFIRMACIÓN LICENCIA ELQADRE") ||
                        upper.contains("CONFIRMACION LICENCIA ELQADRE") ||
                        (upper.contains("ELQADRE") && (upper.contains("PRUEBA") || upper.contains("LICENCIA")) && (upper.contains("TOKEN") || upper.contains("DVC")))
                    ) {
                        val processed = processIncomingConfirmationSms(context, body)
                        if (processed) {
                            return@withContext true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CommercialLicenseManager", "Error checking inbox for confirmation SMS", e)
        }
        return@withContext false
    }

    fun parseLicenseConfirmationSms(body: String): ParsedConfirmationSms? = parseConfirmationSms(body)

    suspend fun processIncomingLicenseConfirmationSms(context: Context, body: String) {
        processIncomingConfirmationSms(context, body)
    }
}
