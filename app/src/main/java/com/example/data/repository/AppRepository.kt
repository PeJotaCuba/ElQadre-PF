package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRepository(private val db: AppDatabase) {

    fun getDatabase(): AppDatabase = db

    private val comandaMutex = Mutex()
    private val cobroMutex = Mutex()

    // Configs
    val businessConfig: Flow<ConfiguracionNegocio?> = db.configuracionNegocioDao().getConfig()
    val generalConfig: Flow<ConfiguracionGeneral?> = db.configuracionGeneralDao().getConfig()

    suspend fun updateBusinessConfig(config: ConfiguracionNegocio) {
        db.configuracionNegocioDao().updateConfig(config)
    }

    suspend fun updateGeneralConfig(config: ConfiguracionGeneral) {
        db.configuracionGeneralDao().updateConfig(config)
    }

    // Production: Raw Materials, Recipes, Elaborated Products
    val allMateriasPrimas: Flow<List<MateriaPrima>> = db.materiaPrimaDao().getAll()
    suspend fun insertMateriaPrima(materiaPrima: MateriaPrima) = db.materiaPrimaDao().insert(materiaPrima)
    suspend fun updateMateriaPrima(materiaPrima: MateriaPrima) = db.materiaPrimaDao().update(materiaPrima)
    suspend fun deleteMateriaPrima(materiaPrima: MateriaPrima) = db.materiaPrimaDao().delete(materiaPrima)

    val allProductosElaborados: Flow<List<ProductoElaborado>> = db.productoElaboradoDao().getAll()
    suspend fun insertProductoElaborado(producto: ProductoElaborado): Long = db.productoElaboradoDao().insert(producto)
    suspend fun updateProductoElaborado(producto: ProductoElaborado) = db.productoElaboradoDao().update(producto)
    suspend fun deleteProductoElaborado(producto: ProductoElaborado) = db.productoElaboradoDao().delete(producto)

    val allRecetaIngredientes: Flow<List<RecetaIngrediente>> = db.recetaIngredienteDao().getAllIngredients()
    fun getIngredientsForProduct(productoElaboradoId: Long): Flow<List<RecetaIngrediente>> =
        db.recetaIngredienteDao().getIngredientsForProduct(productoElaboradoId)
    suspend fun insertRecetaIngrediente(ingrediente: RecetaIngrediente) = db.recetaIngredienteDao().insert(ingrediente)
    suspend fun updateRecetaIngrediente(ingrediente: RecetaIngrediente) = db.recetaIngredienteDao().update(ingrediente)
    suspend fun deleteRecetaIngrediente(ingrediente: RecetaIngrediente) = db.recetaIngredienteDao().delete(ingrediente)
    suspend fun deleteIngredientsForProduct(productoElaboradoId: Long) =
        db.recetaIngredienteDao().deleteIngredientsForProduct(productoElaboradoId)


    // Categories
    val allCategories: Flow<List<Category>> = db.categoryDao().getAllCategories()
    suspend fun insertCategory(category: Category) = db.categoryDao().insertCategory(category)
    suspend fun updateCategory(category: Category) = db.categoryDao().updateCategory(category)
    suspend fun deleteCategory(id: Long) = db.categoryDao().deleteCategory(id)

    // Users
    val allUsers: Flow<List<User>> = db.userDao().getAllUsers()

    suspend fun getUserByUsername(username: String): User? =
        db.userDao().getUserByUsername(username)

    suspend fun insertUser(user: User) {
        val existing = db.userDao().getUserByUsername(user.username)
        if (existing != null && existing.username != user.username) {
            db.userDao().deleteUser(existing.username)
        }
        db.userDao().insertUser(user)
    }
    suspend fun updateUser(user: User) = db.userDao().updateUser(user)
    suspend fun deleteUser(username: String) = db.userDao().deleteUser(username)

    // Jornadas
    val allJornadas: Flow<List<Jornada>> = db.jornadaDao().getAllJornadas()
    val activeJornada: Flow<Jornada?> = db.jornadaDao().getActiveJornada()

    suspend fun getActiveJornadaSync(): Jornada? = db.jornadaDao().getActiveJornadaSync()

    suspend fun openJornada(initialCash: Double, openedBy: String, deviceId: String = "DISPOSITIVO-LOCAL"): Long {
        val active = db.jornadaDao().getActiveJornadaSync()
        if (active != null) {
            // Cannot open two jornadas concurrently on the same device
            return -1L
        }
        val user = db.userDao().getUserByUsername(openedBy)
        val rate = user?.montoPorProducto ?: 0.0
        val now = System.currentTimeMillis()
        val newJornada = Jornada(
            openedAt = now,
            initialCash = initialCash,
            expectedCash = initialCash,
            isOpen = true,
            openedBy = openedBy,
            utilidadSalonMontoUnitario = rate,
            deviceId = deviceId
        )
        val id = db.jornadaDao().insertJornada(newJornada)

        // Snapshot initial physical inventory for Barra products for this new jornada
        val barProducts = db.productDao().getAllProductsSync().filter { it.destination == "BARRA" }
        barProducts.forEach { p ->
            db.stockMovementDao().insertMovement(
                StockMovement(
                    productId = p.id,
                    productName = p.name,
                    type = "INVENTARIO_INICIAL",
                    quantity = p.stock,
                    reason = "Inventario Inicial Apertura Jornada #$id",
                    recordedBy = openedBy,
                    timestamp = now,
                    jornadaId = id
                )
            )
        }

        db.bitacoraDao().insertEntry(
            BitacoraEntry(
                title = "Apertura de Jornada",
                content = "Jornada #$id iniciada por $openedBy con fondo inicial de $${"%.2f".format(initialCash)}",
                category = "CUADRE",
                authorUsername = openedBy
            )
        )
        return id
    }

    suspend fun closeJornada(
        jornada: Jornada,
        finalCash: Double,
        closedBy: String,
        notes: String,
        snapshotJson: String = "",
        realSalesProd: Double = 0.0,
        realCostProd: Double = 0.0,
        gastosProd: Double = 0.0,
        invProd: Double = 0.0,
        resProd: Double = 0.0,
        realSalesMerc: Double = 0.0,
        realCostMerc: Double = 0.0,
        gastosMerc: Double = 0.0,
        invMerc: Double = 0.0,
        resMerc: Double = 0.0,
        totalIng: Double = 0.0,
        totalCost: Double = 0.0,
        totalGast: Double = 0.0,
        totalInv: Double = 0.0,
        utilidad: Double = 0.0,
        modulos: String = "PRODUCCION,MERCADERIAS",
        extracciones: Double = 0.0,
        liquidezFinal: Double = 0.0
    ) {
        val effectiveSales = if (totalIng > 0.0) totalIng else jornada.totalSales
        val expected = jornada.initialCash + effectiveSales - jornada.totalExpenses
        val diff = finalCash - expected
        val updated = jornada.copy(
            closedAt = System.currentTimeMillis(),
            finalCash = finalCash,
            totalSales = effectiveSales,
            expectedCash = expected,
            cashDifference = diff,
            notes = notes,
            isOpen = false,
            closedBy = closedBy,
            realSalesProduccion = realSalesProd,
            realCostProduccion = realCostProd,
            gastosProduccion = gastosProd,
            inversionesProduccion = invProd,
            resultadoProduccion = resProd,
            realSalesMercaderias = realSalesMerc,
            realCostMercaderias = realCostMerc,
            gastosMercaderias = gastosMerc,
            inversionesMercaderias = invMerc,
            resultadoMercaderias = resMerc,
            totalIngresos = totalIng,
            totalCostos = totalCost,
            totalGastos = totalGast,
            totalInversiones = totalInv,
            utilidadDelDia = utilidad,
            modulosUtilizados = modulos,
            snapshotJson = snapshotJson,
            extracciones = extracciones,
            liquidezFinal = liquidezFinal
        )
        db.jornadaDao().updateJornada(updated)

        // 1. CIERRE AUTOMÁTICO DE TANDAS AL CERRAR JORNADA
        // Todas las tandas que pertenezcan a esa jornada deben quedar automáticamente cerradas.
        // NO borrar ninguna tanda. Toda la información debe conservarse.
        try {
            val allTandas = db.tandaDao().getAllSync()
            val tandasToClose = allTandas.filter { tanda ->
                (tanda.jornadaId == jornada.id || (jornada.openedAt > 0 && tanda.date >= jornada.openedAt)) &&
                (tanda.status == "ACTIVA" || tanda.status == "ABIERTA" || tanda.status == "ACTIVADA")
            }
            val allProducts = db.productDao().getAllProductsSync()
            tandasToClose.forEach { tanda ->
                val finalQty = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val rend = if (tanda.baseQuantityUsed > 0.0) finalQty / tanda.baseQuantityUsed else 0.0
                val prod = allProducts.find { it.id == tanda.productId }
                val salePrice = if (tanda.salePrice > 0.0) tanda.salePrice else (prod?.price ?: 0.0)
                val rev = finalQty * salePrice
                val profit = rev - tanda.totalBatchCost
                val pMargin = if (rev > 0.0) (profit / rev) * 100.0 else 0.0
                val uCost = if (finalQty > 0.0) tanda.totalBatchCost / finalQty else 0.0

                db.tandaDao().update(
                    tanda.copy(
                        jornadaId = jornada.id,
                        status = "CERRADA",
                        actualYield = finalQty,
                        yieldPercentage = rend,
                        expectedRevenue = rev,
                        estimatedProfit = profit,
                        profitMargin = pMargin,
                        realUnitCost = uCost
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        db.bitacoraDao().insertEntry(
            BitacoraEntry(
                title = "Cierre de Jornada",
                content = "Jornada #${jornada.id} cerrada por $closedBy. Total Ingresos: $${"%.2f".format(effectiveSales)}, Utilidad: $${"%.2f".format(utilidad)}, Cuadre: $${"%.2f".format(diff)}",
                category = "CUADRE",
                authorUsername = closedBy,
                priority = if (diff != 0.0) "ALTA" else "NORMAL"
            )
        )
    }

    // Products
    val allProducts: Flow<List<Product>> = db.productDao().getAllProducts()

    suspend fun insertProduct(product: Product) = db.productDao().insertProduct(product)
    suspend fun updateProduct(product: Product) = db.productDao().updateProduct(product)
    suspend fun deleteProduct(id: Long) = db.productDao().deleteProduct(id)

    // Table Orders
    val openOrders: Flow<List<TableOrder>> = db.tableOrderDao().getOpenOrders()
    val allOrders: Flow<List<TableOrder>> = db.tableOrderDao().getAllOrders()
    val allOrderItems: Flow<List<OrderItem>> = db.tableOrderDao().getAllOrderItems()
    val pendingSmsComandas: Flow<List<SmsComandaQueue>> = db.smsComandaQueueDao().getPendingComandasQueue()

    suspend fun getPendingSmsComandasSync(): List<SmsComandaQueue> =
        db.smsComandaQueueDao().getPendingComandasQueueSync()

    suspend fun insertSmsComandaQueue(item: SmsComandaQueue): Long =
        db.smsComandaQueueDao().insertSmsComanda(item)

    suspend fun updateSmsComandaQueueEstado(id: Long, estado: String) =
        db.smsComandaQueueDao().updateEstado(id, estado)


    fun getItemsForOrder(orderId: Long): Flow<List<OrderItem>> =
        db.tableOrderDao().getItemsForOrder(orderId)

    fun getPendingItemsByDestination(destination: String): Flow<List<OrderItem>> =
        db.tableOrderDao().getPendingItemsByDestination(destination)

    suspend fun getMaxComandaNumberForJornada(jornadaId: Long): Int {
        return db.tableOrderDao().getMaxComandaNumberForJornada(jornadaId)
    }

    suspend fun confirmComandaWithItems(order: TableOrder, items: List<OrderItem>): Long = comandaMutex.withLock {
        db.withTransaction {
            val assignedComandaNumber = if (order.comandaNumber > 0) {
                order.comandaNumber
            } else {
                db.tableOrderDao().getMaxComandaNumberForJornada(order.jornadaId) + 1
            }
            val finalOrder = order.copy(comandaNumber = assignedComandaNumber)
            val orderId = db.tableOrderDao().insertOrder(finalOrder)
            items.forEach { item ->
                val updatedItem = item.copy(orderId = orderId)
                db.tableOrderDao().insertOrderItem(updatedItem)
            }
            orderId
        }
    }

    suspend fun updateComandaWithItems(order: TableOrder, items: List<OrderItem>) = comandaMutex.withLock {
        db.withTransaction {
            db.tableOrderDao().updateOrder(order)
            db.tableOrderDao().deleteItemsForOrder(order.id)
            items.forEach { item ->
                val updatedItem = item.copy(orderId = order.id)
                db.tableOrderDao().insertOrderItem(updatedItem)
            }
        }
    }

    suspend fun addItemsToComanda(
        orderId: Long,
        newItems: List<OrderItem>,
        updatedTotalAmount: Double,
        updatedTotalCocina: Double,
        updatedTotalBarra: Double
    ) = comandaMutex.withLock {
        db.withTransaction {
            val existing = db.tableOrderDao().getOrderById(orderId) ?: return@withTransaction
            val updated = existing.copy(
                totalAmount = updatedTotalAmount,
                totalCocina = updatedTotalCocina,
                totalBarra = updatedTotalBarra,
                status = if (existing.status == "SERVIDA") "PENDIENTE_SERVIR" else existing.status
            )
            db.tableOrderDao().updateOrder(updated)
            newItems.forEach { item ->
                val updatedItem = item.copy(orderId = orderId)
                db.tableOrderDao().insertOrderItem(updatedItem)
            }
        }
    }

    suspend fun servirComanda(orderId: Long, servedAt: Long, durationSeconds: Long) {
        val existing = db.tableOrderDao().getOrderById(orderId) ?: return
        val updated = existing.copy(
            status = "SERVIDA",
            servedAt = servedAt,
            serviceDurationSeconds = durationSeconds
        )
        db.tableOrderDao().updateOrder(updated)
    }

    suspend fun despacharBarraItems(orderId: Long) {
        val items = db.tableOrderDao().getItemsForOrderSync(orderId)
        items.filter { it.destination == "BARRA" }.forEach { item ->
            db.tableOrderDao().updateOrderItem(item.copy(status = "DESPACHADO"))
        }
        val order = db.tableOrderDao().getOrderById(orderId)
        if (order != null && order.status != "COBRADA" && order.status != "CANCELADA") {
            val now = System.currentTimeMillis()
            val confirmedTime = order.confirmedAt ?: order.createdAt
            val durationSeconds = maxOf(0L, (now - confirmedTime) / 1000)
            db.tableOrderDao().updateOrder(
                order.copy(
                    status = "SERVIDA",
                    servedAt = now,
                    serviceDurationSeconds = durationSeconds
                )
            )
        }
    }

    suspend fun cobrarMesaCompleta(
        tableNumber: Int?,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        chargedBy: String = "salon",
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0
    ): Result<List<TableOrder>> = cobroMutex.withLock {
        db.withTransaction {
            val ordersToClose = db.tableOrderDao().getAllOrdersSync().filter {
                it.tableNumber == tableNumber && it.status != "COBRADA" && it.status != "CANCELADA"
            }
            if (ordersToClose.isEmpty()) {
                return@withTransaction Result.failure(IllegalStateException("No hay comandas activas para cobrar en la Mesa $tableNumber."))
            }

            val closedAt = System.currentTimeMillis()
            var totalCharged = 0.0
            var totalEfectivoCharged = 0.0
            val closedOrdersList = mutableListOf<TableOrder>()

            for (order in ordersToClose) {
                totalCharged += order.totalAmount
                if (paymentMethod == "EFECTIVO") {
                    totalEfectivoCharged += order.totalAmount
                }

                // Descontar inventario y registrar movimientos de salida exactamente una vez al cobrar
                val items = db.tableOrderDao().getItemsForOrderSync(order.id)
                items.forEach { item ->
                    db.productDao().updateStock(item.productId, -item.quantity)
                    val movement = StockMovement(
                        productId = item.productId,
                        productName = item.productName,
                        type = "SALIDA",
                        quantity = item.quantity,
                        reason = "Venta Salón Comanda #${order.comandaNumber} Mesa #${order.tableNumber}",
                        recordedBy = chargedBy,
                        timestamp = closedAt,
                        jornadaId = order.jornadaId
                    )
                    db.stockMovementDao().insertMovement(movement)
                    db.tableOrderDao().updateOrderItem(item.copy(status = "ENTREGADO"))
                }

                val updated = order.copy(
                    status = "COBRADA",
                    closedAt = closedAt,
                    paymentMethod = paymentMethod,
                    cashReceived = cashReceived,
                    changeGiven = changeGiven,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    originalAmount = order.totalAmount,
                    amountInCurrency = amountInCurrency
                )
                db.tableOrderDao().updateOrder(updated)
                closedOrdersList.add(updated)
            }

            val activeJornada = db.jornadaDao().getActiveJornadaSync()
            if (activeJornada != null) {
                val updatedJornada = activeJornada.copy(
                    totalSales = activeJornada.totalSales + totalCharged,
                    expectedCash = activeJornada.expectedCash + totalEfectivoCharged
                )
                db.jornadaDao().updateJornada(updatedJornada)
            }

            val comandaNumsStr = closedOrdersList.joinToString(", ") { "#${it.comandaNumber}" }
            db.bitacoraDao().insertEntry(
                BitacoraEntry(
                    title = "Cobro Mesa $tableNumber",
                    content = "Mesa $tableNumber cobrada por $chargedBy ($paymentMethod: $${"%.2f".format(totalCharged)} CUP). Comandas: $comandaNumsStr. Descuento de stock applied.",
                    category = "SALON",
                    authorUsername = chargedBy,
                    priority = "NORMAL"
                )
            )

            Result.success(closedOrdersList)
        }
    }

    suspend fun cobrarComandaIndividual(
        orderId: Long,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        chargedBy: String = "salon",
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0
    ): Result<TableOrder> = cobroMutex.withLock {
        db.withTransaction {
            val order = db.tableOrderDao().getOrderById(orderId)
                ?: return@withTransaction Result.failure(IllegalStateException("La comanda con ID $orderId no existe."))

            if (order.status == "COBRADA" || order.status == "CANCELADA") {
                return@withTransaction Result.failure(IllegalStateException("La comanda #${order.comandaNumber} ya está cobrada o cancelada."))
            }

            val closedAt = System.currentTimeMillis()
            var totalEfectivoCharged = 0.0
            if (paymentMethod == "EFECTIVO") {
                totalEfectivoCharged = order.totalAmount
            }

            // Descontar inventario y registrar movimientos de salida exactamente una vez al cobrar
            val items = db.tableOrderDao().getItemsForOrderSync(order.id)
            items.forEach { item ->
                db.productDao().updateStock(item.productId, -item.quantity)
                val movement = StockMovement(
                    productId = item.productId,
                    productName = item.productName,
                    type = "SALIDA",
                    quantity = item.quantity,
                    reason = "Venta Salón Comanda #${order.comandaNumber} Mesa #${order.tableNumber ?: "Llevar"}",
                    recordedBy = chargedBy,
                    timestamp = closedAt,
                    jornadaId = order.jornadaId
                )
                db.stockMovementDao().insertMovement(movement)
                db.tableOrderDao().updateOrderItem(item.copy(status = "ENTREGADO"))
            }

            val updated = order.copy(
                status = "COBRADA",
                closedAt = closedAt,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                currency = currency,
                exchangeRate = exchangeRate,
                originalAmount = order.totalAmount,
                amountInCurrency = amountInCurrency
            )
            db.tableOrderDao().updateOrder(updated)

            val activeJornada = db.jornadaDao().getActiveJornadaSync()
            if (activeJornada != null) {
                val updatedJornada = activeJornada.copy(
                    totalSales = activeJornada.totalSales + order.totalAmount,
                    expectedCash = activeJornada.expectedCash + totalEfectivoCharged
                )
                db.jornadaDao().updateJornada(updatedJornada)
            }

            val targetDisplay = if (order.tableNumber == null) "Para Llevar" else "Mesa ${order.tableNumber}"
            db.bitacoraDao().insertEntry(
                BitacoraEntry(
                    title = "Cobro Comanda #${order.comandaNumber}",
                    content = "Comanda #${order.comandaNumber} ($targetDisplay) cobrada por $chargedBy ($paymentMethod: $${"%.2f".format(order.totalAmount)} CUP). Descuento de stock aplicado.",
                    category = "SALON",
                    authorUsername = chargedBy,
                    priority = "NORMAL"
                )
            )

            Result.success(updated)
        }
    }

    suspend fun createOrder(tableNumber: Int, customerName: String, waiterUsername: String, jornadaId: Long): Long {
        val order = TableOrder(
            tableNumber = tableNumber,
            customerName = customerName,
            waiterUsername = waiterUsername,
            jornadaId = jornadaId
        )
        return db.tableOrderDao().insertOrder(order)
    }

    suspend fun addItemToOrder(orderId: Long, product: Product, quantity: Int, notes: String) {
        val item = OrderItem(
            orderId = orderId,
            productId = product.id,
            productName = product.name,
            unitPrice = product.price,
            quantity = quantity,
            destination = product.destination,
            notes = notes,
            productCode = product.code
        )
        db.tableOrderDao().insertOrderItem(item)
    }

    suspend fun closeAndPayOrder(order: TableOrder, paymentMethod: String, tip: Double) {
        val updated = order.copy(
            closedAt = System.currentTimeMillis(),
            status = "COBRADA",
            paymentMethod = paymentMethod,
            tip = tip
        )
        db.tableOrderDao().updateOrder(updated)
        val activeJornada = db.jornadaDao().getActiveJornadaSync()
        if (activeJornada != null) {
            val updatedJornada = activeJornada.copy(
                totalSales = activeJornada.totalSales + order.totalAmount,
                expectedCash = activeJornada.expectedCash + if (paymentMethod == "EFECTIVO") order.totalAmount else 0.0
            )
            db.jornadaDao().updateJornada(updatedJornada)
        }
    }

    suspend fun closeAndPayOrderDirect(order: TableOrder) {
        db.tableOrderDao().updateOrder(order)
        val activeJornada = db.jornadaDao().getActiveJornadaSync()
        if (activeJornada != null) {
            val updatedJornada = activeJornada.copy(
                totalSales = activeJornada.totalSales + order.totalAmount,
                expectedCash = activeJornada.expectedCash + if (order.paymentMethod == "EFECTIVO") order.totalAmount else 0.0
            )
            db.jornadaDao().updateJornada(updatedJornada)
        }
    }

    suspend fun closeAndPayOrdersBatch(orders: List<TableOrder>) {
        if (orders.isEmpty()) return
        db.withTransaction {
            orders.forEach { order ->
                db.tableOrderDao().updateOrder(order)
            }
            val activeJornada = db.jornadaDao().getActiveJornadaSync()
            if (activeJornada != null) {
                val totalBatchSales = orders.sumOf { it.totalAmount }
                val cashBatch = orders.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.totalAmount }
                val updatedJornada = activeJornada.copy(
                    totalSales = activeJornada.totalSales + totalBatchSales,
                    expectedCash = activeJornada.expectedCash + cashBatch
                )
                db.jornadaDao().updateJornada(updatedJornada)
            }
        }
    }

    suspend fun registrarCobroComanda(order: TableOrder, items: List<OrderItem>) {
        db.withTransaction {
            val orderId = db.tableOrderDao().insertOrder(order)
            items.forEach { item ->
                val updatedItem = item.copy(orderId = orderId)
                db.tableOrderDao().insertOrderItem(updatedItem)
                db.productDao().updateStock(item.productId, -item.quantity)
                
                // Registrar movimiento de salida
                db.stockMovementDao().insertMovement(
                    StockMovement(
                        productId = item.productId,
                        productName = item.productName,
                        type = "SALIDA",
                        quantity = item.quantity,
                        reason = "Venta Directa Cajero (Comanda #${order.comandaNumber})",
                        recordedBy = order.waiterUsername,
                        timestamp = System.currentTimeMillis(),
                        jornadaId = order.jornadaId
                    )
                )
            }
            val activeJornada = db.jornadaDao().getActiveJornadaSync()
            if (activeJornada != null) {
                val cashAmount = when (order.paymentMethod) {
                    "EFECTIVO" -> order.totalAmount
                    "MIXTO" -> (order.cashReceived - order.changeGiven).coerceAtLeast(0.0)
                    else -> 0.0
                }
                val updatedJornada = activeJornada.copy(
                    totalSales = activeJornada.totalSales + order.totalAmount,
                    expectedCash = activeJornada.expectedCash + cashAmount
                )
                db.jornadaDao().updateJornada(updatedJornada)
            }
        }
    }

    suspend fun updateOrderItemStatus(item: OrderItem, newStatus: String) {
        db.tableOrderDao().updateOrderItem(item.copy(status = newStatus))
    }

    // Move & Batches
    val allMovements: Flow<List<StockMovement>> = db.stockMovementDao().getAllMovements()
    suspend fun recordMovement(movement: StockMovement) = db.stockMovementDao().insertMovement(movement)

    suspend fun recordBarraEntrada(
        productId: Long,
        productName: String,
        quantity: Int,
        reason: String,
        recordedBy: String,
        jornadaId: Long
    ) {
        val movement = StockMovement(
            productId = productId,
            productName = productName,
            type = "ENTRADA",
            quantity = quantity,
            reason = reason,
            recordedBy = recordedBy,
            timestamp = System.currentTimeMillis(),
            jornadaId = jornadaId
        )
        db.stockMovementDao().insertMovement(movement)
        db.productDao().updateStock(productId, quantity)

        db.bitacoraDao().insertEntry(
            BitacoraEntry(
                title = "Entrada Inventario Barra",
                content = "Usuario $recordedBy registró entrada de $quantity ud. de $productName en Barra. Origen: $reason.",
                category = "MERCADERÍAS",
                authorUsername = recordedBy,
                priority = "NORMAL"
            )
        )
    }

    suspend fun createComandaBarra(order: TableOrder, items: List<OrderItem>): Long = comandaMutex.withLock {
        db.withTransaction {
            val assignedComandaNumber = if (order.comandaNumber > 0) {
                order.comandaNumber
            } else {
                db.tableOrderDao().getMaxComandaNumberForJornada(order.jornadaId) + 1
            }
            val finalOrder = order.copy(comandaNumber = assignedComandaNumber)
            val orderId = db.tableOrderDao().insertOrder(finalOrder)
            items.forEach { item ->
                val updatedItem = item.copy(orderId = orderId)
                db.tableOrderDao().insertOrderItem(updatedItem)
            }
            orderId
        }
    }

    suspend fun cobrarComandaBarra(
        orderId: Long,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        chargedBy: String,
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0
    ): Result<TableOrder> = cobroMutex.withLock {
        db.withTransaction {
            val order = db.tableOrderDao().getOrderById(orderId)
                ?: return@withTransaction Result.failure(IllegalStateException("No se encontró la comanda con ID $orderId"))

            if (order.status == "COBRADA") {
                return@withTransaction Result.failure(IllegalStateException("La comanda #${order.comandaNumber} (Mesa ${order.tableNumber}) ya fue cobrada previamente."))
            }

            val items = db.tableOrderDao().getItemsForOrderSync(orderId)
            val barraItems = items.filter { it.destination == "BARRA" }
            val targetItems = if (barraItems.isNotEmpty()) barraItems else items

            // Validar stock para cada producto de Barra
            for (item in targetItems) {
                val product = db.productDao().getProductById(item.productId)
                if (product == null) {
                    return@withTransaction Result.failure(IllegalStateException("El producto '${item.productName}' no existe en el catálogo."))
                }
                if (product.stock < item.quantity) {
                    val faltante = item.quantity - product.stock
                    return@withTransaction Result.failure(IllegalStateException("Existencia insuficiente para '${product.name}'. Disponible: ${product.stock} ud., Solicitado: ${item.quantity} ud., Faltante: $faltante ud."))
                }
            }

            val now = System.currentTimeMillis()
            val targetTableDesc = if ((order.tableNumber ?: 0) > 0) "Mesa #${order.tableNumber}" else "Para Llevar"

            // Descontar inventario y registrar movimientos de salida inequívocos
            for (item in targetItems) {
                db.productDao().updateStock(item.productId, -item.quantity)
                val movement = StockMovement(
                    productId = item.productId,
                    productName = item.productName,
                    type = "SALIDA",
                    quantity = item.quantity,
                    reason = "Venta Barra Comanda #${order.comandaNumber} $targetTableDesc",
                    recordedBy = chargedBy,
                    timestamp = now,
                    jornadaId = order.jornadaId
                )
                db.stockMovementDao().insertMovement(movement)
                db.tableOrderDao().updateOrderItem(item.copy(status = "ENTREGADO"))
            }

            val updatedOrder = order.copy(
                status = "COBRADA",
                closedAt = now,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                currency = currency,
                exchangeRate = exchangeRate,
                originalAmount = order.totalAmount,
                amountInCurrency = amountInCurrency
            )
            db.tableOrderDao().updateOrder(updatedOrder)

            val activeJornada = db.jornadaDao().getActiveJornadaSync()
            if (activeJornada != null) {
                val cashCollected = when (paymentMethod) {
                    "EFECTIVO" -> order.totalAmount
                    "MIXTO" -> (cashReceived - changeGiven).coerceAtLeast(0.0)
                    else -> 0.0
                }
                val updatedJornada = activeJornada.copy(
                    totalSales = activeJornada.totalSales + order.totalAmount,
                    expectedCash = activeJornada.expectedCash + cashCollected
                )
                db.jornadaDao().updateJornada(updatedJornada)
            }

            db.bitacoraDao().insertEntry(
                BitacoraEntry(
                    title = "Cobro Comanda Barra",
                    content = "Comanda #${order.comandaNumber} ($targetTableDesc) cobrada por $chargedBy ($paymentMethod: $${"%.2f".format(order.totalAmount)} CUP). Descuento de stock aplicado.",
                    category = "BARRA",
                    authorUsername = chargedBy,
                    priority = "NORMAL"
                )
            )

            Result.success(updatedOrder)
        }
    }

    val allBatches: Flow<List<ProductionBatch>> = db.productionBatchDao().getAllBatches()
    suspend fun recordBatch(batch: ProductionBatch) = db.productionBatchDao().insertBatch(batch)

    // Bitácora
    val allBitacoraEntries: Flow<List<BitacoraEntry>> = db.bitacoraDao().getAllEntries()
    suspend fun insertBitacora(entry: BitacoraEntry) = db.bitacoraDao().insertEntry(entry)

    val gastosGenerales: Flow<List<com.example.data.local.model.GastoGeneral>> = db.gastoGeneralDao().getAll()

    // Mercaderías & Movimientos
    val allMercaderias: Flow<List<Mercaderia>> = db.mercaderiaDao().getAll()
    suspend fun insertMercaderia(mercaderia: Mercaderia): Long = db.mercaderiaDao().insert(mercaderia)
    suspend fun updateMercaderia(mercaderia: Mercaderia) = db.mercaderiaDao().update(mercaderia)
    suspend fun deleteMercaderia(mercaderia: Mercaderia) = db.mercaderiaDao().delete(mercaderia)
    suspend fun deleteMercaderiaPermanently(mercaderia: Mercaderia) {
        db.mercaderiaDao().deleteMovimientosForMercaderia(mercaderia.id)
        db.mercaderiaDao().delete(mercaderia)
    }
    suspend fun deleteAllMercaderias() {
        db.mercaderiaDao().deleteAllMovimientos()
        db.mercaderiaDao().deleteAllMercaderias()
    }

    val allMovimientosMercaderia: Flow<List<MovimientoMercaderia>> = db.mercaderiaDao().getAllMovimientos()
    suspend fun insertMovimientoMercaderia(movimiento: MovimientoMercaderia): Long = db.mercaderiaDao().insertMovimiento(movimiento)
    suspend fun updateMovimientoMercaderia(movimiento: MovimientoMercaderia) = db.mercaderiaDao().updateMovimiento(movimiento)

    // Gastos Generales
    suspend fun insertGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().insert(gasto)
    suspend fun updateGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().update(gasto)
    suspend fun deleteGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().delete(gasto)

    // Inversiones
    val allInversiones: Flow<List<com.example.data.local.model.Inversion>> = db.inversionDao().getAll()
    suspend fun insertInversion(inversion: com.example.data.local.model.Inversion): Long = db.inversionDao().insert(inversion)
    suspend fun updateInversion(inversion: com.example.data.local.model.Inversion) = db.inversionDao().update(inversion)
    suspend fun deleteInversion(inversion: com.example.data.local.model.Inversion) = db.inversionDao().delete(inversion)

    // Tandas
    val allTandas: Flow<List<Tanda>> = db.tandaDao().getAll()
    suspend fun getAllTandasSync(): List<Tanda> = db.tandaDao().getAllSync()
    suspend fun insertTanda(tanda: Tanda): Long = db.tandaDao().insert(tanda)
    suspend fun updateTanda(tanda: Tanda) = db.tandaDao().update(tanda)

    // Movimientos Materia Prima
    val allMovimientosMateriaPrima: Flow<List<MovimientoMateriaPrima>> = db.movimientoMateriaPrimaDao().getAll()
    suspend fun insertMovimientoMateriaPrima(movimiento: MovimientoMateriaPrima): Long = db.movimientoMateriaPrimaDao().insert(movimiento)

    // Consumo Personal
    val allConsumoPersonal: Flow<List<ConsumoPersonalItem>> = db.consumoPersonalDao().getAllConsumoPersonal()
    fun getConsumoPersonalByJornada(jornadaId: Long): Flow<List<ConsumoPersonalItem>> = db.consumoPersonalDao().getConsumoPersonalByJornada(jornadaId)
    suspend fun insertConsumoPersonal(item: ConsumoPersonalItem): Long = db.consumoPersonalDao().insertConsumoPersonal(item)
    suspend fun deleteConsumoPersonal(id: Long) = db.consumoPersonalDao().deleteConsumoPersonal(id)
    suspend fun clearConsumoPersonalForJornada(jornadaId: Long) = db.consumoPersonalDao().clearConsumoPersonalForJornada(jornadaId)

    suspend fun closeJornadaSalonAndOpenNew(
        jornada: Jornada,
        closedBy: String,
        notes: String = "",
        totalVentas: Double = 0.0
    ): Long {
        val now = System.currentTimeMillis()
        val user = db.userDao().getUserByUsername(closedBy)
        val userRate = user?.montoPorProducto ?: 0.0
        val finalRate = if (jornada.utilidadSalonMontoUnitario > 0.0) jornada.utilidadSalonMontoUnitario else userRate

        val updated = jornada.copy(
            closedAt = now,
            isOpen = false,
            closedBy = closedBy,
            totalSales = totalVentas,
            notes = if (notes.isNotBlank()) notes else jornada.notes,
            utilidadSalonMontoUnitario = finalRate
        )
        db.jornadaDao().updateJornada(updated)

        val newJornada = Jornada(
            openedAt = now,
            initialCash = 0.0,
            expectedCash = 0.0,
            isOpen = true,
            openedBy = closedBy,
            utilidadSalonMontoUnitario = userRate
        )
        val newId = db.jornadaDao().insertJornada(newJornada)

        // Snapshot initial physical inventory for Barra products for the new jornada
        val barProducts = db.productDao().getAllProductsSync().filter { it.destination == "BARRA" }
        barProducts.forEach { p ->
            db.stockMovementDao().insertMovement(
                StockMovement(
                    productId = p.id,
                    productName = p.name,
                    type = "INVENTARIO_INICIAL",
                    quantity = p.stock,
                    reason = "Inventario Inicial Apertura Jornada #$newId (vía Salón)",
                    recordedBy = closedBy,
                    timestamp = now,
                    jornadaId = newId
                )
            )
        }

        db.bitacoraDao().insertEntry(
            BitacoraEntry(
                title = "Cierre de Jornada de Salón",
                content = "Jornada #${jornada.id} cerrada y archivada por $closedBy (Ventas Totales: $${"%.2f".format(totalVentas)} CUP). Se inició la nueva Jornada #$newId.",
                category = "SALON",
                authorUsername = closedBy
            )
        )
        return newId
    }

    suspend fun closeJornadaBarraAndOpenNew(
        jornada: Jornada,
        closedBy: String,
        notes: String = "",
        totalVentas: Double = 0.0
    ): Long {
        val now = System.currentTimeMillis()
        val user = db.userDao().getUserByUsername(closedBy)
        val userRate = user?.montoPorProducto ?: 0.0
        val finalRate = if (jornada.utilidadSalonMontoUnitario > 0.0) jornada.utilidadSalonMontoUnitario else userRate

        val updated = jornada.copy(
            closedAt = now,
            isOpen = false,
            closedBy = closedBy,
            totalSales = totalVentas,
            notes = if (notes.isNotBlank()) notes else jornada.notes,
            utilidadSalonMontoUnitario = finalRate
        )
        db.jornadaDao().updateJornada(updated)

        val newJornada = Jornada(
            openedAt = now,
            initialCash = 0.0,
            expectedCash = 0.0,
            isOpen = true,
            openedBy = closedBy,
            utilidadSalonMontoUnitario = userRate
        )
        val newId = db.jornadaDao().insertJornada(newJornada)

        // Snapshot initial physical inventory for Barra products for the new jornada
        val barProducts = db.productDao().getAllProductsSync().filter { it.destination == "BARRA" }
        barProducts.forEach { p ->
            db.stockMovementDao().insertMovement(
                StockMovement(
                    productId = p.id,
                    productName = p.name,
                    type = "INVENTARIO_INICIAL",
                    quantity = p.stock,
                    reason = "Inventario Inicial Apertura Jornada #$newId",
                    recordedBy = closedBy,
                    timestamp = now,
                    jornadaId = newId
                )
            )
        }

        db.bitacoraDao().insertEntry(
            BitacoraEntry(
                title = "Cierre de Jornada de Barra",
                content = "Jornada #${jornada.id} de Barra cerrada y archivada por $closedBy (Ventas: $${"%.2f".format(totalVentas)} CUP). Se inició la nueva Jornada #$newId. $notes",
                category = "BARRA",
                authorUsername = closedBy
            )
        )
        return newId
    }

    suspend fun restoreSalonBackup(
        jornadas: List<Jornada>,
        orders: List<TableOrder>,
        orderItems: List<OrderItem>,
        products: List<Product> = emptyList(),
        bitacoraEntries: List<BitacoraEntry> = emptyList()
    ) {
        db.withTransaction {
            // 1. Wipe current operational state for Salon
            db.tableOrderDao().deleteAllOrders()
            db.tableOrderDao().deleteAllOrderItems()
            db.jornadaDao().deleteAllJornadas()

            // 2. Insert atomic state from backup
            if (jornadas.isNotEmpty()) {
                db.jornadaDao().insertJornadas(jornadas)
            }
            if (orders.isNotEmpty()) {
                db.tableOrderDao().insertOrders(orders)
            }
            if (orderItems.isNotEmpty()) {
                db.tableOrderDao().insertOrderItems(orderItems)
            }
            if (products.isNotEmpty()) {
                db.productDao().insertProducts(products)
            }
            if (bitacoraEntries.isNotEmpty()) {
                db.bitacoraDao().deleteSalonCuadreEntries()
                db.bitacoraDao().insertEntries(bitacoraEntries)
            }
        }
    }

    suspend fun restoreBarraBackup(
        jornadas: List<Jornada>,
        products: List<Product>,
        stockMovements: List<StockMovement>,
        orders: List<TableOrder>,
        orderItems: List<OrderItem>,
        bitacoraEntries: List<BitacoraEntry>
    ) {
        db.withTransaction {
            // 1. Wipe current operational state for Barra
            db.tableOrderDao().deleteAllOrders()
            db.tableOrderDao().deleteAllOrderItems()
            db.jornadaDao().deleteAllJornadas()
            db.stockMovementDao().deleteAllMovements()

            // 2. Insert atomic state from backup
            if (jornadas.isNotEmpty()) {
                db.jornadaDao().insertJornadas(jornadas)
            }
            if (products.isNotEmpty()) {
                products.forEach { p ->
                    val existing = db.productDao().getProductById(p.id)
                    if (existing != null) {
                        db.productDao().updateProduct(existing.copy(stock = p.stock, price = p.price, isAvailable = p.isAvailable))
                    } else {
                        db.productDao().insertProduct(p)
                    }
                }
            }
            if (stockMovements.isNotEmpty()) {
                db.stockMovementDao().insertMovements(stockMovements)
            }
            if (orders.isNotEmpty()) {
                db.tableOrderDao().insertOrders(orders)
            }
            if (orderItems.isNotEmpty()) {
                db.tableOrderDao().insertOrderItems(orderItems)
            }
            if (bitacoraEntries.isNotEmpty()) {
                db.bitacoraDao().deleteBarraCuadreEntries()
                db.bitacoraDao().insertEntries(bitacoraEntries)
            }
        }
    }

    suspend fun restoreCajeroBackup(
        jornadas: List<Jornada>,
        products: List<Product> = emptyList(),
        orders: List<TableOrder> = emptyList(),
        orderItems: List<OrderItem> = emptyList(),
        transferencias: List<Transferencia> = emptyList(),
        bitacoraEntries: List<BitacoraEntry> = emptyList(),
        consumoPersonalList: List<ConsumoPersonalItem> = emptyList()
    ) {
        db.withTransaction {
            // 1. Wipe current operational state for Cajero
            db.tableOrderDao().deleteAllOrders()
            db.tableOrderDao().deleteAllOrderItems()
            db.jornadaDao().deleteAllJornadas()
            db.transferenciaDao().deleteAllTransferencias()
            db.consumoPersonalDao().deleteAllConsumoPersonal()

            // 2. Insert atomic state from backup
            if (jornadas.isNotEmpty()) {
                db.jornadaDao().insertJornadas(jornadas)
            }
            if (products.isNotEmpty()) {
                products.forEach { p ->
                    val existing = db.productDao().getProductById(p.id)
                    if (existing != null) {
                        db.productDao().updateProduct(existing.copy(price = p.price, isAvailable = p.isAvailable))
                    } else {
                        db.productDao().insertProduct(p)
                    }
                }
            }
            if (orders.isNotEmpty()) {
                db.tableOrderDao().insertOrders(orders)
            }
            if (orderItems.isNotEmpty()) {
                db.tableOrderDao().insertOrderItems(orderItems)
            }
            if (transferencias.isNotEmpty()) {
                db.transferenciaDao().insertTransferencias(transferencias)
            }
            if (bitacoraEntries.isNotEmpty()) {
                db.bitacoraDao().deleteCajeroCuadreEntries()
                db.bitacoraDao().insertEntries(bitacoraEntries)
            }
            if (consumoPersonalList.isNotEmpty()) {
                db.consumoPersonalDao().insertConsumoPersonalList(consumoPersonalList)
            }
        }
    }

    suspend fun initializeOperationalData() {
        db.withTransaction {
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
        }
    }

    // Transferencias SMS
    val allTransferencias: Flow<List<Transferencia>> = db.transferenciaDao().getAllTransferencias()

    fun getTransferenciasByJornada(jornadaId: Long): Flow<List<Transferencia>> =
        db.transferenciaDao().getTransferenciasByJornada(jornadaId)

    suspend fun getTransferenciasByJornadaSync(jornadaId: Long): List<Transferencia> =
        db.transferenciaDao().getTransferenciasByJornadaSync(jornadaId)

    suspend fun getTransferenciaByTransactionNumber(txNumber: String): Transferencia? =
        db.transferenciaDao().getByTransactionNumber(txNumber)

    suspend fun insertTransferencia(transferencia: Transferencia): Long {
        return db.transferenciaDao().insertTransferencia(transferencia)
    }

    suspend fun updateTransferencia(transferencia: Transferencia) {
        db.transferenciaDao().updateTransferencia(transferencia)
    }

    suspend fun deleteTransferencia(id: Long) {
        db.transferenciaDao().deleteTransferencia(id)
    }

    suspend fun deleteAllTransferencias() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        db.transferenciaDao().deleteAllTransferencias()
    }

    suspend fun asociarTransferenciaAComanda(
        transferencia: Transferencia,
        orderId: Long
    ): Result<TableOrder> = cobroMutex.withLock {
        db.withTransaction {
            val existing = db.transferenciaDao().getByTransactionNumber(transferencia.transactionNumber)
            if (existing != null && existing.comandaId != null && existing.comandaId != orderId && existing.status == "ASOCIADA") {
                return@withTransaction Result.failure(Exception("La transferencia con transacción ${transferencia.transactionNumber} ya está asociada a la Comanda #${existing.comandaNumber}."))
            }

            val order = db.tableOrderDao().getOrderById(orderId)
                ?: return@withTransaction Result.failure(Exception("Comanda no encontrada"))

            val baseTransfer = existing ?: transferencia
            val existingTransfers = db.transferenciaDao().getTransferenciasForComanda(orderId)
                .filter { it.id != baseTransfer.id && it.transactionNumber != baseTransfer.transactionNumber }
            val totalTransfersPrior = existingTransfers.sumOf { it.amount }
            val totalTransfersNow = totalTransfersPrior + baseTransfer.amount

            val isFullyCovered = totalTransfersNow >= order.totalAmount
            val transferStatus = if (isFullyCovered) "ASOCIADA" else "PARCIAL"

            val transferToSave = baseTransfer.copy(
                comandaId = order.id,
                comandaNumber = order.comandaNumber,
                status = transferStatus,
                phoneNumber = if (transferencia.phoneNumber.isNotBlank()) transferencia.phoneNumber else baseTransfer.phoneNumber,
                titularName = if (transferencia.titularName.isNotBlank()) transferencia.titularName else baseTransfer.titularName,
                titularCi = if (transferencia.titularCi.isNotBlank()) transferencia.titularCi else baseTransfer.titularCi
            )

            if (transferToSave.id == 0L) {
                db.transferenciaDao().insertTransferencia(transferToSave)
            } else {
                db.transferenciaDao().updateTransferencia(transferToSave)
            }

            if (isFullyCovered) {
                val updatedOrder = order.copy(
                    status = "COBRADA",
                    closedAt = System.currentTimeMillis(),
                    paymentMethod = if (totalTransfersPrior == 0.0) "TRANSFERENCIA" else "MIXTO"
                )
                db.tableOrderDao().updateOrder(updatedOrder)

                val activeJornada = db.jornadaDao().getActiveJornadaSync()
                if (activeJornada != null) {
                    val updatedJornada = activeJornada.copy(
                        totalSales = activeJornada.totalSales + order.totalAmount
                    )
                    db.jornadaDao().updateJornada(updatedJornada)
                }
                Result.success(updatedOrder)
            } else {
                Result.success(order)
            }
        }
    }

    // Payment Proposals
    val allPaymentProposals: Flow<List<PaymentProposal>> = db.paymentProposalDao().getAllProposalsFlow()
    suspend fun getAllPaymentProposalsSync(): List<PaymentProposal> = db.paymentProposalDao().getAllProposalsSync()
    suspend fun getPaymentProposalByUsername(username: String): PaymentProposal? = db.paymentProposalDao().getProposalByUsername(username)
    suspend fun insertPaymentProposal(proposal: PaymentProposal) = db.paymentProposalDao().insertProposal(proposal)
    suspend fun updatePaymentProposal(proposal: PaymentProposal) = db.paymentProposalDao().updateProposal(proposal)
    suspend fun deletePaymentProposal(username: String) = db.paymentProposalDao().deleteProposal(username)
    suspend fun deleteAllPaymentProposals() = db.paymentProposalDao().deleteAllProposals()

    // Personal Contratado
    val allPersonalContratado: Flow<List<PersonalContratado>> = db.personalContratadoDao().getAllPersonalFlow()
    suspend fun getAllPersonalContratadoSync(): List<PersonalContratado> = db.personalContratadoDao().getAllPersonalSync()
    suspend fun countDuenos(): Int = db.personalContratadoDao().countDuenos()
    suspend fun countDuenosExcluding(excludeId: Long): Int = db.personalContratadoDao().countDuenosExcluding(excludeId)

    suspend fun insertPersonalContratado(personal: PersonalContratado): Long {
        if (personal.tieneAccesoApp && (personal.role.uppercase() == "DUENO" || personal.role.uppercase() == "DUEÑO")) {
            val count = countDuenos()
            if (count >= 3) {
                throw IllegalStateException("Límite alcanzado: El negocio solo puede tener un máximo de 3 DUEÑOS en total (el principal y hasta 2 adicionales).")
            }
        }
        val id = db.personalContratadoDao().insertPersonal(personal)
        syncPersonalToUser(personal.copy(id = id))
        return id
    }

    suspend fun updatePersonalContratado(personal: PersonalContratado) {
        if (personal.tieneAccesoApp && (personal.role.uppercase() == "DUENO" || personal.role.uppercase() == "DUEÑO")) {
            val count = countDuenosExcluding(personal.id)
            if (count >= 3) {
                throw IllegalStateException("Límite alcanzado: El negocio solo puede tener un máximo de 3 DUEÑOS en total (el principal y hasta 2 adicionales).")
            }
        }
        db.personalContratadoDao().updatePersonal(personal)
        syncPersonalToUser(personal)
    }

    suspend fun deletePersonalContratado(personal: PersonalContratado) {
        db.personalContratadoDao().deletePersonal(personal)
        if (personal.username.isNotBlank()) {
            db.userDao().deleteUser(personal.username)
        }
    }

    private suspend fun syncPersonalToUser(personal: PersonalContratado) {
        if (personal.tieneAccesoApp && personal.username.isNotBlank()) {
            val role = when (personal.role.uppercase()) {
                "ADMIN", "ADMINISTRADOR" -> com.example.data.local.model.UserRole.ADMIN
                "DUENO", "DUEÑO" -> com.example.data.local.model.UserRole.DUENO
                "CAJERO" -> com.example.data.local.model.UserRole.CAJERO
                "COCINA" -> com.example.data.local.model.UserRole.COCINA
                "DEPENDIENTE" -> com.example.data.local.model.UserRole.DEPENDIENTE
                "SALON" -> com.example.data.local.model.UserRole.SALON
                "BARRA" -> com.example.data.local.model.UserRole.BARRA
                else -> com.example.data.local.model.UserRole.fromString(personal.role)
            }
            val user = com.example.data.local.model.User(
                username = personal.username.trim().lowercase(),
                fullName = personal.nombreCompleto.trim(),
                passwordHash = personal.passwordHash,
                role = role,
                montoPorProducto = personal.montoPorProducto,
                isActive = personal.isActive,
                telefono = personal.movil,
                permisoProduccion = personal.permisoProduccion,
                permisoMercancias = personal.permisoMercancias,
                permisoPersonal = personal.permisoPersonal,
                permisoControlNegocio = personal.permisoControlNegocio
            )
            db.userDao().insertUser(user)
        } else if (!personal.tieneAccesoApp && personal.username.isNotBlank()) {
            db.userDao().deleteUser(personal.username)
        }
    }

    suspend fun deleteAllPersonalContratado() = db.personalContratadoDao().deleteAllPersonal()

    suspend fun updateJornada(jornada: Jornada) = db.jornadaDao().updateJornada(jornada)
    suspend fun deleteJornadaById(id: Long) = db.jornadaDao().deleteJornadaById(id)
}
