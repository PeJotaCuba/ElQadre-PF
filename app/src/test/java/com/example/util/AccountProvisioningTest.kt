package com.example.util

import com.example.data.local.model.UserRole
import org.junit.Assert.*
import org.junit.Test

class AccountProvisioningTest {

    @Test
    fun testBuildAccountDeliverySmsFormat() {
        val sms = AccountProvisioningHelper.buildAccountDeliverySms(
            numeroNegocio = "1",
            nombre = "Juan Perez",
            usuario = "juanp",
            contrasenaInicial = "abc123",
            rol = "CAJERO"
        )

        assertEquals(
            "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan%20Perez|USUARIO=juanp|CLAVE=abc123|ROL=CAJERO",
            sms
        )
    }

    @Test
    fun testBuildAccountDeliverySmsWithSpecialCharactersAndRoles() {
        val smsDueno = AccountProvisioningHelper.buildAccountDeliverySms(
            numeroNegocio = "042",
            nombre = "María José Peña",
            usuario = "maria_j",
            contrasenaInicial = "pass#2026",
            rol = "DUEÑO"
        )

        assertTrue(smsDueno.startsWith("ELQADRE|ALTA_USUARIO|V1|"))
        assertTrue(smsDueno.contains("NEGOCIO=042"))
        assertTrue(smsDueno.contains("USUARIO=maria_j"))
        assertTrue(smsDueno.contains("CLAVE=pass%232026"))

        val parsed = AccountProvisioningHelper.parseAccountSms(smsDueno)
        assertNotNull(parsed)
        assertEquals("042", parsed?.negocio)
        assertEquals("María José Peña", parsed?.nombre)
        assertEquals("maria_j", parsed?.usuario)
        assertEquals("pass#2026", parsed?.contrasenaInicial)
        assertEquals(UserRole.DUENO, parsed?.rol)
    }

    @Test
    fun testParseExampleFromSpecification() {
        val rawSms = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan%20Perez|USUARIO=juanp|CLAVE=abc123|ROL=CAJERO"
        
        assertTrue(AccountProvisioningHelper.isAccountSms(rawSms))

        val parsed = AccountProvisioningHelper.parseAccountSms(rawSms)
        assertNotNull(parsed)
        assertEquals("001", parsed?.negocio)
        assertEquals("Juan Perez", parsed?.nombre)
        assertEquals("juanp", parsed?.usuario)
        assertEquals("abc123", parsed?.contrasenaInicial)
        assertEquals(UserRole.CAJERO, parsed?.rol)
    }

    @Test
    fun testAllowedOperationalRolesParsing() {
        val operationalRolesToTest = listOf(
            "DUEÑO" to UserRole.DUENO,
            "DEPENDIENTE" to UserRole.DEPENDIENTE,
            "CAJERO" to UserRole.CAJERO,
            "COCINA" to UserRole.COCINA
        )

        for ((rolStr, expectedRole) in operationalRolesToTest) {
            val sms = AccountProvisioningHelper.buildAccountDeliverySms(
                numeroNegocio = "005",
                nombre = "Usuario $rolStr",
                usuario = "user_${rolStr.lowercase()}",
                contrasenaInicial = "pass123",
                rol = rolStr
            )
            val parsed = AccountProvisioningHelper.parseAccountSms(sms)
            assertNotNull("Operational role $rolStr should parse successfully", parsed)
            assertEquals(expectedRole, parsed?.rol)
            assertEquals("005", parsed?.negocio)
            assertEquals("Usuario $rolStr", parsed?.nombre)
            assertEquals("user_${rolStr.lowercase()}", parsed?.usuario)
            assertEquals("pass123", parsed?.contrasenaInicial)
        }
    }

    @Test
    fun testRejectAdministratorRoleViaSms() {
        // Must not create an ADMINISTRADOR account via this distribution mechanism
        val adminSms = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Admin%20Two|USUARIO=admin2|CLAVE=123456|ROL=ADMINISTRADOR"
        assertNull(AccountProvisioningHelper.parseAccountSms(adminSms))
    }

    @Test
    fun testRejectInvalidHeader() {
        val invalidSms = "OTRO_ENCABEZADO|NEGOCIO=001|NOMBRE=Juan|USUARIO=juan|CLAVE=123|ROL=CAJERO"
        assertFalse(AccountProvisioningHelper.isAccountSms(invalidSms))
        assertNull(AccountProvisioningHelper.parseAccountSms(invalidSms))
    }

    @Test
    fun testRejectInvalidBusinessNumber() {
        // Business number not having exactly 3 digits
        val invalidBiz1 = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=1|NOMBRE=Juan|USUARIO=juan|CLAVE=123|ROL=CAJERO"
        val invalidBiz2 = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=1234|NOMBRE=Juan|USUARIO=juan|CLAVE=123|ROL=CAJERO"
        val invalidBiz3 = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=ABC|NOMBRE=Juan|USUARIO=juan|CLAVE=123|ROL=CAJERO"

        assertNull(AccountProvisioningHelper.parseAccountSms(invalidBiz1))
        assertNull(AccountProvisioningHelper.parseAccountSms(invalidBiz2))
        assertNull(AccountProvisioningHelper.parseAccountSms(invalidBiz3))
    }

    @Test
    fun testRejectInvalidRole() {
        val invalidRoleSms = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan|USUARIO=juan|CLAVE=123|ROL=SUPERVISOR"
        assertNull(AccountProvisioningHelper.parseAccountSms(invalidRoleSms))
    }

    @Test
    fun testRejectMissingFields() {
        val missingClave = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan|USUARIO=juan|ROL=CAJERO"
        assertNull(AccountProvisioningHelper.parseAccountSms(missingClave))

        val missingUser = "ELQADRE|ALTA_USUARIO|V1|NEGOCIO=001|NOMBRE=Juan|CLAVE=123|ROL=CAJERO"
        assertNull(AccountProvisioningHelper.parseAccountSms(missingUser))
    }
}
