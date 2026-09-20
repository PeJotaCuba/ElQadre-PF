package com.example.util

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UserUpdateManager {
    const val STATUS_PENDIENTE = "PENDIENTE"
    const val STATUS_SUCCESS = "COMPLETADO"
    const val STATUS_ERROR_NETWORK = "ERROR_RED"
    const val STATUS_ERROR_INVALID_FILE = "ARCHIVO_INVALIDO"

    sealed class UpdateResult {
        object Success : UpdateResult()
        object NoConnection : UpdateResult()
        object DownloadError : UpdateResult()
        object InvalidFile : UpdateResult()
        object NoNewUpdate : UpdateResult()
    }

    private fun cleanJsonString(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim()
        }
        if (s.startsWith("```")) {
            s = s.replaceFirst(Regex("^```(?:json)?\\s*"), "")
            if (s.endsWith("```")) {
                s = s.substring(0, s.length - 3).trim()
            }
        }
        return s.trim()
    }

    private fun fetchUrlWithRedirects(urlString: String, maxRedirects: Int = 5): Pair<Int, String?> {
        var currentUrl = urlString.trim()
        var redirects = 0
        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")

            try {
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(url, location).toString()
                        }
                        redirects++
                        continue
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line).append("\n")
                    }
                    reader.close()
                    return Pair(responseCode, response.toString())
                } else {
                    return Pair(responseCode, null)
                }
            } finally {
                connection.disconnect()
            }
        }
        return Pair(-1, null)
    }

    /**
     * Construye la URL oficial remota para el archivo de usuarios del negocio especificado.
     * Siempre utiliza formato de 3 dígitos (ej: 001 -> Q_001usuarios.json).
     */
    fun buildBusinessUsersUrl(businessCode: String): String {
        val formatted = com.example.licensing.BusinessCodeHelper.formatCode(businessCode)
        return "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_${formatted}usuarios.json"
    }

    suspend fun updateUsers(
        db: AppDatabase,
        urlString: String,
        expectedDvc: String = "",
        expectedBusinessCode: String = ""
    ): UpdateResult = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext UpdateResult.DownloadError

        try {
            val (code, jsonBody) = fetchUrlWithRedirects(urlString)
            if (code != HttpURLConnection.HTTP_OK || jsonBody == null) {
                return@withContext UpdateResult.DownloadError
            }

            val cleaned = cleanJsonString(jsonBody)
            return@withContext processUserJson(db, cleaned, urlString, expectedDvc, expectedBusinessCode)
        } catch (e: java.net.UnknownHostException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: Exception) {
            Log.e("UserUpdateManager", "Error updating users", e)
            return@withContext UpdateResult.DownloadError
        }
    }

    fun parseRole(roleStr: String): UserRole? {
        val normalized = roleStr.uppercase().trim()
            .replace(" ", "_")
            .replace("Ó", "O")
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ú", "U")

        return when (normalized) {
            "ADMIN", "ADMINISTRADOR" -> UserRole.ADMIN
            "CAJERO", "CAJA" -> UserRole.CAJERO
            "SALON", "DEPENDIENTE_SALON", "DEPENDIENTE_DE_SALON" -> UserRole.SALON
            "BARRA", "DEPENDIENTE_BARRA", "DEPENDIENTE_DE_BARRA" -> UserRole.BARRA
            "DUENO", "DUEÑO", "OWNER" -> UserRole.DUENO
            else -> {
                try {
                    UserRole.valueOf(normalized)
                } catch (e: Exception) {
                    UserRole.values().firstOrNull {
                        it.name.equals(normalized, ignoreCase = true) ||
                        it.displayName.equals(roleStr.trim(), ignoreCase = true)
                    }
                }
            }
        }
    }

    suspend fun processUserJson(
        db: AppDatabase,
        jsonString: String,
        sourceUrl: String = "",
        expectedDvc: String = "",
        expectedBusinessCode: String = ""
    ): UpdateResult {
        try {
            val cleaned = cleanJsonString(jsonString)
            val jsonObject = JSONObject(cleaned)

            // Validar que corresponde al negocio autorizado si el JSON incluye metadatos
            val negocioObj = jsonObject.optJSONObject("negocio")
            val fileCode = (negocioObj?.optString("codigo") ?: jsonObject.optString("codigoNegocio", jsonObject.optString("codigo_negocio", ""))).trim()
            if (negocioObj != null) {
                val fileDvc = negocioObj.optString("dvc", "").trim().replace("DVC-", "", ignoreCase = true)
                val cleanExpectedDvc = expectedDvc.trim().replace("DVC-", "", ignoreCase = true)

                if (cleanExpectedDvc.isNotBlank() && fileDvc.isNotBlank() && !cleanExpectedDvc.equals(fileDvc, ignoreCase = true)) {
                    Log.w("UserUpdateManager", "DVC mismatch: expected $cleanExpectedDvc but file has $fileDvc")
                    return UpdateResult.InvalidFile
                }
            }
            if (expectedBusinessCode.isNotBlank() && fileCode.isNotBlank()) {
                val fmtExpected = com.example.licensing.BusinessCodeHelper.formatCode(expectedBusinessCode)
                val fmtFile = com.example.licensing.BusinessCodeHelper.formatCode(fileCode)
                if (!fmtExpected.equals(fmtFile, ignoreCase = true)) {
                    Log.w("UserUpdateManager", "Business code mismatch: expected $fmtExpected but file has $fmtFile")
                    return UpdateResult.InvalidFile
                }
            }

            val usuariosArray = when {
                jsonObject.has("usuarios") -> jsonObject.optJSONArray("usuarios")
                jsonObject.has("users") -> jsonObject.optJSONArray("users")
                else -> null
            } ?: return UpdateResult.InvalidFile

            val remoteVersion = jsonObject.optString("version", System.currentTimeMillis().toString())

            // Negocio extraction if present
            val codigoNegocioRaw = jsonObject.optString("codigoNegocio",
                jsonObject.optString("codigo_negocio",
                negocioObj?.optString("codigo",
                negocioObj?.optString("codigoNegocio", "")) ?: "")).trim()
            val nombreNegocioRaw = (negocioObj?.optString("nombre") ?: jsonObject.optString("nombreNegocio", "")).trim()

            val bizCodeFormatted = if (codigoNegocioRaw.isNotBlank()) {
                com.example.licensing.BusinessCodeHelper.formatCode(codigoNegocioRaw)
            } else ""

            // Extract URLs if provided in Q_XXXusuarios.json
            var urlUsuarios = jsonObject.optString("urlUsuariosJson",
                jsonObject.optString("urlUsuarios",
                jsonObject.optString("url_usuarios",
                jsonObject.optString("url_usuarios_json", "")))).trim()

            var urlCatalogo = jsonObject.optString("urlCatalogoJson",
                jsonObject.optString("urlCatalogo",
                jsonObject.optString("url_catalogo",
                jsonObject.optString("url_catalogo_json", "")))).trim()

            val urlMercainv = jsonObject.optString("urlMercainvJson",
                jsonObject.optString("urlMercainv",
                jsonObject.optString("url_mercainv",
                jsonObject.optString("url_mercainv_json", "")))).trim()

            var urlQDueno = jsonObject.optString("urlDueño",
                jsonObject.optString("urlDueno",
                jsonObject.optString("urlQDuenoJson",
                jsonObject.optString("urlQDueno",
                jsonObject.optString("url_dueño",
                jsonObject.optString("url_dueno",
                jsonObject.optString("url_q_dueno",
                jsonObject.optString("url_q_dueno_json", "")))))))).trim()

            var urlAdmin = jsonObject.optString("urlAdmin",
                jsonObject.optString("urlAdminJson",
                jsonObject.optString("url_admin",
                jsonObject.optString("url_admin_json", "")))).trim()

            if (bizCodeFormatted.isNotBlank()) {
                if (urlUsuarios.isBlank()) urlUsuarios = com.example.licensing.BusinessCodeHelper.buildUrlUsuarios(bizCodeFormatted)
                if (urlCatalogo.isBlank()) urlCatalogo = com.example.licensing.BusinessCodeHelper.buildUrlCatalogo(bizCodeFormatted)
                if (urlQDueno.isBlank()) urlQDueno = com.example.licensing.BusinessCodeHelper.buildUrlDueño(bizCodeFormatted)
                if (urlAdmin.isBlank()) urlAdmin = com.example.licensing.BusinessCodeHelper.buildUrlAdmin(bizCodeFormatted)
            }

            val urlVersion = jsonObject.optString("versionJsonUrl",
                jsonObject.optString("urlVersionJson",
                jsonObject.optString("urlVersion", ""))).trim()

            val hasTelefonoDueno = jsonObject.has("telefonoDueno") ||
                    jsonObject.has("telefono_dueno") ||
                    jsonObject.has("ownerPhone") ||
                    jsonObject.has("telefonoOwner")

            val telefonoDuenoFromExt = jsonObject.optString("telefonoDueno",
                jsonObject.optString("telefono_dueno",
                jsonObject.optString("ownerPhone",
                jsonObject.optString("telefonoOwner", "")))).trim()

            val hasTelefonoCajero = jsonObject.has("telefonoCajero") ||
                    jsonObject.has("telefono_cajero") ||
                    jsonObject.has("cajeroPhone") ||
                    jsonObject.has("telefonoCaja")

            val telefonoCajeroFromExt = jsonObject.optString("telefonoCajero",
                jsonObject.optString("telefono_cajero",
                jsonObject.optString("cajeroPhone",
                jsonObject.optString("telefonoCaja", "")))).trim()

            val hasTelefonoAdmin = jsonObject.has("telefonoAdmin") ||
                    jsonObject.has("telefono_admin") ||
                    jsonObject.has("adminPhone")

            val telefonoAdminFromExt = jsonObject.optString("telefonoAdmin",
                jsonObject.optString("telefono_admin",
                jsonObject.optString("adminPhone", ""))).trim()

            val newUsers = mutableListOf<Pair<User, Boolean>>()

            for (i in 0 until usuariosArray.length()) {
                val userObj = usuariosArray.optJSONObject(i) ?: continue

                val username = userObj.optString("username", userObj.optString("user", userObj.optString("usuario", ""))).trim()
                val fullName = userObj.optString("fullName", userObj.optString("name", userObj.optString("nombre", username))).trim()
                var passwordHash = userObj.optString("passwordHash", userObj.optString("password_hash", userObj.optString("hash", userObj.optString("password", "")))).trim()
                val roleStr = userObj.optString("role", userObj.optString("rol", "")).trim()
                val montoPorProducto = userObj.optDouble("montoPorProducto", userObj.optDouble("monto_por_producto", userObj.optDouble("monto", 0.0)))
                val isActive = userObj.optBoolean("isActive", userObj.optBoolean("is_active", userObj.optBoolean("activo", true)))
                val hasExplicitPhone = userObj.has("telefono") || userObj.has("phone") || userObj.has("celular")
                val userPhone = userObj.optString("telefono", userObj.optString("phone", userObj.optString("celular", ""))).trim()

                if (username.isBlank() || roleStr.isBlank()) {
                    continue
                }

                val role = parseRole(roleStr) ?: continue

                if (passwordHash.isBlank()) {
                    passwordHash = "1234".toSha256()
                } else if (passwordHash.length != 64 || !passwordHash.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                    passwordHash = passwordHash.toSha256()
                }

                val user = User(
                    username = username,
                    fullName = if (fullName.isNotBlank()) fullName else username,
                    passwordHash = passwordHash,
                    role = role,
                    montoPorProducto = montoPorProducto,
                    isActive = isActive,
                    createdAt = System.currentTimeMillis(),
                    telefono = userPhone,
                    permisoProduccion = if (role == UserRole.DUENO) {
                        val permissionsObj = userObj.optJSONObject("permissions") ?: userObj.optJSONObject("permisos")
                        when {
                            permissionsObj != null && permissionsObj.has("produccion") -> permissionsObj.optBoolean("produccion", true)
                            permissionsObj != null && permissionsObj.has("controlProduccion") -> permissionsObj.optBoolean("controlProduccion", true)
                            userObj.has("permisoProduccion") -> userObj.optBoolean("permisoProduccion", true)
                            userObj.has("produccion") -> userObj.optBoolean("produccion", true)
                            else -> true
                        }
                    } else true,
                    permisoMercancias = if (role == UserRole.DUENO) {
                        val permissionsObj = userObj.optJSONObject("permissions") ?: userObj.optJSONObject("permisos")
                        when {
                            permissionsObj != null && permissionsObj.has("mercancias") -> permissionsObj.optBoolean("mercancias", true)
                            permissionsObj != null && permissionsObj.has("controlMercancias") -> permissionsObj.optBoolean("controlMercancias", true)
                            userObj.has("permisoMercancias") -> userObj.optBoolean("permisoMercancias", true)
                            userObj.has("mercancias") -> userObj.optBoolean("mercancias", true)
                            else -> true
                        }
                    } else true,
                    permisoPersonal = if (role == UserRole.DUENO) {
                        val permissionsObj = userObj.optJSONObject("permissions") ?: userObj.optJSONObject("permisos")
                        when {
                            permissionsObj != null && permissionsObj.has("personal") -> permissionsObj.optBoolean("personal", true)
                            permissionsObj != null && permissionsObj.has("controlPersonal") -> permissionsObj.optBoolean("controlPersonal", true)
                            userObj.has("permisoPersonal") -> userObj.optBoolean("permisoPersonal", true)
                            userObj.has("personal") -> userObj.optBoolean("personal", true)
                            else -> true
                        }
                    } else true,
                    permisoControlNegocio = true
                )
                newUsers.add(Pair(user, hasExplicitPhone))
            }

            if (newUsers.isEmpty()) {
                return UpdateResult.InvalidFile
            }

            val latestConfig = db.configuracionGeneralDao().getConfigSync()
            val existingUsers = db.userDao().getAllUsersSync()

            val finalUrlUsuarios = when {
                urlUsuarios.isNotBlank() -> urlUsuarios
                sourceUrl.isNotBlank() -> sourceUrl
                latestConfig != null && latestConfig.urlUsuariosJson.isNotBlank() -> latestConfig.urlUsuariosJson
                else -> ""
            }
            val finalUrlCatalogo = when {
                urlCatalogo.isNotBlank() -> urlCatalogo
                latestConfig != null && latestConfig.urlCatalogoJson.isNotBlank() -> latestConfig.urlCatalogoJson
                else -> ""
            }
            val finalUrlMercainv = when {
                urlMercainv.isNotBlank() -> urlMercainv
                latestConfig != null && latestConfig.urlMercainvJson.isNotBlank() -> latestConfig.urlMercainvJson
                else -> ""
            }
            val finalUrlQDueno = when {
                urlQDueno.isNotBlank() -> urlQDueno
                latestConfig != null && latestConfig.urlQDuenoJson.isNotBlank() -> latestConfig.urlQDuenoJson
                else -> ""
            }
            val finalUrlVersion = when {
                urlVersion.isNotBlank() -> urlVersion
                latestConfig != null && latestConfig.urlVersionJson.isNotBlank() -> latestConfig.urlVersionJson
                else -> ""
            }
            val finalTelefonoDueno = if (hasTelefonoDueno) {
                telefonoDuenoFromExt
            } else {
                latestConfig?.telefonoDueno ?: ""
            }
            val finalTelefonoCajero = if (hasTelefonoCajero) {
                telefonoCajeroFromExt
            } else {
                latestConfig?.telefonoCajero ?: ""
            }
            val finalTelefonoAdmin = if (hasTelefonoAdmin) {
                telefonoAdminFromExt
            } else {
                latestConfig?.telefonoAdmin ?: ""
            }

            // Check if there are actual changes
            if (latestConfig != null && latestConfig.lastUserUpdateStatus == STATUS_SUCCESS) {
                var hasChanges = false

                if (existingUsers.size != newUsers.size) {
                    hasChanges = true
                } else {
                    for ((newUser, hasPhone) in newUsers) {
                        val existing = existingUsers.find { it.username == newUser.username }
                        if (existing == null) {
                            hasChanges = true
                            break
                        }
                        if (existing.fullName != newUser.fullName ||
                            existing.passwordHash != newUser.passwordHash ||
                            existing.role != newUser.role ||
                            existing.montoPorProducto != newUser.montoPorProducto ||
                            existing.isActive != newUser.isActive ||
                            (hasPhone && existing.telefono != newUser.telefono) ||
                            (newUser.role == UserRole.DUENO && (
                                existing.permisoProduccion != newUser.permisoProduccion ||
                                existing.permisoMercancias != newUser.permisoMercancias ||
                                existing.permisoPersonal != newUser.permisoPersonal
                            ))
                        ) {
                            hasChanges = true
                            break
                        }
                    }
                }

                if (!hasChanges) {
                    if (latestConfig.urlUsuariosJson != finalUrlUsuarios ||
                        latestConfig.urlCatalogoJson != finalUrlCatalogo ||
                        latestConfig.urlMercainvJson != finalUrlMercainv ||
                        latestConfig.urlQDuenoJson != finalUrlQDueno ||
                        latestConfig.urlVersionJson != finalUrlVersion ||
                        latestConfig.telefonoDueno != finalTelefonoDueno ||
                        latestConfig.telefonoCajero != finalTelefonoCajero ||
                        latestConfig.telefonoAdmin != finalTelefonoAdmin
                    ) {
                        hasChanges = true
                    }
                }

                if (!hasChanges) {
                    return UpdateResult.NoNewUpdate
                }
            }

            val finalVersionToSave = if (remoteVersion.isNotBlank()) remoteVersion else System.currentTimeMillis().toString()

            db.withTransaction {
                // Ensure business isolation: remove local users that do not belong to this business
                val newUsernameSet = newUsers.map { it.first.username.lowercase() }.toSet()
                existingUsers.forEach { existing ->
                    if (!newUsernameSet.contains(existing.username.lowercase())) {
                        db.userDao().deleteUser(existing.username)
                    }
                }

                newUsers.forEach { (user, hasExplicitPhone) ->
                    val existingUser = db.userDao().getUserByUsername(user.username)
                    if (existingUser != null) {
                        val finalPhone = if (hasExplicitPhone) user.telefono else existingUser.telefono
                        db.userDao().updateUser(
                            existingUser.copy(
                                fullName = user.fullName,
                                passwordHash = user.passwordHash,
                                role = user.role,
                                montoPorProducto = user.montoPorProducto,
                                isActive = user.isActive,
                                telefono = finalPhone,
                                permisoProduccion = user.permisoProduccion,
                                permisoMercancias = user.permisoMercancias,
                                permisoPersonal = user.permisoPersonal,
                                permisoControlNegocio = true
                            )
                        )
                    } else {
                        db.userDao().insertUser(user)
                    }
                }

                if (latestConfig != null) {
                    db.configuracionGeneralDao().updateConfig(
                        latestConfig.copy(
                            urlUsuariosJson = finalUrlUsuarios,
                            urlCatalogoJson = finalUrlCatalogo,
                            urlMercainvJson = finalUrlMercainv,
                            urlQDuenoJson = finalUrlQDueno,
                            urlVersionJson = finalUrlVersion,
                            telefonoDueno = finalTelefonoDueno,
                            telefonoCajero = finalTelefonoCajero,
                            telefonoAdmin = finalTelefonoAdmin,
                            lastUserUpdateDate = System.currentTimeMillis(),
                            lastUserUpdateStatus = STATUS_SUCCESS,
                            lastUserUpdateVersion = finalVersionToSave
                        )
                    )
                } else {
                    db.configuracionGeneralDao().insertConfig(
                        ConfiguracionGeneral(
                            id = 1,
                            urlUsuariosJson = finalUrlUsuarios,
                            urlCatalogoJson = finalUrlCatalogo,
                            urlMercainvJson = finalUrlMercainv,
                            urlQDuenoJson = finalUrlQDueno,
                            urlVersionJson = finalUrlVersion,
                            telefonoDueno = finalTelefonoDueno,
                            telefonoCajero = finalTelefonoCajero,
                            telefonoAdmin = finalTelefonoAdmin,
                            lastUserUpdateDate = System.currentTimeMillis(),
                            lastUserUpdateStatus = STATUS_SUCCESS,
                            lastUserUpdateVersion = finalVersionToSave
                        )
                    )
                }
                if (bizCodeFormatted.isNotBlank()) {
                    val existingBiz = db.configuracionNegocioDao().getConfigSync()
                    val targetName = if (nombreNegocioRaw.isNotBlank()) nombreNegocioRaw else existingBiz?.nombreNegocio ?: "Negocio $bizCodeFormatted"
                    if (existingBiz != null) {
                        db.configuracionNegocioDao().updateConfig(
                            existingBiz.copy(
                                codigoNegocio = "NEG-$bizCodeFormatted",
                                nombreNegocio = targetName
                            )
                        )
                    } else {
                        db.configuracionNegocioDao().insertConfig(
                            com.example.data.local.model.ConfiguracionNegocio(
                                id = 1,
                                codigoNegocio = "NEG-$bizCodeFormatted",
                                nombreNegocio = targetName
                            )
                        )
                    }
                }
            }
            return UpdateResult.Success

        } catch (e: Exception) {
            Log.e("UserUpdateManager", "Error parsing JSON", e)
            return UpdateResult.InvalidFile
        }
    }
}
