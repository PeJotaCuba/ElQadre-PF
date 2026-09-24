package com.example.util

import android.content.Context
import com.example.data.local.model.Jornada
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.MovimientoMateriaPrima
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor del Repositorio Histórico (ARCHIVO) de INFORME INSUMOS
 * Almacena y recupera los datos congelados exactos al momento del cierre de cada jornada.
 * No modifica ni duplica inventarios reales.
 */
object InformeInsumosArchiveManager {

    data class MovimientoArchiveDetail(
        val date: Long,
        val type: String,
        val quantity: Double,
        val notes: String
    )

    data class InsumoArchiveItem(
        val materiaPrimaId: Long,
        val name: String,
        val unit: String,
        val initialStock: Double,
        val entradasQty: Double,
        val finalStock: Double,
        val unitCost: Double,
        val importe: Double,
        val priceBreakdown: List<String> = emptyList(),
        val entradasDetalles: List<MovimientoArchiveDetail> = emptyList(),
        val consumosDetalles: List<MovimientoArchiveDetail> = emptyList()
    )

    data class JornadaInsumosArchive(
        val jornadaId: Long,
        val openedAt: Long,
        val closedAt: Long,
        val closedBy: String,
        val businessName: String,
        val totalImporte: Double,
        val totalInsumos: Int,
        val totalEntradasCount: Int,
        val items: List<InsumoArchiveItem>,
        val createdAt: Long = System.currentTimeMillis()
    )

