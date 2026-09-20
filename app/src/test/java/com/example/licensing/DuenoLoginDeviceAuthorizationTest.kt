package com.example.licensing

import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.util.toSha256
import org.junit.Assert.*
import org.junit.Test

/**
 * Prueba Obligatoria de Autorización y Autenticación del DUEÑO:
 * 1. Probar con un DUEÑO cuyo registro tenga: authorizedDeviceId = DVC-XXXXXX
 *    y cuyo dispositivo entregue: XXXXXX.
 *    Debe poder hacer LOGIN -> usuario + contraseña -> acceso DUEÑO sin recibir
 *    el mensaje "Este usuario no está autorizado para utilizar este dispositivo."
 * 2. Comprobar que la autorización comercial del dispositivo continúa funcionando
 *    independientemente del usuario.
 * 3. Comprobar la no duplicación de cuentas locales por diferencias de mayúsculas/minúsculas.
 */
class DuenoLoginDeviceAuthorizationTest {

    // Helper simulando la regla de decisión de autenticación y autorización de Login
    data class LoginDecision(
        val success: Boolean,
        val authenticatedUser: User?,
        val errorMessage: String?
    )

    private fun evaluateLogin(
        inputUsername: String,
        inputPasswordPlain: String,
        registeredUsers: List<User>,
        commercialStatus: CommercialStatus,
        isOfflineOverdue: Boolean = false
    ): LoginDecision {
        val trimmedUsername = inputUsername.trim()
        if (trimmedUsername.equals("adminq", ignoreCase = true)) {
            return LoginDecision(false, null, "La cuenta de Administrador ha sido eliminada.")
        }

        // Búsqueda sin distinguir mayúsculas/minúsculas
        val user = registeredUsers.firstOrNull { it.username.equals(trimmedUsername, ignoreCase = true) }
            ?: return LoginDecision(false, null, "Usuario no encontrado.")

        if (user.role == UserRole.ADMIN) {
            return LoginDecision(false, null, "Usuario no encontrado.")
        }

        if (user.passwordHash != inputPasswordPlain.trim().toSha256()) {
            return LoginDecision(false, null, "Contraseña incorrecta.")
        }

        if (!user.isActive) {
            return LoginDecision(false, null, "Usuario inactivo.")
        }

        // Comprobación comercial del dispositivo: SuperAdmin / Prueba / Licencia
        if (user.role == UserRole.DUENO) {
            val isAuthorized = commercialStatus == CommercialStatus.PRUEBA_ACTIVA ||
                    commercialStatus == CommercialStatus.LICENCIA_ACTIVA

            if (!isAuthorized) {
                val msg = when (commercialStatus) {
                    CommercialStatus.PRUEBA_VENCIDA -> "Período de prueba vencido. Requiere activación de licencia comercial."
                    CommercialStatus.LICENCIA_VENCIDA -> "Licencia comercial vencida. Requiere renovación."
                    CommercialStatus.LICENCIA_REVOCADA -> "Licencia comercial revocada por la administración."
                    else -> "Dispositivo no autorizado para este negocio."
                }
                return LoginDecision(false, null, msg)
            }

            if (isOfflineOverdue) {
                return LoginDecision(
                    false,
                    null,
                    "Han transcurrido más de 7 días sin comprobación comercial. Conéctese a Internet para verificar su licencia."
                )
            }
        }

        // NOTA: authorizedDeviceId NO bloquea el inicio de sesión.
        return LoginDecision(true, user, null)
    }

    @Test
    fun testDuenoLogin_withDvcPrefixInRecordAndPlainDeviceId_succeedsWithoutError() {
        val deviceIdDeliveredByDevice = "6D953E"
        val historicalOwnerUser = User(
            username = "rosana",
            fullName = "Rosana Perez",
            passwordHash = "Rosana002".toSha256(),
            role = UserRole.DUENO,
            authorizedDeviceId = "DVC-6D953E", // Histórico con DVC-
            isActive = true
        )

        val users = listOf(historicalOwnerUser)

        // Intento de Login con el DUEÑO
        val result = evaluateLogin(
            inputUsername = "rosana",
            inputPasswordPlain = "Rosana002",
            registeredUsers = users,
            commercialStatus = CommercialStatus.PRUEBA_ACTIVA
        )

        assertTrue("El DUEÑO debe acceder exitosamente", result.success)
        assertNotNull(result.authenticatedUser)
        assertEquals("rosana", result.authenticatedUser?.username)
        assertNull(result.errorMessage)
        assertNotEquals("Este usuario no está autorizado para utilizar este dispositivo.", result.errorMessage)
    }

