package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.*
import com.example.ui.screens.barra.BarraProductItem
import com.example.ui.viewmodel.MainUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BarraBackupManager {

    private const val APP_IDENTIFIER = "QDEPBARRA_BACKUP_V1"
    private const val FILE_NAME = "qdepbarra.json"

    fun createBackupJson(
        uiState: MainUiState,
        computedBarraItems: List<BarraProductItem>
    ): String {
        val root = JSONObject()
        root.put("appIdentifier", APP_IDENTIFIER)
        root.put("formatVersion", "1.0")
        root.put("role", "BARRA")
        root.put("timestamp", System.currentTimeMillis())
        root.put("systemName", "El Qadre POS - Dependiente de Barra")

        // 1. User & Config info
        val userObj = JSONObject().apply {
            put("username", uiState.currentUser?.username ?: "barra1")
            put("fullName", uiState.currentUser?.fullName ?: "Dependiente de Barra")
            put("role", uiState.currentUser?.role?.name ?: "BARRA")
            put("montoPorProducto", uiState.currentUser?.montoPorProducto ?: 0.0)
        }
        root.put("user", userObj)

        // 2. Active Jornada
        uiState.activeJornada?.let { j ->
            val jObj = JSONObject().apply {
                put("id", j.id)
                put("openedAt", j.openedAt)
                put("closedAt", j.closedAt ?: 0L)
                put("initialCash", j.initialCash)
                put("totalSales", j.totalSales)
                put("totalExpenses", j.totalExpenses)
                put("expectedCash", j.expectedCash)
                put("cashDifference", j.cashDifference)
                put("notes", j.notes)
                put("isOpen", j.isOpen)
                put("openedBy", j.openedBy)
                put("closedBy", j.closedBy ?: "")
                put("utilidadSalonMontoUnitario", j.utilidadSalonMontoUnitario)
            }
            root.put("activeJornada", jObj)
        }

        // 3. All Jornadas (Active + Archived)
        val jornadasArr = JSONArray()
        uiState.allJornadas.forEach { j ->
            val jObj = JSONObject().apply {
                put("id", j.id)
                put("openedAt", j.openedAt)
                put("closedAt", j.closedAt ?: 0L)
                put("initialCash", j.initialCash)
                put("totalSales", j.totalSales)
                put("totalExpenses", j.totalExpenses)
                put("expectedCash", j.expectedCash)
                put("cashDifference", j.cashDifference)
                put("notes", j.notes)
                put("isOpen", j.isOpen)
                put("openedBy", j.openedBy)
                put("closedBy", j.closedBy ?: "")
                put("utilidadSalonMontoUnitario", j.utilidadSalonMontoUnitario)
            }
            jornadasArr.put(jObj)
        }
        root.put("allJornadas", jornadasArr)

        // 4. Products of Barra
        val productsArr = JSONArray()
        val barraProducts = uiState.products.filter { it.destination == "BARRA" }
        barraProducts.forEach { p ->
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("code", p.code)
                put("name", p.name)
                put("category", p.category)
                put("price", p.price)
                put("cost", p.cost)
                put("stock", p.stock)
                put("destination", p.destination)
                put("isAvailable", p.isAvailable)
                put("description", p.description)
                put("unitOfMeasure", p.unitOfMeasure)
                put("imagePath", p.imagePath ?: "")
            }
            productsArr.put(pObj)
        }
        root.put("barProducts", productsArr)

        // 5. Inventory Control Snapshot (Inicio, Entradas, Vendido, Teórico, Físico, Diferencia)
        val inventoryArr = JSONArray()
        computedBarraItems.forEach { item ->
            val teorico = item.inventarioInicial + item.entradasJornada - item.ventasJornada
            val dif = item.existenciaActual - teorico
            val iObj = JSONObject().apply {
                put("productId", item.product.id)
                put("productName", item.product.name)
                put("subcategory", item.subcategory)
                put("existenciaInicial", item.inventarioInicial)
                put("entradas", item.entradasJornada)
                put("unidadesVendidas", item.ventasJornada)
                put("existenciaTeoricaFinal", teorico)
                put("existenciaFisicaFinal", item.existenciaActual)
                put("diferencia", dif)
                put("precioUnitario", item.product.price)
                put("importeTotalVentas", item.ventasJornada * item.product.price)
            }
            inventoryArr.put(iObj)
        }
        root.put("inventoryControl", inventoryArr)
        root.put("computedBarraSnapshot", inventoryArr)

        // 6. Stock Movements of Barra (ENTRADA, INVENTARIO_INICIAL, AJUSTE, etc.)
        val barProductIds = barraProducts.map { it.id }.toSet()
        val movementsArr = JSONArray()
        uiState.stockMovements.filter { barProductIds.contains(it.productId) }.forEach { m ->
            val mObj = JSONObject().apply {
                put("id", m.id)
                put("productId", m.productId)
                put("productName", m.productName)
                put("type", m.type)
                put("quantity", m.quantity)
                put("reason", m.reason)
                put("recordedBy", m.recordedBy)
                put("timestamp", m.timestamp)
                put("jornadaId", m.jornadaId)
            }
            movementsArr.put(mObj)
        }
        root.put("stockMovements", movementsArr)

        // 7. Table Orders & Order Items (Barra relevant)
        val ordersArr = JSONArray()
        uiState.allOrders.forEach { o ->
            val oObj = JSONObject().apply {
                put("id", o.id)
                put("tableNumber", o.tableNumber)
                put("customerName", o.customerName)
                put("waiterUsername", o.waiterUsername)
                put("createdAt", o.createdAt)
                put("confirmedAt", o.confirmedAt ?: 0L)
                put("servedAt", o.servedAt ?: 0L)
                put("closedAt", o.closedAt ?: 0L)
                put("status", o.status)
                put("totalAmount", o.totalAmount)
                put("totalCocina", o.totalCocina)
                put("totalBarra", o.totalBarra)
                put("paymentMethod", o.paymentMethod)
                put("cashReceived", o.cashReceived)
                put("changeGiven", o.changeGiven)
                put("tip", o.tip)
                put("jornadaId", o.jornadaId)
                put("comandaNumber", o.comandaNumber)
                put("cocinaNumber", o.cocinaNumber)
                put("barraNumber", o.barraNumber)
                put("serviceDurationSeconds", o.serviceDurationSeconds)
            }
            ordersArr.put(oObj)
        }
        root.put("allOrders", ordersArr)

        val itemsArr = JSONArray()
        uiState.allOrderItems.filter { it.destination == "BARRA" || barProductIds.contains(it.productId) }.forEach { item ->
            val iObj = JSONObject().apply {
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
            itemsArr.put(iObj)
        }
        root.put("allOrderItems", itemsArr)

        // 8. Bitacora Entries (BARRA / CUADRE)
        val bitacoraArr = JSONArray()
        uiState.bitacoraEntries.filter { it.category == "BARRA" || it.category == "CUADRE" }.forEach { b ->
            val bObj = JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("content", b.content)
                put("category", b.category)
                put("authorUsername", b.authorUsername)
                put("timestamp", b.timestamp)
                put("priority", b.priority)
            }
            bitacoraArr.put(bObj)
        }
        root.put("bitacoraEntries", bitacoraArr)

        return root.toString(2)
    }

    fun exportBackupFile(context: Context, jsonString: String): File? {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                val file = File(downloadsDir, FILE_NAME)
                FileOutputStream(file).use {
                    it.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            }

            val internalCopy = File(context.getExternalFilesDir(null) ?: context.cacheDir, FILE_NAME)
            FileOutputStream(internalCopy).use {
                it.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            return internalCopy
        } catch (e: Exception) {
            e.printStackTrace()
            return try {
                val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, FILE_NAME)
                FileOutputStream(file).use {
                    it.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                file
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun shareBackupFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Panel de Barra - $FILE_NAME")
                putExtra(Intent.EXTRA_TEXT, "Copia de seguridad del Panel de Dependiente de Barra (El Qadre).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Guardar / Compartir $FILE_NAME...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir respaldo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun validateAndParse(jsonString: String): Result<ParsedBarraBackup> {
        return try {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return Result.failure(IllegalArgumentException("El contenido no es un JSON válido."))
            }

            val root = JSONObject(trimmed)

            // Role and structure validation
            val identifier = root.optString("appIdentifier", "")
            val role = root.optString("role", "")

            if (identifier.contains("SALON") || role.equals("SALON", ignoreCase = true) || root.has("salonTableCount")) {
                return Result.failure(IllegalArgumentException("Error de Rol: El archivo corresponde a Dependiente de Salón (qdepsalon.json) y no puede ser restaurado en el Panel de Barra."))
            }

            val isBarraValid = identifier == APP_IDENTIFIER ||
                    identifier == "QDEPENBARRA_BACKUP_V1" ||
                    role.equals("BARRA", ignoreCase = true) ||
                    root.has("barProducts") ||
                    root.has("inventoryControl")

            if (!isBarraValid) {
                return Result.failure(IllegalArgumentException("Estructura incompatible: El archivo no contiene un formato reconocido de qdepbarra.json."))
            }

            // User info
            val userObj = root.optJSONObject("user")
            val username = userObj?.optString("username", "barra1") ?: "barra1"
            val userMonto = userObj?.optDouble("montoPorProducto", 0.0) ?: 0.0

            // Jornadas (Archived and Active)
            val jornadasList = mutableListOf<Jornada>()
            val jornadasArr = root.optJSONArray("allJornadas")
            if (jornadasArr != null) {
                for (i in 0 until jornadasArr.length()) {
                    val jObj = jornadasArr.getJSONObject(i)
                    val closedAtVal = jObj.optLong("closedAt", 0L)
                    jornadasList.add(
                        Jornada(
                            id = jObj.optLong("id", 0L),
                            openedAt = jObj.optLong("openedAt", System.currentTimeMillis()),
                            closedAt = if (closedAtVal > 0) closedAtVal else null,
                            initialCash = jObj.optDouble("initialCash", 0.0),
                            totalSales = jObj.optDouble("totalSales", 0.0),
                            totalExpenses = jObj.optDouble("totalExpenses", 0.0),
                            expectedCash = jObj.optDouble("expectedCash", 0.0),
                            cashDifference = jObj.optDouble("cashDifference", 0.0),
                            notes = jObj.optString("notes", ""),
                            isOpen = jObj.optBoolean("isOpen", false),
                            openedBy = jObj.optString("openedBy", username),
                            closedBy = jObj.optString("closedBy", username),
                            utilidadSalonMontoUnitario = jObj.optDouble("utilidadSalonMontoUnitario", userMonto)
                        )
                    )
                }
            } else {
                val activeObj = root.optJSONObject("activeJornada")
                if (activeObj != null) {
                    val closedAtVal = activeObj.optLong("closedAt", 0L)
                    jornadasList.add(
                        Jornada(
                            id = activeObj.optLong("id", 1L),
                            openedAt = activeObj.optLong("openedAt", System.currentTimeMillis()),
                            closedAt = if (closedAtVal > 0) closedAtVal else null,
                            initialCash = activeObj.optDouble("initialCash", 0.0),
                            totalSales = activeObj.optDouble("totalSales", 0.0),
                            totalExpenses = activeObj.optDouble("totalExpenses", 0.0),
                            expectedCash = activeObj.optDouble("expectedCash", 0.0),
                            cashDifference = activeObj.optDouble("cashDifference", 0.0),
                            notes = activeObj.optString("notes", ""),
                            isOpen = activeObj.optBoolean("isOpen", true),
                            openedBy = activeObj.optString("openedBy", username),
                            closedBy = activeObj.optString("closedBy", username),
                            utilidadSalonMontoUnitario = activeObj.optDouble("utilidadSalonMontoUnitario", userMonto)
                        )
                    )
                }
            }

            // Products
            val productsList = mutableListOf<Product>()
            val productsArr = root.optJSONArray("barProducts")
            if (productsArr != null) {
                for (i in 0 until productsArr.length()) {
                    val pObj = productsArr.getJSONObject(i)
                    productsList.add(
                        Product(
                            id = pObj.optLong("id", 0L),
                            code = pObj.optString("code", ""),
                            name = pObj.optString("name", ""),
                            category = pObj.optString("category", "Bebidas"),
                            price = pObj.optDouble("price", 0.0),
                            cost = pObj.optDouble("cost", 0.0),
                            stock = pObj.optInt("stock", 0),
                            destination = pObj.optString("destination", "BARRA"),
                            isAvailable = pObj.optBoolean("isAvailable", true),
                            description = pObj.optString("description", ""),
                            unitOfMeasure = pObj.optString("unitOfMeasure", "Unidad"),
                            imagePath = if (pObj.has("imagePath") && !pObj.isNull("imagePath") && pObj.getString("imagePath").isNotBlank()) pObj.getString("imagePath") else null
                        )
                    )
                }
            }

            // Stock Movements
            val movementsList = mutableListOf<StockMovement>()
            val movementsArr = root.optJSONArray("stockMovements")
            if (movementsArr != null) {
                for (i in 0 until movementsArr.length()) {
                    val mObj = movementsArr.getJSONObject(i)
                    movementsList.add(
                        StockMovement(
                            id = mObj.optLong("id", 0L),
                            productId = mObj.optLong("productId", 0L),
                            productName = mObj.optString("productName", ""),
                            type = mObj.optString("type", "ENTRADA"),
                            quantity = mObj.optInt("quantity", 0),
                            reason = mObj.optString("reason", ""),
                            recordedBy = mObj.optString("recordedBy", username),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                            jornadaId = mObj.optLong("jornadaId", 0L)
                        )
                    )
                }
            }

            // Orders
            val ordersList = mutableListOf<TableOrder>()
            val ordersArr = root.optJSONArray("allOrders")
            if (ordersArr != null) {
                for (i in 0 until ordersArr.length()) {
                    val oObj = ordersArr.getJSONObject(i)
                    val confVal = oObj.optLong("confirmedAt", 0L)
                    val servVal = oObj.optLong("servedAt", 0L)
                    val closVal = oObj.optLong("closedAt", 0L)
                    val durVal = oObj.optLong("serviceDurationSeconds", 0L)
                    ordersList.add(
                        TableOrder(
                            id = oObj.optLong("id", 0L),
                            tableNumber = oObj.optInt("tableNumber", 0),
                            customerName = oObj.optString("customerName", ""),
                            waiterUsername = oObj.optString("waiterUsername", username),
                            createdAt = oObj.optLong("createdAt", System.currentTimeMillis()),
                            confirmedAt = if (confVal > 0) confVal else null,
                            servedAt = if (servVal > 0) servVal else null,
                            closedAt = if (closVal > 0) closVal else null,
                            status = oObj.optString("status", "COBRADA"),
                            totalAmount = oObj.optDouble("totalAmount", 0.0),
                            totalCocina = oObj.optDouble("totalCocina", 0.0),
                            totalBarra = oObj.optDouble("totalBarra", 0.0),
                            paymentMethod = oObj.optString("paymentMethod", "EFECTIVO"),
                            cashReceived = oObj.optDouble("cashReceived", 0.0),
                            changeGiven = oObj.optDouble("changeGiven", 0.0),
                            tip = oObj.optDouble("tip", 0.0),
                            jornadaId = oObj.optLong("jornadaId", 1L),
                            comandaNumber = oObj.optInt("comandaNumber", 1),
                            cocinaNumber = oObj.optInt("cocinaNumber", 0),
                            barraNumber = oObj.optInt("barraNumber", 0),
                            serviceDurationSeconds = durVal
                        )
                    )
                }
            }

            // Order Items
            val itemsList = mutableListOf<OrderItem>()
            val itemsArr = root.optJSONArray("allOrderItems")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val iObj = itemsArr.getJSONObject(i)
                    itemsList.add(
                        OrderItem(
                            id = iObj.optLong("id", 0L),
                            orderId = iObj.optLong("orderId", 0L),
                            productId = iObj.optLong("productId", 0L),
                            productName = iObj.optString("productName", ""),
                            unitPrice = iObj.optDouble("unitPrice", 0.0),
                            quantity = iObj.optInt("quantity", 1),
                            destination = iObj.optString("destination", "BARRA"),
                            notes = iObj.optString("notes", ""),
                            status = iObj.optString("status", "ENTREGADO"),
                            productCode = iObj.optString("productCode", "")
                        )
                    )
                }
            }

            // Bitacora
            val bitacoraList = mutableListOf<BitacoraEntry>()
            val bitacoraArr = root.optJSONArray("bitacoraEntries")
            if (bitacoraArr != null) {
                for (i in 0 until bitacoraArr.length()) {
                    val bObj = bitacoraArr.getJSONObject(i)
                    bitacoraList.add(
                        BitacoraEntry(
                            id = bObj.optLong("id", 0L),
                            title = bObj.optString("title", ""),
                            content = bObj.optString("content", ""),
                            category = bObj.optString("category", "BARRA"),
                            authorUsername = bObj.optString("authorUsername", username),
                            timestamp = bObj.optLong("timestamp", System.currentTimeMillis()),
                            priority = bObj.optString("priority", "NORMAL")
                        )
                    )
                }
            }

            Result.success(
                ParsedBarraBackup(
                    username = username,
                    userMonto = userMonto,
                    jornadas = jornadasList,
                    products = productsList,
                    stockMovements = movementsList,
                    orders = ordersList,
                    orderItems = itemsList,
                    bitacoraEntries = bitacoraList
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Error al procesar qdepbarra.json: ${e.localizedMessage ?: e.message}"))
        }
    }

    data class ParsedBarraBackup(
        val username: String,
        val userMonto: Double,
        val jornadas: List<Jornada>,
        val products: List<Product>,
        val stockMovements: List<StockMovement>,
        val orders: List<TableOrder>,
        val orderItems: List<OrderItem>,
        val bitacoraEntries: List<BitacoraEntry>
    )
}
