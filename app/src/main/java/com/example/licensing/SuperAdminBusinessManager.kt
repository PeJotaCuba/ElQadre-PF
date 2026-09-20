package com.example.licensing

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.util.PasswordResetHelper
import com.example.util.UserUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.util.toSha256
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BusinessUser(
    val id: Long = System.currentTimeMillis(),
    val username: String,
    val fullName: String,
    val password: String,
    val role: String, // "ADMINISTRADOR", "CAJERO", "DEPENDIENTE DE SALÓN", "DEPENDIENTE DE BARRA", "DUEÑO"
    val phone: String = "",
    val active: Boolean = true,
    val montoPorProducto: Double = 0.0
)

data class BusinessRecord(
    val code: String, // e.g. "001", "002"
    val name: String,
    val dvc: String,
    val phone: String,
    val phoneAlt: String = "",
    val dueno: String = "",
    val ciudad: String = "",
    val licenseType: String = "PRUEBA", // "PRUEBA", "PERMANENTE", "ANUAL", "MENSUAL"
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "SOLICITUD DE PRUEBA PENDIENTE", // "SOLICITUD DE PRUEBA PENDIENTE", "ACTIVO", "REVOCADA", "INACTIVO"
    val token: String = "",
    val ownerUsername: String = "",
    val ownerPassword: String = "",
    val urlUsuarios: String = "",
    val urlCatalogo: String = "",
    val urlAdmin: String = "",
    val urlDueño: String = "",
    val motivoRevocacion: String = "",
    val fechaRevocacion: String = "",
    val trialSmsSentCount: Int = 0,
    val users: List<BusinessUser> = emptyList()
)

object SuperAdminBusinessManager {

    private const val PREFS_NAME = "elqadre_superadmin_business_prefs"
    private const val KEY_TESTING_CODE = "testing_business_code"
    private const val BUSINESSES_FILE_NAME = "superadmin_businesses.json"

    const val GITHUB_BASE_RAW_URL = "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/"

