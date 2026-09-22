package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Modelo de datos para las tarifas globales de pago de personal asociadas a Bebidas en Mercaderías.
 */
data class TarifasPagoBebidas(
    val pagoDependientePorUnidad: Double = 0.0,
    val pagoCajeroPorUnidad: Double = 0.0,
    val pagoDependienteModalidad: String = "FIJO",
    val pagoDependienteValor: Double = 0.0,
    val pagoCajeroModalidad: String = "FIJO",
    val pagoCajeroValor: Double = 0.0
) {
    /**
     * Pago total de personal por unidad vendida = Pago Dependiente + Pago Cajero
     */
    val totalPagoPersonalPorUnidad: Double
        get() = pagoDependientePorUnidad + pagoCajeroPorUnidad

    fun calcularPagoDependiente(precioVenta: Double): Double {
        return if (pagoDependienteModalidad == "PORCENTAJE") {
            precioVenta * pagoDependienteValor / 100.0
        } else {
            pagoDependienteValor
        }
    }

    fun calcularPagoCajero(precioVenta: Double): Double {
        return if (pagoCajeroModalidad == "PORCENTAJE") {
            precioVenta * pagoCajeroValor / 100.0
        } else {
            pagoCajeroValor
        }
    }

    fun calcularTotalPagoPersonal(precioVenta: Double): Double {
        return calcularPagoDependiente(precioVenta) + calcularPagoCajero(precioVenta)
    }
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

        val depMod = prefs.getString("pago_dependiente_modalidad_bebida", "FIJO") ?: "FIJO"
        val depVal = prefs.getString("pago_dependiente_valor_bebida", dep.toString())?.toDoubleOrNull() ?: dep

        val cajMod = prefs.getString("pago_cajero_modalidad_bebida", "FIJO") ?: "FIJO"
        val cajVal = prefs.getString("pago_cajero_valor_bebida", caj.toString())?.toDoubleOrNull() ?: caj

        return TarifasPagoBebidas(
            pagoDependientePorUnidad = maxOf(0.0, if (depMod == "FIJO") depVal else 0.0),
            pagoCajeroPorUnidad = maxOf(0.0, if (cajMod == "FIJO") cajVal else 0.0),
            pagoDependienteModalidad = depMod,
            pagoDependienteValor = maxOf(0.0, depVal),
            pagoCajeroModalidad = cajMod,
            pagoCajeroValor = maxOf(0.0, cajVal)
        )
    }

    fun saveTarifas(context: Context, tarifas: TarifasPagoBebidas) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PAGO_DEPENDIENTE, (if (tarifas.pagoDependienteModalidad == "FIJO") tarifas.pagoDependienteValor else 0.0).toString())
            .putString(KEY_PAGO_CAJERO, (if (tarifas.pagoCajeroModalidad == "FIJO") tarifas.pagoCajeroValor else 0.0).toString())
            .putString("pago_dependiente_modalidad_bebida", tarifas.pagoDependienteModalidad)
            .putString("pago_dependiente_valor_bebida", tarifas.pagoDependienteValor.toString())
            .putString("pago_cajero_modalidad_bebida", tarifas.pagoCajeroModalidad)
            .putString("pago_cajero_valor_bebida", tarifas.pagoCajeroValor.toString())
            .apply()
    }
}
