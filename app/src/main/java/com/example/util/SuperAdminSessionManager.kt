package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor exclusivo para la persistencia y restauración de la sesión del Super Admin.
 * Guarda localmente un estado específico de sesión Super Admin, independiente de:
 * - sesión del Dueño;
 * - sesión de trabajadores;
 * - token comercial;
 * - PRUEBA_ACTIVA;
 * - LICENCIA_ACTIVA;
 * - autorización de negocio.
 *
 * La sesión se mantiene de forma persistente hasta que el Super Admin pulse explícitamente SALIR.
 * Al pulsar SALIR se elimina el estado de sesión abierta, sin alterar la autorización del dispositivo.
 */
object SuperAdminSessionManager {
    private const val PREFS_NAME = "elqadre_super_admin_session_prefs"
    private const val KEY_SESSION_ACTIVE = "super_admin_session_active"
    private const val KEY_SESSION_USERNAME = "super_admin_session_username"
    private const val KEY_SESSION_TIMESTAMP = "super_admin_session_timestamp"

    internal var prefsProvider: ((Context?) -> SharedPreferences)? = null

    private fun getPrefs(context: Context?): SharedPreferences {
        return prefsProvider?.invoke(context)
            ?: (context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?: throw IllegalStateException("Context required"))
    }

    /**
     * Comprueba si existe una sesión Super Admin persistente válida.
     */
    fun isSessionActive(context: Context?): Boolean {
        return getPrefs(context).getBoolean(KEY_SESSION_ACTIVE, false)
    }

    /**
     * Obtiene el nombre del usuario Super Admin de la sesión persistente.
     */
    fun getSessionUser(context: Context?): String? {
        val user = getPrefs(context).getString(KEY_SESSION_USERNAME, null)
        return if (!user.isNullOrBlank()) user else null
    }

    /**
     * Guarda localmente el estado de sesión abierta de Super Admin.
     */
    fun saveSession(context: Context?, username: String) {
        val trimmedUser = username.trim().ifBlank { "SuperAdmin" }
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putString(KEY_SESSION_USERNAME, trimmedUser)
            .putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Cierra la sesión Super Admin y elimina el estado local de sesión abierta.
     * La autorización del dispositivo como Super Admin NO se elimina.
     */
    fun clearSession(context: Context?) {
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .remove(KEY_SESSION_USERNAME)
            .remove(KEY_SESSION_TIMESTAMP)
            .apply()
    }
}