    fun buildUrlUsuarios(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${BusinessCodeHelper.formatCode(code)}usuarios.json"

    fun buildUrlCatalogo(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${BusinessCodeHelper.formatCode(code)}catalogo.json"

    fun buildUrlAdmin(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${BusinessCodeHelper.formatCode(code)}admin.json"

    fun buildUrlDueño(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${BusinessCodeHelper.formatCode(code)}dueño.json"

    val AVAILABLE_ROLES = listOf(
        "ADMINISTRADOR",
        "CAJERO",
        "DEPENDIENTE DE SALÓN",
        "DEPENDIENTE DE BARRA",
        "DUEÑO"
    )

    private const val KEY_MAX_BUSINESS_CODE_COUNTER = "max_business_code_counter"
    private const val KEY_CONSUMED_BUSINESS_CODES = "consumed_business_codes"

    fun getDefaultUsersForBusiness(
        businessName: String,
        phone: String,
        ownerName: String = "",
        ownerUsername: String = "",
        ownerPassword: String = ""
    ): List<BusinessUser> {
        val basePhone = phone.ifBlank { "54413935" }
        val targetOwnerName = if (ownerName.isNotBlank()) ownerName else "Dueño de Negocio"
        val targetOwnerUser = if (ownerUsername.isNotBlank()) ownerUsername else "dueno"
        val targetOwnerPass = if (ownerPassword.isNotBlank()) ownerPassword else "123"

        return listOf(
            BusinessUser(
                id = 1L,
                username = "admin",
                fullName = "Administrador - $businessName",
                password = "123",
                role = "ADMINISTRADOR",
                phone = basePhone,
                active = true
            ),
            BusinessUser(
                id = 2L,
                username = "cajero",
                fullName = "Cajero Principal",
                password = "123",
                role = "CAJERO",
                phone = basePhone,
                active = true
            ),
            BusinessUser(
                id = 3L,
                username = "salon",
                fullName = "Dependiente de Salón",
                password = "123",
                role = "DEPENDIENTE DE SALÓN",
                phone = basePhone,
                active = true
            ),
            BusinessUser(
                id = 4L,
                username = "barra",
                fullName = "Dependiente de Barra",
                password = "123",
                role = "DEPENDIENTE DE BARRA",
                phone = basePhone,
                active = true
            ),
            BusinessUser(
                id = 5L,
                username = targetOwnerUser,
                fullName = targetOwnerName,
                password = targetOwnerPass,
                role = "DUEÑO",
                phone = basePhone,
                active = true
            )
        )
    }

    private fun getDefaultBusinesses(): List<BusinessRecord> {
        return emptyList()
    }

    @Synchronized
    fun getBusinesses(context: Context): List<BusinessRecord> {
        val file = File(context.filesDir, BUSINESSES_FILE_NAME)
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val content = file.readText()
            if (content.isBlank()) {
                return emptyList()
            }
            parseBusinessesJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun getBusinessByCode(context: Context, code: String): BusinessRecord? {
        val list = getBusinesses(context)
        return list.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
    }

    @Synchronized
    fun getBusinessByDvc(context: Context, dvc: String): BusinessRecord? {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val list = getBusinesses(context)
        return list.firstOrNull {
            val itemDvc = if (it.dvc.startsWith("DVC-", ignoreCase = true)) it.dvc.uppercase() else "DVC-${it.dvc.uppercase()}"
            itemDvc.equals(cleanDvc, ignoreCase = true)
        }
    }

    @Synchronized
    fun saveBusiness(context: Context, business: BusinessRecord) {
        val formattedCode = BusinessCodeHelper.formatCode(business.code)
        val businessWithUrls = business.copy(
            code = formattedCode,
            urlUsuarios = business.urlUsuarios.ifBlank { buildUrlUsuarios(formattedCode) },
            urlCatalogo = business.urlCatalogo.ifBlank { buildUrlCatalogo(formattedCode) },
            urlAdmin = business.urlAdmin.ifBlank { buildUrlAdmin(formattedCode) },
            urlDueño = business.urlDueño.ifBlank { buildUrlDueño(formattedCode) }
        )
        val list = getBusinesses(context).toMutableList()
        val index = list.indexOfFirst { it.code.equals(formattedCode, ignoreCase = true) }
        if (index >= 0) {
            list[index] = businessWithUrls
        } else {
            list.add(businessWithUrls)
        }
        saveBusinessesList(context, list)

        // Automatically generate all 4 files for this business
        try {
            generateAllBusinessFiles(context, businessWithUrls)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun deleteBusiness(context: Context, code: String) {
        val formattedCode = BusinessCodeHelper.formatCode(code)
        val rawTrimmed = code.trim()

        // 1. Remove business from Super Admin local businesses list (immediately freeing its business code)
        val list = getBusinesses(context).toMutableList()
        list.removeAll {
            it.code.equals(rawTrimmed, ignoreCase = true) ||
            it.code.equals(formattedCode, ignoreCase = true) ||
            BusinessCodeHelper.formatCode(it.code).equals(formattedCode, ignoreCase = true)
        }
        saveBusinessesList(context, list)

        // 2. Clear testing mode if current testing business is the one deleted
        val currentTesting = getTestingBusiness(context)
        if (currentTesting != null && (
            currentTesting.code.equals(rawTrimmed, ignoreCase = true) ||
            currentTesting.code.equals(formattedCode, ignoreCase = true) ||
            BusinessCodeHelper.formatCode(currentTesting.code).equals(formattedCode, ignoreCase = true)
        )) {
            clearTestingBusiness(context)
        }

        // 3. Delete local operational and generated files associated with this business
        try {
            val fileNames = listOf(
                "Q_${formattedCode}usuarios.json",
                "Q_${formattedCode}catalogo.json",
                "Q_${formattedCode}admin.json",
                "Q_${formattedCode}dueño.json",
                "Q_${rawTrimmed}usuarios.json",
                "Q_${rawTrimmed}catalogo.json",
                "Q_${rawTrimmed}admin.json",
                "Q_${rawTrimmed}dueño.json"
            ).distinct()

            for (fileName in fileNames) {
                val f = File(context.filesDir, fileName)
                if (f.exists()) f.delete()
                val c = File(context.cacheDir, fileName)
                if (c.exists()) c.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun isBusinessCodeConsumed(context: Context, code: String): Boolean {
        val targetNum = code.filter { it.isDigit() }.toIntOrNull() ?: return false
        val list = getBusinesses(context)
        return list.any { it.code.filter { c -> c.isDigit() }.toIntOrNull() == targetNum }
    }

    /**
     * Calculates the lowest available positive business number starting at 1 ("001").
     * Examples:
     * - [] -> "001"
     * - ["001", "002"] -> "003"
     * - ["002"] (when "001" was deleted) -> "001"
     * - ["001", "003"] (when "002" was deleted) -> "002"
     */
    fun calculateNextAvailableBusinessCode(existingCodes: List<String>): String {
        val occupiedNumbers = existingCodes.mapNotNull { code ->
            code.filter { it.isDigit() }.toIntOrNull()
        }.toSet()

        var candidate = 1
        while (occupiedNumbers.contains(candidate)) {
            candidate++
        }
        return String.format(Locale.US, "%03d", candidate)
    }

    @Synchronized
    fun getNextBusinessCode(context: Context): String {
        val list = getBusinesses(context)
        return calculateNextAvailableBusinessCode(list.map { it.code })
    }

    fun generateUniqueToken(context: Context): String {
        val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val existingTokens = getBusinesses(context).map { it.token }.filter { it.isNotBlank() }.toSet()
        val random = java.util.Random()
        var token: String
        do {
            val sb = StringBuilder(6)
            for (i in 0 until 6) {
                sb.append(charPool[random.nextInt(charPool.length)])
            }
            token = sb.toString()
        } while (existingTokens.contains(token))
        return token
    }

    fun generateOwnerCredentials(duenoName: String, businessCode: String): Pair<String, String> {
        val trimmedName = duenoName.trim()
        val rawFirstName = trimmedName.split("\\s+".toRegex()).firstOrNull()?.ifBlank { "dueno" } ?: "dueno"
        val unaccentedFirst = java.text.Normalizer.normalize(rawFirstName, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

        val username = unaccentedFirst.lowercase(Locale.getDefault())
        val formattedCode = BusinessCodeHelper.formatCode(businessCode)
        val password = unaccentedFirst.lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + formattedCode

        return Pair(username, password)
    }

    private fun saveBusinessesList(context: Context, list: List<BusinessRecord>) {
        val file = File(context.filesDir, BUSINESSES_FILE_NAME)
        val rootArray = JSONArray()

        for (b in list) {
            val formattedCode = BusinessCodeHelper.formatCode(b.code)
            val urlU = b.urlUsuarios.ifBlank { buildUrlUsuarios(formattedCode) }
            val urlC = b.urlCatalogo.ifBlank { buildUrlCatalogo(formattedCode) }
            val urlA = b.urlAdmin.ifBlank { buildUrlAdmin(formattedCode) }
            val urlD = b.urlDueño.ifBlank { buildUrlDueño(formattedCode) }

            val obj = JSONObject().apply {
                put("code", formattedCode)
                put("name", b.name)
                put("dvc", b.dvc)
                put("phone", b.phone)
                put("phoneAlt", b.phoneAlt)
                put("dueno", b.dueno)
                put("ciudad", b.ciudad)
                put("licenseType", b.licenseType)
                put("startDate", b.startDate)
                put("endDate", b.endDate)
                put("status", b.status)
                put("token", b.token)
                put("ownerUsername", b.ownerUsername)
                put("ownerPassword", b.ownerPassword)
                put("urlUsuarios", urlU)
                put("urlCatalogo", urlC)
                put("urlAdmin", urlA)
                put("urlDueño", urlD)
                put("motivoRevocacion", b.motivoRevocacion)
                put("fechaRevocacion", b.fechaRevocacion)
                put("trialSmsSentCount", b.trialSmsSentCount)

                val usersArray = JSONArray()
                for (u in b.users) {
                    val userObj = JSONObject().apply {
                        put("id", u.id)
                        put("username", u.username)
                        put("fullName", u.fullName)
                        put("password", u.password)
                        put("role", u.role)
                        put("phone", u.phone)
                        put("active", u.active)
                        put("montoPorProducto", u.montoPorProducto)
                    }
                    usersArray.put(userObj)
                }
                put("users", usersArray)
            }
            rootArray.put(obj)
        }

        file.writeText(rootArray.toString(2))
    }

    private fun parseBusinessesJson(jsonStr: String): List<BusinessRecord> {
        val result = mutableListOf<BusinessRecord>()
        val array = JSONArray(jsonStr)

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val rawCode = obj.optString("code", String.format(Locale.US, "%03d", i + 1))
            val code = BusinessCodeHelper.formatCode(rawCode)
            val name = obj.optString("name", "Negocio $code")
            val dvc = obj.optString("dvc", "")
            val phone = obj.optString("phone", "54413935")
            val phoneAlt = obj.optString("phoneAlt", "")
            val dueno = obj.optString("dueno", obj.optString("dueño", ""))
            val ciudad = obj.optString("ciudad", "")
            val licenseType = obj.optString("licenseType", "PRUEBA")
            val startDate = obj.optString("startDate", "")
            val endDate = obj.optString("endDate", "")
            val status = obj.optString("status", "SOLICITUD DE PRUEBA PENDIENTE")
            val token = obj.optString("token", "")
            val ownerUsername = obj.optString("ownerUsername", "")
            val ownerPassword = obj.optString("ownerPassword", "")
            val urlUsuarios = obj.optString("urlUsuarios", "").ifBlank { buildUrlUsuarios(code) }
            val urlCatalogo = obj.optString("urlCatalogo", "").ifBlank { buildUrlCatalogo(code) }
            val urlAdmin = obj.optString("urlAdmin", "").ifBlank { buildUrlAdmin(code) }
            val urlDueño = obj.optString("urlDueño", obj.optString("urlDueno", "")).ifBlank { buildUrlDueño(code) }
            val motivoRevocacion = obj.optString("motivoRevocacion", "")
            val fechaRevocacion = obj.optString("fechaRevocacion", "")
            val trialSmsSentCount = obj.optInt("trialSmsSentCount", if (status == "ACTIVO") 1 else 0)

            val usersList = mutableListOf<BusinessUser>()
            val usersArray = obj.optJSONArray("users")
            if (usersArray != null) {
                for (j in 0 until usersArray.length()) {
                    val uObj = usersArray.getJSONObject(j)
                    usersList.add(
                        BusinessUser(
                            id = uObj.optLong("id", j.toLong() + 1L),
                            username = uObj.optString("username", "user$j"),
                            fullName = uObj.optString("fullName", "Usuario $j"),
                            password = uObj.optString("password", "123"),
                            role = uObj.optString("role", "ADMINISTRADOR"),
                            phone = uObj.optString("phone", phone),
                            active = uObj.optBoolean("active", uObj.optBoolean("isActive", true)),
                            montoPorProducto = uObj.optDouble("montoPorProducto", 0.0)
                        )
                    )
                }
            } else {
                usersList.addAll(
                    getDefaultUsersForBusiness(
                        businessName = name,
                        phone = phone,
                        ownerName = dueno,
                        ownerUsername = ownerUsername,
                        ownerPassword = ownerPassword
                    )
                )
            }

            result.add(
                BusinessRecord(
                    code = code,
                    name = name,
                    dvc = dvc,
                    phone = phone,
                    phoneAlt = phoneAlt,
                    dueno = dueno,
                    ciudad = ciudad,
                    licenseType = licenseType,
                    startDate = startDate,
                    endDate = endDate,
                    status = status,
                    token = token,
                    ownerUsername = ownerUsername,
                    ownerPassword = ownerPassword,
                    urlUsuarios = urlUsuarios,
                    urlCatalogo = urlCatalogo,
                    urlAdmin = urlAdmin,
                    urlDueño = urlDueño,
                    motivoRevocacion = motivoRevocacion,
                    fechaRevocacion = fechaRevocacion,
                    trialSmsSentCount = trialSmsSentCount,
                    users = usersList
                )
            )
        }

        return result
    }

    /**
     * Builds dynamic JSON preview strictly from real businesses registered in PRUEBA.
     * If no businesses exist, returns a clean empty structure { "pruebas": [] } with no fake data.
     */
    fun buildDynamicQPreubaJson(context: Context): String {
        val businesses = getBusinesses(context).filter {
            it.licenseType.equals("PRUEBA", ignoreCase = true) || it.status.contains("PRUEBA", ignoreCase = true)
        }
        val rootObj = JSONObject()
        val pruebasArray = JSONArray()

        for (b in businesses) {
            val item = JSONObject().apply {
                put("codigo", BusinessCodeHelper.formatCode(b.code))
                put("dvc", b.dvc)
                put("nombreNegocio", b.name)
                put("dueno", b.dueno)
                put("ciudad", b.ciudad)
                put("numeroMovil", b.phone)
                if (b.phoneAlt.isNotBlank()) {
                    put("numeroMovilAlt", b.phoneAlt)
                }
                put("token", b.token)
                put("usuarioDueño", b.ownerUsername)
                put("claveDueño", b.ownerPassword)
                put("fechaInicio", b.startDate)
                put("fechaFin", b.endDate)
                put("estado", b.status)
                put("urlUsuarios", b.urlUsuarios.ifBlank { buildUrlUsuarios(b.code) })
            }
            pruebasArray.put(item)
        }
        rootObj.put("pruebas", pruebasArray)
        return rootObj.toString(2)
    }

    fun saveDynamicQPreubaFile(context: Context): File {
        val content = buildDynamicQPreubaJson(context)
        val file = File(context.filesDir, "Q_preuba.json")
        file.writeText(content)
        return file
    }

    /**
     * Builds dynamic JSON preview strictly from real businesses with commercial licenses.
     * If no businesses exist, returns a clean empty structure { "licencias": [] } with no fake data.
     */
    fun buildDynamicQLicenciasJson(context: Context): String {
        val businesses = getBusinesses(context).filter {
            !it.licenseType.equals("PRUEBA", ignoreCase = true) && !it.status.contains("PRUEBA", ignoreCase = true)
        }
        val rootObj = JSONObject()
        val licenciasArray = JSONArray()

        for (b in businesses) {
            val item = JSONObject().apply {
                put("codigo", BusinessCodeHelper.formatCode(b.code))
                put("dvc", b.dvc)
                put("nombreNegocio", b.name)
                put("numeroMovil", b.phone)
                if (b.phoneAlt.isNotBlank()) {
                    put("numeroMovilAlt", b.phoneAlt)
                }
                put("tipoLicencia", b.licenseType)
                put("fechaInicio", b.startDate)
                put("fechaFin", b.endDate)
                put("estado", b.status)
                put("urlUsuarios", b.urlUsuarios.ifBlank { buildUrlUsuarios(b.code) })
                if (b.motivoRevocacion.isNotBlank()) {
                    put("motivoRevocacion", b.motivoRevocacion)
                }
                if (b.fechaRevocacion.isNotBlank()) {
                    put("fechaRevocacion", b.fechaRevocacion)
                }
            }
            licenciasArray.put(item)
        }
        rootObj.put("licencias", licenciasArray)
        return rootObj.toString(2)
    }

    fun saveDynamicQLicenciasFile(context: Context): File {
        val content = buildDynamicQLicenciasJson(context)
        val file = File(context.filesDir, "Q_licencias.json")
        file.writeText(content)
        return file
    }

    /**
     * Generates Q_XXXusuarios.json for a specific business.
     * Contains exclusively the users belonging to that business, along with
     * the 4 operational URLs assigned to their corresponding roles.
     */
    fun generateUserJsonForBusiness(context: Context, business: BusinessRecord): File {
        val formattedCode = BusinessCodeHelper.formatCode(business.code)
        val fileName = "Q_${formattedCode}usuarios.json"
        val file = File(context.filesDir, fileName)

        val urlU = business.urlUsuarios.ifBlank { buildUrlUsuarios(formattedCode) }
        val urlC = business.urlCatalogo.ifBlank { buildUrlCatalogo(formattedCode) }
        val urlA = business.urlAdmin.ifBlank { buildUrlAdmin(formattedCode) }
        val urlD = business.urlDueño.ifBlank { buildUrlDueño(formattedCode) }

        val rootObj = JSONObject()
        val negocioObj = JSONObject().apply {
            put("codigo", formattedCode)
            put("nombre", business.name)
            put("dvc", business.dvc)
            put("telefono", business.phone)
        }
        rootObj.put("negocio", negocioObj)
        rootObj.put("codigoNegocio", formattedCode)
        rootObj.put("urlUsuarios", urlU)
        rootObj.put("urlCatalogo", urlC)
        rootObj.put("urlAdmin", urlA)
        rootObj.put("urlDueño", urlD)
        rootObj.put("urlDueno", urlD)
        rootObj.put("urlUsuariosJson", urlU)
        rootObj.put("urlCatalogoJson", urlC)
        rootObj.put("urlAdminJson", urlA)
        rootObj.put("urlQDuenoJson", urlD)
        rootObj.put("telefonoDueno", business.phone)
        rootObj.put("telefonoCajero", business.phone)
        rootObj.put("telefonoAdmin", business.phone)
        rootObj.put("version", System.currentTimeMillis().toString())

        val usersArray = JSONArray()
        for (u in business.users) {
            val rawPass = u.password.trim()
            val passHash = if (rawPass.length == 64 && rawPass.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                rawPass
            } else {
                rawPass.toSha256()
            }

            val userObj = JSONObject().apply {
                put("id", u.id)
                put("username", u.username)
                put("fullName", u.fullName)
                put("password", u.password)
                put("passwordHash", passHash)
                put("role", u.role)
                put("phone", u.phone)
                put("isActive", u.active)
                put("montoPorProducto", u.montoPorProducto)

                // Asignar URLs operativas por rol según requerimiento
                when (u.role.uppercase()) {
                    "ADMINISTRADOR" -> {
                        put("urlAdmin", urlA)
                        put("urlCatalogo", urlC)
                        put("urlCatalogoJson", urlC)
                        put("urlUsuarios", urlU)
                        put("urlDueño", urlD)
                    }
                    "DUEÑO" -> {
                        put("urlDueño", urlD)
                        put("urlDueno", urlD)
                        put("urlQDuenoJson", urlD)
                        put("urlAdmin", urlA)
                        put("urlCatalogo", urlC)
                        put("urlUsuarios", urlU)
                    }
                    else -> { // CAJERO, DEPENDIENTE DE SALÓN, DEPENDIENTE DE BARRA
                        put("urlCatalogo", urlC)
                        put("urlCatalogoJson", urlC)
                        put("urlUsuarios", urlU)
                        put("urlUsuariosJson", urlU)
                        put("urlAdmin", urlA)
                    }
                }
            }
            usersArray.put(userObj)
        }
        rootObj.put("usuarios", usersArray)

        file.writeText(rootObj.toString(2))
        return file
    }

    /**
     * Generates Q_XXXcatalogo.json for a specific business.
     */
    fun generateCatalogoJsonForBusiness(context: Context, business: BusinessRecord): File {
        val formattedCode = BusinessCodeHelper.formatCode(business.code)
        val fileName = "Q_${formattedCode}catalogo.json"
        val file = File(context.filesDir, fileName)

        val rootObj = JSONObject().apply {
            put("version", System.currentTimeMillis().toString())
            put("codigoNegocio", formattedCode)
            put("negocio", JSONObject().apply {
                put("codigo", formattedCode)
                put("nombre", business.name)
            })
            put("urlCatalogo", business.urlCatalogo.ifBlank { buildUrlCatalogo(formattedCode) })
            put("tasaUsd", 0.0)
            put("tasaEur", 0.0)
            put("categories", JSONArray())
            put("products", JSONArray())
        }

        file.writeText(rootObj.toString(2))
        return file
    }

    /**
     * Generates Q_XXXadmin.json for a specific business.
     */
    fun generateAdminJsonForBusiness(context: Context, business: BusinessRecord): File {
        val formattedCode = BusinessCodeHelper.formatCode(business.code)
        val fileName = "Q_${formattedCode}admin.json"
        val file = File(context.filesDir, fileName)

        val rootObj = JSONObject().apply {
            put("archivo", fileName)
            put("identificador_archivo", "Q_ADMIN")
            put("version", System.currentTimeMillis().toString())
            put("codigoNegocio", formattedCode)
            put("nombreNegocio", business.name)
            put("fecha_generacion", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
            put("negocio", JSONObject().apply {
                put("codigo", formattedCode)
                put("nombre", business.name)
                put("dvc", business.dvc)
                put("telefono", business.phone)
            })
            put("operaciones", JSONObject())
        }

        file.writeText(rootObj.toString(2))
        return file
    }

    /**
     * Generates Q_XXXdueño.json for a specific business.
     */
    fun generateDuenoJsonForBusiness(context: Context, business: BusinessRecord): File {
        val formattedCode = BusinessCodeHelper.formatCode(business.code)
        val fileName = "Q_${formattedCode}dueño.json"
        val file = File(context.filesDir, fileName)

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

        val rootObj = JSONObject().apply {
            put("archivo", fileName)
            put("identificador_archivo", "Q_DUENO")
            put("version", now.toString())
            put("codigoNegocio", formattedCode)
            put("fecha_publicacion", dateFormat.format(Date(now)))
            put("fecha_generacion", dateTimeFormat.format(Date(now)))
            put("negocio", JSONObject().apply {
                put("codigo", formattedCode)
                put("nombre", business.name)
                put("dvc", business.dvc)
                put("telefono", business.phone)
            })
            put("datos", JSONObject().apply {
                put("materias_primas", JSONArray())
                put("productos_elaborados", JSONArray())
                put("mercaderias", JSONArray())
                put("gastos_fijos", JSONArray())
                put("inversiones", JSONArray())
            })
        }

        file.writeText(rootObj.toString(2))
        return file
    }

    /**
     * Generates all 4 operational JSON files for the business.
     */
    fun generateAllBusinessFiles(context: Context, business: BusinessRecord): List<File> {
        val fUsuarios = generateUserJsonForBusiness(context, business)
        val fCatalogo = generateCatalogoJsonForBusiness(context, business)
        val fAdmin = generateAdminJsonForBusiness(context, business)
        val fDueno = generateDuenoJsonForBusiness(context, business)
        return listOf(fUsuarios, fCatalogo, fAdmin, fDueno)
    }

    /**
     * Toggles a user's active state, saves business and updates Q_XXXusuarios.json
     */
    fun toggleUserActiveStatus(context: Context, business: BusinessRecord, userId: Long): BusinessRecord {
        val updatedUsers = business.users.map { u ->
            if (u.id == userId) u.copy(active = !u.active) else u
        }
        val updatedBusiness = business.copy(users = updatedUsers)
        saveBusiness(context, updatedBusiness)
        generateUserJsonForBusiness(context, updatedBusiness)
        return updatedBusiness
    }

    /**
     * Updates password for a user, saves business and updates Q_XXXusuarios.json
     */
    fun changeUserPassword(context: Context, business: BusinessRecord, userId: Long, newPassword: String): BusinessRecord {
        val cleanPass = newPassword.trim().ifBlank { "1234" }
        val updatedUsers = business.users.map { u ->
            if (u.id == userId) u.copy(password = cleanPass) else u
        }
        val updatedBusiness = business.copy(users = updatedUsers)
        saveBusiness(context, updatedBusiness)
        generateUserJsonForBusiness(context, updatedBusiness)
        return updatedBusiness
    }

    /**
     * Saves or updates a user in the business record, saves and updates Q_XXXusuarios.json
     */
    fun saveOrUpdateUser(context: Context, business: BusinessRecord, user: BusinessUser): BusinessRecord {
        val existingUsers = business.users.toMutableList()
        val index = existingUsers.indexOfFirst { it.id == user.id || it.username.equals(user.username, ignoreCase = true) }
        if (index >= 0) {
            existingUsers[index] = user
        } else {
            existingUsers.add(user)
        }
        val updatedBusiness = business.copy(users = existingUsers)
        saveBusiness(context, updatedBusiness)
        generateUserJsonForBusiness(context, updatedBusiness)
        return updatedBusiness
    }

    /**
     * Deletes a user from business, saves and updates Q_XXXusuarios.json
     */
    fun deleteUser(context: Context, business: BusinessRecord, userId: Long): BusinessRecord {
        val updatedUsers = business.users.filter { it.id != userId }
        val updatedBusiness = business.copy(users = updatedUsers)
        saveBusiness(context, updatedBusiness)
        generateUserJsonForBusiness(context, updatedBusiness)
        return updatedBusiness
    }

    // Testing context management for Super Admin "ENTRAR A ELQADRE"
    fun setTestingBusiness(context: Context, business: BusinessRecord?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (business != null) {
            prefs.edit().putString(KEY_TESTING_CODE, business.code).apply()
        } else {
            prefs.edit().remove(KEY_TESTING_CODE).apply()
        }
    }

    fun getTestingBusiness(context: Context): BusinessRecord? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_TESTING_CODE, null) ?: return null
        return getBusinessByCode(context, code)
    }

    fun clearTestingBusiness(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TESTING_CODE).apply()
    }

    /**
     * Applies the business users and configuration directly to the local Room database
     * ensuring complete isolation and immediate login availability for testing.
     */
    private fun hashSha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun applyBusinessUsersToLocalDb(
        context: Context,
        db: AppDatabase,
        business: BusinessRecord
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val userDao = db.userDao()
            val configDao = db.configuracionGeneralDao()

            // 1. Clear existing users in database to guarantee isolation
            val existingUsers = userDao.getAllUsersSync()
            for (u in existingUsers) {
                userDao.deleteUser(u.username)
            }

            // 2. Insert the business users with hashed passwords
            for (u in business.users) {
                if (!u.active) continue
                val roleEnum = (UserUpdateManager.parseRole(u.role) ?: UserRole.DUENO).let {
                    if (it == UserRole.ADMIN) UserRole.DUENO else it
                }
                val passHash = if (u.password.length == 64 && u.password.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    u.password
                } else {
                    hashSha256(u.password)
                }

                val newUser = User(
                    username = u.username.trim(),
                    fullName = u.fullName.trim(),
                    passwordHash = passHash,
                    role = roleEnum,
                    isActive = true,
                    telefono = u.phone.trim()
                )
                userDao.insertUser(newUser)
            }

            // 3. Update general config with business URLs
            val formattedCode = BusinessCodeHelper.formatCode(business.code)
            val urlU = business.urlUsuarios.ifBlank { buildUrlUsuarios(formattedCode) }
            val urlC = business.urlCatalogo.ifBlank { buildUrlCatalogo(formattedCode) }
            val urlD = business.urlDueño.ifBlank { buildUrlDueño(formattedCode) }

            val currentConfig = configDao.getConfigSync()
            if (currentConfig != null) {
                configDao.updateConfig(
                    currentConfig.copy(
                        urlUsuariosJson = urlU,
                        urlCatalogoJson = urlC,
                        urlQDuenoJson = urlD,
                        telefonoDueno = business.phone,
                        telefonoCajero = business.phone,
                        telefonoAdmin = business.phone,
                        lastUserUpdateStatus = UserUpdateManager.STATUS_SUCCESS,
                        lastUserUpdateDate = System.currentTimeMillis()
                    )
                )
            } else {
                configDao.insertConfig(
                    ConfiguracionGeneral(
                        id = 1,
                        urlUsuariosJson = urlU,
                        urlCatalogoJson = urlC,
                        urlQDuenoJson = urlD,
                        telefonoDueno = business.phone,
                        telefonoCajero = business.phone,
                        telefonoAdmin = business.phone,
                        lastUserUpdateStatus = UserUpdateManager.STATUS_SUCCESS,
                        lastUserUpdateDate = System.currentTimeMillis()
                    )
                )
            }

            // 4. Update business config with business info
            val negocioDao = db.configuracionNegocioDao()
            val curNegocio = negocioDao.getConfigSync()
            if (curNegocio != null) {
                negocioDao.updateConfig(
                    curNegocio.copy(
                        nombreNegocio = business.name,
                        codigoNegocio = "NEG-$formattedCode",
                        telefono = business.phone
                    )
                )
            } else {
                negocioDao.insertConfig(
                    com.example.data.local.model.ConfiguracionNegocio(
                        id = 1,
                        nombreNegocio = business.name,
                        codigoNegocio = "NEG-$formattedCode",
                        telefono = business.phone
                    )
                )
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Matches an incoming SuperAdminSmsRequest to a registered BusinessRecord.
     * Searches by DVC, code, or business name.
     */
    fun findBusinessForRequest(context: Context, req: SuperAdminSmsRequest): BusinessRecord? {
        val businesses = getBusinesses(context)
        val reqDvcClean = req.dvc.replace("DVC-", "", ignoreCase = true).trim()
        val reqBizTrim = req.nombreNegocio.trim()

        // 1. Exact or clean DVC match
        val byDvc = businesses.firstOrNull { biz ->
            val bizDvcClean = biz.dvc.replace("DVC-", "", ignoreCase = true).trim()
            bizDvcClean.isNotBlank() && bizDvcClean.equals(reqDvcClean, ignoreCase = true)
        }
        if (byDvc != null) return byDvc

        // 2. Business code match (e.g. if req.nombreNegocio contains code "001" or starts with "001")
        val byCode = businesses.firstOrNull { biz ->
            reqBizTrim.startsWith(biz.code, ignoreCase = true) ||
            reqBizTrim.contains(biz.code, ignoreCase = true) ||
            req.rawBody.contains(biz.code, ignoreCase = true)
        }
        if (byCode != null) return byCode

        // 3. Name match
        val byName = businesses.firstOrNull { biz ->
            biz.name.isNotBlank() && (
                biz.name.equals(reqBizTrim, ignoreCase = true) ||
                reqBizTrim.contains(biz.name, ignoreCase = true)
            )
        }
        return byName
    }

    /**
     * Retrieves all pending NUEVO_USUARIO SMS requests for a specific business.
     */
    fun getPendingUserRequestsForBusiness(context: Context, business: BusinessRecord): List<SuperAdminSmsRequest> {
        val allRequests = SuperAdminSmsHelper.scanInboxForRequests(context)
        return allRequests.filter { req ->
            req.tipo == "NUEVO_USUARIO" &&
            !SuperAdminSmsHelper.isSmsProcessed(context, req.uniqueKey) &&
            findBusinessForRequest(context, req)?.code == business.code
        }
    }

    /**
     * Approves a NUEVO_USUARIO request:
     * - Adds the user exclusively to that business's user list
     * - Saves updated business
     * - Generates updated Q_XXXusuarios.json locally (no auto GitHub upload)
     * - Marks SMS as processed to avoid duplicates
     */
    fun approveUserRequest(
        context: Context,
        business: BusinessRecord,
        req: SuperAdminSmsRequest,
        initialPassword: String = "1234"
    ): Pair<BusinessRecord, File> {
        val existingUsers = business.users.toMutableList()
        val cleanUsername = req.nuevoUsername.trim().lowercase().ifBlank { "usuario${existingUsers.size + 1}" }
        val cleanFullName = req.nuevoNombre.trim().ifBlank { cleanUsername.replaceFirstChar { it.uppercase() } }
        val cleanRole = req.nuevoRol.trim().uppercase().ifBlank { "CAJERO" }
        val cleanPhone = req.nuevoTelefono.trim()

        // Replace if username already exists, or append
        val existingIndex = existingUsers.indexOfFirst { it.username.equals(cleanUsername, ignoreCase = true) }
        val newUser = BusinessUser(
            id = if (existingIndex >= 0) existingUsers[existingIndex].id else System.currentTimeMillis(),
            username = cleanUsername,
            fullName = cleanFullName,
            password = initialPassword,
            role = cleanRole,
            phone = cleanPhone,
            active = true
        )

        if (existingIndex >= 0) {
            existingUsers[existingIndex] = newUser
        } else {
            existingUsers.add(newUser)
        }

        val updatedBusiness = business.copy(users = existingUsers)
        saveBusiness(context, updatedBusiness)

        // Mark SMS as processed
        SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)

        // Generate Q_XXXusuarios.json locally
        val generatedFile = generateUserJsonForBusiness(context, updatedBusiness)

        return Pair(updatedBusiness, generatedFile)
    }

    /**
     * Rejects/discards a NUEVO_USUARIO request and marks it as processed.
     */
    fun rejectUserRequest(context: Context, req: SuperAdminSmsRequest) {
        SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)
    }

    /**
     * Calculates the accurate CommercialStatus for a business based on its fields and current date.
     */
    fun calculateCommercialStatus(business: BusinessRecord): CommercialStatus {
        val statusUpper = business.status.trim().uppercase()
        if (statusUpper == "REVOCADA" || statusUpper == "REVOCADO") {
            return CommercialStatus.LICENCIA_REVOCADA
        }
        if (statusUpper == "SIN_AUTORIZACION" || statusUpper == "SIN AUTORIZACION") {
            return CommercialStatus.SIN_AUTORIZACION
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val licenseType = business.licenseType.trim().uppercase()

        if (licenseType == "PRUEBA") {
            if (business.endDate.isBlank()) return CommercialStatus.PRUEBA_ACTIVA
            try {
                val endDate = sdf.parse(business.endDate.trim())
                if (endDate != null && today.after(endDate)) {
                    return CommercialStatus.PRUEBA_VENCIDA
                }
            } catch (_: Exception) {}
            return CommercialStatus.PRUEBA_ACTIVA
        }

        if (licenseType == "PERMANENTE" || licenseType == "INDEFINIDO") {
            return CommercialStatus.LICENCIA_ACTIVA
        }

        // Mensual, Anual, Comercial with expiration
        if (business.endDate.isNotBlank() && !business.endDate.equals("PERMANENTE", ignoreCase = true)) {
            try {
                val endDate = sdf.parse(business.endDate.trim())
                if (endDate != null && today.after(endDate)) {
                    return CommercialStatus.LICENCIA_VENCIDA
                }
            } catch (_: Exception) {}
        }

        return CommercialStatus.LICENCIA_ACTIVA
    }

    /**
     * FASE 5: Revokes a business license.
     * - Updates BusinessRecord to status REVOCADA, records motivo and fechaRevocacion
     * - Generates updated Q_licencias.json locally
     * - Sends automatic notification SMS to client
     */
    suspend fun revokeBusinessLicense(
        context: Context,
        business: BusinessRecord,
        motivo: String,
        sendNotificationSms: Boolean = true
    ): Pair<BusinessRecord, File> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        val updatedBusiness = business.copy(
            status = "REVOCADA",
            motivoRevocacion = motivo.trim(),
            fechaRevocacion = todayStr
        )
        saveBusiness(context, updatedBusiness)

        val licenciasFile = SuperAdminSmsHelper.revokeLicenciaJson(
            context = context,
            dvc = business.dvc,
            motivo = motivo.trim(),
            fechaRevocacion = todayStr
        )

        if (sendNotificationSms && business.phone.isNotBlank()) {
            val smsBody = SuperAdminSmsHelper.buildRevocacionSms(
                negocio = business.name,
                dvc = business.dvc,
                motivo = motivo.trim(),
                fechaRevocacion = todayStr,
                fechaFinAnterior = business.endDate
            )
            SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, business.phone, smsBody)
        }

        return@withContext Pair(updatedBusiness, licenciasFile)
    }

    /**
     * FASE 5: Renews or extends a commercial license.
     * - durationDays: -1 for PERMANENTE, 30 for 1 month, 365 for 1 year, etc.
     */
    suspend fun renewBusinessLicense(
        context: Context,
        business: BusinessRecord,
        durationDays: Int,
        customEndDate: String = ""
    ): Pair<BusinessRecord, File> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        val newEndDate = if (customEndDate.isNotBlank()) {
            customEndDate.trim()
        } else if (durationDays > 0) {
            cal.add(Calendar.DAY_OF_YEAR, durationDays)
            sdf.format(cal.time)
        } else {
            "PERMANENTE"
        }

        val newLicenseType = when {
            durationDays < 0 || newEndDate.equals("PERMANENTE", ignoreCase = true) -> "PERMANENTE"
            durationDays >= 360 -> "ANUAL"
            else -> "MENSUAL"
        }

        val updatedBusiness = business.copy(
            licenseType = newLicenseType,
            startDate = todayStr,
            endDate = newEndDate,
            status = "ACTIVO",
            motivoRevocacion = "",
            fechaRevocacion = ""
        )
        saveBusiness(context, updatedBusiness)

        val licenciasFile = SuperAdminSmsHelper.renewLicenciaJson(
            context = context,
            dvc = business.dvc,
            durationDays = durationDays,
            customEndDate = newEndDate
        )

        return@withContext Pair(updatedBusiness, licenciasFile)
    }

    /**
     * FASE 5: Activates trial (7 days) for a business.
     */
    suspend fun activateBusinessTrial(
        context: Context,
        business: BusinessRecord,
        durationDays: Int = 7
    ): Pair<BusinessRecord, File> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, durationDays)
        val endStr = sdf.format(cal.time)

        val updatedBusiness = business.copy(
            licenseType = "PRUEBA",
            startDate = todayStr,
            endDate = endStr,
            status = "ACTIVO",
            motivoRevocacion = "",
            fechaRevocacion = ""
        )
        saveBusiness(context, updatedBusiness)

        val pruebaFile = SuperAdminSmsHelper.generateOrUpdatePruebasJson(
            context = context,
            dvc = business.dvc,
            nombreNegocio = business.name,
            numeroMovil = business.phone,
            codigoNegocio = business.code,
            urlUsuarios = business.urlUsuarios
        )

        return@withContext Pair(updatedBusiness, pruebaFile)
    }
}
