package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.*
import com.example.ui.viewmodel.MainUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object CajeroBackupManager {

    private const val APP_IDENTIFIER = "QDEPCAJERO_BACKUP_V1"
    private const val FILE_NAME = "qdepcajero.json"

    fun createBackupJson(uiState: MainUiState): String {
        val root = JSONObject()
        root.put("appIdentifier", APP_IDENTIFIER)
        root.put("formatVersion", "1.0")
        root.put("role", "CAJERO")
        root.put("timestamp", System.currentTimeMillis())
        root.put("systemName", "El Qadre POS - Cajero")

        // 1. User & Config Info
        val userObj = JSONObject().apply {
            put("username", uiState.currentUser?.username ?: "cajero")
            put("fullName", uiState.currentUser?.fullName ?: "Cajero")
            put("role", uiState.currentUser?.role?.name ?: "CAJERO")
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

        // 4. Products / Menu
        val productsArr = JSONArray()
        uiState.products.forEach { p ->
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
        root.put("products", productsArr)

        // 5. Orders (Comandas / Ventas)
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

        // 6. Order Items
        val itemsArr = JSONArray()
        uiState.allOrderItems.forEach { item ->
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

        // 7. Transferencias Recibidas (SMS y Manuales)
        val transferenciasArr = JSONArray()
        uiState.allTransferencias.forEach { t ->
            val tObj = JSONObject().apply {
                put("id", t.id)
                put("transactionNumber", t.transactionNumber)
                put("jornadaId", t.jornadaId)
                put("comandaId", t.comandaId ?: -1L)
                put("comandaNumber", t.comandaNumber ?: -1)
                put("amount", t.amount)
                put("currency", t.currency)
                put("phoneNumber", t.phoneNumber)
                put("titularName", t.titularName)
                put("titularCi", t.titularCi)
                put("recipientAccount", t.recipientAccount)
                put("smsDate", t.smsDate)
                put("receivedAt", t.receivedAt)
                put("cajeroUsername", t.cajeroUsername)
                put("status", t.status)
                put("rawSmsBody", t.rawSmsBody)
                put("isManual", t.isManual)
                put("source", t.source)
            }
            transferenciasArr.put(tObj)
        }
        root.put("allTransferencias", transferenciasArr)

        // 8. Bitacora Entries (CAJERO / CUADRE)
        val bitacoraArr = JSONArray()
        uiState.bitacoraEntries.filter { it.category == "CAJERO" || it.category == "CUADRE" }.forEach { b ->
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

        // 9. Consumo Personal Items
        val consumoArr = JSONArray()
        uiState.consumoPersonalList.forEach { c ->
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("jornadaId", c.jornadaId)
                put("productId", c.productId)
                put("productName", c.productName)
                put("unitPrice", c.unitPrice)
                put("quantity", c.quantity)
                put("totalAmount", c.totalAmount)
                put("recordedBy", c.recordedBy)
                put("timestamp", c.timestamp)
            }
            consumoArr.put(cObj)
        }
        root.put("consumoPersonalList", consumoArr)

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
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Panel de Cajero - $FILE_NAME")
                putExtra(Intent.EXTRA_TEXT, "Copia de seguridad del Panel de Cajero (El Qadre).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Guardar / Compartir $FILE_NAME...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir respaldo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun validateAndParse(jsonString: String): Result<ParsedCajeroBackup> {
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
                return Result.failure(IllegalArgumentException("Error de Rol: El archivo corresponde a Dependiente de Salón (qdepsalon.json) y no puede ser restaurado en el Panel de Cajero."))
            }

            if (identifier.contains("BARRA") || role.equals("BARRA", ignoreCase = true) || root.has("barProducts")) {
                return Result.failure(IllegalArgumentException("Error de Rol: El archivo corresponde a Dependiente de Barra (qdepbarra.json) y no puede ser restaurado en el Panel de Cajero."))
            }

            val isCajeroValid = identifier == APP_IDENTIFIER ||
                    identifier == "QDEPENCAJERO_BACKUP_V1" ||
                    role.equals("CAJERO", ignoreCase = true) ||
                    root.has("allTransferencias") ||
                    root.has("allOrders")

            if (!isCajeroValid) {
                return Result.failure(IllegalArgumentException("Estructura incompatible: El archivo no contiene un formato reconocido de qdepcajero.json."))
            }

            // User info
            val userObj = root.optJSONObject("user")
            val username = userObj?.optString("username", "cajero") ?: "cajero"

            // Jornadas
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
                            isOpen = jObj.optBoolean("isOpen", true),
                            openedBy = jObj.optString("openedBy", username),
                            closedBy = jObj.optString("closedBy", "").ifEmpty { null },
                            utilidadSalonMontoUnitario = jObj.optDouble("utilidadSalonMontoUnitario", 0.0)
                        )
                    )
                }
            }

            // Products
            val productsList = mutableListOf<Product>()
            val productsArr = root.optJSONArray("products")
            if (productsArr != null) {
                for (i in 0 until productsArr.length()) {
                    val pObj = productsArr.getJSONObject(i)
                    productsList.add(
                        Product(
                            id = pObj.optLong("id", 0L),
                            code = pObj.optString("code", ""),
                            name = pObj.optString("name", ""),
                            category = pObj.optString("category", ""),
                            price = pObj.optDouble("price", 0.0),
                            cost = pObj.optDouble("cost", 0.0),
                            stock = pObj.optInt("stock", 0),
                            destination = pObj.optString("destination", "COCINA"),
                            isAvailable = pObj.optBoolean("isAvailable", true),
                            description = pObj.optString("description", ""),
                            unitOfMeasure = pObj.optString("unitOfMeasure", "UNIDAD"),
                            imagePath = pObj.optString("imagePath", "").ifEmpty { null }
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
                            destination = iObj.optString("destination", "COCINA"),
                            notes = iObj.optString("notes", ""),
                            status = iObj.optString("status", "ENTREGADO"),
                            productCode = iObj.optString("productCode", "")
                        )
                    )
                }
            }

            // Transferencias
            val transferenciasList = mutableListOf<Transferencia>()
            val transferenciasArr = root.optJSONArray("allTransferencias")
            if (transferenciasArr != null) {
                for (i in 0 until transferenciasArr.length()) {
                    val tObj = transferenciasArr.getJSONObject(i)
                    val cIdVal = tObj.optLong("comandaId", -1L)
                    val cNumVal = tObj.optInt("comandaNumber", -1)
                    transferenciasList.add(
                        Transferencia(
                            id = tObj.optLong("id", 0L),
                            transactionNumber = tObj.optString("transactionNumber", ""),
                            jornadaId = tObj.optLong("jornadaId", 0L),
                            comandaId = if (cIdVal > 0) cIdVal else null,
                            comandaNumber = if (cNumVal > 0) cNumVal else null,
                            amount = tObj.optDouble("amount", 0.0),
                            currency = tObj.optString("currency", "CUP"),
                            phoneNumber = tObj.optString("phoneNumber", ""),
                            titularName = tObj.optString("titularName", ""),
                            titularCi = tObj.optString("titularCi", ""),
                            recipientAccount = tObj.optString("recipientAccount", ""),
                            smsDate = tObj.optString("smsDate", ""),
                            receivedAt = tObj.optLong("receivedAt", System.currentTimeMillis()),
                            cajeroUsername = tObj.optString("cajeroUsername", username),
                            status = tObj.optString("status", "NO ASOCIADA"),
                            rawSmsBody = tObj.optString("rawSmsBody", ""),
                            isManual = tObj.optBoolean("isManual", false),
                            source = tObj.optString("source", "SMS_AUTOMATICA")
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
                            category = bObj.optString("category", "CAJERO"),
                            authorUsername = bObj.optString("authorUsername", username),
                            timestamp = bObj.optLong("timestamp", System.currentTimeMillis()),
                            priority = bObj.optString("priority", "NORMAL")
                        )
                    )
                }
            }

            // Consumo Personal
            val consumoList = mutableListOf<ConsumoPersonalItem>()
            val consumoArr = root.optJSONArray("consumoPersonalList")
            if (consumoArr != null) {
                for (i in 0 until consumoArr.length()) {
                    val cObj = consumoArr.getJSONObject(i)
                    consumoList.add(
                        ConsumoPersonalItem(
                            id = cObj.optLong("id", 0L),
                            jornadaId = cObj.optLong("jornadaId", 0L),
                            productId = cObj.optLong("productId", 0L),
                            productName = cObj.optString("productName", ""),
                            unitPrice = cObj.optDouble("unitPrice", 0.0),
                            quantity = cObj.optInt("quantity", 1),
                            totalAmount = cObj.optDouble("totalAmount", 0.0),
                            recordedBy = cObj.optString("recordedBy", username),
                            timestamp = cObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            Result.success(
                ParsedCajeroBackup(
                    username = username,
                    jornadas = jornadasList,
                    products = productsList,
                    orders = ordersList,
                    orderItems = itemsList,
                    transferencias = transferenciasList,
                    bitacoraEntries = bitacoraList,
                    consumoPersonalList = consumoList
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Error al procesar qdepcajero.json: ${e.localizedMessage ?: e.message}"))
        }
    }

    data class ParsedCajeroBackup(
        val username: String,
        val jornadas: List<Jornada>,
        val products: List<Product>,
        val orders: List<TableOrder>,
        val orderItems: List<OrderItem>,
        val transferencias: List<Transferencia>,
        val bitacoraEntries: List<BitacoraEntry>,
        val consumoPersonalList: List<ConsumoPersonalItem>
    )
}
