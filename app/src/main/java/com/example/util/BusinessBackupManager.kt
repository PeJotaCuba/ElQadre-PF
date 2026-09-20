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
    val mercaderiasCount: Int,
    val categoriesCount: Int,
    val personalCount: Int,
    val usersCount: Int,
    val jornadasCount: Int,
    val ordersCount: Int,
    val gastosCount: Int,
    val inversionesCount: Int
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
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("codigoNegocio", currentBizCode)
        root.put("nombreNegocio", uiState.businessConfig?.nombreNegocio ?: "El Qadre POS")

        // Configuracion Negocio
        uiState.businessConfig?.let { cfg ->
            val obj = JSONObject().apply {
                put("id", cfg.id)
                put("nombreNegocio", cfg.nombreNegocio)
                put("direccion", cfg.direccion)
                put("telefono", cfg.telefono)
                put("codigoNegocio", currentBizCode)
            }
            root.put("configuracionNegocio", obj)
        }

        // Configuracion General
        uiState.generalConfig?.let { cfg ->
            val obj = JSONObject().apply {
                put("id", cfg.id)
                put("moneda", cfg.moneda)
                put("metodosPago", cfg.metodosPago)
                put("tasaUsd", cfg.tasaUsd)
                put("tasaEur", cfg.tasaEur)
                put("telefonoDueno", cfg.telefonoDueno)
                put("telefonoCajero", cfg.telefonoCajero)
                put("telefonoAdmin", cfg.telefonoAdmin)
            }
            root.put("configuracionGeneral", obj)
        }

        // Materias Primas (Insumos)
        val mpArr = JSONArray()
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
            }
            mpArr.put(obj)
        }
        root.put("materiasPrimas", mpArr)

        // Products
        val prodArr = JSONArray()
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
        }
        root.put("products", prodArr)

        // Productos Elaborados
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
            }
            peArr.put(obj)
        }
        root.put("productosElaborados", peArr)

        // Receta Ingredientes
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
        root.put("recetaIngredientes", riArr)

        // Production Batches (Tandas)
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
        root.put("productionBatches", pbArr)

        // Mercaderias
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
            }
            mercArr.put(obj)
        }
        root.put("mercaderias", mercArr)

        // Stock Movements
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
        root.put("stockMovements", smArr)

        // Categories
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
        root.put("categories", catArr)

        // Personal Contratado
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
        root.put("personalContratado", pcArr)

        // Users (Exclude SuperAdmin)
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
        root.put("users", userArr)

        // Gastos Generales
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
        root.put("gastosGenerales", ggArr)

        // Inversiones
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
            }
            invArr.put(obj)
        }
        root.put("inversiones", invArr)

        // Jornadas
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
            }
            jArr.put(obj)
        }
        root.put("jornadas", jArr)

        // Table Orders
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
                put("currency", o.currency)
                put("exchangeRate", o.exchangeRate)
                put("originalAmount", o.originalAmount)
                put("amountInCurrency", o.amountInCurrency)
            }
            oArr.put(obj)
        }
        root.put("tableOrders", oArr)

        // Order Items
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
        root.put("orderItems", oiArr)

        // Transferencias
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
        root.put("transferencias", trArr)

        // Bitacora
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
        root.put("bitacoraEntries", bitArr)

        // Consumo Personal
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
        root.put("consumoPersonalList", cpArr)

        // Payment Proposals
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
        root.put("paymentProposals", ppArr)

        return root.toString(2)
    }

    fun exportAndShareBackup(context: Context, uiState: MainUiState): Boolean {
        return try {
            val currentBizCode = BusinessCodeHelper.resolveBusinessCode(
                context = context,
                configNegocio = uiState.businessConfig,
                configGeneral = uiState.generalConfig
            )
            val fileName = "Q_${currentBizCode}respaldo.json"
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
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Completo Negocio Q_$currentBizCode")
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
            val backupBizCode = BusinessCodeHelper.formatCode(rawBizCode)
            val deviceBizCode = BusinessCodeHelper.resolveBusinessCode(context)

            // VALIDATE BUSINESS CODE MATCH
            if (!BusinessCodeHelper.matches(backupBizCode, deviceBizCode)) {
                return Result.failure(
                    Exception(
                        "RESPALDO RECHAZADO:\n\n" +
                        "El archivo pertenece al Negocio $backupBizCode, pero este dispositivo está autorizado únicamente para el Negocio $deviceBizCode.\n\n" +
                        "No se modificó ni eliminó ningún dato operativo."
                    )
                )
            }

            val nombreNegocio = root.optString("nombreNegocio", "El Qadre POS")
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
            val version = root.optString("version", "1")

            val mpCount = root.optJSONArray("materiasPrimas")?.length() ?: 0
            val prodCount = root.optJSONArray("products")?.length() ?: 0
            val peCount = root.optJSONArray("productosElaborados")?.length() ?: 0
            val riCount = root.optJSONArray("recetaIngredientes")?.length() ?: 0
            val mercCount = root.optJSONArray("mercaderias")?.length() ?: 0
            val catCount = root.optJSONArray("categories")?.length() ?: 0
            val pcCount = root.optJSONArray("personalContratado")?.length() ?: 0
            val userCount = root.optJSONArray("users")?.length() ?: 0
            val jCount = root.optJSONArray("jornadas")?.length() ?: 0
            val oCount = root.optJSONArray("tableOrders")?.length() ?: 0
            val ggCount = root.optJSONArray("gastosGenerales")?.length() ?: 0
            val invCount = root.optJSONArray("inversiones")?.length() ?: 0

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
                mercaderiasCount = mercCount,
                categoriesCount = catCount,
                personalCount = pcCount,
                usersCount = userCount,
                jornadasCount = jCount,
                ordersCount = oCount,
                gastosCount = ggCount,
                inversionesCount = invCount
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
        val text = "Respaldo Negocio ${s.codigoNegocio} - ${s.nombreNegocio}\n" +
                "Fecha: ${s.dateStr}\n\n" +
                "Contenido a restaurar:\n" +
                "• Insumos / Materias Primas: ${s.materiasPrimasCount}\n" +
                "• Productos de Cocina/Barra: ${s.productsCount}\n" +
                "• Fichas de Producto Elaborado: ${s.productosElaboradosCount}\n" +
                "• Ingredientes de Recetas: ${s.recetaIngredientesCount}\n" +
                "• Mercaderías: ${s.mercaderiasCount}\n" +
                "• Categorías: ${s.categoriesCount}\n" +
                "• Personal Contratado: ${s.personalCount}\n" +
                "• Cuentas de Usuarios: ${s.usersCount}\n" +
                "• Jornadas registradas: ${s.jornadasCount}\n" +
                "• Comandas registradas: ${s.ordersCount}\n" +
                "• Gastos Generales: ${s.gastosCount}\n" +
                "• Inversiones: ${s.inversionesCount}"
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

            db.withTransaction {
                // Clear existing business tables
                db.materiaPrimaDao().deleteAll()
                db.productDao().deleteAllProducts()
                db.productoElaboradoDao().deleteAll()
                db.recetaIngredienteDao().deleteAll()
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

                // Insert Materias Primas
                root.optJSONArray("materiasPrimas")?.let { arr ->
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
                                productId = if (o.isNull("productId")) null else o.getLong("productId")
                            )
                        )
                    }
                }

                // Insert Products
                root.optJSONArray("products")?.let { arr ->
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
                root.optJSONArray("productosElaborados")?.let { arr ->
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
                                targetMarginPct = o.optDouble("targetMarginPct", 30.0)
                            )
                        )
                    }
                }

                // Insert Receta Ingredientes
                root.optJSONArray("recetaIngredientes")?.let { arr ->
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

                // Insert Production Batches
                root.optJSONArray("productionBatches")?.let { arr ->
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

                // Insert Mercaderias
                root.optJSONArray("mercaderias")?.let { arr ->
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
                                unitsPerLot = o.optDouble("unitsPerLot", 1.0)
                            )
                        )
                    }
                }

                // Insert Stock Movements
                root.optJSONArray("stockMovements")?.let { arr ->
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
                root.optJSONArray("categories")?.let { arr ->
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
                root.optJSONArray("personalContratado")?.let { arr ->
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
                root.optJSONArray("users")?.let { arr ->
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
                root.optJSONArray("gastosGenerales")?.let { arr ->
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
                root.optJSONArray("inversiones")?.let { arr ->
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
                                convertedAmount = o.optDouble("convertedAmount", o.getDouble("amount"))
                            )
                        )
                    }
                }

                // Insert Jornadas
                root.optJSONArray("jornadas")?.let { arr ->
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
                                deviceId = o.optString("deviceId", "DISPOSITIVO-LOCAL")
                            )
                        )
                    }
                }

                // Insert Table Orders
                root.optJSONArray("tableOrders")?.let { arr ->
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
                                currency = o.optString("currency", "CUP"),
                                exchangeRate = o.optDouble("exchangeRate", 1.0),
                                originalAmount = o.optDouble("originalAmount", o.optDouble("totalAmount", 0.0)),
                                amountInCurrency = o.optDouble("amountInCurrency", o.optDouble("totalAmount", 0.0))
                            )
                        )
                    }
                }

                // Insert Order Items
                root.optJSONArray("orderItems")?.let { arr ->
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
                root.optJSONArray("transferencias")?.let { arr ->
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
                root.optJSONArray("bitacoraEntries")?.let { arr ->
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
                root.optJSONArray("consumoPersonalList")?.let { arr ->
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
                root.optJSONArray("paymentProposals")?.let { arr ->
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

                // Restore ConfiguracionNegocio without overriding commercial parameters
                root.optJSONObject("configuracionNegocio")?.let { o ->
                    val existing = db.configuracionNegocioDao().getConfigSync()
                    val newConfig = ConfiguracionNegocio(
                        id = 1,
                        nombreNegocio = o.optString("nombreNegocio", existing?.nombreNegocio ?: "El Qadre POS"),
                        logoPath = existing?.logoPath,
                        direccion = o.optString("direccion", existing?.direccion ?: ""),
                        telefono = o.optString("telefono", existing?.telefono ?: ""),
                        codigoNegocio = summary.codigoNegocio,
                        fechaCreacion = existing?.fechaCreacion ?: System.currentTimeMillis(),
                        fechaActualizacion = System.currentTimeMillis()
                    )
                    db.configuracionNegocioDao().insertConfig(newConfig)
                }

                // Restore ConfiguracionGeneral without overriding licensing URLs
                root.optJSONObject("configuracionGeneral")?.let { o ->
                    val existing = db.configuracionGeneralDao().getConfigSync()
                    val newGen = ConfiguracionGeneral(
                        id = 1,
                        moneda = o.optString("moneda", existing?.moneda ?: "CUP"),
                        metodosPago = o.optString("metodosPago", existing?.metodosPago ?: "Efectivo"),
                        parametrosJornada = existing?.parametrosJornada ?: "",
                        denominacionesCaja = existing?.denominacionesCaja ?: "20000,10000,5000,2000,1000,500,200,100,50,20,10,5",
                        tasaUsd = o.optDouble("tasaUsd", existing?.tasaUsd ?: 0.0),
                        tasaEur = o.optDouble("tasaEur", existing?.tasaEur ?: 0.0),
                        urlUsuariosJson = existing?.urlUsuariosJson ?: "",
                        lastUserUpdateDate = existing?.lastUserUpdateDate ?: 0L,
                        lastUserUpdateStatus = existing?.lastUserUpdateStatus ?: "PENDIENTE",
                        lastUserUpdateVersion = existing?.lastUserUpdateVersion ?: "",
                        urlCatalogoJson = existing?.urlCatalogoJson ?: "",
                        lastCatalogoUpdateDate = existing?.lastCatalogoUpdateDate ?: 0L,
                        lastCatalogoUpdateStatus = existing?.lastCatalogoUpdateStatus ?: "PENDIENTE",
                        lastCatalogoUpdateVersion = existing?.lastCatalogoUpdateVersion ?: "",
                        urlMercainvJson = existing?.urlMercainvJson ?: "",
                        lastMercainvUpdateDate = existing?.lastMercainvUpdateDate ?: 0L,
                        lastMercainvUpdateStatus = existing?.lastMercainvUpdateStatus ?: "PENDIENTE",
                        lastMercainvUpdateVersion = existing?.lastMercainvUpdateVersion ?: "",
                        telefonoDueno = o.optString("telefonoDueno", existing?.telefonoDueno ?: ""),
                        telefonoCajero = o.optString("telefonoCajero", existing?.telefonoCajero ?: ""),
                        telefonoAdmin = o.optString("telefonoAdmin", existing?.telefonoAdmin ?: ""),
                        urlQDuenoJson = existing?.urlQDuenoJson ?: "",
                        urlVersionJson = existing?.urlVersionJson ?: "",
                        lastQDuenoUpdateDate = existing?.lastQDuenoUpdateDate ?: 0L,
                        lastQDuenoUpdateStatus = existing?.lastQDuenoUpdateStatus ?: "PENDIENTE",
                        lastQDuenoUpdateVersion = existing?.lastQDuenoUpdateVersion ?: "",
                        lastQDuenoUpdateFechaPublicacion = existing?.lastQDuenoUpdateFechaPublicacion ?: ""
                    )
                    db.configuracionGeneralDao().insertConfig(newGen)
                }
            }

            Result.success("Restauración completada con éxito. Se han reconstruido todos los datos operativos del Negocio ${summary.codigoNegocio}.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Error al restaurar los datos en base de datos: ${e.localizedMessage}"))
        }
    }
}
