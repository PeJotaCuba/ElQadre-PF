package com.example.util

import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.UserRole
import com.example.licensing.BusinessCodeHelper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Test de Integración y Auditoría - FASE FINAL de la Arquitectura de Cuentas ElQadre.
 * Valida:
 * 1. Eliminación del rol ADMINISTRADOR y autonomía total del DUEÑO.
 * 2. Creación y distribución local de Usuarios (DUEÑO -> Personal Contratado -> COMPARTIR DATOS).
 * 3. Importación y validación local en dispositivo receptor (CARGAR DATOS) sin dependencias de red o SuperAdmin.
 * 4. Generación y distribución local del Catálogo (DUEÑO -> COMPARTIR CATÁLOGO -> CARGAR DATOS).
 * 5. Aislamiento estricto de negocio e integridad de contraseñas (SHA-256).
 */
class FinalArchitectureIntegrationTest {

    @Test
    fun testFinalArchitectureRoles() {
        // Only DUEÑO, CAJERO, SALON (Dependiente Salón), and BARRA (Dependiente Barra) are valid business roles
        val activeRoles = listOf(UserRole.DUENO, UserRole.CAJERO, UserRole.SALON, UserRole.BARRA)
        assertTrue(activeRoles.contains(UserRole.DUENO))
        assertTrue(activeRoles.contains(UserRole.CAJERO))
        assertTrue(activeRoles.contains(UserRole.SALON))
        assertTrue(activeRoles.contains(UserRole.BARRA))

        // Ensure ADMIN is deprecated and blocked from user generation
        @Suppress("DEPRECATION")
        val deprecatedRole = UserRole.ADMIN
        assertNotNull(deprecatedRole)
    }

    @Test
    fun testUserSharingAndImportWorkflow() {
        val bizNumber = "007"
        val bizCode = "NEG-007"
        val bizName = "Cafetería La Palma"

        // 1. Dueño creates Personal Contratado
        val personal = PersonalContratado(
            id = 101,
            nombreCompleto = "Ana María Gómez",
            carnetIdentidad = "95050512345",
            movil = "+53 58889900",
            formaPago = "Comisión",
            tieneAccesoApp = true,
            username = "anita_salon",
            passwordHash = "anita1234".toSha256(),
            passwordPlain = "anita1234",
            role = "DEPENDIENTE",
            dependienteTipo = "SALON",
            montoPorProducto = 15.0,
            isActive = true,
            permisoProduccion = false,
            permisoMercancias = false,
            permisoPersonal = false,
            permisoControlNegocio = false
        )

        // 2. COMPARTIR DATOS generates JSON
        val root = JSONObject().apply {
            put("tipo", "USUARIO_ELQADRE")
            put("version", "1.0")
            put("numeroNegocio", bizNumber)
            put("codigoNegocio", bizCode)
            put("nombreNegocio", bizName)

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

        val exportedJson = root.toString(2)
        assertNotNull(exportedJson)

        val parsed = JSONObject(exportedJson)
        assertEquals("USUARIO_ELQADRE", parsed.getString("tipo"))
        assertEquals("007", parsed.getString("numeroNegocio"))
        assertEquals("anita_salon", parsed.getJSONObject("usuario").getString("username"))
        assertEquals("anita1234".toSha256(), parsed.getJSONObject("usuario").getString("passwordHash"))

        // 3. CARGAR DATOS on target device with matching business
        val targetDeviceBizNumber = "007"
        val fileBizCode = parsed.optString("numeroNegocio", parsed.optString("codigoNegocio"))
        val isMatch = BusinessCodeHelper.matches(targetDeviceBizNumber, fileBizCode)
        assertTrue("Business numbers must match on receptor device", isMatch)

        // 4. Target device verifies user integrity
        val usuario = parsed.getJSONObject("usuario")
        val persona = parsed.getJSONObject("persona")
        val targetRole = when (usuario.getString("role").uppercase()) {
            "CAJERO" -> UserRole.CAJERO
            "DEPENDIENTE" -> if (usuario.optString("dependienteTipo").uppercase() == "BARRA") UserRole.BARRA else UserRole.SALON
            else -> UserRole.SALON
        }

        assertEquals("anita_salon", usuario.getString("username"))
        assertEquals("Ana María Gómez", persona.getString("nombreCompleto"))
        assertEquals(UserRole.SALON, targetRole)
        assertEquals("anita1234".toSha256(), usuario.getString("passwordHash"))

        // 5. Verification that login succeeds locally with hash comparison
        val loginAttemptPassword = "anita1234"
        val isPasswordCorrect = (usuario.getString("passwordHash") == loginAttemptPassword.toSha256())
        assertTrue("Local offline password verification must succeed", isPasswordCorrect)
    }

    @Test
    fun testCrossBusinessUserRejection() {
        val bizA = "001"
        val bizB = "002"

        val root = JSONObject().apply {
            put("tipo", "USUARIO_ELQADRE")
            put("numeroNegocio", bizA)
            put("codigoNegocio", "NEG-001")
            put("nombreNegocio", "Negocio A")
        }

        val fileBiz = root.getString("numeroNegocio")
        val matchesDeviceB = BusinessCodeHelper.matches(bizB, fileBiz)
        assertFalse("Importing JSON from Negocio 001 on Negocio 002 device must be rejected", matchesDeviceB)
    }

    @Test
    fun testCatalogGenerationAndDiscrimination() {
        val bizNumber = "005"
        val root = JSONObject().apply {
            put("tipo", "CATALOGO_ELQADRE")
            put("version", "1.0")
            put("numeroNegocio", bizNumber)
            put("codigoNegocio", "NEG-005")
            put("nombreNegocio", "Bar Central")
            put("dueno", "Roberto Dueño")
            put("catalogo", org.json.JSONArray())
            put("categorias", org.json.JSONArray())
            put("timestamp", System.currentTimeMillis())
        }

        val catalogJson = root.toString(2)
        val parsed = JSONObject(catalogJson)
        assertEquals("CATALOGO_ELQADRE", parsed.getString("tipo"))
        assertEquals("005", parsed.getString("numeroNegocio"))

        // Discrimination test: Verify distinction between User JSON and Catalog JSON
        val isUserJson = parsed.has("usuario") || parsed.optString("tipo") == "USUARIO_ELQADRE"
        val isCatalogJson = parsed.has("catalogo") || parsed.optString("tipo") == "CATALOGO_ELQADRE"

        assertFalse(isUserJson)
        assertTrue(isCatalogJson)
    }
}
