package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Modelo de datos para las tarifas globales de pago de personal asociadas a Bebidas en Mercaderías.
 */
data class TarifasPagoBebidas(
    val pagoDependientePorUnidad: Double = 0.0,
    val pagoCajeroPorUnidad: Double = 0.0
) {
    /**
     * Pago total de personal por unidad vendida = Pago Dependiente + Pago Cajero
     */
    val totalPagoPersonalPorUnidad: Double
        get() = pagoDependientePorUnidad + pagoCajeroPorUnidad
}

/**
 * Gestor de persistencia local para las tarifas de Bebidas en Mercaderías.
 * Asegura que los valores se conserven al cerrar la pantalla, cerrar la aplicación
 * o volver a abrir la app.
 */
object BebidasTarifasPreferences {
    private const val PREFS_NAME = "elqadre_bebidas_tarifas_prefs"
    private const val KEY_PAGO_DEPENDIENTE = "pago_dependiente_bebida"
    private const val KEY_PAGO_CAJERO = "pago_cajero_bebida"

    fun getTarifas(context: Context): TarifasPagoBebidas {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dep = prefs.getString(KEY_PAGO_DEPENDIENTE, "0.0")?.toDoubleOrNull() ?: 0.0
        val caj = prefs.getString(KEY_PAGO_CAJERO, "0.0")?.toDoubleOrNull() ?: 0.0
        return TarifasPagoBebidas(
            pagoDependientePorUnidad = maxOf(0.0, dep),
            pagoCajeroPorUnidad = maxOf(0.0, caj)
        )
    }

    fun saveTarifas(context: Context, tarifas: TarifasPagoBebidas) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PAGO_DEPENDIENTE, tarifas.pagoDependientePorUnidad.toString())
            .putString(KEY_PAGO_CAJERO, tarifas.pagoCajeroPorUnidad.toString())
            .apply()
    }
}
