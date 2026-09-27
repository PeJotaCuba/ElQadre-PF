package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.model.*
import com.example.licensing.BusinessCodeHelper
import com.example.ui.viewmodel.MainUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSummary(
    val codigoNegocio: String,
    val nombreNegocio: String,
    val timestamp: Long,
    val dateStr: String,
    val formatVersion: String,
    val materiasPrimasCount: Int,
    val productsCount: Int,
    val productosElaboradosCount: Int,
    val recetaIngredientesCount: Int,
    val tandasCount: Int = 0,
    val mercaderiasCount: Int,
    val categoriesCount: Int,
    val personalCount: Int,
    val usersCount: Int,
    val jornadasCount: Int,
    val ordersCount: Int,
    val gastosCount: Int,
    val inversionesCount: Int,
    val transferenciasCount: Int = 0,
    val smsQueueCount: Int = 0
)

data class LegacyImportSummary(
    val insumosNuevos: Int,
    val insumosActualizados: Int,
    val productosNuevos: Int,
    val productosActualizados: Int,
    val recetasNuevas: Int,
    val recetasActualizadas: Int,
    val categoriasNuevas: Int,
    val categoriasActualizadas: Int
)

object BusinessBackupManager {
    private const val APP_IDENTIFIER = "Q_RESPALDO"
    private const val FORMAT_IDENTIFIER = "ELQADRE_FULL_BUSINESS_BACKUP"

    fun createBackupJson(context: Context, uiState: MainUiState): String {
        val currentBizCode = BusinessCodeHelper.resolveBusinessCode(
            context = context,
            configNegocio = uiState.businessConfig,
            configGeneral = uiState.generalConfig
        )

        val root = JSONObject()
        root.put("identificador_archivo", APP_IDENTIFIER)
        root.put("formatIdentifier", FORMAT_IDENTIFIER)
        root.put("nombre_archivo", "Q_respaldo.json")
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())
        root.put("codigoNegocio", currentBizCode)
        root.put("nombreNegocio", uiState.businessConfig?.nombreNegocio ?: "El Qadre POS")

        // 1. Configuracion Negocio (Ajustes -> Preferencias / General)
        val cfgNegocioObj = uiState.businessConfig?.let { cfg ->
            JSONObject().apply {
                put("id", cfg.id)
                put("nombreNegocio", cfg.nombreNegocio)
                put("direccion", cfg.direccion)
                put("telefono", cfg.telefono)
                put("codigoNegocio", cfg.codigoNegocio)
                put("logoPath", cfg.logoPath ?: JSONObject.NULL)
                put("fechaCreacion", cfg.fechaCreacion)
                put("fechaActualizacion", cfg.fechaActualizacion)
            }
        }

        // 2. Configuracion General y Divisas (Ajustes -> Preferencias)
        val cfgGeneralObj = uiState.generalConfig?.let { cfg ->
            JSONObject().apply {
                put("id", cfg.id)
                put("moneda", cfg.moneda)
                put("metodosPago", cfg.metodosPago)
                put("parametrosJornada", cfg.parametrosJornada)
                put("denominacionesCaja", cfg.denominacionesCaja)
                put("tasaUsd", cfg.tasaUsd)
                put("tasaEur", cfg.tasaEur)
                put("telefonoDueno", cfg.telefonoDueno)
                put("telefonoCajero", cfg.telefonoCajero)
                put("telefonoAdmin", cfg.telefonoAdmin)
                put("urlUsuariosJson", cfg.urlUsuariosJson)
                put("lastUserUpdateDate", cfg.lastUserUpdateDate)
                put("lastUserUpdateStatus", cfg.lastUserUpdateStatus)
                put("lastUserUpdateVersion", cfg.lastUserUpdateVersion)
                put("urlCatalogoJson", cfg.urlCatalogoJson)
                put("lastCatalogoUpdateDate", cfg.lastCatalogoUpdateDate)
                put("lastCatalogoUpdateStatus", cfg.lastCatalogoUpdateStatus)
                put("lastCatalogoUpdateVersion", cfg.lastCatalogoUpdateVersion)
                put("urlMercainvJson", cfg.urlMercainvJson)
                put("lastMercainvUpdateDate", cfg.lastMercainvUpdateDate)
                put("lastMercainvUpdateStatus", cfg.lastMercainvUpdateStatus)
                put("lastMercainvUpdateVersion", cfg.lastMercainvUpdateVersion)
                put("urlQDuenoJson", cfg.urlQDuenoJson)
                put("urlVersionJson", cfg.urlVersionJson)
                put("lastQDuenoUpdateDate", cfg.lastQDuenoUpdateDate)
                put("lastQDuenoUpdateStatus", cfg.lastQDuenoUpdateStatus)
                put("lastQDuenoUpdateVersion", cfg.lastQDuenoUpdateVersion)
                put("lastQDuenoUpdateFechaPublicacion", cfg.lastQDuenoUpdateFechaPublicacion)
            }
        }

        // 3. Materias Primas / Insumos y Agregados (Ajustes -> Gestión -> Inventario -> Producción)
        val mpArr = JSONArray()
        val b1InsumosArr = JSONArray()
        val b1AgregadosArr = JSONArray()

        uiState.materiasPrimas.forEach { mp ->
            val obj = JSONObject().apply {
                put("id", mp.id)
                put("name", mp.name)
                put("unit", mp.unit)
                put("unitCost", mp.unitCost)
                put("isActive", mp.isActive)
                put("stock", mp.stock)
                put("initialStock", mp.initialStock)
                put("purchasePrice", mp.purchasePrice)
                put("purchaseUnit", mp.purchaseUnit)
                put("purchaseQuantity", mp.purchaseQuantity)
                put("productId", mp.productId ?: JSONObject.NULL)
                put("isAgregado", mp.isAgregado)
                put("rationQuantity", mp.rationQuantity)
                put("rationUnit", mp.rationUnit)
                put("suggestedPrice", mp.suggestedPrice)
                put("salePrice", mp.salePrice)
                put("stockEnVenta", mp.stockEnVenta)
                put("racionesEnVenta", mp.racionesEnVenta)
                put("purchaseMode", mp.purchaseMode)
                put("purchaseLotUnits", mp.purchaseLotUnits)
                put("purchaseLotQuantity", mp.purchaseLotQuantity)
                put("purchaseLotPrice", mp.purchaseLotPrice)
            }
            mpArr.put(obj)
            if (mp.isAgregado) {
                b1AgregadosArr.put(obj)
            } else {
                b1InsumosArr.put(obj)
            }
        }

        // Movimientos Materia Prima
        val mmpArr = JSONArray()
        uiState.movimientosMateriaPrima.forEach { mmp ->
            val obj = JSONObject().apply {
                put("id", mmp.id)
                put("materiaPrimaId", mmp.materiaPrimaId)
                put("materiaPrimaName", mmp.materiaPrimaName)
                put("type", mmp.type)
                put("quantity", mmp.quantity)
                put("unit", mmp.unit)
                put("date", mmp.date)
                put("responsibleUser", mmp.responsibleUser)
                put("notes", mmp.notes)
                put("resultingStock", mmp.resultingStock)
            }
            mmpArr.put(obj)
        }

        // 4. Products / Catálogo (Ajustes -> Gestión -> Catálogo)
        val prodArr = JSONArray()
        val prodProduccionArr = JSONArray()
        val prodMercaderiasArr = JSONArray()

