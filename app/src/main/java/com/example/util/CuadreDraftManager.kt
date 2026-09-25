package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ProduccionDraftItem(
    val productId: Long,
    val isSpecialPresentation: Boolean = false,
    val specialPresentationName: String = "",
    val defectuosoStr: String = "0",
    val consumoStr: String = "0",
    val regaliaStr: String = "0",
    val pendientesStr: String = "0",
    val customTotalProducedStr: String = "0"
)

data class MercaderiaDraftItem(
    val mercaderiaId: Long,
    val existenciaInicialStr: String = "",
    val entradasStr: String = "",
    val existenciaFinalStr: String = "",
    val defectuosoStr: String = "0",
    val consumoStr: String = "0",
    val regaliaStr: String = "0",
    val customPriceStr: String = ""
)

data class AgregadoDraftItem(
    val materiaPrimaId: Long,
    val existenciaInicialStr: String = "",
    val entradasStr: String = "",
    val existenciaFinalStr: String = "",
    val mermaStr: String = "0"
)

data class ExtraccionDraftItem(
    val montoStr: String = "",
    val descripcion: String = ""
)

data class CuadreDraftData(
    val jornadaId: Long,
    val produccionDrafts: List<ProduccionDraftItem> = emptyList(),
    val mercaderiaDrafts: List<MercaderiaDraftItem> = emptyList(),
    val agregadoDrafts: List<AgregadoDraftItem> = emptyList(),
    val efectivoRealStr: String = "",
    val notasCuadre: String = "",
    val extracciones: List<ExtraccionDraftItem> = emptyList(),
    val otrasTransferenciasStr: String = ""
)

/**
 * Gestor de persistencia para el borrador activo del Cuadre de Caja.
 * Mantiene guardados los datos introducidos durante la jornada (INICIO, FINAL, ENTRADAS, MERMAS, EFECTIVO, etc.)
 * evitando su pérdida al salir de la pantalla, cambiar de apartado o reiniciar la app.
 */
object CuadreDraftManager {
    private const val PREFS_NAME = "elqadre_cuadre_draft_prefs"
    private const val KEY_PREFIX = "draft_jornada_"

