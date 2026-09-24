package com.example.util

import android.content.Context
import com.example.data.local.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor del Repositorio Histórico (ARCHIVO) de CUADRO DE CAJA.
 * Almacena y recupera los datos congelados exactos al momento del cierre de cada jornada.
 * No recalcula ni altera los datos históricos con tandas o movimientos posteriores.
 */
object CuadreCajaArchiveManager {

    data class ArchivedProduccionItem(
        val productId: Long,
        val productName: String,
        val tandasCount: Int,
        val qtyPerTanda: Double,
        val totalProduced: Double,
        val unit: String,
        val defectuoso: Double,
        val consumo: Double,
        val regalia: Double,
        val pendientes: Double = 0.0,
        val vendible: Double,
        val price: Double,
        val ingresoEstimado: Double,
        val pagoCocinaUnitario: Double = 0.0,
        val cantidadCocineros: Int = 1
    )

    data class ArchivedMercaderiaItem(
        val mercaderiaId: Long,
        val productId: Long,
        val productName: String,
        val unit: String,
        val existenciaInicial: Double,
        val entradas: Double,
        val existenciaFinal: Double,
        val defectuoso: Double,
        val consumo: Double = 0.0,
        val regalia: Double = 0.0,
        val ventas: Double,
        val price: Double,
        val ingresoEstimado: Double,
        val isConfitura: Boolean = false
    )

    data class ArchivedAgregadoItem(
        val materiaPrimaId: Long,
        val name: String,
        val unit: String,
        val racionesEnviadas: Double,
        val racionesVendidas: Double,
        val racionesRegalia: Double,
        val racionesSobrantes: Double,
        val precioVenta: Double,
        val costoPorRacion: Double,
        val ingresoEstimado: Double
    )

    data class ArchivedCocinaPagoRow(
        val productName: String,
        val vendible: Double,
        val unit: String,
        val pagoUnitario: Double,
        val cantidadCocineros: Int,
        val totalPago: Double
    )

    data class ArchivedCajeroPagoInfo(
        val pagoProduccion: Double,
        val pagoMercaderias: Double,
        val totalPago: Double
    )

    data class ArchivedDependientePago(
        val id: Int,
        val name: String,
        val ventasProduccion: Double,
        val ventasBebidas: Double,
        val ventasTotales: Double,
        val montoPago: Double
    )

    data class JornadaCuadreCajaArchive(
        val jornadaId: Long,
        val openedAt: Long,
        val closedAt: Long,
        val closedBy: String,
        val businessName: String,
        val initialCash: Double,
        val ingresosProduccion: Double,
        val ingresosMercaderias: Double,
        val ingresosAgregados: Double = 0.0,
        val totalIngresos: Double,
        val mermasTotalValor: Double,
        val mermasTotalUnidades: Double = 0.0,
        val transferenciasMonto: Double,
        val transferenciasCount: Int = 0,
        val extracciones: Double,
        val extraccionesNotas: String = "",
        val efectivoEsperado: Double,
        val efectivoReal: Double,
        val diferencia: Double,
        val costoProduccion: Double = 0.0,
        val costoMercaderias: Double = 0.0,
        val costoAgregados: Double = 0.0,
        val costoTotal: Double = 0.0,
        val utilidadTeorica: Double = 0.0,
        val pagosConfirmados: Boolean = false,
        val totalPagosPersonal: Double = 0.0,
        val totalPagoCocina: Double = 0.0,
        val totalPagoCajero: Double = 0.0,
        val totalPagoDependientes: Double = 0.0,
        val dineroFinalEnCaja: Double = 0.0,
        val notas: String = "",
        val produccionItems: List<ArchivedProduccionItem> = emptyList(),
        val mercaderiaItems: List<ArchivedMercaderiaItem> = emptyList(),
        val agregadoItems: List<ArchivedAgregadoItem> = emptyList(),
        val cocinaPagoRows: List<ArchivedCocinaPagoRow> = emptyList(),
        val cajeroPagoInfo: ArchivedCajeroPagoInfo? = null,
        val dependientesRows: List<ArchivedDependientePago> = emptyList(),
        val createdAt: Long = System.currentTimeMillis()
    )

