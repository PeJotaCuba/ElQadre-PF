package com.example.ui.screens.barra

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.cajero.QuickActionsDialog
import com.example.util.OrderNotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.data.local.model.Jornada
import com.example.data.local.model.OrderItem
import com.example.data.local.model.Product
import com.example.data.local.model.StockMovement
import com.example.data.local.model.TableOrder
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class BarraTab(val title: String, val icon: ImageVector, val tag: String) {
    INICIO("Inicio", Icons.Outlined.Home, "tab_barra_inicio"),
    HISTORIAL("Historial", Icons.Outlined.History, "tab_barra_historial"),
    INFORME("Informe", Icons.Outlined.Assessment, "tab_barra_informe"),
    CIERRE("Cierre", Icons.Outlined.Lock, "tab_barra_cierre"),
    AJUSTES("Ajustes", Icons.Outlined.Settings, "tab_barra_ajustes")
}

data class BarraProductItem(
    val product: Product,
    val subcategory: String, // "BEBIDAS" or "CONFITERÍAS"
    val inventarioInicial: Int,
    val entradasJornada: Int,
    val ventasJornada: Int,
    val existenciaActual: Int
)

fun getBarraSubcategory(product: Product): String {
    val cat = product.category.trim()
    return if (cat.contains("confiter", ignoreCase = true) ||
        cat.contains("snack", ignoreCase = true) ||
        cat.contains("dulce", ignoreCase = true) ||
        cat.contains("galleta", ignoreCase = true) ||
        cat.contains("chocolate", ignoreCase = true) ||
        cat.contains("mani", ignoreCase = true) ||
        cat.contains("maní", ignoreCase = true) ||
        cat.contains("caramelo", ignoreCase = true) ||
        cat.contains("postre", ignoreCase = true)
    ) {
        "CONFITERÍAS"
    } else {
        "BEBIDAS"
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun BarraScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(BarraTab.INICIO) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state controllers
    var showInventarioDialog by remember { mutableStateOf(false) }
    var showEntradasDialog by remember { mutableStateOf(false) }
    var showRegistrarEntradaDialog by remember { mutableStateOf(false) }
    var preselectedProductForEntrada by remember { mutableStateOf<Product?>(null) }
    var showComandaDialog by remember { mutableStateOf(false) }
    var selectedOrderForCobro by remember { mutableStateOf<TableOrder?>(null) }
    var showQuickActions by remember { mutableStateOf(false) }

    val isAnyBarraModalOpen = showInventarioDialog ||
            showEntradasDialog ||
            showRegistrarEntradaDialog ||
            showComandaDialog ||
            selectedOrderForCobro != null ||
            showQuickActions

    BackHandler(enabled = isAnyBarraModalOpen || selectedTab != BarraTab.INICIO) {
        when {
            showInventarioDialog -> showInventarioDialog = false
            showEntradasDialog -> showEntradasDialog = false
            showRegistrarEntradaDialog -> showRegistrarEntradaDialog = false
            showComandaDialog -> showComandaDialog = false
            selectedOrderForCobro != null -> selectedOrderForCobro = null
            showQuickActions -> showQuickActions = false
            selectedTab != BarraTab.INICIO -> selectedTab = BarraTab.INICIO
        }
    }

    val context = LocalContext.current

    // Notification permission request for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* handled */ }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 1. Notificación al pasar comanda a estado SERVIDA
    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id ?: 0L

    val servedBarraOrders = remember(uiState.allOrders, uiState.openOrders, activeJornadaId) {
        val allActive = if (uiState.openOrders.isNotEmpty()) uiState.openOrders else uiState.allOrders
        allActive.filter { order ->
            (activeJornadaId == 0L || order.jornadaId == activeJornadaId) &&
            order.status == "SERVIDA" &&
            order.status != "COBRADA" &&
            order.status != "CANCELADA"
        }
    }
    var knownServedOrderIds by remember { mutableStateOf<Set<Long>?>(null) }

    LaunchedEffect(servedBarraOrders) {
        val currentServedIds = servedBarraOrders.map { it.id }.toSet()
        val previous = knownServedOrderIds
        if (previous == null) {
            // First composition: register existing served orders without re-notifying them
            knownServedOrderIds = currentServedIds
        } else {
            val newlyServedOrders = servedBarraOrders.filter { it.id !in previous }
            newlyServedOrders.forEach { order ->
                OrderNotificationManager.notifyComandaServida(context, order)
            }
            knownServedOrderIds = currentServedIds
        }
    }

    // 2. Recordatorio de Cobro: Cuando existan 2 o más comandas SERVIDAS y no cobradas, cada 10 minutos
    LaunchedEffect(servedBarraOrders.size >= 2) {
        if (servedBarraOrders.size >= 2) {
            while (isActive) {
                delay(10 * 60 * 1000L) // 10 minutos
                val allActive = if (uiState.openOrders.isNotEmpty()) uiState.openOrders else uiState.allOrders
                val currentServedCount = allActive.count { order ->
                    (activeJornadaId == 0L || order.jornadaId == activeJornadaId) &&
                    order.status == "SERVIDA" &&
                    order.status != "COBRADA" &&
                    order.status != "CANCELADA"
                }
                if (currentServedCount >= 2) {
                    OrderNotificationManager.notifyRecordatorioCobro(context, currentServedCount)
                }
            }
        }
    }

    // Surface user notifications
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Compute inventory numbers per product in Barra
    val barProducts = remember(uiState.products) {
        uiState.products.filter { it.destination == "BARRA" }
    }

    val computedBarraItems = remember(barProducts, uiState.stockMovements, uiState.allOrders, uiState.allOrderItems, activeJornadaId) {
        val closedOrdersIds = uiState.allOrders
            .filter { it.jornadaId == activeJornadaId && it.status == "COBRADA" }
            .map { it.id }
            .toSet()

        barProducts.map { p ->
            val entradas = uiState.stockMovements
                .filter { it.productId == p.id && it.type == "ENTRADA" && it.jornadaId == activeJornadaId }
                .sumOf { it.quantity }

            val ventas = uiState.allOrderItems
                .filter { closedOrdersIds.contains(it.orderId) && it.productId == p.id }
                .sumOf { it.quantity }

            val existenciaActual = p.stock
            val initialSnapshotMovement = uiState.stockMovements.firstOrNull {
                it.productId == p.id && it.type == "INVENTARIO_INICIAL" && it.jornadaId == activeJornadaId
            }
            val inventarioInicial = initialSnapshotMovement?.quantity ?: maxOf(0, existenciaActual - entradas + ventas)
            val subcategory = getBarraSubcategory(p)

            BarraProductItem(
                product = p,
                subcategory = subcategory,
                inventarioInicial = inventarioInicial,
                entradasJornada = entradas,
                ventasJornada = ventas,
                existenciaActual = existenciaActual
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElQadreBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BarraTopBar(
                uiState = uiState,
                onRefresh = { viewModel.refreshCatalog() },
                onLogout = onLogout
            )
        },
        bottomBar = {
            BarraBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        floatingActionButton = {
            if (selectedTab == BarraTab.INICIO) {
                FloatingActionButton(
                    onClick = { showQuickActions = true },
                    containerColor = ElQadreGold,
                    contentColor = ElQadreNavy,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("btn_acciones_rapidas_barra")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Calculate,
                        contentDescription = "Acciones Rápidas"
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                BarraTab.INICIO -> {
                    BarraInicioTab(
                        uiState = uiState,
                        onOpenInventario = { showInventarioDialog = true },
                        onOpenEntradas = { showEntradasDialog = true },
                        onOpenRegistrarEntrada = { product ->
                            preselectedProductForEntrada = product
                            showRegistrarEntradaDialog = true
                        },
                        onOpenRegistrarComanda = { showComandaDialog = true },
                        onOpenCobro = { order -> selectedOrderForCobro = order },
                        onDespacharComanda = { viewModel.despacharComandaBarra(it) }
                    )
                }
                BarraTab.HISTORIAL -> {
                    BarraHistorialTab(
                        uiState = uiState
                    )
                }
                BarraTab.INFORME -> {
                    BarraInformeTab(
                        uiState = uiState,
                        computedBarraItems = computedBarraItems
                    )
                }
                BarraTab.CIERRE -> {
                    BarraCierreTab(
                        uiState = uiState,
                        computedBarraItems = computedBarraItems,
                        onConfirmCierre = { notes, physicalCounts ->
                            viewModel.closeJornadaBarra(
                                notes = notes,
                                physicalCounts = physicalCounts,
                                onSuccess = {
                                    selectedTab = BarraTab.INICIO
                                }
                            )
                        }
                    )
                }
                BarraTab.AJUSTES -> {
                    BarraAjustesTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        computedBarraItems = computedBarraItems
                    )
                }
            }
        }
    }

    // Modal: Inventario Completo
    if (showInventarioDialog) {
        BarraInventarioCompletoDialog(
            uiState = uiState,
            onDismiss = { showInventarioDialog = false },
            onRegistrarEntrada = { product ->
                preselectedProductForEntrada = product
                showRegistrarEntradaDialog = true
            }
        )
    }

    // Modal: Historial de Entradas de la Jornada
    if (showEntradasDialog) {
        BarraEntradasHistorialDialog(
            uiState = uiState,
            onDismiss = { showEntradasDialog = false },
            onNuevaEntradaClick = {
                preselectedProductForEntrada = null
                showRegistrarEntradaDialog = true
            }
        )
    }

    // Modal: Registrar Entrada
    if (showRegistrarEntradaDialog) {
        BarraRegistrarEntradaDialog(
            uiState = uiState,
            initialProduct = preselectedProductForEntrada,
            onDismiss = {
                showRegistrarEntradaDialog = false
                preselectedProductForEntrada = null
            },
            onConfirm = { product, qty, origin ->
                viewModel.recordBarraEntrada(
                    productId = product.id,
                    productName = product.name,
                    quantity = qty,
                    origin = origin,
                    onSuccess = {
                        showRegistrarEntradaDialog = false
                        preselectedProductForEntrada = null
                    }
                )
            }
        )
    }

    // Modal: Registrar Comanda de Barra (Fase 4.2)
    if (showComandaDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        BarraRegistrarComandaDialog(
            uiState = uiState,
            onDismiss = { showComandaDialog = false },
            onConfirmOrder = { tableNumber, cartItems ->
                viewModel.confirmarComandaBarra(
                    tableNumber = tableNumber,
                    cartItems = cartItems,
                    context = context,
                    onSuccess = {
                        showComandaDialog = false
                    }
                )
            }
        )
    }

    // Modal: Cobro de Comanda de Barra (Fase 4.2)
    val orderToCobrar = selectedOrderForCobro
    if (orderToCobrar != null) {
        val orderItems = remember(uiState.allOrderItems, orderToCobrar.id) {
            uiState.allOrderItems.filter { it.orderId == orderToCobrar.id }
        }
        BarraCobroDialog(
            order = orderToCobrar,
            items = orderItems,
            products = uiState.products,
            tasaUsd = uiState.generalConfig?.tasaUsd ?: 0.0,
            tasaEur = uiState.generalConfig?.tasaEur ?: 0.0,
            onDismiss = { selectedOrderForCobro = null },
            onConfirmCobro = { paymentMethod, cashReceived, changeGiven, currency, exchangeRate, amountInCurrency ->
                viewModel.cobrarComandaBarra(
                    order = orderToCobrar,
                    paymentMethod = paymentMethod,
                    cashReceived = cashReceived,
                    changeGiven = changeGiven,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    amountInCurrency = amountInCurrency,
                    onSuccess = {
                        selectedOrderForCobro = null
                    }
                )
            }
        )
    }

    // Acciones Rápidas (Calculadora y Conteo de Billetes)
    if (showQuickActions) {
        QuickActionsDialog(
            onDismiss = { showQuickActions = false }
        )
    }
}

@Composable
private fun BarraTopBar(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val activeJornada = uiState.activeJornada
    val isJornadaAbierta = activeJornada != null && activeJornada.isOpen

    Surface(
        color = ElQadreNavy,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terminal de Barra",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Dependiente: ${uiState.currentUser?.fullName ?: uiState.currentUser?.username ?: "Cantinero"}",
                        color = ElQadreGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Actualizar Catálogo / Sincronización
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreGold,
                            contentColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_actualizar_barra")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElQadreNavy,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Actualizar",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualizar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    // Logout Button
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.size(36.dp).testTag("btn_logout_barra")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Jornada State & Product Count Indicator
            val barProductsCount = remember(uiState.products) {
                uiState.products.count { it.destination == "BARRA" }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isJornadaAbierta) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                    border = BorderStroke(1.dp, if (isJornadaAbierta) Emerald600 else Rose600)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (isJornadaAbierta) Emerald500 else Rose600,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isJornadaAbierta) "JORNADA ABIERTA #${activeJornada?.id ?: 1}" else "JORNADA CERRADA",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = "$barProductsCount productos en Barra",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BarraBottomBar(
    selectedTab: BarraTab,
    onTabSelected: (BarraTab) -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = Color.White,
            contentColor = ElQadreNavy,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier.navigationBarsPadding()
        ) {
            BarraTab.values().forEach { tab ->
                val selected = selectedTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (selected) ElQadreNavy else Slate400
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) ElQadreNavy else Slate500,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFFEF3C7),
                        selectedIconColor = ElQadreNavy,
                        unselectedIconColor = Slate400,
                        selectedTextColor = ElQadreNavy,
                        unselectedTextColor = Slate500
                    ),
                    modifier = Modifier.testTag(tab.tag)
                )
            }
        }
    }
}

