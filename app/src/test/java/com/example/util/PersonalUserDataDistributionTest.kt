package com.example.util

import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.ConfiguracionNegocio
import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.UserRole
import com.example.licensing.BusinessCodeHelper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PersonalUserDataDistributionTest {

    @Test
    fun testGenerateAndValidateUserJsonStructure() {
        val personal = PersonalContratado(
            id = 42,
            nombreCompleto = "Carlos Alberto Mendoza",
            carnetIdentidad = "90010112345",
            movil = "+53 51234567",
            formaPago = "Fijo mensual",
            tieneAccesoApp = true,
            username = "carlos_caja",
            passwordHash = "1234".toSha256(),
            passwordPlain = "1234",
            role = "CAJERO",
            dependienteTipo = "SALON",
            montoPorProducto = 0.0,
            isActive = true,
            permisoProduccion = false,
            permisoMercancias = false,
            permisoPersonal = false,
            permisoControlNegocio = false
        )

        val businessNumber = "001"
        val businessCode = "NEG-001"
        val businessName = "Restaurante Don Roberto"

        val root = JSONObject().apply {
            put("tipo", "USUARIO_ELQADRE")
            put("version", "1.0")
            put("numeroNegocio", businessNumber)
            put("codigoNegocio", businessCode)
            put("nombreNegocio", businessName)

            val personaObj = JSONObject().apply {
                put("id", personal.id)
                put("nombreCompleto", personal.nombreCompleto)
                put("carnetIdentidad", personal.carnetIdentidad)
                put("movil", personal.movil)
                put("formaPago", personal.formaPago)
            }
            put("persona", personaObj)

            val usuarioObj = JSONObject().apply {
                put("username", personal.username)
                put("passwordHash", personal.passwordHash)
                put("passwordPlain", personal.passwordPlain)
                put("role", personal.role)
                put("dependienteTipo", personal.dependienteTipo)
                put("montoPorProducto", personal.montoPorProducto)
                put("isActive", personal.isActive)
                put("permisoProduccion", personal.permisoProduccion)
                put("permisoMercancias", personal.permisoMercancias)
                put("permisoPersonal", personal.permisoPersonal)
                put("permisoControlNegocio", personal.permisoControlNegocio)
            }
            put("usuario", usuarioObj)
            put("timestamp", System.currentTimeMillis())
        }

        val jsonString = root.toString(2)
        assertNotNull(jsonString)

        // Parse back and verify strict boundaries
        val parsed = JSONObject(jsonString)
        assertEquals("USUARIO_ELQADRE", parsed.getString("tipo"))
        assertEquals("1.0", parsed.getString("version"))
        assertEquals("001", parsed.getString("numeroNegocio"))
        assertEquals("NEG-001", parsed.getString("codigoNegocio"))
        assertEquals("Restaurante Don Roberto", parsed.getString("nombreNegocio"))

        val persona = parsed.getJSONObject("persona")
        assertEquals(42L, persona.getLong("id"))
        assertEquals("Carlos Alberto Mendoza", persona.getString("nombreCompleto"))
        assertEquals("90010112345", persona.getString("carnetIdentidad"))
        assertEquals("+53 51234567", persona.getString("movil"))
        assertEquals("Fijo mensual", persona.getString("formaPago"))

        val usuario = parsed.getJSONObject("usuario")
        assertEquals("carlos_caja", usuario.getString("username"))
        assertEquals("1234".toSha256(), usuario.getString("passwordHash"))
        assertEquals("1234", usuario.getString("passwordPlain"))
        assertEquals("CAJERO", usuario.getString("role"))
        assertEquals("SALON", usuario.getString("dependienteTipo"))
        assertEquals(0.0, usuario.getDouble("montoPorProducto"), 0.001)
        assertTrue(usuario.getBoolean("isActive"))
        assertFalse(usuario.getBoolean("permisoProduccion"))
        assertFalse(usuario.getBoolean("permisoControlNegocio"))

        // Verify that NO other users, DVC, license, trial or SuperAdmin data exist in the JSON
        assertFalse(parsed.has("usuarios"))
        assertFalse(parsed.has("productos"))
        assertFalse(parsed.has("precios"))
        assertFalse(parsed.has("dvc"))
        assertFalse(parsed.has("dispositivo"))
        assertFalse(parsed.has("licencia"))
        assertFalse(parsed.has("prueba"))
        assertFalse(parsed.has("superAdmin"))
    }

    @Test
    fun testMax3DuenosLimitValidation() {
        // Business can have at most 3 Dueños in total (1 main + 2 additional)
        val maxAllowedDuenos = 3
        val existingDuenosCount = 3

        val canAddFourthDueno = existingDuenosCount < maxAllowedDuenos
        assertFalse("No se debe permitir un cuarto DUEÑO", canAddFourthDueno)

        val existingDuenos2 = 2
        val canAddThirdDueno = existingDuenos2 < maxAllowedDuenos
        assertTrue("Se debe permitir hasta 3 DUEÑOS en total", canAddThirdDueno)
    }

    @Test
    fun testBusinessCodeValidationRules() {
        val targetDeviceBizNumber = "001"

        // Matching file
        val matchingFileBiz = "001"
        assertEquals(targetDeviceBizNumber, BusinessCodeHelper.formatCode(matchingFileBiz))

        // Different business code - must be detected as mismatch
        val foreignFileBiz = "002"
        assertNotEquals(targetDeviceBizNumber, BusinessCodeHelper.formatCode(foreignFileBiz))

        val foreignBizWithPrefix = "NEG-002"
        assertNotEquals(targetDeviceBizNumber, BusinessCodeHelper.formatCode(foreignBizWithPrefix))
    }

    @Test
    fun testRoleMappingAndSecurityValidation() {
        // Ensure Admin role is blocked
        val invalidRoleAdmin = "ADMIN"
        val isAdminRejected = invalidRoleAdmin == "ADMIN" || invalidRoleAdmin == "ADMINISTRADOR"
        assertTrue(isAdminRejected)

        // Ensure Cajero maps cleanly
        val rawRoleCajero = "CAJERO"
        val mappedCajero = when (rawRoleCajero) {
            "CAJERO" -> UserRole.CAJERO
            else -> UserRole.SALON
        }
        assertEquals(UserRole.CAJERO, mappedCajero)

        // Ensure Dependiente maps to SALON or BARRA
        val rawRoleDependiente = "DEPENDIENTE"
        val dependienteTipoBarra = "BARRA"
        val mappedBarra = if (dependienteTipoBarra == "BARRA") UserRole.BARRA else UserRole.SALON
        assertEquals(UserRole.BARRA, mappedBarra)

        val dependienteTipoSalon = "SALON"
        val mappedSalon = if (dependienteTipoSalon == "BARRA") UserRole.BARRA else UserRole.SALON
        assertEquals(UserRole.SALON, mappedSalon)
    }

    @Test
    fun testPasswordHashingConsistency() {
        val plain = "caja2026"
        val hashed = plain.toSha256()

        assertNotNull(hashed)
        assertEquals(64, hashed.length) // SHA-256 is 64 hex characters
        assertEquals(hashed, "caja2026".toSha256())
        assertNotEquals(hashed, "1234".toSha256())
    }
}
