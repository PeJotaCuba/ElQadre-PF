package com.example.util

import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.Jornada
import com.example.data.local.model.User
import com.example.data.local.model.UserRole

data class CajeroStatusInfo(
    val cajeroUser: User?,
    val phoneNumber: String,
    val isConfigured: Boolean,
    val statusDescription: String
)

object CajaPhoneHelper {

    /**
     * Resolves the valid, active Cajero's telephone information for comanda operations.
     * Sourced strictly from the active Cajero user configured in qusuarios.json.
     */
    fun resolveCajeroInfo(
        users: List<User>,
        activeJornada: Jornada?,
        generalConfig: ConfiguracionGeneral?
    ): CajeroStatusInfo {
        // 1. Check if an active open jornada was opened by a specific Cajero user
        val jornadaOpener = if (activeJornada != null && activeJornada.isOpen) {
            users.firstOrNull { it.username.equals(activeJornada.openedBy, ignoreCase = true) && it.role == UserRole.CAJERO && it.isActive }
        } else null

        // 2. Active Cajero users
        val activeCajeros = users.filter { it.role == UserRole.CAJERO && it.isActive }
        
        val targetCajero = when {
            jornadaOpener != null && jornadaOpener.telefono.isNotBlank() -> jornadaOpener
            jornadaOpener != null -> jornadaOpener
            else -> activeCajeros.firstOrNull { it.telefono.isNotBlank() }
                ?: activeCajeros.firstOrNull()
                ?: users.firstOrNull { it.role == UserRole.CAJERO && it.telefono.isNotBlank() }
                ?: users.firstOrNull { it.role == UserRole.CAJERO }
        }

        val phone = when {
            targetCajero != null && targetCajero.telefono.isNotBlank() -> targetCajero.telefono.trim()
            !generalConfig?.telefonoCajero.isNullOrBlank() -> generalConfig!!.telefonoCajero.trim()
            else -> ""
        }

        val isConfigured = phone.isNotBlank()

        val description = when {
            isConfigured && targetCajero != null -> "${targetCajero.fullName} (@${targetCajero.username})"
            isConfigured -> "Caja Central (Configuración General)"
            targetCajero != null -> "Caja no tiene teléfono configurado (${targetCajero.fullName})"
            else -> "Caja no tiene teléfono configurado (Sin usuario Cajero habilitado)"
        }

        return CajeroStatusInfo(
            cajeroUser = targetCajero,
            phoneNumber = phone,
            isConfigured = isConfigured,
            statusDescription = description
        )
    }
}
