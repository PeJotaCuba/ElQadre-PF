package com.example.util

import android.content.Context

object ProduccionModoHelper {
    private const val PREFS_NAME = "produccion_modo_contabilizacion_prefs"
    private const val KEY_PREFIX = "modo_prod_"

    fun getModo(context: Context, productId: Long): String {
        if (productId <= 0) return "POR_TANDAS"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_PREFIX$productId", "POR_TANDAS") ?: "POR_TANDAS"
    }

    fun isDirecto(context: Context, productId: Long): Boolean {
        return getModo(context, productId) == "DIRECTO"
    }

    fun setModo(context: Context, productId: Long, modo: String) {
        if (productId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PREFIX$productId", modo).apply()
    }
}