    private fun getArchiveDir(context: Context): File {
        val dir = File(context.filesDir, "cuadre_caja_archives")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getArchiveFile(context: Context, jornadaId: Long): File {
        return File(getArchiveDir(context), "archive_cuadre_jornada_$jornadaId.json")
    }

    /**
     * Guarda el snapshot histórico en disco para una jornada.
     */
    fun saveArchive(context: Context, archive: JornadaCuadreCajaArchive) {
        try {
            val json = JSONObject().apply {
                put("jornadaId", archive.jornadaId)
                put("openedAt", archive.openedAt)
                put("closedAt", archive.closedAt)
                put("closedBy", archive.closedBy)
                put("businessName", archive.businessName)
                put("initialCash", archive.initialCash)
                put("ingresosProduccion", archive.ingresosProduccion)
                put("ingresosMercaderias", archive.ingresosMercaderias)
                put("ingresosAgregados", archive.ingresosAgregados)
                put("totalIngresos", archive.totalIngresos)
                put("mermasTotalValor", archive.mermasTotalValor)
                put("mermasTotalUnidades", archive.mermasTotalUnidades)
                put("transferenciasMonto", archive.transferenciasMonto)
                put("transferenciasCount", archive.transferenciasCount)
                put("extracciones", archive.extracciones)
                put("extraccionesNotas", archive.extraccionesNotas)
                put("efectivoEsperado", archive.efectivoEsperado)
                put("efectivoReal", archive.efectivoReal)
                put("diferencia", archive.diferencia)
                put("costoProduccion", archive.costoProduccion)
                put("costoMercaderias", archive.costoMercaderias)
                put("costoAgregados", archive.costoAgregados)
                put("costoTotal", archive.costoTotal)
                put("utilidadTeorica", archive.utilidadTeorica)
                put("pagosConfirmados", archive.pagosConfirmados)
                put("totalPagosPersonal", archive.totalPagosPersonal)
                put("totalPagoCocina", archive.totalPagoCocina)
                put("totalPagoCajero", archive.totalPagoCajero)
                put("totalPagoDependientes", archive.totalPagoDependientes)
                put("dineroFinalEnCaja", archive.dineroFinalEnCaja)
                put("notas", archive.notas)
                put("createdAt", archive.createdAt)

                // Produccion Items
                val prodArray = JSONArray()
                archive.produccionItems.forEach { p ->
                    prodArray.put(JSONObject().apply {
                        put("productId", p.productId)
                        put("productName", p.productName)
                        put("tandasCount", p.tandasCount)
                        put("qtyPerTanda", p.qtyPerTanda)
                        put("totalProduced", p.totalProduced)
                        put("unit", p.unit)
                        put("defectuoso", p.defectuoso)
                        put("consumo", p.consumo)
                        put("regalia", p.regalia)
                        put("pendientes", p.pendientes)
                        put("vendible", p.vendible)
                        put("price", p.price)
                        put("ingresoEstimado", p.ingresoEstimado)
                        put("pagoCocinaUnitario", p.pagoCocinaUnitario)
                        put("cantidadCocineros", p.cantidadCocineros)
                    })
                }
                put("produccionItems", prodArray)

                // Mercaderia Items
                val mercArray = JSONArray()
                archive.mercaderiaItems.forEach { m ->
                    mercArray.put(JSONObject().apply {
                        put("mercaderiaId", m.mercaderiaId)
                        put("productId", m.productId)
                        put("productName", m.productName)
                        put("unit", m.unit)
                        put("existenciaInicial", m.existenciaInicial)
                        put("entradas", m.entradas)
                        put("existenciaFinal", m.existenciaFinal)
                        put("defectuoso", m.defectuoso)
                        put("consumo", m.consumo)
                        put("regalia", m.regalia)
                        put("ventas", m.ventas)
                        put("price", m.price)
                        put("ingresoEstimado", m.ingresoEstimado)
                        put("isConfitura", m.isConfitura)
                    })
                }
                put("mercaderiaItems", mercArray)

                // Agregado Items
                val agArray = JSONArray()
                archive.agregadoItems.forEach { ag ->
                    agArray.put(JSONObject().apply {
                        put("materiaPrimaId", ag.materiaPrimaId)
                        put("name", ag.name)
                        put("unit", ag.unit)
                        put("racionesEnviadas", ag.racionesEnviadas)
                        put("racionesVendidas", ag.racionesVendidas)
                        put("racionesRegalia", ag.racionesRegalia)
                        put("racionesSobrantes", ag.racionesSobrantes)
                        put("precioVenta", ag.precioVenta)
                        put("costoPorRacion", ag.costoPorRacion)
                        put("ingresoEstimado", ag.ingresoEstimado)
                    })
                }
                put("agregadoItems", agArray)

                // Cocina Pago Rows
                val cocArray = JSONArray()
                archive.cocinaPagoRows.forEach { c ->
                    cocArray.put(JSONObject().apply {
                        put("productName", c.productName)
                        put("vendible", c.vendible)
                        put("unit", c.unit)
                        put("pagoUnitario", c.pagoUnitario)
                        put("cantidadCocineros", c.cantidadCocineros)
                        put("totalPago", c.totalPago)
                    })
                }
                put("cocinaPagoRows", cocArray)

                // Cajero Pago Info
                if (archive.cajeroPagoInfo != null) {
                    put("cajeroPagoInfo", JSONObject().apply {
                        put("pagoProduccion", archive.cajeroPagoInfo.pagoProduccion)
                        put("pagoMercaderias", archive.cajeroPagoInfo.pagoMercaderias)
                        put("totalPago", archive.cajeroPagoInfo.totalPago)
                    })
                }

                // Dependientes Rows
                val depArray = JSONArray()
                archive.dependientesRows.forEach { dep ->
                    depArray.put(JSONObject().apply {
                        put("id", dep.id)
                        put("name", dep.name)
                        put("ventasProduccion", dep.ventasProduccion)
                        put("ventasBebidas", dep.ventasBebidas)
                        put("ventasTotales", dep.ventasTotales)
                        put("montoPago", dep.montoPago)
                    })
                }
                put("dependientesRows", depArray)
            }

            val file = getArchiveFile(context, archive.jornadaId)
            file.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Obtiene el archivo congelado de una jornada si existe en almacenamiento.
     */
    fun getArchive(context: Context, jornadaId: Long): JornadaCuadreCajaArchive? {
        val file = getArchiveFile(context, jornadaId)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            parseArchiveFromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseArchiveFromJson(json: JSONObject): JornadaCuadreCajaArchive {
        val jornadaId = json.getLong("jornadaId")
        val openedAt = json.getLong("openedAt")
        val closedAt = json.optLong("closedAt", openedAt)
        val closedBy = json.optString("closedBy", "Administrador")
        val businessName = json.optString("businessName", "ElQadre")
        val initialCash = json.optDouble("initialCash", 0.0)
        val ingresosProduccion = json.optDouble("ingresosProduccion", 0.0)
        val ingresosMercaderias = json.optDouble("ingresosMercaderias", 0.0)
        val ingresosAgregados = json.optDouble("ingresosAgregados", 0.0)
        val totalIngresos = json.optDouble("totalIngresos", ingresosProduccion + ingresosMercaderias + ingresosAgregados)
        val mermasTotalValor = json.optDouble("mermasTotalValor", 0.0)
        val mermasTotalUnidades = json.optDouble("mermasTotalUnidades", 0.0)
        val transferenciasMonto = json.optDouble("transferenciasMonto", 0.0)
        val transferenciasCount = json.optInt("transferenciasCount", 0)
        val extracciones = json.optDouble("extracciones", 0.0)
        val extraccionesNotas = json.optString("extraccionesNotas", "")
        val efectivoEsperado = json.optDouble("efectivoEsperado", 0.0)
        val efectivoReal = json.optDouble("efectivoReal", 0.0)
        val diferencia = json.optDouble("diferencia", 0.0)
        val costoProduccion = json.optDouble("costoProduccion", 0.0)
        val costoMercaderias = json.optDouble("costoMercaderias", 0.0)
        val costoAgregados = json.optDouble("costoAgregados", 0.0)
        val costoTotal = json.optDouble("costoTotal", costoProduccion + costoMercaderias + costoAgregados)
        val utilidadTeorica = json.optDouble("utilidadTeorica", 0.0)
        val pagosConfirmados = json.optBoolean("pagosConfirmados", false)
        val totalPagosPersonal = json.optDouble("totalPagosPersonal", 0.0)
        val totalPagoCocina = json.optDouble("totalPagoCocina", 0.0)
        val totalPagoCajero = json.optDouble("totalPagoCajero", 0.0)
        val totalPagoDependientes = json.optDouble("totalPagoDependientes", 0.0)
        val dineroFinalEnCaja = json.optDouble("dineroFinalEnCaja", 0.0)
        val notas = json.optString("notas", "")
        val createdAt = json.optLong("createdAt", System.currentTimeMillis())

        // Produccion
        val prodArray = json.optJSONArray("produccionItems") ?: JSONArray()
        val prodList = mutableListOf<ArchivedProduccionItem>()
        for (i in 0 until prodArray.length()) {
            val pObj = prodArray.getJSONObject(i)
            prodList.add(
                ArchivedProduccionItem(
                    productId = pObj.optLong("productId"),
                    productName = pObj.optString("productName", "Producto"),
                    tandasCount = pObj.optInt("tandasCount", 1),
                    qtyPerTanda = pObj.optDouble("qtyPerTanda", 0.0),
                    totalProduced = pObj.optDouble("totalProduced", 0.0),
                    unit = pObj.optString("unit", "U"),
                    defectuoso = pObj.optDouble("defectuoso", 0.0),
                    consumo = pObj.optDouble("consumo", 0.0),
                    regalia = pObj.optDouble("regalia", 0.0),
                    pendientes = pObj.optDouble("pendientes", 0.0),
                    vendible = pObj.optDouble("vendible", 0.0),
                    price = pObj.optDouble("price", 0.0),
                    ingresoEstimado = pObj.optDouble("ingresoEstimado", 0.0),
                    pagoCocinaUnitario = pObj.optDouble("pagoCocinaUnitario", 0.0),
                    cantidadCocineros = pObj.optInt("cantidadCocineros", 1)
                )
            )
        }

        // Mercaderias
        val mercArray = json.optJSONArray("mercaderiaItems") ?: JSONArray()
        val mercList = mutableListOf<ArchivedMercaderiaItem>()
        for (i in 0 until mercArray.length()) {
            val mObj = mercArray.getJSONObject(i)
            mercList.add(
                ArchivedMercaderiaItem(
                    mercaderiaId = mObj.optLong("mercaderiaId"),
                    productId = mObj.optLong("productId"),
                    productName = mObj.optString("productName", "Mercadería"),
                    unit = mObj.optString("unit", "U"),
                    existenciaInicial = mObj.optDouble("existenciaInicial", 0.0),
                    entradas = mObj.optDouble("entradas", 0.0),
                    existenciaFinal = mObj.optDouble("existenciaFinal", 0.0),
                    defectuoso = mObj.optDouble("defectuoso", 0.0),
                    consumo = mObj.optDouble("consumo", 0.0),
                    regalia = mObj.optDouble("regalia", 0.0),
                    ventas = mObj.optDouble("ventas", 0.0),
                    price = mObj.optDouble("price", 0.0),
                    ingresoEstimado = mObj.optDouble("ingresoEstimado", 0.0),
                    isConfitura = mObj.optBoolean("isConfitura", false)
                )
            )
        }

        // Agregados
        val agArray = json.optJSONArray("agregadoItems") ?: JSONArray()
        val agList = mutableListOf<ArchivedAgregadoItem>()
        for (i in 0 until agArray.length()) {
            val aObj = agArray.getJSONObject(i)
            agList.add(
                ArchivedAgregadoItem(
                    materiaPrimaId = aObj.optLong("materiaPrimaId"),
                    name = aObj.optString("name", "Agregado"),
                    unit = aObj.optString("unit", "Ración"),
                    racionesEnviadas = aObj.optDouble("racionesEnviadas", 0.0),
                    racionesVendidas = aObj.optDouble("racionesVendidas", 0.0),
                    racionesRegalia = aObj.optDouble("racionesRegalia", 0.0),
                    racionesSobrantes = aObj.optDouble("racionesSobrantes", 0.0),
                    precioVenta = aObj.optDouble("precioVenta", 0.0),
                    costoPorRacion = aObj.optDouble("costoPorRacion", 0.0),
                    ingresoEstimado = aObj.optDouble("ingresoEstimado", 0.0)
                )
            )
        }

        // Cocina Pago Rows
        val cocArray = json.optJSONArray("cocinaPagoRows") ?: JSONArray()
        val cocList = mutableListOf<ArchivedCocinaPagoRow>()
        for (i in 0 until cocArray.length()) {
            val cObj = cocArray.getJSONObject(i)
            cocList.add(
                ArchivedCocinaPagoRow(
                    productName = cObj.optString("productName", "Producto"),
                    vendible = cObj.optDouble("vendible", 0.0),
                    unit = cObj.optString("unit", "U"),
                    pagoUnitario = cObj.optDouble("pagoUnitario", 0.0),
                    cantidadCocineros = cObj.optInt("cantidadCocineros", 1),
                    totalPago = cObj.optDouble("totalPago", 0.0)
                )
            )
        }

        // Cajero Pago Info
        val cajeroObj = json.optJSONObject("cajeroPagoInfo")
        val cajeroInfo = if (cajeroObj != null) {
            ArchivedCajeroPagoInfo(
                pagoProduccion = cajeroObj.optDouble("pagoProduccion", 0.0),
                pagoMercaderias = cajeroObj.optDouble("pagoMercaderias", 0.0),
                totalPago = cajeroObj.optDouble("totalPago", 0.0)
            )
        } else null

        // Dependientes
        val depArray = json.optJSONArray("dependientesRows") ?: JSONArray()
        val depList = mutableListOf<ArchivedDependientePago>()
        for (i in 0 until depArray.length()) {
            val dObj = depArray.getJSONObject(i)
            depList.add(
                ArchivedDependientePago(
                    id = dObj.optInt("id", i + 1),
                    name = dObj.optString("name", "Dependiente"),
                    ventasProduccion = dObj.optDouble("ventasProduccion", 0.0),
                    ventasBebidas = dObj.optDouble("ventasBebidas", 0.0),
                    ventasTotales = dObj.optDouble("ventasTotales", 0.0),
                    montoPago = dObj.optDouble("montoPago", 0.0)
                )
            )
        }

        return JornadaCuadreCajaArchive(
            jornadaId = jornadaId,
            openedAt = openedAt,
            closedAt = closedAt,
            closedBy = closedBy,
            businessName = businessName,
            initialCash = initialCash,
            ingresosProduccion = ingresosProduccion,
            ingresosMercaderias = ingresosMercaderias,
            ingresosAgregados = ingresosAgregados,
            totalIngresos = totalIngresos,
            mermasTotalValor = mermasTotalValor,
            mermasTotalUnidades = mermasTotalUnidades,
            transferenciasMonto = transferenciasMonto,
            transferenciasCount = transferenciasCount,
            extracciones = extracciones,
            extraccionesNotas = extraccionesNotas,
            efectivoEsperado = efectivoEsperado,
            efectivoReal = efectivoReal,
            diferencia = diferencia,
            costoProduccion = costoProduccion,
            costoMercaderias = costoMercaderias,
            costoAgregados = costoAgregados,
            costoTotal = costoTotal,
            utilidadTeorica = utilidadTeorica,
            pagosConfirmados = pagosConfirmados,
            totalPagosPersonal = totalPagosPersonal,
            totalPagoCocina = totalPagoCocina,
            totalPagoCajero = totalPagoCajero,
            totalPagoDependientes = totalPagoDependientes,
            dineroFinalEnCaja = dineroFinalEnCaja,
            notas = notas,
            produccionItems = prodList,
            mercaderiaItems = mercList,
            agregadoItems = agList,
            cocinaPagoRows = cocList,
            cajeroPagoInfo = cajeroInfo,
            dependientesRows = depList,
            createdAt = createdAt
        )
    }

    /**
     * Obtiene o construye un archivo histórico de Cuadre de Caja para una jornada dada.
     * Si ya existe congelado en archivo JSON, lo devuelve tal cual.
     * Si no existe en JSON (ej. jornada antigua), lo reconstruye a partir de la información registrada en la DB y lo guarda.
     */
    fun getOrBuildArchive(
        context: Context,
        jornada: Jornada,
        allTandas: List<Tanda>,
        allMovimientosMercaderia: List<MovimientoMercaderia>,
        allProducts: List<Product>,
        allMercaderias: List<Mercaderia>,
        allTransferencias: List<Transferencia>,
        businessName: String
    ): JornadaCuadreCajaArchive {
        val existing = getArchive(context, jornada.id)
        if (existing != null) return existing

        val constructed = buildArchiveFromHistoricalData(
            jornada = jornada,
            allTandas = allTandas,
            allMovimientosMercaderia = allMovimientosMercaderia,
            allProducts = allProducts,
            allMercaderias = allMercaderias,
            allTransferencias = allTransferencias,
            businessName = businessName
        )
        saveArchive(context, constructed)
        return constructed
    }

    fun buildArchiveFromHistoricalData(
        jornada: Jornada,
        allTandas: List<Tanda>,
        allMovimientosMercaderia: List<MovimientoMercaderia>,
        allProducts: List<Product>,
        allMercaderias: List<Mercaderia>,
        allTransferencias: List<Transferencia>,
        businessName: String
    ): JornadaCuadreCajaArchive {
        val jId = jornada.id
        val jTandas = allTandas.filter {
            it.jornadaId == jId || (jornada.openedAt > 0 && it.date >= jornada.openedAt && (jornada.closedAt == null || it.date <= (jornada.closedAt ?: Long.MAX_VALUE)))
        }

        val tandasByProduct = jTandas.groupBy { it.productId }
        val prodList = mutableListOf<ArchivedProduccionItem>()

        tandasByProduct.forEach { (productId, tandasList) ->
            val prod = allProducts.find { it.id == productId }
            val prodName = prod?.name ?: (tandasList.firstOrNull()?.productName ?: "Producto #$productId")
            val unit = prod?.unitOfMeasure ?: "U"
            val totalProd = tandasList.sumOf { if (it.actualYield > 0.0) it.actualYield else if (it.expectedYield > 0.0) it.expectedYield else it.estimatedYield }
            val unitPrice = tandasList.firstOrNull()?.salePrice ?: (prod?.price ?: 0.0)
            val sold = tandasList.sumOf { if (it.quantitySold > 0.0) it.quantitySold else if (it.actualYield > 0.0) it.actualYield else it.expectedYield }
            val revenue = tandasList.sumOf { if (it.realRevenue > 0.0) it.realRevenue else (it.quantitySold * unitPrice) }

            prodList.add(
                ArchivedProduccionItem(
                    productId = productId,
                    productName = prodName,
                    tandasCount = tandasList.size,
                    qtyPerTanda = if (tandasList.isNotEmpty()) totalProd / tandasList.size else 0.0,
                    totalProduced = totalProd,
                    unit = unit,
                    defectuoso = 0.0,
                    consumo = 0.0,
                    regalia = 0.0,
                    pendientes = (totalProd - sold).coerceAtLeast(0.0),
                    vendible = sold,
                    price = unitPrice,
                    ingresoEstimado = if (revenue > 0.0) revenue else (sold * unitPrice),
                    pagoCocinaUnitario = 0.0,
                    cantidadCocineros = 1
                )
            )
        }

        // Mercaderias
        val jMovs = allMovimientosMercaderia.filter {
            it.jornadaId == jId || (jornada.openedAt > 0 && it.date >= jornada.openedAt && (jornada.closedAt == null || it.date <= (jornada.closedAt ?: Long.MAX_VALUE)))
        }

        val mercList = mutableListOf<ArchivedMercaderiaItem>()
        allMercaderias.forEach { merc ->
            val prod = allProducts.find { it.id == merc.productId }
            val movsMerc = jMovs.filter { it.mercaderiaId == merc.id }
            val sold = movsMerc.filter { it.type.uppercase() == "VENTA" }.sumOf { it.quantity }
            val mermas = movsMerc.filter { it.type.uppercase() == "MERMA" || it.type.uppercase() == "DEFECTUOSO" }.sumOf { it.quantity }
            val entradas = movsMerc.filter { it.type.uppercase() == "ENTRADA" || it.type.uppercase() == "PARA VENTA" }.sumOf { it.quantity }
            val price = prod?.price ?: 0.0

            if (sold > 0.0 || mermas > 0.0 || entradas > 0.0 || merc.isActive) {
                mercList.add(
                    ArchivedMercaderiaItem(
                        mercaderiaId = merc.id,
                        productId = merc.productId,
                        productName = prod?.name ?: "Mercadería #${merc.id}",
                        unit = merc.unitOfMeasure.ifBlank { prod?.unitOfMeasure ?: "U" },
                        existenciaInicial = merc.initialStock,
                        entradas = entradas,
                        existenciaFinal = (merc.initialStock + entradas - sold - mermas).coerceAtLeast(0.0),
                        defectuoso = mermas,
                        consumo = 0.0,
                        regalia = 0.0,
                        ventas = sold,
                        price = price,
                        ingresoEstimado = sold * price,
                        isConfitura = prod?.category?.equals("CONFITURAS", ignoreCase = true) == true
                    )
                )
            }
        }

        val transJornada = allTransferencias.filter {
            it.jornadaId == jId || (jornada.openedAt > 0 && it.receivedAt >= jornada.openedAt && (jornada.closedAt == null || it.receivedAt <= (jornada.closedAt ?: Long.MAX_VALUE)))
        }
        val transTotal = transJornada.sumOf { it.amount }

        val ingProd = if (jornada.realSalesProduccion > 0.0) jornada.realSalesProduccion else prodList.sumOf { it.ingresoEstimado }
        val ingMerc = if (jornada.realSalesMercaderias > 0.0) jornada.realSalesMercaderias else mercList.sumOf { it.ingresoEstimado }
        val totalIng = ingProd + ingMerc

        return JornadaCuadreCajaArchive(
            jornadaId = jornada.id,
            openedAt = jornada.openedAt,
            closedAt = jornada.closedAt ?: System.currentTimeMillis(),
            closedBy = jornada.closedBy ?: "DUEÑO",
            businessName = businessName,
            initialCash = jornada.initialCash,
            ingresosProduccion = ingProd,
            ingresosMercaderias = ingMerc,
            ingresosAgregados = 0.0,
            totalIngresos = totalIng,
            mermasTotalValor = 0.0,
            mermasTotalUnidades = 0.0,
            transferenciasMonto = transTotal,
            transferenciasCount = transJornada.size,
            extracciones = jornada.extracciones,
            extraccionesNotas = "",
            efectivoEsperado = if (jornada.expectedCash > 0.0) jornada.expectedCash else (jornada.initialCash + totalIng - transTotal - jornada.extracciones),
            efectivoReal = jornada.finalCash,
            diferencia = jornada.cashDifference,
            costoProduccion = 0.0,
            costoMercaderias = 0.0,
            costoAgregados = 0.0,
            costoTotal = 0.0,
            utilidadTeorica = 0.0,
            pagosConfirmados = jornada.liquidezFinal > 0.0,
            totalPagosPersonal = 0.0,
            totalPagoCocina = 0.0,
            totalPagoCajero = 0.0,
            totalPagoDependientes = 0.0,
            dineroFinalEnCaja = if (jornada.liquidezFinal > 0.0) jornada.liquidezFinal else jornada.finalCash,
            notas = jornada.notes,
            produccionItems = prodList,
            mercaderiaItems = mercList,
            agregadoItems = emptyList(),
            cocinaPagoRows = emptyList(),
            cajeroPagoInfo = null,
            dependientesRows = emptyList(),
            createdAt = System.currentTimeMillis()
        )
    }
}