    fun getDraft(context: Context, jornadaId: Long): CuadreDraftData? {
        if (jornadaId <= 0) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("$KEY_PREFIX$jornadaId", null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)

            val prodArray = obj.optJSONArray("produccion") ?: JSONArray()
            val prodList = mutableListOf<ProduccionDraftItem>()
            for (i in 0 until prodArray.length()) {
                val p = prodArray.getJSONObject(i)
                prodList.add(
                    ProduccionDraftItem(
                        productId = p.optLong("productId"),
                        isSpecialPresentation = p.optBoolean("isSpecialPresentation", false),
                        specialPresentationName = p.optString("specialPresentationName", ""),
                        defectuosoStr = p.optString("defectuosoStr", "0"),
                        consumoStr = p.optString("consumoStr", "0"),
                        regaliaStr = p.optString("regaliaStr", "0"),
                        pendientesStr = p.optString("pendientesStr", "0"),
                        customTotalProducedStr = p.optString("customTotalProducedStr", "0")
                    )
                )
            }

            val mercArray = obj.optJSONArray("mercaderia") ?: JSONArray()
            val mercList = mutableListOf<MercaderiaDraftItem>()
            for (i in 0 until mercArray.length()) {
                val m = mercArray.getJSONObject(i)
                mercList.add(
                    MercaderiaDraftItem(
                        mercaderiaId = m.optLong("mercaderiaId"),
                        existenciaInicialStr = m.optString("existenciaInicialStr", ""),
                        entradasStr = m.optString("entradasStr", ""),
                        existenciaFinalStr = m.optString("existenciaFinalStr", ""),
                        defectuosoStr = m.optString("defectuosoStr", "0"),
                        consumoStr = m.optString("consumoStr", "0"),
                        regaliaStr = m.optString("regaliaStr", "0"),
                        customPriceStr = m.optString("customPriceStr", "")
                    )
                )
            }

            val agArray = obj.optJSONArray("agregados") ?: JSONArray()
            val agList = mutableListOf<AgregadoDraftItem>()
            for (i in 0 until agArray.length()) {
                val a = agArray.getJSONObject(i)
                agList.add(
                    AgregadoDraftItem(
                        materiaPrimaId = a.optLong("materiaPrimaId"),
                        existenciaInicialStr = a.optString("existenciaInicialStr", ""),
                        entradasStr = a.optString("entradasStr", ""),
                        existenciaFinalStr = a.optString("existenciaFinalStr", ""),
                        mermaStr = a.optString("mermaStr", "0")
                    )
                )
            }

            val extArray = obj.optJSONArray("extracciones") ?: JSONArray()
            val extList = mutableListOf<ExtraccionDraftItem>()
            for (i in 0 until extArray.length()) {
                val e = extArray.getJSONObject(i)
                extList.add(
                    ExtraccionDraftItem(
                        montoStr = e.optString("montoStr", ""),
                        descripcion = e.optString("descripcion", "")
                    )
                )
            }

            CuadreDraftData(
                jornadaId = obj.optLong("jornadaId", jornadaId),
                produccionDrafts = prodList,
                mercaderiaDrafts = mercList,
                agregadoDrafts = agList,
                efectivoRealStr = obj.optString("efectivoRealStr", ""),
                notasCuadre = obj.optString("notasCuadre", ""),
                extracciones = extList,
                otrasTransferenciasStr = obj.optString("otrasTransferenciasStr", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveDraft(context: Context, draft: CuadreDraftData) {
        if (draft.jornadaId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        obj.put("jornadaId", draft.jornadaId)

        val prodArray = JSONArray()
        draft.produccionDrafts.forEach { p ->
            val pObj = JSONObject()
            pObj.put("productId", p.productId)
            pObj.put("isSpecialPresentation", p.isSpecialPresentation)
            pObj.put("specialPresentationName", p.specialPresentationName)
            pObj.put("defectuosoStr", p.defectuosoStr)
            pObj.put("consumoStr", p.consumoStr)
            pObj.put("regaliaStr", p.regaliaStr)
            pObj.put("pendientesStr", p.pendientesStr)
            pObj.put("customTotalProducedStr", p.customTotalProducedStr)
            prodArray.put(pObj)
        }
        obj.put("produccion", prodArray)

        val mercArray = JSONArray()
        draft.mercaderiaDrafts.forEach { m ->
            val mObj = JSONObject()
            mObj.put("mercaderiaId", m.mercaderiaId)
            mObj.put("existenciaInicialStr", m.existenciaInicialStr)
            mObj.put("entradasStr", m.entradasStr)
            mObj.put("existenciaFinalStr", m.existenciaFinalStr)
            mObj.put("defectuosoStr", m.defectuosoStr)
            mObj.put("consumoStr", m.consumoStr)
            mObj.put("regaliaStr", m.regaliaStr)
            mObj.put("customPriceStr", m.customPriceStr)
            mercArray.put(mObj)
        }
        obj.put("mercaderia", mercArray)

        val agArray = JSONArray()
        draft.agregadoDrafts.forEach { a ->
            val aObj = JSONObject()
            aObj.put("materiaPrimaId", a.materiaPrimaId)
            aObj.put("existenciaInicialStr", a.existenciaInicialStr)
            aObj.put("entradasStr", a.entradasStr)
            aObj.put("existenciaFinalStr", a.existenciaFinalStr)
            aObj.put("mermaStr", a.mermaStr)
            agArray.put(aObj)
        }
        obj.put("agregados", agArray)

        val extArray = JSONArray()
        draft.extracciones.forEach { e ->
            val eObj = JSONObject()
            eObj.put("montoStr", e.montoStr)
            eObj.put("descripcion", e.descripcion)
            extArray.put(eObj)
        }
        obj.put("extracciones", extArray)

        obj.put("efectivoRealStr", draft.efectivoRealStr)
        obj.put("notasCuadre", draft.notasCuadre)
        obj.put("otrasTransferenciasStr", draft.otrasTransferenciasStr)

        prefs.edit().putString("$KEY_PREFIX${draft.jornadaId}", obj.toString()).apply()
    }

    fun clearDraft(context: Context, jornadaId: Long) {
        if (jornadaId <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PREFIX$jornadaId").apply()
    }
}