        uiState.products.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("code", p.code)
                put("name", p.name)
                put("category", p.category)
                put("price", p.price)
                put("cost", p.cost)
                put("stock", p.stock)
                put("minStock", p.minStock)
                put("destination", p.destination)
                put("isAvailable", p.isAvailable)
                put("description", p.description)
                put("unitOfMeasure", p.unitOfMeasure)
                put("imagePath", p.imagePath ?: JSONObject.NULL)
                put("admitsAgregados", p.admitsAgregados)
                put("agregadosList", p.agregadosList)
                put("isConvertedToInsumo", p.isConvertedToInsumo)
                put("presentacionesEspeciales", p.presentacionesEspeciales)
            }
            prodArr.put(obj)
            val isMercaderia = p.destination.equals("BARRA", ignoreCase = true) ||
                    uiState.mercaderias.any { it.productId == p.id }
            if (isMercaderia) {
                prodMercaderiasArr.put(obj)
            } else {
                prodProduccionArr.put(obj)
            }
        }

        // 5. Productos Elaborados (Ajustes -> Gestión -> Inventario -> Producción)
        val peArr = JSONArray()
        uiState.productosElaborados.forEach { pe ->
            val obj = JSONObject().apply {
                put("id", pe.id)
                put("productId", pe.productId)
                put("isActive", pe.isActive)
                put("recipeName", pe.recipeName)
                put("productionUnit", pe.productionUnit)
                put("baseYield", pe.baseYield)
                put("baseMateriaPrimaId", pe.baseMateriaPrimaId)
                put("baseQuantity", pe.baseQuantity)
                put("estimatedDailyQuantity", pe.estimatedDailyQuantity)
                put("ppd", pe.ppd)
                put("precioDefinitivo", pe.precioDefinitivo)
                put("hasPrecioDefinitivo", pe.hasPrecioDefinitivo)
                put("targetMarginPct", pe.targetMarginPct)
                put("pagoCocinaUnitario", pe.pagoCocinaUnitario)
                put("cantidadCocineros", pe.cantidadCocineros)
                put("isPagoCocinaFijo", pe.isPagoCocinaFijo)
                put("pagoDependienteUnitario", pe.pagoDependienteUnitario)
                put("pagoCajeroUnitario", pe.pagoCajeroUnitario)
            }
            peArr.put(obj)
        }

        // 6. Receta Ingredientes (Ajustes -> Gestión -> Inventario -> Producción)
        val riArr = JSONArray()
        uiState.recetaIngredientes.forEach { ri ->
            val obj = JSONObject().apply {
                put("id", ri.id)
                put("productoElaboradoId", ri.productoElaboradoId)
                put("materiaPrimaId", ri.materiaPrimaId)
                put("quantity", ri.quantity)
                put("unit", ri.unit)
            }
            riArr.put(obj)
        }

        // 7. Tandas (Ajustes -> Gestión -> Inventario -> Tandas)
        val tandasArr = JSONArray()
        val tandasJornadaArr = JSONArray()
        val tandasCerradasArr = JSONArray()

        uiState.tandas.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("uuid", t.uuid)
                put("productId", t.productId)
                put("productName", t.productName)
                put("date", t.date)
                put("responsibleUser", t.responsibleUser)
                put("baseMateriaPrimaId", t.baseMateriaPrimaId)
                put("baseMateriaPrimaName", t.baseMateriaPrimaName)
                put("baseQuantityUsed", t.baseQuantityUsed)
                put("baseQuantityUnit", t.baseQuantityUnit)
                put("productionFactor", t.productionFactor)
                put("estimatedYield", t.estimatedYield)
                put("productionUnit", t.productionUnit)
                put("ingredientsConsumedText", t.ingredientsConsumedText)
                put("status", t.status)
                put("jornada", t.jornada)
                put("jornadaId", t.jornadaId)
                put("observation", t.observation)
                put("laborCostType", t.laborCostType)
                put("laborCostValue", t.laborCostValue)
                put("totalLaborCost", t.totalLaborCost)
                put("totalDirectIngredientsCost", t.totalDirectIngredientsCost)
                put("totalIndirectCostAllocated", t.totalIndirectCostAllocated)
                put("totalBatchCost", t.totalBatchCost)
                put("realUnitCost", t.realUnitCost)
                put("tandaNumber", t.tandaNumber)
                put("expectedYield", t.expectedYield)
                put("actualYield", t.actualYield)
                put("yieldPercentage", t.yieldPercentage)
                put("expectedRevenue", t.expectedRevenue)
                put("estimatedProfit", t.estimatedProfit)
                put("profitMargin", t.profitMargin)
                put("inventoryDeducted", t.inventoryDeducted)
                put("ownerPayType", t.ownerPayType)
                put("ownerPayValue", t.ownerPayValue)
                put("totalOwnerPay", t.totalOwnerPay)
                put("quantitySold", t.quantitySold)
                put("salePrice", t.salePrice)
                put("realRevenue", t.realRevenue)
                put("deviceId", t.deviceId)
                put("specialPresentationName", t.specialPresentationName)
                put("specialPresentationQty", t.specialPresentationQty)
                put("specialPresentationEquivalence", t.specialPresentationEquivalence)
            }
            tandasArr.put(obj)
            if (t.status.equals("CERRADA", ignoreCase = true)) {
                tandasCerradasArr.put(obj)
            } else {
                tandasJornadaArr.put(obj)
            }
        }

        // 8. Production Batches
        val pbArr = JSONArray()
        uiState.productionBatches.forEach { pb ->
            val obj = JSONObject().apply {
                put("id", pb.id)
                put("itemName", pb.itemName)
                put("quantity", pb.quantity)
                put("destination", pb.destination)
                put("notes", pb.notes)
                put("createdBy", pb.createdBy)
                put("timestamp", pb.timestamp)
                put("status", pb.status)
            }
            pbArr.put(obj)
        }

        // 10. Mercaderias (Ajustes -> Gestión -> Inventario -> Mercaderías)
        val mercArr = JSONArray()
        uiState.mercaderias.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("productId", m.productId)
                put("acquisitionCost", m.acquisitionCost)
                put("unitOfMeasure", m.unitOfMeasure)
                put("initialStock", m.initialStock)
                put("isActive", m.isActive)
                put("directExpenses", m.directExpenses)
                put("directExpensesDetails", m.directExpensesDetails)
                put("purchaseMode", m.purchaseMode)
                put("purchasePrice", m.purchasePrice)
                put("unitsPerLot", m.unitsPerLot)
                put("dailySalesAverage", m.dailySalesAverage)
            }
            mercArr.put(obj)
        }

        // 11. Movimientos Mercaderia
        val mmArr = JSONArray()
        uiState.movimientosMercaderia.forEach { mm ->
            val obj = JSONObject().apply {
                put("id", mm.id)
                put("mercaderiaId", mm.mercaderiaId)
                put("type", mm.type)
                put("quantity", mm.quantity)
                put("date", mm.date)
                put("responsibleAdmin", mm.responsibleAdmin)
                put("notes", mm.notes)
                put("quantitySold", mm.quantitySold)
                put("salePrice", mm.salePrice)
                put("acquisitionCost", mm.acquisitionCost)
                put("realRevenue", mm.realRevenue)
                put("jornadaId", mm.jornadaId)
                put("deviceId", mm.deviceId)
            }
            mmArr.put(obj)
        }

        // 12. Stock Movements
        val smArr = JSONArray()
        uiState.stockMovements.forEach { sm ->
            val obj = JSONObject().apply {
                put("id", sm.id)
                put("productId", sm.productId)
                put("productName", sm.productName)
                put("type", sm.type)
                put("quantity", sm.quantity)
                put("reason", sm.reason)
                put("recordedBy", sm.recordedBy)
                put("timestamp", sm.timestamp)
                put("jornadaId", sm.jornadaId)
            }
            smArr.put(obj)
        }

        // 13. Categories (Ajustes -> Gestión -> Catálogo)
        val catArr = JSONArray()
        uiState.categories.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("description", c.description)
                put("isActive", c.isActive)
            }
            catArr.put(obj)
        }

        // 14. Personal Contratado (Ajustes -> Gestión -> Personal)
        val pcArr = JSONArray()
        uiState.personalContratado.forEach { pc ->
            val obj = JSONObject().apply {
                put("id", pc.id)
                put("nombreCompleto", pc.nombreCompleto)
                put("carnetIdentidad", pc.carnetIdentidad)
                put("movil", pc.movil)
                put("formaPago", pc.formaPago)
                put("fechaRegistro", pc.fechaRegistro)
                put("tieneAccesoApp", pc.tieneAccesoApp)
                put("username", pc.username)
                put("passwordHash", pc.passwordHash)
                put("passwordPlain", pc.passwordPlain)
                put("role", pc.role)
                put("dependienteTipo", pc.dependienteTipo)
                put("montoPorProducto", pc.montoPorProducto)
                put("isActive", pc.isActive)
                put("permisoProduccion", pc.permisoProduccion)
                put("permisoMercancias", pc.permisoMercancias)
                put("permisoPersonal", pc.permisoPersonal)
                put("permisoControlNegocio", pc.permisoControlNegocio)
            }
            pcArr.put(obj)
        }

        // 15. Users (Ajustes -> Preferencias / Seguridad)
        val userArr = JSONArray()
        uiState.users.filter { !it.username.equals("superadmin", ignoreCase = true) }.forEach { u ->
            val obj = JSONObject().apply {
                put("username", u.username)
                put("fullName", u.fullName)
                put("passwordHash", u.passwordHash)
                put("role", u.role.name)
                put("montoPorProducto", u.montoPorProducto)
                put("isActive", u.isActive)
                put("authorizedDeviceId", u.authorizedDeviceId ?: JSONObject.NULL)
                put("createdAt", u.createdAt)
                put("telefono", u.telefono)
                put("permisoProduccion", u.permisoProduccion)
                put("permisoMercancias", u.permisoMercancias)
                put("permisoPersonal", u.permisoPersonal)
                put("permisoControlNegocio", u.permisoControlNegocio)
            }
            userArr.put(obj)
        }

        // 16. Gastos Generales (Ajustes -> Gestión -> Gastos)
        val ggArr = JSONArray()
        uiState.gastosGenerales.forEach { gg ->
            val obj = JSONObject().apply {
                put("id", gg.id)
                put("name", gg.name)
                put("description", gg.description)
                put("amount", gg.amount)
                put("period", gg.period)
                put("periodDays", gg.periodDays)
                put("category", gg.category)
                put("isActive", gg.isActive)
                put("createdAt", gg.createdAt)
                put("inversionId", gg.inversionId ?: JSONObject.NULL)
                put("targetProductId", gg.targetProductId ?: JSONObject.NULL)
                put("targetProductIds", gg.targetProductIds ?: JSONObject.NULL)
                put("startDate", gg.startDate ?: JSONObject.NULL)
                put("endDate", gg.endDate ?: JSONObject.NULL)
                put("scope", gg.scope)
            }
            ggArr.put(obj)
        }

        // 17. Inversiones (Ajustes -> Gestión -> Inversiones)
        val invArr = JSONArray()
        uiState.inversiones.forEach { inv ->
            val obj = JSONObject().apply {
                put("id", inv.id)
                put("name", inv.name)
                put("category", inv.category)
                put("amount", inv.amount)
                put("date", inv.date)
                put("usefulLife", inv.usefulLife)
                put("usefulLifeUnit", inv.usefulLifeUnit)
                put("observation", inv.observation)
                put("targetProductId", inv.targetProductId ?: JSONObject.NULL)
                put("targetProductIds", inv.targetProductIds ?: JSONObject.NULL)
                put("startDate", inv.startDate ?: JSONObject.NULL)
                put("endDate", inv.endDate ?: JSONObject.NULL)
                put("scope", inv.scope)
                put("currency", inv.currency)
                put("originalAmount", inv.originalAmount)
                put("exchangeRate", inv.exchangeRate)
                put("convertedAmount", inv.convertedAmount)
                put("method", inv.method)
                put("dailyAmount", inv.dailyAmount)
            }
            invArr.put(obj)
        }

        // 18. Jornadas / Archivo de Jornadas (Ajustes -> Preferencias -> Archivo de Jornadas & Control de Negocio)
        val jArr = JSONArray()
        uiState.allJornadas.forEach { j ->
            val obj = JSONObject().apply {
                put("id", j.id)
                put("openedAt", j.openedAt)
                put("closedAt", j.closedAt ?: JSONObject.NULL)
                put("initialCash", j.initialCash)
                put("finalCash", j.finalCash)
                put("totalSales", j.totalSales)
                put("totalExpenses", j.totalExpenses)
                put("expectedCash", j.expectedCash)
                put("cashDifference", j.cashDifference)
                put("notes", j.notes)
                put("isOpen", j.isOpen)
                put("openedBy", j.openedBy)
                put("closedBy", j.closedBy ?: JSONObject.NULL)
                put("utilidadSalonMontoUnitario", j.utilidadSalonMontoUnitario)
                put("deviceId", j.deviceId)
                put("realSalesProduccion", j.realSalesProduccion)
                put("realCostProduccion", j.realCostProduccion)
                put("gastosProduccion", j.gastosProduccion)
                put("inversionesProduccion", j.inversionesProduccion)
                put("resultadoProduccion", j.resultadoProduccion)
                put("realSalesMercaderias", j.realSalesMercaderias)
                put("realCostMercaderias", j.realCostMercaderias)
                put("gastosMercaderias", j.gastosMercaderias)
                put("inversionesMercaderias", j.inversionesMercaderias)
                put("resultadoMercaderias", j.resultadoMercaderias)
                put("totalIngresos", j.totalIngresos)
                put("totalCostos", j.totalCostos)
                put("totalGastos", j.totalGastos)
                put("totalInversiones", j.totalInversiones)
                put("utilidadDelDia", j.utilidadDelDia)
                put("modulosUtilizados", j.modulosUtilizados)
                put("snapshotJson", j.snapshotJson)
                put("extracciones", j.extracciones)
                put("liquidezFinal", j.liquidezFinal)
            }
            jArr.put(obj)
        }

        // 19. Table Orders (Ajustes -> Gestión -> Control del Negocio)
        val oArr = JSONArray()
        uiState.allOrders.forEach { o ->
            val obj = JSONObject().apply {
                put("id", o.id)
                put("tableNumber", o.tableNumber ?: JSONObject.NULL)
                put("customerName", o.customerName)
                put("waiterUsername", o.waiterUsername)
                put("createdAt", o.createdAt)
                put("closedAt", o.closedAt ?: JSONObject.NULL)
                put("status", o.status)
                put("totalAmount", o.totalAmount)
                put("paymentMethod", o.paymentMethod)
                put("tip", o.tip)
                put("jornadaId", o.jornadaId)
                put("comandaNumber", o.comandaNumber)
                put("cocinaNumber", o.cocinaNumber)
                put("barraNumber", o.barraNumber)
                put("totalCocina", o.totalCocina)
                put("totalBarra", o.totalBarra)
                put("cashReceived", o.cashReceived)
                put("changeGiven", o.changeGiven)
                put("confirmedAt", o.confirmedAt ?: JSONObject.NULL)
                put("servedAt", o.servedAt ?: JSONObject.NULL)
                put("serviceDurationSeconds", o.serviceDurationSeconds)
                put("currency", o.currency)
                put("exchangeRate", o.exchangeRate)
                put("originalAmount", o.originalAmount)
                put("amountInCurrency", o.amountInCurrency)
            }
            oArr.put(obj)
        }

        // 20. Order Items (Ajustes -> Gestión -> Control del Negocio)
        val oiArr = JSONArray()
        uiState.allOrderItems.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("orderId", item.orderId)
                put("productId", item.productId)
                put("productName", item.productName)
                put("unitPrice", item.unitPrice)
                put("quantity", item.quantity)
                put("destination", item.destination)
                put("notes", item.notes)
                put("status", item.status)
                put("productCode", item.productCode)
            }
            oiArr.put(obj)
        }

        // 21. Transferencias (Ajustes -> Gestión -> Control del Negocio)
        val trArr = JSONArray()
        uiState.allTransferencias.forEach { tr ->
            val obj = JSONObject().apply {
                put("id", tr.id)
                put("transactionNumber", tr.transactionNumber)
                put("jornadaId", tr.jornadaId)
                put("comandaId", tr.comandaId ?: JSONObject.NULL)
                put("comandaNumber", tr.comandaNumber ?: JSONObject.NULL)
                put("amount", tr.amount)
                put("currency", tr.currency)
                put("phoneNumber", tr.phoneNumber)
                put("titularName", tr.titularName)
                put("titularCi", tr.titularCi)
                put("recipientAccount", tr.recipientAccount)
                put("smsDate", tr.smsDate)
                put("receivedAt", tr.receivedAt)
                put("cajeroUsername", tr.cajeroUsername)
                put("status", tr.status)
                put("rawSmsBody", tr.rawSmsBody)
                put("isManual", tr.isManual)
                put("source", tr.source)
            }
            trArr.put(obj)
        }

        // 22. Bitacora (Ajustes -> Gestión -> Control del Negocio)
        val bitArr = JSONArray()
        uiState.bitacoraEntries.forEach { b ->
            val obj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("content", b.content)
                put("category", b.category)
                put("authorUsername", b.authorUsername)
                put("timestamp", b.timestamp)
                put("priority", b.priority)
            }
            bitArr.put(obj)
        }

        // 23. Consumo Personal (Ajustes -> Gestión -> Control del Negocio)
        val cpArr = JSONArray()
        uiState.consumoPersonalList.forEach { cp ->
            val obj = JSONObject().apply {
                put("id", cp.id)
                put("jornadaId", cp.jornadaId)
                put("productId", cp.productId)
                put("productName", cp.productName)
                put("unitPrice", cp.unitPrice)
                put("quantity", cp.quantity)
                put("totalAmount", cp.totalAmount)
                put("recordedBy", cp.recordedBy)
                put("timestamp", cp.timestamp)
            }
            cpArr.put(obj)
        }

        // 24. Payment Proposals (Ajustes -> Gestión -> Personal)
        val ppArr = JSONArray()
        uiState.paymentProposals.forEach { pp ->
            val obj = JSONObject().apply {
                put("username", pp.username)
                put("fullName", pp.fullName)
                put("role", pp.role.name)
                put("paymentAmount", pp.paymentAmount)
                put("paymentType", pp.paymentType)
                put("isActiveProposal", pp.isActiveProposal)
                put("lastUpdated", pp.lastUpdated)
            }
            ppArr.put(obj)
        }

        // 25. SMS Comandas Queue (Control del Negocio / Mensajería)
        val smsQueueArr = JSONArray()
        try {
            val db = AppDatabase.getDatabase(context)
            val queueItems = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                db.smsComandaQueueDao().getAllComandasQueueSync()
            }
            queueItems.forEach { sq ->
                val obj = JSONObject().apply {
                    put("id", sq.id)
                    put("senderPhone", sq.senderPhone)
                    put("smsText", sq.smsText)
                    put("receivedAt", sq.receivedAt)
                    put("estado", sq.estado)
                    put("jornadaId", sq.jornadaId)
                    put("comandaNumber", sq.comandaNumber)
                }
                smsQueueArr.put(obj)
            }
        } catch (e: Exception) {
            // ignore if database not accessible synchronously
        }

        // 26. Cuadre Pagos por Jornada
        val cuadrePagosArr = JSONArray()
        uiState.allJornadas.forEach { j ->
            val pagos = CuadrePagosManager.getPagosJornada(context, j.id)
            if (pagos != null) {
                val pObj = JSONObject().apply {
                    put("jornadaId", pagos.jornadaId)
                    put("isConfirmed", pagos.isConfirmed)
                    put("confirmedAt", pagos.confirmedAt)
                    put("confirmedBy", pagos.confirmedBy)
                    put("totalPagos", pagos.totalPagos)
                    put("totalCocina", pagos.totalCocina)
                    put("totalCajero", pagos.totalCajero)
                    put("totalDependiente", pagos.totalDependiente)
                    put("cantidadDependientes", pagos.cantidadDependientes)
                    put("distributionMode", pagos.distributionMode)
                    put("efectivoContado", pagos.efectivoContado)
                    put("dineroFinalEnCaja", pagos.dineroFinalEnCaja)
                    put("cantidadCocineros", pagos.cantidadCocineros)
                    put("spaguettiPago", pagos.spaguettiPago)
                    val depsArr = JSONArray()
                    pagos.dependientes.forEach { d ->
                        depsArr.put(JSONObject().apply {
                            put("id", d.id)
                            put("name", d.name)
                            put("username", d.username)
                            put("ventasProduccion", d.ventasProduccion)
                            put("ventasBebidas", d.ventasBebidas)
                            put("ventasTotales", d.ventasTotales)
                            put("montoPago", d.montoPago)
                        })
                    }
                    put("dependientes", depsArr)
                }
                cuadrePagosArr.put(pObj)
            }
        }

        // 27. Preferencias Locales y Parámetros de Funcionamiento (Ajustes -> Preferencias)
        val prefsObj = JSONObject()
        val tarifasBebidas = BebidasTarifasPreferences.getTarifas(context)
        prefsObj.put("tarifasPagoBebidas", JSONObject().apply {
            put("pagoDependientePorUnidad", tarifasBebidas.pagoDependientePorUnidad)
            put("pagoCajeroPorUnidad", tarifasBebidas.pagoCajeroPorUnidad)
            put("pagoDependienteModalidad", tarifasBebidas.pagoDependienteModalidad)
            put("pagoDependienteValor", tarifasBebidas.pagoDependienteValor)
            put("pagoCajeroModalidad", tarifasBebidas.pagoCajeroModalidad)
            put("pagoCajeroValor", tarifasBebidas.pagoCajeroValor)
        })

        // Modos Producción
        val modosArr = JSONArray()
        uiState.products.forEach { prod ->
            val modo = ProduccionModoHelper.getModo(context, prod.id)
            if (modo != "POR_TANDAS") {
                modosArr.put(JSONObject().apply {
                    put("productId", prod.id)
                    put("modo", modo)
                })
            }
        }
        prefsObj.put("modosProduccion", modosArr)

        // Clasificacion Transferencias
        val clasifPrefs = context.getSharedPreferences("clasificacion_transferencias_prefs", Context.MODE_PRIVATE)
        val clasifArr = JSONArray()
        uiState.allJornadas.forEach { j ->
            val key = "clasif_jornada_${j.id}"
            val valStr = clasifPrefs.getString(key, null)
            if (valStr != null) {
                clasifArr.put(JSONObject().apply {
                    put("jornadaId", j.id)
                    put("clasificacion", valStr)
                })
            }
        }
        prefsObj.put("clasificacionTransferencias", clasifArr)

        // Módulos Visibles Dueño
        val visibleModules = DuenoSessionPreferences.getVisibleModules(context, uiState.currentUser?.username)
        val vmArr = JSONArray()
        visibleModules.forEach { vmArr.put(it) }
        prefsObj.put("modulosVisiblesDueno", vmArr)

        // Límites de Transferencia para Salón por Producto (Ajustes -> Catálogo / Transferencias)
        val salonTransferPrefs = context.getSharedPreferences("SalonTransferPrefs", Context.MODE_PRIVATE)
        val salonLimitsObj = JSONObject()
        salonTransferPrefs.all.forEach { (k, v) ->
            if (v is Int) {
                salonLimitsObj.put(k, v)
            }
        }
        prefsObj.put("limitesTransferenciaSalon", salonLimitsObj)

        // Sincronización de Marcas de Tiempo
        val syncPrefs = context.getSharedPreferences("elqadre_json_sync", Context.MODE_PRIVATE)
        val syncObj = JSONObject()
        syncPrefs.all.forEach { (k, v) ->
            if (v is Long) {
                syncObj.put(k, v)
            }
        }
        prefsObj.put("jsonSyncTimestamps", syncObj)

        // -----------------------------------------------------------------
        // CONSTRUCCIÓN DE LOS 7 BLOQUES OBLIGATORIOS DE Q_respaldo.json
        // -----------------------------------------------------------------

        // BLOQUE 1: INSUMOS Y AGREGADOS
        val bloque1 = JSONObject().apply {
            put("titulo", "INSUMOS Y AGREGADOS")
            put("totalInsumos", b1InsumosArr.length())
            put("totalAgregados", b1AgregadosArr.length())
            put("totalMateriasPrimas", mpArr.length())
            put("insumos", b1InsumosArr)
            put("agregados", b1AgregadosArr)
            put("materiasPrimas", mpArr)
            put("movimientosMateriaPrima", mmpArr)
        }

        // BLOQUE 2: PRODUCTOS
        val bloque2 = JSONObject().apply {
            put("titulo", "PRODUCTOS")
            put("totalProductos", prodArr.length())
            put("totalProductosProduccion", prodProduccionArr.length())
            put("totalProductosMercaderias", prodMercaderiasArr.length())
            put("products", prodArr)
            put("productosProduccion", prodProduccionArr)
            put("productosMercaderias", prodMercaderiasArr)
            put("productosElaborados", peArr)
            put("recetaIngredientes", riArr)
            put("mercaderias", mercArr)
            put("movimientosMercaderia", mmArr)
            put("stockMovements", smArr)
            put("categories", catArr)
        }

        // BLOQUE 3: TANDAS
        val bloque3 = JSONObject().apply {
            put("titulo", "TANDAS")
            put("totalTandas", tandasArr.length())
            put("totalTandasJornada", tandasJornadaArr.length())
            put("totalTandasCerradas", tandasCerradasArr.length())
            put("tandas", tandasArr)
            put("tandasJornada", tandasJornadaArr)
            put("tandasCerradas", tandasCerradasArr)
            put("archivoTandas", tandasArr)
            put("productionBatches", pbArr)
        }

        // BLOQUE 4: PREFERENCIAS
        val bloque4 = JSONObject().apply {
            put("titulo", "PREFERENCIAS")
            if (cfgNegocioObj != null) put("configuracionNegocio", cfgNegocioObj)
            if (cfgGeneralObj != null) put("configuracionGeneral", cfgGeneralObj)
            put("preferenciasLocales", prefsObj)
        }

        // BLOQUE 5: GESTIÓN
        val bloque5 = JSONObject().apply {
            put("titulo", "GESTIÓN")
            put("personalContratado", pcArr)
            put("users", userArr)
            put("paymentProposals", ppArr)
            put("gastosGenerales", ggArr)
            put("inversiones", invArr)
        }

        // BLOQUE 6: INFORMES
        val bloque6 = JSONObject().apply {
            put("titulo", "INFORMES")
            put("jornadas", jArr)
            put("tableOrders", oArr)
            put("orderItems", oiArr)
            put("transferencias", trArr)
            put("bitacoraEntries", bitArr)
            put("consumoPersonalList", cpArr)
            put("cuadrePagos", cuadrePagosArr)
            put("smsComandaQueue", smsQueueArr)
        }

        // BLOQUE 7: CUADRE DE CAJA — ÚNICAMENTE MERCADERÍAS
        // Exclusivamente Inicio y Entradas de Mercaderías.
        // No incluye Producción, ventas, gastos, créditos, efectivo, transferencias, diferencias ni estados/cierres.
        val bloque7 = JSONObject().apply {
            put("titulo", "CUADRE DE CAJA — ÚNICAMENTE MERCADERÍAS")
            put(
                "descripcion",
                "Exclusivamente Inicio y Entradas de productos de Mercaderías desde Cuadre de Caja. " +
                        "No contiene producción, ventas de producción, ventas de mercaderías, gastos, créditos, " +
                        "efectivo, transferencias, diferencias, estados de caja ni cierres de caja."
            )

            val draftPrefs = context.getSharedPreferences("elqadre_cuadre_draft_prefs", Context.MODE_PRIVATE)
            val currentJornadaId = uiState.activeJornada?.id ?: uiState.allJornadas.maxOfOrNull { it.id } ?: 0L
            val currentDraft = if (currentJornadaId > 0) CuadreDraftManager.getDraft(context, currentJornadaId) else null

            val mercaderiasCuadreArr = JSONArray()
            uiState.mercaderias.forEach { merc ->
                val prod = uiState.products.find { it.id == merc.productId }
                val draftItem = currentDraft?.mercaderiaDrafts?.find { it.mercaderiaId == merc.id }
                val inicioStr = draftItem?.existenciaInicialStr?.ifBlank { null }
                    ?: (if (merc.initialStock > 0.0) "%.1f".format(merc.initialStock).replace(',', '.') else "0")
                val entradasStr = draftItem?.entradasStr?.ifBlank { "0" } ?: "0"

                mercaderiasCuadreArr.put(JSONObject().apply {
                    put("mercaderiaId", merc.id)
                    put("productId", merc.productId)
                    put("productName", prod?.name ?: "Mercadería #${merc.id}")
                    put("inicio", inicioStr)
                    put("entradas", entradasStr)
                })
            }
            put("mercaderias", mercaderiasCuadreArr)

            val porJornadaArr = JSONArray()
            draftPrefs.all.forEach { (k, v) ->
                if (k.startsWith("draft_jornada_") && v is String) {
                    val jId = k.removePrefix("draft_jornada_").toLongOrNull() ?: 0L
                    if (jId > 0) {
                        val d = CuadreDraftManager.getDraft(context, jId)
                        if (d != null && d.mercaderiaDrafts.isNotEmpty()) {
                            val jObj = JSONObject().apply {
                                put("jornadaId", jId)
                                val itemsArr = JSONArray()
                                d.mercaderiaDrafts.forEach { m ->
                                    val merc = uiState.mercaderias.find { it.id == m.mercaderiaId }
                                    val prod = uiState.products.find { it.id == merc?.productId }
                                    itemsArr.put(JSONObject().apply {
                                        put("mercaderiaId", m.mercaderiaId)
                                        put("productId", merc?.productId ?: 0L)
                                        put("productName", prod?.name ?: "")
                                        put("inicio", m.existenciaInicialStr.ifBlank { "0" })
                                        put("entradas", m.entradasStr.ifBlank { "0" })
                                    })
                                }
                                put("mercaderias", itemsArr)
                            }
                            porJornadaArr.put(jObj)
                        }
                    }
                }
            }
            put("porJornada", porJornadaArr)
        }

        // -----------------------------------------------------------------
        // INSERCIÓN DE LOS 7 BLOQUES EN EL NODO RAÍZ DE Q_respaldo.json
        // -----------------------------------------------------------------
        root.put("bloque_1_insumos_y_agregados", bloque1)
        root.put("bloque_2_productos", bloque2)
        root.put("bloque_3_tandas", bloque3)
        root.put("bloque_4_preferencias", bloque4)
        root.put("bloque_5_gestion", bloque5)
        root.put("bloque_6_informes", bloque6)
        root.put("bloque_7_cuadre_caja_mercaderias", bloque7)

        // Alias canónicos para acceso directo y compatibilidad semántica
        root.put("insumos_y_agregados", bloque1)
        root.put("productos", bloque2)
        root.put("tandas_bloque", bloque3)
        root.put("preferencias", bloque4)
        root.put("gestion", bloque5)
        root.put("informes", bloque6)
        root.put("cuadre_de_caja_mercaderias", bloque7)

        // Compatibilidad raíz con la base de datos local y parsers existentes
        if (cfgNegocioObj != null) root.put("configuracionNegocio", cfgNegocioObj)
        if (cfgGeneralObj != null) root.put("configuracionGeneral", cfgGeneralObj)
        root.put("materiasPrimas", mpArr)
        root.put("products", prodArr)
        root.put("productosElaborados", peArr)
        root.put("recetaIngredientes", riArr)
        root.put("tandas", tandasArr)
        root.put("productionBatches", pbArr)
        root.put("movimientosMateriaPrima", mmpArr)
        root.put("mercaderias", mercArr)
        root.put("movimientosMercaderia", mmArr)
        root.put("stockMovements", smArr)
        root.put("categories", catArr)
        root.put("personalContratado", pcArr)
        root.put("users", userArr)
        root.put("gastosGenerales", ggArr)
        root.put("inversiones", invArr)
        root.put("jornadas", jArr)
        root.put("tableOrders", oArr)
        root.put("orderItems", oiArr)
        root.put("transferencias", trArr)
        root.put("bitacoraEntries", bitArr)
        root.put("consumoPersonalList", cpArr)
        root.put("paymentProposals", ppArr)
        root.put("smsComandaQueue", smsQueueArr)
        root.put("preferenciasLocales", prefsObj)

        return root.toString(2)
    }

    fun exportAndShareBackup(context: Context, uiState: MainUiState): Boolean {
        return try {
            val fileName = "Q_respaldo.json"
            val jsonString = createBackupJson(context, uiState)

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
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Completo Negocio (Q_respaldo.json)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir Respaldo del Negocio ($fileName)"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar el respaldo: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun getBackupSummary(jsonString: String, context: Context): Result<BackupSummary> {
        return try {
            val root = JSONObject(jsonString)
            val idTag = root.optString("identificador_archivo", "")
            val formatTag = root.optString("formatIdentifier", "")

            val validTags = listOf("Q_RESPALDO", "QDEP_INITIALIZATION_BACKUP_V1", "Q_ADMIN")
            if (formatTag != FORMAT_IDENTIFIER && validTags.none { idTag.contains(it, ignoreCase = true) }) {
                return Result.failure(Exception("Formato de archivo no válido para respaldo completo del negocio."))
            }

            val rawBizCode = root.optString("codigoNegocio").ifBlank {
                root.optString("businessCode", "001")
            }
            val backupBizCode = if (rawBizCode.isNotBlank()) BusinessCodeHelper.formatCode(rawBizCode) else "001"

            val nombreNegocio = root.optString("nombreNegocio", "El Qadre POS")
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
            val version = root.optString("version", "2")

            val b1 = root.optJSONObject("bloque_1_insumos_y_agregados") ?: root.optJSONObject("insumos_y_agregados")
            val b2 = root.optJSONObject("bloque_2_productos") ?: root.optJSONObject("productos")
            val b3 = root.optJSONObject("bloque_3_tandas") ?: root.optJSONObject("tandas_bloque")
            val b5 = root.optJSONObject("bloque_5_gestion") ?: root.optJSONObject("gestion")
            val b6 = root.optJSONObject("bloque_6_informes") ?: root.optJSONObject("informes")

            val mpCount = root.optJSONArray("materiasPrimas")?.length()
                ?: b1?.optJSONArray("materiasPrimas")?.length() ?: 0
            val prodCount = root.optJSONArray("products")?.length()
                ?: b2?.optJSONArray("products")?.length() ?: 0
            val peCount = root.optJSONArray("productosElaborados")?.length()
                ?: b2?.optJSONArray("productosElaborados")?.length() ?: 0
            val riCount = root.optJSONArray("recetaIngredientes")?.length()
                ?: b2?.optJSONArray("recetaIngredientes")?.length() ?: 0
            val tandasCount = root.optJSONArray("tandas")?.length()
                ?: b3?.optJSONArray("tandas")?.length()
                ?: (root.optJSONArray("productionBatches")?.length() ?: b3?.optJSONArray("productionBatches")?.length() ?: 0)
            val mercCount = root.optJSONArray("mercaderias")?.length()
                ?: b2?.optJSONArray("mercaderias")?.length() ?: 0
            val catCount = root.optJSONArray("categories")?.length()
                ?: b2?.optJSONArray("categories")?.length() ?: 0
            val pcCount = root.optJSONArray("personalContratado")?.length()
                ?: b5?.optJSONArray("personalContratado")?.length() ?: 0
            val userCount = root.optJSONArray("users")?.length()
                ?: b5?.optJSONArray("users")?.length() ?: 0
            val jCount = root.optJSONArray("jornadas")?.length()
                ?: b6?.optJSONArray("jornadas")?.length() ?: 0
            val oCount = root.optJSONArray("tableOrders")?.length()
                ?: b6?.optJSONArray("tableOrders")?.length() ?: 0
            val ggCount = root.optJSONArray("gastosGenerales")?.length()
                ?: b5?.optJSONArray("gastosGenerales")?.length() ?: 0
            val invCount = root.optJSONArray("inversiones")?.length()
                ?: b5?.optJSONArray("inversiones")?.length() ?: 0
            val trCount = root.optJSONArray("transferencias")?.length()
                ?: b6?.optJSONArray("transferencias")?.length() ?: 0
            val smsCount = root.optJSONArray("smsComandaQueue")?.length()
                ?: b6?.optJSONArray("smsComandaQueue")?.length() ?: 0

            val summary = BackupSummary(
                codigoNegocio = backupBizCode,
                nombreNegocio = nombreNegocio,
                timestamp = timestamp,
                dateStr = dateStr,
                formatVersion = version,
                materiasPrimasCount = mpCount,
                productsCount = prodCount,
                productosElaboradosCount = peCount,
                recetaIngredientesCount = riCount,
                tandasCount = tandasCount,
                mercaderiasCount = mercCount,
                categoriesCount = catCount,
                personalCount = pcCount,
                usersCount = userCount,
                jornadasCount = jCount,
                ordersCount = oCount,
                gastosCount = ggCount,
                inversionesCount = invCount,
                transferenciasCount = trCount,
                smsQueueCount = smsCount
            )
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBackupSummaryText(jsonString: String, context: Context): Result<String> {
        val summaryRes = getBackupSummary(jsonString, context)
        if (summaryRes.isFailure) {
            return Result.failure(summaryRes.exceptionOrNull() ?: Exception("Validación de respaldo fallida."))
        }
        val s = summaryRes.getOrNull()!!
        var text = "Respaldo: ${s.nombreNegocio}\n" +
                "Fecha: ${s.dateStr}\n\n" +
                "Contenido de Q_respaldo.json (7 Bloques):\n" +
                "• Bloque 1 - Insumos y Agregados: ${s.materiasPrimasCount}\n" +
                "• Bloque 2 - Productos (Cocina / Barra / Mercaderías): ${s.productsCount}\n" +
                "• Bloque 3 - Tandas y Archivo de Producción: ${s.tandasCount}\n" +
                "• Bloque 4 - Preferencias de Dueño: Integradas\n" +
                "• Bloque 5 - Gestión (Personal: ${s.personalCount}, Gastos: ${s.gastosCount}, Inversiones: ${s.inversionesCount})\n" +
                "• Bloque 6 - Informes (Jornadas: ${s.jornadasCount}, Ventas: ${s.ordersCount}, Transferencias: ${s.transferenciasCount})\n" +
                "• Bloque 7 - Cuadre de Caja (Inicio y Entradas de Mercaderías): Integrado"
        if (s.smsQueueCount > 0) {
            text += "\n• Mensajes SMS en cola: ${s.smsQueueCount}"
        }
        return Result.success(text)
    }

    suspend fun restoreBackupJson(jsonString: String, db: AppDatabase, context: Context): Result<String> {
        val summaryRes = getBackupSummary(jsonString, context)
        if (summaryRes.isFailure) {
            return Result.failure(summaryRes.exceptionOrNull() ?: Exception("Validación de respaldo fallida."))
        }
        val summary = summaryRes.getOrNull()!!

        return try {
            val root = JSONObject(jsonString)
            val b1 = root.optJSONObject("bloque_1_insumos_y_agregados") ?: root.optJSONObject("insumos_y_agregados")
            val b2 = root.optJSONObject("bloque_2_productos") ?: root.optJSONObject("productos")
            val b3 = root.optJSONObject("bloque_3_tandas") ?: root.optJSONObject("tandas_bloque")
            val b4 = root.optJSONObject("bloque_4_preferencias") ?: root.optJSONObject("preferencias")
            val b5 = root.optJSONObject("bloque_5_gestion") ?: root.optJSONObject("gestion")
            val b6 = root.optJSONObject("bloque_6_informes") ?: root.optJSONObject("informes")
            val b7 = root.optJSONObject("bloque_7_cuadre_caja_mercaderias") ?: root.optJSONObject("cuadre_de_caja_mercaderias")

            db.withTransaction {
                // Clear existing business tables
                db.materiaPrimaDao().deleteAll()
                db.productDao().deleteAllProducts()
                db.productoElaboradoDao().deleteAll()
                db.recetaIngredienteDao().deleteAll()
                db.tandaDao().deleteAllTandas()
                db.productionBatchDao().deleteAllBatches()
                db.movimientoMateriaPrimaDao().deleteAllMovimientosMateriaPrima()
                db.mercaderiaDao().deleteAllMercaderias()
                db.mercaderiaDao().deleteAllMovimientos()
                db.stockMovementDao().deleteAllMovements()
                db.categoryDao().deleteAllCategories()
                db.personalContratadoDao().deleteAllPersonal()
                db.userDao().deleteAllUsers()
                db.gastoGeneralDao().deleteAll()
                db.inversionDao().deleteAll()
                db.jornadaDao().deleteAllJornadas()
                db.tableOrderDao().deleteAllOrders()
                db.tableOrderDao().deleteAllOrderItems()
                db.transferenciaDao().deleteAllTransferencias()
                db.bitacoraDao().deleteAllEntries()
                db.consumoPersonalDao().deleteAllConsumoPersonal()
                db.paymentProposalDao().deleteAllProposals()
                db.smsComandaQueueDao().deleteAll()

                // Insert Materias Primas
                (root.optJSONArray("materiasPrimas") ?: b1?.optJSONArray("materiasPrimas"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.materiaPrimaDao().insert(
                            MateriaPrima(
                                id = o.getLong("id"),
                                name = o.getString("name"),
                                unit = o.optString("unit", "g"),
                                unitCost = o.optDouble("unitCost", 0.0),
                                isActive = o.optBoolean("isActive", true),
                                stock = o.optDouble("stock", 0.0),
                                initialStock = o.optDouble("initialStock", 0.0),
                                purchasePrice = o.optDouble("purchasePrice", 0.0),
                                purchaseUnit = o.optString("purchaseUnit", "g"),
                                purchaseQuantity = o.optDouble("purchaseQuantity", 1.0),
                                productId = if (o.isNull("productId")) null else o.getLong("productId"),
                                isAgregado = o.optBoolean("isAgregado", false),
                                rationQuantity = o.optDouble("rationQuantity", 0.0),
                                rationUnit = o.optString("rationUnit", ""),
                                suggestedPrice = o.optDouble("suggestedPrice", 0.0),
                                salePrice = o.optDouble("salePrice", 0.0),
                                stockEnVenta = o.optDouble("stockEnVenta", 0.0),
                                racionesEnVenta = o.optDouble("racionesEnVenta", 0.0),
                                purchaseMode = o.optString("purchaseMode", "POR UNIDAD"),
                                purchaseLotUnits = o.optDouble("purchaseLotUnits", 0.0),
                                purchaseLotQuantity = o.optDouble("purchaseLotQuantity", 1.0),
                                purchaseLotPrice = o.optDouble("purchaseLotPrice", 0.0)
                            )
                        )
                    }
                }

                // Insert Products
                (root.optJSONArray("products") ?: b2?.optJSONArray("products"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.productDao().insertProduct(
                            Product(
                                id = o.getLong("id"),
                                code = o.optString("code", ""),
                                name = o.getString("name"),
                                category = o.optString("category", "General"),
                                price = o.optDouble("price", o.optDouble("salePrice", 0.0)),
                                cost = o.optDouble("cost", 0.0),
                                stock = o.optInt("stock", 0),
                                minStock = o.optInt("minStock", 3),
                                destination = o.optString("destination", "COCINA"),
                                isAvailable = o.optBoolean("isAvailable", true),
                                description = o.optString("description", ""),
                                unitOfMeasure = o.optString("unitOfMeasure", "Unidad"),
                                imagePath = if (o.isNull("imagePath")) null else o.optString("imagePath"),
                                admitsAgregados = o.optBoolean("admitsAgregados", false),
                                agregadosList = o.optString("agregadosList", "[]"),
                                isConvertedToInsumo = o.optBoolean("isConvertedToInsumo", false),
                                presentacionesEspeciales = o.optString("presentacionesEspeciales", "[]")
                            )
                        )
                    }
                }

                // Insert Productos Elaborados
                (root.optJSONArray("productosElaborados") ?: b2?.optJSONArray("productosElaborados"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.productoElaboradoDao().insert(
                            ProductoElaborado(
                                id = o.getLong("id"),
                                productId = o.getLong("productId"),
                                isActive = o.optBoolean("isActive", true),
                                recipeName = o.optString("recipeName", ""),
                                productionUnit = o.optString("productionUnit", "unidades"),
                                baseYield = o.optDouble("baseYield", 1.0),
                                baseMateriaPrimaId = o.optLong("baseMateriaPrimaId", 0L),
                                baseQuantity = o.optDouble("baseQuantity", 0.0),
                                estimatedDailyQuantity = o.optDouble("estimatedDailyQuantity", 10.0),
                                ppd = o.optDouble("ppd", 10.0),
                                precioDefinitivo = o.optDouble("precioDefinitivo", 0.0),
                                hasPrecioDefinitivo = o.optBoolean("hasPrecioDefinitivo", false),
                                targetMarginPct = o.optDouble("targetMarginPct", 30.0),
                                pagoCocinaUnitario = o.optDouble("pagoCocinaUnitario", 0.0),
                                cantidadCocineros = o.optInt("cantidadCocineros", 1),
                                isPagoCocinaFijo = o.optBoolean("isPagoCocinaFijo", false),
                                pagoDependienteUnitario = o.optDouble("pagoDependienteUnitario", 0.0),
                                pagoCajeroUnitario = o.optDouble("pagoCajeroUnitario", 0.0)
                            )
                        )
                    }
                }

                // Insert Receta Ingredientes
                (root.optJSONArray("recetaIngredientes") ?: b2?.optJSONArray("recetaIngredientes"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.recetaIngredienteDao().insert(
                            RecetaIngrediente(
                                id = o.getLong("id"),
                                productoElaboradoId = o.getLong("productoElaboradoId"),
                                materiaPrimaId = o.getLong("materiaPrimaId"),
                                quantity = o.getDouble("quantity"),
                                unit = o.optString("unit", "g")
                            )
                        )
                    }
                }

                // Insert Tandas (Ajustes -> Gestión -> Inventario -> Tandas)
                (root.optJSONArray("tandas") ?: b3?.optJSONArray("tandas") ?: b3?.optJSONArray("tandasCerradas"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.tandaDao().insert(
                            Tanda(
                                id = o.optLong("id", 0L),
                                uuid = o.optString("uuid", ""),
                                productId = o.optLong("productId", 0L),
                                productName = o.optString("productName", ""),
                                date = o.optLong("date", System.currentTimeMillis()),
                                responsibleUser = o.optString("responsibleUser", ""),
                                baseMateriaPrimaId = o.optLong("baseMateriaPrimaId", 0L),
                                baseMateriaPrimaName = o.optString("baseMateriaPrimaName", ""),
                                baseQuantityUsed = o.optDouble("baseQuantityUsed", 0.0),
                                baseQuantityUnit = o.optString("baseQuantityUnit", "g"),
                                productionFactor = o.optDouble("productionFactor", 1.0),
                                estimatedYield = o.optDouble("estimatedYield", 0.0),
                                productionUnit = o.optString("productionUnit", "unidades"),
                                ingredientsConsumedText = o.optString("ingredientsConsumedText", ""),
                                status = o.optString("status", "ACTIVADA"),
                                jornada = o.optString("jornada", "Jornada Unica"),
                                jornadaId = o.optLong("jornadaId", 0L),
                                observation = o.optString("observation", ""),
                                laborCostType = o.optString("laborCostType", "NINGUNO"),
                                laborCostValue = o.optDouble("laborCostValue", 0.0),
                                totalLaborCost = o.optDouble("totalLaborCost", 0.0),
                                totalDirectIngredientsCost = o.optDouble("totalDirectIngredientsCost", 0.0),
                                totalIndirectCostAllocated = o.optDouble("totalIndirectCostAllocated", 0.0),
                                totalBatchCost = o.optDouble("totalBatchCost", 0.0),
                                realUnitCost = o.optDouble("realUnitCost", 0.0),
                                tandaNumber = o.optString("tandaNumber", "01"),
                                expectedYield = o.optDouble("expectedYield", 0.0),
                                actualYield = o.optDouble("actualYield", 0.0),
                                yieldPercentage = o.optDouble("yieldPercentage", 100.0),
                                expectedRevenue = o.optDouble("expectedRevenue", 0.0),
                                estimatedProfit = o.optDouble("estimatedProfit", 0.0),
                                profitMargin = o.optDouble("profitMargin", 0.0),
                                inventoryDeducted = o.optBoolean("inventoryDeducted", true),
                                ownerPayType = o.optString("ownerPayType", "NINGUNO"),
                                ownerPayValue = o.optDouble("ownerPayValue", 0.0),
                                totalOwnerPay = o.optDouble("totalOwnerPay", 0.0),
                                quantitySold = o.optDouble("quantitySold", 0.0),
                                salePrice = o.optDouble("salePrice", 0.0),
                                realRevenue = o.optDouble("realRevenue", 0.0),
                                deviceId = o.optString("deviceId", "DISPOSITIVO-LOCAL"),
                                specialPresentationName = o.optString("specialPresentationName", ""),
                                specialPresentationQty = o.optDouble("specialPresentationQty", 0.0),
                                specialPresentationEquivalence = o.optDouble("specialPresentationEquivalence", 1.0)
                            )
                        )
                    }
                }

                // Insert Production Batches
                (root.optJSONArray("productionBatches") ?: b3?.optJSONArray("productionBatches"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.productionBatchDao().insertBatch(
                            ProductionBatch(
                                id = o.getLong("id"),
                                itemName = o.optString("itemName", o.optString("productName", "")),
                                quantity = o.optInt("quantity", o.optDouble("quantityProduced", 1.0).toInt()),
                                destination = o.optString("destination", "COCINA"),
                                notes = o.optString("notes", ""),
                                createdBy = o.optString("createdBy", o.optString("responsibleUsername", "admin")),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                                status = o.optString("status", "COMPLETADO")
                            )
                        )
                    }
                }

                // Insert Movimientos Materia Prima
                (root.optJSONArray("movimientosMateriaPrima") ?: b1?.optJSONArray("movimientosMateriaPrima"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.movimientoMateriaPrimaDao().insert(
                            MovimientoMateriaPrima(
                                id = o.optLong("id", 0L),
                                materiaPrimaId = o.optLong("materiaPrimaId", 0L),
                                materiaPrimaName = o.optString("materiaPrimaName", ""),
                                type = o.optString("type", "ENTRADA"),
                                quantity = o.optDouble("quantity", 0.0),
                                unit = o.optString("unit", "g"),
                                date = o.optLong("date", System.currentTimeMillis()),
                                responsibleUser = o.optString("responsibleUser", ""),
                                notes = o.optString("notes", ""),
                                resultingStock = o.optDouble("resultingStock", 0.0)
                            )
                        )
                    }
                }

                // Insert Mercaderias
                (root.optJSONArray("mercaderias") ?: b2?.optJSONArray("mercaderias"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.mercaderiaDao().insert(
                            Mercaderia(
                                id = o.getLong("id"),
                                productId = o.getLong("productId"),
                                acquisitionCost = o.optDouble("acquisitionCost", o.optDouble("purchasePrice", 0.0)),
                                unitOfMeasure = o.optString("unitOfMeasure", o.optString("unit", "unidades")),
                                initialStock = o.optDouble("initialStock", o.optDouble("stockCentral", 0.0)),
                                isActive = o.optBoolean("isActive", true),
                                directExpenses = o.optDouble("directExpenses", 0.0),
                                directExpensesDetails = o.optString("directExpensesDetails", "[]"),
                                purchaseMode = o.optString("purchaseMode", "POR UNIDAD"),
                                purchasePrice = o.optDouble("purchasePrice", 0.0),
                                unitsPerLot = o.optDouble("unitsPerLot", 1.0),
                                dailySalesAverage = o.optDouble("dailySalesAverage", 1.0)
                            )
                        )
                    }
                }

                // Insert Movimientos Mercaderia
                (root.optJSONArray("movimientosMercaderia") ?: b2?.optJSONArray("movimientosMercaderia"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.mercaderiaDao().insertMovimiento(
                            MovimientoMercaderia(
                                id = o.optLong("id", 0L),
                                mercaderiaId = o.optLong("mercaderiaId", 0L),
                                type = o.optString("type", "ENTRADA"),
                                quantity = o.optDouble("quantity", 0.0),
                                date = o.optLong("date", System.currentTimeMillis()),
                                responsibleAdmin = o.optString("responsibleAdmin", ""),
                                notes = o.optString("notes", ""),
                                quantitySold = o.optDouble("quantitySold", 0.0),
                                salePrice = o.optDouble("salePrice", 0.0),
                                acquisitionCost = o.optDouble("acquisitionCost", 0.0),
                                realRevenue = o.optDouble("realRevenue", 0.0),
                                jornadaId = o.optLong("jornadaId", 0L),
                                deviceId = o.optString("deviceId", "DISPOSITIVO-LOCAL")
                            )
                        )
                    }
                }

                // Insert Stock Movements
                (root.optJSONArray("stockMovements") ?: b2?.optJSONArray("stockMovements"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.stockMovementDao().insertMovement(
                            StockMovement(
                                id = o.getLong("id"),
                                productId = o.getLong("productId"),
                                productName = o.getString("productName"),
                                type = o.getString("type"),
                                quantity = o.getInt("quantity"),
                                reason = o.optString("reason", ""),
                                recordedBy = o.optString("recordedBy", o.optString("userUsername", "")),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                                jornadaId = o.optLong("jornadaId", 0L)
                            )
                        )
                    }
                }

                // Insert Categories
                (root.optJSONArray("categories") ?: b2?.optJSONArray("categories"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.categoryDao().insertCategory(
                            Category(
                                id = o.getLong("id"),
                                name = o.getString("name"),
                                description = o.optString("description", ""),
                                isActive = o.optBoolean("isActive", true)
                            )
                        )
                    }
                }

                // Insert Personal Contratado
                (root.optJSONArray("personalContratado") ?: b5?.optJSONArray("personalContratado"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.personalContratadoDao().insertPersonal(
                            PersonalContratado(
                                id = o.getLong("id"),
                                nombreCompleto = o.getString("nombreCompleto"),
                                carnetIdentidad = o.optString("carnetIdentidad", ""),
                                movil = o.optString("movil", o.optString("telefono", "")),
                                formaPago = o.optString("formaPago", "DIARIO"),
                                fechaRegistro = o.optLong("fechaRegistro", System.currentTimeMillis()),
                                tieneAccesoApp = o.optBoolean("tieneAccesoApp", true),
                                username = o.optString("username", ""),
                                passwordHash = o.optString("passwordHash", ""),
                                passwordPlain = o.optString("passwordPlain", ""),
                                role = o.optString("role", o.optString("cargoRol", "CAJERO")),
                                dependienteTipo = o.optString("dependienteTipo", "SALON"),
                                montoPorProducto = o.optDouble("montoPorProducto", 0.0),
                                isActive = o.optBoolean("isActive", o.optBoolean("activo", true)),
                                permisoProduccion = o.optBoolean("permisoProduccion", true),
                                permisoMercancias = o.optBoolean("permisoMercancias", true),
                                permisoPersonal = o.optBoolean("permisoPersonal", true),
                                permisoControlNegocio = o.optBoolean("permisoControlNegocio", false)
                            )
                        )
                    }
                }

                // Insert Users (Excluding SuperAdmin)
                (root.optJSONArray("users") ?: b5?.optJSONArray("users"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val roleStr = o.optString("role", "DUENO")
                        val username = o.getString("username")
                        if (username.equals("superadmin", ignoreCase = true) || roleStr.equals("SUPERADMIN", ignoreCase = true)) {
                            continue
                        }
                        val userRole = try {
                            UserRole.valueOf(roleStr.uppercase())
                        } catch (e: Exception) {
                            UserRole.DUENO
                        }
                        db.userDao().insertUser(
                            User(
                                username = username,
                                fullName = o.optString("fullName", username),
                                passwordHash = o.optString("passwordHash", "1234".toSha256()),
                                role = userRole,
                                montoPorProducto = o.optDouble("montoPorProducto", 0.0),
                                isActive = o.optBoolean("isActive", true),
                                authorizedDeviceId = if (o.isNull("authorizedDeviceId")) null else o.optString("authorizedDeviceId"),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                telefono = o.optString("telefono", ""),
                                permisoProduccion = o.optBoolean("permisoProduccion", true),
                                permisoMercancias = o.optBoolean("permisoMercancias", true),
                                permisoPersonal = o.optBoolean("permisoPersonal", true),
                                permisoControlNegocio = o.optBoolean("permisoControlNegocio", true)
                            )
                        )
                    }
                }

                // Insert Gastos Generales
                (root.optJSONArray("gastosGenerales") ?: b5?.optJSONArray("gastosGenerales"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.gastoGeneralDao().insert(
                            GastoGeneral(
                                id = o.getLong("id"),
                                name = o.getString("name"),
                                description = o.optString("description", ""),
                                amount = o.getDouble("amount"),
                                period = o.optString("period", o.optString("frequency", "MENSUAL")),
                                periodDays = o.optInt("periodDays", 30),
                                category = o.optString("category", "Otros"),
                                isActive = o.optBoolean("isActive", true),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                inversionId = if (o.isNull("inversionId")) null else o.getLong("inversionId"),
                                targetProductId = if (o.isNull("targetProductId")) null else o.getLong("targetProductId"),
                                targetProductIds = if (o.isNull("targetProductIds")) null else o.optString("targetProductIds"),
                                startDate = if (o.isNull("startDate")) null else o.getLong("startDate"),
                                endDate = if (o.isNull("endDate")) null else o.getLong("endDate"),
                                scope = o.optString("scope", "PRODUCCION")
                            )
                        )
                    }
                }

                // Insert Inversiones
                (root.optJSONArray("inversiones") ?: b5?.optJSONArray("inversiones"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.inversionDao().insert(
                            Inversion(
                                id = o.getLong("id"),
                                name = o.getString("name"),
                                category = o.optString("category", "Equipamiento"),
                                amount = o.getDouble("amount"),
                                date = o.optLong("date", System.currentTimeMillis()),
                                usefulLife = o.optDouble("usefulLife", 12.0),
                                usefulLifeUnit = o.optString("usefulLifeUnit", "MESES"),
                                observation = o.optString("observation", ""),
                                targetProductId = if (o.isNull("targetProductId")) null else o.getLong("targetProductId"),
                                targetProductIds = if (o.isNull("targetProductIds")) null else o.optString("targetProductIds"),
                                startDate = if (o.isNull("startDate")) null else o.getLong("startDate"),
                                endDate = if (o.isNull("endDate")) null else o.getLong("endDate"),
                                scope = o.optString("scope", "PRODUCCION"),
                                currency = o.optString("currency", "CUP"),
                                originalAmount = o.optDouble("originalAmount", o.getDouble("amount")),
                                exchangeRate = o.optDouble("exchangeRate", 1.0),
                                convertedAmount = o.optDouble("convertedAmount", o.getDouble("amount")),
                                method = o.optString("method", "VIDA_UTIL"),
                                dailyAmount = o.optDouble("dailyAmount", 0.0)
                            )
                        )
                    }
                }

                // Insert Jornadas
                (root.optJSONArray("jornadas") ?: b6?.optJSONArray("jornadas"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.jornadaDao().insertJornada(
                            Jornada(
                                id = o.getLong("id"),
                                openedAt = o.getLong("openedAt"),
                                closedAt = if (o.isNull("closedAt")) null else o.getLong("closedAt"),
                                initialCash = o.optDouble("initialCash", 0.0),
                                finalCash = o.optDouble("finalCash", 0.0),
                                totalSales = o.optDouble("totalSales", 0.0),
                                totalExpenses = o.optDouble("totalExpenses", 0.0),
                                expectedCash = o.optDouble("expectedCash", 0.0),
                                cashDifference = o.optDouble("cashDifference", 0.0),
                                notes = o.optString("notes", ""),
                                isOpen = o.optBoolean("isOpen", false),
                                openedBy = o.optString("openedBy", "admin"),
                                closedBy = if (o.isNull("closedBy")) null else o.optString("closedBy"),
                                utilidadSalonMontoUnitario = o.optDouble("utilidadSalonMontoUnitario", 0.0),
                                deviceId = o.optString("deviceId", "DISPOSITIVO-LOCAL"),
                                realSalesProduccion = o.optDouble("realSalesProduccion", 0.0),
                                realCostProduccion = o.optDouble("realCostProduccion", 0.0),
                                gastosProduccion = o.optDouble("gastosProduccion", 0.0),
                                inversionesProduccion = o.optDouble("inversionesProduccion", 0.0),
                                resultadoProduccion = o.optDouble("resultadoProduccion", 0.0),
                                realSalesMercaderias = o.optDouble("realSalesMercaderias", 0.0),
                                realCostMercaderias = o.optDouble("realCostMercaderias", 0.0),
                                gastosMercaderias = o.optDouble("gastosMercaderias", 0.0),
                                inversionesMercaderias = o.optDouble("inversionesMercaderias", 0.0),
                                resultadoMercaderias = o.optDouble("resultadoMercaderias", 0.0),
                                totalIngresos = o.optDouble("totalIngresos", 0.0),
                                totalCostos = o.optDouble("totalCostos", 0.0),
                                totalGastos = o.optDouble("totalGastos", 0.0),
                                totalInversiones = o.optDouble("totalInversiones", 0.0),
                                utilidadDelDia = o.optDouble("utilidadDelDia", 0.0),
                                modulosUtilizados = o.optString("modulosUtilizados", "PRODUCCION,MERCADERIAS"),
                                snapshotJson = o.optString("snapshotJson", ""),
                                extracciones = o.optDouble("extracciones", 0.0),
                                liquidezFinal = o.optDouble("liquidezFinal", 0.0)
                            )
                        )
                    }
                }

                // Insert Table Orders
                (root.optJSONArray("tableOrders") ?: b6?.optJSONArray("tableOrders"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.tableOrderDao().insertOrder(
                            TableOrder(
                                id = o.getLong("id"),
                                tableNumber = if (o.isNull("tableNumber")) null else o.getInt("tableNumber"),
                                customerName = o.optString("customerName", ""),
                                waiterUsername = o.optString("waiterUsername", ""),
                                createdAt = o.getLong("createdAt"),
                                closedAt = if (o.isNull("closedAt")) null else o.getLong("closedAt"),
                                status = o.optString("status", "COBRADA"),
                                totalAmount = o.optDouble("totalAmount", 0.0),
                                paymentMethod = o.optString("paymentMethod", "EFECTIVO"),
                                tip = o.optDouble("tip", 0.0),
                                jornadaId = o.optLong("jornadaId", 0L),
                                comandaNumber = o.optInt("comandaNumber", 0),
                                cocinaNumber = o.optInt("cocinaNumber", 0),
                                barraNumber = o.optInt("barraNumber", 0),
                                totalCocina = o.optDouble("totalCocina", 0.0),
                                totalBarra = o.optDouble("totalBarra", 0.0),
                                cashReceived = o.optDouble("cashReceived", 0.0),
                                changeGiven = o.optDouble("changeGiven", 0.0),
                                confirmedAt = if (o.isNull("confirmedAt")) null else o.optLong("confirmedAt"),
                                servedAt = if (o.isNull("servedAt")) null else o.optLong("servedAt"),
                                serviceDurationSeconds = o.optLong("serviceDurationSeconds", 0L),
                                currency = o.optString("currency", "CUP"),
                                exchangeRate = o.optDouble("exchangeRate", 1.0),
                                originalAmount = o.optDouble("originalAmount", o.optDouble("totalAmount", 0.0)),
                                amountInCurrency = o.optDouble("amountInCurrency", o.optDouble("totalAmount", 0.0))
                            )
                        )
                    }
                }

                // Insert Order Items
                (root.optJSONArray("orderItems") ?: b6?.optJSONArray("orderItems"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.tableOrderDao().insertOrderItem(
                            OrderItem(
                                id = o.getLong("id"),
                                orderId = o.getLong("orderId"),
                                productId = o.getLong("productId"),
                                productName = o.getString("productName"),
                                unitPrice = o.getDouble("unitPrice"),
                                quantity = o.getInt("quantity"),
                                destination = o.optString("destination", "COCINA"),
                                notes = o.optString("notes", ""),
                                status = o.optString("status", "ENTREGADO"),
                                productCode = o.optString("productCode", "")
                            )
                        )
                    }
                }

                // Insert Transferencias
                (root.optJSONArray("transferencias") ?: b6?.optJSONArray("transferencias"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.transferenciaDao().insertTransferencias(
                            listOf(
                                Transferencia(
                                    id = o.getLong("id"),
                                    transactionNumber = o.getString("transactionNumber"),
                                    jornadaId = o.optLong("jornadaId", 0L),
                                    comandaId = if (o.isNull("comandaId")) null else o.getLong("comandaId"),
                                    comandaNumber = if (o.isNull("comandaNumber")) null else o.getInt("comandaNumber"),
                                    amount = o.optDouble("amount", 0.0),
                                    currency = o.optString("currency", "CUP"),
                                    phoneNumber = o.optString("phoneNumber", ""),
                                    titularName = o.optString("titularName", ""),
                                    titularCi = o.optString("titularCi", ""),
                                    recipientAccount = o.optString("recipientAccount", ""),
                                    smsDate = o.optString("smsDate", ""),
                                    receivedAt = o.optLong("receivedAt", System.currentTimeMillis()),
                                    cajeroUsername = o.optString("cajeroUsername", ""),
                                    status = o.optString("status", "CONFIRMADA"),
                                    rawSmsBody = o.optString("rawSmsBody", ""),
                                    isManual = o.optBoolean("isManual", false),
                                    source = o.optString("source", "MANUAL")
                                )
                            )
                        )
                    }
                }

                // Insert Bitacora Entries
                (root.optJSONArray("bitacoraEntries") ?: b6?.optJSONArray("bitacoraEntries"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.bitacoraDao().insertEntry(
                            BitacoraEntry(
                                id = o.getLong("id"),
                                title = o.getString("title"),
                                content = o.getString("content"),
                                category = o.optString("category", "GENERAL"),
                                authorUsername = o.optString("authorUsername", "admin"),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                                priority = o.optString("priority", "NORMAL")
                            )
                        )
                    }
                }

                // Insert Consumo Personal
                (root.optJSONArray("consumoPersonalList") ?: b6?.optJSONArray("consumoPersonalList"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.consumoPersonalDao().insertConsumoPersonal(
                            ConsumoPersonalItem(
                                id = o.getLong("id"),
                                jornadaId = o.getLong("jornadaId"),
                                productId = o.getLong("productId"),
                                productName = o.getString("productName"),
                                unitPrice = o.getDouble("unitPrice"),
                                quantity = o.getInt("quantity"),
                                totalAmount = o.optDouble("totalAmount", o.getDouble("unitPrice") * o.getInt("quantity")),
                                recordedBy = o.optString("recordedBy", ""),
                                timestamp = o.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Insert Payment Proposals
                (root.optJSONArray("paymentProposals") ?: b5?.optJSONArray("paymentProposals"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val roleStr = o.optString("role", "DUENO")
                        val pRole = try {
                            UserRole.valueOf(roleStr.uppercase())
                        } catch (e: Exception) {
                            UserRole.DUENO
                        }
                        db.paymentProposalDao().insertProposal(
                            PaymentProposal(
                                username = o.getString("username"),
                                fullName = o.optString("fullName", ""),
                                role = pRole,
                                paymentAmount = o.getDouble("paymentAmount"),
                                paymentType = o.optString("paymentType", "FIJO"),
                                isActiveProposal = o.optBoolean("isActiveProposal", true),
                                lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Restore SmsComandaQueue
                (root.optJSONArray("smsComandaQueue") ?: b6?.optJSONArray("smsComandaQueue"))?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        db.smsComandaQueueDao().insertSmsComanda(
                            SmsComandaQueue(
                                id = o.optLong("id", 0L),
                                senderPhone = o.optString("senderPhone", ""),
                                smsText = o.optString("smsText", ""),
                                receivedAt = o.optLong("receivedAt", System.currentTimeMillis()),
                                estado = o.optString("estado", "PENDIENTE"),
                                jornadaId = o.optLong("jornadaId", 0L),
                                comandaNumber = o.optInt("comandaNumber", 0)
                            )
                        )
                    }
                }

                // Restore ConfiguracionNegocio
                (root.optJSONObject("configuracionNegocio") ?: b4?.optJSONObject("configuracionNegocio") ?: b5?.optJSONObject("configuracionNegocio"))?.let { o ->
                    val existing = db.configuracionNegocioDao().getConfigSync()
                    val newConfig = ConfiguracionNegocio(
                        id = 1,
                        nombreNegocio = o.optString("nombreNegocio", existing?.nombreNegocio ?: "El Qadre POS"),
                        logoPath = if (o.isNull("logoPath")) existing?.logoPath else o.optString("logoPath", existing?.logoPath ?: ""),
                        direccion = o.optString("direccion", existing?.direccion ?: ""),
                        telefono = o.optString("telefono", existing?.telefono ?: ""),
                        codigoNegocio = o.optString("codigoNegocio", existing?.codigoNegocio ?: summary.codigoNegocio),
                        fechaCreacion = o.optLong("fechaCreacion", existing?.fechaCreacion ?: System.currentTimeMillis()),
                        fechaActualizacion = System.currentTimeMillis()
                    )
                    db.configuracionNegocioDao().insertConfig(newConfig)
                }

                // Restore ConfiguracionGeneral
                (root.optJSONObject("configuracionGeneral") ?: b4?.optJSONObject("configuracionGeneral"))?.let { o ->
                    val existing = db.configuracionGeneralDao().getConfigSync()
                    val newGen = ConfiguracionGeneral(
                        id = 1,
                        moneda = o.optString("moneda", existing?.moneda ?: "CUP"),
                        metodosPago = o.optString("metodosPago", existing?.metodosPago ?: "Efectivo"),
                        parametrosJornada = o.optString("parametrosJornada", existing?.parametrosJornada ?: ""),
                        denominacionesCaja = o.optString("denominacionesCaja", existing?.denominacionesCaja ?: "20000,10000,5000,2000,1000,500,200,100,50,20,10,5"),
                        tasaUsd = o.optDouble("tasaUsd", existing?.tasaUsd ?: 0.0),
                        tasaEur = o.optDouble("tasaEur", existing?.tasaEur ?: 0.0),
                        urlUsuariosJson = existing?.urlUsuariosJson ?: o.optString("urlUsuariosJson", ""),
                        lastUserUpdateDate = existing?.lastUserUpdateDate ?: 0L,
                        lastUserUpdateStatus = existing?.lastUserUpdateStatus ?: "PENDIENTE",
                        lastUserUpdateVersion = existing?.lastUserUpdateVersion ?: "",
                        urlCatalogoJson = existing?.urlCatalogoJson ?: o.optString("urlCatalogoJson", ""),
                        lastCatalogoUpdateDate = existing?.lastCatalogoUpdateDate ?: 0L,
                        lastCatalogoUpdateStatus = existing?.lastCatalogoUpdateStatus ?: "PENDIENTE",
                        lastCatalogoUpdateVersion = existing?.lastCatalogoUpdateVersion ?: "",
                        urlMercainvJson = existing?.urlMercainvJson ?: o.optString("urlMercainvJson", ""),
                        lastMercainvUpdateDate = existing?.lastMercainvUpdateDate ?: 0L,
                        lastMercainvUpdateStatus = existing?.lastMercainvUpdateStatus ?: "PENDIENTE",
                        lastMercainvUpdateVersion = existing?.lastMercainvUpdateVersion ?: "",
                        telefonoDueno = o.optString("telefonoDueno", existing?.telefonoDueno ?: ""),
                        telefonoCajero = o.optString("telefonoCajero", existing?.telefonoCajero ?: ""),
                        telefonoAdmin = o.optString("telefonoAdmin", existing?.telefonoAdmin ?: ""),
                        urlQDuenoJson = existing?.urlQDuenoJson ?: o.optString("urlQDuenoJson", ""),
                        urlVersionJson = existing?.urlVersionJson ?: o.optString("urlVersionJson", ""),
                        lastQDuenoUpdateDate = existing?.lastQDuenoUpdateDate ?: 0L,
                        lastQDuenoUpdateStatus = existing?.lastQDuenoUpdateStatus ?: "PENDIENTE",
                        lastQDuenoUpdateVersion = existing?.lastQDuenoUpdateVersion ?: "",
                        lastQDuenoUpdateFechaPublicacion = existing?.lastQDuenoUpdateFechaPublicacion ?: ""
                    )
                    db.configuracionGeneralDao().insertConfig(newGen)
                }
            }

            // Restore Preferencias Locales y Parámetros
            (root.optJSONObject("preferenciasLocales") ?: b4?.optJSONObject("preferenciasLocales"))?.let { pObj ->
                // 1. Tarifas Pago Bebidas
                pObj.optJSONObject("tarifasPagoBebidas")?.let { tObj ->
                    val tarifas = TarifasPagoBebidas(
                        pagoDependientePorUnidad = tObj.optDouble("pagoDependientePorUnidad", 0.0),
                        pagoCajeroPorUnidad = tObj.optDouble("pagoCajeroPorUnidad", 0.0),
                        pagoDependienteModalidad = tObj.optString("pagoDependienteModalidad", "FIJO"),
                        pagoDependienteValor = tObj.optDouble("pagoDependienteValor", 0.0),
                        pagoCajeroModalidad = tObj.optString("pagoCajeroModalidad", "FIJO"),
                        pagoCajeroValor = tObj.optDouble("pagoCajeroValor", 0.0)
                    )
                    BebidasTarifasPreferences.saveTarifas(context, tarifas)
                }

                // 2. Modos Producción
                pObj.optJSONArray("modosProduccion")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val mObj = arr.getJSONObject(i)
                        val pId = mObj.optLong("productId", 0L)
                        val modo = mObj.optString("modo", "POR_TANDAS")
                        if (pId > 0) {
                            ProduccionModoHelper.setModo(context, pId, modo)
                        }
                    }
                }

                // 3. Cuadre Pagos
                pObj.optJSONArray("cuadrePagos")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val jId = obj.optLong("jornadaId", 0L)
                        if (jId > 0) {
                            val depsArray = obj.optJSONArray("dependientes") ?: JSONArray()
                            val depsList = mutableListOf<DependientePagoDistribucion>()
                            for (j in 0 until depsArray.length()) {
                                val dObj = depsArray.getJSONObject(j)
                                depsList.add(
                                    DependientePagoDistribucion(
                                        id = dObj.optInt("id", j + 1),
                                        name = dObj.optString("name", "Dependiente ${j + 1}"),
                                        username = dObj.optString("username", ""),
                                        ventasProduccion = dObj.optDouble("ventasProduccion", 0.0),
                                        ventasBebidas = dObj.optDouble("ventasBebidas", 0.0),
                                        ventasTotales = dObj.optDouble("ventasTotales", 0.0),
                                        montoPago = dObj.optDouble("montoPago", 0.0)
                                    )
                                )
                            }
                            val cpj = CuadrePagosJornada(
                                jornadaId = jId,
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
                                cantidadCocineros = obj.optInt("cantidadCocineros", 1),
                                spaguettiPago = obj.optDouble("spaguettiPago", 0.0)
                            )
                            CuadrePagosManager.savePagosJornada(context, cpj)
                        }
                    }
                }

                // 4. Clasificación Transferencias
                pObj.optJSONArray("clasificacionTransferencias")?.let { arr ->
                    val clasifPrefs = context.getSharedPreferences("clasificacion_transferencias_prefs", Context.MODE_PRIVATE)
                    val editor = clasifPrefs.edit()
                    for (i in 0 until arr.length()) {
                        val cObj = arr.getJSONObject(i)
                        val jId = cObj.optLong("jornadaId", 0L)
                        val clasif = cObj.optString("clasificacion", "")
                        if (jId > 0 && clasif.isNotBlank()) {
                            editor.putString("clasif_jornada_$jId", clasif)
                        }
                    }
                    editor.apply()
                }

                // 5. Módulos Visibles Dueño
                val modulosArray = pObj.optJSONArray("modulosVisiblesDueno") ?: pObj.optJSONArray("modosVisiblesDueno")
                modulosArray?.let { arr ->
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    if (set.isNotEmpty()) {
                        DuenoSessionPreferences.setVisibleModules(context, null, set)
                        DuenoSessionPreferences.setVisibleModules(context, "dueno", set)
                    }
                }

                // 6. Límites de Transferencia para Salón por Producto
                pObj.optJSONObject("limitesTransferenciaSalon")?.let { stObj ->
                    val salonTransferPrefs = context.getSharedPreferences("SalonTransferPrefs", Context.MODE_PRIVATE)
                    val editor = salonTransferPrefs.edit()
                    editor.clear()
                    val keys = stObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        editor.putInt(k, stObj.optInt(k, 0))
                    }
                    editor.apply()
                }

                // 7. Borradores Activos de Cuadre de Caja (Legacy)
                pObj.optJSONArray("cuadreDrafts")?.let { dArr ->
                    val draftPrefs = context.getSharedPreferences("elqadre_cuadre_draft_prefs", Context.MODE_PRIVATE)
                    val editor = draftPrefs.edit()
                    for (i in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(i)
                        val jId = dObj.optLong("jornadaId", 0L)
                        if (jId > 0) {
                            editor.putString("draft_jornada_$jId", dObj.toString())
                        }
                    }
                    editor.apply()
                }

                // 8. Timestamps de Sincronización
                pObj.optJSONObject("jsonSyncTimestamps")?.let { sObj ->
                    val syncPrefs = context.getSharedPreferences("elqadre_json_sync", Context.MODE_PRIVATE)
                    val editor = syncPrefs.edit()
                    val keys = sObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        editor.putLong(k, sObj.optLong(k, 0L))
                    }
                    editor.apply()
                }
            }

            // Restore Bloque 7: Cuadre de Caja — Únicamente Mercaderías (Inicio y Entradas)
            b7?.let { blk7 ->
                val porJornada = blk7.optJSONArray("porJornada")
                if (porJornada != null && porJornada.length() > 0) {
                    for (i in 0 until porJornada.length()) {
                        val jObj = porJornada.getJSONObject(i)
                        val jId = jObj.optLong("jornadaId", 0L)
                        val mercsArr = jObj.optJSONArray("mercaderias")
                        if (jId > 0 && mercsArr != null) {
                            val draft = CuadreDraftManager.getDraft(context, jId) ?: CuadreDraftData(jornadaId = jId)
                            val updatedMercDrafts = draft.mercaderiaDrafts.toMutableList()
                            for (mIdx in 0 until mercsArr.length()) {
                                val mItem = mercsArr.getJSONObject(mIdx)
                                val mercId = mItem.optLong("mercaderiaId", 0L)
                                val inicio = mItem.optString("inicio", "0")
                                val entradas = mItem.optString("entradas", "0")
                                val existingIdx = updatedMercDrafts.indexOfFirst { it.mercaderiaId == mercId }
                                if (existingIdx >= 0) {
                                    val existing = updatedMercDrafts[existingIdx]
                                    updatedMercDrafts[existingIdx] = existing.copy(
                                        existenciaInicialStr = inicio,
                                        entradasStr = entradas
                                    )
                                } else {
                                    updatedMercDrafts.add(
                                        MercaderiaDraftItem(
                                            mercaderiaId = mercId,
                                            existenciaInicialStr = inicio,
                                            entradasStr = entradas
                                        )
                                    )
                                }
                            }
                            CuadreDraftManager.saveDraft(context, draft.copy(mercaderiaDrafts = updatedMercDrafts))
                        }
                    }
                } else {
                    val mercsArr = blk7.optJSONArray("mercaderias")
                    if (mercsArr != null && mercsArr.length() > 0) {
                        val activeJornada = db.jornadaDao().getActiveJornadaSync()
                        val targetJId = activeJornada?.id ?: 1L
                        val draft = CuadreDraftManager.getDraft(context, targetJId) ?: CuadreDraftData(jornadaId = targetJId)
                        val updatedMercDrafts = draft.mercaderiaDrafts.toMutableList()
                        for (mIdx in 0 until mercsArr.length()) {
                            val mItem = mercsArr.getJSONObject(mIdx)
                            val mercId = mItem.optLong("mercaderiaId", 0L)
                            val inicio = mItem.optString("inicio", "0")
                            val entradas = mItem.optString("entradas", "0")
                            val existingIdx = updatedMercDrafts.indexOfFirst { it.mercaderiaId == mercId }
                            if (existingIdx >= 0) {
                                val existing = updatedMercDrafts[existingIdx]
                                updatedMercDrafts[existingIdx] = existing.copy(
                                    existenciaInicialStr = inicio,
                                    entradasStr = entradas
                                )
                            } else {
                                updatedMercDrafts.add(
                                    MercaderiaDraftItem(
                                        mercaderiaId = mercId,
                                        existenciaInicialStr = inicio,
                                        entradasStr = entradas
                                    )
                                )
                            }
                        }
                        CuadreDraftManager.saveDraft(context, draft.copy(mercaderiaDrafts = updatedMercDrafts))
                    }
                }
            }

            Result.success("Restauración completada con éxito. Se han reconstruido todos los datos operativos y de Ajustes del Negocio.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Error al restaurar los datos en base de datos: ${e.localizedMessage}"))
        }
    }

    suspend fun importLegacyProduccion(
        jsonString: String,
        db: AppDatabase,
        context: Context
    ): Result<LegacyImportSummary> {
        return try {
            val root = JSONObject(jsonString)
            
            // Validate that we have some production structure
            val hasMateriasPrimas = root.has("materiasPrimas") || root.has("insumos")
            val hasProducts = root.has("products") || root.has("productos")
            val hasElaborados = root.has("productosElaborados")
            val hasRecetas = root.has("recetaIngredientes")
            val hasCategories = root.has("categories")
            
            if (!hasMateriasPrimas && !hasProducts && !hasElaborados && !hasRecetas && !hasCategories) {
                return Result.failure(Exception("El JSON no contiene estructuras reconocibles de Producción."))
            }

            var insumosNuevos = 0
            var insumosActualizados = 0
            var productosNuevos = 0
            var productosActualizados = 0
            var recetasNuevas = 0
            var recetasActualizadas = 0
            var categoriasNuevas = 0
            var categoriasActualizadas = 0

            db.withTransaction {
                // 1. Categories
                val categoryArr = root.optJSONArray("categories")
                if (categoryArr != null) {
                    val existing = db.categoryDao().getAllCategoriesSync().associateBy { it.name.lowercase().trim() }
                    for (i in 0 until categoryArr.length()) {
                        val o = categoryArr.getJSONObject(i)
                        val name = o.getString("name")
                        val key = name.lowercase().trim()
                        val oldCat = existing[key]
                        if (oldCat == null) {
                            db.categoryDao().insertCategory(
                                Category(
                                    id = o.optLong("id", 0L),
                                    name = name,
                                    description = o.optString("description", ""),
                                    isActive = o.optBoolean("isActive", true)
                                )
                            )
                            categoriasNuevas++
                        } else {
                            db.categoryDao().insertCategory(
                                Category(
                                    id = oldCat.id,
                                    name = name,
                                    description = o.optString("description", oldCat.description),
                                    isActive = o.optBoolean("isActive", oldCat.isActive)
                                )
                            )
                            categoriasActualizadas++
                        }
                    }
                }

                // 2. MateriaPrima (Insumos)
                val mpsArr = root.optJSONArray("materiasPrimas") ?: root.optJSONArray("insumos")
                if (mpsArr != null) {
                    val existingMps = db.materiaPrimaDao().getAllSync().associateBy { it.name.lowercase().trim() }
                    for (i in 0 until mpsArr.length()) {
                        val o = mpsArr.getJSONObject(i)
                        val name = o.getString("name")
                        val key = name.lowercase().trim()
                        val oldMp = existingMps[key]
                        
                        val unit = o.optString("unit", o.optString("purchaseUnit", "g"))
                        val unitCost = o.optDouble("unitCost", 0.0)
                        val stock = o.optDouble("stock", o.optDouble("initialStock", 0.0))
                        val initialStock = o.optDouble("initialStock", stock)
                        val purchasePrice = o.optDouble("purchasePrice", 0.0)
                        val purchaseUnit = o.optString("purchaseUnit", unit)
                        val purchaseQuantity = o.optDouble("purchaseQuantity", 1.0)
                        val isActive = o.optBoolean("isActive", true)
                        
                        if (oldMp == null) {
                            db.materiaPrimaDao().insert(
                                MateriaPrima(
                                    id = o.optLong("id", 0L),
                                    name = name,
                                    unit = unit,
                                    unitCost = unitCost,
                                    isActive = isActive,
                                    stock = stock,
                                    initialStock = initialStock,
                                    purchasePrice = purchasePrice,
                                    purchaseUnit = purchaseUnit,
                                    purchaseQuantity = purchaseQuantity,
                                    productId = if (o.isNull("productId")) null else o.optLong("productId")
                                )
                            )
                            insumosNuevos++
                        } else {
                            db.materiaPrimaDao().insert(
                                MateriaPrima(
                                    id = oldMp.id,
                                    name = name,
                                    unit = unit,
                                    unitCost = unitCost,
                                    isActive = isActive,
                                    stock = oldMp.stock,
                                    initialStock = oldMp.initialStock,
                                    purchasePrice = purchasePrice,
                                    purchaseUnit = purchaseUnit,
                                    purchaseQuantity = purchaseQuantity,
                                    productId = if (o.isNull("productId")) oldMp.productId else o.optLong("productId")
                                )
                            )
                            insumosActualizados++
                        }
                    }
                }

                // 3. Products
                val prodArr = root.optJSONArray("products") ?: root.optJSONArray("productos")
                if (prodArr != null) {
                    val existingProducts = db.productDao().getAllProductsSync().associateBy { it.name.lowercase().trim() }
                    for (i in 0 until prodArr.length()) {
                        val o = prodArr.getJSONObject(i)
                        val name = o.getString("name")
                        val key = name.lowercase().trim()
                        val oldProd = existingProducts[key]
                        
                        val code = o.optString("code", "")
                        val category = o.optString("category", "General")
                        val price = o.optDouble("price", o.optDouble("salePrice", 0.0))
                        val cost = o.optDouble("cost", o.optDouble("costoTotalUnitario", 0.0))
                        val stock = o.optInt("stock", 0)
                        val minStock = o.optInt("minStock", 3)
                        val destination = o.optString("destination", "COCINA")
                        val isAvailable = o.optBoolean("isAvailable", true)
                        val description = o.optString("description", "")
                        val unitOfMeasure = o.optString("unitOfMeasure", "Unidad")
                        val admitsAgregados = o.optBoolean("admitsAgregados", false)
                        val agregadosList = o.optString("agregadosList", "[]")
                        val isConvertedToInsumo = o.optBoolean("isConvertedToInsumo", false)
                        val presentacionesEspeciales = o.optString("presentacionesEspeciales", "[]")

                        val prodId: Long
                        if (oldProd == null) {
                            prodId = db.productDao().insertProduct(
                                Product(
                                    id = o.optLong("id", 0L),
                                    code = code,
                                    name = name,
                                    category = category,
                                    price = price,
                                    cost = cost,
                                    stock = stock,
                                    minStock = minStock,
                                    destination = destination,
                                    isAvailable = isAvailable,
                                    description = description,
                                    unitOfMeasure = unitOfMeasure,
                                    imagePath = if (o.isNull("imagePath")) null else o.optString("imagePath"),
                                    admitsAgregados = admitsAgregados,
                                    agregadosList = agregadosList,
                                    isConvertedToInsumo = isConvertedToInsumo,
                                    presentacionesEspeciales = presentacionesEspeciales
                                )
                            )
                            productosNuevos++
                        } else {
                            prodId = oldProd.id
                            db.productDao().insertProduct(
                                Product(
                                    id = oldProd.id,
                                    code = code,
                                    name = name,
                                    category = category,
                                    price = price,
                                    cost = cost,
                                    stock = oldProd.stock,
                                    minStock = oldProd.minStock,
                                    destination = destination,
                                    isAvailable = isAvailable,
                                    description = description,
                                    unitOfMeasure = unitOfMeasure,
                                    imagePath = oldProd.imagePath,
                                    admitsAgregados = oldProd.admitsAgregados,
                                    agregadosList = oldProd.agregadosList,
                                    isConvertedToInsumo = oldProd.isConvertedToInsumo,
                                    presentacionesEspeciales = oldProd.presentacionesEspeciales
                                )
                            )
                            productosActualizados++
                        }

                        // Also process inline "receta" if it exists (legacy Q_produccion format)
                        val inlineReceta = o.optJSONArray("receta")
                        if (inlineReceta != null) {
                            // Find or create ProductoElaborado
                            val existingElab = db.productoElaboradoDao().getAllSync().find { it.productId == prodId }
                            val elabId = if (existingElab == null) {
                                db.productoElaboradoDao().insert(
                                    ProductoElaborado(
                                        productId = prodId,
                                        isActive = isAvailable,
                                        recipeName = "Receta $name",
                                        productionUnit = unitOfMeasure,
                                        baseYield = 1.0,
                                        baseMateriaPrimaId = 0L,
                                        baseQuantity = 0.0,
                                        ppd = o.optDouble("ppd", 10.0),
                                        precioDefinitivo = price,
                                        hasPrecioDefinitivo = price > 0.0
                                    )
                                )
                            } else {
                                existingElab.id
                            }

                            // Recreate ingredients safely
                            db.recetaIngredienteDao().deleteIngredientsForProduct(elabId)
                            for (j in 0 until inlineReceta.length()) {
                                val ingObj = inlineReceta.getJSONObject(j)
                                val mpId = ingObj.optLong("materiaPrimaId", 0L)
                                val qty = ingObj.optDouble("quantity", 0.0)
                                val unitStr = ingObj.optString("unit", "g")
                                if (mpId > 0 && qty > 0) {
                                    db.recetaIngredienteDao().insert(
                                        RecetaIngrediente(
                                            productoElaboradoId = elabId,
                                            materiaPrimaId = mpId,
                                            quantity = qty,
                                            unit = unitStr
                                        )
                                    )
                                }
                            }
                            recetasActualizadas++
                        }
                    }
                }

                // 4. ProductosElaborados
                val elabArr = root.optJSONArray("productosElaborados")
                if (elabArr != null) {
                    val existingElabs = db.productoElaboradoDao().getAllSync().associateBy { it.productId }
                    for (i in 0 until elabArr.length()) {
                        val o = elabArr.getJSONObject(i)
                        val productId = o.getLong("productId")
                        val oldElab = existingElabs[productId]
                        
                        val isActive = o.optBoolean("isActive", true)
                        val recipeName = o.optString("recipeName", "")
                        val productionUnit = o.optString("productionUnit", "unidades")
                        val baseYield = o.optDouble("baseYield", 1.0)
                        val baseMateriaPrimaId = o.optLong("baseMateriaPrimaId", 0L)
                        val baseQuantity = o.optDouble("baseQuantity", 0.0)
                        val estimatedDailyQuantity = o.optDouble("estimatedDailyQuantity", 10.0)
                        val ppd = o.optDouble("ppd", 10.0)
                        val precioDefinitivo = o.optDouble("precioDefinitivo", 0.0)
                        val hasPrecioDefinitivo = o.optBoolean("hasPrecioDefinitivo", false)
                        val targetMarginPct = o.optDouble("targetMarginPct", 30.0)

                        if (oldElab == null) {
                            db.productoElaboradoDao().insert(
                                ProductoElaborado(
                                    id = o.optLong("id", 0L),
                                    productId = productId,
                                    isActive = isActive,
                                    recipeName = recipeName,
                                    productionUnit = productionUnit,
                                    baseYield = baseYield,
                                    baseMateriaPrimaId = baseMateriaPrimaId,
                                    baseQuantity = baseQuantity,
                                    estimatedDailyQuantity = estimatedDailyQuantity,
                                    ppd = ppd,
                                    precioDefinitivo = precioDefinitivo,
                                    hasPrecioDefinitivo = hasPrecioDefinitivo,
                                    targetMarginPct = targetMarginPct
                                )
                            )
                        } else {
                            db.productoElaboradoDao().insert(
                                ProductoElaborado(
                                    id = oldElab.id,
                                    productId = productId,
                                    isActive = isActive,
                                    recipeName = recipeName,
                                    productionUnit = productionUnit,
                                    baseYield = baseYield,
                                    baseMateriaPrimaId = baseMateriaPrimaId,
                                    baseQuantity = baseQuantity,
                                    estimatedDailyQuantity = estimatedDailyQuantity,
                                    ppd = ppd,
                                    precioDefinitivo = precioDefinitivo,
                                    hasPrecioDefinitivo = hasPrecioDefinitivo,
                                    targetMarginPct = targetMarginPct
                                )
                            )
                        }
                    }
                }

                // 5. RecetaIngredientes
                val recArr = root.optJSONArray("recetaIngredientes")
                if (recArr != null) {
                    val existingRecetas = db.recetaIngredienteDao().getAllIngredientsSync()
                    for (i in 0 until recArr.length()) {
                        val o = recArr.getJSONObject(i)
                        val peId = o.getLong("productoElaboradoId")
                        val mpId = o.getLong("materiaPrimaId")
                        val qty = o.getDouble("quantity")
                        val unit = o.optString("unit", "g")

                        val duplicate = existingRecetas.find { it.productoElaboradoId == peId && it.materiaPrimaId == mpId }
                        if (duplicate == null) {
                            db.recetaIngredienteDao().insert(
                                RecetaIngrediente(
                                    id = o.optLong("id", 0L),
                                    productoElaboradoId = peId,
                                    materiaPrimaId = mpId,
                                    quantity = qty,
                                    unit = unit
                                )
                            )
                            recetasNuevas++
                        } else {
                            db.recetaIngredienteDao().insert(
                                RecetaIngrediente(
                                    id = duplicate.id,
                                    productoElaboradoId = peId,
                                    materiaPrimaId = mpId,
                                    quantity = qty,
                                    unit = unit
                                )
                            )
                            recetasActualizadas++
                        }
                    }
                }
            }

            Result.success(
                LegacyImportSummary(
                    insumosNuevos = insumosNuevos,
                    insumosActualizados = insumosActualizados,
                    productosNuevos = productosNuevos,
                    productosActualizados = productosActualizados,
                    recetasNuevas = recetasNuevas,
                    recetasActualizadas = recetasActualizadas,
                    categoriasNuevas = categoriasNuevas,
                    categoriasActualizadas = categoriasActualizadas
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLegacyProduccionSummaryText(jsonString: String): Result<String> {
        return try {
            val root = JSONObject(jsonString)
            val hasMateriasPrimas = root.has("materiasPrimas") || root.has("insumos")
            val hasProducts = root.has("products") || root.has("productos")
            val hasElaborados = root.has("productosElaborados")
            val hasRecetas = root.has("recetaIngredientes")
            val hasCategories = root.has("categories")
            
            if (!hasMateriasPrimas && !hasProducts && !hasElaborados && !hasRecetas && !hasCategories) {
                return Result.failure(Exception("El JSON no contiene estructuras reconocibles de Producción."))
            }

            val mpsCount = root.optJSONArray("materiasPrimas")?.length() ?: root.optJSONArray("insumos")?.length() ?: 0
            val prodsCount = root.optJSONArray("products")?.length() ?: root.optJSONArray("productos")?.length() ?: 0
            val elabsCount = root.optJSONArray("productosElaborados")?.length() ?: 0
            val recsCount = root.optJSONArray("recetaIngredientes")?.length() ?: 0
            val catsCount = root.optJSONArray("categories")?.length() ?: 0

            val summary = "Estructura de Producción detectada:\n" +
                    "• Insumos / Materias Primas: $mpsCount\n" +
                    "• Productos: $prodsCount\n" +
                    "• Productos Elaborados: $elabsCount\n" +
                    "• Ingredientes de Recetas: $recsCount\n" +
                    "• Categorías: $catsCount"
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
