package com.example.util

import android.content.Context

/**
 * Gestor de preferencias locales para la personalización de módulos visibles
 * en la sesión de cada usuario DUEÑO.
 *
 * Guardado exclusivo localmente por dispositivo y clave de usuario (username).
 * No modifica permisos, datos de base de datos ni lógica interna del sistema.
 */
object DuenoSessionPreferences {

    private const val PREFS_NAME = "elqadre_dueno_session_prefs"
    private const val KEY_PREFIX = "visible_modules_"

    val ALL_MODULES = setOf(
        "PRODUCCION",
        "MERCADERIAS",
        "INVENTARIO",
        "CATALOGO",
        "INVERSIONES",
        "GASTOS",
        "CONTROL_NEGOCIO",
        "PERSONAL",
        "AJUSTES"
    )

    /**
     * Verifica si un módulo específico debe estar visible según el conjunto de preferencias guardadas.
     * Mantiene retrocompatibilidad con la clave "INVENTARIO".
     */
    fun isModuleVisible(visibleModules: Set<String>, moduleKey: String): Boolean {
        return when (moduleKey) {
            "INVENTARIO" -> visibleModules.contains("INVENTARIO") || visibleModules.contains("PRODUCCION") || visibleModules.contains("MERCADERIAS")
            "PRODUCCION" -> visibleModules.contains("PRODUCCION") || visibleModules.contains("INVENTARIO")
            "MERCADERIAS" -> visibleModules.contains("MERCADERIAS") || visibleModules.contains("INVENTARIO")
            else -> visibleModules.contains(moduleKey)
        }
    }

    /**
     * Obtiene el conjunto de nombres de módulos visibles para el usuario [username].
     * Si no existe una configuración previa, retorna por defecto TODOS los módulos.
     */
    fun getVisibleModules(context: Context, username: String?): Set<String> {
        val userKey = if (!username.isNullOrBlank()) username.trim().lowercase() else "dueno"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_PREFIX$userKey"
        val saved = prefs.getStringSet(key, null)
        return saved ?: ALL_MODULES
    }

    /**
     * Guarda el conjunto de módulos visibles para el usuario [username] de forma local.
     */
    fun setVisibleModules(context: Context, username: String?, modules: Set<String>) {
        val userKey = if (!username.isNullOrBlank()) username.trim().lowercase() else "dueno"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_PREFIX$userKey"
        prefs.edit().putStringSet(key, modules).apply()
    }

    /**
     * Restablece la configuración por defecto (todos los módulos visibles).
     */
    fun resetToDefault(context: Context, username: String?) {
        setVisibleModules(context, username, ALL_MODULES)
    }
}
