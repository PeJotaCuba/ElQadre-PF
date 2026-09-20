package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.data.local.model.*
import com.example.ui.viewmodel.MainUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object InitializationManager {
    private const val APP_IDENTIFIER = "QDEP_INITIALIZATION_BACKUP_V1"
    private const val FILE_NAME = "qdep_inicializacion_respaldo.json"

    fun createBackupJson(uiState: MainUiState): String {
        val root = JSONObject()
        root.put("appIdentifier", APP_IDENTIFIER)
        root.put("formatVersion", "1.0")
        root.put("timestamp", System.currentTimeMillis())
        root.put("systemName", "El Qadre POS - Inicializacion")

        val userObj = JSONObject().apply {
            put("username", uiState.currentUser?.username ?: "dueno")
            put("fullName", uiState.currentUser?.fullName ?: "Dueño")
            put("role", uiState.currentUser?.role?.name ?: "DUENO")
        }
        root.put("user", userObj)

        val jArray = JSONArray()
        uiState.allJornadas.forEach { j ->
            val obj = JSONObject().apply {
                put("id", j.id)
                put("openedAt", j.openedAt)
                put("closedAt", j.closedAt)
                put("initialCash", j.initialCash)
                put("finalCash", j.finalCash)
                put("totalSales", j.totalSales)
                put("totalExpenses", j.totalExpenses)
                put("expectedCash", j.expectedCash)
                put("cashDifference", j.cashDifference)
                put("isOpen", j.isOpen)
                put("openedBy", j.openedBy)
                put("closedBy", j.closedBy)
                put("utilidadSalonMontoUnitario", j.utilidadSalonMontoUnitario)
            }
            jArray.put(obj)
        }
        root.put("jornadas", jArray)

        val tArray = JSONArray()
        uiState.allOrders.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("tableNumber", t.tableNumber ?: JSONObject.NULL)
                put("customerName", t.customerName)
                put("waiterUsername", t.waiterUsername)
                put("createdAt", t.createdAt)
                put("closedAt", t.closedAt ?: JSONObject.NULL)
                put("status", t.status)
                put("totalAmount", t.totalAmount)
                put("currency", t.currency)
                put("exchangeRate", t.exchangeRate)
                put("originalAmount", t.originalAmount)
                put("amountInCurrency", t.amountInCurrency)
                put("paymentMethod", t.paymentMethod ?: JSONObject.NULL)
                put("jornadaId", t.jornadaId ?: JSONObject.NULL)
                put("confirmedAt", t.confirmedAt ?: JSONObject.NULL)
                put("servedAt", t.servedAt ?: JSONObject.NULL)
                put("serviceDurationSeconds", t.serviceDurationSeconds)
            }
            tArray.put(obj)
        }
        root.put("tableOrders", tArray)

        val itemsArr = JSONArray()
        uiState.allOrderItems.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("orderId", item.orderId)
                put("productId", item.productId)
                put("productName", item.productName)
                put("productCode", item.productCode)
                put("quantity", item.quantity)
                put("unitPrice", item.unitPrice)
                put("notes", item.notes)
                put("status", item.status)
                put("destination", item.destination)
            }
            itemsArr.put(obj)
        }
        root.put("orderItems", itemsArr)

        val transArr = JSONArray()
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
            transArr.put(obj)
        }
        root.put("transferencias", transArr)

        val bitacoraArr = JSONArray()
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
            bitacoraArr.put(obj)
        }
        root.put("bitacoraEntries", bitacoraArr)

        val cList = JSONArray()
        uiState.consumoPersonalList.forEach { cp ->
            val o = JSONObject().apply {
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
            cList.put(o)
        }
        root.put("consumoPersonalList", cList)

        // Serialize Inversiones
        val invArray = JSONArray()
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
                put("scope", inv.scope)
                put("currency", inv.currency)
                put("originalAmount", inv.originalAmount)
                put("exchangeRate", inv.exchangeRate)
                put("convertedAmount", inv.convertedAmount)
            }
            invArray.put(obj)
        }
        root.put("inversiones", invArray)

        // Serialize PaymentProposals
        val propArray = JSONArray()
        uiState.paymentProposals.forEach { prop ->
            val obj = JSONObject().apply {
                put("username", prop.username)
                put("fullName", prop.fullName)
                put("role", prop.role)
                put("paymentAmount", prop.paymentAmount)
                put("paymentType", prop.paymentType)
                put("isActiveProposal", prop.isActiveProposal)
                put("lastUpdated", prop.lastUpdated)
            }
            propArray.put(obj)
        }
        root.put("paymentProposals", propArray)

        return root.toString(2)
    }

    fun exportAndShareBackup(context: Context, uiState: MainUiState): Boolean {
        return try {
            val jsonString = createBackupJson(uiState)
            val file = File(context.cacheDir, FILE_NAME)
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
                putExtra(Intent.EXTRA_SUBJECT, "Respaldo Inicializacion - El Qadre")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Guardar Respaldo de Inicializacion"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al crear el respaldo: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun getBackupSummary(jsonString: String): Result<String> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("appIdentifier") || root.getString("appIdentifier") != APP_IDENTIFIER) {
                return Result.failure(Exception("Identificador de aplicación incompatible o archivo corrupto."))
            }
            val formatVersion = root.optString("formatVersion", "1.0")
            val timestamp = root.optLong("timestamp", 0L)
            val dateStr = if (timestamp > 0) {
                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
            } else {
                "Fecha desconocida"
            }

            val jornadasLen = root.optJSONArray("jornadas")?.length() ?: 0
            val ordersLen = root.optJSONArray("tableOrders")?.length() ?: 0
            val itemsLen = root.optJSONArray("orderItems")?.length() ?: 0
            val transLen = root.optJSONArray("transferencias")?.length() ?: 0
            val cpLen = root.optJSONArray("consumoPersonalList")?.length() ?: 0
            val invLen = root.optJSONArray("inversiones")?.length() ?: 0
            val propLen = root.optJSONArray("paymentProposals")?.length() ?: 0

            val text = "Respaldo del Dueño - $dateStr (v$formatVersion)\n\n" +
                    "Contenido a restaurar:\n" +
                    "• Jornadas registradas: $jornadasLen\n" +
                    "• Comandas registradas: $ordersLen\n" +
                    "• Ítems de comandas: $itemsLen\n" +
                    "• Transferencias: $transLen\n" +
                    "• Consumos de personal: $cpLen\n" +
                    "• Inversiones: $invLen\n" +
                    "• Propuestas de pago: $propLen\n\n" +
                    "⚠️ ADVERTENCIA: Esta acción reemplazará atómicamente todos tus datos operativos actuales por los de este archivo. El catálogo de productos y los usuarios no sufrirán cambios."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackupJson(jsonString: String, db: AppDatabase): Result<String> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("appIdentifier") || root.getString("appIdentifier") != APP_IDENTIFIER) {
                return Result.failure(Exception("Identificador de aplicación incompatible o archivo corrupto."))
            }

            val summaryRes = getBackupSummary(jsonString)
            if (summaryRes.isFailure) {
                return Result.failure(summaryRes.exceptionOrNull() ?: Exception("No se pudo obtener el resumen."))
            }

            db.withTransaction {
                // Wipe operational tables
                db.jornadaDao().deleteAllJornadas()
                db.tableOrderDao().deleteAllOrders()
                db.tableOrderDao().deleteAllOrderItems()
                db.transferenciaDao().deleteAllTransferencias()
                db.consumoPersonalDao().deleteAllConsumoPersonal()
                db.bitacoraDao().deleteAllEntries()
                db.tandaDao().deleteAllTandas()
                db.movimientoMateriaPrimaDao().deleteAllMovimientosMateriaPrima()
                db.mercaderiaDao().deleteAllMovimientos()
                db.productionBatchDao().deleteAllBatches()
                db.stockMovementDao().deleteAllMovements()
                db.paymentProposalDao().deleteAllProposals()

                // Insert Jornadas
                root.optJSONArray("jornadas")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val j = Jornada(
                            id = obj.getLong("id"),
                            openedAt = obj.getLong("openedAt"),
                            closedAt = if (obj.isNull("closedAt")) null else obj.getLong("closedAt"),
                            initialCash = obj.getDouble("initialCash"),
                            finalCash = obj.getDouble("finalCash"),
                            totalSales = obj.getDouble("totalSales"),
                            totalExpenses = obj.getDouble("totalExpenses"),
                            expectedCash = obj.getDouble("expectedCash"),
                            cashDifference = obj.getDouble("cashDifference"),
                            isOpen = obj.getBoolean("isOpen"),
                            openedBy = obj.getString("openedBy"),
                            closedBy = obj.optString("closedBy", ""),
                            utilidadSalonMontoUnitario = obj.optDouble("utilidadSalonMontoUnitario", 0.0)
                        )
                        db.jornadaDao().insertJornada(j)
                    }
                }

                // Insert TableOrders
                root.optJSONArray("tableOrders")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val order = TableOrder(
                            id = obj.getLong("id"),
                            tableNumber = if (obj.isNull("tableNumber")) null else obj.getInt("tableNumber"),
                            customerName = obj.getString("customerName"),
                            waiterUsername = obj.getString("waiterUsername"),
                            createdAt = obj.getLong("createdAt"),
                            closedAt = if (obj.isNull("closedAt")) null else obj.getLong("closedAt"),
                            status = obj.getString("status"),
                            totalAmount = obj.getDouble("totalAmount"),
                            paymentMethod = if (obj.isNull("paymentMethod")) "Efectivo" else obj.getString("paymentMethod"),
                            jornadaId = obj.getLong("jornadaId"),
                            confirmedAt = if (obj.isNull("confirmedAt")) null else obj.getLong("confirmedAt"),
                            servedAt = if (obj.isNull("servedAt")) null else obj.getLong("servedAt"),
                            serviceDurationSeconds = obj.optLong("serviceDurationSeconds", 0L),
                            currency = obj.optString("currency", "CUP"),
                            exchangeRate = obj.optDouble("exchangeRate", 1.0),
                            originalAmount = obj.optDouble("originalAmount", 0.0),
                            amountInCurrency = obj.optDouble("amountInCurrency", 0.0)
                        )
                        db.tableOrderDao().insertOrder(order)
                    }
                }

                // Insert OrderItems
                root.optJSONArray("orderItems")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val item = OrderItem(
                            id = obj.getLong("id"),
                            orderId = obj.getLong("orderId"),
                            productId = obj.getLong("productId"),
                            productName = obj.getString("productName"),
                            productCode = obj.optString("productCode", ""),
                            quantity = obj.getInt("quantity"),
                            unitPrice = obj.getDouble("unitPrice"),
                            notes = obj.getString("notes"),
                            status = obj.getString("status"),
                            destination = obj.getString("destination")
                        )
                        db.tableOrderDao().insertOrderItem(item)
                    }
                }

                // Insert Transferencias
                root.optJSONArray("transferencias")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val tr = Transferencia(
                            id = obj.getLong("id"),
                            transactionNumber = obj.getString("transactionNumber"),
                            jornadaId = obj.getLong("jornadaId"),
                            comandaId = if (obj.isNull("comandaId")) null else obj.getLong("comandaId"),
                            comandaNumber = if (obj.isNull("comandaNumber")) null else obj.getInt("comandaNumber"),
                            amount = obj.getDouble("amount"),
                            currency = obj.optString("currency", "CUP"),
                            phoneNumber = obj.optString("phoneNumber", ""),
                            titularName = obj.optString("titularName", ""),
                            titularCi = obj.optString("titularCi", ""),
                            recipientAccount = obj.optString("recipientAccount", ""),
                            smsDate = obj.optString("smsDate", ""),
                            receivedAt = obj.getLong("receivedAt"),
                            cajeroUsername = obj.optString("cajeroUsername", ""),
                            status = obj.optString("status", "NO ASOCIADA"),
                            rawSmsBody = obj.optString("rawSmsBody", ""),
                            isManual = if (obj.has("isManual")) obj.getBoolean("isManual") else false,
                            source = obj.optString("source", "SMS_AUTOMATICA")
                        )
                        db.transferenciaDao().insertTransferencia(tr)
                    }
                }

                // Insert BitacoraEntries
                root.optJSONArray("bitacoraEntries")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val b = BitacoraEntry(
                            id = obj.getLong("id"),
                            title = obj.getString("title"),
                            content = obj.getString("content"),
                            category = obj.getString("category"),
                            authorUsername = obj.getString("authorUsername"),
                            timestamp = obj.getLong("timestamp"),
                            priority = obj.getString("priority")
                        )
                        db.bitacoraDao().insertEntry(b)
                    }
                }

                // Insert ConsumoPersonalList
                root.optJSONArray("consumoPersonalList")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val cp = ConsumoPersonalItem(
                            id = obj.getLong("id"),
                            jornadaId = obj.getLong("jornadaId"),
                            productId = obj.getLong("productId"),
                            productName = obj.getString("productName"),
                            unitPrice = obj.getDouble("unitPrice"),
                            quantity = obj.getInt("quantity"),
                            totalAmount = obj.getDouble("totalAmount"),
                            recordedBy = obj.getString("recordedBy"),
                            timestamp = obj.getLong("timestamp")
                        )
                        db.consumoPersonalDao().insertConsumoPersonal(cp)
                    }
                }

                // Insert Inversiones
                root.optJSONArray("inversiones")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val inv = Inversion(
                            id = obj.getLong("id"),
                            name = obj.getString("name"),
                            category = obj.getString("category"),
                            amount = obj.getDouble("amount"),
                            date = obj.getLong("date"),
                            usefulLife = obj.getDouble("usefulLife"),
                            usefulLifeUnit = obj.getString("usefulLifeUnit"),
                            observation = obj.getString("observation"),
                            targetProductId = if (obj.isNull("targetProductId")) null else obj.getLong("targetProductId"),
                            scope = obj.optString("scope", "PRODUCCION"),
                            currency = obj.optString("currency", "CUP"),
                            originalAmount = obj.optDouble("originalAmount", 0.0),
                            exchangeRate = obj.optDouble("exchangeRate", 1.0),
                            convertedAmount = obj.optDouble("convertedAmount", 0.0)
                        )
                        db.inversionDao().insert(inv)
                    }
                }

                // Insert PaymentProposals
                root.optJSONArray("paymentProposals")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val prop = PaymentProposal(
                            username = obj.getString("username"),
                            fullName = obj.getString("fullName"),
                            role = UserRole.valueOf(obj.getString("role")),
                            paymentAmount = obj.getDouble("paymentAmount"),
                            paymentType = obj.getString("paymentType"),
                            isActiveProposal = obj.getBoolean("isActiveProposal"),
                            lastUpdated = obj.getLong("lastUpdated")
                        )
                        db.paymentProposalDao().insertProposal(prop)
                    }
                }
            }
            Result.success("¡Respaldo restaurado exitosamente!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
