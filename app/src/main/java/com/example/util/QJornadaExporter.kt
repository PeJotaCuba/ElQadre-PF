package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.model.*
import com.example.ui.viewmodel.MainUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QJornadaExporter {

    fun extractUnidadesPendientesFromObservation(obs: String): Double {
        if (obs.isBlank()) return 0.0
        val key = when {
            obs.contains("Pendientes:") -> "Pendientes:"
            obs.contains("Pendientes_Convertidos:") -> "Pendientes_Convertidos:"
            else -> return 0.0
        }
        val after = obs.substringAfter(key).trim()
        val firstPart = after.substringBefore("|").substringBefore("[").trim()
        val match = """^([\d.,]+)""".toRegex().find(firstPart) ?: return 0.0
        return match.value.replace(',', '.').toDoubleOrNull() ?: 0.0
    }

    data class TandaItemSummary(
        val tandaId: Long,
        val productName: String,
        val producedQty: Double,
        val soldQty: Double,
        val remainingQty: Double,
        val salePrice: Double,
        val unitCost: Double,
        val totalBatchCost: Double,
        val soldCost: Double,
        val realRevenue: Double,
        val netResult: Double
    )

    data class MovimientoItemSummary(
        val movimientoId: Long,
        val productName: String,
        val salidaQty: Double,
        val soldQty: Double,
        val remainingQty: Double,
        val salePrice: Double,
        val acquisitionCost: Double,
        val soldCost: Double,
        val realRevenue: Double,
        val netResult: Double
    )

    data class GastoItemSummary(
        val name: String,
        val amount: Double,
        val period: String,
        val periodDays: Int,
        val dailyCost: Double,
        val scope: String
    )

    data class InversionItemSummary(
        val name: String,
        val amount: Double,
        val usefulLifeUnit: String,
        val usefulLifeValue: Double,
        val dailyDepreciation: Double,
        val scope: String
    )

    data class JornadaCalculationResult(
        val jornadaId: Long,
        val openedAt: Long,
        val closedAt: Long,
        val openedBy: String,
        val closedBy: String,
        val deviceId: String,
        val initialCash: Double,
        val finalCash: Double,
        val expectedCash: Double,
        val cashDifference: Double,
        val notes: String,
        val hasProduccion: Boolean,
        val hasMercancias: Boolean,
        val modulosUtilizados: String,

        // Produccion
        val produccionTandas: List<TandaItemSummary>,
        val produccionProducidoTotal: Double,
        val produccionVendidoTotal: Double,
        val produccionRestanteTotal: Double,
        val produccionIngresosReales: Double,
        val produccionCostosReales: Double,
        val produccionGastos: Double,
        val produccionInversiones: Double,
        val resultadoProduccion: Double,

        // Mercaderias
        val mercaderiasMovimientos: List<MovimientoItemSummary>,
        val mercaderiasSalidasTotal: Double,
        val mercaderiasVendidoTotal: Double,
        val mercaderiasRestanteTotal: Double,
        val mercaderiasIngresosReales: Double,
        val mercaderiasCostosReales: Double,
        val mercaderiasGastos: Double,
        val mercaderiasInversiones: Double,
        val resultadoMercaderias: Double,

        // Gastos e Inversiones
        val gastosGeneralesList: List<GastoItemSummary>,
        val inversionesList: List<InversionItemSummary>,
        val gastosGeneralesNoProrrateados: Double,
        val inversionesGeneralesNoProrrateadas: Double,

        // Totales Consolidado
        val totalIngresos: Double,
        val totalCostos: Double,
        val totalGastos: Double,
        val totalInversiones: Double,
        val resultadoOperativoTotal: Double,
        val utilidadDelDia: Double,

        // Insumos y Materias Primas para sincronización
        val materiasPrimas: List<MateriaPrima> = emptyList(),
        // Mercaderías y Productos para sincronización en Q Admin.json
        val mercaderias: List<Mercaderia> = emptyList(),
        val products: List<Product> = emptyList()
    )

    fun calculateJornadaEconomics(
        jornada: Jornada,
        allTandas: List<Tanda>,
        allMovimientos: List<MovimientoMercaderia>,
        allProducts: List<Product>,
        allMercaderias: List<Mercaderia>,
        allGastos: List<GastoGeneral>,
        allInversiones: List<Inversion>,
        currentUser: User?,
        allMateriasPrimas: List<MateriaPrima> = emptyList(),
        finalCashInput: Double? = null,
        closedByInput: String? = null,
        notesInput: String? = null
    ): JornadaCalculationResult {
        val hasProduccion = currentUser?.permisoProduccion ?: true
        val hasMercancias = currentUser?.permisoMercancias ?: true

        val modulosUtilizados = when {
            hasProduccion && hasMercancias -> "PRODUCCION_Y_MERCADERIAS"
            hasProduccion -> "SOLO_PRODUCCION"
            hasMercancias -> "SOLO_MERCADERIAS"
            else -> "NINGUNO"
        }

        val prodMap = allProducts.associateBy { it.id }
        val mercMap = allMercaderias.associateBy { it.id }

        // Filter tandas of this jornada (or period)
        val jorTandas = allTandas.filter { tanda ->
            tanda.date >= jornada.openedAt && (jornada.closedAt == null || tanda.date <= jornada.closedAt)
        }

        val tandasSummary = if (hasProduccion) {
            jorTandas.map { t ->
                val prod = prodMap[t.productId]
                val effectiveSalePrice = if (prod != null && prod.price > 0.0) prod.price else t.salePrice
                val producedQty = if (t.actualYield > 0.0) t.actualYield else (if (t.estimatedYield > 0.0) t.estimatedYield else 1.0)
                val unitCost = if (producedQty > 0.0) t.totalBatchCost / producedQty else t.realUnitCost
                val pendingFromObs = extractUnidadesPendientesFromObservation(t.observation)
                val soldQty = if (t.quantitySold > 0.0) {
                    t.quantitySold
                } else if (pendingFromObs > 0.0) {
                    (producedQty - pendingFromObs).coerceAtLeast(0.0)
                } else {
                    t.quantitySold
                }
                val remainingQty = if (t.quantitySold > 0.0) {
                    (producedQty - soldQty).coerceAtLeast(0.0)
                } else if (pendingFromObs > 0.0) {
                    pendingFromObs
                } else {
                    (producedQty - soldQty).coerceAtLeast(0.0)
                }
                val realRev = soldQty * effectiveSalePrice
                val soldCost = soldQty * unitCost
                val net = realRev - soldCost
                TandaItemSummary(
                    tandaId = t.id,
                    productName = t.productName,
                    producedQty = producedQty,
                    soldQty = soldQty,
                    remainingQty = remainingQty,
                    salePrice = effectiveSalePrice,
                    unitCost = unitCost,
                    totalBatchCost = t.totalBatchCost,
                    soldCost = soldCost,
                    realRevenue = realRev,
                    netResult = net
                )
            }
        } else emptyList()

        val produccionProducidoTotal = tandasSummary.sumOf { it.producedQty }
        val produccionVendidoTotal = tandasSummary.sumOf { it.soldQty }
        val produccionRestanteTotal = tandasSummary.sumOf { it.remainingQty }
        val produccionIngresosReales = tandasSummary.sumOf { it.realRevenue }
        val produccionCostosReales = tandasSummary.sumOf { it.soldCost }

        // Filter movimientos de mercaderías (SALIDA o VENTA) of this jornada (or period)
        val jorMovs = allMovimientos.filter { m ->
            (m.type.uppercase() == "SALIDA" || m.type.uppercase() == "VENTA") &&
            (m.jornadaId == jornada.id || (m.jornadaId == 0L && m.date >= jornada.openedAt && (jornada.closedAt == null || m.date <= jornada.closedAt)))
        }

        val movsSummary = if (hasMercancias) {
            jorMovs.map { m ->
                val merc = mercMap[m.mercaderiaId]
                val prod = merc?.productId?.let { prodMap[it] }
                val effectiveSalePrice = if (prod != null && prod.price > 0.0) prod.price else m.salePrice
                val acqCost = if (merc != null && merc.acquisitionCost > 0.0) merc.acquisitionCost else m.acquisitionCost
                val isVenta = m.type.uppercase() == "VENTA"
                val salidaQty = if (isVenta) 0.0 else m.quantity
                val soldQty = if (isVenta) {
                    if (m.quantitySold > 0.0) m.quantitySold else m.quantity
                } else {
                    m.quantitySold
                }
                val remainingQty = (salidaQty - soldQty).coerceAtLeast(0.0)
                val realRev = if (isVenta) {
                    soldQty * effectiveSalePrice
                } else {
                    if (m.realRevenue > 0.0) m.realRevenue else (soldQty * effectiveSalePrice)
                }
                val soldCost = soldQty * acqCost
                val net = realRev - soldCost
                MovimientoItemSummary(
                    movimientoId = m.id,
                    productName = prod?.name ?: "Mercadería #${m.mercaderiaId}",
                    salidaQty = salidaQty,
                    soldQty = soldQty,
                    remainingQty = remainingQty,
                    salePrice = effectiveSalePrice,
                    acquisitionCost = acqCost,
                    soldCost = soldCost,
                    realRevenue = realRev,
                    netResult = net
                )
            }
        } else emptyList()

        val mercaderiasSalidasTotal = movsSummary.sumOf { it.salidaQty }
        val mercaderiasVendidoTotal = movsSummary.sumOf { it.soldQty }
        val mercaderiasRestanteTotal = movsSummary.sumOf { it.remainingQty }
        val mercaderiasIngresosReales = movsSummary.sumOf { it.realRevenue }
        val mercaderiasCostosReales = movsSummary.sumOf { it.soldCost }

        // Gastos
        val activeGastos = allGastos.filter { it.isActive }.map { g ->
            GastoItemSummary(
                name = g.name,
                amount = g.amount,
                period = g.period,
                periodDays = g.periodDays,
                dailyCost = g.dailyCost(),
                scope = g.scope
            )
        }

        val produccionGastos = if (hasProduccion) {
            activeGastos.filter { it.scope == "PRODUCCION" || (!hasMercancias && it.scope != "MERCADERIAS") }.sumOf { it.dailyCost }
        } else 0.0

        val mercaderiasGastos = if (hasMercancias) {
            activeGastos.filter { it.scope == "MERCADERIAS" || (!hasProduccion && it.scope != "PRODUCCION") }.sumOf { it.dailyCost }
        } else 0.0

        val gastosGeneralesNoProrrateados = if (hasProduccion && hasMercancias) {
            activeGastos.filter { it.scope != "PRODUCCION" && it.scope != "MERCADERIAS" }.sumOf { it.dailyCost }
        } else 0.0

        // Inversiones
        val activeInversiones = allInversiones.map { inv ->
            InversionItemSummary(
                name = inv.name,
                amount = inv.amount,
                usefulLifeUnit = inv.usefulLifeUnit,
                usefulLifeValue = inv.usefulLife,
                dailyDepreciation = inv.dailyDepreciation(),
                scope = inv.scope
            )
        }

        val produccionInversiones = if (hasProduccion) {
            activeInversiones.filter { it.scope == "PRODUCCION" || (!hasMercancias && it.scope != "MERCADERIAS") }.sumOf { it.dailyDepreciation }
        } else 0.0

        val mercaderiasInversiones = if (hasMercancias) {
            activeInversiones.filter { it.scope == "MERCADERIAS" || (!hasProduccion && it.scope != "PRODUCCION") }.sumOf { it.dailyDepreciation }
        } else 0.0

        val inversionesGeneralesNoProrrateadas = if (hasProduccion && hasMercancias) {
            activeInversiones.filter { it.scope != "PRODUCCION" && it.scope != "MERCADERIAS" }.sumOf { it.dailyDepreciation }
        } else 0.0

        // Resultados por modulo
        val resultadoProduccion = produccionIngresosReales - produccionCostosReales - produccionGastos - produccionInversiones
        val resultadoMercaderias = mercaderiasIngresosReales - mercaderiasCostosReales - mercaderiasGastos - mercaderiasInversiones

        // Totales Consolidado
        val totalIngresos = when {
            hasProduccion && hasMercancias -> produccionIngresosReales + mercaderiasIngresosReales
            hasProduccion -> produccionIngresosReales
            hasMercancias -> mercaderiasIngresosReales
            else -> 0.0
        }

        val totalCostos = when {
            hasProduccion && hasMercancias -> produccionCostosReales + mercaderiasCostosReales
            hasProduccion -> produccionCostosReales
            hasMercancias -> mercaderiasCostosReales
            else -> 0.0
        }

        val totalGastos = produccionGastos + mercaderiasGastos + gastosGeneralesNoProrrateados
        val totalInversiones = produccionInversiones + mercaderiasInversiones + inversionesGeneralesNoProrrateadas

        val resultadoOperativoTotal = when {
            hasProduccion && hasMercancias -> resultadoProduccion + resultadoMercaderias
            hasProduccion -> resultadoProduccion
            hasMercancias -> resultadoMercaderias
            else -> 0.0
        }

        val utilidadDelDia = totalIngresos - totalCostos - totalGastos - totalInversiones

        // Cuadre
        val finalCash = finalCashInput ?: (if (!jornada.isOpen) jornada.finalCash else 0.0)
        val expectedCash = jornada.initialCash + totalIngresos - jornada.totalExpenses
        val cashDiff = finalCash - expectedCash

        return JornadaCalculationResult(
            jornadaId = jornada.id,
            openedAt = jornada.openedAt,
            closedAt = jornada.closedAt ?: System.currentTimeMillis(),
            openedBy = jornada.openedBy,
            closedBy = closedByInput ?: jornada.closedBy ?: "dueno",
            deviceId = jornada.deviceId.ifBlank { "DISPOSITIVO-LOCAL" },
            initialCash = jornada.initialCash,
            finalCash = finalCash,
            expectedCash = expectedCash,
            cashDifference = cashDiff,
            notes = notesInput ?: jornada.notes,
            hasProduccion = hasProduccion,
            hasMercancias = hasMercancias,
            modulosUtilizados = modulosUtilizados,

            produccionTandas = tandasSummary,
            produccionProducidoTotal = produccionProducidoTotal,
            produccionVendidoTotal = produccionVendidoTotal,
            produccionRestanteTotal = produccionRestanteTotal,
            produccionIngresosReales = produccionIngresosReales,
            produccionCostosReales = produccionCostosReales,
            produccionGastos = produccionGastos,
            produccionInversiones = produccionInversiones,
            resultadoProduccion = resultadoProduccion,

            mercaderiasMovimientos = movsSummary,
            mercaderiasSalidasTotal = mercaderiasSalidasTotal,
            mercaderiasVendidoTotal = mercaderiasVendidoTotal,
            mercaderiasRestanteTotal = mercaderiasRestanteTotal,
            mercaderiasIngresosReales = mercaderiasIngresosReales,
            mercaderiasCostosReales = mercaderiasCostosReales,
            mercaderiasGastos = mercaderiasGastos,
            mercaderiasInversiones = mercaderiasInversiones,
            resultadoMercaderias = resultadoMercaderias,

            gastosGeneralesList = activeGastos,
            inversionesList = activeInversiones,
            gastosGeneralesNoProrrateados = gastosGeneralesNoProrrateados,
            inversionesGeneralesNoProrrateadas = inversionesGeneralesNoProrrateadas,

            totalIngresos = totalIngresos,
            totalCostos = totalCostos,
            totalGastos = totalGastos,
            totalInversiones = totalInversiones,
            resultadoOperativoTotal = resultadoOperativoTotal,
            utilidadDelDia = utilidadDelDia,
            materiasPrimas = allMateriasPrimas,
            mercaderias = allMercaderias,
            products = allProducts
        )
    }

    fun generateQJornadaJsonString(
        calc: JornadaCalculationResult,
        nombreNegocio: String = "El Qadre POS",
        codigoNegocio: String = "NEG-000001",
        customBusinessCode: String? = null
    ): String {
        val root = JSONObject()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val fechaCierreStr = dateTimeFormat.format(Date(calc.closedAt))
        val fechaStr = dateFormat.format(Date(calc.openedAt))
        val horaAperturaStr = timeFormat.format(Date(calc.openedAt))
        val horaCierreStr = timeFormat.format(Date(calc.closedAt))

        val formattedCode = if (!customBusinessCode.isNullOrBlank()) {
            com.example.licensing.BusinessCodeHelper.formatCode(customBusinessCode)
        } else {
            com.example.licensing.BusinessCodeHelper.formatCode(codigoNegocio)
        }
        val fileName = "Q_${formattedCode}admin.json"

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val isoDateTimeStr = isoFormat.format(Date(calc.closedAt))

        root.put("archivo", fileName)
        root.put("identificador_archivo", "Q_ADMIN")
        root.put("codigoNegocio", formattedCode)
        root.put("urlAdmin", com.example.licensing.BusinessCodeHelper.buildUrlAdmin(formattedCode))
        root.put("timestamp_ms", calc.closedAt)
        root.put("version", calc.closedAt.toString())
        root.put("fechaHoraCierre", fechaCierreStr)
        root.put("fecha_generacion", isoDateTimeStr)
        root.put("fecha_hora_generacion", isoDateTimeStr)

        // Negocio
        val negObj = JSONObject()
        negObj.put("nombre", nombreNegocio)
        negObj.put("codigo", formattedCode)
        negObj.put("codigoNegocio", "NEG-$formattedCode")
        root.put("negocio", negObj)

        // Jornada Metadata
        val jorObj = JSONObject()
        jorObj.put("id", calc.jornadaId)
        jorObj.put("fecha", fechaStr)
        jorObj.put("horaApertura", horaAperturaStr)
        jorObj.put("horaCierre", horaCierreStr)
        jorObj.put("usuarioApertura", calc.openedBy)
        jorObj.put("usuarioCierre", calc.closedBy)
        jorObj.put("dispositivo", calc.deviceId)
        jorObj.put("estado", "CERRADA")
        jorObj.put("fondoInicial", calc.initialCash)
        jorObj.put("efectivoFinal", calc.finalCash)
        jorObj.put("modulosUtilizados", calc.modulosUtilizados)
        jorObj.put("notas", calc.notes)
        root.put("jornada", jorObj)

        // Resultado Economico Consolidado
        val resObj = JSONObject()
        resObj.put("ingresosTotales", calc.totalIngresos)
        resObj.put("costosTotales", calc.totalCostos)
        resObj.put("gastosTotales", calc.totalGastos)
        resObj.put("inversionesTotales", calc.totalInversiones)
        resObj.put("resultadoOperativo", calc.resultadoOperativoTotal)
        resObj.put("utilidadDelDia", calc.utilidadDelDia)
        root.put("resultadoEconomico", resObj)

        // Produccion
        val prodObj = JSONObject()
        prodObj.put("habilitado", calc.hasProduccion)
        prodObj.put("tandasRealizadas", calc.produccionTandas.size)
        prodObj.put("totalProducido", calc.produccionProducidoTotal)
        prodObj.put("totalVendidoReal", calc.produccionVendidoTotal)
        prodObj.put("totalRestante", calc.produccionRestanteTotal)
        prodObj.put("ingresosReales", calc.produccionIngresosReales)
        prodObj.put("costos", calc.produccionCostosReales)
        prodObj.put("gastos", calc.produccionGastos)
        prodObj.put("inversiones", calc.produccionInversiones)
        prodObj.put("resultadoProduccion", calc.resultadoProduccion)

        val tandasArray = JSONArray()
        for (t in calc.produccionTandas) {
            val tObj = JSONObject()
            tObj.put("tandaId", t.tandaId)
            tObj.put("producto", t.productName)
            tObj.put("producidoReal", t.producedQty)
            tObj.put("vendidoReal", t.soldQty)
            tObj.put("restante", t.remainingQty)
            tObj.put("precioVenta", t.salePrice)
            tObj.put("costoUnitario", t.unitCost)
            tObj.put("costoLoteTotal", t.totalBatchCost)
            tObj.put("costoVendido", t.soldCost)
            tObj.put("ingresoReal", t.realRevenue)
            tObj.put("resultadoLote", t.netResult)
            tandasArray.put(tObj)
        }
        prodObj.put("detallesTandas", tandasArray)

        // Insumos y Materias Primas para Q Admin.json
        val insumosArray = JSONArray()
        for (mp in calc.materiasPrimas) {
            val mpObj = JSONObject()
            mpObj.put("id", mp.id)
            mpObj.put("nombre", mp.name)
            mpObj.put("unidad", mp.unit)
            mpObj.put("costoUnitario", mp.unitCost)
            mpObj.put("stock", mp.stock)
            mpObj.put("stockInicial", mp.initialStock)
            mpObj.put("precioCompra", mp.purchasePrice)
            mpObj.put("unidadCompra", mp.purchaseUnit)
            mpObj.put("activo", mp.isActive)
            insumosArray.put(mpObj)
        }
        prodObj.put("insumos", insumosArray)
        root.put("materiasPrimas", insumosArray)
        root.put("insumos", insumosArray)

        root.put("produccion", prodObj)

        // Mercaderias
        val mercObj = JSONObject()
        mercObj.put("habilitado", calc.hasMercancias)
        mercObj.put("salidasRegistradas", calc.mercaderiasMovimientos.size)
        mercObj.put("totalSalidas", calc.mercaderiasSalidasTotal)
        mercObj.put("totalVendidoReal", calc.mercaderiasVendidoTotal)
        mercObj.put("totalRestante", calc.mercaderiasRestanteTotal)
        mercObj.put("ingresosReales", calc.mercaderiasIngresosReales)
        mercObj.put("costos", calc.mercaderiasCostosReales)
        mercObj.put("gastos", calc.mercaderiasGastos)
        mercObj.put("inversiones", calc.mercaderiasInversiones)
        mercObj.put("resultadoMercaderias", calc.resultadoMercaderias)

        val movsArray = JSONArray()
        for (m in calc.mercaderiasMovimientos) {
            val mObj = JSONObject()
            mObj.put("movimientoId", m.movimientoId)
            mObj.put("producto", m.productName)
            mObj.put("salidaRetirada", m.salidaQty)
            mObj.put("vendidoReal", m.soldQty)
            mObj.put("restante", m.remainingQty)
            mObj.put("precioVenta", m.salePrice)
            mObj.put("costoAdquisicion", m.acquisitionCost)
            mObj.put("costoVendido", m.soldCost)
            mObj.put("ingresoReal", m.realRevenue)
            mObj.put("resultadoMovimiento", m.netResult)
            movsArray.put(mObj)
        }
        mercObj.put("detallesMovimientos", movsArray)

        // Mercancías / Productos de Mercadería para incorporación en Q Admin.json
        val mercanciasArray = JSONArray()
        for (merc in calc.mercaderias) {
            val prod = calc.products.find { it.id == merc.productId }
            val mObj = JSONObject()
            mObj.put("id", merc.id)
            mObj.put("productId", merc.productId)
            mObj.put("nombre", prod?.name ?: "Mercadería #${merc.id}")
            mObj.put("name", prod?.name ?: "Mercadería #${merc.id}")
            mObj.put("codigo", prod?.code ?: "")
            mObj.put("code", prod?.code ?: "")
            mObj.put("categoria", prod?.category ?: "Bebidas")
            mObj.put("category", prod?.category ?: "Bebidas")
            mObj.put("unidad_medida", merc.unitOfMeasure)
            mObj.put("unitOfMeasure", merc.unitOfMeasure)
            mObj.put("purchaseMode", merc.purchaseMode)
            mObj.put("purchasePrice", merc.purchasePrice)
            mObj.put("unitsPerLot", merc.unitsPerLot)
            mObj.put("costo_adquisicion", merc.acquisitionCost)
            mObj.put("acquisitionCost", merc.acquisitionCost)
            mObj.put("gastosDirectos", merc.directExpenses)
            mObj.put("directExpenses", merc.directExpenses)
            mObj.put("precio_venta", prod?.price ?: 0.0)
            mObj.put("salePrice", prod?.price ?: 0.0)
            mObj.put("stock_inicial", merc.initialStock)
            mObj.put("initialStock", merc.initialStock)
            mObj.put("stock_actual", prod?.stock ?: merc.initialStock)
            mObj.put("currentStock", prod?.stock ?: merc.initialStock)
            mObj.put("activo", merc.isActive)
            mObj.put("isActive", merc.isActive)
            mercanciasArray.put(mObj)
        }
        mercObj.put("mercancias", mercanciasArray)
        mercObj.put("productos", mercanciasArray)
        root.put("mercancias", mercanciasArray)
        root.put("mercaderiasList", mercanciasArray)

        root.put("mercaderias", mercObj)

        // Cuadre
        val cuadreObj = JSONObject()
        cuadreObj.put("fondoInicial", calc.initialCash)
        cuadreObj.put("ventasTotales", calc.totalIngresos)
        cuadreObj.put("efectivoEsperado", calc.expectedCash)
        cuadreObj.put("contadoFisico", calc.finalCash)
        cuadreObj.put("diferencia", calc.cashDifference)
        val clasificacion = when {
            calc.cashDifference == 0.0 -> "EXACTO"
            calc.cashDifference > 0.0 -> "SOBRANTE"
            else -> "FALTANTE"
        }
        cuadreObj.put("clasificacion", clasificacion)
        root.put("cuadre", cuadreObj)

        // Gastos Corrientes
        val gastosArr = JSONArray()
        for (g in calc.gastosGeneralesList) {
            val gObj = JSONObject()
            gObj.put("nombre", g.name)
            gObj.put("name", g.name)
            gObj.put("importe", g.amount)
            gObj.put("amount", g.amount)
            gObj.put("periodo", g.period)
            gObj.put("period", g.period)
            gObj.put("periodDays", g.periodDays)
            gObj.put("dailyCost", g.dailyCost)
            gObj.put("scope", g.scope)
            gastosArr.put(gObj)
        }
        root.put("gastos_corrientes", gastosArr)

        // Inversiones
        val invArr = JSONArray()
        for (inv in calc.inversionesList) {
            val invObj = JSONObject()
            invObj.put("name", inv.name)
            invObj.put("amount", inv.amount)
            invObj.put("usefulLifeUnit", inv.usefulLifeUnit)
            invObj.put("usefulLifeValue", inv.usefulLifeValue)
            invObj.put("usefulLife", inv.usefulLifeValue)
            invObj.put("dailyDepreciation", inv.dailyDepreciation)
            invObj.put("scope", inv.scope)
            invArr.put(invObj)
        }
        root.put("inversiones", invArr)

        val datosObj = JSONObject()
        datosObj.put("produccion", prodObj)
        datosObj.put("mercaderias", mercObj)
        datosObj.put("inversiones", invArr)
        datosObj.put("gastos_corrientes", gastosArr)
        root.put("datos", datosObj)

        return root.toString(2)
    }

    suspend fun saveQJornadaToFile(
        context: Context,
        jsonString: String,
        jornadaId: Long
    ): File = withContext(Dispatchers.IO) {
        val dir = context.filesDir
        val mainFile = File(dir, "Q_jornada.json")
        mainFile.writeText(jsonString)

        val qAdminFile = File(dir, "Q Admin.json")
        qAdminFile.writeText(jsonString)

        val qAdminFileUnder = File(dir, "Q_Admin.json")
        qAdminFileUnder.writeText(jsonString)

        val archiveFile = File(dir, "Q_jornada_${jornadaId}.json")
        archiveFile.writeText(jsonString)

        mainFile
    }

    fun copyToClipboard(context: Context, jsonString: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Q_jornada.json", jsonString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Archivo Q_jornada.json copiado al portapapeles", Toast.LENGTH_LONG).show()
    }

    fun shareQJornadaJson(context: Context, jsonString: String, jornadaId: Long) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Q_jornada_${jornadaId}.json")
            putExtra(Intent.EXTRA_TEXT, jsonString)
        }
        val chooser = Intent.createChooser(intent, "Compartir Q_jornada.json")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun exportAndShareQAdminJson(context: Context, uiState: MainUiState): Boolean {
        return try {
            val jor = uiState.activeJornada ?: uiState.allJornadas.firstOrNull() ?: Jornada(
                openedAt = System.currentTimeMillis(),
                isOpen = true,
                openedBy = uiState.currentUser?.username ?: "dueno"
            )
            val calcResult = calculateJornadaEconomics(
                jornada = jor,
                allTandas = uiState.tandas,
                allMovimientos = uiState.movimientosMercaderia,
                allProducts = uiState.products,
                allMercaderias = uiState.mercaderias,
                allGastos = uiState.gastosGenerales,
                allInversiones = uiState.inversiones,
                currentUser = uiState.currentUser,
                allMateriasPrimas = uiState.materiasPrimas
            )
            val configNegocio = uiState.businessConfig
            val nombreNegocio = configNegocio?.nombreNegocio ?: uiState.businessName
            val bizCode = com.example.licensing.BusinessCodeHelper.resolveBusinessCode(context, configNegocio, uiState.generalConfig)
            val codigoNegocio = "NEG-$bizCode"
            val fileName = "Q_${bizCode}admin.json"

            val jsonString = generateQJornadaJsonString(
                calc = calcResult,
                nombreNegocio = nombreNegocio,
                codigoNegocio = codigoNegocio,
                customBusinessCode = bizCode
            )
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, "Adjunto archivo $fileName generado desde el módulo de Dueño.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Generar y Adjuntar $fileName")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            val fallbackCode = com.example.licensing.BusinessCodeHelper.resolveBusinessCode(context, uiState.businessConfig, uiState.generalConfig)
            Toast.makeText(context, "Error al generar Q_${fallbackCode}admin.json: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