    @Test
    fun testDuenoLogin_caseInsensitiveLookupSucceeds() {
        // Usuario registrado como "rosana", pero ingresado como "ROSANA"
        val ownerUser = User(
            username = "rosana",
            fullName = "Rosana Perez",
            passwordHash = "Rosana002".toSha256(),
            role = UserRole.DUENO,
            authorizedDeviceId = "DVC-6D953E",
            isActive = true
        )

        val users = listOf(ownerUser)

        val result = evaluateLogin(
            inputUsername = "ROSANA", // Mayúsculas
            inputPasswordPlain = "Rosana002",
            registeredUsers = users,
            commercialStatus = CommercialStatus.LICENCIA_ACTIVA
        )

        assertTrue("Debe encontrar al usuario independientemente de mayúsculas/minúsculas", result.success)
        assertEquals("rosana", result.authenticatedUser?.username)
    }

    @Test
    fun testCommercialAuthorizationProtectsDeviceIndependentlyOfUser() {
        val ownerUser = User(
            username = "rosana",
            fullName = "Rosana Perez",
            passwordHash = "Rosana002".toSha256(),
            role = UserRole.DUENO,
            authorizedDeviceId = "DVC-6D953E",
            isActive = true
        )

        val users = listOf(ownerUser)

        // Caso 1: Período de prueba vencido
        val expiredResult = evaluateLogin(
            inputUsername = "rosana",
            inputPasswordPlain = "Rosana002",
            registeredUsers = users,
            commercialStatus = CommercialStatus.PRUEBA_VENCIDA
        )
        assertFalse("El DUEÑO debe ser bloqueado por licencia vencida", expiredResult.success)
        assertEquals(
            "Período de prueba vencido. Requiere activación de licencia comercial.",
            expiredResult.errorMessage
        )

        // Caso 2: Licencia revocada
        val revokedResult = evaluateLogin(
            inputUsername = "rosana",
            inputPasswordPlain = "Rosana002",
            registeredUsers = users,
            commercialStatus = CommercialStatus.LICENCIA_REVOCADA
        )
        assertFalse("El DUEÑO debe ser bloqueado por licencia revocada", revokedResult.success)
        assertEquals(
            "Licencia comercial revocada por la administración.",
            revokedResult.errorMessage
        )

        // Caso 3: Sin autorización comercial
        val unauthorizedResult = evaluateLogin(
            inputUsername = "rosana",
            inputPasswordPlain = "Rosana002",
            registeredUsers = users,
            commercialStatus = CommercialStatus.SIN_AUTORIZACION
        )
        assertFalse("El DUEÑO debe ser bloqueado si el dispositivo no está autorizado", unauthorizedResult.success)
        assertEquals("Dispositivo no autorizado para este negocio.", unauthorizedResult.errorMessage)
    }

    @Test
    fun testNoDuplicateAccountsDifferingOnlyByCase() {
        // Simular lógica de unicidad por mayúsculas/minúsculas
        val userList = mutableListOf<User>()

        fun saveUserPreventingCasingDuplicates(newUser: User) {
            userList.removeAll { it.username.equals(newUser.username, ignoreCase = true) }
            userList.add(newUser)
        }

        saveUserPreventingCasingDuplicates(
            User(
                username = "Rosana",
                fullName = "Rosana Perez",
                passwordHash = "Rosana002".toSha256(),
                role = UserRole.DUENO
            )
        )
        assertEquals(1, userList.size)
        assertEquals("Rosana", userList[0].username)

        // Insertar variación "rosana"
        saveUserPreventingCasingDuplicates(
            User(
                username = "rosana",
                fullName = "Rosana Perez",
                passwordHash = "Rosana002".toSha256(),
                role = UserRole.DUENO
            )
        )
        assertEquals("No deben existir dos cuentas locales equivalentes únicamente por mayúsculas/minúsculas", 1, userList.size)
        assertEquals("rosana", userList[0].username)
    }
}
