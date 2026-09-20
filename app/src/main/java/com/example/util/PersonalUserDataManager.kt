package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.ConfiguracionNegocio
import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.licensing.BusinessCodeHelper
import com.example.licensing.CommercialLicenseManager
import com.example.util.toSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PersonalUserDataManager {

    sealed class ImportResult {
        data class Success(
            val username: String,
            val fullName: String,
            val roleDisplayName: String,
            val businessCode: String
        ) : ImportResult()

        data class Error(val message: String) : ImportResult()
    }

    /**
     * Identifica si un objeto JSON corresponde a un archivo de Usuario de ElQadre.
     */
    fun isPersonalUserJson(root: JSONObject): Boolean {
        val tipo = root.optString("tipo", "").trim().uppercase()
        if (tipo == "USUARIO_ELQADRE") return true
        return root.has("persona") || root.has("usuario")
    }

    /**
     * Genera un archivo JSON individual y compacto correspondiente a una única persona contratada / usuario.
     */
    fun generatePersonalUserJson(
        context: Context,
        personal: PersonalContratado,
        configNegocio: ConfiguracionNegocio?,
        configGeneral: ConfiguracionGeneral?
    ): File {
        val licenseMgr = CommercialLicenseManager.getInstance(context)
        val licenseInfo = licenseMgr.licenseInfo.value
        val rawBizCode = BusinessCodeHelper.resolveBusinessCode(
            context = context,
            configNegocio = configNegocio,
            configGeneral = configGeneral,
            licenseInfo = licenseInfo
        )
        val businessNumber = BusinessCodeHelper.formatCode(rawBizCode)
        val businessCode = configNegocio?.codigoNegocio?.ifBlank { "NEG-$businessNumber" } ?: "NEG-$businessNumber"
        val businessName = configNegocio?.nombreNegocio?.ifBlank { "ElQadre" } ?: "ElQadre"

        val root = JSONObject().apply {
            put("tipo", "USUARIO_ELQADRE")
            put("version", "1.0")
            put("negocio", businessName)
            put("nombreNegocio", businessName)
            put("numeroNegocio", businessNumber)
            put("codigoNegocio", businessCode)

            val personaObj = JSONObject().apply {
                put("id", personal.id)
                put("nombreCompleto", personal.nombreCompleto)
                put("carnetIdentidad", personal.carnetIdentidad)
                put("movil", personal.movil)
                put("formaPago", personal.formaPago)
            }
            put("persona", personaObj)

            val passHash = if (personal.passwordHash.isNotBlank()) {
                personal.passwordHash
            } else if (personal.passwordPlain.isNotBlank()) {
                personal.passwordPlain.trim().toSha256()
            } else {
                ""
            }

            val permisosObj = JSONObject().apply {
                put("permisoProduccion", personal.permisoProduccion)
                put("permisoMercancias", personal.permisoMercancias)
                put("permisoPersonal", personal.permisoPersonal)
                put("permisoControlNegocio", personal.permisoControlNegocio)
                put("dependienteTipo", personal.dependienteTipo)
                put("montoPorProducto", personal.montoPorProducto)
            }

            val usuarioObj = JSONObject().apply {
                put("usuario", personal.username)
                put("username", personal.username)
                put("contrasena", personal.passwordPlain)
                put("passwordPlain", personal.passwordPlain)
                put("passwordHash", passHash)
                put("rol", personal.role)
                put("role", personal.role)
                put("estado", personal.isActive)
                put("isActive", personal.isActive)
                put("permisos", permisosObj)
                put("dependienteTipo", personal.dependienteTipo)
                put("montoPorProducto", personal.montoPorProducto)
                put("permisoProduccion", personal.permisoProduccion)
                put("permisoMercancias", personal.permisoMercancias)
                put("permisoPersonal", personal.permisoPersonal)
                put("permisoControlNegocio", personal.permisoControlNegocio)
            }
            put("usuario", usuarioObj)

            put("timestamp", System.currentTimeMillis())
        }

        val jsonString = root.toString(4)
        val cleanUsername = personal.username.trim().lowercase().ifBlank { "personal_${personal.id}" }
        val fileName = "Q_${businessNumber}_user_${cleanUsername}.json"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var targetFile = File(downloadsDir, fileName)

        try {
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            FileOutputStream(targetFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            targetFile = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
            FileOutputStream(targetFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
        }

        return targetFile
    }

    /**
     * Utiliza el mecanismo nativo de compartir de Android para enviar el archivo JSON por WhatsApp u otra app disponible.
     */
    fun sharePersonalUserViaWhatsApp(
        context: Context,
        personal: PersonalContratado,
        jsonFile: File
    ) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jsonFile
        )

        val roleLabel = when (personal.role.uppercase()) {
            "DUENO", "DUEÑO" -> "DUEÑO"
            "CAJERO" -> "CAJERO"
            else -> "DEPENDIENTE (${if (personal.dependienteTipo == "BARRA") "Barra" else "Salón"})"
        }

        val recipientPhoneInfo = if (personal.movil.isNotBlank()) " (Destinatario: ${personal.movil})" else ""
        val shareText = "Hola ${personal.nombreCompleto},\n\n" +
                "Se ha generado tu archivo individual de acceso a ElQadre$recipientPhoneInfo:\n" +
                "• Usuario: ${personal.username}\n" +
                "• Rol: $roleLabel\n\n" +
                "Instrucciones de primer acceso:\n" +
                "1. Abre ElQadre e introduce el token del negocio en PORTADA si es un dispositivo nuevo.\n" +
                "2. En la pantalla de Login, pulsa 'CARGAR DATOS' y selecciona este archivo JSON adjunto.\n" +
                "3. Inicia sesión con tu usuario y contraseña."

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Credenciales ElQadre - ${personal.nombreCompleto}")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Compartir datos de acceso (${personal.nombreCompleto}${if (personal.movil.isNotBlank()) " - ${personal.movil}" else ""})")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Importa y valida localmente el archivo JSON individual de una persona/usuario recibido en el dispositivo.
     */
    suspend fun importPersonalUserJson(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("No se pudo abrir el archivo seleccionado.")

            val jsonString = inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            if (jsonString.isBlank()) {
                return@withContext ImportResult.Error("El archivo seleccionado está vacío.")
            }

            val root = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                return@withContext ImportResult.Error("El archivo no contiene un formato JSON válido.")
            }

            // 1. Validar estructura básica
            val personaObj = root.optJSONObject("persona")
            val usuarioObj = root.optJSONObject("usuario")

            if (personaObj == null && usuarioObj == null) {
                return@withContext ImportResult.Error("Estructura de archivo inválida: no contiene datos de persona o usuario.")
            }

            val nombreCompleto = personaObj?.optString("nombreCompleto")?.trim()
                ?: usuarioObj?.optString("fullName")?.trim()
                ?: usuarioObj?.optString("nombreCompleto")?.trim() ?: ""
            val carnetIdentidad = personaObj?.optString("carnetIdentidad")?.trim() ?: ""
            val movil = personaObj?.optString("movil")?.trim()
                ?: usuarioObj?.optString("telefono")?.trim()
                ?: usuarioObj?.optString("movil")?.trim() ?: ""
            val formaPago = personaObj?.optString("formaPago")?.trim() ?: "A convenir"

            val username = (usuarioObj?.optString("usuario")?.takeIf { it.isNotBlank() }
                ?: usuarioObj?.optString("username"))?.trim()?.lowercase() ?: ""
            var passwordHash = usuarioObj?.optString("passwordHash")?.trim() ?: ""
            val passwordPlain = (usuarioObj?.optString("contrasena")?.takeIf { it.isNotBlank() }
                ?: usuarioObj?.optString("passwordPlain"))?.trim() ?: ""
            if (passwordHash.isBlank() && passwordPlain.isNotBlank()) {
                passwordHash = passwordPlain.trim().toSha256()
            }

            val rawRole = (usuarioObj?.optString("rol")?.takeIf { it.isNotBlank() }
                ?: usuarioObj?.optString("role"))?.trim()?.uppercase() ?: ""
            val permisosObj = usuarioObj?.optJSONObject("permisos")
            val dependienteTipo = (permisosObj?.optString("dependienteTipo")?.takeIf { it.isNotBlank() }
                ?: usuarioObj?.optString("dependienteTipo"))?.trim()?.uppercase() ?: "SALON"
            val montoPorProducto = permisosObj?.optDouble("montoPorProducto", usuarioObj?.optDouble("montoPorProducto", 0.0) ?: 0.0)
                ?: usuarioObj?.optDouble("montoPorProducto", 0.0) ?: 0.0
            val isActive = if (usuarioObj?.has("estado") == true) {
                usuarioObj.optBoolean("estado", true)
            } else {
                usuarioObj?.optBoolean("isActive", true) ?: true
            }

            val permisoProduccion = permisosObj?.optBoolean("permisoProduccion", usuarioObj?.optBoolean("permisoProduccion", true) ?: true)
                ?: usuarioObj?.optBoolean("permisoProduccion", true) ?: true
            val permisoMercancias = permisosObj?.optBoolean("permisoMercancias", usuarioObj?.optBoolean("permisoMercancias", true) ?: true)
                ?: usuarioObj?.optBoolean("permisoMercancias", true) ?: true
            val permisoPersonal = permisosObj?.optBoolean("permisoPersonal", usuarioObj?.optBoolean("permisoPersonal", true) ?: true)
                ?: usuarioObj?.optBoolean("permisoPersonal", true) ?: true
            val permisoControlNegocio = permisosObj?.optBoolean("permisoControlNegocio", usuarioObj?.optBoolean("permisoControlNegocio", false) ?: false)
                ?: usuarioObj?.optBoolean("permisoControlNegocio", false) ?: false

            if (username.isBlank()) {
                return@withContext ImportResult.Error("El archivo no contiene un nombre de usuario válido.")
            }

            if (rawRole == "ADMIN" || rawRole == "ADMINISTRADOR") {
                return@withContext ImportResult.Error("El rol Administrador ha sido eliminado y no es válido.")
            }

            // 2. Validar correspondencia de negocio
            val fileBizNumber = root.optString("numeroNegocio").trim()
            val fileBizCode = root.optString("codigoNegocio").trim()

            val configNegocio = db.configuracionNegocioDao().getConfigSync()
            val configGeneral = db.configuracionGeneralDao().getConfigSync()
            val licenseMgr = CommercialLicenseManager.getInstance(context)
            val licenseInfo = licenseMgr.licenseInfo.value
            val deviceRawBiz = BusinessCodeHelper.resolveBusinessCode(
                context = context,
                configNegocio = configNegocio,
                configGeneral = configGeneral,
                licenseInfo = licenseInfo
            )
            val deviceBizNumber = BusinessCodeHelper.formatCode(deviceRawBiz)

            // Si el dispositivo ya tiene un negocio asignado/autorizado, debe coincidir
            if (deviceBizNumber.isNotBlank() && deviceBizNumber != "000") {
                val fileBizRaw = if (fileBizNumber.isNotBlank()) fileBizNumber else fileBizCode
                val normalizedFileBiz = BusinessCodeHelper.formatCode(fileBizRaw)
                if (normalizedFileBiz.isNotBlank() && normalizedFileBiz != deviceBizNumber) {
                    return@withContext ImportResult.Error(
                        "El archivo pertenece al negocio $normalizedFileBiz, pero este dispositivo está vinculado al negocio $deviceBizNumber. Un archivo de otro negocio no puede cargarse."
                    )
                }
            }

            // 3. Mapear rol a UserRole
            val mappedRole = when (rawRole) {
                "DUENO", "DUEÑO" -> UserRole.DUENO
                "CAJERO" -> UserRole.CAJERO
                "DEPENDIENTE", "SALON", "DEPENDIENTE DE SALÓN" -> {
                    if (dependienteTipo == "BARRA") UserRole.BARRA else UserRole.SALON
                }
                "BARRA", "DEPENDIENTE DE BARRA" -> UserRole.BARRA
                else -> {
                    if (dependienteTipo == "BARRA") UserRole.BARRA else UserRole.SALON
                }
            }

            val storedRole = when (mappedRole) {
                UserRole.DUENO -> "DUENO"
                UserRole.CAJERO -> "CAJERO"
                else -> "DEPENDIENTE"
            }

            // 4. Buscar duplicados en PersonalContratado
            val existingPersonal = if (carnetIdentidad.isNotBlank()) {
                db.personalContratadoDao().getPersonalByCarnet(carnetIdentidad)
            } else {
                db.personalContratadoDao().getPersonalByUsername(username)
            }

            // 5. Validar límite estricto de máximo 3 DUEÑOS en el negocio
            if (mappedRole == UserRole.DUENO) {
                val currentDuenos = if (existingPersonal != null) {
                    db.personalContratadoDao().countDuenosExcluding(existingPersonal.id)
                } else {
                    db.personalContratadoDao().countDuenos()
                }
                if (currentDuenos >= 3) {
                    return@withContext ImportResult.Error(
                        "Límite alcanzado: El negocio ya cuenta con el máximo de 3 DUEÑOS permitidos (el principal y hasta 2 adicionales)."
                    )
                }
            }

            val finalPersonal = if (existingPersonal != null) {
                existingPersonal.copy(
                    nombreCompleto = nombreCompleto.ifBlank { existingPersonal.nombreCompleto },
                    carnetIdentidad = carnetIdentidad.ifBlank { existingPersonal.carnetIdentidad },
                    movil = movil.ifBlank { existingPersonal.movil },
                    formaPago = formaPago.ifBlank { existingPersonal.formaPago },
                    tieneAccesoApp = true,
                    username = username,
                    passwordHash = passwordHash.ifBlank { existingPersonal.passwordHash },
                    passwordPlain = passwordPlain.ifBlank { existingPersonal.passwordPlain },
                    role = storedRole,
                    dependienteTipo = if (mappedRole == UserRole.BARRA) "BARRA" else "SALON",
                    montoPorProducto = montoPorProducto,
                    isActive = isActive,
                    permisoProduccion = permisoProduccion,
                    permisoMercancias = permisoMercancias,
                    permisoPersonal = permisoPersonal,
                    permisoControlNegocio = permisoControlNegocio
                )
            } else {
                PersonalContratado(
                    nombreCompleto = nombreCompleto.ifBlank { username },
                    carnetIdentidad = carnetIdentidad,
                    movil = movil,
                    formaPago = formaPago,
                    tieneAccesoApp = true,
                    username = username,
                    passwordHash = passwordHash,
                    passwordPlain = passwordPlain,
                    role = storedRole,
                    dependienteTipo = if (mappedRole == UserRole.BARRA) "BARRA" else "SALON",
                    montoPorProducto = montoPorProducto,
                    isActive = isActive,
                    permisoProduccion = permisoProduccion,
                    permisoMercancias = permisoMercancias,
                    permisoPersonal = permisoPersonal,
                    permisoControlNegocio = permisoControlNegocio
                )
            }

            if (existingPersonal != null) {
                db.personalContratadoDao().updatePersonal(finalPersonal)
            } else {
                db.personalContratadoDao().insertPersonal(finalPersonal)
            }

            // 6. Crear o actualizar registro de usuario en users
            val newUser = User(
                username = username,
                fullName = nombreCompleto.ifBlank { username },
                passwordHash = passwordHash,
                role = mappedRole,
                montoPorProducto = montoPorProducto,
                isActive = isActive,
                telefono = movil,
                permisoProduccion = permisoProduccion,
                permisoMercancias = permisoMercancias,
                permisoPersonal = permisoPersonal,
                permisoControlNegocio = permisoControlNegocio
            )
            db.userDao().insertUser(newUser)

            // 7. Marcar actualización de usuarios como COMPLETADA para que el login offline esté disponible de inmediato
            if (configGeneral != null) {
                db.configuracionGeneralDao().updateConfig(
                    configGeneral.copy(
                        lastUserUpdateStatus = UserUpdateManager.STATUS_SUCCESS,
                        lastUserUpdateDate = System.currentTimeMillis()
                    )
                )
            }

            ImportResult.Success(
                username = username,
                fullName = nombreCompleto.ifBlank { username },
                roleDisplayName = mappedRole.displayName,
                businessCode = fileBizNumber.ifBlank { deviceBizNumber }
            )
        } catch (e: Exception) {
            ImportResult.Error("Error al importar datos: ${e.localizedMessage ?: e.message}")
        }
    }
}
