package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.AppDatabase
import com.example.data.local.model.ConfiguracionNegocio
import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class AccountSmsData(
    val negocio: String, // Exactamente 3 dígitos (ej: "001")
    val nombre: String,
    val usuario: String,
    val contrasenaInicial: String,
    val rol: UserRole,
    val rolRaw: String,
    val rawBody: String = ""
)

sealed class AccountProvisioningResult {
    data class Success(val user: User, val isUpdate: Boolean, val message: String) : AccountProvisioningResult()
    data class Error(val reason: String) : AccountProvisioningResult()
    object IgnoredNotAccountSms : AccountProvisioningResult()
}

object AccountProvisioningBus {
    private val _events = MutableSharedFlow<Pair<User, Boolean>>(extraBufferCapacity = 10)
    val events: SharedFlow<Pair<User, Boolean>> = _events.asSharedFlow()

    fun postAccountSaved(user: User, isUpdated: Boolean) {
        _events.tryEmit(Pair(user, isUpdated))
    }
}

object AccountProvisioningHelper {

    private const val TAG = "AccountProvisioning"
    private const val PREFS_NAME = "elqadre_account_sms_prefs"
    private const val KEY_PROCESSED_SMS = "processed_account_sms_keys"

    private fun logD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun logI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun logW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        }
    }

    const val SMS_ALTA_HEADER = "ELQADRE|ALTA_USUARIO|V1|"

    val ALLOWED_ROLES = listOf(
        "DUEÑO",
        "DEPENDIENTE",
        "CAJERO",
        "COCINA"
    )

    /**
     * Formatea el número de negocio para asegurar exactamente 3 dígitos (ej: "001", "042").
     */
    fun formatBusinessNumber3Digits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length == 3) return digits
        val num = digits.toIntOrNull() ?: 1
        return String.format(Locale.ROOT, "%03d", num % 1000)
    }

    /**
     * Mapea un UserRole a uno de los 5 roles exactos permitidos en el protocolo:
     * DUEÑO, ADMINISTRADOR, DEPENDIENTE, CAJERO, COCINA
     */
    fun mapRoleToProtocolString(role: UserRole): String {
        return when (role) {
            UserRole.DUENO -> "DUEÑO"
            UserRole.ADMIN -> "ADMINISTRADOR"
            UserRole.DEPENDIENTE, UserRole.SALON -> "DEPENDIENTE"
            UserRole.CAJERO, UserRole.BARRA -> "CAJERO"
            UserRole.COCINA -> "COCINA"
        }
    }

    /**
     * Mapea el texto del rol validado a UserRole
     */
    fun mapProtocolStringToRole(rolStr: String): UserRole? {
        return when (rolStr.trim().uppercase(Locale.ROOT)) {
            "DUEÑO", "DUENO" -> UserRole.DUENO
            "ADMINISTRADOR", "ADMIN" -> UserRole.ADMIN
            "DEPENDIENTE" -> UserRole.DEPENDIENTE
            "CAJERO" -> UserRole.CAJERO
            "COCINA" -> UserRole.COCINA
            else -> null
        }
    }

    /**
     * Construye el SMS de alta de usuario con el formato único y obligatorio:
     * ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan%20Perez|USUARIO=juanp|CLAVE=abc123|ROL=CAJERO
     *
     * Reglas:
     * - Encabezado exacto: ELQADRE|ALTA_USUARIO|V1|
     * - Campos separados por '|'
     * - NEGOCIO: número del negocio con exactamente 3 dígitos
     * - NOMBRE, USUARIO, CLAVE, ROL codificados en UTF-8 mediante URL encoding (%20 para espacios)
     * - ROL: uno de los 5 exactos (DUEÑO, ADMINISTRADOR, DEPENDIENTE, CAJERO, COCINA)
     * - NO contiene DVC, licencia, prueba, datos de SuperAdmin ni autorización comercial.
     */
    fun buildAccountDeliverySms(
        numeroNegocio: String,
        nombre: String,
        usuario: String,
        contrasenaInicial: String,
        rol: String,
        telefono: String = "",
        montoPorProducto: Double = 0.0,
        negocio: String = ""
    ): String {
        val biz3Digits = formatBusinessNumber3Digits(numeroNegocio)
        
        val normalizedRol = when (rol.trim().uppercase(Locale.ROOT)) {
            "DUEÑO", "DUENO" -> "DUEÑO"
            "ADMINISTRADOR", "ADMIN" -> "ADMINISTRADOR"
            "DEPENDIENTE", "SALÓN", "SALON" -> "DEPENDIENTE"
            "CAJERO", "BARRA" -> "CAJERO"
            "COCINA" -> "COCINA"
            else -> {
                if (ALLOWED_ROLES.contains(rol.trim().uppercase(Locale.ROOT))) {
                    rol.trim().uppercase(Locale.ROOT)
                } else {
                    "DEPENDIENTE"
                }
            }
        }

        val encodedNombre = try {
            URLEncoder.encode(nombre.trim(), "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            nombre.trim()
        }

        val encodedUsuario = try {
            URLEncoder.encode(usuario.trim().lowercase(Locale.ROOT), "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            usuario.trim().lowercase(Locale.ROOT)
        }

        val encodedClave = try {
            URLEncoder.encode(contrasenaInicial.trim(), "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            contrasenaInicial.trim()
        }

        val encodedRol = try {
            URLEncoder.encode(normalizedRol, "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            normalizedRol
        }

        return "$SMS_ALTA_HEADER" +
                "NEGOCIO=$biz3Digits|" +
                "NOMBRE=$encodedNombre|" +
                "USUARIO=$encodedUsuario|" +
                "CLAVE=$encodedClave|" +
                "ROL=$encodedRol"
    }

    /**
     * Identifica el mensaje como SMS de alta ÚNICAMENTE cuando encuentre exactamente:
     * ELQADRE|ALTA_USUARIO|V1|
     * Un SMS que no tenga ese encabezado no debe procesarse como alta de usuario.
     */
    fun isAccountSms(body: String): Boolean {
        if (body.isBlank()) return false
        return body.contains(SMS_ALTA_HEADER)
    }

    /**
     * Parsea y valida el SMS de alta según las reglas especificadas:
     * 1. Leer todos los campos separados por '|'.
     * 2. Validar que estén presentes: NEGOCIO, NOMBRE, USUARIO, CLAVE, ROL.
     * 3. Validar que NEGOCIO tenga exactamente 3 dígitos.
     * 4. Validar que ROL sea uno de los permitidos: DUEÑO, ADMINISTRADOR, DEPENDIENTE, CAJERO, COCINA.
     * 5. Validar usuario y contraseña.
     */
    fun parseAccountSms(body: String, senderPhone: String = ""): AccountSmsData? {
        if (!isAccountSms(body)) return null

        val headerIdx = body.indexOf(SMS_ALTA_HEADER)
        if (headerIdx == -1) return null

        val payload = body.substring(headerIdx + SMS_ALTA_HEADER.length)
        val fields = payload.split('|').map { it.trim() }.filter { it.isNotEmpty() }

        val map = mutableMapOf<String, String>()
        for (field in fields) {
            val eqIdx = field.indexOf('=')
            if (eqIdx != -1) {
                val rawKey = field.substring(0, eqIdx).trim().uppercase(Locale.ROOT)
                val rawVal = field.substring(eqIdx + 1).trim()
                val decodedVal = try {
                    URLDecoder.decode(rawVal, "UTF-8")
                } catch (e: Exception) {
                    rawVal
                }
                map[rawKey] = decodedVal
            }
        }

        // 1. Validar presencia de campos obligatorios
        val negocio = map["NEGOCIO"]?.trim()
        val nombre = map["NOMBRE"]?.trim()
        val usuario = map["USUARIO"]?.trim()
        val clave = map["CLAVE"]?.trim()
        val rolStr = map["ROL"]?.trim()

        if (negocio == null || nombre == null || usuario == null || clave == null || rolStr == null) {
            logW(TAG, "Rechazado SMS de alta: Faltan campos obligatorios en el mensaje.")
            return null
        }

        // 2. Validar que NEGOCIO tenga exactamente 3 dígitos
        if (!Regex("""^\d{3}$""").matches(negocio)) {
            logW(TAG, "Rechazado SMS de alta: NEGOCIO '$negocio' no tiene exactamente 3 dígitos.")
            return null
        }

        // 3. Validar USUARIO y CLAVE
        if (usuario.isBlank() || clave.isBlank()) {
            logW(TAG, "Rechazado SMS de alta: USUARIO o CLAVE están vacíos.")
            return null
        }

        // 4. Validar ROL permitido (DUEÑO, DEPENDIENTE, CAJERO, COCINA)
        val rolUpper = rolStr.uppercase(Locale.ROOT)
        val resolvedRole = when (rolUpper) {
            "DUEÑO", "DUENO" -> UserRole.DUENO
            "DEPENDIENTE", "SALÓN", "SALON" -> UserRole.DEPENDIENTE
            "CAJERO", "BARRA" -> UserRole.CAJERO
            "COCINA" -> UserRole.COCINA
            "ADMINISTRADOR", "ADMIN" -> {
                logW(TAG, "Rechazado SMS de alta: No se permite crear cuentas de ADMINISTRADOR por SMS.")
                return null
            }
            else -> {
                logW(TAG, "Rechazado SMS de alta: ROL '$rolStr' no es uno de los permitidos (DUEÑO, DEPENDIENTE, CAJERO, COCINA).")
                return null
            }
        }

        return AccountSmsData(
            negocio = negocio,
            nombre = if (nombre.isBlank()) usuario else nombre,
            usuario = usuario.lowercase(Locale.ROOT),
            contrasenaInicial = clave,
            rol = resolvedRole,
            rolRaw = rolUpper,
            rawBody = body
        )
    }

    /**
     * Normaliza un número de teléfono quedándose con los últimos 8 dígitos numéricos si es posible.
     */
    fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
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

    /**
     * Procesa un SMS de alta de usuario:
     * 1. Lee todos los campos y valida que estén presentes.
     * 2. Valida que el número de negocio tenga 3 dígitos.
     * 3. Valida que el rol sea uno de los permitidos (DUEÑO, DEPENDIENTE, CAJERO, COCINA).
     * 4. Valida usuario y contraseña.
     * 5. Crea o actualiza la cuenta local (no duplica si ya existe, ignora diferencias de mayúsculas/minúsculas).
     * 6. Asocia la cuenta al negocio indicado (3 dígitos).
     * 7. NO añade DVC, licencia, prueba, SuperAdmin ni autorización comercial.
     */
    suspend fun processAccountSms(
        context: Context,
        body: String,
        senderPhone: String = "",
        smsId: String = ""
    ): AccountProvisioningResult {
        val parsed = parseAccountSms(body, senderPhone)
            ?: return AccountProvisioningResult.IgnoredNotAccountSms

        if (parsed.usuario.equals("admin", ignoreCase = true)) {
            logW(TAG, "Rechazado SMS de alta: No se permite modificar la cuenta inicial de ADMINISTRADOR mediante SMS.")
            return AccountProvisioningResult.Error("No se permite modificar la cuenta de ADMINISTRADOR mediante SMS.")
        }

        val uniqueKey = "ACC_${smsId.ifBlank { parsed.usuario }}_${parsed.contrasenaInicial.hashCode()}_${parsed.negocio}"

        return try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val userDao = db.userDao()
            val personalDao = db.personalContratadoDao()
            val configNegocioDao = db.configuracionNegocioDao()

            val existingUsers = userDao.getAllUsersSync()

            // 1. Localizar cuenta equivalente para no duplicarla si ya existe (sin importar mayúsculas/minúsculas)
            val equivalentUser = existingUsers.firstOrNull {
                it.username.equals(parsed.usuario, ignoreCase = true)
            }

            val savedUser: User
            val isUpdate: Boolean

            if (equivalentUser != null) {
                // Actualizar datos de la cuenta existente
                isUpdate = true
                savedUser = equivalentUser.copy(
                    fullName = parsed.nombre,
                    passwordHash = parsed.contrasenaInicial.toSha256(),
                    role = parsed.rol,
                    isActive = true,
                    permisoProduccion = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO || parsed.rol == UserRole.COCINA),
                    permisoMercancias = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO || parsed.rol == UserRole.CAJERO),
                    permisoPersonal = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO),
                    permisoControlNegocio = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO)
                )
                userDao.updateUser(savedUser)
                logI(TAG, "Cuenta existente actualizada localmente: ${savedUser.username} (${savedUser.role.displayName})")
            } else {
                // Crear cuenta nueva localmente
                isUpdate = false
                savedUser = User(
                    username = parsed.usuario,
                    fullName = parsed.nombre,
                    passwordHash = parsed.contrasenaInicial.toSha256(),
                    role = parsed.rol,
                    isActive = true,
                    telefono = if (senderPhone.isNotBlank()) senderPhone else "",
                    createdAt = System.currentTimeMillis(),
                    permisoProduccion = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO || parsed.rol == UserRole.COCINA),
                    permisoMercancias = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO || parsed.rol == UserRole.CAJERO),
                    permisoPersonal = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO),
                    permisoControlNegocio = (parsed.rol == UserRole.ADMIN || parsed.rol == UserRole.DUENO)
                )
                userDao.insertUser(savedUser)
                logI(TAG, "Cuenta nueva creada localmente: ${savedUser.username} (${savedUser.role.displayName})")
            }

            // 2. Sincronizar en personal_contratado
            try {
                val existingPersonal = personalDao.getPersonalByUsername(savedUser.username)
                val protocolRoleStr = mapRoleToProtocolString(savedUser.role)

                if (existingPersonal != null) {
                    personalDao.updatePersonal(
                        existingPersonal.copy(
                            nombreCompleto = savedUser.fullName,
                            username = savedUser.username,
                            passwordHash = savedUser.passwordHash,
                            role = protocolRoleStr,
                            tieneAccesoApp = true,
                            isActive = true,
                            permisoProduccion = savedUser.permisoProduccion,
                            permisoMercancias = savedUser.permisoMercancias,
                            permisoPersonal = savedUser.permisoPersonal,
                            permisoControlNegocio = savedUser.permisoControlNegocio
                        )
                    )
                } else {
                    personalDao.insertPersonal(
                        PersonalContratado(
                            nombreCompleto = savedUser.fullName,
                            carnetIdentidad = "",
                            movil = savedUser.telefono,
                            formaPago = "Por producto",
                            username = savedUser.username,
                            passwordHash = savedUser.passwordHash,
                            role = protocolRoleStr,
                            montoPorProducto = savedUser.montoPorProducto,
                            tieneAccesoApp = true,
                            isActive = true,
                            permisoProduccion = savedUser.permisoProduccion,
                            permisoMercancias = savedUser.permisoMercancias,
                            permisoPersonal = savedUser.permisoPersonal,
                            permisoControlNegocio = savedUser.permisoControlNegocio
                        )
                    )
                }
            } catch (pe: Exception) {
                logW(TAG, "Advertencia al sincronizar personal contratado: ${pe.message}")
            }

            // 3. Asociar al negocio indicado (código de negocio de 3 dígitos)
            try {
                val currentConfig = configNegocioDao.getConfigSync()
                val updatedConfig = (currentConfig ?: ConfiguracionNegocio(
                    nombreNegocio = "ElQadre",
                    codigoNegocio = parsed.negocio
                )).copy(
                    codigoNegocio = parsed.negocio
                )
                configNegocioDao.insertConfig(updatedConfig)
                logI(TAG, "Negocio asociado al código #${parsed.negocio}")
            } catch (ce: Exception) {
                logW(TAG, "Advertencia al asociar negocio: ${ce.message}")
            }

            // 4. Marcar SMS como procesado y notificar vía Bus
            markSmsAsProcessed(context, uniqueKey)
            AccountProvisioningBus.postAccountSaved(savedUser, isUpdate)

            val actionLabel = if (isUpdate) "actualizada" else "creada"
            AccountProvisioningResult.Success(
                user = savedUser,
                isUpdate = isUpdate,
                message = "Cuenta $actionLabel localmente: ${savedUser.username} (${savedUser.role.displayName})"
            )
        } catch (e: Exception) {
            logE(TAG, "Error procesando SMS de alta de usuario", e)
            AccountProvisioningResult.Error("Error al procesar alta de usuario: ${e.message}")
        }
    }

    /**
     * Escanea el buzón de entrada de SMS (content://sms/inbox) en busca de SMS de alta.
     * Si forceReprocess es true, procesa/actualiza incluso si ya fue escaneado anteriormente.
     */
    fun scanAndProcessInbox(context: Context, forceReprocess: Boolean = false): List<AccountProvisioningResult> {
        val results = mutableListOf<AccountProvisioningResult>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            logD(TAG, "Permiso READ_SMS no concedido. Omitiendo escaneo de bandeja de entrada.")
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

                while (c.moveToNext()) {
                    val smsId = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                    val address = if (addressCol >= 0) c.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""

                    if (isAccountSms(body)) {
                        val parsed = parseAccountSms(body, address)
                        if (parsed != null) {
                            val uniqueKey = "ACC_${smsId.ifBlank { parsed.usuario }}_${parsed.contrasenaInicial.hashCode()}_${parsed.negocio}"
                            if (forceReprocess || !isSmsProcessed(context, uniqueKey)) {
                                kotlinx.coroutines.runBlocking {
                                    val res = processAccountSms(context, body, address, smsId)
                                    results.add(res)
                                }
                            }
                        }
                    }
                }
            }
        } catch (se: SecurityException) {
            logW(TAG, "SecurityException al escanear bandeja de SMS: ${se.message}")
        } catch (e: Exception) {
            logE(TAG, "Error escaneando bandeja de SMS para cuentas", e)
        }

        return results
    }
}