@Composable
private fun BarraInicioTab(
    uiState: MainUiState,
    onOpenInventario: () -> Unit,
    onOpenEntradas: () -> Unit,
    onOpenRegistrarEntrada: (Product?) -> Unit,
    onOpenRegistrarComanda: () -> Unit,
    onOpenCobro: (TableOrder) -> Unit,
    onDespacharComanda: (TableOrder) -> Unit
) {
    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id ?: 0L

    val barProducts = remember(uiState.products) {
        uiState.products.filter { it.destination == "BARRA" }
    }

    // Compute inventory numbers per product in Barra
    val computedBarraItems = remember(barProducts, uiState.stockMovements, uiState.allOrders, uiState.allOrderItems, activeJornadaId) {
        val closedOrdersIds = uiState.allOrders
            .filter { it.jornadaId == activeJornadaId && it.status == "COBRADA" }
            .map { it.id }
            .toSet()

        barProducts.map { p ->
            val entradas = uiState.stockMovements
                .filter { it.productId == p.id && it.type == "ENTRADA" && it.jornadaId == activeJornadaId }
                .sumOf { it.quantity }

            val ventas = uiState.allOrderItems
                .filter { closedOrdersIds.contains(it.orderId) && it.productId == p.id }
                .sumOf { it.quantity }

            val existenciaActual = p.stock
            val initialSnapshotMovement = uiState.stockMovements.firstOrNull {
                it.productId == p.id && it.type == "INVENTARIO_INICIAL" && it.jornadaId == activeJornadaId
            }
            val inventarioInicial = initialSnapshotMovement?.quantity ?: maxOf(0, existenciaActual - entradas + ventas)
            val subcategory = getBarraSubcategory(p)

            BarraProductItem(
                product = p,
                subcategory = subcategory,
                inventarioInicial = inventarioInicial,
                entradasJornada = entradas,
                ventasJornada = ventas,
                existenciaActual = existenciaActual
            )
        }
    }

    val totalInventarioInicial = remember(computedBarraItems) { computedBarraItems.sumOf { it.inventarioInicial } }
    val totalEntradasJornada = remember(computedBarraItems) { computedBarraItems.sumOf { it.entradasJornada } }
    val totalExistenciasActuales = remember(computedBarraItems) { computedBarraItems.sumOf { it.existenciaActual } }
    val totalTiposProductos = barProducts.size

    val totalBebidas = remember(computedBarraItems) { computedBarraItems.count { it.subcategory == "BEBIDAS" } }
    val totalConfiterias = remember(computedBarraItems) { computedBarraItems.count { it.subcategory == "CONFITERÍAS" } }

    val entradasJornadaList = remember(uiState.stockMovements, activeJornadaId, barProducts) {
        val barProductIds = barProducts.map { it.id }.toSet()
        uiState.stockMovements
            .filter { it.jornadaId == activeJornadaId && it.type == "ENTRADA" && barProductIds.contains(it.productId) }
            .sortedByDescending { it.timestamp }
    }

    // Active Comandas with Barra items for this jornada
    val barraOrders = remember(uiState.allOrders, uiState.allOrderItems, activeJornadaId) {
        uiState.allOrders.filter { order ->
            (activeJornadaId == 0L || order.jornadaId == activeJornadaId) && (
                order.waiterUsername.contains("barra", ignoreCase = true) ||
                order.customerName.contains("barra", ignoreCase = true) ||
                order.totalBarra > 0 ||
                uiState.allOrderItems.any { it.orderId == order.id && it.destination == "BARRA" }
            )
        }.sortedByDescending { it.createdAt }
    }

    val pendingBarraOrders = remember(barraOrders) {
        barraOrders.filter { it.status != "COBRADA" && it.status != "CANCELADA" }
    }

    val closedBarraOrders = remember(barraOrders) {
        barraOrders.filter { it.status == "COBRADA" }
    }

    // Closed orders metrics
    val totalVentasBarraImporte = remember(closedBarraOrders, uiState.allOrderItems) {
        val closedIds = closedBarraOrders.map { it.id }.toSet()
        uiState.allOrderItems
            .filter { closedIds.contains(it.orderId) && it.destination == "BARRA" }
            .sumOf { it.unitPrice * it.quantity }
    }
    val totalBebidasVendidasUnidades = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "BEBIDAS" }.sumOf { it.ventasJornada }
    }
    val totalConfiteriasVendidasUnidades = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.ventasJornada }
    }

    var selectedFilterCategory by remember { mutableStateOf("TODOS") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(computedBarraItems, selectedFilterCategory, searchQuery) {
        computedBarraItems.filter { item ->
            val matchesCategory = when (selectedFilterCategory) {
                "BEBIDAS" -> item.subcategory == "BEBIDAS"
                "CONFITERÍAS" -> item.subcategory == "CONFITERÍAS"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.product.name.contains(searchQuery, ignoreCase = true) ||
                    item.product.code.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Dashboard KPI Cards (Situación Inicial & Existencias)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Situación Inicial y Estado de Jornada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ElQadreNavy
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BarraKpiCard(
                        title = "Inv. Inicial",
                        value = "$totalInventarioInicial",
                        subtitle = "unidades",
                        icon = Icons.Outlined.Inventory2,
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                        testTag = "kpi_inventario_inicial"
                    )
                    BarraKpiCard(
                        title = "Entradas",
                        value = "+$totalEntradasJornada",
                        subtitle = "${entradasJornadaList.size} mov.",
                        icon = Icons.Outlined.AddCircleOutline,
                        accentColor = Emerald600,
                        modifier = Modifier.weight(1f),
                        testTag = "kpi_entradas"
                    )
                    BarraKpiCard(
                        title = "Existencias",
                        value = "$totalExistenciasActuales",
                        subtitle = "stock actual",
                        icon = Icons.Outlined.Storage,
                        accentColor = ElQadreNavy,
                        modifier = Modifier.weight(1f),
                        testTag = "kpi_existencias"
                    )
                    BarraKpiCard(
                        title = "Productos",
                        value = "$totalTiposProductos",
                        subtitle = "$totalBebidas B / $totalConfiterias C",
                        icon = Icons.Outlined.Category,
                        accentColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f),
                        testTag = "kpi_tipos_productos"
                    )
                }
            }
        }

        // 2. Realtime Sales & Financials of Barra
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.MonetizationOn, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                            Text("Ventas y Cobros de Barra (Hoy)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = Emerald50) {
                            Text(
                                "${closedBarraOrders.size} cobradas",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Total Facturado", fontSize = 9.sp, color = Slate500)
                                Text("$${"%.2f".format(totalVentasBarraImporte)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                Text("en CUP", fontSize = 8.sp, color = Slate400)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Bebidas Vendidas", fontSize = 9.sp, color = Color(0xFF1E40AF))
                                Text("$totalBebidasVendidasUnidades ud.", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                                Text("descontadas", fontSize = 8.sp, color = Color(0xFF3B82F6))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Amber50,
                            border = BorderStroke(1.dp, Amber100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Confiterías", fontSize = 9.sp, color = Amber800)
                                Text("$totalConfiteriasVendidasUnidades ud.", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Amber800)
                                Text("descontadas", fontSize = 8.sp, color = Amber700)
                            }
                        }
                    }
                }
            }
        }

        // 3. Main Comanda Action Button
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ElQadreNavy,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRegistrarComanda() }
                    .testTag("btn_registrar_comanda")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElQadreGold,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.ReceiptLong,
                                    contentDescription = "Comanda",
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "REGISTRAR COMANDA",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Nueva orden directa de Barra (Bebidas / Confiterías)",
                                fontSize = 11.sp,
                                color = ElQadreGoldLight
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                }
            }
        }

        // 4. Active Comandas (Pending Cobro) Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Comandas por Cobrar (${pendingBarraOrders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                    }
                    Text(
                        text = "Barra y Salón",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
        }

        if (pendingBarraOrders.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = Emerald600, modifier = Modifier.size(28.dp))
                        Text("No hay comandas pendientes de cobro", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)
                        Text("Registra una comanda con el botón superior", fontSize = 11.sp, color = Slate400)
                    }
                }
            }
        } else {
            items(pendingBarraOrders, key = { it.id }) { order ->
                val itemsForOrder = remember(uiState.allOrderItems, order.id) {
                    val filtered = uiState.allOrderItems.filter { it.orderId == order.id && (it.destination == "BARRA" || order.waiterUsername.contains("barra", ignoreCase = true)) }
                    if (filtered.isNotEmpty()) filtered else uiState.allOrderItems.filter { it.orderId == order.id }
                }
                val isFromSalon = !order.waiterUsername.contains("barra", ignoreCase = true) && !order.customerName.contains("barra", ignoreCase = true) && (order.tableNumber ?: 0) > 0
                val allDespachados = (itemsForOrder.isNotEmpty() && itemsForOrder.all { it.status == "DESPACHADO" || it.status == "ENTREGADO" }) || order.status == "SERVIDA" || order.status == "DESPACHADA"
                val isDespachadaOrServida = allDespachados || order.status == "SERVIDA" || order.status == "DESPACHADA"
                val totalBarraAmount = if (itemsForOrder.isNotEmpty()) itemsForOrder.sumOf { it.unitPrice * it.quantity } else order.totalAmount

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, if (isDespachadaOrServida) Emerald200 else if (isFromSalon) Color(0xFFBFDBFE) else Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("comanda_card_${order.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header: Comanda #, Origin Badge, Table #, Status Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isFromSalon) Color(0xFFDBEAFE) else Amber100
                                ) {
                                    Text(
                                        text = if (isFromSalon) "SALÓN" else "BARRA",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        color = if (isFromSalon) Color(0xFF1E40AF) else Amber800,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    "Comanda #${order.comandaNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ElQadreNavy
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isDespachadaOrServida) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFECFDF5),
                                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                                    ) {
                                        Text(
                                            text = "✓ DESPACHADA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Emerald700,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                    ) {
                                        Text(
                                            text = "PENDIENTE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if ((order.tableNumber ?: 0) > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Emerald50,
                                        border = BorderStroke(1.dp, Emerald200)
                                    ) {
                                        Text(
                                            "MESA ${order.tableNumber}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Emerald800,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFFCD34D))
                                    ) {
                                        Text(
                                            "PARA LLEVAR",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Slate100)

                        // Items
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsForOrder.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "${item.quantity}× ${item.productName}",
                                            fontSize = 11.sp,
                                            color = Slate700
                                        )
                                        if (item.status == "DESPACHADO" || item.status == "ENTREGADO" || isDespachadaOrServida) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFD1FAE5)
                                            ) {
                                                Text(
                                                    "DESPACHADO",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Emerald700,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "$${"%.2f".format(item.unitPrice * item.quantity)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Slate100)

                        // Footer: Total & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total a Cobrar:",
                                    fontSize = 9.sp,
                                    color = Slate400
                                )
                                Text(
                                    "$${"%.2f".format(if (totalBarraAmount > 0.0) totalBarraAmount else order.totalAmount)} CUP",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Emerald700
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isDespachadaOrServida) {
                                    Button(
                                        onClick = { onDespacharComanda(order) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("btn_despachar_comanda_${order.id}")
                                    ) {
                                        Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("DESPACHAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = { onOpenCobro(order) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_cobrar_comanda_${order.id}")
                                ) {
                                    Icon(Icons.Outlined.Paid, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COBRAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Operational Cards (INVENTARIO and ENTRADAS)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tarjeta INVENTARIO
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Inventory,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text("INVENTARIO", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        }

                        Button(
                            onClick = onOpenInventario,
                            modifier = Modifier.fillMaxWidth().testTag("btn_tarjeta_inventario"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEFF6FF),
                                contentColor = Color(0xFF1D4ED8)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Ver Inventario", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Tarjeta ENTRADAS
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Emerald50,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.AddCircle,
                                        contentDescription = null,
                                        tint = Emerald700,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text("ENTRADAS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        }

                        Button(
                            onClick = onOpenEntradas,
                            modifier = Modifier.fillMaxWidth().testTag("btn_tarjeta_entradas"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald50,
                                contentColor = Emerald700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Ver Entradas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarraKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BarraProductCard(
    item: BarraProductItem,
    onRegistrarEntrada: () -> Unit
) {
    val isBebida = item.subcategory == "BEBIDAS"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("producto_barra_${item.product.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Subcategory Badge, Name, Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isBebida) Color(0xFFEFF6FF) else Amber50,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBebida) Icons.Outlined.LocalBar else Icons.Outlined.Cake,
                                contentDescription = null,
                                tint = if (isBebida) Color(0xFF1D4ED8) else Amber700,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.product.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ElQadreNavy
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isBebida) Color(0xFFDBEAFE) else Amber100
                            ) {
                                Text(
                                    text = item.subcategory,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBebida) Color(0xFF1E40AF) else Amber800,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Cód: ${item.product.code}",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${"%.2f".format(item.product.price)} CUP",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Emerald700
                    )
                    Text("Precio Venta", fontSize = 9.sp, color = Slate400)
                }
            }

            HorizontalDivider(color = Slate100)

            // Row 2: Metrics (Inicial, Entradas, Ventas, Existencia Actual)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text("Inicial", fontSize = 9.sp, color = Slate400)
                            Text("${item.inventarioInicial} ud.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                        Column {
                            Text("Entradas", fontSize = 9.sp, color = Slate400)
                            Text("+${item.entradasJornada}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                        Column {
                            Text("Ventas", fontSize = 9.sp, color = Slate400)
                            Text("-${item.ventasJornada}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.existenciaActual <= item.product.minStock) Rose50 else Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, if (item.existenciaActual <= item.product.minStock) Rose200 else Emerald200)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("STOCK ACTUAL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (item.existenciaActual <= item.product.minStock) Rose700 else Emerald700)
                            Text("${item.existenciaActual} ud.", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (item.existenciaActual <= item.product.minStock) Rose800 else Emerald800)
                        }
                    }

                    IconButton(
                        onClick = onRegistrarEntrada,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Emerald600, CircleShape)
                            .testTag("btn_entrada_prod_${item.product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Entrada",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOG: INVENTARIO COMPLETO
// -------------------------------------------------------------
@Composable
private fun BarraInventarioCompletoDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onRegistrarEntrada: (Product) -> Unit
) {
    val activeJornadaId = uiState.activeJornada?.id ?: 0L
    val barProducts = remember(uiState.products) { uiState.products.filter { it.destination == "BARRA" } }

    val computedItems = remember(barProducts, uiState.stockMovements, uiState.allOrders, uiState.allOrderItems, activeJornadaId) {
        val closedOrdersIds = uiState.allOrders
            .filter { it.jornadaId == activeJornadaId && it.status == "COBRADA" }
            .map { it.id }
            .toSet()

        barProducts.map { p ->
            val entradas = uiState.stockMovements
                .filter { it.productId == p.id && it.type == "ENTRADA" && it.jornadaId == activeJornadaId }
                .sumOf { it.quantity }

            val ventas = uiState.allOrderItems
                .filter { closedOrdersIds.contains(it.orderId) && it.productId == p.id }
                .sumOf { it.quantity }

            val existenciaActual = p.stock
            val initialSnapshotMovement = uiState.stockMovements.firstOrNull {
                it.productId == p.id && it.type == "INVENTARIO_INICIAL" && it.jornadaId == activeJornadaId
            }
            val inventarioInicial = initialSnapshotMovement?.quantity ?: maxOf(0, existenciaActual - entradas + ventas)
            val subcategory = getBarraSubcategory(p)

            BarraProductItem(
                product = p,
                subcategory = subcategory,
                inventarioInicial = inventarioInicial,
                entradasJornada = entradas,
                ventasJornada = ventas,
                existenciaActual = existenciaActual
            )
        }
    }

    var selectedSubcategory by remember { mutableStateOf("TODOS") }
    var search by remember { mutableStateOf("") }

    val filtered = remember(computedItems, selectedSubcategory, search) {
        computedItems.filter { item ->
            val matchesSub = when (selectedSubcategory) {
                "BEBIDAS" -> item.subcategory == "BEBIDAS"
                "CONFITERÍAS" -> item.subcategory == "CONFITERÍAS"
                else -> true
            }
            val matchesSearch = search.isBlank() ||
                    item.product.name.contains(search, ignoreCase = true) ||
                    item.product.code.contains(search, ignoreCase = true)
            matchesSub && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = ElQadreBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = ElQadreNavy, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Inventario Completo de Barra",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "${barProducts.size} productos configurados • Jornada #${activeJornadaId}",
                                color = ElQadreGold,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }

                // Filter & Search Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Formula Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Calculate, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                            Text(
                                "EXISTENCIA ACTUAL = INVENTARIO INICIAL + ENTRADAS - VENTAS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    }

                    // Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedSubcategory == "TODOS",
                            onClick = { selectedSubcategory = "TODOS" },
                            label = { Text("TODOS (${computedItems.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedSubcategory == "BEBIDAS",
                            onClick = { selectedSubcategory = "BEBIDAS" },
                            label = { Text("BEBIDAS (${computedItems.count { it.subcategory == "BEBIDAS" }})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedSubcategory == "CONFITERÍAS",
                            onClick = { selectedSubcategory = "CONFITERÍAS" },
                            label = { Text("CONFITERÍAS (${computedItems.count { it.subcategory == "CONFITERÍAS" }})", fontSize = 11.sp) }
                        )
                    }

                    // Search input
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Filtrar por nombre o código...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                }

                // Table / List of Products
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.product.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (item.subcategory == "BEBIDAS") Color(0xFFDBEAFE) else Amber100
                                            ) {
                                                Text(
                                                    text = item.subcategory,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.subcategory == "BEBIDAS") Color(0xFF1E40AF) else Amber800,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("Cód: ${item.product.code}", fontSize = 10.sp, color = Slate500)
                                        }
                                    }

                                    Text(
                                        "$${"%.2f".format(item.product.price)} CUP",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Emerald700
                                    )
                                }

                                HorizontalDivider(color = Slate100)

                                // Detailed breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Column {
                                            Text("Inicial", fontSize = 9.sp, color = Slate400)
                                            Text("${item.inventarioInicial}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        }
                                        Column {
                                            Text("Entradas", fontSize = 9.sp, color = Slate400)
                                            Text("+${item.entradasJornada}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                                        }
                                        Column {
                                            Text("Ventas", fontSize = 9.sp, color = Slate400)
                                            Text("-${item.ventasJornada}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        }
                                        Column {
                                            Text("Existencia", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                            Text("${item.existenciaActual} ud.", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            onRegistrarEntrada(item.product)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("+ Entrada", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOG: HISTORIAL DE ENTRADAS DE LA JORNADA
// -------------------------------------------------------------
@Composable
private fun BarraEntradasHistorialDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onNuevaEntradaClick: () -> Unit
) {
    val activeJornadaId = uiState.activeJornada?.id ?: 0L
    val barProducts = remember(uiState.products) { uiState.products.filter { it.destination == "BARRA" } }
    val barProductIds = remember(barProducts) { barProducts.map { it.id }.toSet() }

    val entradasJornadaList = remember(uiState.stockMovements, activeJornadaId, barProductIds) {
        uiState.stockMovements
            .filter { it.jornadaId == activeJornadaId && it.type == "ENTRADA" && barProductIds.contains(it.productId) }
            .sortedByDescending { it.timestamp }
    }

    val totalUnidades = remember(entradasJornadaList) { entradasJornadaList.sumOf { it.quantity } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = ElQadreBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TopBar
                Surface(color = ElQadreNavy, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Entradas de Inventario de Barra",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "Jornada #${activeJornadaId} • ${entradasJornadaList.size} entradas (+$totalUnidades ud.)",
                                color = ElQadreGold,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }

                // Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Registro Cronológico",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )

                    Button(
                        onClick = {
                            onDismiss()
                            onNuevaEntradaClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_modal_nueva_entrada")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Registrar Entrada", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // List of Entries
                if (entradasJornadaList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Emerald50,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Inbox, contentDescription = null, tint = Emerald600, modifier = Modifier.size(32.dp))
                                }
                            }
                            Text(
                                "Sin entradas registradas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Slate700
                            )
                            Text(
                                "Las entradas añadidas durante esta jornada se registrarán aquí con trazabilidad completa.",
                                fontSize = 12.sp,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entradasJornadaList, key = { it.id }) { mov ->
                            val product = barProducts.find { it.id == mov.productId }
                            val subcategory = product?.let { getBarraSubcategory(it) } ?: "BARRA"

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Emerald50,
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, tint = Emerald700, modifier = Modifier.size(20.dp))
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                mov.productName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = ElQadreNavy
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (subcategory == "BEBIDAS") Color(0xFFDBEAFE) else Amber100
                                                ) {
                                                    Text(
                                                        subcategory,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (subcategory == "BEBIDAS") Color(0xFF1E40AF) else Amber800,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(
                                                    "Origen: ${mov.reason}",
                                                    fontSize = 10.sp,
                                                    color = Slate600
                                                )
                                            }
                                            Text(
                                                "Por: ${mov.recordedBy} • ${formatDateTime(mov.timestamp)}",
                                                fontSize = 10.sp,
                                                color = Slate400
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Emerald50,
                                        border = BorderStroke(1.dp, Emerald200)
                                    ) {
                                        Text(
                                            text = "+${mov.quantity} ud.",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Emerald800,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOG: REGISTRAR ENTRADA
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraRegistrarEntradaDialog(
    uiState: MainUiState,
    initialProduct: Product?,
    onDismiss: () -> Unit,
    onConfirm: (Product, Int, String) -> Unit
) {
    val barProducts = remember(uiState.products) {
        uiState.products.filter { it.destination == "BARRA" }
    }

    var selectedProduct by remember {
        mutableStateOf(initialProduct ?: barProducts.firstOrNull())
    }
    var expandedDropdown by remember { mutableStateOf(false) }
    var quantityText by remember { mutableStateOf("1") }
    var originText by remember { mutableStateOf("Almacén Central") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedQuantity = quantityText.toIntOrNull() ?: 0
    val currentStock = selectedProduct?.stock ?: 0
    val subcategory = selectedProduct?.let { getBarraSubcategory(it) } ?: "BARRA"
    val resultingStock = currentStock + parsedQuantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Emerald50,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AddCircle, contentDescription = null, tint = Emerald700, modifier = Modifier.size(20.dp))
                    }
                }
                Text("Registrar Entrada de Inventario", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Selector de Producto (Sólo productos de BARRA)
                Text("1. Seleccionar Producto (Barra):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)

                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown },
                    modifier = Modifier.fillMaxWidth().testTag("select_producto_entrada")
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: "Seleccione un producto",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        barProducts.forEach { prod ->
                            val sub = getBarraSubcategory(prod)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${prod.code} • $sub • Stock: ${prod.stock} ud.", fontSize = 11.sp, color = Slate500)
                                        }
                                        Text("$${"%.2f".format(prod.price)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Emerald700)
                                    }
                                },
                                onClick = {
                                    selectedProduct = prod
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                // 2. Información del Producto Seleccionado
                if (selectedProduct != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subcategoría:", fontSize = 11.sp, color = Slate500)
                                Text(subcategory, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (subcategory == "BEBIDAS") Color(0xFF1E40AF) else Amber800)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Precio Vigente:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(selectedProduct!!.price)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Existencia Actual:", fontSize = 11.sp, color = Slate500)
                                Text("$currentStock unidades", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                        }
                    }
                }

                // 3. Cantidad a Ingresar
                Text("2. Cantidad de Entrada:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it.filter { ch -> ch.isDigit() }
                        validationError = null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Ej. 12") },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = Emerald600) },
                    suffix = { Text("unidades", fontSize = 12.sp, color = Slate500) },
                    modifier = Modifier.fillMaxWidth().testTag("input_cantidad_entrada"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )

                // Quick Increment Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 6, 12, 24).forEach { addQty ->
                        OutlinedButton(
                            onClick = {
                                val current = quantityText.toIntOrNull() ?: 0
                                quantityText = (current + addQty).toString()
                                validationError = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Text("+$addQty", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }

                // 4. Origen / Nota
                Text("3. Origen del Suministro (Opcional):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)

                OutlinedTextField(
                    value = originText,
                    onValueChange = { originText = it },
                    placeholder = { Text("Ej. Almacén Central, Proveedor...") },
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null, tint = Slate400) },
                    modifier = Modifier.fillMaxWidth().testTag("input_origen_entrada"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )

                // Live Preview Result
                if (selectedProduct != null && parsedQuantity > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Emerald200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nueva Existencia Prevista:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text("$currentStock + $parsedQuantity = $resultingStock ud.", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Emerald700)
                        }
                    }
                }

                validationError?.let { err ->
                    Text(err, color = Rose600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = selectedProduct
                    if (prod == null) {
                        validationError = "Debe seleccionar un producto de Barra"
                        return@Button
                    }
                    if (parsedQuantity <= 0) {
                        validationError = "La cantidad debe ser mayor a 0"
                        return@Button
                    }
                    onConfirm(prod, parsedQuantity, originText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirmar_entrada")
            ) {
                Text("Confirmar Entrada", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar", color = Slate600)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

// -------------------------------------------------------------
// PLACEHOLDER TAB PARA OTRAS SECCIONES (FUTURAS SUBFASES)
// -------------------------------------------------------------
@Composable
private fun BarraPlaceholderTab(
    title: String,
    description: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElQadreGoldSoft,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(28.dp))
                    }
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Text(
                        text = "Subfase 4.1 Completada: Inicio, Inventario y Entradas funcionales.",
                        fontSize = 11.sp,
                        color = Slate500,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
