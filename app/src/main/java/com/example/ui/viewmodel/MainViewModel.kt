package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.model.*
import com.example.data.repository.AppRepository
import com.example.ui.screens.admin.convertToBaseQty
import com.example.ui.screens.admin.getNormalizedCost
import com.example.util.ParsedTransferSms
import com.example.util.SmsTransferBus
import com.example.util.SmsTransferParser
import com.example.util.toSha256
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

data class PendingComandaSmsAlert(
    val comandaNumber: Int,
    val senderUsername: String,
    val senderFullName: String,
    val itemsSummary: List<String>,
    val totalAmount: Double,
    val orderId: Long,
    val isUpdate: Boolean = false
)

data class MainUiState(
    val currentUser: User? = null,
    val adminBackupUser: User? = null,
    val activeJornada: Jornada? = null,
    val allJornadas: List<Jornada> = emptyList(),
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val users: List<User> = emptyList(),
    val openOrders: List<TableOrder> = emptyList(),
    val bitacoraEntries: List<BitacoraEntry> = emptyList(),
    val productionBatches: List<ProductionBatch> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val deviceId: String = "",
    val businessName: String = "Restaurante El Buen Sabor",
    val businessConfig: ConfiguracionNegocio? = null,
    val generalConfig: ConfiguracionGeneral? = null,
    val materiasPrimas: List<MateriaPrima> = emptyList(),
    val productosElaborados: List<ProductoElaborado> = emptyList(),
    val recetaIngredientes: List<RecetaIngrediente> = emptyList(),
    val gastosGenerales: List<com.example.data.local.model.GastoGeneral> = emptyList(),
    val inversiones: List<com.example.data.local.model.Inversion> = emptyList(),
    val mercaderias: List<Mercaderia> = emptyList(),
    val movimientosMercaderia: List<MovimientoMercaderia> = emptyList(),
    val tandas: List<Tanda> = emptyList(),
    val movimientosMateriaPrima: List<MovimientoMateriaPrima> = emptyList(),
    val allOrders: List<TableOrder> = emptyList(),
    val allOrderItems: List<OrderItem> = emptyList(),
    val consumoPersonalList: List<ConsumoPersonalItem> = emptyList(),
    val allTransferencias: List<Transferencia> = emptyList(),
    val paymentProposals: List<PaymentProposal> = emptyList(),
    val personalContratado: List<PersonalContratado> = emptyList(),
    val pendingTransferAlert: ParsedTransferSms? = null,
    val showTransferAlert: Boolean = false,
    val transferAlertDuplicateMsg: String? = null,
    val pendingComandaAlert: PendingComandaSmsAlert? = null,
    val showComandaAlert: Boolean = false,
    val salonTableCount: Int = 12,
    val tarifasPagoBebidas: com.example.util.TarifasPagoBebidas = com.example.util.TarifasPagoBebidas(),
    val transferenciasClasificacionMap: Map<Long, Pair<Double, Double>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val updateStatusText: String? = null
) {
    val catalogoProducts: List<Product> get() {
        val elabProdIds = productosElaborados.map { it.productId }.toSet()
        val mercProdIds = mercaderias.map { it.productId }.toSet()
        return products.filter { p ->
            if (p.destination == "BARRA") {
                mercProdIds.contains(p.id)
            } else if (p.destination == "COCINA") {
                elabProdIds.contains(p.id)
            } else {
                elabProdIds.contains(p.id) || mercProdIds.contains(p.id)
            }
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var repository: AppRepository
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var flowCollectionJob: kotlinx.coroutines.Job? = null

    private fun startCollectingFlows() {
        flowCollectionJob?.cancel()
        flowCollectionJob = viewModelScope.launch {
            launch {
                repository.activeJornada.collect { jornada ->
                    _uiState.update { it.copy(activeJornada = jornada) }
                    if (jornada != null && jornada.isOpen) {
                        processPendingSmsComandas()
                    }
                }
            }
            launch {
                repository.allJornadas.collect { list ->
                    _uiState.update { it.copy(allJornadas = list) }
                }
            }
            launch {
                repository.gastosGenerales.collect { list ->
                    _uiState.update { it.copy(gastosGenerales = list) }
                }
            }
            launch {
                repository.allInversiones.collect { list ->
                    _uiState.update { it.copy(inversiones = list) }
                }
            }
            launch {
                var isAssigningCodes = false
                repository.allProducts.collect { list ->
                    _uiState.update { it.copy(products = list) }
                    // Auto-assign codes to any products missing code (legacy protection)
                    if (!isAssigningCodes) {
                        val unassigned = list.filter { it.code.trim().isBlank() }
                        if (unassigned.isNotEmpty()) {
                            isAssigningCodes = true
                            withContext(Dispatchers.IO) {
                                val working = list.toMutableList()
                                for (p in unassigned) {
                                    val dest = if (p.destination.isNotBlank()) p.destination else if (com.example.util.ProductCodeHelper.isCocina(p.destination, p.category, p.code)) "COCINA" else "BARRA"
                                    val nextCode = com.example.util.ProductCodeHelper.generateNextProductCode(dest, p.category, working)
                                    val updated = p.copy(code = nextCode, destination = dest)
                                    working.add(updated)
                                    repository.updateProduct(updated)
                                }
                            }
                            isAssigningCodes = false
                        }
                    }
                }
            }
            launch {
                repository.allCategories.collect { list ->
                    _uiState.update { it.copy(categories = list) }
                }
            }
            launch {
                repository.allUsers.collect { list ->
                    _uiState.update { it.copy(users = list) }
                }
            }
            launch {
                repository.openOrders.collect { list ->
                    _uiState.update { it.copy(openOrders = list) }
                }
            }
            launch {
                repository.allBitacoraEntries.collect { list ->
                    _uiState.update { it.copy(bitacoraEntries = list) }
                }
            }
            launch {
                repository.allBatches.collect { list ->
                    _uiState.update { it.copy(productionBatches = list) }
                }
            }
            launch {
                repository.allMovements.collect { list ->
                    _uiState.update { it.copy(stockMovements = list) }
                }
            }
            launch {
                repository.businessConfig.collect { config ->
                    _uiState.update {
                        it.copy(
                            businessConfig = config,
                            businessName = config?.nombreNegocio?.ifBlank { "Pizzas Factory" } ?: "Pizzas Factory"
                        )
                    }
                }
            }
            launch {
                repository.generalConfig.collect { config ->
                    _uiState.update { it.copy(generalConfig = config) }
                }
            }
            launch {
                repository.allMateriasPrimas.collect { list ->
                    _uiState.update { it.copy(materiasPrimas = list) }
                }
            }
            launch {
                repository.allProductosElaborados.collect { list ->
                    _uiState.update { it.copy(productosElaborados = list) }
                }
            }
            launch {
                repository.allRecetaIngredientes.collect { list ->
                    _uiState.update { it.copy(recetaIngredientes = list) }
                }
            }
            launch {
                repository.allMercaderias.collect { list ->
                    _uiState.update { it.copy(mercaderias = list) }
                }
            }
            launch {
                repository.allMovimientosMercaderia.collect { list ->
                    _uiState.update { it.copy(movimientosMercaderia = list) }
                }
            }
            launch {
                repository.allTandas.collect { list ->
                    _uiState.update { it.copy(tandas = list) }
                }
            }
            launch {
                repository.allMovimientosMateriaPrima.collect { list ->
                    _uiState.update { it.copy(movimientosMateriaPrima = list) }
                }
            }
            launch {
                repository.allOrders.collect { list ->
                    _uiState.update { it.copy(allOrders = list) }
                }
            }
            launch {
                repository.allOrderItems.collect { list ->
                    _uiState.update { it.copy(allOrderItems = list) }
                }
            }
            launch {
                repository.allConsumoPersonal.collect { list ->
                    _uiState.update { it.copy(consumoPersonalList = list) }
                }
            }
            launch {
                repository.allTransferencias.collect { list ->
                    _uiState.update { it.copy(allTransferencias = list) }
                }
            }
            launch {
                repository.allPaymentProposals.collect { list ->
                    _uiState.update { it.copy(paymentProposals = list) }
                }
            }
            launch {
                repository.allPersonalContratado.collect { list ->
                    _uiState.update { it.copy(personalContratado = list) }
                }
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = AppRepository(db)

        val deterministicDvc = com.example.util.DeviceIdentity.getDeterministicDeviceId(application)
        val savedDeviceId = sharedPreferences.getString("device_id_code", null)
        if (savedDeviceId != deterministicDvc) {
            sharedPreferences.edit().putString("device_id_code", deterministicDvc).apply()
        }
        val finalDeviceId = deterministicDvc

        val savedTableCount = sharedPreferences.getInt("salon_tables_count_global", sharedPreferences.getInt("salon_tables_count_default", 12))
        val savedTarifasBebidas = com.example.util.BebidasTarifasPreferences.getTarifas(application)
        _uiState.update { it.copy(deviceId = finalDeviceId, salonTableCount = savedTableCount, tarifasPagoBebidas = savedTarifasBebidas) }

        // Check for active session (24 hours inactivity timeout)
        viewModelScope.launch {
            val loggedInUsername = sharedPreferences.getString("loggedInUsername", null)
            val lastActivityTimestamp = sharedPreferences.getLong("lastActivityTimestamp", 0L)
            
            if (loggedInUsername != null) {
                val now = System.currentTimeMillis()
                val sessionDurationMillis = 24L * 60 * 60 * 1000 // 24 hours
                if (now - lastActivityTimestamp < sessionDurationMillis) {
                    val user = repository.getUserByUsername(loggedInUsername)
                    if (user != null && user.isActive) {
                        loadSalonTableCountForUser(user.username)
                        _uiState.update { it.copy(currentUser = user) }
                        
                        // Update activity timestamp since the app is being opened and used
                        sharedPreferences.edit().putLong("lastActivityTimestamp", System.currentTimeMillis()).apply()
                        
                        checkDailyDataUpdates()
                    } else {
                        logout() // Invalid user, clear session
                    }
                } else {
                    logout() // Session expired due to 24 hours of inactivity
                }
            }
        }

        startCollectingFlows()

        // Normalización preventiva de cuentas locales y verificación de la cuenta ADMINISTRADOR:
        // 1. Comprueba y asegura la existencia de la cuenta ADMINISTRADOR local ("admin" / "admin26" inicial).
        // 2. Elimina duplicados que sólo difieran por mayúsculas/minúsculas.
        // 3. Normaliza cuentas DUEÑO históricas.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.ensureAdminUserExists(repository.getDatabase())
                val allUsers = repository.getDatabase().userDao().getAllUsersSync()
                val seenUsernames = mutableMapOf<String, User>()
                for (u in allUsers) {
                    val key = u.username.lowercase().trim()
                    val existing = seenUsernames[key]
                    if (existing != null) {
                        repository.getDatabase().userDao().deleteUser(u.username)
                    } else {
                        seenUsernames[key] = u
                        if (u.role == UserRole.DUENO && u.authorizedDeviceId != null) {
                            repository.getDatabase().userDao().updateUser(u.copy(authorizedDeviceId = null))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Listen for incoming transfer SMS
        viewModelScope.launch {
            SmsTransferBus.incomingTransfers.collect { parsed ->
                handleIncomingTransferSms(parsed)
            }
        }

        // Listen for incoming comanda SMS
        viewModelScope.launch {
            com.example.util.SmsComandaBus.incomingComandas.collect { incoming ->
                processSingleComandaSms(incoming.queueId, incoming.senderPhone, incoming.smsText, 0L)
            }
        }

        // Listen for incoming account provisioning SMS events
        viewModelScope.launch {
            com.example.util.AccountProvisioningBus.events.collect { (accountUser, isUpdate) ->
                val action = if (isUpdate) "actualizada" else "creada"
                _uiState.update { 
                    it.copy(successMessage = "Cuenta $action por SMS: ${accountUser.username} (${accountUser.role.displayName})")
                }
            }
        }

        // Scan inbox for any pending account delivery SMS without requiring SuperAdmin
        scanPendingAccountSms()

        // Process any pending SMS comandas in queue
        processPendingSmsComandas()
    }

    fun scanPendingAccountSms() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.util.AccountProvisioningHelper.scanAndProcessInbox(getApplication())
            } catch (_: Exception) {}
        }
    }

    fun dismissComandaAlert() {
        _uiState.update { it.copy(showComandaAlert = false, pendingComandaAlert = null) }
    }

    fun processPendingSmsComandas() {
        viewModelScope.launch {
            try {
                val pendingList = withContext(Dispatchers.IO) {
                    repository.getPendingSmsComandasSync()
                }
                if (pendingList.isEmpty()) return@launch

                for (item in pendingList) {
                    processSingleComandaSms(item.id, item.senderPhone, item.smsText, item.jornadaId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleIncomingComandaSms(senderPhone: String, smsText: String) {
        viewModelScope.launch {
            processSingleComandaSms(0L, senderPhone, smsText, 0L)
        }
    }

    private suspend fun processSingleComandaSms(
        queueId: Long,
        senderPhone: String,
        smsText: String,
        targetJornadaId: Long
    ) {
        val users = _uiState.value.users.ifEmpty {
            withContext(Dispatchers.IO) { repository.getDatabase().userDao().getAllUsersSync() }
        }
        val authorizedUser = com.example.util.SmsComandaHelper.findAuthorizedUser(senderPhone, users)
        if (authorizedUser == null) {
            if (queueId > 0L) {
                withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "IGNORADA") }
            }
            _uiState.update {
                it.copy(errorMessage = "SMS de comanda ignorado: El remitente ($senderPhone) no está registrado ni autorizado en qusuarios.json.")
            }
            return
        }

        val parsed = com.example.util.SmsComandaHelper.parseComandaSms(smsText)
        if (parsed == null) {
            if (queueId > 0L) {
                withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "IGNORADA") }
            }
            _uiState.update {
                it.copy(errorMessage = "SMS recibido con formato de comanda inválido. Ignorado.")
            }
            return
        }

        var activeJornada = _uiState.value.activeJornada
        if (activeJornada == null || !activeJornada.isOpen) {
            val dbJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
            if (dbJornada != null && dbJornada.isOpen) {
                activeJornada = dbJornada
                _uiState.update { it.copy(activeJornada = dbJornada) }
            } else {
                // If there's no open jornada right now, keep as PENDIENTE in Room.
                // It will be processed automatically as soon as Caja opens a jornada.
                return
            }
        }
        val activeJornadaId = activeJornada.id

        // Catalog lookup and decoding
        val catalogProducts = _uiState.value.products.ifEmpty {
            withContext(Dispatchers.IO) { repository.getDatabase().productDao().getAllProductsSync() }
        }

        val orderItemsToCreate = mutableListOf<OrderItem>()
        val humanReadableSummaries = mutableListOf<String>()
        var totalCocina = 0.0
        var totalBarra = 0.0
        var totalAmount = 0.0

        for (token in parsed.itemTokens) {
            val product = catalogProducts.firstOrNull { it.code.trim().equals(token.productCode.trim(), ignoreCase = true) }
            if (product == null) {
                if (queueId > 0L) {
                    withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "IGNORADA") }
                }
                _uiState.update {
                    it.copy(errorMessage = "ALERTA CAJERO: La comanda #${parsed.comandaNumber} contiene un producto no reconocido (${token.productCode}). Comanda no procesada.")
                }
                return
            }

            // Decode agregados
            val availableAgregados = com.example.ui.components.parseProductAgregados(product.agregadosList)
            val matchedAgregados = mutableListOf<com.example.ui.components.ProductAgregadoItem>()

            for (code in token.agregadosCodes) {
                val codeInt = code.toIntOrNull() ?: -1
                val match = availableAgregados.firstOrNull { ag ->
                    ag.id.trim() == code ||
                    ag.id.trim() == String.format("%02d", codeInt) ||
                    (availableAgregados.indexOf(ag) + 1) == codeInt
                }
                if (match == null) {
                    if (queueId > 0L) {
                        withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "IGNORADA") }
                    }
                    _uiState.update {
                        it.copy(errorMessage = "ALERTA CAJERO: El agregado ($code) no existe para el producto ${product.name} en la Comanda #${parsed.comandaNumber}. Comanda no procesada.")
                    }
                    return
                }
                matchedAgregados.add(match)
            }

            val agregadosUnitPrice = matchedAgregados.sumOf { it.price }
            val itemUnitPrice = product.price + agregadosUnitPrice
            val itemTotalPrice = itemUnitPrice * token.quantity
            totalAmount += itemTotalPrice

            if (com.example.util.ProductCodeHelper.isCocina(product.destination, product.category, product.code.ifBlank { token.productCode })) {
                totalCocina += itemTotalPrice
            } else {
                totalBarra += itemTotalPrice
            }

            val notesStr = if (matchedAgregados.isNotEmpty()) {
                "Agregados: " + matchedAgregados.joinToString(", ") { "${it.name} (+$${"%.2f".format(it.price)})" }
            } else ""

            orderItemsToCreate.add(
                OrderItem(
                    orderId = 0L,
                    productId = product.id,
                    productName = product.name,
                    unitPrice = itemUnitPrice,
                    quantity = token.quantity,
                    destination = product.destination,
                    notes = notesStr,
                    status = "PENDIENTE",
                    productCode = product.code
                )
            )

            val summaryText = if (matchedAgregados.isNotEmpty()) {
                "${token.quantity}x ${product.name} (+ ${matchedAgregados.joinToString(", ") { it.name }}) = $${"%.2f".format(itemTotalPrice)} CUP"
            } else {
                "${token.quantity}x ${product.name} = $${"%.2f".format(itemTotalPrice)} CUP"
            }
            humanReadableSummaries.add(summaryText)
        }

        // Check if order already exists for activeJornadaId + comandaNumber
        val existingOrders = _uiState.value.allOrders.ifEmpty {
            withContext(Dispatchers.IO) { repository.getDatabase().tableOrderDao().getAllOrdersSync() }
        }
        val existingOrder = existingOrders.firstOrNull { it.jornadaId == activeJornadaId && it.comandaNumber == parsed.comandaNumber }

        if (existingOrder != null) {
            if (existingOrder.status == "COBRADA" || existingOrder.status == "CANCELADA") {
                if (queueId > 0L) {
                    withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "IGNORADA") }
                }
                _uiState.update {
                    it.copy(errorMessage = "SMS ignorado: Comanda #${parsed.comandaNumber} ya fue ${existingOrder.status.lowercase()}.")
                }
                return
            }

            val updatedOrder = existingOrder.copy(
                totalAmount = totalAmount,
                totalCocina = totalCocina,
                totalBarra = totalBarra,
                confirmedAt = System.currentTimeMillis()
            )

            try {
                repository.updateComandaWithItems(updatedOrder, orderItemsToCreate)
                if (queueId > 0L) {
                    withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "PROCESADA") }
                }

                _uiState.update {
                    it.copy(
                        pendingComandaAlert = PendingComandaSmsAlert(
                            comandaNumber = parsed.comandaNumber,
                            senderUsername = authorizedUser.username,
                            senderFullName = authorizedUser.fullName,
                            itemsSummary = humanReadableSummaries,
                            totalAmount = totalAmount,
                            orderId = existingOrder.id,
                            isUpdate = true
                        ),
                        showComandaAlert = true,
                        successMessage = "Comanda #${parsed.comandaNumber} ACTUALIZADA por SMS de ${authorizedUser.fullName} (@${authorizedUser.username})."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar la comanda por SMS: ${e.localizedMessage}") }
            }
        } else {
            val now = System.currentTimeMillis()
            val newOrder = TableOrder(
                tableNumber = null,
                customerName = "Comanda SMS (@${authorizedUser.username})",
                waiterUsername = authorizedUser.username,
                createdAt = now,
                confirmedAt = now,
                status = "PENDIENTE_SERVIR",
                totalAmount = totalAmount,
                totalCocina = totalCocina,
                totalBarra = totalBarra,
                jornadaId = activeJornadaId,
                comandaNumber = parsed.comandaNumber
            )

            try {
                repository.confirmComandaWithItems(newOrder, orderItemsToCreate)
                if (queueId > 0L) {
                    withContext(Dispatchers.IO) { repository.updateSmsComandaQueueEstado(queueId, "PROCESADA") }
                }
                val createdOrders = withContext(Dispatchers.IO) { repository.getDatabase().tableOrderDao().getAllOrdersSync() }
                val createdOrder = createdOrders.firstOrNull { it.jornadaId == activeJornadaId && it.comandaNumber == parsed.comandaNumber }

                val orderId = createdOrder?.id ?: 0L

                _uiState.update {
                    it.copy(
                        pendingComandaAlert = PendingComandaSmsAlert(
                            comandaNumber = parsed.comandaNumber,
                            senderUsername = authorizedUser.username,
                            senderFullName = authorizedUser.fullName,
                            itemsSummary = humanReadableSummaries,
                            totalAmount = totalAmount,
                            orderId = orderId,
                            isUpdate = false
                        ),
                        showComandaAlert = true,
                        successMessage = "Comanda #${parsed.comandaNumber} recibida por SMS de ${authorizedUser.fullName} (@${authorizedUser.username}). Añadida a POR COBRAR."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar la comanda por SMS: ${e.localizedMessage}") }
            }
        }
    }


    fun handleIncomingTransferSms(parsed: ParsedTransferSms) {
        viewModelScope.launch {
            val existing = repository.getTransferenciaByTransactionNumber(parsed.transactionNumber)
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        transferAlertDuplicateMsg = "Transferencia duplicada recibida (${parsed.transactionNumber}). Ya fue registrada previamente."
                    )
                }
                return@launch
            }

            val username = _uiState.value.currentUser?.username ?: "cajero"
            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L

            val finalTimestamp = when {
                parsed.timestampMillis > 0L -> parsed.timestampMillis
                else -> System.currentTimeMillis()
            }
            val finalDate = if (parsed.dateStr.isNotBlank()) {
                parsed.dateStr
            } else {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(finalTimestamp))
            }

            val transferencia = Transferencia(
                transactionNumber = parsed.transactionNumber,
                jornadaId = activeJornadaId,
                comandaId = null,
                comandaNumber = null,
                amount = parsed.amount,
                currency = parsed.currency,
                phoneNumber = parsed.phoneNumber.trim(),
                titularName = "",
                titularCi = "",
                recipientAccount = parsed.recipientAccount,
                smsDate = finalDate,
                receivedAt = finalTimestamp,
                cajeroUsername = username,
                status = "NO ASOCIADA",
                rawSmsBody = parsed.rawText
            )

            repository.insertTransferencia(transferencia)

            playTransferAlertSound()

            _uiState.update {
                it.copy(
                    pendingTransferAlert = parsed,
                    showTransferAlert = true,
                    transferAlertDuplicateMsg = null
                )
            }
        }
    }

    private fun playTransferAlertSound() {
        try {
            val context = getApplication<Application>()
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
            } catch (ignored: Exception) {}
        }
        try {
            val context = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(400)
            }
        } catch (ignored: Exception) {}
    }

    fun dismissTransferAlert() {
        _uiState.update { it.copy(showTransferAlert = false, pendingTransferAlert = null) }
    }

    fun clearTransferDuplicateMsg() {
        _uiState.update { it.copy(transferAlertDuplicateMsg = null) }
    }

    fun processManualSms(rawText: String) {
        val parsed = SmsTransferParser.parseTransferSms(rawText)
        if (parsed != null) {
            handleIncomingTransferSms(parsed)
        } else {
            _uiState.update { it.copy(errorMessage = "El texto no corresponde a un formato de SMS de transferencia válido.") }
        }
    }

    fun saveTransferenciaSinAsociar(
        parsed: ParsedTransferSms,
        titularName: String,
        titularCi: String,
        phoneNumber: String
    ) {
        viewModelScope.launch {
            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L
            val cajero = _uiState.value.currentUser?.username ?: "cajero"
            val phone = if (phoneNumber.isNotBlank()) phoneNumber.trim() else parsed.phoneNumber.trim()

            val existing = repository.getTransferenciaByTransactionNumber(parsed.transactionNumber)
            if (existing != null) {
                _uiState.update { it.copy(errorMessage = "La transferencia ${parsed.transactionNumber} ya está registrada.") }
                dismissTransferAlert()
                return@launch
            }

            val transferencia = Transferencia(
                transactionNumber = parsed.transactionNumber,
                jornadaId = activeJornadaId,
                comandaId = null,
                comandaNumber = null,
                amount = parsed.amount,
                currency = parsed.currency,
                phoneNumber = phone,
                titularName = titularName.trim(),
                titularCi = titularCi.trim(),
                recipientAccount = parsed.recipientAccount,
                smsDate = parsed.dateStr,
                receivedAt = System.currentTimeMillis(),
                cajeroUsername = cajero,
                status = "NO ASOCIADA",
                rawSmsBody = parsed.rawText
            )
            repository.insertTransferencia(transferencia)
            dismissTransferAlert()
            _uiState.update { it.copy(successMessage = "Transferencia de $${"%.2f".format(parsed.amount)} ${parsed.currency} guardada como NO ASOCIADA.") }
        }
    }

    fun asociarTransferenciaAComanda(
        parsed: ParsedTransferSms,
        titularName: String,
        titularCi: String,
        phoneNumber: String,
        orderId: Long,
        onSuccess: (TableOrder) -> Unit = {}
    ) {
        viewModelScope.launch {
            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L
            val cajero = _uiState.value.currentUser?.username ?: "cajero"
            val phone = if (phoneNumber.isNotBlank()) phoneNumber.trim() else parsed.phoneNumber.trim()

            val finalTimestamp = when {
                parsed.timestampMillis > 0L -> parsed.timestampMillis
                else -> System.currentTimeMillis()
            }
            val finalDate = if (parsed.dateStr.isNotBlank()) {
                parsed.dateStr
            } else {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(finalTimestamp))
            }

            val transferencia = Transferencia(
                transactionNumber = parsed.transactionNumber,
                jornadaId = activeJornadaId,
                amount = parsed.amount,
                currency = parsed.currency,
                phoneNumber = phone,
                titularName = titularName.trim(),
                titularCi = titularCi.trim(),
                recipientAccount = parsed.recipientAccount,
                smsDate = finalDate,
                receivedAt = finalTimestamp,
                cajeroUsername = cajero,
                rawSmsBody = parsed.rawText
            )

            val result = repository.asociarTransferenciaAComanda(transferencia, orderId)
            if (result.isSuccess) {
                dismissTransferAlert()
                val order = result.getOrNull()
                if (order != null) {
                    onSuccess(order)
                }
                _uiState.update { it.copy(successMessage = "Transferencia asociada exitosamente a la Comanda #${order?.comandaNumber ?: orderId}.") }
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Error al asociar comanda") }
            }
        }
    }

    fun asociarTransferenciaExistente(
        transferencia: Transferencia,
        orderId: Long,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.asociarTransferenciaAComanda(transferencia, orderId)
            if (result.isSuccess) {
                onSuccess()
                _uiState.update { it.copy(successMessage = "Transferencia asociada exitosamente a la Comanda #${result.getOrNull()?.comandaNumber ?: orderId}.") }
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Error al asociar") }
            }
        }
    }

    fun registrarTransferenciaManual(
        parsed: com.example.util.ParsedTransferSms,
        smsTimestampMillis: Long? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val existing = repository.getTransferenciaByTransactionNumber(parsed.transactionNumber)
            if (existing != null) {
                onError("La transferencia ${parsed.transactionNumber} ya existe.")
                return@launch
            }
            val username = _uiState.value.currentUser?.username ?: "cajero"
            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L
            val actualTimestamp = when {
                smsTimestampMillis != null && smsTimestampMillis > 0L -> smsTimestampMillis
                parsed.timestampMillis > 0L -> parsed.timestampMillis
                else -> System.currentTimeMillis()
            }
            val formattedDate = if (parsed.dateStr.isNotBlank()) {
                parsed.dateStr
            } else {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(actualTimestamp))
            }
            val transferencia = com.example.data.local.model.Transferencia(
                transactionNumber = parsed.transactionNumber,
                jornadaId = activeJornadaId,
                amount = parsed.amount,
                currency = parsed.currency,
                phoneNumber = parsed.phoneNumber,
                recipientAccount = parsed.recipientAccount,
                smsDate = formattedDate,
                receivedAt = actualTimestamp,
                cajeroUsername = username,
                status = "NO ASOCIADA",
                rawSmsBody = parsed.rawText,
                isManual = true,
                source = "MANUAL_EXTERNA"
            )
            repository.insertTransferencia(transferencia)
            onSuccess()
        }
    }

    fun borrarTodasLasTransferencias(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllTransferencias()
            _uiState.update { it.copy(successMessage = "Todas las transferencias registradas han sido eliminadas correctamente.") }
            onComplete()
        }
    }

    fun updateTransferenciaSenderData(
        transferenciaId: Long,
        titularName: String,
        titularCi: String,
        phoneNumber: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                repository.getDatabase().transferenciaDao().getById(transferenciaId)
            }
            if (existing != null) {
                val cleanPhone = phoneNumber.trim()
                val updated = existing.copy(
                    titularName = titularName.trim(),
                    titularCi = titularCi.trim(),
                    phoneNumber = cleanPhone
                )
                repository.updateTransferencia(updated)
                _uiState.update { it.copy(successMessage = "Datos del remitente actualizados correctamente.") }
                onSuccess()
            }
        }
    }

    fun saveTransferenciasClasificacion(jornadaId: Long, produccionMonto: Double, mercaderiasMonto: Double) {
        if (jornadaId > 0) {
            try {
                val context = getApplication<Application>()
                val prefs = context.getSharedPreferences("clasificacion_transferencias_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("clasif_jornada_$jornadaId", "$produccionMonto;$mercaderiasMonto").apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.update { state ->
            val updated = state.transferenciasClasificacionMap + (jornadaId to Pair(produccionMonto, mercaderiasMonto))
            state.copy(
                transferenciasClasificacionMap = updated,
                successMessage = "Clasificación de transferencias guardada correctamente."
            )
        }
    }

    fun getOrLoadTransferenciasClasificacion(jornadaId: Long): Pair<Double, Double>? {
        if (jornadaId <= 0) return null
        val inMemory = _uiState.value.transferenciasClasificacionMap[jornadaId]
        if (inMemory != null) return inMemory

        try {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("clasificacion_transferencias_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("clasif_jornada_$jornadaId", null) ?: return null
            val parts = saved.split(";")
            if (parts.size == 2) {
                val prod = parts[0].toDoubleOrNull()
                val merc = parts[1].toDoubleOrNull()
                if (prod != null && merc != null) {
                    val pair = Pair(prod, merc)
                    _uiState.update { state ->
                        state.copy(transferenciasClasificacionMap = state.transferenciasClasificacionMap + (jornadaId to pair))
                    }
                    return pair
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun registrarNuevasTransferenciasDetectadas(
        nuevas: List<com.example.util.SearchedPagoXMovilSms>,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val username = _uiState.value.currentUser?.username ?: "cajero"
            val activeJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
            val activeJornadaId = activeJornada?.id ?: _uiState.value.activeJornada?.id ?: 0L
            val dateDisplayFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            var count = 0

            val seenTxs = mutableSetOf<String>()
            val existingInDb = withContext(Dispatchers.IO) {
                repository.getDatabase().transferenciaDao().getAllTransferenciasSync().map { it.transactionNumber.trim() }.toSet()
            }

            for (item in nuevas) {
                val txNum = item.parsed.transactionNumber.trim()
                if (txNum.isNotBlank() && !existingInDb.contains(txNum) && seenTxs.add(txNum)) {
                    val actualTimestamp = if (item.smsDateMillis > 0L) item.smsDateMillis else item.parsed.timestampMillis
                    val finalTimestamp = if (actualTimestamp > 0L) actualTimestamp else System.currentTimeMillis()
                    val finalDate = if (item.parsed.dateStr.isNotBlank()) item.parsed.dateStr else dateDisplayFormat.format(java.util.Date(finalTimestamp))

                    val transferencia = com.example.data.local.model.Transferencia(
                        transactionNumber = txNum,
                        jornadaId = activeJornadaId,
                        amount = item.parsed.amount,
                        currency = item.parsed.currency,
                        phoneNumber = item.parsed.phoneNumber,
                        recipientAccount = item.parsed.recipientAccount,
                        smsDate = finalDate,
                        receivedAt = finalTimestamp,
                        cajeroUsername = username,
                        status = "NO ASOCIADA",
                        rawSmsBody = item.parsed.rawText,
                        isManual = false,
                        source = "AUTO_SMS_JORNADA"
                    )
                    repository.insertTransferencia(transferencia)
                    count++
                }
            }

            if (count > 0) {
                // Ensure allTransferencias is re-synced if needed
                val reloaded = withContext(Dispatchers.IO) {
                    repository.getDatabase().transferenciaDao().getAllTransferenciasSync()
                }
                _uiState.update {
                    it.copy(
                        allTransferencias = reloaded,
                        successMessage = "Se registraron $count transferencias de la jornada actual."
                    )
                }
            }
            onComplete(count)
        }
    }
    fun registrarTransferenciaExterna(
        titularName: String,
        titularCi: String,
        phoneNumber: String,
        amount: Double,
        transactionNumber: String,
        smsDate: String,
        orderId: Long?,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val txClean = transactionNumber.trim().uppercase()
        if (txClean.isBlank()) {
            val err = "El número de transacción es obligatorio."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }
        if (amount <= 0.0) {
            val err = "El importe debe ser mayor a 0.00 CUP."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }
        val cleanPhone = phoneNumber.trim()
        if (cleanPhone.isNotBlank() && (cleanPhone.length != 10 || !cleanPhone.all { it.isDigit() })) {
            val err = "El número de teléfono debe contener exactamente 10 dígitos."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }

        viewModelScope.launch {
            val existing = repository.getTransferenciaByTransactionNumber(txClean)
            if (existing != null) {
                val err = "La transacción '$txClean' ya se encuentra registrada en el sistema."
                _uiState.update { it.copy(errorMessage = err) }
                onError(err)
                return@launch
            }

            val activeJornadaId = _uiState.value.activeJornada?.id ?: 0L
            val cajero = _uiState.value.currentUser?.username ?: "cajero"
            val finalDate = if (smsDate.isNotBlank()) smsDate.trim() else java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            val parsedMillis = try {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).parse(finalDate)?.time
            } catch (_: Exception) { null }
            val finalTimestamp = parsedMillis ?: System.currentTimeMillis()

            val transferencia = Transferencia(
                transactionNumber = txClean,
                jornadaId = activeJornadaId,
                comandaId = null,
                comandaNumber = null,
                amount = amount,
                currency = "CUP",
                phoneNumber = cleanPhone,
                titularName = titularName.trim(),
                titularCi = titularCi.trim(),
                recipientAccount = "",
                smsDate = finalDate,
                receivedAt = finalTimestamp,
                cajeroUsername = cajero,
                status = "NO ASOCIADA",
                rawSmsBody = "REGISTRO_MANUAL_EXTERNO",
                isManual = true,
                source = "MANUAL_EXTERNA"
            )

            if (orderId != null && orderId > 0) {
                val result = repository.asociarTransferenciaAComanda(transferencia, orderId)
                if (result.isSuccess) {
                    val order = result.getOrNull()
                    _uiState.update {
                        it.copy(successMessage = "Transferencia externa $txClean ($${"%.2f".format(amount)} CUP) registrada y asociada a la Comanda #${order?.comandaNumber ?: orderId}.")
                    }
                    onSuccess()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Error al asociar la transferencia externa"
                    _uiState.update { it.copy(errorMessage = err) }
                    onError(err)
                }
            } else {
                repository.insertTransferencia(transferencia)
                _uiState.update {
                    it.copy(successMessage = "Transferencia externa $txClean ($${"%.2f".format(amount)} CUP) registrada exitosamente como NO ASOCIADA.")
                }
                onSuccess()
            }
        }
    }

    fun initializeSystem(context: Context, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 1. Generate full JSON Backup using InitializationManager
                val isBackupSuccess = withContext(Dispatchers.IO) {
                    com.example.util.InitializationManager.exportAndShareBackup(context, _uiState.value)
                }
                
                if (isBackupSuccess) {
                    withContext(Dispatchers.IO) {
                        // 2. Perform the transactional wipe
                        repository.initializeOperationalData()
                    }
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            successMessage = "Inicialización completada. El sistema está limpio y listo para una nueva operación."
                        )
                    }
                    onSuccess()
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            errorMessage = "No se pudo generar el respaldo. Operación cancelada por seguridad."
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error durante la inicialización: ${e.message}"
                    )
                }
            }
        }
    }



    fun login(username: String, password: String, onSuccess: (User) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val trimmedUsername = username.trim()
            val normalizedUsername = trimmedUsername.lowercase()

            var user = repository.getUserByUsername(trimmedUsername)
            if (user == null && normalizedUsername != trimmedUsername) {
                user = repository.getUserByUsername(normalizedUsername)
            }

            // Comprobación y creación garantizada de cuenta ADMINISTRADOR local si no existiera
            if (user == null && (normalizedUsername == "admin" || trimmedUsername.equals("admin", ignoreCase = true))) {
                user = AppDatabase.ensureAdminUserExists(repository.getDatabase())
            }

            val personalAccount = repository.getDatabase().personalContratadoDao().getPersonalByUsername(trimmedUsername)
                ?: repository.getDatabase().personalContratadoDao().getPersonalByUsername(normalizedUsername)
            if (user == null && personalAccount != null && personalAccount.tieneAccesoApp) {
                val role = when (personalAccount.role.uppercase()) {
                    "ADMIN", "ADMINISTRADOR" -> UserRole.ADMIN
                    "DUENO", "DUEÑO" -> UserRole.DUENO
                    "CAJERO" -> UserRole.CAJERO
                    "COCINA" -> UserRole.COCINA
                    "DEPENDIENTE" -> UserRole.DEPENDIENTE
                    "SALON" -> UserRole.SALON
                    "BARRA" -> UserRole.BARRA
                    else -> UserRole.fromString(personalAccount.role)
                }
                val passHash = if (personalAccount.passwordHash.isNotBlank()) {
                    personalAccount.passwordHash
                } else if (personalAccount.passwordPlain.isNotBlank()) {
                    personalAccount.passwordPlain.trim().toSha256()
                } else {
                    "1234".toSha256()
                }
                val newUser = User(
                    username = personalAccount.username.trim().lowercase(),
                    fullName = personalAccount.nombreCompleto.trim(),
                    passwordHash = passHash,
                    role = role,
                    montoPorProducto = personalAccount.montoPorProducto,
                    isActive = personalAccount.isActive,
                    telefono = personalAccount.movil,
                    permisoProduccion = personalAccount.permisoProduccion,
                    permisoMercancias = personalAccount.permisoMercancias,
                    permisoPersonal = personalAccount.permisoPersonal,
                    permisoControlNegocio = personalAccount.permisoControlNegocio
                )
                repository.insertUser(newUser)
                user = newUser
            }

            if (user == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Usuario no encontrado.") }
                return@launch
            }
            
            val inputPassHash = password.trim().toSha256()
            val passMatches = (user.passwordHash == inputPassHash) || (user.passwordHash == password.trim())
            if (!passMatches) {
                if (user.username.equals("admin", ignoreCase = true)) {
                    val ensuredAdmin = AppDatabase.ensureAdminUserExists(repository.getDatabase())
                    if (ensuredAdmin.passwordHash != inputPassHash && ensuredAdmin.passwordHash != password.trim()) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Contraseña incorrecta.") }
                        return@launch
                    } else {
                        user = ensuredAdmin
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Contraseña incorrecta.") }
                    return@launch
                }
            }
            if (!user.isActive) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Usuario inactivo.") }
                return@launch
            }

            loadSalonTableCountForUser(user.username)
            _uiState.update { it.copy(isLoading = false, currentUser = user, errorMessage = null) }
            
            // Persist session for 24 hours of inactivity
            sharedPreferences.edit()
                .putString("loggedInUsername", user.username)
                .putLong("lastActivityTimestamp", System.currentTimeMillis())
                .apply()
                
            onSuccess(user)
            checkDailyDataUpdates()
        }
    }

    fun logout() {
        if (_uiState.value.adminBackupUser != null) {
            exitTestAccount()
            return
        }
        sharedPreferences.edit()
            .remove("loggedInUsername")
            .remove("lastActivityTimestamp")
            .apply()
        _uiState.update { it.copy(currentUser = null, adminBackupUser = null) }
    }

    fun testAccount(targetUser: User) {
        val current = _uiState.value.currentUser
        if (current?.role == UserRole.ADMIN && _uiState.value.adminBackupUser == null) {
            _uiState.update { it.copy(currentUser = targetUser, adminBackupUser = current) }
        } else {
            _uiState.update { it.copy(currentUser = targetUser) }
        }
    }

    fun exitTestAccount() {
        val admin = _uiState.value.adminBackupUser
        if (admin != null) {
            _uiState.update { it.copy(currentUser = admin, adminBackupUser = null) }
        } else {
            val adminUser = _uiState.value.users.find { it.role == UserRole.ADMIN }
                ?: User(username = "admin", fullName = "Administrador", passwordHash = "", role = UserRole.ADMIN)
            _uiState.update { it.copy(currentUser = adminUser, adminBackupUser = null) }
        }
    }

    fun generateUsersBackupJson(): String {
        val jsonObject = JSONObject()
        jsonObject.put("version", System.currentTimeMillis().toString())
        jsonObject.put("codigoNegocio", _uiState.value.businessConfig?.codigoNegocio ?: "001")
        val usersArray = JSONArray()
        for (user in _uiState.value.users) {
            val userObj = JSONObject().apply {
                put("username", user.username)
                put("fullName", user.fullName)
                put("passwordHash", user.passwordHash)
                put("role", user.role.name)
                put("montoPorProducto", user.montoPorProducto)
                put("isActive", user.isActive)
                put("telefono", user.telefono)
                put("permisoProduccion", user.permisoProduccion)
                put("permisoMercancias", user.permisoMercancias)
                put("permisoPersonal", user.permisoPersonal)
                put("permisoControlNegocio", user.permisoControlNegocio)
                put("authorizedDeviceId", user.authorizedDeviceId ?: "")
            }
            usersArray.put(userObj)
        }
        jsonObject.put("usuarios", usersArray)
        return jsonObject.toString(4)
    }

    fun openJornada(
        initialCash: Double,
        openedBy: String = "dueno",
        device: String = "DISPOSITIVO-LOCAL",
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val active = repository.getActiveJornadaSync()
            if (active != null) {
                val msg = "Ya existe una jornada activa (#${active.id}). Debe cerrarla antes de abrir una nueva."
                _uiState.update { it.copy(errorMessage = msg) }
                onResult?.invoke(false, msg)
                return@launch
            }
            val username = openedBy.ifBlank { _uiState.value.currentUser?.username ?: "dueno" }
            val devToUse = device.ifBlank { _uiState.value.deviceId.ifBlank { "DISPOSITIVO-LOCAL" } }
            // Set initialStock = stock for all materias primas at the moment of opening!
            _uiState.value.materiasPrimas.forEach { mp ->
                repository.updateMateriaPrima(mp.copy(initialStock = mp.stock))
            }
            val id = repository.openJornada(initialCash, username, devToUse)
            if (id > 0) {
                _uiState.update { it.copy(successMessage = "Jornada #$id iniciada exitosamente") }
                onResult?.invoke(true, "Jornada #$id iniciada exitosamente")
            } else {
                _uiState.update { it.copy(errorMessage = "Error al abrir la jornada") }
                onResult?.invoke(false, "Error al abrir la jornada")
            }
        }
    }

    fun updateStockInicialPreJornada(materiaPrimaId: Long, newStock: Double, responsibleUser: String) {
        viewModelScope.launch {
            val mpList = _uiState.value.materiasPrimas
            val raw = mpList.find { it.id == materiaPrimaId }
            if (raw == null) {
                _uiState.update { it.copy(errorMessage = "Materia prima no encontrada") }
                return@launch
            }
            
            val previousStock = raw.stock
            val updatedMp = raw.copy(stock = newStock, initialStock = newStock)
            
            // Guardar en la base de datos
            repository.updateMateriaPrima(updatedMp)
            
            // Registrar automáticamente en la Bitácora
            val unitStr = raw.unit
            val logContent = "Administrador actualizó stock inicial de ${raw.name}: ${"%.2f".format(previousStock)} $unitStr → ${"%.2f".format(newStock)} $unitStr."
            
            repository.insertBitacora(
                BitacoraEntry(
                    title = "Ajuste Stock Inicial",
                    content = logContent,
                    category = "PRODUCCION",
                    authorUsername = responsibleUser,
                    priority = "NORMAL"
                )
            )
            
            _uiState.update { it.copy(successMessage = "Stock inicial de ${raw.name} actualizado correctamente") }
        }
    }

    fun registrarCuadreSalon(jornadaId: Long, realCash: Double, expectedCash: Double, diferencia: Double, notes: String) {
        viewModelScope.launch {
            val activeJornada = _uiState.value.activeJornada
            if (activeJornada == null || !activeJornada.isOpen) {
                _uiState.update { it.copy(errorMessage = "No se puede guardar el cuadre: La jornada está cerrada.") }
                return@launch
            }
            sharedPreferences.edit()
                .putBoolean("salon_cuadre_registrado_$jornadaId", true)
                .putFloat("salon_cuadre_real_cash_$jornadaId", realCash.toFloat())
                .putFloat("salon_cuadre_expected_cash_$jornadaId", expectedCash.toFloat())
                .putFloat("salon_cuadre_diferencia_$jornadaId", diferencia.toFloat())
                .putString("salon_cuadre_notes_$jornadaId", notes)
                .apply()

            val username = _uiState.value.currentUser?.username ?: "dueno"
            val diffText = when {
                diferencia == 0.0 -> "CUADRA"
                diferencia > 0.0 -> "SOBRANTE"
                else -> "FALTANTE"
            }
            repository.insertBitacora(
                com.example.data.local.model.BitacoraEntry(
                    title = "Cuadre de Salón Registrado",
                    content = "Se registró el cuadre de Salón para la jornada #$jornadaId. Debe haber: $${"%.2f".format(expectedCash)} CUP, Contado: $${"%.2f".format(realCash)} CUP, Resultado: $diffText ($${"%.2f".format(diferencia)} CUP). Notas: $notes",
                    category = "CUADRE",
                    authorUsername = username,
                    priority = if (diferencia != 0.0) "ALTA" else "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Cuadre de Salón registrado correctamente.") }
        }
    }

    fun registrarCuadreDueno(
        jornadaId: Long,
        realCash: Double,
        expectedCash: Double,
        diferencia: Double,
        ingresosProduccion: Double,
        ingresosMercaderias: Double,
        mermas: Double,
        transferencias: Double,
        extracciones: Double,
        notes: String,
        mercaderiaItems: List<MercaderiaCuadreItem> = emptyList(),
        agregadoItems: List<AgregadoCuadreItem> = emptyList(),
        produccionItems: List<ProduccionCuadreItem> = emptyList()
    ) {
        viewModelScope.launch {
            val active = repository.getActiveJornadaSync()
            if (active == null || !active.isOpen || active.id != jornadaId) {
                _uiState.update { it.copy(errorMessage = "No se puede guardar el cuadre: La jornada está cerrada.") }
                return@launch
            }
            val updated = active.copy(
                finalCash = realCash,
                expectedCash = expectedCash,
                cashDifference = diferencia,
                realSalesProduccion = ingresosProduccion,
                realSalesMercaderias = ingresosMercaderias,
                extracciones = extracciones,
                notes = notes
            )
            repository.updateJornada(updated)

            val username = _uiState.value.currentUser?.username ?: "dueno"

            // Process Production Items: Update tandas quantitySold and record pending units if declared
            if (produccionItems.isNotEmpty()) {
                val currentTandas = _uiState.value.tandas.filter {
                    it.jornadaId == jornadaId || (active.openedAt > 0 && it.date >= active.openedAt)
                }
                for (pItem in produccionItems) {
                    val tandasForProduct = currentTandas.filter { it.productId == pItem.productId }
                    if (tandasForProduct.isNotEmpty()) {
                        var remainingSold = pItem.vendible
                        for (tanda in tandasForProduct) {
                            val tYield = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                            val soldThisTanda = if (remainingSold >= tYield) {
                                remainingSold -= tYield
                                tYield
                            } else {
                                val s = remainingSold
                                remainingSold = 0.0
                                s
                            }
                            var obs = tanda.observation
                            if (pItem.pendientes > 0.0) {
                                val pQtyStr = if (pItem.pendientes % 1.0 == 0.0) pItem.pendientes.toInt().toString() else "%.1f".format(pItem.pendientes)
                                val noteTag = "Pendientes: $pQtyStr ${pItem.unit}"
                                obs = if (obs.isNotBlank()) {
                                    if (obs.contains("Pendientes:")) {
                                        obs.replace(Regex("Pendientes:[^|\\[]*"), noteTag).trim()
                                    } else {
                                        "$obs | $noteTag"
                                    }
                                } else {
                                    noteTag
                                }
                            }
                            val updatedTanda = tanda.copy(
                                quantitySold = soldThisTanda,
                                realRevenue = soldThisTanda * tanda.salePrice,
                                observation = obs
                            )
                            repository.updateTanda(updatedTanda)
                        }
                    }
                }
            }

            // Process Agregados: Return sobrantes to warehouse stock, reset stockEnVenta/racionesEnVenta, record movements
            val mpList = _uiState.value.materiasPrimas
            for (agItem in agregadoItems) {
                val rawMp = mpList.find { it.id == agItem.materiaPrimaId }
                val sobranteFisico = (agItem.racionesSobrantes * agItem.rationQuantity).coerceAtLeast(0.0)
                if (rawMp != null) {
                    val nextStock = (rawMp.stock + sobranteFisico).coerceAtLeast(0.0)
                    val updatedMp = rawMp.copy(
                        stock = nextStock,
                        stockEnVenta = 0.0,
                        racionesEnVenta = 0.0
                    )
                    repository.updateMateriaPrima(updatedMp)

                    // Record movement for returning sobrante to stock
                    if (sobranteFisico > 0.0 || agItem.racionesSobrantes > 0.0) {
                        val movSobrante = MovimientoMateriaPrima(
                            materiaPrimaId = rawMp.id,
                            materiaPrimaName = rawMp.name,
                            type = "DEVOLUCION_SOBRANTE",
                            quantity = sobranteFisico,
                            unit = rawMp.unit,
                            responsibleUser = username,
                            notes = "Devolución de sobrante a inventario desde Cuadre de Caja: ${agItem.racionesSobrantes} raciones (${"%.1f".format(sobranteFisico)} ${rawMp.unit}). Vendidas: ${agItem.racionesVendidas}, Regalías: ${agItem.racionesRegalia}",
                            resultingStock = nextStock
                        )
                        repository.insertMovimientoMateriaPrima(movSobrante)
                    }

                    // Record movement for real sales if sold
                    if (agItem.racionesVendidas > 0.0) {
                        val cantidadFisicaVendida = agItem.racionesVendidas * agItem.rationQuantity
                        val movVenta = MovimientoMateriaPrima(
                            materiaPrimaId = rawMp.id,
                            materiaPrimaName = rawMp.name,
                            type = "VENTA_AGREGADO",
                            quantity = cantidadFisicaVendida,
                            unit = rawMp.unit,
                            responsibleUser = username,
                            notes = "Venta real registrada en Cuadre de Caja: ${agItem.racionesVendidas} raciones. Ingreso real: $${"%.2f".format(agItem.racionesVendidas * agItem.precioVenta)} CUP",
                            resultingStock = nextStock
                        )
                        repository.insertMovimientoMateriaPrima(movVenta)
                    }
                }
            }

            // Insert VENTA and MERMA movements for mercaderías from Cuadre de Caja
            for (item in mercaderiaItems) {
                if (item.ventas > 0.0) {
                    val movVenta = MovimientoMercaderia(
                        mercaderiaId = item.mercaderiaId,
                        type = "VENTA",
                        quantity = item.ventas,
                        quantitySold = item.ventas,
                        salePrice = item.price,
                        realRevenue = item.ventas * item.price,
                        jornadaId = jornadaId,
                        responsibleAdmin = username,
                        notes = "Venta registrada desde Cuadre de Caja"
                    )
                    repository.insertMovimientoMercaderia(movVenta)
                }
                if (item.mermas > 0.0) {
                    val movMerma = MovimientoMercaderia(
                        mercaderiaId = item.mercaderiaId,
                        type = "MERMA",
                        quantity = item.mermas,
                        jornadaId = jornadaId,
                        responsibleAdmin = username,
                        notes = "Merma registrada desde Cuadre de Caja"
                    )
                    repository.insertMovimientoMercaderia(movMerma)
                }
            }

            val diffText = when {
                diferencia == 0.0 -> "CUADRA EXACTO"
                diferencia > 0.0 -> "SOBRANTE"
                else -> "FALTANTE"
            }
            repository.insertBitacora(
                com.example.data.local.model.BitacoraEntry(
                    title = "Cuadre de Caja (DUEÑO) Registrado",
                    content = "Cuadre de Caja para jornada #$jornadaId registrado por $username. Efectivo Esperado: $${"%.2f".format(expectedCash)} CUP, Efectivo Real: $${"%.2f".format(realCash)} CUP, Diferencia: $diffText ($${"%.2f".format(diferencia)} CUP). Producción: $${"%.2f".format(ingresosProduccion)} CUP, Mercaderías: $${"%.2f".format(ingresosMercaderias)} CUP, Mermas: $${"%.2f".format(mermas)} CUP, Transferencias: $${"%.2f".format(transferencias)} CUP, Extracciones: $${"%.2f".format(extracciones)} CUP. Notas: $notes",
                    category = "CUADRE",
                    authorUsername = username,
                    priority = if (diferencia != 0.0) "ALTA" else "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Cuadre de Caja del DUEÑO registrado exitosamente.") }
        }
    }

    fun confirmarPagosPersonal(
        jornadaId: Long,
        totalPagos: Double,
        totalCocina: Double,
        totalCajero: Double,
        totalDependiente: Double,
        efectivoContado: Double,
        dineroFinalEnCaja: Double,
        detallesDistribucion: String
    ) {
        viewModelScope.launch {
            val username = _uiState.value.currentUser?.username ?: "dueno"
            val active = repository.getActiveJornadaSync()
            if (active != null && active.id == jornadaId) {
                val updated = active.copy(
                    liquidezFinal = dineroFinalEnCaja
                )
                repository.updateJornada(updated)
            }

            repository.insertBitacora(
                com.example.data.local.model.BitacoraEntry(
                    title = "Pagos de Personal Confirmados",
                    content = "Pagos de personal confirmados para jornada #$jornadaId por $username. Total Pagos: $${"%.2f".format(totalPagos)} CUP (Cocina: $${"%.2f".format(totalCocina)} CUP, Cajero: $${"%.2f".format(totalCajero)} CUP, Dependientes: $${"%.2f".format(totalDependiente)} CUP). Efectivo contado: $${"%.2f".format(efectivoContado)} CUP. Salida física de efectivo: -$${"%.2f".format(totalPagos)} CUP. Dinero final en caja: $${"%.2f".format(dineroFinalEnCaja)} CUP. Detalle: $detallesDistribucion",
                    category = "CUADRE",
                    authorUsername = username,
                    priority = "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Pagos de personal confirmados y registrados correctamente.") }
        }
    }

    fun resetCuadreSalon(jornadaId: Long) {
        viewModelScope.launch {
            sharedPreferences.edit()
                .remove("salon_cuadre_registrado_$jornadaId")
                .remove("salon_cuadre_real_cash_$jornadaId")
                .remove("salon_cuadre_expected_cash_$jornadaId")
                .remove("salon_cuadre_diferencia_$jornadaId")
                .remove("salon_cuadre_notes_$jornadaId")
                .apply()
            
            _uiState.update { it.copy(successMessage = "Se ha reiniciado el cuadre de Salón.") }
        }
    }

    fun registrarCuadreBarra(
        jornadaId: Long,
        expectedIncome: Double,
        registeredIncome: Double,
        notes: String,
        physicalCounts: Map<Long, Double>
    ) {
        viewModelScope.launch {
            val activeJornada = _uiState.value.activeJornada
            if (activeJornada == null || !activeJornada.isOpen) {
                _uiState.update { it.copy(errorMessage = "No se puede guardar el cuadre: La jornada está cerrada.") }
                return@launch
            }
            val editor = sharedPreferences.edit()
            editor.putBoolean("barra_cuadre_registrado_$jornadaId", true)
            editor.putFloat("barra_expected_income_$jornadaId", expectedIncome.toFloat())
            editor.putFloat("barra_registered_income_$jornadaId", registeredIncome.toFloat())
            editor.putString("barra_notes_$jornadaId", notes)
            
            physicalCounts.forEach { (mercId, count) ->
                editor.putFloat("barra_physical_count_${jornadaId}_$mercId", count.toFloat())
            }
            editor.apply()

            val username = _uiState.value.currentUser?.username ?: "dueno"
            val diffIncome = registeredIncome - expectedIncome
            val moneyStatus = when {
                diffIncome == 0.0 -> "CUADRA DINERO"
                diffIncome > 0.0 -> "SOBRANTE DINERO"
                else -> "FALTANTE DINERO"
            }

            repository.insertBitacora(
                com.example.data.local.model.BitacoraEntry(
                    title = "Cuadre de Barra Registrado",
                    content = "Se registró el cuadre de Barra para la jornada #$jornadaId. Dinero Esperado: $${"%.2f".format(expectedIncome)} CUP, Registrado en Caja: $${"%.2f".format(registeredIncome)} CUP. Resultado: $moneyStatus ($${"%.2f".format(diffIncome)} CUP). Notas: $notes",
                    category = "CUADRE",
                    authorUsername = username,
                    priority = if (diffIncome != 0.0) "ALTA" else "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Cuadre de Barra registrado correctamente.") }
        }
    }

    fun resetCuadreBarra(jornadaId: Long, mercaderiaIds: List<Long>) {
        viewModelScope.launch {
            val editor = sharedPreferences.edit()
            editor.remove("barra_cuadre_registrado_$jornadaId")
            editor.remove("barra_expected_income_$jornadaId")
            editor.remove("barra_registered_income_$jornadaId")
            editor.remove("barra_notes_$jornadaId")
            
            mercaderiaIds.forEach { mercId ->
                editor.remove("barra_physical_count_${jornadaId}_$mercId")
            }
            editor.apply()
            
            _uiState.update { it.copy(successMessage = "Se ha reiniciado el cuadre de Barra.") }
        }
    }

    fun closeJornada(finalCash: Double, notes: String) {
        viewModelScope.launch {
            val currentJornada = _uiState.value.activeJornada ?: return@launch
            val username = _uiState.value.currentUser?.username ?: "dueno"
            val state = _uiState.value

            // 1. CIERRE AUTOMÁTICO DE TANDAS AL CERRAR JORNADA
            val openTandas = state.tandas.filter {
                (it.jornadaId == currentJornada.id || (currentJornada.openedAt > 0 && it.date >= currentJornada.openedAt)) &&
                (it.status == "ACTIVA" || it.status == "ABIERTA" || it.status == "ACTIVADA")
            }
            openTandas.forEach { tanda ->
                val finalQty = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val prod = state.products.find { it.id == tanda.productId }
                val presEquiv = if (tanda.specialPresentationEquivalence > 0.0) tanda.specialPresentationEquivalence else {
                    prod?.let { parsePresentacionesEspeciales(it.presentacionesEspeciales).find { p -> p.name.equals(tanda.specialPresentationName, true) }?.baseEquivalence } ?: 1.0
                }
                val specialUnitsEq = if (tanda.specialPresentationQty > 0.0) tanda.specialPresentationQty * presEquiv else 0.0
                val totalYieldUnits = finalQty + specialUnitsEq
                val expectedVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val yieldPct = if (expectedVal > 0.0) (totalYieldUnits / expectedVal) * 100.0 else 100.0
                val salePrice = if (tanda.salePrice > 0.0) tanda.salePrice else (prod?.price ?: 0.0)
                val rev = finalQty * salePrice
                val profit = rev - tanda.totalBatchCost
                val pMargin = if (rev > 0.0) (profit / rev) * 100.0 else 0.0
                val uCost = if (finalQty > 0.0) tanda.totalBatchCost / finalQty else 0.0

                repository.updateTanda(
                    tanda.copy(
                        jornadaId = currentJornada.id,
                        status = "CERRADA",
                        actualYield = finalQty,
                        yieldPercentage = yieldPct,
                        expectedRevenue = rev,
                        estimatedProfit = profit,
                        profitMargin = pMargin,
                        realUnitCost = uCost
                    )
                )
            }

            // 2. Guardar snapshot congelado de INFORME INSUMOS para el ARCHIVO histórico
            try {
                val insumosArchive = com.example.util.InformeInsumosArchiveManager.buildArchiveFromHistoricalData(
                    jornada = currentJornada.copy(closedAt = System.currentTimeMillis(), closedBy = username),
                    allMaterias = state.materiasPrimas,
                    allMovimientos = state.movimientosMateriaPrima,
                    businessName = state.businessName,
                    closedBy = username
                )
                com.example.util.InformeInsumosArchiveManager.saveArchive(getApplication(), insumosArchive)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Guardar snapshot congelado de CUADRE DE CAJA para el ARCHIVO histórico
            try {
                val cuadreArchive = com.example.util.CuadreCajaArchiveManager.getOrBuildArchive(
                    context = getApplication(),
                    jornada = currentJornada.copy(closedAt = System.currentTimeMillis(), closedBy = username, finalCash = finalCash, notes = notes),
                    allTandas = state.tandas,
                    allMovimientosMercaderia = state.movimientosMercaderia,
                    allProducts = state.products,
                    allMercaderias = state.mercaderias,
                    allTransferencias = state.allTransferencias,
                    businessName = state.businessName
                )
                com.example.util.CuadreCajaArchiveManager.saveArchive(getApplication(), cuadreArchive)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                com.example.util.CuadreDraftManager.clearDraft(getApplication(), currentJornada.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            repository.closeJornada(currentJornada, finalCash, username, notes)
            _uiState.update { it.copy(successMessage = "Jornada cerrada correctamente y cuadre registrado.") }
        }
    }

    fun closeJornadaWithSnapshot(
        context: Context,
        jornadaId: Long = 0L,
        finalCash: Double,
        notes: String,
        onResult: (Boolean, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            val currentJornada = _uiState.value.activeJornada ?: run {
                onResult(false, null, "No hay ninguna jornada activa.")
                return@launch
            }
            val username = _uiState.value.currentUser?.username ?: "dueno"
            val state = _uiState.value

            // 1. CIERRE AUTOMÁTICO DE TANDAS AL CERRAR JORNADA
            val openTandas = state.tandas.filter {
                (it.jornadaId == currentJornada.id || (currentJornada.openedAt > 0 && it.date >= currentJornada.openedAt)) &&
                (it.status == "ACTIVA" || it.status == "ABIERTA" || it.status == "ACTIVADA")
            }
            val closedTandasForJornada = openTandas.map { tanda ->
                val finalQty = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val rend = if (tanda.baseQuantityUsed > 0.0) finalQty / tanda.baseQuantityUsed else 0.0
                val prod = state.products.find { it.id == tanda.productId }
                val salePrice = if (tanda.salePrice > 0.0) tanda.salePrice else (prod?.price ?: 0.0)
                val rev = finalQty * salePrice
                val profit = rev - tanda.totalBatchCost
                val pMargin = if (rev > 0.0) (profit / rev) * 100.0 else 0.0
                val uCost = if (finalQty > 0.0) tanda.totalBatchCost / finalQty else 0.0

                val closed = tanda.copy(
                    jornadaId = currentJornada.id,
                    status = "CERRADA",
                    actualYield = finalQty,
                    yieldPercentage = rend,
                    expectedRevenue = rev,
                    estimatedProfit = profit,
                    profitMargin = pMargin,
                    realUnitCost = uCost
                )
                repository.updateTanda(closed)
                closed
            }

            val updatedTandasList = state.tandas.map { t ->
                closedTandasForJornada.find { it.id == t.id } ?: t
            }

            val calcResult = com.example.util.QJornadaExporter.calculateJornadaEconomics(
                jornada = currentJornada,
                allTandas = updatedTandasList,
                allMovimientos = state.movimientosMercaderia,
                allProducts = state.products,
                allMercaderias = state.mercaderias,
                allGastos = state.gastosGenerales,
                allInversiones = state.inversiones,
                currentUser = state.currentUser,
                allMateriasPrimas = state.materiasPrimas,
                finalCashInput = finalCash,
                closedByInput = username,
                notesInput = notes
            )

            val configNegocio = state.businessConfig
            val nombreNegocio = configNegocio?.nombreNegocio ?: state.businessName
            val codigoNegocio = configNegocio?.codigoNegocio ?: "NEG-000001"

            val jsonString = com.example.util.QJornadaExporter.generateQJornadaJsonString(
                calc = calcResult,
                nombreNegocio = nombreNegocio,
                codigoNegocio = codigoNegocio
            )

            // Save file to local storage
            com.example.util.QJornadaExporter.saveQJornadaToFile(context, jsonString, currentJornada.id)

            // Save Informe Insumos Archive Snapshot congelado
            try {
                val insumosArchive = com.example.util.InformeInsumosArchiveManager.buildArchiveFromHistoricalData(
                    jornada = currentJornada.copy(closedAt = System.currentTimeMillis(), closedBy = username),
                    allMaterias = state.materiasPrimas,
                    allMovimientos = state.movimientosMateriaPrima,
                    businessName = nombreNegocio,
                    closedBy = username
                )
                com.example.util.InformeInsumosArchiveManager.saveArchive(context, insumosArchive)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Save Cuadre de Caja Archive Snapshot congelado
            try {
                val cuadreArchive = com.example.util.CuadreCajaArchiveManager.getOrBuildArchive(
                    context = context,
                    jornada = currentJornada.copy(closedAt = System.currentTimeMillis(), closedBy = username, finalCash = finalCash, notes = notes),
                    allTandas = updatedTandasList,
                    allMovimientosMercaderia = state.movimientosMercaderia,
                    allProducts = state.products,
                    allMercaderias = state.mercaderias,
                    allTransferencias = state.allTransferencias,
                    businessName = nombreNegocio
                )
                com.example.util.CuadreCajaArchiveManager.saveArchive(context, cuadreArchive)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Close in DB with immutable snapshot
            repository.closeJornada(
                jornada = currentJornada,
                finalCash = finalCash,
                closedBy = username,
                notes = notes,
                snapshotJson = jsonString,
                realSalesProd = calcResult.produccionIngresosReales,
                realCostProd = calcResult.produccionCostosReales,
                gastosProd = calcResult.produccionGastos,
                invProd = calcResult.produccionInversiones,
                resProd = calcResult.resultadoProduccion,
                realSalesMerc = calcResult.mercaderiasIngresosReales,
                realCostMerc = calcResult.mercaderiasCostosReales,
                gastosMerc = calcResult.mercaderiasGastos,
                invMerc = calcResult.mercaderiasInversiones,
                resMerc = calcResult.resultadoMercaderias,
                totalIng = calcResult.totalIngresos,
                totalCost = calcResult.totalCostos,
                totalGast = calcResult.totalGastos,
                totalInv = calcResult.totalInversiones,
                utilidad = calcResult.utilidadDelDia,
                modulos = calcResult.modulosUtilizados
            )

            _uiState.update { it.copy(successMessage = "Jornada #${currentJornada.id} cerrada correctamente. Archivo Q_jornada.json generado.") }
            onResult(true, jsonString, null)
        }
    }

    fun updateJornada(jornada: Jornada) {
        viewModelScope.launch {
            repository.updateJornada(jornada)
        }
    }

    fun deleteJornada(jornadaId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteJornadaById(jornadaId)
            _uiState.update { current ->
                val updated = current.allJornadas.filter { it.id != jornadaId }
                val newActive = if (current.activeJornada?.id == jornadaId) null else current.activeJornada
                current.copy(
                    allJornadas = updated,
                    activeJornada = newActive,
                    successMessage = "Jornada #$jornadaId eliminada correctamente."
                )
            }
            onComplete()
        }
    }

    fun archivarJornada(
        jornada: Jornada,
        calcResult: com.example.util.QJornadaExporter.JornadaCalculationResult,
        extracciones: Double,
        liquidezFinal: Double,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val username = _uiState.value.currentUser?.username ?: "dueno"
                val state = _uiState.value
                val configNegocio = state.businessConfig
                val nombreNegocio = configNegocio?.nombreNegocio ?: state.businessName
                val codigoNegocio = configNegocio?.codigoNegocio ?: "NEG-000001"

                val jsonString = com.example.util.QJornadaExporter.generateQJornadaJsonString(
                    calc = calcResult,
                    nombreNegocio = nombreNegocio,
                    codigoNegocio = codigoNegocio
                )

                val closedJornada = jornada.copy(
                    isOpen = false,
                    closedAt = System.currentTimeMillis(),
                    closedBy = username,
                    realSalesProduccion = calcResult.produccionIngresosReales,
                    realCostProduccion = calcResult.produccionCostosReales,
                    gastosProduccion = calcResult.produccionGastos,
                    inversionesProduccion = calcResult.produccionInversiones,
                    resultadoProduccion = calcResult.resultadoProduccion,
                    realSalesMercaderias = calcResult.mercaderiasIngresosReales,
                    realCostMercaderias = calcResult.mercaderiasCostosReales,
                    gastosMercaderias = calcResult.mercaderiasGastos,
                    inversionesMercaderias = calcResult.mercaderiasInversiones,
                    resultadoMercaderias = calcResult.resultadoMercaderias,
                    totalIngresos = calcResult.totalIngresos,
                    totalCostos = calcResult.totalCostos,
                    totalGastos = calcResult.totalGastos,
                    totalInversiones = calcResult.totalInversiones,
                    utilidadDelDia = calcResult.utilidadDelDia,
                    extracciones = extracciones,
                    liquidezFinal = liquidezFinal,
                    modulosUtilizados = calcResult.modulosUtilizados,
                    snapshotJson = jsonString
                )

                repository.updateJornada(closedJornada)
                _uiState.update { current ->
                    val updatedList = current.allJornadas.map { if (it.id == closedJornada.id) closedJornada else it }
                    current.copy(
                        activeJornada = null,
                        allJornadas = updatedList,
                        successMessage = "Jornada #${closedJornada.id} archivada correctamente como registro histórico inmutable."
                    )
                }
                onResult(true, "Jornada #${closedJornada.id} archivada exitosamente.")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Error al archivar jornada")
            }
        }
    }

    fun insertPersonalContratado(personal: PersonalContratado, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.insertPersonalContratado(personal)
                _uiState.update { it.copy(successMessage = "Personal registrado y guardado localmente", errorMessage = null) }
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Error al guardar personal") }
            }
        }
    }

    fun updatePersonalContratado(personal: PersonalContratado, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.updatePersonalContratado(personal)
                _uiState.update { it.copy(successMessage = "Personal actualizado correctamente", errorMessage = null) }
                onComplete?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Error al actualizar personal") }
            }
        }
    }

    fun deletePersonalContratado(personal: PersonalContratado) {
        viewModelScope.launch {
            repository.deletePersonalContratado(personal)
            _uiState.update { it.copy(successMessage = "Personal eliminado") }
        }
    }

    fun addUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

    fun deleteUser(username: String) {
        val clean = username.trim().lowercase()
        if (clean == "admin") return
        viewModelScope.launch {
            repository.deleteUser(username)
            repository.deleteUser(clean)
            try {
                val db = repository.getDatabase()
                val personal = db.personalContratadoDao().getPersonalByUsername(username)
                    ?: db.personalContratadoDao().getPersonalByUsername(clean)
                if (personal != null) {
                    db.personalContratadoDao().deletePersonal(personal)
                }
            } catch (_: Exception) {}
        }
    }

    fun addBitacoraEntry(title: String, content: String, category: String, priority: String) {
        viewModelScope.launch {
            val author = _uiState.value.currentUser?.username ?: "dueno"
            repository.insertBitacora(
                BitacoraEntry(
                    title = title,
                    content = content,
                    category = category,
                    authorUsername = author,
                    priority = priority
                )
            )
        }
    }

    fun recordProductionBatch(itemName: String, quantity: Int, destination: String, notes: String) {
        viewModelScope.launch {
            val creator = _uiState.value.currentUser?.username ?: "cocinero"
            repository.recordBatch(
                ProductionBatch(
                    itemName = itemName,
                    quantity = quantity,
                    destination = destination,
                    notes = notes,
                    createdBy = creator
                )
            )
        }
    }

    fun recordStockMovement(productId: Long, productName: String, type: String, quantity: Int, reason: String) {
        viewModelScope.launch {
            val recorder = _uiState.value.currentUser?.username ?: "dueno"
            repository.recordMovement(
                StockMovement(
                    productId = productId,
                    productName = productName,
                    type = type,
                    quantity = quantity,
                    reason = reason,
                    recordedBy = recorder
                )
            )
        }
    }

    fun recordBarraEntrada(
        productId: Long,
        productName: String,
        quantity: Int,
        origin: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (quantity <= 0) {
            onError("La cantidad debe ser mayor a cero (0)")
            return
        }
        val product = _uiState.value.products.find { it.id == productId }
        if (product == null || product.destination != "BARRA") {
            onError("El producto seleccionado no pertenece al inventario de Barra")
            return
        }
        val activeJornada = _uiState.value.activeJornada
        if (activeJornada == null || !activeJornada.isOpen) {
            onError("No hay una jornada activa abierta")
            return
        }
        val currentUser = _uiState.value.currentUser
        val username = currentUser?.username ?: "barra1"

        viewModelScope.launch {
            try {
                repository.recordBarraEntrada(
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    reason = if (origin.isBlank()) "Almacén Central" else origin.trim(),
                    recordedBy = username,
                    jornadaId = activeJornada.id
                )
                _uiState.update { it.copy(successMessage = "Entrada de $quantity ud. de $productName registrada exitosamente") }
                onSuccess()
            } catch (e: Exception) {
                val err = e.message ?: "Error al registrar la entrada de inventario"
                _uiState.update { it.copy(errorMessage = err) }
                onError(err)
            }
        }
    }

    fun confirmarComandaBarra(
        tableNumber: Int?,
        cartItems: List<BarraCartItem>,
        context: Context? = null,
        onSuccess: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (tableNumber != null && tableNumber <= 0) {
            val err = "El número de mesa debe ser mayor que 0."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }
        if (cartItems.isEmpty()) {
            val err = "La comanda no tiene productos seleccionados."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }

        viewModelScope.launch {
            try {
                val username = _uiState.value.currentUser?.username ?: "barra1"
                var activeJornada = _uiState.value.activeJornada
                if (activeJornada == null || !activeJornada.isOpen) {
                    val dbJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                    if (dbJornada != null && dbJornada.isOpen) {
                        activeJornada = dbJornada
                        _uiState.update { it.copy(activeJornada = dbJornada) }
                    } else {
                        val newJornadaId = withContext(Dispatchers.IO) {
                            repository.openJornada(0.0, username)
                        }
                        val openedJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                        activeJornada = openedJornada ?: Jornada(id = newJornadaId, openedAt = System.currentTimeMillis(), openedBy = username, isOpen = true)
                        _uiState.update { it.copy(activeJornada = activeJornada) }
                    }
                }

                val activeJornadaId = activeJornada?.id ?: 1L
                val maxInDb = repository.getMaxComandaNumberForJornada(activeJornadaId)
                val maxInState = _uiState.value.allOrders
                    .filter { it.jornadaId == activeJornadaId }
                    .maxOfOrNull { it.comandaNumber } ?: 0
                val nextComandaNumber = maxOf(maxInDb, maxInState) + 1

                val totalBarra = cartItems.sumOf { it.totalAmount }
                val now = System.currentTimeMillis()

                val newOrder = TableOrder(
                    tableNumber = tableNumber,
                    customerName = if (tableNumber == null) "Para Llevar" else "Barra",
                    waiterUsername = username,
                    createdAt = now,
                    confirmedAt = now,
                    status = "ABIERTA",
                    totalAmount = totalBarra,
                    totalCocina = 0.0,
                    totalBarra = totalBarra,
                    jornadaId = activeJornadaId,
                    comandaNumber = nextComandaNumber
                )

                val orderItems = cartItems.map { item ->
                    val finalNotes = if (item.agregadosNoteString.isNotEmpty()) {
                        "${item.subcategory} | ${item.agregadosNoteString}"
                    } else {
                        item.subcategory
                    }
                    OrderItem(
                        orderId = 0L,
                        productId = item.product.id,
                        productName = item.product.name,
                        unitPrice = item.unitPriceWithAgregados,
                        quantity = item.quantity,
                        destination = "BARRA",
                        notes = finalNotes,
                        status = "PENDIENTE",
                        productCode = item.product.code
                     )
                }

                repository.createComandaBarra(newOrder, orderItems)

                val targetDisplay = if (tableNumber == null) "Para Llevar" else "Mesa $tableNumber"
                _uiState.update {
                    it.copy(successMessage = "Comanda #$nextComandaNumber registrada para $targetDisplay. Lista para cobro.")
                }
                onSuccess(nextComandaNumber)
            } catch (e: Exception) {
                val err = e.message ?: "Error al registrar la comanda"
                _uiState.update { it.copy(errorMessage = err) }
                onError(err)
            }
        }
    }

    fun cobrarComandaBarra(
        order: TableOrder,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUser = _uiState.value.currentUser
        val username = currentUser?.username ?: "barra1"

        viewModelScope.launch {
            val result = repository.cobrarComandaBarra(
                orderId = order.id,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                chargedBy = username,
                currency = currency,
                exchangeRate = exchangeRate,
                amountInCurrency = amountInCurrency
            )

            result.fold(
                onSuccess = { closedOrder ->
                    _uiState.update {
                        it.copy(successMessage = "Comanda #${closedOrder.comandaNumber} cobrada exitosamente ($${"%.2f".format(closedOrder.totalAmount)} CUP). Inventario descontado.")
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    val msg = error.message ?: "Error al procesar el cobro"
                    _uiState.update { it.copy(errorMessage = msg) }
                    onError(msg)
                }
            )
        }
    }

    fun closeJornadaBarra(
        notes: String = "",
        physicalCounts: Map<Long, Int> = emptyMap(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val activeJornada = _uiState.value.activeJornada
        if (activeJornada == null || !activeJornada.isOpen) {
            val err = "No existe una jornada activa de Barra para cerrar."
            _uiState.update { it.copy(errorMessage = err) }
            onError(err)
            return
        }

        val username = _uiState.value.currentUser?.username ?: "barra1"

        viewModelScope.launch {
            try {
                // Validar que no haya comandas pendientes antes de cerrar
                val pendingOrders = _uiState.value.allOrders.filter {
                    it.jornadaId == activeJornada.id && it.status != "COBRADA" && it.status != "CANCELADA"
                }
                if (pendingOrders.isNotEmpty()) {
                    val err = "No se puede cerrar la jornada: existen ${pendingOrders.size} comandas pendientes de cobro."
                    _uiState.update { it.copy(errorMessage = err) }
                    onError(err)
                    return@launch
                }

                val closedOrders = _uiState.value.allOrders.filter {
                    it.jornadaId == activeJornada.id && it.status == "COBRADA"
                }
                val totalVentas = closedOrders.sumOf { it.totalAmount }

                // Record any physical inventory discrepancy adjustments if needed
                val barProducts = _uiState.value.products.filter { it.destination == "BARRA" }
                for (prod in barProducts) {
                    val physical = physicalCounts[prod.id]
                    if (physical != null && physical != prod.stock) {
                        val dif = physical - prod.stock
                        val movType = if (dif > 0) "AJUSTE_POSITIVO" else "AJUSTE_NEGATIVO"
                        repository.recordMovement(
                            com.example.data.local.model.StockMovement(
                                productId = prod.id,
                                productName = prod.name,
                                type = movType,
                                quantity = kotlin.math.abs(dif),
                                reason = "Ajuste por Cierre Físico de Barra (Jornada #${activeJornada.id})",
                                recordedBy = username,
                                timestamp = System.currentTimeMillis(),
                                jornadaId = activeJornada.id
                            )
                        )
                        repository.updateProduct(prod.copy(stock = physical))
                    }
                }

                repository.closeJornadaBarraAndOpenNew(
                    jornada = activeJornada,
                    closedBy = username,
                    notes = notes,
                    totalVentas = totalVentas
                )

                _uiState.update {
                    it.copy(successMessage = "Jornada #${activeJornada.id} de Barra cerrada y archivada exitosamente. Nueva jornada iniciada.")
                }
                onSuccess()
            } catch (e: Exception) {
                val err = e.message ?: "Error al realizar el cierre de jornada"
                _uiState.update { it.copy(errorMessage = err) }
                onError(err)
            }
        }
    }

    fun createUser(user: User) {
        saveUserByAdmin(user)
    }

    fun saveUserByAdmin(user: User, onComplete: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val db = repository.getDatabase()
                val cleanUser = user.copy(username = user.username.trim().lowercase())
                val existing = db.userDao().getUserByUsername(cleanUser.username)
                if (existing != null) {
                    db.userDao().updateUser(cleanUser)
                } else {
                    db.userDao().insertUser(cleanUser)
                }

                // Sincronizar con PersonalContratado para mantener consistencia
                try {
                    val personalDao = db.personalContratadoDao()
                    val existingPersonal = personalDao.getPersonalByUsername(cleanUser.username)
                    val roleStr = when (cleanUser.role) {
                        UserRole.ADMIN -> "ADMINISTRADOR"
                        UserRole.DUENO -> "DUEÑO"
                        UserRole.DEPENDIENTE -> "DEPENDIENTE"
                        UserRole.CAJERO -> "CAJERO"
                        UserRole.COCINA -> "COCINA"
                        UserRole.SALON -> "DEPENDIENTE"
                        UserRole.BARRA -> "BARRA"
                    }
                    if (existingPersonal != null) {
                        personalDao.updatePersonal(
                            existingPersonal.copy(
                                nombreCompleto = cleanUser.fullName,
                                movil = cleanUser.telefono,
                                passwordHash = cleanUser.passwordHash,
                                role = roleStr,
                                montoPorProducto = cleanUser.montoPorProducto,
                                permisoProduccion = cleanUser.permisoProduccion,
                                permisoMercancias = cleanUser.permisoMercancias,
                                permisoPersonal = cleanUser.permisoPersonal,
                                permisoControlNegocio = cleanUser.permisoControlNegocio,
                                isActive = cleanUser.isActive,
                                tieneAccesoApp = true
                            )
                        )
                    } else {
                        personalDao.insertPersonal(
                            com.example.data.local.model.PersonalContratado(
                                nombreCompleto = cleanUser.fullName,
                                carnetIdentidad = "",
                                movil = cleanUser.telefono,
                                formaPago = if (cleanUser.montoPorProducto > 0) "Por producto" else "Fijo",
                                username = cleanUser.username,
                                passwordHash = cleanUser.passwordHash,
                                role = roleStr,
                                montoPorProducto = cleanUser.montoPorProducto,
                                tieneAccesoApp = true,
                                isActive = cleanUser.isActive,
                                permisoProduccion = cleanUser.permisoProduccion,
                                permisoMercancias = cleanUser.permisoMercancias,
                                permisoPersonal = cleanUser.permisoPersonal,
                                permisoControlNegocio = cleanUser.permisoControlNegocio
                            )
                        )
                    }
                } catch (_: Exception) {}

                _uiState.update { it.copy(successMessage = "Usuario ${cleanUser.username} guardado localmente") }
                onComplete?.invoke(cleanUser)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al guardar usuario: ${e.message}") }
            }
        }
    }


    fun updateUrls(
        urlUsuarios: String,
        urlCatalogo: String,
        urlMercainv: String,
        telefonoDueno: String? = null,
        telefonoCajero: String? = null,
        telefonoAdmin: String? = null,
        urlQDueno: String? = null,
        urlVersionJson: String? = null
    ) {
        viewModelScope.launch {
            val db = repository.getDatabase()
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                val newConfig = config.copy(
                    urlUsuariosJson = urlUsuarios,
                    urlCatalogoJson = urlCatalogo,
                    urlMercainvJson = urlMercainv,
                    telefonoDueno = telefonoDueno ?: config.telefonoDueno,
                    telefonoCajero = telefonoCajero ?: config.telefonoCajero,
                    telefonoAdmin = telefonoAdmin ?: config.telefonoAdmin,
                    urlQDuenoJson = urlQDueno ?: config.urlQDuenoJson,
                    urlVersionJson = urlVersionJson ?: config.urlVersionJson
                )
                db.configuracionGeneralDao().updateConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Configuración guardada exitosamente") }
            } else {
                val newConfig = ConfiguracionGeneral(
                    id = 1,
                    urlUsuariosJson = urlUsuarios,
                    urlCatalogoJson = urlCatalogo,
                    urlMercainvJson = urlMercainv,
                    telefonoDueno = telefonoDueno ?: "",
                    telefonoCajero = telefonoCajero ?: "",
                    telefonoAdmin = telefonoAdmin ?: "",
                    urlQDuenoJson = urlQDueno ?: ""
                )
                db.configuracionGeneralDao().insertConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Configuración guardada exitosamente") }
            }
        }
    }

    fun updateOwnerPhone(phone: String) {
        viewModelScope.launch {
            val db = repository.getDatabase()
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                val newConfig = config.copy(telefonoDueno = phone.trim())
                db.configuracionGeneralDao().updateConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono del dueño guardado exitosamente") }
            } else {
                val newConfig = ConfiguracionGeneral(
                    id = 1,
                    telefonoDueno = phone.trim()
                )
                db.configuracionGeneralDao().insertConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono del dueño guardado exitosamente") }
            }
        }
    }

    fun updateCajeroPhone(phone: String) {
        viewModelScope.launch {
            val db = repository.getDatabase()
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                val newConfig = config.copy(telefonoCajero = phone.trim())
                db.configuracionGeneralDao().updateConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono de Caja guardado exitosamente") }
            } else {
                val newConfig = ConfiguracionGeneral(
                    id = 1,
                    telefonoCajero = phone.trim()
                )
                db.configuracionGeneralDao().insertConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono de Caja guardado exitosamente") }
            }
        }
    }

    fun updateAdminPhone(phone: String) {
        viewModelScope.launch {
            val db = repository.getDatabase()
            val config = db.configuracionGeneralDao().getConfigSync()
            if (config != null) {
                val newConfig = config.copy(telefonoAdmin = phone.trim())
                db.configuracionGeneralDao().updateConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono de Administrador guardado exitosamente") }
            } else {
                val newConfig = ConfiguracionGeneral(
                    id = 1,
                    telefonoAdmin = phone.trim()
                )
                db.configuracionGeneralDao().insertConfig(newConfig)
                _uiState.update { it.copy(generalConfig = newConfig, successMessage = "Teléfono de Administrador guardado exitosamente") }
            }
        }
    }

    fun updateUser(user: User) {
        saveUserByAdmin(user) { savedUser ->
            _uiState.update { state ->
                val updatedCurrent = if (state.currentUser?.username == savedUser.username) savedUser else state.currentUser
                state.copy(
                    currentUser = updatedCurrent,
                    successMessage = "Usuario actualizado exitosamente"
                )
            }
        }
    }

    fun createCategory(category: Category) {
        viewModelScope.launch {
            repository.insertCategory(category)
            _uiState.update { it.copy(successMessage = "Categoría creada exitosamente") }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
            _uiState.update { it.copy(successMessage = "Categoría actualizada exitosamente") }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            _uiState.update { it.copy(successMessage = "Categoría eliminada exitosamente") }
        }
    }

    fun createProduct(product: Product) {
        viewModelScope.launch {
            val dest = if (product.destination.isNotBlank()) product.destination else "COCINA"
            val finalCode = product.code.trim().ifBlank {
                com.example.util.ProductCodeHelper.generateNextProductCode(dest, product.category, _uiState.value.products)
            }
            repository.insertProduct(product.copy(code = finalCode, destination = dest))
            _uiState.update { it.copy(successMessage = "Producto creado exitosamente ($finalCode)", errorMessage = null) }
        }
    }

    fun createProductElaborado(product: Product, ppdVal: Double = 10.0) {
        createProductElaboradoWithRecipe(product, ppdVal, emptyList())
    }

    fun createProductElaboradoWithRecipe(
        product: Product,
        ppdVal: Double = 10.0,
        ingredients: List<Pair<Long, Pair<Double, String>>>
    ) {
        viewModelScope.launch {
            val dest = "COCINA"
            val finalCode = product.code.trim().ifBlank {
                com.example.util.ProductCodeHelper.generateNextProductCode(dest, product.category, _uiState.value.products)
            }
            val newId = repository.insertProduct(product.copy(code = finalCode, destination = dest))
            repository.insertProductoElaborado(
                ProductoElaborado(
                    productId = newId,
                    productionUnit = product.unitOfMeasure,
                    ppd = ppdVal,
                    estimatedDailyQuantity = ppdVal
                )
            )
            for ((materiaId, qtyUnit) in ingredients) {
                repository.insertRecetaIngrediente(
                    RecetaIngrediente(
                        productoElaboradoId = newId,
                        materiaPrimaId = materiaId,
                        quantity = qtyUnit.first,
                        unit = qtyUnit.second
                    )
                )
            }
            _uiState.update { it.copy(successMessage = "Producto de producción creado ($finalCode) con receta y PPD: ${ppdVal} ud/día", errorMessage = null) }
        }
    }

    fun importProductsFromTxtContent(
        content: String,
        onComplete: (importedCount: Int, errors: List<String>) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val lines = content.lines()
            val errors = mutableListOf<String>()
            var importedCount = 0

            val workingProductsList = _uiState.value.products.toMutableList()

            for ((index, rawLine) in lines.withIndex()) {
                val lineNumber = index + 1
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                val parts = line.split(";")
                if (parts.size < 4) {
                    errors.add("Línea $lineNumber: Formato incorrecto. Se esperan 4 campos (Nombre; Categoría; Unidad; PPD). Contenido: '$line'")
                    continue
                }

                val name = parts[0].trim()
                val category = parts[1].trim()
                val unit = parts[2].trim()
                val ppdStr = parts[3].trim()
                val ppdVal = ppdStr.toDoubleOrNull()

                if (name.isBlank()) {
                    errors.add("Línea $lineNumber: El Nombre del producto no puede estar vacío.")
                    continue
                }
                if (category.isBlank()) {
                    errors.add("Línea $lineNumber: La Categoría no puede estar vacía.")
                    continue
                }
                if (unit.isBlank()) {
                    errors.add("Línea $lineNumber: La Unidad de venta no puede estar vacía.")
                    continue
                }
                if (ppdVal == null || ppdVal <= 0.0) {
                    errors.add("Línea $lineNumber: El PPD debe ser un número positivo (recibido '$ppdStr').")
                    continue
                }

                // Check duplicate by name + category (case-insensitive) or code
                val isDuplicate = workingProductsList.any { existing ->
                    (existing.destination == "COCINA" || com.example.util.ProductCodeHelper.isCocina(existing.destination, existing.category, existing.code)) &&
                    existing.name.trim().equals(name, ignoreCase = true) &&
                    existing.category.trim().equals(category, ignoreCase = true)
                }

                if (isDuplicate) {
                    errors.add("Línea $lineNumber: El producto '$name' en la categoría '$category' ya existe.")
                    continue
                }

                // Generate next sequential code starting with PF-C-
                val code = com.example.util.ProductCodeHelper.generateNextProductCode("COCINA", category, workingProductsList)

                val newProduct = Product(
                    code = code,
                    name = name,
                    category = category,
                    unitOfMeasure = unit,
                    destination = "COCINA",
                    price = 0.0,
                    cost = 0.0,
                    isAvailable = true
                )

                try {
                    val newId = repository.insertProduct(newProduct)
                    val insertedProduct = newProduct.copy(id = newId)
                    workingProductsList.add(insertedProduct)

                    repository.insertProductoElaborado(
                        ProductoElaborado(
                            productId = newId,
                            recipeName = name,
                            productionUnit = unit,
                            estimatedDailyQuantity = ppdVal,
                            ppd = ppdVal
                        )
                    )

                    // Ensure category exists
                    val catExists = _uiState.value.categories.any { it.name.trim().equals(category, ignoreCase = true) }
                    if (!catExists) {
                        repository.insertCategory(Category(name = category))
                    }

                    importedCount++
                } catch (e: Exception) {
                    errors.add("Línea $lineNumber: Error al guardar en base de datos: ${e.message}")
                }
            }

            if (importedCount > 0) {
                _uiState.update {
                    it.copy(
                        successMessage = "Se importaron $importedCount producto(s) de Producción exitosamente.",
                        errorMessage = if (errors.isNotEmpty()) "${errors.size} línea(s) no se pudieron importar." else null
                    )
                }
            } else if (errors.isNotEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = "No se pudo importar ningún producto. Verifique las líneas con error.")
                }
            }

            onComplete(importedCount, errors)
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            // If it's a BARRA product with Mercaderia, keep active state in sync
            val merc = _uiState.value.mercaderias.find { it.productId == product.id }
            if (merc != null && merc.isActive != product.isAvailable) {
                repository.updateMercaderia(merc.copy(isActive = product.isAvailable))
            }
            // If it's a COCINA product with ProductoElaborado, keep active state in sync
            val prodElaborado = _uiState.value.productosElaborados.find { it.productId == product.id }
            if (prodElaborado != null && prodElaborado.isActive != product.isAvailable) {
                repository.updateProductoElaborado(prodElaborado.copy(isActive = product.isAvailable))
            }
            _uiState.update { it.copy(successMessage = "Producto actualizado exitosamente", errorMessage = null) }
        }
    }

    // Delete product logic
    fun deleteProduct(id: Long) {
        viewModelScope.launch {
            val product = _uiState.value.products.find { it.id == id }
            if (product != null) {
                if (product.destination == "COCINA" || com.example.util.ProductCodeHelper.isCocina(product.destination, product.category, product.code)) {
                    val prodElaborado = _uiState.value.productosElaborados.find { it.productId == id }
                    if (prodElaborado != null) {
                        repository.deleteIngredientsForProduct(prodElaborado.id)
                        repository.deleteProductoElaborado(prodElaborado)
                    }
                    repository.deleteProduct(id)
                    _uiState.update { it.copy(successMessage = "Producto de Producción eliminado exitosamente", errorMessage = null) }
                } else {
                    repository.updateProduct(product.copy(isAvailable = false))
                    val merc = _uiState.value.mercaderias.find { it.productId == id }
                    if (merc != null) {
                        repository.updateMercaderia(merc.copy(isActive = false))
                    }
                    _uiState.update { it.copy(successMessage = "Producto desactivado exitosamente", errorMessage = null) }
                }
            }
        }
    }

    fun clearAllCocinaProducts() {
        viewModelScope.launch {
            try {
                val cocinaProducts = _uiState.value.products.filter { p ->
                    p.destination == "COCINA" || com.example.util.ProductCodeHelper.isCocina(p.destination, p.category, p.code)
                }
                val cocinaProductIds = cocinaProducts.map { it.id }.toSet()
                val elaborados = _uiState.value.productosElaborados.filter { it.productId in cocinaProductIds }

                for (el in elaborados) {
                    repository.deleteIngredientsForProduct(el.id)
                    repository.deleteProductoElaborado(el)
                }

                for (p in cocinaProducts) {
                    repository.deleteProduct(p.id)
                }

                _uiState.update {
                    it.copy(
                        successMessage = "Todos los productos de Producción y sus recetas fueron eliminados exitosamente.",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al limpiar productos de Producción: ${e.message}") }
            }
        }
    }

    fun updateBusinessConfig(config: ConfiguracionNegocio) {
        viewModelScope.launch {
            repository.updateBusinessConfig(config)
            _uiState.update { it.copy(successMessage = "Información del negocio guardada localmente") }
        }
    }

    fun updateGeneralConfig(config: ConfiguracionGeneral) {
        viewModelScope.launch {
            repository.updateGeneralConfig(config)
            _uiState.update { it.copy(successMessage = "Configuración general guardada localmente") }
        }
    }

    // Production CRUD
    fun insertMateriaPrima(m: MateriaPrima) {
        viewModelScope.launch {
            repository.insertMateriaPrima(m)
            _uiState.update { it.copy(successMessage = "Materia prima agregada exitosamente") }
        }
    }

    fun updateMateriaPrima(m: MateriaPrima) {
        viewModelScope.launch {
            val currentJornada = _uiState.value.activeJornada
            val existing = _uiState.value.materiasPrimas.find { it.id == m.id }
            val isJornadaOpen = currentJornada?.isOpen == true

            if (existing != null && isJornadaOpen && existing.initialStock != m.initialStock) {
                // Recharse modification of initial stock when jornada is open
                val safeM = m.copy(initialStock = existing.initialStock)
                repository.updateMateriaPrima(safeM)
                _uiState.update {
                    it.copy(
                        errorMessage = "No se puede modificar el Stock Inicial con la jornada abierta. Utilice Entradas o Salidas.",
                        successMessage = "Materia prima actualizada (Stock inicial mantenido)"
                    )
                }
            } else {
                repository.updateMateriaPrima(m)
                _uiState.update { it.copy(successMessage = "Materia prima actualizada") }
            }
        }
    }

    fun deleteMateriaPrima(m: MateriaPrima) {
        viewModelScope.launch {
            repository.deleteMateriaPrima(m)
            _uiState.update { it.copy(successMessage = "Materia prima eliminada") }
        }
    }

    fun registerEntradaMateriaPrima(
        materiaPrimaId: Long,
        cantidad: Double,
        unidad: String,
        importe: Double,
        gastosCompra: Double = 0.0,
        responsibleUser: String = "Dueño",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val mpList = _uiState.value.materiasPrimas
            val raw = mpList.find { it.id == materiaPrimaId } ?: return@launch

            val totalValorEntrada = importe + maxOf(0.0, gastosCompra)
            val existenciaAgregadaBase = convertToBaseQty(cantidad, unidad)

            val currentStock = maxOf(0.0, raw.stock)
            val currentUnitCost = raw.unitCost
            val currentTotalValue = currentStock * currentUnitCost

            val nextStock = raw.stock + existenciaAgregadaBase
            val nextCost = if (currentStock > 0.0 && existenciaAgregadaBase > 0.0) {
                (currentTotalValue + totalValorEntrada) / (currentStock + existenciaAgregadaBase)
            } else if (existenciaAgregadaBase > 0.0) {
                totalValorEntrada / existenciaAgregadaBase
            } else {
                raw.unitCost
            }

            val updatedMp = raw.copy(
                unitCost = nextCost,
                stock = nextStock,
                purchasePrice = if (cantidad > 0.0) importe / cantidad else importe,
                purchaseUnit = unidad,
                purchaseQuantity = cantidad
            )

            val gastoInfo = if (gastosCompra > 0.0) " (Gastos: $$gastosCompra)" else ""
            val movimiento = MovimientoMateriaPrima(
                materiaPrimaId = materiaPrimaId,
                materiaPrimaName = raw.name,
                type = "ENTRADA",
                quantity = existenciaAgregadaBase,
                unit = raw.unit,
                responsibleUser = responsibleUser,
                notes = "Entrada: $cantidad $unidad con importe de $$importe$gastoInfo. Costo promedio ponderado recalculado: $${"%.2f".format(nextCost)}/${raw.unit}." + (if (notes.isNotBlank()) " $notes" else ""),
                resultingStock = nextStock
            )

            repository.updateMateriaPrima(updatedMp)
            repository.insertMovimientoMateriaPrima(movimiento)
            _uiState.update { it.copy(successMessage = "Entrada de insumo registrada exitosamente") }
        }
    }

    fun registerEntradaMateriaPrima(
        materiaPrimaId: Long,
        precio: Double,
        unidad: String,
        cantidad: Double,
        cantidadComprada: Double,
        notes: String,
        responsibleUser: String,
        gasto: Double = 0.0,
        compartido: Int = 1
    ) {
        val divisor = compartido.coerceIn(1, 5)
        val assignedExpenses = gasto / divisor
        val totalQty = cantidad * cantidadComprada
        val totalImporte = precio * cantidadComprada
        registerEntradaMateriaPrima(
            materiaPrimaId = materiaPrimaId,
            cantidad = totalQty,
            unidad = unidad,
            importe = totalImporte,
            gastosCompra = assignedExpenses,
            responsibleUser = responsibleUser,
            notes = notes
        )
    }

    fun registrarSalidaParaVentaAgregado(
        materiaPrimaId: Long,
        cantidadFisica: Double,
        responsibleUser: String = "Dueño",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val mpList = _uiState.value.materiasPrimas
            val raw = mpList.find { it.id == materiaPrimaId } ?: return@launch
            if (cantidadFisica <= 0.0) return@launch

            val racionesSalida = if (raw.rationQuantity > 0.0) cantidadFisica / raw.rationQuantity else 0.0
            val nextStock = (raw.stock - cantidadFisica).coerceAtLeast(0.0)
            val nextStockEnVenta = raw.stockEnVenta + cantidadFisica
            val nextRacionesEnVenta = raw.racionesEnVenta + racionesSalida

            val updatedMp = raw.copy(
                stock = nextStock,
                stockEnVenta = nextStockEnVenta,
                racionesEnVenta = nextRacionesEnVenta
            )

            val movimiento = MovimientoMateriaPrima(
                materiaPrimaId = materiaPrimaId,
                materiaPrimaName = raw.name,
                type = "SALIDA_VENTA",
                quantity = cantidadFisica,
                unit = raw.unit,
                responsibleUser = responsibleUser,
                notes = "Salida para Venta (Agregado): $cantidadFisica ${raw.unit} (${"%.1f".format(racionesSalida)} raciones). " + notes,
                resultingStock = nextStock
            )

            repository.updateMateriaPrima(updatedMp)
            repository.insertMovimientoMateriaPrima(movimiento)
            _uiState.update { it.copy(successMessage = "Salida para venta de Agregado registrada. Stock en almacén actualizado.") }
        }
    }

    fun insertProductoElaborado(p: ProductoElaborado) {
        viewModelScope.launch {
            repository.insertProductoElaborado(p)
            _uiState.update { it.copy(successMessage = "Producto elaborado registrado exitosamente") }
        }
    }

    fun updateProductoElaborado(p: ProductoElaborado) {
        viewModelScope.launch {
            repository.updateProductoElaborado(p)
            _uiState.update { it.copy(successMessage = "Producto elaborado actualizado") }
        }
    }

    fun updateProductElaboradoFull(product: Product, ppdVal: Double) {
        viewModelScope.launch {
            repository.updateProduct(product)
            val prodElaborado = _uiState.value.productosElaborados.find { it.productId == product.id }
            if (prodElaborado != null) {
                repository.updateProductoElaborado(
                    prodElaborado.copy(
                        productionUnit = product.unitOfMeasure,
                        ppd = ppdVal,
                        estimatedDailyQuantity = ppdVal,
                        isActive = product.isAvailable
                    )
                )
            } else {
                repository.insertProductoElaborado(
                    ProductoElaborado(
                        productId = product.id,
                        productionUnit = product.unitOfMeasure,
                        ppd = ppdVal,
                        estimatedDailyQuantity = ppdVal,
                        isActive = product.isAvailable
                    )
                )
            }
            _uiState.update { it.copy(successMessage = "Producto de producción actualizado exitosamente", errorMessage = null) }
        }
    }

    fun setProductoDefinitivePrice(productId: Long, definitivePrice: Double, calculatedCost: Double) {
        viewModelScope.launch {
            // 1. Update or create ProductoElaborado with definitive price
            val prodElaborado = _uiState.value.productosElaborados.find { it.productId == productId }
            if (prodElaborado != null) {
                repository.updateProductoElaborado(
                    prodElaborado.copy(
                        precioDefinitivo = definitivePrice,
                        hasPrecioDefinitivo = true
                    )
                )
            } else {
                repository.insertProductoElaborado(
                    ProductoElaborado(
                        productId = productId,
                        precioDefinitivo = definitivePrice,
                        hasPrecioDefinitivo = true
                    )
                )
            }

            // 2. Update Product price and cost in Catalog
            val product = _uiState.value.products.find { it.id == productId }
            if (product != null) {
                repository.updateProduct(
                    product.copy(
                        price = definitivePrice,
                        cost = calculatedCost
                    )
                )
            }
            _uiState.update { it.copy(successMessage = "Precio definitivo establecido: $${"%.2f".format(definitivePrice)} CUP (Sincronizado con Catálogo)") }
        }
    }

    fun setPresentacionEspecialDefinitivePrice(productId: Long, presentationName: String, definitivePrice: Double) {
        viewModelScope.launch {
            val product = _uiState.value.products.find { it.id == productId } ?: return@launch
            val currentList = parsePresentacionesEspeciales(product.presentacionesEspeciales)
            val updatedList = currentList.map { pres ->
                if (pres.name.equals(presentationName, ignoreCase = true)) {
                    pres.copy(price = definitivePrice)
                } else pres
            }
            val updatedJson = serializePresentacionesEspeciales(updatedList)
            val updatedProduct = product.copy(presentacionesEspeciales = updatedJson)
            repository.updateProduct(updatedProduct)

            // Si existe un producto en catálogo con el nombre de la presentación especial, sincronizarlo también
            val matchingCatalogProd = _uiState.value.products.find {
                it.id != product.id && (
                    it.name.equals(presentationName, ignoreCase = true) ||
                    it.name.equals("${product.name} - $presentationName", ignoreCase = true) ||
                    it.name.equals("${product.name} ($presentationName)", ignoreCase = true)
                )
            }
            if (matchingCatalogProd != null) {
                repository.updateProduct(matchingCatalogProd.copy(price = definitivePrice))
            }

            _uiState.update { it.copy(successMessage = "Precio definitivo de '$presentationName' fijado en $${"%.2f".format(definitivePrice)} CUP") }
        }
    }

    fun setMercaderiaDefinitivePrice(productId: Long, definitivePrice: Double, calculatedRealCost: Double) {
        viewModelScope.launch {
            val product = _uiState.value.products.find { it.id == productId }
            if (product != null) {
                repository.updateProduct(
                    product.copy(
                        price = definitivePrice,
                        cost = calculatedRealCost
                    )
                )
                _uiState.update { it.copy(successMessage = "Precio definitivo de '${product.name}' actualizado a $${"%.2f".format(definitivePrice)} CUP (Sincronizado con Catálogo)") }
            }
        }
    }

    fun updatePpd(productId: Long, ppdVal: Double) {
        viewModelScope.launch {
            val prodElaborado = _uiState.value.productosElaborados.find { it.productId == productId }
            if (prodElaborado != null) {
                repository.updateProductoElaborado(
                    prodElaborado.copy(ppd = ppdVal, estimatedDailyQuantity = ppdVal)
                )
                _uiState.update { it.copy(successMessage = "Producción Promedio Diaria (PPD) actualizada a ${ppdVal} ud/día") }
            } else {
                repository.insertProductoElaborado(
                    ProductoElaborado(
                        productId = productId,
                        ppd = ppdVal,
                        estimatedDailyQuantity = ppdVal
                    )
                )
            }
        }
    }

    fun updatePagosPersonalProductoElaborado(
        productId: Long,
        pagoCocinaUnitario: Double,
        cantidadCocineros: Int,
        pagoDependienteUnitario: Double,
        pagoCajeroUnitario: Double,
        isPagoCocinaFijo: Boolean = false
    ) {
        viewModelScope.launch {
            val prodElaborado = _uiState.value.productosElaborados.find { it.productId == productId }
            val cocinerosCount = if (cantidadCocineros > 0) cantidadCocineros else 1
            if (prodElaborado != null) {
                repository.updateProductoElaborado(
                    prodElaborado.copy(
                        pagoCocinaUnitario = pagoCocinaUnitario,
                        cantidadCocineros = cocinerosCount,
                        pagoDependienteUnitario = pagoDependienteUnitario,
                        pagoCajeroUnitario = pagoCajeroUnitario,
                        isPagoCocinaFijo = isPagoCocinaFijo
                    )
                )
            } else {
                val product = _uiState.value.products.find { it.id == productId }
                repository.insertProductoElaborado(
                    ProductoElaborado(
                        productId = productId,
                        productionUnit = product?.unitOfMeasure ?: "unidades",
                        pagoCocinaUnitario = pagoCocinaUnitario,
                        cantidadCocineros = cocinerosCount,
                        pagoDependienteUnitario = pagoDependienteUnitario,
                        pagoCajeroUnitario = pagoCajeroUnitario,
                        isPagoCocinaFijo = isPagoCocinaFijo
                    )
                )
            }
            _uiState.update { it.copy(successMessage = "Pagos de personal asociados guardados correctamente") }
        }
    }

    fun updateEstimatedDailyQuantity(productId: Long, quantity: Double) {
        updatePpd(productId, quantity)
    }

    fun deleteProductoElaborado(p: ProductoElaborado) {
        viewModelScope.launch {
            repository.deleteIngredientsForProduct(p.id)
            repository.deleteProductoElaborado(p)
            _uiState.update { it.copy(successMessage = "Producto elaborado y su receta eliminados") }
        }
    }

    fun convertProductToInsumo(
        product: Product,
        unitCost: Double? = null,
        unitOfMeasure: String? = null,
        stock: Double? = null
    ) {
        viewModelScope.launch {
            val costToUse = unitCost ?: if (product.cost > 0.0) product.cost else product.price
            val unitToUse = unitOfMeasure ?: product.unitOfMeasure.ifBlank { "Unidad" }
            val stockToUse = stock ?: product.stock.toDouble()

            // Check if MateriaPrima already exists linked to this product or matching name
            val existingMp = _uiState.value.materiasPrimas.find { it.productId == product.id || it.name.trim().equals(product.name.trim(), ignoreCase = true) }
            if (existingMp != null) {
                repository.updateMateriaPrima(
                    existingMp.copy(
                        name = product.name,
                        unit = unitToUse,
                        unitCost = costToUse,
                        stock = stockToUse,
                        productId = product.id,
                        isActive = true
                    )
                )
            } else {
                val newMp = MateriaPrima(
                    name = product.name,
                    unit = unitToUse,
                    unitCost = costToUse,
                    stock = stockToUse,
                    initialStock = stockToUse,
                    purchasePrice = costToUse,
                    purchaseUnit = unitToUse,
                    purchaseQuantity = 1.0,
                    productId = product.id,
                    isActive = true
                )
                repository.insertMateriaPrima(newMp)
            }

            // Update product flag without deleting history or duplicating inventory
            val updatedProd = product.copy(isConvertedToInsumo = true, cost = costToUse)
            repository.updateProduct(updatedProd)

            val author = _uiState.value.currentUser?.username ?: "dueno"
            repository.insertBitacora(
                BitacoraEntry(
                    title = "Conversión de Producto a Insumo",
                    content = "Producto '${product.name}' (${product.code}) convertido en Insumo. Stock: $stockToUse $unitToUse, Costo: $$costToUse.",
                    category = "PRODUCCION",
                    authorUsername = author,
                    priority = "NORMAL"
                )
            )
            _uiState.update { it.copy(successMessage = "Producto '${product.name}' convertido a Insumo exitosamente.") }
        }
    }

    fun insertRecetaIngrediente(i: RecetaIngrediente) {
        viewModelScope.launch {
            repository.insertRecetaIngrediente(i)
            _uiState.update { it.copy(successMessage = "Ingrediente agregado a la receta") }
        }
    }

    fun updateRecetaIngrediente(i: RecetaIngrediente) {
        viewModelScope.launch {
            repository.updateRecetaIngrediente(i)
            _uiState.update { it.copy(successMessage = "Ingrediente de receta actualizado") }
        }
    }

    fun deleteRecetaIngrediente(i: RecetaIngrediente) {
        viewModelScope.launch {
            repository.deleteRecetaIngrediente(i)
            _uiState.update { it.copy(successMessage = "Ingrediente removido de la receta") }
        }
    }

    fun insertGastoGeneral(g: com.example.data.local.model.GastoGeneral) {
        viewModelScope.launch {
            repository.insertGastoGeneral(g)
            _uiState.update { it.copy(successMessage = "Gasto general agregado exitosamente") }
        }
    }

    fun updateGastoGeneral(g: com.example.data.local.model.GastoGeneral) {
        viewModelScope.launch {
            repository.updateGastoGeneral(g)
            _uiState.update { it.copy(successMessage = "Gasto general actualizado exitosamente") }
        }
    }

    fun deleteGastoGeneral(g: com.example.data.local.model.GastoGeneral) {
        viewModelScope.launch {
            repository.deleteGastoGeneral(g)
            _uiState.update { it.copy(successMessage = "Gasto general eliminado exitosamente") }
        }
    }

    fun insertInversion(inv: com.example.data.local.model.Inversion) {
        viewModelScope.launch {
            repository.insertInversion(inv)
            _uiState.update { it.copy(successMessage = "Inversión registrada exitosamente") }
        }
    }

    fun updateInversion(inv: com.example.data.local.model.Inversion) {
        viewModelScope.launch {
            repository.updateInversion(inv)
            _uiState.update { it.copy(successMessage = "Inversión actualizada exitosamente") }
        }
    }

    fun deleteInversion(inv: com.example.data.local.model.Inversion) {
        viewModelScope.launch {
            repository.deleteInversion(inv)
            _uiState.update { it.copy(successMessage = "Inversión eliminada exitosamente") }
        }
    }

    fun insertPaymentProposal(proposal: PaymentProposal) {
        viewModelScope.launch {
            repository.insertPaymentProposal(proposal)
            _uiState.update { it.copy(successMessage = "Propuesta de pago registrada exitosamente") }
        }
    }

    fun updatePaymentProposal(proposal: PaymentProposal) {
        viewModelScope.launch {
            repository.updatePaymentProposal(proposal)
            _uiState.update { it.copy(successMessage = "Propuesta de pago actualizada exitosamente") }
        }
    }

    fun getMercaderiaCurrentStock(mercaderiaId: Long, initialStock: Double): Double {
        val movements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId }
        val hasInitialMov = movements.any { it.type == "INVENTARIO_INICIAL" }
        var stock = if (hasInitialMov) 0.0 else initialStock
        for (mov in movements) {
            when (mov.type.uppercase()) {
                "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> stock += mov.quantity
                "SALIDA", "AJUSTE_NEGATIVO", "MERMA", "PARA_VENTA" -> stock -= mov.quantity
                "VENTA" -> stock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                "AJUSTE" -> stock += mov.quantity
            }
        }
        return maxOf(0.0, stock)
    }

    fun createMercaderiaProduct(
        name: String,
        code: String,
        category: String,
        acquisitionCost: Double,
        definitivePrice: Double,
        unitOfMeasure: String,
        initialStock: Double,
        responsibleAdmin: String,
        description: String = "",
        directExpenses: Double = 0.0,
        directExpensesDetails: String = "[]",
        purchaseMode: String = "POR UNIDAD",
        purchasePrice: Double = 0.0,
        unitsPerLot: Double = 1.0
    ) {
        viewModelScope.launch {
            // 1. Create Product directly in Catalog (identified as BARRA)
            val generatedCode = code.ifBlank {
                com.example.util.ProductCodeHelper.generateNextProductCode("BARRA", category.ifBlank { "Bebidas" }, _uiState.value.products)
            }
            val product = Product(
                code = generatedCode.trim(),
                name = name.trim(),
                category = category.ifBlank { "Bebidas" },
                price = definitivePrice,
                cost = acquisitionCost,
                stock = initialStock.toInt(),
                minStock = 3,
                destination = "BARRA",
                isAvailable = true,
                description = description.trim(),
                unitOfMeasure = unitOfMeasure.ifBlank { "Unidad" }
            )
            val productId = repository.insertProduct(product)

            // 2. Register Mercaderia linking to this exact productId
            val mercaderia = Mercaderia(
                productId = productId,
                acquisitionCost = acquisitionCost,
                unitOfMeasure = unitOfMeasure.ifBlank { "Unidad" },
                initialStock = initialStock,
                isActive = true,
                directExpenses = directExpenses,
                directExpensesDetails = directExpensesDetails,
                purchaseMode = purchaseMode,
                purchasePrice = if (purchasePrice > 0.0) purchasePrice else acquisitionCost,
                unitsPerLot = if (unitsPerLot > 0.0) unitsPerLot else 1.0
            )
            val mercaderiaId = repository.insertMercaderia(mercaderia)

            // 3. Register initial stock movement
            if (initialStock >= 0) {
                val initialMov = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "INVENTARIO_INICIAL",
                    quantity = initialStock,
                    responsibleAdmin = responsibleAdmin.ifBlank { _uiState.value.currentUser?.username ?: "dueno" },
                    notes = "Configuración de inventario inicial al crear mercadería",
                    date = System.currentTimeMillis()
                )
                repository.insertMovimientoMercaderia(initialMov)
            }

            // 4. Audit in Bitacora
            val author = responsibleAdmin.ifBlank { _uiState.value.currentUser?.username ?: "dueno" }
            val modeDetails = if (purchaseMode == "POR LOTE") "Modalidad: Lote (${unitsPerLot.toInt()} u. a $$purchasePrice CUP)" else "Modalidad: Unidad"
            repository.insertBitacora(
                BitacoraEntry(
                    title = "Mercadería Creada",
                    content = "Nueva mercadería '$name' ($generatedCode) creada por $author. $modeDetails. Costo unitario real: $${"%.2f".format(acquisitionCost)} CUP, Gastos directos: $${"%.2f".format(directExpenses)} CUP, Precio definitivo: $${"%.2f".format(definitivePrice)} CUP, Stock inicial: $initialStock $unitOfMeasure.",
                    category = "MERCADERÍAS",
                    authorUsername = author,
                    priority = "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Mercadería '$name' creada y registrada en Catálogo") }
        }
    }

    fun updateMercaderiaAndProduct(
        mercaderia: Mercaderia,
        name: String,
        code: String,
        category: String,
        acquisitionCost: Double,
        definitivePrice: Double,
        unitOfMeasure: String,
        description: String = "",
        directExpenses: Double = mercaderia.directExpenses,
        directExpensesDetails: String = mercaderia.directExpensesDetails,
        purchaseMode: String = mercaderia.purchaseMode,
        purchasePrice: Double = mercaderia.purchasePrice,
        unitsPerLot: Double = mercaderia.unitsPerLot
    ) {
        viewModelScope.launch {
            // 1. Update Mercaderia entity
            val updatedMerc = mercaderia.copy(
                acquisitionCost = acquisitionCost,
                unitOfMeasure = unitOfMeasure,
                directExpenses = directExpenses,
                directExpensesDetails = directExpensesDetails,
                purchaseMode = purchaseMode,
                purchasePrice = purchasePrice,
                unitsPerLot = unitsPerLot
            )
            repository.updateMercaderia(updatedMerc)

            // 2. Update Product in Catalog (maintains exact same ID!)
            val existingProduct = _uiState.value.products.find { it.id == mercaderia.productId }
            if (existingProduct != null) {
                val updatedProduct = existingProduct.copy(
                    name = name.trim(),
                    code = code.trim().ifBlank { existingProduct.code },
                    category = category.ifBlank { existingProduct.category },
                    price = definitivePrice,
                    cost = acquisitionCost,
                    unitOfMeasure = unitOfMeasure,
                    description = description
                )
                repository.updateProduct(updatedProduct)
            }

            _uiState.update { it.copy(successMessage = "Mercadería '$name' y Catálogo actualizados correctamente") }
        }
    }

    fun toggleMercaderiaActive(mercaderia: Mercaderia) {
        viewModelScope.launch {
            val newStatus = !mercaderia.isActive
            val updatedMerc = mercaderia.copy(isActive = newStatus)
            repository.updateMercaderia(updatedMerc)

            // Sync with Product.isAvailable
            val existingProduct = _uiState.value.products.find { it.id == mercaderia.productId }
            if (existingProduct != null) {
                repository.updateProduct(existingProduct.copy(isAvailable = newStatus))
            }

            _uiState.update { it.copy(successMessage = "Mercadería ${if (newStatus) "activada" else "desactivada"}") }
        }
    }

    /**
     * Actualiza y persiste localmente las tarifas globales de pago de personal asociadas a Bebidas.
     * (Pago Dependiente por unidad vendida y Pago Cajero por unidad vendida).
     */
    fun updateTarifasPagoBebidas(
        pagoDependiente: Double,
        pagoCajero: Double,
        pagoDependienteModalidad: String = "FIJO",
        pagoDependienteValor: Double = 0.0,
        pagoCajeroModalidad: String = "FIJO",
        pagoCajeroValor: Double = 0.0
    ) {
        val tarifas = com.example.util.TarifasPagoBebidas(
            pagoDependientePorUnidad = maxOf(0.0, if (pagoDependienteModalidad == "FIJO") pagoDependienteValor else 0.0),
            pagoCajeroPorUnidad = maxOf(0.0, if (pagoCajeroModalidad == "FIJO") pagoCajeroValor else 0.0),
            pagoDependienteModalidad = pagoDependienteModalidad,
            pagoDependienteValor = maxOf(0.0, pagoDependienteValor),
            pagoCajeroModalidad = pagoCajeroModalidad,
            pagoCajeroValor = maxOf(0.0, pagoCajeroValor)
        )
        com.example.util.BebidasTarifasPreferences.saveTarifas(getApplication(), tarifas)
        _uiState.update { 
            it.copy(
                tarifasPagoBebidas = tarifas,
                successMessage = "Tarifas de personal para Bebidas guardadas correctamente"
            ) 
        }
    }

    fun insertMercaderia(productId: Long, acquisitionCost: Double, unitOfMeasure: String, initialStock: Double, responsibleAdmin: String) {
        viewModelScope.launch {
            val m = Mercaderia(
                productId = productId,
                acquisitionCost = acquisitionCost,
                unitOfMeasure = unitOfMeasure,
                initialStock = initialStock,
                isActive = true
            )
            val mercId = repository.insertMercaderia(m)
            // Insert initial inventory movement
            val movement = MovimientoMercaderia(
                mercaderiaId = mercId,
                type = "INVENTARIO_INICIAL",
                quantity = initialStock,
                responsibleAdmin = responsibleAdmin,
                notes = "Configuración de inventario inicial"
            )
            repository.insertMovimientoMercaderia(movement)
            _uiState.update { it.copy(successMessage = "Producto de mercadería registrado con inventario inicial") }
        }
    }

    fun updateMercaderia(m: Mercaderia) {
        viewModelScope.launch {
            repository.updateMercaderia(m)
            _uiState.update { it.copy(successMessage = "Mercadería actualizada exitosamente") }
        }
    }

    fun deleteMercaderia(m: Mercaderia) {
        viewModelScope.launch {
            // Logical deletion: preserve history
            repository.updateMercaderia(m.copy(isActive = false))
            val product = _uiState.value.products.find { it.id == m.productId }
            if (product != null) {
                repository.updateProduct(product.copy(isAvailable = false))
            }
            _uiState.update { it.copy(successMessage = "Mercadería desactivada") }
        }
    }

    fun deleteMercaderiaPermanently(m: Mercaderia) {
        viewModelScope.launch {
            try {
                repository.deleteMercaderiaPermanently(m)
                val product = _uiState.value.products.find { it.id == m.productId }
                if (product != null && product.destination == "BARRA") {
                    repository.deleteProduct(product.id)
                }
                _uiState.update { it.copy(successMessage = "Mercadería, producto asociado y movimientos eliminados definitivamente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar mercadería: ${e.message}") }
            }
        }
    }

    fun clearAllMercaderias() {
        viewModelScope.launch {
            try {
                val mercs = _uiState.value.mercaderias
                val mercProductIds = mercs.map { it.productId }.toSet()
                
                repository.deleteAllMercaderias()
                
                _uiState.value.products.filter { it.id in mercProductIds && it.destination == "BARRA" }.forEach { p ->
                    repository.deleteProduct(p.id)
                }
                
                _uiState.value.gastosGenerales.filter { it.scope == "MERCADERIAS" }.forEach { g ->
                    repository.deleteGastoGeneral(g)
                }
                _uiState.value.inversiones.filter { it.scope == "MERCADERIAS" }.forEach { inv ->
                    repository.deleteInversion(inv)
                }
                
                _uiState.update { it.copy(successMessage = "Se limpiaron todas las mercaderías, productos BARRA exclusivos, gastos e inversiones de forma coherente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al limpiar mercaderías: ${e.message}") }
            }
        }
    }

    fun importMercaderiasFromTxtContent(
        content: String,
        onComplete: (importedCount: Int, errors: List<String>) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val lines = content.lines()
            val errors = mutableListOf<String>()
            var importedCount = 0

            val currentUserStr = _uiState.value.currentUser?.username ?: "dueno"

            for ((index, rawLine) in lines.withIndex()) {
                val lineNumber = index + 1
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                val parts = line.split(";")
                if (parts.size < 4) {
                    errors.add("Línea $lineNumber: Formato incorrecto. Se esperan al menos 4 campos (Nombre; Código; Categoría; CostoAdquisición; PrecioVenta; UnidadMedida; StockInicial...). Contenido: '$line'")
                    continue
                }

                val name = parts[0].trim()
                val codeRaw = if (parts.size > 1) parts[1].trim() else ""
                val categoryRaw = if (parts.size > 2) parts[2].trim() else "Bebidas"
                val costStr = if (parts.size > 3) parts[3].trim() else "0"
                val priceStr = if (parts.size > 4) parts[4].trim() else "0"
                val unitRaw = if (parts.size > 5) parts[5].trim() else "Unidad"
                val stockStr = if (parts.size > 6) parts[6].trim() else "0"
                val modeRaw = if (parts.size > 7) parts[7].trim().uppercase() else "POR UNIDAD"
                val unitsPerLotStr = if (parts.size > 8) parts[8].trim() else "1"
                val directExpStr = if (parts.size > 9) parts[9].trim() else "0"

                if (name.isBlank()) {
                    errors.add("Línea $lineNumber: El Nombre del producto no puede estar vacío.")
                    continue
                }

                val costVal = costStr.toDoubleOrNull()
                if (costVal == null || costVal < 0.0) {
                    errors.add("Línea $lineNumber: El Costo de adquisición debe ser un número válido (recibido '$costStr').")
                    continue
                }

                val priceVal = priceStr.toDoubleOrNull() ?: costVal
                val stockVal = stockStr.toDoubleOrNull() ?: 0.0
                val unitsPerLotVal = (unitsPerLotStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(1.0)
                val directExpVal = directExpStr.toDoubleOrNull() ?: 0.0
                val purchaseModeVal = if (modeRaw.contains("LOTE")) "POR LOTE" else "POR UNIDAD"

                val totalUnitsCsv = if (purchaseModeVal == "POR LOTE") {
                    if (stockVal > 0.0) stockVal else unitsPerLotVal
                } else {
                    if (stockVal > 0.0) stockVal else 1.0
                }

                val unitAcquisitionCost = if (purchaseModeVal == "POR LOTE") {
                    if (unitsPerLotVal > 0.0) costVal / unitsPerLotVal else costVal
                } else {
                    costVal
                }

                val unitDirectExpenses = if (totalUnitsCsv > 0.0) directExpVal / totalUnitsCsv else 0.0

                try {
                    createMercaderiaProduct(
                        name = name,
                        code = codeRaw,
                        category = categoryRaw.ifBlank { "Bebidas" },
                        acquisitionCost = unitAcquisitionCost,
                        definitivePrice = priceVal,
                        unitOfMeasure = unitRaw.ifBlank { "Unidad" },
                        initialStock = stockVal,
                        responsibleAdmin = currentUserStr,
                        directExpenses = unitDirectExpenses,
                        purchaseMode = purchaseModeVal,
                        purchasePrice = costVal,
                        unitsPerLot = unitsPerLotVal
                    )
                    importedCount++
                } catch (e: Exception) {
                    errors.add("Línea $lineNumber: Error al guardar '$name': ${e.message}")
                }
            }

            onComplete(importedCount, errors)
        }
    }

    fun registerMercaderiaMovement(
        mercaderiaId: Long,
        type: String, // "ENTRADA", "SALIDA", "AJUSTE_POSITIVO", "AJUSTE_NEGATIVO", "AJUSTE"
        quantity: Double,
        responsibleAdmin: String,
        notes: String
    ) {
        viewModelScope.launch {
            val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId } ?: return@launch
            val product = _uiState.value.products.find { it.id == merc.productId }
            val productName = product?.name ?: "Mercadería #$mercaderiaId"

            val movement = MovimientoMercaderia(
                mercaderiaId = mercaderiaId,
                type = type,
                quantity = quantity,
                responsibleAdmin = responsibleAdmin.ifBlank { _uiState.value.currentUser?.username ?: "dueno" },
                notes = notes.trim(),
                date = System.currentTimeMillis()
            )
            repository.insertMovimientoMercaderia(movement)

            // Recalculate dynamic stock from single source of truth
            val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
            val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
            var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
            for (mov in currentMovements) {
                when (mov.type.uppercase()) {
                    "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                    "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> newCalculatedStock -= mov.quantity
                    "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                    "AJUSTE" -> newCalculatedStock += mov.quantity
                }
            }
            newCalculatedStock = maxOf(0.0, newCalculatedStock)

            // Sync Product.stock
            if (product != null) {
                repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
            }

            // Register Bitácora entry
            val sign = when (type.uppercase()) {
                "ENTRADA", "AJUSTE_POSITIVO", "INVENTARIO_INICIAL" -> "+"
                "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> "-"
                else -> ""
            }
            repository.insertBitacora(
                BitacoraEntry(
                    title = "Movimiento de Mercadería",
                    content = "$type en '$productName': $sign$quantity ${merc.unitOfMeasure} por $responsibleAdmin. Stock resultante: $newCalculatedStock. Nota: $notes",
                    category = "MERCADERÍAS",
                    authorUsername = responsibleAdmin.ifBlank { _uiState.value.currentUser?.username ?: "dueno" },
                    priority = "NORMAL"
                )
            )

            _uiState.update { it.copy(successMessage = "Movimiento de $quantity ${merc.unitOfMeasure} registrado exitosamente") }
        }
    }

    fun insertMovimientoMercaderia(mercaderiaId: Long, type: String, quantity: Double, responsibleAdmin: String, notes: String) {
        registerMercaderiaMovement(mercaderiaId, type, quantity, responsibleAdmin, notes)
    }

    // 1.5.3: Inventario y Movimientos de Materias Primas
    fun insertMovimientoMateriaPrima(
        materiaPrimaId: Long,
        type: String, // "ENTRADA", "SALIDA", "TANDA_CONSUMO"
        quantity: Double,
        notes: String,
        responsibleUser: String
    ) {
        viewModelScope.launch {
            val mpList = _uiState.value.materiasPrimas
            val raw = mpList.find { it.id == materiaPrimaId }
            if (raw == null) {
                _uiState.update { it.copy(errorMessage = "Materia prima no encontrada") }
                return@launch
            }

            // Realizar ajuste de stock
            val stockChange = if (type == "ENTRADA") quantity else -quantity
            val nextStock = raw.stock + stockChange
            val updatedMp = raw.copy(stock = nextStock)
            
            // Insertar movimiento
            val movimiento = MovimientoMateriaPrima(
                materiaPrimaId = materiaPrimaId,
                materiaPrimaName = raw.name,
                type = type,
                quantity = quantity,
                unit = raw.unit,
                responsibleUser = responsibleUser,
                notes = notes,
                resultingStock = nextStock
            )

            repository.updateMateriaPrima(updatedMp)
            repository.insertMovimientoMateriaPrima(movimiento)
            _uiState.update { it.copy(successMessage = "Inventario de materia prima actualizado correctamente") }
        }
    }

    // 1.5.3: Registrar Tanda de Producción con Consumo de Materias Primas
    fun registrarTanda(
        tanda: Tanda,
        consumos: List<Triple<Long, Double, String>> // Triple of (materiaPrimaId, quantityToDeductInInventoryUnit, originalQuantityWithUnitText)
    ) {
        viewModelScope.launch {
            try {
                // Validación estricta: NO SE PUEDE CREAR UNA NUEVA TANDA SI NO EXISTE UNA JORNADA ABIERTA
                val activeJornada = _uiState.value.activeJornada
                if (activeJornada == null || !activeJornada.isOpen) {
                    _uiState.update { 
                        it.copy(errorMessage = "No se puede registrar una nueva tanda sin una jornada abierta. Por favor, abra la jornada primero.") 
                    }
                    return@launch
                }

                // 1. Guardar la tanda en Room vinculada a la jornada abierta
                val tandaToInsert = if (tanda.jornadaId == 0L) {
                    tanda.copy(
                        jornada = "Jornada #${activeJornada.id}",
                        jornadaId = activeJornada.id,
                        status = "ABIERTA"
                    )
                } else {
                    tanda.copy(status = "ABIERTA")
                }
                
                val generatedId = repository.insertTanda(tandaToInsert)
                val finalTanda = tandaToInsert.copy(id = if (generatedId > 0) generatedId else tandaToInsert.id)

                // Actualización inmediata del estado UI para respuesta instantánea en Tandas
                _uiState.update { state ->
                    val updatedList = state.tandas.filter { it.uuid != finalTanda.uuid && it.id != finalTanda.id } + finalTanda
                    state.copy(
                        tandas = updatedList,
                        successMessage = "Tanda #${finalTanda.tandaNumber} registrada como ABIERTA",
                        errorMessage = null
                    )
                }

                // 2. Descontar stock de materias primas y registrar movimientos en inventario si existen
                // REGLA CRÍTICA TANDA 00: La Tanda 00 representa unidades de la jornada anterior y NUNCA descuenta insumos
                if (finalTanda.tandaNumber != "00") {
                    consumos.forEach { (mpId, qtyToDeduct, originalText) ->
                        val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                        if (raw != null) {
                            val nextStock = raw.stock - qtyToDeduct
                            val updatedMp = raw.copy(stock = nextStock)
                            repository.updateMateriaPrima(updatedMp)

                            // Registrar movimiento
                            val mov = MovimientoMateriaPrima(
                                materiaPrimaId = mpId,
                                materiaPrimaName = raw.name,
                                type = "TANDA_CONSUMO",
                                quantity = qtyToDeduct,
                                unit = raw.unit,
                                responsibleUser = finalTanda.responsibleUser,
                                notes = "Consumo para tanda ${finalTanda.uuid} (${originalText})",
                                resultingStock = nextStock
                            )
                            repository.insertMovimientoMateriaPrima(mov)
                        }
                    }
                }

                // 3. Registrar en Bitácora
                if (finalTanda.tandaNumber == "00") {
                    repository.insertBitacora(
                        BitacoraEntry(
                            title = "Registro de Tanda 00 (Unidades Pendientes)",
                            content = "Tanda 00 de ${finalTanda.productName} registrada en Jornada #${activeJornada.id} procedente de la jornada anterior. Unidades disponibles: ${finalTanda.estimatedYield.toInt()} ${finalTanda.productionUnit}. Equivalente de insumo base (informativo): ${finalTanda.baseQuantityUsed} ${finalTanda.baseQuantityUnit}. Sin consumo ni descuento de insumos de inventario.",
                            category = "PRODUCCIÓN",
                            authorUsername = finalTanda.responsibleUser
                        )
                    )
                } else {
                    repository.insertBitacora(
                        BitacoraEntry(
                            title = "Registro de Tanda #${finalTanda.tandaNumber} (ABIERTA)",
                            content = "Tanda de ${finalTanda.productName} registrada como ABIERTA con base de ${finalTanda.baseQuantityUsed} ${finalTanda.baseQuantityUnit}. Producción esperada: ${finalTanda.estimatedYield.toInt()} ${finalTanda.productionUnit}.",
                            category = "PRODUCCIÓN",
                            authorUsername = finalTanda.responsibleUser
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Error al registrar tanda: ${e.message}") }
            }
        }
    }

    // Convertir Unidades Pendientes de Jornada Anterior a Tanda 00
    fun convertirPendientesATanda00(
        tanda00: Tanda,
        previousTandasToMarkConverted: List<Tanda>
    ) {
        viewModelScope.launch {
            try {
                val activeJornada = _uiState.value.activeJornada
                if (activeJornada == null || !activeJornada.isOpen) {
                    _uiState.update { it.copy(errorMessage = "No se puede registrar la Tanda 00 sin una jornada abierta. Por favor, abra la jornada primero.") }
                    return@launch
                }

                // 1. Guardar la Tanda 00 en Room vinculada a la jornada abierta
                val tandaToInsert = tanda00.copy(
                    jornada = "Jornada #${activeJornada.id}",
                    jornadaId = activeJornada.id,
                    status = "ABIERTA",
                    tandaNumber = "00",
                    inventoryDeducted = true
                )
                val generatedId = repository.insertTanda(tandaToInsert)
                val finalTanda00 = tandaToInsert.copy(id = if (generatedId > 0) generatedId else tandaToInsert.id)

                // 2. Marcar las tandas de la jornada anterior como YA CONVERTIDAS en Room
                val updatedPreviousTandas = previousTandasToMarkConverted.map { oldTanda ->
                    val newObs = if (oldTanda.observation.contains("Pendientes:")) {
                        oldTanda.observation.replace("Pendientes:", "Pendientes_Convertidos:") + " [YA_CONVERTIDA_A_TANDA_00]"
                    } else if (!oldTanda.observation.contains("[YA_CONVERTIDA_A_TANDA_00]")) {
                        oldTanda.observation + " [YA_CONVERTIDA_A_TANDA_00]"
                    } else {
                        oldTanda.observation
                    }
                    val updated = oldTanda.copy(observation = newObs)
                    repository.updateTanda(updated)
                    updated
                }

                // 3. Actualización atómica inmediata del StateFlow para que la UI reaccione instantáneamente
                val updatedOldIds = updatedPreviousTandas.map { it.id }.filter { it > 0 }.toSet()
                val updatedOldUuids = updatedPreviousTandas.map { it.uuid }.filter { it.isNotBlank() }.toSet()

                _uiState.update { state ->
                    val currentTandas = state.tandas.map { existing ->
                        if (existing.id in updatedOldIds || (existing.uuid.isNotBlank() && existing.uuid in updatedOldUuids)) {
                            updatedPreviousTandas.find { it.id == existing.id || (existing.uuid.isNotBlank() && it.uuid == existing.uuid) } ?: existing
                        } else {
                            existing
                        }
                    }
                    val withTanda00 = currentTandas.filter { it.uuid != finalTanda00.uuid && it.id != finalTanda00.id } + finalTanda00
                    state.copy(
                        tandas = withTanda00,
                        successMessage = "Tanda 00 creada exitosamente para las unidades pendientes.",
                        errorMessage = null
                    )
                }

                // 4. Bitácora
                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Registro de Tanda 00 (Unidades Pendientes)",
                        content = "Tanda 00 de ${finalTanda00.productName} registrada en Jornada #${activeJornada.id} procedente de la jornada anterior. Unidades: ${finalTanda00.estimatedYield.toInt()} ${finalTanda00.productionUnit}. Insumo base equivalente (solo informativo): ${finalTanda00.baseQuantityUsed} ${finalTanda00.baseQuantityUnit}. Sin consumo ni descuento de inventario.",
                        category = "PRODUCCIÓN",
                        authorUsername = finalTanda00.responsibleUser
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Error al convertir unidades pendientes a Tanda 00: ${e.message}") }
            }
        }
    }

    // 1.5.8: Editar Tanda Activa
    fun editarTandaActiva(
        oldTanda: Tanda,
        newTanda: Tanda,
        oldConsumos: List<Triple<Long, Double, String>>,
        newConsumos: List<Triple<Long, Double, String>>
    ) {
        viewModelScope.launch {
            try {
                // 1. Revertir consumos anteriores
                oldConsumos.forEach { (mpId, oldQty, _) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val restoredStock = raw.stock + oldQty
                        val updatedMp = raw.copy(stock = restoredStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "AJUSTE_TANDA_REVERTIR",
                            quantity = oldQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Reversión por edición de tanda ${newTanda.uuid}",
                            resultingStock = restoredStock
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }

                // 2. Aplicar nuevos consumos
                newConsumos.forEach { (mpId, newQty, originalText) ->
                    val raw = _uiState.value.materiasPrimas.find { it.id == mpId }
                    if (raw != null) {
                        val nextStock = (raw.stock - newQty).coerceAtLeast(0.0)
                        val updatedMp = raw.copy(stock = nextStock)
                        repository.updateMateriaPrima(updatedMp)

                        val mov = MovimientoMateriaPrima(
                            materiaPrimaId = mpId,
                            materiaPrimaName = raw.name,
                            type = "TANDA_CONSUMO",
                            quantity = newQty,
                            unit = raw.unit,
                            responsibleUser = newTanda.responsibleUser,
                            notes = "Consumo actualizado tanda ${newTanda.uuid} (${originalText})",
                            resultingStock = nextStock
                        )
                        repository.insertMovimientoMateriaPrima(mov)
                    }
                }

                // 3. Actualizar tanda en BD
                repository.updateTanda(newTanda)

                // 4. Bitácora
                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Edición de Tanda #${newTanda.tandaNumber}",
                        content = "Tanda de ${newTanda.productName} editada. Base anterior: ${oldTanda.baseQuantityUsed} ${oldTanda.baseQuantityUnit} -> Nueva base: ${newTanda.baseQuantityUsed} ${newTanda.baseQuantityUnit}. Producción esperada ajustada a ${newTanda.estimatedYield.toInt()} ${newTanda.productionUnit}.",
                        category = "PRODUCCIÓN",
                        authorUsername = newTanda.responsibleUser
                    )
                )

                _uiState.update { it.copy(successMessage = "Tanda #${newTanda.tandaNumber} actualizada y consumos recalculados correctamente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al editar la tanda: ${e.message}") }
            }
        }
    }

    // 1.5.8: Ajustar Tanda Activa
    fun importarTandasDesdeCocina(
        tandas: List<Tanda>,
        onSuccess: (Int) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                val activeJornada = _uiState.value.activeJornada
                tandas.forEach { rawTanda ->
                    val existing = _uiState.value.tandas.find { it.uuid == rawTanda.uuid }
                    if (existing == null) {
                        val tandaToInsert = if (rawTanda.jornadaId == 0L && activeJornada != null) {
                            rawTanda.copy(
                                jornada = "Jornada #${activeJornada.id}",
                                jornadaId = activeJornada.id,
                                inventoryDeducted = true
                            )
                        } else rawTanda.copy(inventoryDeducted = true)

                        repository.insertTanda(tandaToInsert)
                        importedCount++
                    }
                }

                if (importedCount > 0) {
                    repository.insertBitacora(
                        BitacoraEntry(
                            title = "Importación de Tandas desde Cocina",
                            content = "Se importaron $importedCount tandas recibidas por SMS desde Cocina sin duplicar inventario.",
                            category = "PRODUCCIÓN",
                            authorUsername = _uiState.value.currentUser?.username ?: "Dueño"
                        )
                    )
                    _uiState.update {
                        it.copy(successMessage = "Se importaron $importedCount tandas desde Cocina correctamente")
                    }
                    onSuccess(importedCount)
                } else {
                    onSuccess(0)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al importar tandas: ${e.message}") }
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    fun ajustarTanda(tanda: Tanda) {
        viewModelScope.launch {
            try {
                repository.updateTanda(tanda)
                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Ajuste de Producción Tanda #${tanda.tandaNumber}",
                        content = "Cantidad obtenida real de ${tanda.productName} ajustada a ${tanda.actualYield} ${tanda.productionUnit}. Nota: ${tanda.observation}",
                        category = "PRODUCCIÓN",
                        authorUsername = _uiState.value.currentUser?.username ?: tanda.responsibleUser
                    )
                )
                _uiState.update { it.copy(successMessage = "Tanda #${tanda.tandaNumber} ajustada correctamente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al ajustar la tanda: ${e.message}") }
            }
        }
    }

    fun ajustarTanda(tandaId: Long, newActualYield: Double, observation: String = "") {
        viewModelScope.launch {
            try {
                val tanda = _uiState.value.tandas.find { it.id == tandaId } ?: return@launch
                val realUnitCost = if (newActualYield > 0.0) tanda.totalBatchCost / newActualYield else 0.0
                val presEquiv = if (tanda.specialPresentationEquivalence > 0.0) tanda.specialPresentationEquivalence else 1.0
                val specialUnitsEq = if (tanda.specialPresentationQty > 0.0) tanda.specialPresentationQty * presEquiv else 0.0
                val totalYieldUnits = newActualYield + specialUnitsEq
                val expectedVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val yieldPct = if (expectedVal > 0.0) (totalYieldUnits / expectedVal) * 100.0 else 100.0
                val updated = tanda.copy(
                    actualYield = newActualYield,
                    realUnitCost = realUnitCost,
                    yieldPercentage = yieldPct,
                    observation = if (observation.isNotBlank()) "${tanda.observation} | Ajuste: $observation".trimStart(' ', '|') else tanda.observation
                )
                repository.updateTanda(updated)
                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Ajuste de Producción Tanda #${tanda.tandaNumber}",
                        content = "Cantidad obtenida real de ${tanda.productName} ajustada a $newActualYield ${tanda.productionUnit}. Nota: $observation",
                        category = "PRODUCCIÓN",
                        authorUsername = _uiState.value.currentUser?.username ?: tanda.responsibleUser
                    )
                )
                _uiState.update { it.copy(successMessage = "Tanda #${tanda.tandaNumber} ajustada correctamente a $newActualYield ${tanda.productionUnit}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al ajustar la tanda: ${e.message}") }
            }
        }
    }

    // 1.5.8: Cerrar Tanda
    fun cerrarTanda(tanda: Tanda) {
        viewModelScope.launch {
            try {
                repository.updateTanda(tanda)

                _uiState.update { state ->
                    val updatedTandas = state.tandas.map {
                        if (it.id == tanda.id || (tanda.uuid.isNotBlank() && it.uuid == tanda.uuid)) tanda else it
                    }
                    state.copy(
                        tandas = updatedTandas,
                        successMessage = "Tanda #${tanda.tandaNumber} cerrada con éxito",
                        errorMessage = null
                    )
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Cierre de Tanda #${tanda.tandaNumber}",
                        content = "Tanda de ${tanda.productName} CERRADA. Producción Esperada: ${tanda.expectedYield.toInt()} ${tanda.productionUnit}, Producción Real: ${tanda.actualYield.toInt()} ${tanda.productionUnit}. Rendimiento Real: ${"%.1f".format(tanda.yieldPercentage)}%. Ganancia Real: $${"%.2f".format(tanda.estimatedProfit)} CUP.",
                        category = "PRODUCCIÓN",
                        authorUsername = tanda.responsibleUser
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al cerrar la tanda: ${e.message}") }
            }
        }
    }

    fun archivarTandasConUnidadesPendientes(updatedTandas: List<Tanda>, summaryBitacora: String = "") {
        viewModelScope.launch {
            try {
                updatedTandas.forEach { tanda ->
                    repository.updateTanda(tanda)
                }
                val updatedIds = updatedTandas.map { it.id }.toSet()
                val updatedUuids = updatedTandas.map { it.uuid }.filter { it.isNotBlank() }.toSet()
                _uiState.update { state ->
                    val newList = state.tandas.map { existing ->
                        if (existing.id in updatedIds || existing.uuid in updatedUuids) {
                            updatedTandas.find { it.id == existing.id || (existing.uuid.isNotBlank() && it.uuid == existing.uuid) } ?: existing
                        } else existing
                    }
                    state.copy(
                        tandas = newList,
                        successMessage = "Tandas archivadas correctamente con el registro de unidades pendientes."
                    )
                }
                if (summaryBitacora.isNotBlank()) {
                    repository.insertBitacora(
                        BitacoraEntry(
                            title = "Archivado de Tandas y Unidades Pendientes",
                            content = summaryBitacora,
                            category = "PRODUCCIÓN",
                            authorUsername = _uiState.value.currentUser?.username ?: "admin"
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al archivar tandas: ${e.message}") }
            }
        }
    }

    fun eliminarJornadaArchivadaDeTandas(jornadaId: Long, tandasList: List<Tanda>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val tandaIds = tandasList.map { it.id }.filter { it > 0 }.toSet()
                val tandaUuids = tandasList.map { it.uuid }.filter { it.isNotBlank() }.toSet()

                repository.deleteTandas(tandasList)
                if (jornadaId > 0) {
                    repository.deleteJornadaById(jornadaId)
                }

                _uiState.update { current ->
                    val newTandas = current.tandas.filterNot { it.id in tandaIds || (it.uuid.isNotBlank() && it.uuid in tandaUuids) }
                    val newJornadas = if (jornadaId > 0) current.allJornadas.filterNot { it.id == jornadaId } else current.allJornadas
                    current.copy(
                        tandas = newTandas,
                        allJornadas = newJornadas,
                        successMessage = if (jornadaId > 0) "Jornada #$jornadaId y sus tandas fueron eliminadas del archivo." else "Tandas seleccionadas eliminadas del archivo."
                    )
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar la jornada del archivo: ${e.message}") }
            }
        }
    }

    fun limpiarTodoElArchivoDeTandas(archivedJornadasConTandas: List<Pair<Jornada, List<Tanda>>>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val allTandasToDelete = archivedJornadasConTandas.flatMap { it.second }
                val tandaIds = allTandasToDelete.map { it.id }.filter { it > 0 }.toSet()
                val tandaUuids = allTandasToDelete.map { it.uuid }.filter { it.isNotBlank() }.toSet()

                val jornadaIdsToDelete = archivedJornadasConTandas.map { it.first.id }.filter { it > 0 }.toSet()

                repository.deleteTandas(allTandasToDelete)
                jornadaIdsToDelete.forEach { jId ->
                    repository.deleteJornadaById(jId)
                }

                _uiState.update { current ->
                    val newTandas = current.tandas.filterNot { it.id in tandaIds || (it.uuid.isNotBlank() && it.uuid in tandaUuids) }
                    val newJornadas = current.allJornadas.filterNot { it.id in jornadaIdsToDelete }
                    current.copy(
                        tandas = newTandas,
                        allJornadas = newJornadas,
                        successMessage = "Todo el archivo de tandas ha sido eliminado correctamente."
                    )
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al limpiar el archivo de tandas: ${e.message}") }
            }
        }
    }

    // FASE 4: Producción - Registrar / Actualizar Producción Real y Venta Real de la Tanda
    fun actualizarProduccionRealYVentaTanda(
        tandaId: Long,
        actualYield: Double,
        quantitySold: Double,
        observation: String
    ) {
        viewModelScope.launch {
            try {
                val tanda = _uiState.value.tandas.find { it.id == tandaId }
                if (tanda == null) {
                    _uiState.update { it.copy(errorMessage = "Tanda no encontrada") }
                    return@launch
                }

                val product = _uiState.value.products.find { it.id == tanda.productId }
                val salePrice = if (product != null && product.price > 0.0) product.price else tanda.salePrice
                val presEquiv = if (tanda.specialPresentationEquivalence > 0.0) tanda.specialPresentationEquivalence else {
                    product?.let { parsePresentacionesEspeciales(it.presentacionesEspeciales).find { p -> p.name.equals(tanda.specialPresentationName, true) }?.baseEquivalence } ?: 1.0
                }
                val specialUnitsEq = if (tanda.specialPresentationQty > 0.0) tanda.specialPresentationQty * presEquiv else 0.0
                val totalYieldUnits = actualYield + specialUnitsEq
                val expectedYieldVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                val yieldPct = if (expectedYieldVal > 0.0) (totalYieldUnits / expectedYieldVal) * 100.0 else 100.0
                val realRevenue = quantitySold * salePrice
                val realUnitCost = if (actualYield > 0.0) tanda.totalBatchCost / actualYield else 0.0
                val realProfit = realRevenue - tanda.totalBatchCost
                val realProfitMargin = if (realRevenue > 0.0) (realProfit / realRevenue) * 100.0 else 0.0

                val updated = tanda.copy(
                    actualYield = actualYield,
                    quantitySold = quantitySold,
                    salePrice = salePrice,
                    realRevenue = realRevenue,
                    yieldPercentage = yieldPct,
                    realUnitCost = realUnitCost,
                    estimatedProfit = realProfit,
                    profitMargin = realProfitMargin,
                    observation = observation.trim()
                )

                repository.updateTanda(updated)

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Registro Real Producción/Venta Tanda #${tanda.tandaNumber}",
                        content = "Tanda de ${tanda.productName}: Producido Real: $actualYield ${tanda.productionUnit}, Vendido Real: $quantitySold ${tanda.productionUnit}, Restante: ${actualYield - quantitySold}. Ingreso: $$realRevenue CUP. Costo: $${tanda.totalBatchCost} CUP.",
                        category = "PRODUCCIÓN",
                        authorUsername = _uiState.value.currentUser?.username ?: tanda.responsibleUser
                    )
                )

                _uiState.update { it.copy(successMessage = "Producción real y venta de Tanda #${tanda.tandaNumber} registradas con éxito") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar tanda: ${e.message}") }
            }
        }
    }

    // FASE 4: Mercaderías - Registrar Salida con Venta Real
    fun registrarSalidaMercaderiaConVenta(
        mercaderiaId: Long,
        quantitySalida: Double,
        quantitySold: Double,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId }
                if (merc == null) {
                    _uiState.update { it.copy(errorMessage = "Mercadería no encontrada") }
                    return@launch
                }

                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val salePrice = product?.price ?: 0.0
                val acquisitionCost = merc.acquisitionCost
                val validQtySold = quantitySold.coerceIn(0.0, quantitySalida)
                val realRevenue = validQtySold * salePrice
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "SALIDA",
                    quantity = quantitySalida,
                    quantitySold = validQtySold,
                    salePrice = salePrice,
                    acquisitionCost = acquisitionCost,
                    realRevenue = realRevenue,
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = notes.trim(),
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Actualizar stock del producto
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Salida y Venta Real de Mercadería",
                        content = "Salida de $quantitySalida ${merc.unitOfMeasure} de '$productName'. Vendido Real: $validQtySold (Restante de salida: ${quantitySalida - validQtySold}). Ingreso: $$realRevenue CUP. Costo de salida: $${validQtySold * acquisitionCost} CUP.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "Salida y venta real registradas correctamente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar salida y venta: ${e.message}") }
            }
        }
    }

    // FASE 4: Mercaderías - Actualizar Venta Real en Salida Existente
    fun actualizarVentaRealSalidaMercaderia(
        movimientoId: Long,
        quantitySold: Double,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val mov = _uiState.value.movimientosMercaderia.find { it.id == movimientoId }
                if (mov == null) {
                    _uiState.update { it.copy(errorMessage = "Movimiento no encontrado") }
                    return@launch
                }

                val validQtySold = quantitySold.coerceIn(0.0, mov.quantity)
                val realRevenue = validQtySold * mov.salePrice

                val updatedMov = mov.copy(
                    quantitySold = validQtySold,
                    realRevenue = realRevenue,
                    notes = notes.trim()
                )

                repository.updateMovimientoMercaderia(updatedMov)

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Actualización Venta Real Mercadería",
                        content = "Movimiento #${mov.id} (Salida: ${mov.quantity}): Vendido Real actualizado a $validQtySold. Ingreso: $$realRevenue CUP.",
                        category = "MERCADERÍAS",
                        authorUsername = _uiState.value.currentUser?.username ?: mov.responsibleAdmin
                    )
                )

                _uiState.update { it.copy(successMessage = "Venta real del movimiento actualizada con éxito") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar venta real: ${e.message}") }
            }
        }
    }

    // REGISTRO DE VENTAS EXCLUSIVAS DE MERCADERÍA AL CLIENTE
    fun registrarVentaMercaderia(
        mercaderiaId: Long,
        quantitySold: Double,
        salePrice: Double,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId }
                if (merc == null) {
                    _uiState.update { it.copy(errorMessage = "Mercadería no encontrada") }
                    return@launch
                }

                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val finalSalePrice = if (salePrice > 0.0) salePrice else (product?.price ?: 0.0)
                val acquisitionCost = merc.acquisitionCost
                val realRevenue = quantitySold * finalSalePrice
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "VENTA",
                    quantity = quantitySold, // La venta disminuye la existencia física
                    quantitySold = quantitySold,
                    salePrice = finalSalePrice,
                    acquisitionCost = acquisitionCost,
                    realRevenue = realRevenue, // Genera ingreso económico: CANTIDAD VENDIDA × PRECIO DE VENTA
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = notes.trim().ifBlank { "Venta registrada por Dueño" },
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Recalcular stock dinámico: EXISTENCIA = EXISTENCIA ANTERIOR - VENTA
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Venta Registrada de Mercadería",
                        content = "Venta de $quantitySold ${merc.unitOfMeasure} de '$productName'. Ingreso: $${"%.2f".format(realRevenue)} CUP ($${"%.2f".format(finalSalePrice)} c/u). Costo: $${"%.2f".format(quantitySold * acquisitionCost)} CUP. Existencia final: $newCalculatedStock.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "Venta de $quantitySold ${merc.unitOfMeasure} registrada exitosamente ($${"%.2f".format(realRevenue)} CUP)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar venta: ${e.message}") }
            }
        }
    }

    // REGISTRO DE ENTRADA FÍSICA DE MERCADERÍA (AUMENTA EXISTENCIA)
    fun registrarEntradaMercaderia(
        mercaderiaId: Long,
        quantity: Double,
        costoUnitario: Double? = null,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId } ?: return@launch
                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada
                val unitCost = if (costoUnitario != null && costoUnitario > 0.0) costoUnitario else merc.acquisitionCost

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "ENTRADA",
                    quantity = quantity,
                    quantitySold = 0.0,
                    salePrice = 0.0,
                    acquisitionCost = unitCost,
                    realRevenue = 0.0,
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = notes.trim().ifBlank { "Entrada registrada por Dueño" },
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Recalcular stock dinámico: EXISTENCIA = EXISTENCIA ANTERIOR + ENTRADA
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Entrada Física de Mercadería",
                        content = "Entrada de $quantity ${merc.unitOfMeasure} de '$productName'. Costo unitario: $${"%.2f".format(unitCost)} CUP. Existencia resultante: $newCalculatedStock.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "Entrada de $quantity ${merc.unitOfMeasure} registrada (+ existencia)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar entrada: ${e.message}") }
            }
        }
    }

    // REGISTRO DE SALIDA FÍSICA DE MERCADERÍA (NO CONSTITUYE VENTA NI GENERA INGRESO)
    fun registrarSalidaMercaderiaFisica(
        mercaderiaId: Long,
        quantity: Double,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId } ?: return@launch
                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "SALIDA",
                    quantity = quantity,
                    quantitySold = 0.0, // Salida física pura, NO constituye venta
                    salePrice = 0.0, // NO genera ingresos
                    acquisitionCost = merc.acquisitionCost,
                    realRevenue = 0.0, // Ingreso = 0
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = notes.trim().ifBlank { "Salida física (deterioro/pérdida/consumo)" },
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Recalcular stock dinámico: EXISTENCIA = EXISTENCIA ANTERIOR - SALIDA
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Salida Física de Mercadería (Sin Ingreso)",
                        content = "Salida física de $quantity ${merc.unitOfMeasure} de '$productName'. Motivo: $notes. Existencia resultante: $newCalculatedStock.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "Salida de $quantity ${merc.unitOfMeasure} registrada (- existencia, sin ingreso)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar salida física: ${e.message}") }
            }
        }
    }

    // REGISTRO DE MERMA DE MERCADERÍA EN ALMACÉN (Defectuoso, Consumo, Regalía)
    fun registrarMermaMercaderia(
        mercaderiaId: Long,
        quantity: Double,
        motivo: String, // "Defectuoso", "Consumo", "Regalía"
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId } ?: return@launch
                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada
                val detailNotes = if (notes.isNotBlank()) "Merma [$motivo]: ${notes.trim()}" else "Merma: $motivo"

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "MERMA",
                    quantity = quantity,
                    quantitySold = 0.0,
                    salePrice = 0.0,
                    acquisitionCost = merc.acquisitionCost,
                    realRevenue = 0.0,
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = detailNotes,
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Recalcular stock de almacén
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA", "PARA_VENTA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Merma de Mercadería en Almacén",
                        content = "Merma de $quantity ${merc.unitOfMeasure} de '$productName'. Concepto: $motivo. Existencia resultante en Almacén: $newCalculatedStock.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "Merma de $quantity ${merc.unitOfMeasure} registrada (- almacén)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar merma: ${e.message}") }
            }
        }
    }

    // REGISTRO DE SALIDA 'PARA VENTA' (ALMACÉN -> LOCAL DE VENTAS)
    // NO representa una venta ni modifica automáticamente ventas de Caja
    fun registrarParaVentaMercaderia(
        mercaderiaId: Long,
        quantity: Double,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                val merc = _uiState.value.mercaderias.find { it.id == mercaderiaId } ?: return@launch
                val product = _uiState.value.products.find { it.id == merc.productId }
                val productName = product?.name ?: "Mercadería #$mercaderiaId"
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val activeJornada = _uiState.value.activeJornada
                val detailNotes = if (notes.isNotBlank()) "Para Venta: ${notes.trim()}" else "Salida Almacén → Local de Ventas"

                val movement = MovimientoMercaderia(
                    mercaderiaId = mercaderiaId,
                    type = "PARA_VENTA",
                    quantity = quantity,
                    quantitySold = 0.0, // Salida a local de ventas, NO es venta directa de caja
                    salePrice = 0.0,
                    acquisitionCost = merc.acquisitionCost,
                    realRevenue = 0.0,
                    jornadaId = activeJornada?.id ?: 0L,
                    responsibleAdmin = username,
                    notes = detailNotes,
                    date = System.currentTimeMillis()
                )

                repository.insertMovimientoMercaderia(movement)

                // Recalcular stock de almacén: disminuye Almacén
                val currentMovements = _uiState.value.movimientosMercaderia.filter { it.mercaderiaId == mercaderiaId } + movement
                val hasInitialMov = currentMovements.any { it.type == "INVENTARIO_INICIAL" }
                var newCalculatedStock = if (hasInitialMov) 0.0 else merc.initialStock
                for (mov in currentMovements) {
                    when (mov.type.uppercase()) {
                        "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> newCalculatedStock += mov.quantity
                        "SALIDA", "AJUSTE_NEGATIVO", "MERMA", "PARA_VENTA" -> newCalculatedStock -= mov.quantity
                        "VENTA" -> newCalculatedStock -= (if (mov.quantity > 0) mov.quantity else mov.quantitySold)
                        "AJUSTE" -> newCalculatedStock += mov.quantity
                    }
                }
                newCalculatedStock = maxOf(0.0, newCalculatedStock)

                if (product != null) {
                    repository.updateProduct(product.copy(stock = newCalculatedStock.toInt()))
                }

                repository.insertBitacora(
                    BitacoraEntry(
                        title = "Salida Para Venta (Almacén → Local de Ventas)",
                        content = "Traslado de $quantity ${merc.unitOfMeasure} de '$productName' hacia el Local de Ventas. Existencia restante en Almacén: $newCalculatedStock.",
                        category = "MERCADERÍAS",
                        authorUsername = username
                    )
                )

                _uiState.update { it.copy(successMessage = "$quantity ${merc.unitOfMeasure} enviadas a Local de Ventas (- almacén)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar Para Venta: ${e.message}") }
            }
        }
    }

    fun actualizarVentaMercaderia(
        movimientoId: Long,
        quantitySold: Double,
        notes: String
    ) {
        viewModelScope.launch {
            try {
                val mov = _uiState.value.movimientosMercaderia.find { it.id == movimientoId } ?: return@launch
                val merc = _uiState.value.mercaderias.find { it.id == mov.mercaderiaId }
                val prod = _uiState.value.products.find { it.id == merc?.productId }
                val price = prod?.price ?: mov.salePrice
                val realRev = quantitySold * price

                val updatedMov = mov.copy(
                    quantitySold = quantitySold,
                    realRevenue = realRev,
                    notes = notes.trim()
                )
                repository.updateMovimientoMercaderia(updatedMov)
                _uiState.update { it.copy(successMessage = "Venta actualizada correctamente") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al actualizar venta: ${e.message}") }
            }
        }
    }

    fun registrarCobroComanda(order: TableOrder, items: List<OrderItem>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.registrarCobroComanda(order, items)
                _uiState.update { it.copy(successMessage = "Comanda #${order.comandaNumber} cobrada y registrada exitosamente.") }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al registrar cobro: ${e.message}") }
            }
        }
    }

    fun closeAndPayOrderDirect(order: TableOrder, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.closeAndPayOrderDirect(order)
                _uiState.update { it.copy(successMessage = "Comanda #${order.comandaNumber} (Mesa #${order.tableNumber}) cobrada y cerrada exitosamente.") }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al cobrar mesa #${order.tableNumber}: ${e.message}") }
            }
        }
    }

    fun closeAndPayOrdersBatch(orders: List<TableOrder>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.closeAndPayOrdersBatch(orders)
                val count = orders.size
                _uiState.update {
                    it.copy(successMessage = if (count == 1) "Comanda #${orders.first().comandaNumber} cobrada exitosamente." else "$count comandas cobradas exitosamente.")
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al procesar cobro: ${e.message}") }
            }
        }
    }

    fun crearComandaCaja(
        tableNumber: Int = 0,
        customerName: String = "",
        cartItems: List<SalonCartItem>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (cartItems.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "La selección no tiene productos.") }
                return@launch
            }

            val username = _uiState.value.currentUser?.username ?: "cajero"
            var activeJornada = _uiState.value.activeJornada
            if (activeJornada == null || !activeJornada.isOpen) {
                val dbJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                if (dbJornada != null && dbJornada.isOpen) {
                    activeJornada = dbJornada
                    _uiState.update { it.copy(activeJornada = dbJornada) }
                } else {
                    val newJornadaId = withContext(Dispatchers.IO) {
                        repository.openJornada(0.0, username)
                    }
                    val openedJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                    activeJornada = openedJornada ?: Jornada(id = newJornadaId, openedAt = System.currentTimeMillis(), openedBy = username, isOpen = true)
                    _uiState.update { it.copy(activeJornada = activeJornada) }
                }
            }

            val activeJornadaId = activeJornada?.id ?: 1L
            val maxInDb = repository.getMaxComandaNumberForJornada(activeJornadaId)
            val maxInState = _uiState.value.allOrders
                .filter { it.jornadaId == activeJornadaId }
                .maxOfOrNull { it.comandaNumber } ?: 0
            val nextComandaNumber = maxOf(maxInDb, maxInState) + 1

            val totalCocina = cartItems
                .filter { it.product.destination == "COCINA" }
                .sumOf { it.totalItemPrice }
            val totalBarra = cartItems
                .filter { it.product.destination == "BARRA" }
                .sumOf { it.totalItemPrice }
            val totalAmount = cartItems.sumOf { it.totalItemPrice }

            val now = System.currentTimeMillis()
            val newOrder = TableOrder(
                tableNumber = tableNumber,
                customerName = if (customerName.isNotBlank()) customerName else if (tableNumber > 0) "Mesa $tableNumber" else "Comanda Caja #$nextComandaNumber",
                waiterUsername = username,
                createdAt = now,
                confirmedAt = now,
                status = "PENDIENTE_SERVIR",
                totalAmount = totalAmount,
                totalCocina = totalCocina,
                totalBarra = totalBarra,
                jornadaId = activeJornadaId,
                comandaNumber = nextComandaNumber
            )

            val orderItems = cartItems.map { cartItem ->
                OrderItem(
                    orderId = 0L,
                    productId = cartItem.product.id,
                    productName = cartItem.product.name,
                    unitPrice = cartItem.unitPriceWithAgregados,
                    quantity = cartItem.quantity,
                    destination = cartItem.product.destination,
                    notes = cartItem.agregadosNoteString,
                    status = "PENDIENTE",
                    productCode = cartItem.product.code
                )
            }

            try {
                repository.confirmComandaWithItems(newOrder, orderItems)
                _uiState.update {
                    it.copy(
                        successMessage = "Comanda #$nextComandaNumber creada exitosamente."
                    )
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al crear comanda: ${e.localizedMessage}") }
            }
        }
    }

    fun addConsumoPersonalItem(productId: Long, productName: String, unitPrice: Double, quantity: Int) {
        val jornada = _uiState.value.activeJornada ?: return
        val user = _uiState.value.currentUser?.username ?: "cajero"
        if (quantity <= 0) return
        viewModelScope.launch {
            val item = ConsumoPersonalItem(
                jornadaId = jornada.id,
                productId = productId,
                productName = productName,
                unitPrice = unitPrice,
                quantity = quantity,
                totalAmount = unitPrice * quantity,
                recordedBy = user
            )
            repository.insertConsumoPersonal(item)
        }
    }

    fun deleteConsumoPersonalItem(id: Long) {
        viewModelScope.launch {
            repository.deleteConsumoPersonal(id)
        }
    }

    fun clearConsumoPersonalForJornada(jornadaId: Long) {
        viewModelScope.launch {
            repository.clearConsumoPersonalForJornada(jornadaId)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun loadSalonTableCountForUser(username: String?) {
        val userKey = username ?: _uiState.value.currentUser?.username ?: "default"
        val count = sharedPreferences.getInt("salon_tables_count_$userKey", sharedPreferences.getInt("salon_tables_count_global", 12))
        _uiState.update { it.copy(salonTableCount = count) }
    }

    fun getSalonTableCount(): Int {
        val userKey = _uiState.value.currentUser?.username ?: "default"
        return sharedPreferences.getInt("salon_tables_count_global", sharedPreferences.getInt("salon_tables_count_$userKey", _uiState.value.salonTableCount))
    }

    fun updateSalonTableCount(count: Int) {
        val userKey = _uiState.value.currentUser?.username ?: "default"
        val validCount = count.coerceIn(1, 100)
        sharedPreferences.edit()
            .putInt("salon_tables_count_global", validCount)
            .putInt("salon_tables_count_$userKey", validCount)
            .apply()
        _uiState.update { it.copy(salonTableCount = validCount, successMessage = "Cantidad de mesas actualizada a $validCount.") }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentProducts = repository.allProducts.first()
            _uiState.update {
                it.copy(
                    products = currentProducts,
                    isLoading = false,
                    successMessage = "Catálogo de salón actualizado correctamente."
                )
            }
        }
    }

    private fun convertOrderItemToSmsItem(
        item: OrderItem,
        catalogProducts: List<Product>
    ): com.example.util.ComandaItemForSms {
        val p = catalogProducts.firstOrNull { it.id == item.productId || (item.productCode.isNotBlank() && it.code.trim().equals(item.productCode.trim(), ignoreCase = true)) }
        val code = if (item.productCode.isNotBlank()) {
            item.productCode
        } else if (p != null && p.code.isNotBlank()) {
            p.code
        } else if (p != null) {
            com.example.util.ProductCodeHelper.generateNextProductCode(p.destination, p.category, catalogProducts)
        } else {
            "PF-C-0001"
        }

        val agregadosIds = mutableListOf<String>()
        if (p != null && item.notes.isNotBlank()) {
            val availableAgregados = com.example.ui.components.parseProductAgregados(p.agregadosList)
            availableAgregados.forEachIndexed { idx, ag ->
                if (item.notes.contains(ag.name.trim(), ignoreCase = true)) {
                    val formattedId = if (ag.id.isNotBlank()) ag.id else String.format("%02d", idx + 1)
                    agregadosIds.add(formattedId)
                }
            }
        }

        return com.example.util.ComandaItemForSms(
            productCode = code,
            quantity = item.quantity,
            agregadosIds = agregadosIds
        )
    }

    fun confirmarComandaSalon(
        tableNumber: Int?,
        cartItems: List<SalonCartItem>,
        existingOrder: TableOrder? = null,
        context: Context? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (cartItems.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "La comanda no tiene productos seleccionados.") }
                return@launch
            }

            val username = _uiState.value.currentUser?.username ?: "salon"
            var activeJornada = _uiState.value.activeJornada
            if (activeJornada == null || !activeJornada.isOpen) {
                val dbJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                if (dbJornada != null && dbJornada.isOpen) {
                    activeJornada = dbJornada
                    _uiState.update { it.copy(activeJornada = dbJornada) }
                } else {
                    val newJornadaId = withContext(Dispatchers.IO) {
                        repository.openJornada(0.0, username)
                    }
                    val openedJornada = withContext(Dispatchers.IO) { repository.getActiveJornadaSync() }
                    activeJornada = openedJornada ?: Jornada(id = newJornadaId, openedAt = System.currentTimeMillis(), openedBy = username, isOpen = true)
                    _uiState.update { it.copy(activeJornada = activeJornada) }
                }
            }

            val activeJornadaId = activeJornada?.id ?: 1L
            val catalogProducts = _uiState.value.products.ifEmpty {
                withContext(Dispatchers.IO) { repository.getDatabase().productDao().getAllProductsSync() }
            }

            if (existingOrder != null) {
                // SUMAR A COMANDA EXISTENTE
                val targetComandaNumber = existingOrder.comandaNumber
                val targetOrderId = existingOrder.id

                val addCocina = cartItems.filter { com.example.util.ProductCodeHelper.isCocina(it.product.destination, it.product.category, it.product.code) }.sumOf { it.totalItemPrice }
                val addBarra = cartItems.filter { !com.example.util.ProductCodeHelper.isCocina(it.product.destination, it.product.category, it.product.code) }.sumOf { it.totalItemPrice }
                val addTotal = cartItems.sumOf { it.totalItemPrice }

                val newOrderItems = cartItems.map { cartItem ->
                    val p = catalogProducts.firstOrNull { it.id == cartItem.product.id } ?: cartItem.product
                    val prodCode = if (p.code.isNotBlank()) p.code else if (cartItem.product.code.isNotBlank()) cartItem.product.code else com.example.util.ProductCodeHelper.generateNextProductCode(p.destination, p.category, catalogProducts)
                    OrderItem(
                        orderId = targetOrderId,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        unitPrice = cartItem.unitPriceWithAgregados,
                        quantity = cartItem.quantity,
                        destination = cartItem.product.destination,
                        notes = cartItem.agregadosNoteString,
                        status = "PENDIENTE",
                        productCode = prodCode
                    )
                }

                try {
                    repository.addItemsToComanda(
                        orderId = targetOrderId,
                        newItems = newOrderItems,
                        updatedTotalAmount = existingOrder.totalAmount + addTotal,
                        updatedTotalCocina = existingOrder.totalCocina + addCocina,
                        updatedTotalBarra = existingOrder.totalBarra + addBarra
                    )

                    // Retrieve ALL items for targetOrderId to build full updated SMS
                    val allDbItems = withContext(Dispatchers.IO) {
                        repository.getDatabase().tableOrderDao().getItemsForOrderSync(targetOrderId)
                    }

                    // Send SMS automatically in background to Cajero
                    context?.let { ctx ->
                        val usersList = _uiState.value.users.ifEmpty {
                            withContext(Dispatchers.IO) { repository.getDatabase().userDao().getAllUsersSync() }
                        }
                        val cajeroInfo = com.example.util.CajaPhoneHelper.resolveCajeroInfo(
                            usersList,
                            _uiState.value.activeJornada,
                            _uiState.value.generalConfig
                        )
                        if (cajeroInfo.isConfigured) {
                            val itemsForSms = allDbItems.map { dbItem ->
                                convertOrderItemToSmsItem(dbItem, catalogProducts)
                            }
                            val smsText = com.example.util.SmsComandaHelper.encodeComandaSms(targetComandaNumber, itemsForSms)
                            val sendResult = com.example.util.SmsComandaHelper.sendComandaSms(ctx, cajeroInfo.phoneNumber, smsText)

                            if (sendResult.success) {
                                _uiState.update {
                                    it.copy(
                                        successMessage = "SMS enviado al Cajero correctamente.",
                                        errorMessage = null
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        successMessage = "Comanda #$targetComandaNumber actualizada en Salón.",
                                        errorMessage = "No se pudo enviar el SMS al Cajero (${sendResult.errorReason ?: "Error de red"}). Puede reintentar con 'Reenviar SMS'."
                                    )
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    successMessage = "Comanda #$targetComandaNumber actualizada en Salón.",
                                    errorMessage = "No se pudo enviar el SMS al Cajero (${cajeroInfo.statusDescription})."
                                )
                            }
                        }
                    } ?: run {
                        _uiState.update {
                            it.copy(
                                successMessage = "Comanda #$targetComandaNumber actualizada en Salón.",
                                errorMessage = null
                            )
                        }
                    }
                    onComplete()
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Error al actualizar comanda: ${e.localizedMessage}") }
                }
            } else {
                // COMANDA NUEVA
                val maxInDb = repository.getMaxComandaNumberForJornada(activeJornadaId)
                val maxInState = _uiState.value.allOrders
                    .filter { it.jornadaId == activeJornadaId }
                    .maxOfOrNull { it.comandaNumber } ?: 0
                val nextComandaNumber = maxOf(maxInDb, maxInState) + 1

                val totalCocina = cartItems.filter { com.example.util.ProductCodeHelper.isCocina(it.product.destination, it.product.category, it.product.code) }.sumOf { it.totalItemPrice }
                val totalBarra = cartItems.filter { !com.example.util.ProductCodeHelper.isCocina(it.product.destination, it.product.category, it.product.code) }.sumOf { it.totalItemPrice }
                val totalAmount = cartItems.sumOf { it.totalItemPrice }

                val now = System.currentTimeMillis()
                val newOrder = TableOrder(
                    tableNumber = tableNumber,
                    waiterUsername = username,
                    createdAt = now,
                    confirmedAt = now,
                    status = "PENDIENTE_SERVIR",
                    totalAmount = totalAmount,
                    totalCocina = totalCocina,
                    totalBarra = totalBarra,
                    jornadaId = activeJornadaId,
                    comandaNumber = nextComandaNumber
                )

                val orderItems = cartItems.map { cartItem ->
                    val p = catalogProducts.firstOrNull { it.id == cartItem.product.id } ?: cartItem.product
                    val prodCode = if (p.code.isNotBlank()) p.code else if (cartItem.product.code.isNotBlank()) cartItem.product.code else com.example.util.ProductCodeHelper.generateNextProductCode(p.destination, p.category, catalogProducts)
                    OrderItem(
                        orderId = 0L,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        unitPrice = cartItem.unitPriceWithAgregados,
                        quantity = cartItem.quantity,
                        destination = cartItem.product.destination,
                        notes = cartItem.agregadosNoteString,
                        status = "PENDIENTE",
                        productCode = prodCode
                    )
                }

                try {
                    repository.confirmComandaWithItems(newOrder, orderItems)

                    // Send SMS automatically in background to Cajero
                    context?.let { ctx ->
                        val usersList = _uiState.value.users.ifEmpty {
                            withContext(Dispatchers.IO) { repository.getDatabase().userDao().getAllUsersSync() }
                        }
                        val cajeroInfo = com.example.util.CajaPhoneHelper.resolveCajeroInfo(
                            usersList,
                            _uiState.value.activeJornada,
                            _uiState.value.generalConfig
                        )
                        if (cajeroInfo.isConfigured) {
                            val itemsForSms = cartItems.map { cartItem ->
                                val p = catalogProducts.firstOrNull { it.id == cartItem.product.id } ?: cartItem.product
                                val code = if (p.code.isNotBlank()) p.code else if (cartItem.product.code.isNotBlank()) cartItem.product.code else com.example.util.ProductCodeHelper.generateNextProductCode(p.destination, p.category, catalogProducts)
                                val availableAgregados = com.example.ui.components.parseProductAgregados(p.agregadosList)
                                val agregadosIds = cartItem.selectedAgregados.map { agPair ->
                                    val matchedAg = availableAgregados.firstOrNull { it.name.trim().equals(agPair.first.trim(), ignoreCase = true) }
                                    val idx = availableAgregados.indexOf(matchedAg)
                                    if (matchedAg != null && matchedAg.id.isNotBlank()) matchedAg.id else String.format("%02d", idx + 1)
                                }
                                com.example.util.ComandaItemForSms(
                                    productCode = code,
                                    quantity = cartItem.quantity,
                                    agregadosIds = agregadosIds
                                )
                            }
                            val smsText = com.example.util.SmsComandaHelper.encodeComandaSms(nextComandaNumber, itemsForSms)
                            val sendResult = com.example.util.SmsComandaHelper.sendComandaSms(ctx, cajeroInfo.phoneNumber, smsText)

                            if (sendResult.success) {
                                _uiState.update {
                                    it.copy(
                                        successMessage = "SMS enviado al Cajero correctamente.",
                                        errorMessage = null
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        successMessage = "Comanda #$nextComandaNumber creada en Salón.",
                                        errorMessage = "No se pudo enviar el SMS al Cajero (${sendResult.errorReason ?: "Error de red"}). Puede reintentar con 'Reenviar SMS'."
                                    )
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    successMessage = "Comanda #$nextComandaNumber creada en Salón.",
                                    errorMessage = "No se pudo enviar el SMS al Cajero (${cajeroInfo.statusDescription})."
                                )
                            }
                        }
                    } ?: run {
                        _uiState.update {
                            it.copy(
                                successMessage = "Comanda #$nextComandaNumber creada en Salón.",
                                errorMessage = null
                            )
                        }
                    }
                    onComplete()
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Error al confirmar comanda: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun reenviarSmsComanda(order: TableOrder, context: Context) {
        viewModelScope.launch {
            try {
                val usersList = _uiState.value.users.ifEmpty {
                    withContext(Dispatchers.IO) { repository.getDatabase().userDao().getAllUsersSync() }
                }
                val cajeroInfo = com.example.util.CajaPhoneHelper.resolveCajeroInfo(
                    usersList,
                    _uiState.value.activeJornada,
                    _uiState.value.generalConfig
                )
                if (!cajeroInfo.isConfigured) {
                    _uiState.update {
                        it.copy(errorMessage = "No se pudo enviar el SMS al Cajero (${cajeroInfo.statusDescription}).")
                    }
                    return@launch
                }

                val allDbItems = withContext(Dispatchers.IO) {
                    repository.getDatabase().tableOrderDao().getItemsForOrderSync(order.id)
                }
                val catalogProducts = _uiState.value.products.ifEmpty {
                    withContext(Dispatchers.IO) { repository.getDatabase().productDao().getAllProductsSync() }
                }

                val itemsForSms = allDbItems.map { dbItem ->
                    convertOrderItemToSmsItem(dbItem, catalogProducts)
                }
                val smsText = com.example.util.SmsComandaHelper.encodeComandaSms(order.comandaNumber, itemsForSms)

                val sendResult = com.example.util.SmsComandaHelper.sendComandaSms(context, cajeroInfo.phoneNumber, smsText)

                if (sendResult.success) {
                    _uiState.update {
                        it.copy(
                            successMessage = "SMS enviado al Cajero correctamente.",
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "No se pudo enviar el SMS al Cajero (${sendResult.errorReason ?: "Error de red"})."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al reenviar SMS: ${e.localizedMessage}") }
            }
        }
    }


    fun setErrorMessage(msg: String) {
        _uiState.update { it.copy(errorMessage = msg) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setSuccessMessage(msg: String) {
        _uiState.update { it.copy(successMessage = msg) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun servirComandaSalon(order: TableOrder) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val confirmedTime = order.confirmedAt ?: order.createdAt
            val durationSeconds = maxOf(0L, (now - confirmedTime) / 1000)

            try {
                repository.servirComanda(order.id, now, durationSeconds)
                _uiState.update {
                    it.copy(successMessage = "Comanda #${order.comandaNumber} servida en Mesa ${order.tableNumber}.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al servir comanda: ${e.localizedMessage}") }
            }
        }
    }

    fun despacharComandaBarra(order: TableOrder) {
        viewModelScope.launch {
            try {
                repository.despacharBarraItems(order.id)
                _uiState.update {
                    it.copy(successMessage = "Comanda #${order.comandaNumber} despachada hacia Salón.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al despachar comanda: ${e.localizedMessage}") }
            }
        }
    }

    fun cobrarMesaSalon(
        tableNumber: Int?,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val activeOrdersForTable = _uiState.value.openOrders.filter {
                it.tableNumber == tableNumber && it.status != "COBRADA" && it.status != "CANCELADA"
            }

            if (activeOrdersForTable.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "La Mesa $tableNumber no tiene comandas activas para cobrar.") }
                return@launch
            }

            val hasPendingServir = activeOrdersForTable.any { it.status != "SERVIDA" }
            if (hasPendingServir) {
                _uiState.update {
                    it.copy(errorMessage = "No se puede cobrar la Mesa $tableNumber porque tiene comandas pendientes de servir.")
                }
                return@launch
            }

            val username = _uiState.value.currentUser?.username ?: "salon"
            val result = repository.cobrarMesaCompleta(
                tableNumber = tableNumber,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                chargedBy = username,
                currency = currency,
                exchangeRate = exchangeRate,
                amountInCurrency = amountInCurrency
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(successMessage = "Mesa $tableNumber cobrada exitosamente. Mesa liberada e inventario actualizado.")
                    }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Error al cobrar la mesa.") }
                }
            )
        }
    }

    fun cobrarComandaIndividual(
        orderId: Long,
        paymentMethod: String,
        cashReceived: Double,
        changeGiven: Double,
        currency: String = "CUP",
        exchangeRate: Double = 1.0,
        amountInCurrency: Double = 0.0,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val username = _uiState.value.currentUser?.username ?: "salon"
            val result = repository.cobrarComandaIndividual(
                orderId = orderId,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeGiven = changeGiven,
                chargedBy = username,
                currency = currency,
                exchangeRate = exchangeRate,
                amountInCurrency = amountInCurrency
            )

            result.fold(
                onSuccess = { order ->
                    val targetDisplay = if (order.tableNumber == null) "Para Llevar" else "Mesa ${order.tableNumber}"
                    _uiState.update {
                        it.copy(successMessage = "Comanda #${order.comandaNumber} ($targetDisplay) cobrada exitosamente. Inventario actualizado.")
                    }
                    onComplete()
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Error al cobrar comanda: ${err.message}") }
                }
            )
        }
    }

    fun closeJornadaSalon(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val activeJornada = _uiState.value.activeJornada
            if (activeJornada == null || !activeJornada.isOpen) {
                _uiState.update { it.copy(errorMessage = "No existe una jornada activa para cerrar.") }
                return@launch
            }

            val username = _uiState.value.currentUser?.username ?: "salon"
            try {
                // Validar que no haya comandas pendientes antes de cerrar
                val pendingOrders = _uiState.value.allOrders.filter {
                    it.jornadaId == activeJornada.id && it.status != "COBRADA" && it.status != "CANCELADA"
                }
                if (pendingOrders.isNotEmpty()) {
                    _uiState.update { it.copy(errorMessage = "No se puede cerrar la jornada: hay mesas activas o comandas pendientes.") }
                    return@launch
                }

                // Calcular ventas totales de la jornada para el archivo
                val closedOrders = _uiState.value.allOrders.filter {
                    it.jornadaId == activeJornada.id && it.status == "COBRADA"
                }
                val totalVentas = closedOrders.sumOf { it.totalAmount }

                repository.closeJornadaSalonAndOpenNew(
                    jornada = activeJornada,
                    closedBy = username,
                    totalVentas = totalVentas
                )
                _uiState.update {
                    it.copy(successMessage = "Jornada #${activeJornada.id} cerrada y archivada exitosamente. Nueva jornada iniciada.")
                }
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al cerrar jornada: ${e.localizedMessage}") }
            }
        }
    }

    fun restoreSalonBackupJson(jsonString: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val parseResult = com.example.util.SalonBackupManager.validateAndParse(jsonString)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()?.localizedMessage ?: "Formato de archivo JSON incompatible o corrupto."
                _uiState.update { it.copy(errorMessage = "Error al restaurar respaldo: $err") }
                onComplete(false)
                return@launch
            }

            val parsedData = parseResult.getOrNull()!!
            try {
                repository.restoreSalonBackup(
                    jornadas = parsedData.jornadas,
                    orders = parsedData.orders,
                    orderItems = parsedData.orderItems,
                    products = parsedData.products,
                    bitacoraEntries = parsedData.bitacoraEntries
                )
                if (parsedData.salonTableCount in 1..100) {
                    updateSalonTableCount(parsedData.salonTableCount)
                }
                sharedPreferences.edit().putLong("last_imported_salon_timestamp", System.currentTimeMillis()).apply()
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        successMessage = "Respaldo qdepsalon.json restaurado correctamente. Estado reemplazado de forma atómica."
                    )
                }
                onComplete(true)
            } catch (e: Exception) {
                val err = "Error al aplicar el respaldo en la base de datos: ${e.localizedMessage ?: e.message}"
                _uiState.update { it.copy(errorMessage = err) }
                onComplete(false)
            }
        }
    }

    fun restoreBarraBackupJson(jsonString: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val parseResult = com.example.util.BarraBackupManager.validateAndParse(jsonString)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()?.localizedMessage ?: "Formato de archivo qdepbarra.json inválido o corrupto."
                _uiState.update { it.copy(errorMessage = "Error al restaurar respaldo: $err") }
                onComplete(false)
                return@launch
            }

            val parsedData = parseResult.getOrNull()!!
            try {
                repository.restoreBarraBackup(
                    jornadas = parsedData.jornadas,
                    products = parsedData.products,
                    stockMovements = parsedData.stockMovements,
                    orders = parsedData.orders,
                    orderItems = parsedData.orderItems,
                    bitacoraEntries = parsedData.bitacoraEntries
                )
                sharedPreferences.edit().putLong("last_imported_barra_timestamp", System.currentTimeMillis()).apply()
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        successMessage = "Respaldo qdepbarra.json restaurado correctamente. Estado reemplazado de forma atómica."
                    )
                }
                onComplete(true)
            } catch (e: Exception) {
                val err = "Error al aplicar el respaldo de Barra en la base de datos: ${e.localizedMessage ?: e.message}"
                _uiState.update { it.copy(errorMessage = err) }
                onComplete(false)
            }
        }
    }

    fun restoreCajeroBackupJson(jsonString: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val parseResult = com.example.util.CajeroBackupManager.validateAndParse(jsonString)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()?.localizedMessage ?: "Formato de archivo qdepcajero.json inválido o corrupto."
                _uiState.update { it.copy(errorMessage = "Error al restaurar respaldo: $err") }
                onComplete(false)
                return@launch
            }

            val parsedData = parseResult.getOrNull()!!
            try {
                repository.restoreCajeroBackup(
                    jornadas = parsedData.jornadas,
                    products = parsedData.products,
                    orders = parsedData.orders,
                    orderItems = parsedData.orderItems,
                    transferencias = parsedData.transferencias,
                    bitacoraEntries = parsedData.bitacoraEntries,
                    consumoPersonalList = parsedData.consumoPersonalList
                )
                sharedPreferences.edit().putLong("last_imported_cajero_timestamp", System.currentTimeMillis()).apply()
                _uiState.update {
                    it.copy(
                        errorMessage = null,
                        successMessage = "Respaldo qdepcajero.json restaurado correctamente. Estado reemplazado de forma atómica."
                    )
                }
                onComplete(true)
            } catch (e: Exception) {
                val err = "Error al aplicar el respaldo de Cajero en la base de datos: ${e.localizedMessage ?: e.message}"
                _uiState.update { it.copy(errorMessage = err) }
                onComplete(false)
            }
        }
    }

    fun getLastImportedSalonTime(): String {
        val ts = sharedPreferences.getLong("last_imported_salon_timestamp", 0L)
        return if (ts == 0L) "No recibido aún" else java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }

    fun getLastImportedBarraTime(): String {
        val ts = sharedPreferences.getLong("last_imported_barra_timestamp", 0L)
        return if (ts == 0L) "No recibido aún" else java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }

    fun getLastImportedCajeroTime(): String {
        val ts = sharedPreferences.getLong("last_imported_cajero_timestamp", 0L)
        return if (ts == 0L) "No recibido aún" else java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }

    fun restoreDuenoBackupJson(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                com.example.util.BusinessBackupManager.restoreBackupJson(
                    jsonString = jsonString,
                    db = repository.getDatabase(),
                    context = getApplication()
                )
            }
            _uiState.update { it.copy(isLoading = false) }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        successMessage = "Respaldo del Negocio restaurado correctamente. Estado operativo actualizado."
                    )
                }
                onComplete(true, result.getOrNull() ?: "")
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Error al restaurar el respaldo del Negocio."
                _uiState.update { it.copy(errorMessage = err) }
                onComplete(false, err)
            }
        }
    }

    fun getDuenoBackupSummary(jsonString: String): Result<String> {
        return com.example.util.BusinessBackupManager.getBackupSummaryText(jsonString, getApplication())
    }

    fun getLegacyProduccionBackupSummary(jsonString: String): Result<String> {
        return com.example.util.BusinessBackupManager.getLegacyProduccionSummaryText(jsonString)
    }

    fun importLegacyProduccionBackupJson(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                com.example.util.BusinessBackupManager.importLegacyProduccion(
                    jsonString = jsonString,
                    db = repository.getDatabase(),
                    context = getApplication()
                )
            }
            _uiState.update { it.copy(isLoading = false) }
            if (result.isSuccess) {
                val summary = result.getOrNull()!!
                val successMsg = "Importación de Producción completada.\n" +
                        "• Insumos: ${summary.insumosNuevos} nuevos, ${summary.insumosActualizados} actualizados.\n" +
                        "• Productos: ${summary.productosNuevos} nuevos, ${summary.productosActualizados} actualizados.\n" +
                        "• Recetas: ${summary.recetasNuevas} nuevas, ${summary.recetasActualizadas} actualizadas.\n" +
                        "• Categorías: ${summary.categoriasNuevas} nuevas."
                _uiState.update {
                    it.copy(
                        successMessage = successMsg
                    )
                }
                onComplete(true, successMsg)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Error al importar los datos de Producción."
                _uiState.update { it.copy(errorMessage = err) }
                onComplete(false, err)
            }
        }
    }


    private suspend fun isConnectionAvailable(urlString: String): Boolean = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext false
        kotlinx.coroutines.withTimeoutOrNull(2500L) {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.requestMethod = "HEAD"
                connection.responseCode
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun runUpdateFlow(
        url: String,
        isAuto: Boolean,
        onPerformUpdate: suspend () -> Any,
        onSuccessRefresh: suspend () -> Unit = {},
        customResultMessages: Map<Any, String>? = null
    ): Boolean {
        if (url.isBlank()) {
            if (!isAuto) {
                _uiState.update { it.copy(updateStatusText = "Error: URL no configurada") }
            }
            return false
        }

        if (isAuto) {
            // SILENT AUTO FLOW:
            // 1. Never set isLoading = true
            // 2. Never update updateStatusText
            // 3. Strict timeout for network checks
            val hasConn = isConnectionAvailable(url)
            if (!hasConn) return false

            return try {
                val result = onPerformUpdate()
                val isSuccess = when (result) {
                    is com.example.util.UserUpdateManager.UpdateResult.Success,
                    is com.example.util.UserUpdateManager.UpdateResult.NoNewUpdate,
                    com.example.util.DataUpdateManager.UpdateResult.Success,
                    com.example.util.DataUpdateManager.UpdateResult.NoNewUpdate -> true
                    else -> false
                }
                if (isSuccess) {
                    onSuccessRefresh()
                }
                isSuccess
            } catch (e: Exception) {
                false
            }
        } else {
            // MANUAL FLOW (Interactive visual feedback with status messages):
            _uiState.update { it.copy(updateStatusText = "Comprobando conexión...", isLoading = true) }
            kotlinx.coroutines.delay(1000)

            val hasConn = isConnectionAvailable(url)
            if (!hasConn) {
                val noConnMsg = customResultMessages?.get(com.example.util.DataUpdateManager.UpdateResult.NoConnection)
                    ?: customResultMessages?.get(com.example.util.UserUpdateManager.UpdateResult.NoConnection)
                    ?: "No hay conexión."
                _uiState.update { it.copy(updateStatusText = noConnMsg, isLoading = false) }
                return false
            }

            _uiState.update { it.copy(updateStatusText = "Conexión disponible.") }
            kotlinx.coroutines.delay(1000)

            _uiState.update { it.copy(updateStatusText = "Comprobando si corresponde realizar actualización...") }
            kotlinx.coroutines.delay(1000)

            val result = try {
                onPerformUpdate()
            } catch (e: Exception) {
                null
            }

            if (customResultMessages != null && result != null) {
                val mappedMsg = customResultMessages[result]
                if (mappedMsg != null) {
                    val isSuccess = result == com.example.util.UserUpdateManager.UpdateResult.Success ||
                            result == com.example.util.DataUpdateManager.UpdateResult.Success
                    if (isSuccess) {
                        _uiState.update { it.copy(updateStatusText = "Actualización necesaria.") }
                        kotlinx.coroutines.delay(1000)
                        _uiState.update { it.copy(updateStatusText = "Actualización en curso...") }
                        kotlinx.coroutines.delay(1200)
                        onSuccessRefresh()
                        _uiState.update { it.copy(updateStatusText = mappedMsg) }
                    } else {
                        _uiState.update { it.copy(updateStatusText = mappedMsg) }
                    }
                    _uiState.update { it.copy(isLoading = false) }
                    return isSuccess
                }
            }

            val success = when (result) {
                is com.example.util.UserUpdateManager.UpdateResult.Success,
                com.example.util.DataUpdateManager.UpdateResult.Success -> {
                    _uiState.update { it.copy(updateStatusText = "Hay información nueva disponible.") }
                    kotlinx.coroutines.delay(1000)
                    _uiState.update { it.copy(updateStatusText = "Actualización en curso...") }
                    kotlinx.coroutines.delay(1200)
                    onSuccessRefresh()
                    _uiState.update { it.copy(updateStatusText = "Información actualizada correctamente.") }
                    kotlinx.coroutines.delay(1500)
                    true
                }
                is com.example.util.UserUpdateManager.UpdateResult.NoNewUpdate,
                com.example.util.DataUpdateManager.UpdateResult.NoNewUpdate -> {
                    _uiState.update { it.copy(updateStatusText = "Tu información ya está actualizada.") }
                    kotlinx.coroutines.delay(1500)
                    true
                }
                is com.example.util.UserUpdateManager.UpdateResult.InvalidFile,
                com.example.util.DataUpdateManager.UpdateResult.InvalidFile -> {
                    _uiState.update { it.copy(updateStatusText = "No se pudo actualizar la información.") }
                    false
                }
                is com.example.util.UserUpdateManager.UpdateResult.NoConnection,
                com.example.util.DataUpdateManager.UpdateResult.NoConnection -> {
                    _uiState.update { it.copy(updateStatusText = "No hay conexión.") }
                    false
                }
                else -> {
                    _uiState.update { it.copy(updateStatusText = "No se pudo actualizar la información.") }
                    false
                }
            }

            if (success) {
                _uiState.update { it.copy(updateStatusText = null, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
            return success
        }
    }

    suspend fun updateInformationFromLogin(url: String): com.example.util.UserUpdateManager.UpdateResult {
        val db = repository.getDatabase()
        return com.example.util.UserUpdateManager.updateUsers(db, url)
    }

    fun updateUsers(url: String? = null, isAuto: Boolean = false) {
        if (!isAuto && _uiState.value.isLoading) return
        val finalUrl = url ?: uiState.value.generalConfig?.urlUsuariosJson ?: ""
        if (finalUrl.isBlank()) {
            if (!isAuto) {
                _uiState.update { it.copy(updateStatusText = "Error: URL no configurada") }
            }
            return
        }

        viewModelScope.launch {
            val db = repository.getDatabase()
            runUpdateFlow(
                url = finalUrl,
                isAuto = isAuto,
                onPerformUpdate = {
                    com.example.util.UserUpdateManager.updateUsers(db, finalUrl)
                },
                onSuccessRefresh = {
                    val currentLoggedIn = _uiState.value.currentUser
                    if (currentLoggedIn != null) {
                        val updatedUser = db.userDao().getUserByUsername(currentLoggedIn.username)
                        if (updatedUser == null || !updatedUser.isActive) {
                            logout()
                        } else {
                            _uiState.update { it.copy(currentUser = updatedUser) }
                        }
                    }
                }
            )
        }
    }

    fun updateDuenoPasswordLocally(username: String, newPasswordPlain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = repository.getDatabase()
            val newHash = newPasswordPlain.trim().toSha256()

            // 1. Update User
            val user = db.userDao().getUserByUsername(username)
            if (user != null) {
                db.userDao().updateUser(user.copy(passwordHash = newHash))
                if (_uiState.value.currentUser?.username.equals(username, ignoreCase = true)) {
                    _uiState.update { it.copy(currentUser = user.copy(passwordHash = newHash)) }
                }
            }

            // 2. Update PersonalContratado if exists
            val personal = db.personalContratadoDao().getPersonalByUsername(username)
            if (personal != null) {
                db.personalContratadoDao().updatePersonal(
                    personal.copy(
                        passwordHash = newHash,
                        passwordPlain = newPasswordPlain.trim()
                    )
                )
            }
        }
    }

    fun importUsersFromJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val result = com.example.util.UserUpdateManager.processUserJson(db, jsonContent, "")
                when (result) {
                    is com.example.util.UserUpdateManager.UpdateResult.Success -> {
                        val currentLoggedIn = _uiState.value.currentUser
                        if (currentLoggedIn != null) {
                            val updatedUser = db.userDao().getUserByUsername(currentLoggedIn.username)
                            if (updatedUser == null || !updatedUser.isActive) {
                                logout()
                            } else {
                                _uiState.update { it.copy(currentUser = updatedUser) }
                            }
                        }
                        _uiState.update { it.copy(isLoading = false, successMessage = "Usuarios importados exitosamente") }
                    }
                    is com.example.util.UserUpdateManager.UpdateResult.InvalidFile -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "El archivo JSON no tiene un formato válido de qusuarios.json") }
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "No se pudieron importar los usuarios") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar usuarios: ${e.message}") }
            }
        }
    }

    fun importQProduccionJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val success = com.example.util.DataUpdateManager.importQProduccion(db, jsonContent, username)
                if (success) {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Producción (Q_produccion.json) actualizada exitosamente") }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "El archivo no contiene un formato válido de Q_produccion.json") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar producción: ${e.message}") }
            }
        }
    }

    fun importQMercanciasJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val success = com.example.util.DataUpdateManager.importQMercancias(db, jsonContent, username)
                if (success) {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Mercaderías (Q_mercaderias.json) actualizadas exitosamente") }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "El archivo no contiene un formato válido de Q_mercaderias.json") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar mercaderías: ${e.message}") }
            }
        }
    }

    fun importQInversionesJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val success = com.example.util.DataUpdateManager.importQInversiones(db, jsonContent, username)
                if (success) {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Inversiones (Q_inversiones.json) actualizadas exitosamente") }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "El archivo no contiene un formato válido de Q_inversiones.json") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar inversiones: ${e.message}") }
            }
        }
    }

    suspend fun generateQDuenoJson(): String {
        return com.example.util.QDuenoExporter.generateQDuenoJsonString(repository.getDatabase())
    }

    fun clearUpdateStatus() {
        _uiState.update { it.copy(updateStatusText = null) }
    }

    fun updateCatalogo(url: String? = null, isAuto: Boolean = false) {
        if (!isAuto && _uiState.value.isLoading) return
        val finalUrl = url ?: uiState.value.generalConfig?.urlCatalogoJson ?: ""
        if (finalUrl.isBlank()) {
            if (!isAuto) {
                _uiState.update { it.copy(updateStatusText = "Error: URL de catálogo no configurada") }
            }
            return
        }

        viewModelScope.launch {
            val db = repository.getDatabase()
            runUpdateFlow(
                url = finalUrl,
                isAuto = isAuto,
                onPerformUpdate = {
                    com.example.util.DataUpdateManager.updateCatalogo(db, finalUrl)
                }
            )
        }
    }

    fun updateMercainv(url: String? = null, isAuto: Boolean = false) {
        if (!isAuto && _uiState.value.isLoading) return
        val finalUrl = url ?: uiState.value.generalConfig?.urlMercainvJson ?: ""
        if (finalUrl.isBlank()) {
            if (!isAuto) {
                _uiState.update { it.copy(updateStatusText = "Error: URL de inventario no configurada") }
            }
            return
        }

        viewModelScope.launch {
            val db = repository.getDatabase()
            runUpdateFlow(
                url = finalUrl,
                isAuto = isAuto,
                onPerformUpdate = {
                    com.example.util.DataUpdateManager.updateMercainv(db, finalUrl)
                }
            )
        }
    }

    fun updateQDueno(url: String? = null, isAuto: Boolean = false) {
        if (!isAuto && _uiState.value.isLoading) return
        val finalUrl = url ?: uiState.value.generalConfig?.urlQDuenoJson ?: ""
        if (finalUrl.isBlank()) {
            if (!isAuto) {
                _uiState.update { it.copy(updateStatusText = "No fue posible obtener la actualización. URL Q_DUEÑO no configurada.") }
            }
            return
        }

        viewModelScope.launch {
            val db = repository.getDatabase()
            val context = getApplication<android.app.Application>()
            val username = _uiState.value.currentUser?.username ?: "Dueño"
            val customMsgMap: Map<Any, String> = mapOf(
                com.example.util.DataUpdateManager.UpdateResult.Success to "Actualización realizada correctamente.",
                com.example.util.DataUpdateManager.UpdateResult.NoNewUpdate to "Ya tienes la versión más reciente.",
                com.example.util.DataUpdateManager.UpdateResult.NoConnection to "No fue posible obtener la actualización.",
                com.example.util.DataUpdateManager.UpdateResult.DownloadError to "No fue posible obtener la actualización.",
                com.example.util.DataUpdateManager.UpdateResult.InvalidFile to "No fue posible obtener la actualización."
            )
            runUpdateFlow(
                url = finalUrl,
                isAuto = isAuto,
                onPerformUpdate = {
                    com.example.util.DataUpdateManager.updateQDueno(db, finalUrl, username, context)
                },
                customResultMessages = customMsgMap
            )
        }
    }

    fun importQDuenoJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val context = getApplication<android.app.Application>()
                val username = _uiState.value.currentUser?.username ?: "Dueño"
                val result = com.example.util.DataUpdateManager.importQDueno(db, jsonContent, username, context)
                when (result) {
                    is com.example.util.DataUpdateManager.QDuenoUpdateResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "Datos de Q_dueño.json actualizados exitosamente (Versión: ${result.version})"
                            )
                        }
                    }
                    is com.example.util.DataUpdateManager.QDuenoUpdateResult.VersionNotNewer -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                updateStatusText = result.message,
                                errorMessage = result.message
                            )
                        }
                    }
                    is com.example.util.DataUpdateManager.QDuenoUpdateResult.InvalidFile -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.reason
                            )
                        }
                    }
                    is com.example.util.DataUpdateManager.QDuenoUpdateResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar Q_dueño.json: ${e.message}") }
            }
        }
    }

    fun importQAdminJsonContent(jsonContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val db = repository.getDatabase()
                val context = getApplication<android.app.Application>()
                val username = _uiState.value.currentUser?.username ?: "Admin"
                val (success, message) = com.example.util.DataUpdateManager.importQAdmin(db, jsonContent, username, context)
                if (success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = message
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error al importar Q_admin.json: ${e.message}") }
            }
        }
    }

    fun checkDailyDataUpdates() {
        val user = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val db = repository.getDatabase()
            val config = db.configuracionGeneralDao().getConfigSync() ?: return@launch

            val cal = java.util.Calendar.getInstance()
            val hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY)
            if (hourOfDay >= 9) {
                // 1. Catalogo first
                if (config.urlCatalogoJson.isNotBlank()) {
                    val lastCatCal = java.util.Calendar.getInstance().apply { timeInMillis = config.lastCatalogoUpdateDate }
                    val isCatSameDay = cal.get(java.util.Calendar.YEAR) == lastCatCal.get(java.util.Calendar.YEAR) &&
                                       cal.get(java.util.Calendar.DAY_OF_YEAR) == lastCatCal.get(java.util.Calendar.DAY_OF_YEAR)

                    if (!isCatSameDay || config.lastCatalogoUpdateStatus != com.example.util.DataUpdateManager.STATUS_SUCCESS) {
                        runUpdateFlow(
                            url = config.urlCatalogoJson,
                            isAuto = true,
                            onPerformUpdate = {
                                com.example.util.DataUpdateManager.updateCatalogo(db, config.urlCatalogoJson)
                            }
                        )
                    }
                }

                // 2. Mercainv second (only for Barra and Cajero)
                if ((user.role == com.example.data.local.model.UserRole.BARRA || user.role == com.example.data.local.model.UserRole.CAJERO)) {
                    val latestConfig = db.configuracionGeneralDao().getConfigSync() ?: config
                    if (latestConfig.urlMercainvJson.isNotBlank()) {
                        val lastInvCal = java.util.Calendar.getInstance().apply { timeInMillis = latestConfig.lastMercainvUpdateDate }
                        val isInvSameDay = cal.get(java.util.Calendar.YEAR) == lastInvCal.get(java.util.Calendar.YEAR) &&
                                           cal.get(java.util.Calendar.DAY_OF_YEAR) == lastInvCal.get(java.util.Calendar.DAY_OF_YEAR)

                        if (!isInvSameDay || latestConfig.lastMercainvUpdateStatus != com.example.util.DataUpdateManager.STATUS_SUCCESS) {
                            runUpdateFlow(
                                url = latestConfig.urlMercainvJson,
                                isAuto = true,
                                onPerformUpdate = {
                                    com.example.util.DataUpdateManager.updateMercainv(db, latestConfig.urlMercainvJson)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SalonCartItem(
    val product: com.example.data.local.model.Product,
    val quantity: Int,
    val selectedAgregados: List<Pair<String, Double>>,
    val customNotes: String = ""
) {
    val unitPriceWithAgregados: Double
        get() = product.price + selectedAgregados.sumOf { it.second }

    val totalItemPrice: Double
        get() = unitPriceWithAgregados * quantity

    val agregadosNoteString: String
        get() {
            val listStr = selectedAgregados.joinToString(", ") { it.first }
            return when {
                listStr.isNotEmpty() && customNotes.isNotEmpty() -> "Agregados: $listStr | Nota: $customNotes"
                listStr.isNotEmpty() -> "Agregados: $listStr"
                customNotes.isNotEmpty() -> "Nota: $customNotes"
                else -> ""
            }
        }



}

data class BarraCartItem(
    val product: com.example.data.local.model.Product,
    val quantity: Int,
    val subcategory: String = "BEBIDAS",
    val selectedAgregados: List<Pair<String, Double>> = emptyList()
) {
    val unitPriceWithAgregados: Double
        get() = product.price + selectedAgregados.sumOf { it.second }

    val totalAmount: Double
        get() = unitPriceWithAgregados * quantity

    val agregadosNoteString: String
        get() {
            val listStr = selectedAgregados.joinToString(", ") { it.first }
            return if (listStr.isNotEmpty()) "Agregados: $listStr" else ""
        }
}

// 1.5.3: Conversor de Unidades
object UnitConverter {
    fun areCompatible(unitA: String, unitB: String): Boolean {
        val uA = unitA.lowercase().trim()
        val uB = unitB.lowercase().trim()
        if (uA == uB) return true
        val weightUnits = setOf("g", "gr", "gramos", "kg", "kilogramos", "lb", "lbs", "libras", "oz", "onzas")
        val volumeUnits = setOf("ml", "mililitros", "l", "litros")
        val countUnits = setOf("u", "un", "unidad", "unidades", "docena", "docenas", "file", "files", "carton", "cartón")
        
        if (uA in weightUnits && uB in weightUnits) return true
        if (uA in volumeUnits && uB in volumeUnits) return true
        if (uA in countUnits && uB in countUnits) return true
        return false
    }

    fun convert(value: Double, fromUnit: String, toUnit: String): Double? {
        val from = fromUnit.lowercase().trim()
        val to = toUnit.lowercase().trim()
        if (from == to) return value

        val weightUnits = setOf("g", "gr", "gramos", "kg", "kilogramos", "lb", "lbs", "libras", "oz", "onzas")
        val volumeUnits = setOf("ml", "mililitros", "l", "litros")
        val countUnits = setOf("u", "un", "unidad", "unidades", "docena", "docenas", "file", "files", "carton", "cartón")

        // Weight conversions
        if (from in weightUnits && to in weightUnits) {
            val valInGrams = when (from) {
                "g", "gr", "gramos" -> value
                "kg", "kilogramos" -> value * 1000.0
                "lb", "lbs", "libras" -> value * 453.59237
                "oz", "onzas" -> value * 28.349523125
                else -> return null
            }
            return when (to) {
                "g", "gr", "gramos" -> valInGrams
                "kg", "kilogramos" -> valInGrams / 1000.0
                "lb", "lbs", "libras" -> valInGrams / 453.59237
                "oz", "onzas" -> valInGrams / 28.349523125
                else -> null
            }
        }

        // Volume conversions
        if (from in volumeUnits && to in volumeUnits) {
            val valInMl = when (from) {
                "ml", "mililitros" -> value
                "l", "litros" -> value * 1000.0
                else -> return null
            }
            return when (to) {
                "ml", "mililitros" -> valInMl
                "l", "litros" -> valInMl / 1000.0
                else -> null
            }
        }

        // Count conversions
        if (from in countUnits && to in countUnits) {
            val valInUnits = when (from) {
                "u", "un", "unidad", "unidades" -> value
                "docena", "docenas" -> value * 12.0
                "file", "files", "carton", "cartón" -> value * 30.0
                else -> return null
            }
            return when (to) {
                "u", "un", "unidad", "unidades" -> valInUnits
                "docena", "docenas" -> valInUnits / 12.0
                "file", "files", "carton", "cartón" -> valInUnits / 30.0
                else -> null
            }
        }

        return null
    }

    fun convertToGrams(value: Double, unit: String): Double? {
        return convert(value, unit, "g")
    }
}

data class MercaderiaCuadreItem(
    val mercaderiaId: Long,
    val productId: Long,
    val ventas: Double,
    val mermas: Double,
    val price: Double
)

data class AgregadoCuadreItem(
    val materiaPrimaId: Long,
    val name: String,
    val racionesEnviadas: Double,
    val racionesVendidas: Double,
    val racionesRegalia: Double,
    val racionesSobrantes: Double,
    val precioVenta: Double,
    val costoPorRacion: Double,
    val rationQuantity: Double,
    val unit: String
)

data class ProduccionCuadreItem(
    val productId: Long,
    val productName: String,
    val totalProduced: Double,
    val vendible: Double,
    val pendientes: Double,
    val mermas: Double,
    val unit: String
)

