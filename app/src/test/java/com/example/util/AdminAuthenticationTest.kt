package com.example.util

import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import org.junit.Assert.*
import org.junit.Test

class AdminAuthenticationTest {

    @Test
    fun testInitialAdminCredentialMatching() {
        val initialUser = "admin"
        val initialPass = "admin26"

        val defaultAdmin = User(
            username = initialUser,
            fullName = "Administrador Principal",
            passwordHash = initialPass.toSha256(),
            role = UserRole.ADMIN,
            isActive = true,
            montoPorProducto = 0.0
        )

        // Verify that input "admin" and "admin26" matches
        val inputUser = " admin "
        val inputPass = " admin26 "

        assertEquals("admin", inputUser.trim().lowercase())
        assertEquals(defaultAdmin.passwordHash, inputPass.trim().toSha256())
        assertTrue(defaultAdmin.isActive)
        assertEquals(UserRole.ADMIN, defaultAdmin.role)
    }

    @Test
    fun testAdminPasswordChangeInvalidatesOldPassword() {
        val initialPass = "admin26"
        val newPass = "clavePersonalizada123"

        val adminUser = User(
            username = "admin",
            fullName = "Administrador Principal",
            passwordHash = initialPass.toSha256(),
            role = UserRole.ADMIN,
            isActive = true,
            montoPorProducto = 0.0
        )

        // Initial check
        assertTrue(adminUser.passwordHash == initialPass.trim().toSha256())

        // Update password
        val updatedAdmin = adminUser.copy(passwordHash = newPass.trim().toSha256())

        // Verify old password "admin26" is now rejected
        assertFalse(updatedAdmin.passwordHash == initialPass.trim().toSha256())
        // Verify new password is accepted
        assertTrue(updatedAdmin.passwordHash == newPass.trim().toSha256())
    }

    @Test
    fun testUsernameNormalization() {
        val testInputs = listOf("admin", "Admin", "ADMIN", " admin ", " Admin\t")
        for (input in testInputs) {
            val normalized = input.trim().lowercase()
            assertEquals("admin", normalized)
        }
    }

    // --- TEST ESCENARIO 1: Instalación nueva (el usuario no existe) ---
    @Test
    fun testMigrationScenarioNewInstall() {
        var mockDatabaseUser: User? = null
        var databaseInsertCalled = false

        // Simular lógica de ensureAdminUserExists para instalación limpia
        val existingAdmin: User? = mockDatabaseUser
        val finalAdmin: User = if (existingAdmin == null) {
            val newAdmin = User(
                username = "admin",
                fullName = "Administrador Principal",
                passwordHash = "admin26".toSha256(),
                role = UserRole.ADMIN,
                isActive = true,
                montoPorProducto = 0.0
            )
            mockDatabaseUser = newAdmin
            databaseInsertCalled = true
            newAdmin
        } else {
            existingAdmin
        }

        assertTrue(databaseInsertCalled)
        assertNotNull(mockDatabaseUser)
        assertEquals("admin26".toSha256(), mockDatabaseUser?.passwordHash)
    }

    // --- TEST ESCENARIO 2: Base heredada con hash de "1234" ---
    @Test
    fun testMigrationScenarioLegacyHash() {
        val legacyHash = "1234".toSha256()
        var mockDatabaseUser: User? = User(
            username = "admin",
            fullName = "Administrador Principal",
            passwordHash = legacyHash,
            role = UserRole.ADMIN,
            isActive = true,
            montoPorProducto = 0.0
        )
        var databaseUpdateCalled = false

        // Simular lógica de ensureAdminUserExists para base heredada con hash antiguo de "1234"
        val existingAdmin: User? = mockDatabaseUser
        if (existingAdmin != null) {
            if (existingAdmin.passwordHash == legacyHash) {
                val migratedAdmin = existingAdmin.copy(passwordHash = "admin26".toSha256())
                mockDatabaseUser = migratedAdmin
                databaseUpdateCalled = true
            }
        }

        assertTrue(databaseUpdateCalled)
        assertNotNull(mockDatabaseUser)
        assertEquals("admin26".toSha256(), mockDatabaseUser?.passwordHash)
    }

    // --- TEST ESCENARIO 3: Administrador que ya cambió su contraseña ---
    @Test
    fun testMigrationScenarioCustomPasswordUnchanged() {
        val customPasswordHash = "MiClaveSuperSegura99".toSha256()
        val legacyHash = "1234".toSha256()
        var mockDatabaseUser: User? = User(
            username = "admin",
            fullName = "Administrador Principal",
            passwordHash = customPasswordHash,
            role = UserRole.ADMIN,
            isActive = true,
            montoPorProducto = 0.0
        )
        var databaseUpdateOrInsertCalled = false

        // Simular lógica de ensureAdminUserExists para contraseña cambiada
        val existingAdmin: User? = mockDatabaseUser
        if (existingAdmin != null) {
            if (existingAdmin.passwordHash == legacyHash) {
                val migratedAdmin = existingAdmin.copy(passwordHash = "admin26".toSha256())
                mockDatabaseUser = migratedAdmin
                databaseUpdateOrInsertCalled = true
            }
        }

        // NO debe haberse modificado, debe conservar su hash personalizado intacto
        assertFalse(databaseUpdateOrInsertCalled)
        assertNotNull(mockDatabaseUser)
        assertEquals(customPasswordHash, mockDatabaseUser?.passwordHash)
        assertNotEquals("admin26".toSha256(), mockDatabaseUser?.passwordHash)
    }
}

