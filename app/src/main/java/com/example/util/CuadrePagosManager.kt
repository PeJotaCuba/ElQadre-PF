package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Modelo para la distribución de pagos a un dependiente individual.
 */
data class DependientePagoDistribucion(
    val id: Int, // 1, 2, o 3
    val name: String,
    val username: String = "",
    val ventasProduccion: Double = 0.0,
    val ventasBebidas: Double = 0.0,
    val ventasTotales: Double = 0.0,
    val montoPago: Double = 0.0
)

/**
 * Modelo para el registro persistente de pagos de personal confirmados en una jornada.
 */
data class CuadrePagosJornada(
    val jornadaId: Long,
    val isConfirmed: Boolean = false,
    val confirmedAt: Long = 0L,
    val confirmedBy: String = "",
    val totalPagos: Double = 0.0,
    val totalCocina: Double = 0.0,
    val totalCajero: Double = 0.0,
    val totalDependiente: Double = 0.0,
    val cantidadDependientes: Int = 1,
    val dependientes: List<DependientePagoDistribucion> = emptyList(),
    val distributionMode: String = "CATEGORIA", // "CATEGORIA" o "GLOBAL"
    val efectivoContado: Double = 0.0,
    val dineroFinalEnCaja: Double = 0.0,
    val cantidadCocineros: Int = 1
)

/**
 * Gestor de persistencia para los pagos de personal asociados al Cuadre de Caja del Dueño.
 * Conserva la distribución, importes y estado de confirmación por Jornada, garantizando
 * que no se dupliquen pagos accidentales al salir y volver a entrar a la sección.
 */
object CuadrePagosManager {
    private const val PREFS_NAME = "elqadre_cuadre_pagos_prefs"
    private const val KEY_PREFIX = "pagos_jornada_"

    fun getPagosJornada(context: Context, jornadaId: Long): CuadrePagosJornada? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("$KEY_PREFIX$jornadaId", null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            val depsArray = obj.optJSONArray("dependientes") ?: JSONArray()
            val depsList = mutableListOf<DependientePagoDistribucion>()
            for (i in 0 until depsArray.length()) {
                val dObj = depsArray.getJSONObject(i)
                depsList.add(
                    DependientePagoDistribucion(
                        id = dObj.optInt("id", i + 1),
                        name = dObj.optString("name", "Dependiente ${i + 1}"),
                        username = dObj.optString("username", ""),
                        ventasProduccion = dObj.optDouble("ventasProduccion", 0.0),
                        ventasBebidas = dObj.optDouble("ventasBebidas", 0.0),
                        ventasTotales = dObj.optDouble("ventasTotales", 0.0),
                        montoPago = dObj.optDouble("montoPago", 0.0)
                    )
                )
            }
            CuadrePagosJornada(
                jornadaId = obj.optLong("jornadaId", jornadaId),
                isConfirmed = obj.optBoolean("isConfirmed", false),
                confirmedAt = obj.optLong("confirmedAt", 0L),
                confirmedBy = obj.optString("confirmedBy", ""),
                totalPagos = obj.optDouble("totalPagos", 0.0),
                totalCocina = obj.optDouble("totalCocina", 0.0),
                totalCajero = obj.optDouble("totalCajero", 0.0),
                totalDependiente = obj.optDouble("totalDependiente", 0.0),
                cantidadDependientes = obj.optInt("cantidadDependientes", 1),
                dependientes = depsList,
                distributionMode = obj.optString("distributionMode", "CATEGORIA"),
                efectivoContado = obj.optDouble("efectivoContado", 0.0),
                dineroFinalEnCaja = obj.optDouble("dineroFinalEnCaja", 0.0),
                cantidadCocineros = obj.optInt("cantidadCocineros", 1)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun savePagosJornada(context: Context, pagos: CuadrePagosJornada) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        obj.put("jornadaId", pagos.jornadaId)
        obj.put("isConfirmed", pagos.isConfirmed)
        obj.put("confirmedAt", pagos.confirmedAt)
        obj.put("confirmedBy", pagos.confirmedBy)
        obj.put("totalPagos", pagos.totalPagos)
        obj.put("totalCocina", pagos.totalCocina)
        obj.put("totalCajero", pagos.totalCajero)
        obj.put("totalDependiente", pagos.totalDependiente)
        obj.put("cantidadDependientes", pagos.cantidadDependientes)
        obj.put("distributionMode", pagos.distributionMode)
        obj.put("efectivoContado", pagos.efectivoContado)
        obj.put("dineroFinalEnCaja", pagos.dineroFinalEnCaja)
        obj.put("cantidadCocineros", pagos.cantidadCocineros)

        val depsArray = JSONArray()
        for (dep in pagos.dependientes) {
            val dObj = JSONObject()
            dObj.put("id", dep.id)
            dObj.put("name", dep.name)
            dObj.put("username", dep.username)
            dObj.put("ventasProduccion", dep.ventasProduccion)
            dObj.put("ventasBebidas", dep.ventasBebidas)
            dObj.put("ventasTotales", dep.ventasTotales)
            dObj.put("montoPago", dep.montoPago)
            depsArray.put(dObj)
        }
        obj.put("dependientes", depsArray)

        prefs.edit().putString("$KEY_PREFIX${pagos.jornadaId}", obj.toString()).apply()
    }

    fun clearPagosJornada(context: Context, jornadaId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PREFIX$jornadaId").apply()
    }
}