    private fun getArchiveDir(context: Context): File {
        val dir = File(context.filesDir, "informe_insumos_archives")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getArchiveFile(context: Context, jornadaId: Long): File {
        return File(getArchiveDir(context), "archive_jornada_$jornadaId.json")
    }

    /**
     * Guarda el snapshot histórico en disco para una jornada cerrada.
     */
    fun saveArchive(context: Context, archive: JornadaInsumosArchive) {
        try {
            val json = JSONObject().apply {
                put("jornadaId", archive.jornadaId)
                put("openedAt", archive.openedAt)
                put("closedAt", archive.closedAt)
                put("closedBy", archive.closedBy)
                put("businessName", archive.businessName)
                put("totalImporte", archive.totalImporte)
                put("totalInsumos", archive.totalInsumos)
                put("totalEntradasCount", archive.totalEntradasCount)
                put("createdAt", archive.createdAt)

                val itemsArray = JSONArray()
                archive.items.forEach { item ->
                    val itemObj = JSONObject().apply {
                        put("materiaPrimaId", item.materiaPrimaId)
                        put("name", item.name)
                        put("unit", item.unit)
                        put("initialStock", item.initialStock)
                        put("entradasQty", item.entradasQty)
                        put("finalStock", item.finalStock)
                        put("unitCost", item.unitCost)
                        put("importe", item.importe)

                        val pbArray = JSONArray()
                        item.priceBreakdown.forEach { pbArray.put(it) }
                        put("priceBreakdown", pbArray)

                        val entArray = JSONArray()
                        item.entradasDetalles.forEach { e ->
                            entArray.put(JSONObject().apply {
                                put("date", e.date)
                                put("type", e.type)
                                put("quantity", e.quantity)
                                put("notes", e.notes)
                            })
                        }
                        put("entradasDetalles", entArray)

                        val conArray = JSONArray()
                        item.consumosDetalles.forEach { c ->
                            conArray.put(JSONObject().apply {
                                put("date", c.date)
                                put("type", c.type)
                                put("quantity", c.quantity)
                                put("notes", c.notes)
                            })
                        }
                        put("consumosDetalles", conArray)
                    }
                    itemsArray.put(itemObj)
                }
                put("items", itemsArray)
            }

            val file = getArchiveFile(context, archive.jornadaId)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Obtiene el archivo congelado si existe en almacenamiento.
     */
    fun getArchive(context: Context, jornadaId: Long): JornadaInsumosArchive? {
        val file = getArchiveFile(context, jornadaId)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val jId = json.getLong("jornadaId")
            val openedAt = json.getLong("openedAt")
            val closedAt = json.optLong("closedAt", openedAt)
            val closedBy = json.optString("closedBy", "Administrador")
            val businessName = json.optString("businessName", "ElQadre")
            val totalImporte = json.optDouble("totalImporte", 0.0)
            val totalInsumos = json.optInt("totalInsumos", 0)
            val totalEntradasCount = json.optInt("totalEntradasCount", 0)
            val createdAt = json.optLong("createdAt", System.currentTimeMillis())

            val itemsArray = json.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<InsumoArchiveItem>()

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val pbArray = itemObj.optJSONArray("priceBreakdown") ?: JSONArray()
                val pbList = mutableListOf<String>()
                for (j in 0 until pbArray.length()) {
                    pbList.add(pbArray.getString(j))
                }

                val entArray = itemObj.optJSONArray("entradasDetalles") ?: JSONArray()
                val entList = mutableListOf<MovimientoArchiveDetail>()
                for (j in 0 until entArray.length()) {
                    val eo = entArray.getJSONObject(j)
                    entList.add(
                        MovimientoArchiveDetail(
                            date = eo.optLong("date"),
                            type = eo.optString("type", "ENTRADA"),
                            quantity = eo.optDouble("quantity", 0.0),
                            notes = eo.optString("notes", "")
                        )
                    )
                }

                val conArray = itemObj.optJSONArray("consumosDetalles") ?: JSONArray()
                val conList = mutableListOf<MovimientoArchiveDetail>()
                for (j in 0 until conArray.length()) {
                    val co = conArray.getJSONObject(j)
                    conList.add(
                        MovimientoArchiveDetail(
                            date = co.optLong("date"),
                            type = co.optString("type", "SALIDA"),
                            quantity = co.optDouble("quantity", 0.0),
                            notes = co.optString("notes", "")
                        )
                    )
                }

                items.add(
                    InsumoArchiveItem(
                        materiaPrimaId = itemObj.optLong("materiaPrimaId"),
                        name = itemObj.optString("name", "Insumo"),
                        unit = itemObj.optString("unit", "U"),
                        initialStock = itemObj.optDouble("initialStock", 0.0),
                        entradasQty = itemObj.optDouble("entradasQty", 0.0),
                        finalStock = itemObj.optDouble("finalStock", 0.0),
                        unitCost = itemObj.optDouble("unitCost", 0.0),
                        importe = itemObj.optDouble("importe", 0.0),
                        priceBreakdown = pbList,
                        entradasDetalles = entList,
                        consumosDetalles = conList
                    )
                )
            }

            JornadaInsumosArchive(
                jornadaId = jId,
                openedAt = openedAt,
                closedAt = closedAt,
                closedBy = closedBy,
                businessName = businessName,
                totalImporte = totalImporte,
                totalInsumos = totalInsumos,
                totalEntradasCount = totalEntradasCount,
                items = items,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Construye y congela el informe histórico para una jornada a partir de los datos registrados.
     */
    fun buildArchiveFromHistoricalData(
        jornada: Jornada,
        allMaterias: List<MateriaPrima>,
        allMovimientos: List<MovimientoMateriaPrima>,
        businessName: String = "ElQadre",
        closedBy: String = "Administrador"
    ): JornadaInsumosArchive {
        val closedAt = jornada.closedAt ?: System.currentTimeMillis()
        val movimientosJornada = allMovimientos.filter { it.date in jornada.openedAt..closedAt }
        val activeMaterias = allMaterias.filter { it.isActive }

        val items = activeMaterias.map { mp ->
            val movsInsumo = movimientosJornada.filter { it.materiaPrimaId == mp.id }
            val entradas = movsInsumo.filter { it.type == "ENTRADA" }
            val consumos = movsInsumo.filter { it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA") }

            val entradasQty = entradas.sumOf { it.quantity }
            val consumosQty = consumos.sumOf { it.quantity }

            val inicioQty = mp.initialStock
            val finalQty = if (jornada.isOpen) mp.stock else (inicioQty + entradasQty - consumosQty).coerceAtLeast(0.0)
            val importe = finalQty * mp.unitCost

            val priceBreakdown = mutableListOf<String>()
            val entradasDetalles = mutableListOf<MovimientoArchiveDetail>()
            val consumosDetalles = mutableListOf<MovimientoArchiveDetail>()

            entradas.forEachIndexed { i, e ->
                entradasDetalles.add(MovimientoArchiveDetail(e.date, e.type, e.quantity, e.notes))
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.date))
                var notePrice = ""
                val match = """importe\s*(?:de)?\s*\$?([0-9]+(?:\.[0-9]+)?)""".toRegex(RegexOption.IGNORE_CASE).find(e.notes)
                if (match != null) {
                    val imp = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (e.quantity > 0.0 && imp > 0.0) {
                        val unitP = imp / e.quantity
                        notePrice = " ($${"%.2f".format(unitP)}/u = $${"%.2f".format(imp)})"
                    }
                }
                priceBreakdown.add("Entrada #${i + 1} ($time): +${"%.2f".format(e.quantity)} ${mp.unit}$notePrice")
            }

            consumos.forEach { c ->
                consumosDetalles.add(MovimientoArchiveDetail(c.date, c.type, c.quantity, c.notes))
            }

            InsumoArchiveItem(
                materiaPrimaId = mp.id,
                name = mp.name,
                unit = mp.unit,
                initialStock = inicioQty,
                entradasQty = entradasQty,
                finalStock = finalQty,
                unitCost = mp.unitCost,
                importe = importe,
                priceBreakdown = priceBreakdown,
                entradasDetalles = entradasDetalles,
                consumosDetalles = consumosDetalles
            )
        }

        val totalImporte = items.sumOf { it.importe }
        val totalEntradasCount = movimientosJornada.count { it.type == "ENTRADA" }

        return JornadaInsumosArchive(
            jornadaId = jornada.id,
            openedAt = jornada.openedAt,
            closedAt = closedAt,
            closedBy = jornada.closedBy ?: closedBy,
            businessName = businessName,
            totalImporte = totalImporte,
            totalInsumos = items.size,
            totalEntradasCount = totalEntradasCount,
            items = items
        )
    }

    /**
     * Obtiene el archivo si está guardado, o lo construye y guarda si aún no existe.
     */
    fun getOrBuildArchive(
        context: Context,
        jornada: Jornada,
        allMaterias: List<MateriaPrima>,
        allMovimientos: List<MovimientoMateriaPrima>,
        businessName: String = "ElQadre"
    ): JornadaInsumosArchive {
        val existing = getArchive(context, jornada.id)
        if (existing != null) return existing

        val built = buildArchiveFromHistoricalData(
            jornada = jornada,
            allMaterias = allMaterias,
            allMovimientos = allMovimientos,
            businessName = businessName,
            closedBy = jornada.closedBy ?: "Administrador"
        )
        saveArchive(context, built)
        return built
    }
}
