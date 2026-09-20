package com.example.ui.screens.salon

import androidx.activity.compose.BackHandler

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Jornada
import com.example.data.local.model.OrderItem
import com.example.data.local.model.Product
import com.example.data.local.model.TableOrder
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SalonCartItem
import com.example.util.SalonBackupManager
import com.example.util.SalonReportPdfExporter
import com.example.util.SalonTransferConfig
import com.example.util.OrderNotificationManager
import com.example.ui.screens.cajero.QuickActionsDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Calculate
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SalonTab(val title: String, val icon: ImageVector, val tag: String) {
    INICIO("Inicio", Icons.Outlined.GridView, "salon_tab_inicio"),
    HISTORIAL("Historial", Icons.AutoMirrored.Outlined.ReceiptLong, "salon_tab_historial"),
    CIERRE("Cierre", Icons.Outlined.Lock, "salon_tab_cierre"),
    AJUSTES("Ajustes", Icons.Outlined.Settings, "salon_tab_ajustes")
}

@Composable
fun SalonScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(SalonTab.INICIO) }
    var selectedTableForDetail by remember { mutableStateOf<Int?>(null) }
    var showTableDetailDialog by remember { mutableStateOf(false) }

    var tableForNewComanda by remember { mutableStateOf<Int?>(null) }
    var showNewComandaDialog by remember { mutableStateOf(false) }

    var tableForCobro by remember { mutableStateOf<Int?>(null) }
    var showCobroDialog by remember { mutableStateOf(false) }

    var orderForIndividualCobro by remember { mutableStateOf<TableOrder?>(null) }
    var existingOrderForComanda by remember { mutableStateOf<TableOrder?>(null) }

    var showCatalogDialog by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }

    val isAnySalonModalOpen = showTableDetailDialog ||
            selectedTableForDetail != null ||
            showNewComandaDialog ||
            tableForNewComanda != null ||
            showCobroDialog ||
            tableForCobro != null ||
            orderForIndividualCobro != null ||
            existingOrderForComanda != null ||
            showCatalogDialog ||
            showQuickActions

    BackHandler(enabled = isAnySalonModalOpen || selectedTab != SalonTab.INICIO) {
        when {
            showTableDetailDialog -> showTableDetailDialog = false
            selectedTableForDetail != null -> selectedTableForDetail = null
            showNewComandaDialog -> showNewComandaDialog = false
            tableForNewComanda != null -> tableForNewComanda = null
            showCobroDialog -> showCobroDialog = false
            tableForCobro != null -> tableForCobro = null
            orderForIndividualCobro != null -> orderForIndividualCobro = null
            existingOrderForComanda != null -> existingOrderForComanda = null
            showCatalogDialog -> showCatalogDialog = false
            showQuickActions -> showQuickActions = false
            selectedTab != SalonTab.INICIO -> selectedTab = SalonTab.INICIO
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Permissions request for SEND_SMS and POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { /* handled */ }
    )
    LaunchedEffect(Unit) {
        val neededPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    // 1. Notificación al pasar a estado SERVIDA
    val servedOrders = remember(uiState.openOrders, uiState.allOrders) {
        val orders = if (uiState.openOrders.isNotEmpty()) uiState.openOrders else uiState.allOrders.filter { it.status != "COBRADA" && it.status != "CANCELADA" }
        orders.filter { it.status == "SERVIDA" }
    }
    var knownServedOrderIds by remember { mutableStateOf<Set<Long>?>(null) }

    LaunchedEffect(servedOrders) {
        val currentServedIds = servedOrders.map { it.id }.toSet()
        val previous = knownServedOrderIds
        if (previous == null) {
            // First composition: register existing served orders without re-notifying them
            knownServedOrderIds = currentServedIds
        } else {
            val newlyServedOrders = servedOrders.filter { it.id !in previous }
            newlyServedOrders.forEach { order ->
                OrderNotificationManager.notifyComandaServida(context, order)
            }
            knownServedOrderIds = currentServedIds
        }
    }

    // 2. Recordatorio de Cobro: Cuando existan 2 o más comandas SERVIDAS y no cobradas, cada 10 minutos
    LaunchedEffect(servedOrders.size >= 2) {
        if (servedOrders.size >= 2) {
            while (isActive) {
                delay(10 * 60 * 1000L) // 10 minutos
                val activeOrders = if (uiState.openOrders.isNotEmpty()) uiState.openOrders else uiState.allOrders.filter { it.status != "COBRADA" && it.status != "CANCELADA" }
                val currentServedCount = activeOrders.count { it.status == "SERVIDA" }
                if (currentServedCount >= 2) {
                    OrderNotificationManager.notifyRecordatorioCobro(context, currentServedCount)
                }
            }
        }
    }

    // Display messages if present
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElQadreBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SalonTopBar(
                uiState = uiState,
                onRefreshCatalog = { viewModel.refreshCatalog() },
                onLogout = onLogout
            )
        },
        bottomBar = {
            SalonBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        floatingActionButton = {
            if (selectedTab == SalonTab.INICIO) {
                FloatingActionButton(
                    onClick = { showQuickActions = true },
                    containerColor = ElQadreGold,
                    contentColor = ElQadreNavy,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("btn_acciones_rapidas_salon")
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
                SalonTab.INICIO -> {
                    SalonInicioTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onTableClick = { tableNum ->
                            selectedTableForDetail = tableNum
                            showTableDetailDialog = true
                        },
                        onParaLlevarClick = {
                            tableForNewComanda = null
                            showNewComandaDialog = true
                        },
                        onParaLlevarDetailClick = {
                            selectedTableForDetail = null
                            showTableDetailDialog = true
                        },
                        onOpenCatalog = { showCatalogDialog = true }
                    )
                }
                SalonTab.HISTORIAL -> {
                    SalonHistorialTab(uiState = uiState)
                }
                SalonTab.CIERRE -> {
                    SalonCierreTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToInicio = { selectedTab = SalonTab.INICIO }
                    )
                }
                SalonTab.AJUSTES -> {
                    SalonAjustesTab(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Modal Details Sheet for Selected Table
    if (showTableDetailDialog) {
        MesaDetailDialog(
            tableNum = selectedTableForDetail,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showTableDetailDialog = false },
            onAgregarPedido = { orderToUpdate ->
                tableForNewComanda = selectedTableForDetail
                existingOrderForComanda = orderToUpdate
                showNewComandaDialog = true
            },
            onCobrarMesa = {
                tableForCobro = selectedTableForDetail
                showCobroDialog = true
            },
            onCobrarIndividual = { order ->
                orderForIndividualCobro = order
            }
        )
    }

    // Modal to create/add a comanda for a table
    if (showNewComandaDialog) {
        NuevaComandaDialog(
            tableNum = tableForNewComanda,
            uiState = uiState,
            viewModel = viewModel,
            existingOrder = existingOrderForComanda,
            onDismiss = {
                showNewComandaDialog = false
                existingOrderForComanda = null
            },
            onComandaConfirmed = {
                showNewComandaDialog = false
                existingOrderForComanda = null
                selectedTableForDetail = tableForNewComanda // Refresh table view
                showTableDetailDialog = true
            }
        )
    }

    // Modal to process payment for an occupied table
    if (showCobroDialog) {
        CobrarMesaDialog(
            tableNum = tableForCobro,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showCobroDialog = false },
            onCobroComplete = {
                showCobroDialog = false
                showTableDetailDialog = false // Close table detail as table is now LIBRE
            }
        )
    }

    // Modal to process payment for an individual comanda
    orderForIndividualCobro?.let { order ->
        CobrarComandaIndividualDialog(
            order = order,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { orderForIndividualCobro = null },
            onCobroComplete = {
                orderForIndividualCobro = null
            }
        )
    }

    // Unified Catalog Dialog (View Only)
    if (showCatalogDialog) {
        UnifiedCatalogDialog(
            uiState = uiState,
            onDismiss = { showCatalogDialog = false }
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
private fun SalonTopBar(
    uiState: MainUiState,
    onRefreshCatalog: () -> Unit,
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
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Panel de Salón",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Dependiente: ${uiState.currentUser?.fullName ?: uiState.currentUser?.username ?: "Salón"}",
                            color = ElQadreGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón "Actualizar" Catálogo
                    Button(
                        onClick = onRefreshCatalog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreGold,
                            contentColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_actualizar_catalogo")
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
                                contentDescription = "Actualizar Catálogo",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Actualizar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Logout Button
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.size(36.dp)
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

            // Estado de la Jornada Badge
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
                            text = if (isJornadaAbierta) "JORNADA ABIERTA" else "JORNADA CERRADA",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = "${uiState.products.size} productos listos en catálogo",
                    color = Slate300,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SalonBottomBar(
    selectedTab: SalonTab,
    onTabSelected: (SalonTab) -> Unit
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
            SalonTab.values().forEach { tab ->
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
private fun SalonInicioTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onTableClick: (Int) -> Unit,
    onParaLlevarClick: () -> Unit,
    onParaLlevarDetailClick: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    val tableCount = uiState.salonTableCount
    val tables = remember(tableCount) { (1..tableCount).toList() }

    val activeParaLlevarOrders = remember(uiState.openOrders) {
        uiState.openOrders.filter {
            it.tableNumber == null && it.status != "COBRADA" && it.status != "CANCELADA"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gestión de Mesas",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "$tableCount mesas operativas en el turno",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onOpenCatalog,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ElQadreNavy),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ver Catálogo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botón "PARA LLEVAR"
        Button(
            onClick = onParaLlevarClick,
            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_para_llevar"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PARA LLEVAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (activeParaLlevarOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            // Active Para Llevar Card/Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF), // Soft blue
                border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onParaLlevarDetailClick() }
                    .testTag("para_llevar_active_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8)
                        )
                        Column {
                            Text(
                                text = "Comandas Para Llevar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                text = "${activeParaLlevarOrders.size} comanda(s) activa(s)",
                                fontSize = 11.sp,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }
                    Text(
                        text = "VER PEDIDOS ➡️",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grid of Tables
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(tables) { tableNum ->
                val activeOrdersForTable = uiState.openOrders.filter {
                    it.tableNumber == tableNum && it.status != "COBRADA" && it.status != "CANCELADA"
                }
                val isOccupied = activeOrdersForTable.isNotEmpty()
                val totalTableAmount = activeOrdersForTable.sumOf { it.totalAmount }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isOccupied) Color(0xFFFFFBEB) else Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(
                        width = if (isOccupied) 2.dp else 1.dp,
                        color = if (isOccupied) Color(0xFFD97706) else Slate200
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clickable { onTableClick(tableNum) }
                        .testTag("table_card_$tableNum")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TableRestaurant,
                                contentDescription = null,
                                tint = if (isOccupied) Color(0xFFD97706) else Slate400,
                                modifier = Modifier.size(24.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOccupied) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                            ) {
                                Text(
                                    text = if (isOccupied) "OCUPADA" else "LIBRE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isOccupied) Color(0xFFB45309) else Emerald700,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Mesa $tableNum",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ElQadreNavy
                            )

                            if (isOccupied) {
                                Text(
                                    text = "$${"%.2f".format(totalTableAmount)} CUP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                                Text(
                                    text = "${activeOrdersForTable.size} comanda(s)",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            } else {
                                Text(
                                    text = "Toca para abrir",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Reusable Responsive Dialog Wrapper preventing navigation bar cutoffs
@Composable
fun SalonResponsiveDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = ElQadreNavy,
    badgeText: String? = null,
    badgeColor: Color = Color(0xFFFEF3C7),
    badgeTextColor: Color = Color(0xFFB45309),
    showCloseButton: Boolean = true,
    maxWidth: androidx.compose.ui.unit.Dp = 640.dp,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 16.dp)
                .padding(top = 28.dp, bottom = 44.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = maxHeight
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                ) {
                    // Fixed Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = ElQadreNavy
                                )
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (badgeText != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeColor
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (showCloseButton) {
                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Slate600,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // Scrollable Central Content
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }

                    HorizontalDivider(color = Slate200)

                    // Fixed Action Buttons Footer
                    Surface(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}

// Mesa Detail Sheet showing active comandas and actions
@Composable
private fun MesaDetailDialog(
    tableNum: Int?,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onAgregarPedido: (TableOrder?) -> Unit,
    onCobrarMesa: () -> Unit,
    onCobrarIndividual: (TableOrder) -> Unit
) {
    val context = LocalContext.current
    val activeOrders = remember(uiState.openOrders, tableNum) {
        uiState.openOrders.filter {
            it.tableNumber == tableNum && it.status != "COBRADA" && it.status != "CANCELADA"
        }.sortedBy { it.createdAt }
    }
    val isOccupied = activeOrders.isNotEmpty()
    val allServed = isOccupied && activeOrders.all { it.status == "SERVIDA" }
    val totalTableAmount = activeOrders.sumOf { it.totalAmount }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = if (tableNum == null) "Para Llevar" else "Mesa $tableNum",
        icon = if (tableNum == null) Icons.Outlined.ShoppingBag else Icons.Outlined.TableRestaurant,
        badgeText = if (isOccupied) "PENDIENTES (${activeOrders.size})" else "SIN PEDIDOS",
        badgeColor = if (isOccupied) Color(0xFFFEF3C7) else Color(0xFFD1FAE5),
        badgeTextColor = if (isOccupied) Color(0xFFB45309) else Emerald700,
        buttons = {
            if (!isOccupied) {
                Button(
                    onClick = { onAgregarPedido(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CREAR COMANDA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onAgregarPedido(null) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, ElQadreNavy),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AÑADIR PEDIDO", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onCobrarMesa,
                    enabled = allServed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald600,
                        disabledContainerColor = Slate200,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Outlined.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (tableNum == null) "COBRAR PEDIDOS" else "COBRAR MESA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("CERRAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) {
        if (!isOccupied) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (tableNum == null) Icons.Outlined.ShoppingBag else Icons.Outlined.TableRestaurant,
                        contentDescription = null,
                        tint = Emerald600,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (tableNum == null) "Sin pedidos para llevar" else "Mesa Libre",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = if (tableNum == null) "No hay comandas para llevar activas en este momento." else "Esta mesa no tiene clientes activos. Toca 'CREAR COMANDA' para iniciar el servicio.",
                        fontSize = 12.sp,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = if (tableNum == null) "Comandas Para Llevar (${activeOrders.size}):" else "Comandas de la Mesa (${activeOrders.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = ElQadreNavy
            )

            activeOrders.forEach { order ->
                val itemsForOrder = remember(uiState.allOrderItems, order.id) {
                    uiState.allOrderItems.filter { it.orderId == order.id }
                }

                ComandaCard(
                    order = order,
                    items = itemsForOrder,
                    onServir = { viewModel.servirComandaSalon(order) },
                    onCobrarIndividual = { onCobrarIndividual(order) },
                    onSumarAComanda = { onAgregarPedido(order) },
                    onReenviarSms = { viewModel.reenviarSmsComanda(order, context) }
                )
            }

            HorizontalDivider(color = Slate200)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tableNum == null) "TOTAL ACUMULADO:" else "TOTAL ACUMULADO MESA:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate600
                )
                Text(
                    text = "$${"%.2f".format(totalTableAmount)} CUP",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Emerald700
                )
            }

            if (!allServed) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Text(
                        text = if (tableNum == null) "⚠️ Para cobrar, todas las comandas de Para Llevar deben estar en estado SERVIDA." else "⚠️ Para cobrar la mesa, todas las comandas deben estar en estado SERVIDA.",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// Single Comanda Card inside Table Detail
@Composable
private fun ComandaCard(
    order: TableOrder,
    items: List<OrderItem>,
    onServir: () -> Unit,
    onCobrarIndividual: () -> Unit,
    onSumarAComanda: () -> Unit,
    onReenviarSms: () -> Unit = {}
) {
    val isPending = order.status == "PENDIENTE_SERVIR"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isPending) Color(0xFFFDE68A) else Emerald200),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Comanda Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreNavy
                    ) {
                        Text(
                            text = "#${order.comandaNumber}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "Comanda #${order.comandaNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ElQadreNavy
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onReenviarSms,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Reenviar SMS",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onSumarAComanda,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ElQadreNavy),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("SUMAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPending) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                    ) {
                        Text(
                            text = if (isPending) "PENDIENTE DE SERVIR" else "SERVIDA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isPending) Color(0xFFB45309) else Emerald700,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Items List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity}x ${item.productName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ElQadreNavy
                            )
                            if (item.notes.isNotEmpty()) {
                                Text(
                                    text = item.notes,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            text = "$${"%.2f".format(item.unitPrice * item.quantity)} CUP",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Service Timer & Servir Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPending) {
                    ComandaTimerText(confirmedAt = order.confirmedAt ?: order.createdAt)

                    Button(
                        onClick = onServir,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_servir_comanda_${order.id}")
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Servir en mesa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Servida | Tiempo: ${formatDurationSeconds(order.serviceDurationSeconds)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald700
                        )
                    }

                    Text(
                        text = "$${"%.2f".format(order.totalAmount)} CUP",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = ElQadreNavy
                    )
                }
            }

            if (!isPending) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCobrarIndividual,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_cobrar_individual_${order.id}")
                ) {
                    Icon(Icons.Outlined.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COBRAR COMANDA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Live Timer Component for pending comandas
@Composable
private fun ComandaTimerText(confirmedAt: Long) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val elapsedMillis = maxOf(0L, currentTime - confirmedAt)
    val totalSeconds = elapsedMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formatted = "%02d:%02d".format(minutes, seconds)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = null,
            tint = Rose600,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Tiempo: $formatted",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Rose600
        )
    }
}

private fun formatDurationSeconds(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

// Dialog to create a new comanda for a table
@Composable
private fun NuevaComandaDialog(
    tableNum: Int?,
    uiState: MainUiState,
    viewModel: MainViewModel,
    existingOrder: TableOrder? = null,
    onDismiss: () -> Unit,
    onComandaConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val cartItems = remember { mutableStateListOf<SalonCartItem>() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var productToCustomize by remember { mutableStateOf<Product?>(null) }
    var showConfirmationModal by remember { mutableStateOf(false) }

    val categories = remember(uiState.products) {
        listOf("Todos") + uiState.products.map { it.category }.distinct()
    }

    val filteredProducts = remember(uiState.products, searchQuery, selectedCategory) {
        uiState.products.filter { p ->
            p.isAvailable &&
            (selectedCategory == "Todos" || p.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isEmpty() || p.name.contains(searchQuery, ignoreCase = true) || p.code.contains(searchQuery, ignoreCase = true))
        }
    }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = if (existingOrder != null) {
            "Sumar a Comanda #${existingOrder.comandaNumber} - ${if (tableNum == null) "Para Llevar" else "Mesa $tableNum"}"
        } else if (tableNum == null) {
            "Nueva Comanda - Para Llevar"
        } else {
            "Nueva Comanda - Mesa $tableNum"
        },
        subtitle = if (existingOrder != null) "Agregando productos a la comanda existente" else null,
        icon = if (tableNum == null) Icons.Outlined.ShoppingBag else Icons.Outlined.TableRestaurant,
        badgeText = if (cartItems.isNotEmpty()) "${cartItems.sumOf { it.quantity }} ítems" else null,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { showConfirmationModal = true },
                enabled = cartItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VERIFICAR Y CONFIRMAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar producto...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElQadreNavy,
                unfocusedBorderColor = Slate200
            )
        )

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(5).forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Available Products List
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            filteredProducts.forEach { product ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { productToCustomize = product }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                            Text(
                                text = "${product.category} • Stock: ${product.stock}",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "$${"%.2f".format(product.price)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Emerald700
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElQadreNavy
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Agregar",
                                    tint = Color.White,
                                    modifier = Modifier.padding(6.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cart Summary Section
        if (cartItems.isNotEmpty()) {
            HorizontalDivider(color = Slate200)

            Text(
                text = "Productos en la Comanda (${cartItems.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElQadreNavy
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cartItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.quantity}x ${item.product.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (item.agregadosNoteString.isNotEmpty()) {
                                Text(item.agregadosNoteString, fontSize = 11.sp, color = Color(0xFFD97706))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$${"%.2f".format(item.totalItemPrice)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(
                                onClick = { cartItems.remove(item) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOTAL COMANDA:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Text("$${"%.2f".format(cartItems.sumOf { it.totalItemPrice })} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
            }
        }
    }

    // Customize Product with Agregados Dialog
    productToCustomize?.let { product ->
        PersonalizarProductoDialog(
            product = product,
            onDismiss = { productToCustomize = null },
            onAddCartItem = { cartItem ->
                cartItems.add(cartItem)
                productToCustomize = null
            }
        )
    }

    // Confirmation Summary Dialog before saving
    if (showConfirmationModal) {
        SalonResponsiveDialog(
            onDismissRequest = { showConfirmationModal = false },
            title = if (existingOrder != null) {
                "Confirmar Productos a Sumar - Comanda #${existingOrder.comandaNumber}"
            } else if (tableNum == null) {
                "Confirmar Comanda - Para Llevar"
            } else {
                "Confirmar Comanda - Mesa $tableNum"
            },
            icon = Icons.Outlined.AssignmentTurnedIn,
            buttons = {
                TextButton(
                    onClick = { showConfirmationModal = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("VOLVER", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        viewModel.confirmarComandaSalon(
                            tableNumber = tableNum,
                            cartItems = cartItems,
                            existingOrder = existingOrder,
                            context = context,
                            onComplete = {
                                showConfirmationModal = false
                                onComandaConfirmed()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CONFIRMAR Y ENVIAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text("Verifica el pedido con el cliente antes de confirmar:", fontSize = 13.sp, color = Slate600)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cartItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.quantity}x ${item.product.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (item.agregadosNoteString.isNotEmpty()) {
                                    Text(item.agregadosNoteString, fontSize = 11.sp, color = Color(0xFFD97706))
                                }
                            }
                            Text("$${"%.2f".format(item.totalItemPrice)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = Slate200)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (existingOrder != null) "TOTAL ADICIONAL:" else "TOTAL:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Text("$${"%.2f".format(cartItems.sumOf { it.totalItemPrice })} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
            }

            Text(
                text = if (existingOrder != null) "ℹ️ Al confirmar, se actualizará la Comanda #${existingOrder.comandaNumber} en Caja por SMS con la lista completa de productos." else "ℹ️ Al confirmar, la comanda se enviará automáticamente a las áreas de Cocina y Barra y comenzará su tiempo de servicio.",
                fontSize = 11.sp,
                color = Slate500
            )
        }
    }
}

// Dialog to customize product with Agregados / Complements
@Composable
private fun PersonalizarProductoDialog(
    product: Product,
    onDismiss: () -> Unit,
    onAddCartItem: (SalonCartItem) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    var customNotes by remember { mutableStateOf("") }
    val selectedAgregados = remember { mutableStateListOf<Pair<String, Double>>() }

    val isCocina = product.destination == "COCINA" || product.category.contains("Cocina", ignoreCase = true) || product.category.contains("Postres", ignoreCase = true)
    val isBarra = product.destination == "BARRA" || product.category.contains("Bebidas", ignoreCase = true)

    val agregadosDisponibles = remember(product.admitsAgregados, product.agregadosList) {
        if (!product.admitsAgregados) emptyList() else {
            try {
                val arr = org.json.JSONArray(product.agregadosList)
                val list = mutableListOf<Pair<String, Double>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Pair(obj.getString("name"), obj.getDouble("price")))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val agregadosPriceSum = selectedAgregados.sumOf { it.second }
    val unitPriceWithAgregados = product.price + agregadosPriceSum
    val totalPrice = unitPriceWithAgregados * quantity

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = product.name,
        subtitle = "Precio base: $${"%.2f".format(product.price)} CUP",
        icon = Icons.Outlined.RestaurantMenu,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onAddCartItem(
                        SalonCartItem(
                            product = product,
                            quantity = quantity,
                            selectedAgregados = selectedAgregados.toList(),
                            customNotes = customNotes
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("AGREGAR AL PEDIDO", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        if (agregadosDisponibles.isNotEmpty()) {
            Text("Agregados / Complementos disponibles:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                agregadosDisponibles.forEach { agreg ->
                    val isChecked = selectedAgregados.any { it.first == agreg.first }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isChecked) Color(0xFFFEF3C7) else Slate50,
                        border = BorderStroke(1.dp, if (isChecked) Color(0xFFFDE68A) else Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) {
                                    selectedAgregados.removeAll { it.first == agreg.first }
                                } else {
                                    selectedAgregados.add(agreg)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedAgregados.add(agreg)
                                        else selectedAgregados.removeAll { it.first == agreg.first }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                )
                                Text(agreg.first, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("+$${"%.2f".format(agreg.second)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                    }
                }
            }
        }

        // Custom Notes Field
        OutlinedTextField(
            value = customNotes,
            onValueChange = { customNotes = it },
            placeholder = { Text("Nota especial (ej: Sin cebolla)", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(10.dp)
        )

        // Quantity Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cantidad:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedIconButton(
                    onClick = { if (quantity > 1) quantity-- },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text("$quantity", fontWeight = FontWeight.Black, fontSize = 18.sp)
                OutlinedIconButton(
                    onClick = { quantity++ },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }

        HorizontalDivider(color = Slate200)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PRECIO FINAL:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
            Text("$${"%.2f".format(totalPrice)} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
        }
    }
}

// Dialog to process payment for all active comandas on a table
@Composable
private fun CobrarMesaDialog(
    tableNum: Int?,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onCobroComplete: () -> Unit
) {
    val context = LocalContext.current
    val activeOrders = remember(uiState.openOrders, tableNum) {
        uiState.openOrders.filter {
            it.tableNumber == tableNum && it.status != "COBRADA" && it.status != "CANCELADA"
        }
    }
    val activeOrderIds = remember(activeOrders) { activeOrders.map { it.id }.toSet() }
    val mesaItems = remember(uiState.allOrderItems, activeOrderIds) {
        uiState.allOrderItems.filter { it.orderId in activeOrderIds }
    }
    val totalMesaAmount = remember(activeOrders, mesaItems) {
        val sumOrders = activeOrders.sumOf { it.totalAmount }
        if (sumOrders > 0.0) sumOrders else mesaItems.sumOf { it.unitPrice * it.quantity }
    }

    val transferBreakdown = remember(mesaItems, uiState.products, totalMesaAmount) {
        SalonTransferConfig.calculatePaymentBreakdown(
            context = context,
            items = mesaItems,
            products = uiState.products,
            fallbackTotal = totalMesaAmount
        ) { prodId -> uiState.productosElaborados.any { it.productId == prodId } }
    }

    var selectedPaymentMethod by remember { mutableStateOf("EFECTIVO") }
    var cashReceivedText by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("CUP") }
    val tasaUsd = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEur = uiState.generalConfig?.tasaEur ?: 0.0

    val activeRate = when (selectedCurrency) {
        "USD" -> if (tasaUsd > 0) tasaUsd else 1.0
        "EUR" -> if (tasaEur > 0) tasaEur else 1.0
        else -> 1.0
    }
    val totalMesaAmountInCurrency = totalMesaAmount / activeRate

    val calculatedTransferCUP = when (selectedPaymentMethod) {
        "EFECTIVO" -> 0.0
        "TRANSFERENCIA" -> transferBreakdown.transferAmount
        "MIXTO" -> transferBreakdown.transferAmount
        else -> 0.0
    }
    val calculatedCashCUP = when (selectedPaymentMethod) {
        "EFECTIVO" -> totalMesaAmount
        "TRANSFERENCIA" -> transferBreakdown.cashAmount
        "MIXTO" -> transferBreakdown.cashAmount
        else -> totalMesaAmount
    }
    val calculatedTransferInCurrency = calculatedTransferCUP / activeRate
    val calculatedCashInCurrency = calculatedCashCUP / activeRate

    val cashReceivedInCurrency = cashReceivedText.toDoubleOrNull() ?: 0.0
    val changeGivenInCurrency = maxOf(0.0, cashReceivedInCurrency - (if (selectedPaymentMethod == "EFECTIVO") totalMesaAmountInCurrency else calculatedCashInCurrency))

    val finalExchangeRate = when (selectedCurrency) {
        "USD" -> if (tasaUsd > 0) tasaUsd else 1.0
        "EUR" -> if (tasaEur > 0) tasaEur else 1.0
        else -> 1.0
    }
    val finalAmountInCurrency = totalMesaAmount / finalExchangeRate
    val enteredCash = cashReceivedText.toDoubleOrNull() ?: 0.0
    val persistenceCash = enteredCash * finalExchangeRate
    val requiredCash = if (selectedPaymentMethod == "EFECTIVO") totalMesaAmount else calculatedCashCUP
    val persistenceChange = maxOf(0.0, persistenceCash - requiredCash)
    val effectivePaymentMethod = when (selectedPaymentMethod) {
        "TRANSFERENCIA" -> if (calculatedCashCUP > 0.0) "MIXTO" else "TRANSFERENCIA"
        "MIXTO" -> "MIXTO"
        else -> "EFECTIVO"
    }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = if (tableNum == null) "Cobro de Pedido - Para Llevar" else "Cobro de Mesa $tableNum",
        icon = Icons.Outlined.PointOfSale,
        iconTint = Emerald600,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.cobrarMesaSalon(
                        tableNumber = tableNum,
                        paymentMethod = effectivePaymentMethod,
                        cashReceived = if (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP == 0.0) 0.0 else persistenceCash,
                        changeGiven = if (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP == 0.0) 0.0 else persistenceChange,
                        currency = selectedCurrency,
                        exchangeRate = finalExchangeRate,
                        amountInCurrency = finalAmountInCurrency,
                        onComplete = onCobroComplete
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CONFIRMAR Y COBRAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFECFDF5),
            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (tableNum == null) "Monto Total de los Pedidos:" else "Monto Total de la Mesa:",
                    fontSize = 13.sp,
                    color = Emerald800,
                    fontWeight = FontWeight.Bold
                )
                Text("$${"%.2f".format(totalMesaAmount)} CUP", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Emerald700)
                Text("Comandas a cerrar: ${activeOrders.map { "#${it.comandaNumber}" }.joinToString(", ")}", fontSize = 11.sp, color = Slate600)
            }
        }

        Text("Método de Pago:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("EFECTIVO", "TRANSFERENCIA", "MIXTO").forEach { method ->
                FilterChip(
                    selected = selectedPaymentMethod == method,
                    onClick = { selectedPaymentMethod = method },
                    label = { Text(method, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Moneda de la Operación:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("CUP", "USD", "EUR").forEach { curr ->
                FilterChip(
                    selected = selectedCurrency == curr,
                    onClick = { selectedCurrency = curr },
                    label = { Text(curr, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedCurrency != "CUP") {
            val rateText = when (selectedCurrency) {
                "USD" -> "1 USD = $tasaUsd CUP"
                "EUR" -> "1 EUR = $tasaEur CUP"
                else -> ""
            }
            Text(
                text = "Tasa de cambio: $rateText (Monto en $selectedCurrency: ${"%.2f".format(totalMesaAmountInCurrency)})",
                fontSize = 12.sp,
                color = Slate500,
                fontWeight = FontWeight.Medium
            )
        }

        if (selectedPaymentMethod == "TRANSFERENCIA" || selectedPaymentMethod == "MIXTO") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (transferBreakdown.isLimited) Color(0xFFFFFBEB) else Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, if (transferBreakdown.isLimited) Color(0xFFFDE68A) else Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRANSFERENCIA:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Text("$${"%.2f".format(calculatedTransferInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = ElQadreNavy)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EFECTIVO:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (calculatedCashCUP > 0.0) Amber700 else Slate600)
                        Text("$${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (calculatedCashCUP > 0.0) Amber700 else Slate600)
                    }
                    if (transferBreakdown.isLimited && calculatedCashCUP > 0.0) {
                        Text(
                            text = "⚠️ La comanda supera las unidades permitidas por transferencia. El resto ($${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency) debe abonarse en EFECTIVO.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (selectedPaymentMethod == "EFECTIVO" || (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP > 0.0) || selectedPaymentMethod == "MIXTO") {
            val cashNeededText = if (selectedPaymentMethod == "EFECTIVO") {
                "Efectivo Recibido ($selectedCurrency)"
            } else {
                "Efectivo Recibido para el resto ($${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency)"
            }

            OutlinedTextField(
                value = cashReceivedText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                        cashReceivedText = input
                    }
                },
                placeholder = { Text(cashNeededText, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(10.dp)
            )

            if (cashReceivedInCurrency > 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cambio a Entregar:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${"%.2f".format(changeGivenInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Rose600)
                    }
                }
            }
        }
    }
}

// Dialog to process payment for a single, individual comanda
@Composable
private fun CobrarComandaIndividualDialog(
    order: TableOrder,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onCobroComplete: () -> Unit
) {
    val context = LocalContext.current
    val orderItems = remember(uiState.allOrderItems, order.id) {
        uiState.allOrderItems.filter { it.orderId == order.id }
    }
    val totalAmount = remember(order, orderItems) {
        if (order.totalAmount > 0.0) order.totalAmount else orderItems.sumOf { it.unitPrice * it.quantity }
    }

    val transferBreakdown = remember(orderItems, uiState.products, totalAmount) {
        SalonTransferConfig.calculatePaymentBreakdown(
            context = context,
            items = orderItems,
            products = uiState.products,
            fallbackTotal = totalAmount
        ) { prodId -> uiState.productosElaborados.any { it.productId == prodId } }
    }

    var selectedPaymentMethod by remember { mutableStateOf("EFECTIVO") }
    var cashReceivedText by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("CUP") }
    val tasaUsd = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEur = uiState.generalConfig?.tasaEur ?: 0.0

    val activeRate = when (selectedCurrency) {
        "USD" -> if (tasaUsd > 0) tasaUsd else 1.0
        "EUR" -> if (tasaEur > 0) tasaEur else 1.0
        else -> 1.0
    }
    val totalAmountInCurrency = totalAmount / activeRate

    val calculatedTransferCUP = when (selectedPaymentMethod) {
        "EFECTIVO" -> 0.0
        "TRANSFERENCIA" -> transferBreakdown.transferAmount
        "MIXTO" -> transferBreakdown.transferAmount
        else -> 0.0
    }
    val calculatedCashCUP = when (selectedPaymentMethod) {
        "EFECTIVO" -> totalAmount
        "TRANSFERENCIA" -> transferBreakdown.cashAmount
        "MIXTO" -> transferBreakdown.cashAmount
        else -> totalAmount
    }
    val calculatedTransferInCurrency = calculatedTransferCUP / activeRate
    val calculatedCashInCurrency = calculatedCashCUP / activeRate

    val cashReceivedInCurrency = cashReceivedText.toDoubleOrNull() ?: 0.0
    val changeGivenInCurrency = maxOf(0.0, cashReceivedInCurrency - (if (selectedPaymentMethod == "EFECTIVO") totalAmountInCurrency else calculatedCashInCurrency))

    val finalExchangeRate = when (selectedCurrency) {
        "USD" -> if (tasaUsd > 0) tasaUsd else 1.0
        "EUR" -> if (tasaEur > 0) tasaEur else 1.0
        else -> 1.0
    }
    val finalAmountInCurrency = totalAmount / finalExchangeRate
    val enteredCash = cashReceivedText.toDoubleOrNull() ?: 0.0
    val persistenceCash = enteredCash * finalExchangeRate
    val requiredCash = if (selectedPaymentMethod == "EFECTIVO") totalAmount else calculatedCashCUP
    val persistenceChange = maxOf(0.0, persistenceCash - requiredCash)
    val effectivePaymentMethod = when (selectedPaymentMethod) {
        "TRANSFERENCIA" -> if (calculatedCashCUP > 0.0) "MIXTO" else "TRANSFERENCIA"
        "MIXTO" -> "MIXTO"
        else -> "EFECTIVO"
    }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = "Cobro Comanda #${order.comandaNumber}",
        icon = Icons.Outlined.PointOfSale,
        iconTint = Emerald600,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.cobrarComandaIndividual(
                        orderId = order.id,
                        paymentMethod = effectivePaymentMethod,
                        cashReceived = if (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP == 0.0) 0.0 else persistenceCash,
                        changeGiven = if (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP == 0.0) 0.0 else persistenceChange,
                        currency = selectedCurrency,
                        exchangeRate = finalExchangeRate,
                        amountInCurrency = finalAmountInCurrency,
                        onComplete = onCobroComplete
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CONFIRMAR Y COBRAR COMANDA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFECFDF5),
            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Monto Total de la Comanda:",
                    fontSize = 13.sp,
                    color = Emerald800,
                    fontWeight = FontWeight.Bold
                )
                Text("$${"%.2f".format(totalAmount)} CUP", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Emerald700)
                Text(
                    text = if (order.tableNumber == null) "Pedido Para Llevar" else "Ubicación: Mesa ${order.tableNumber}",
                    fontSize = 11.sp,
                    color = Slate600
                )
            }
        }

        Text("Método de Pago:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("EFECTIVO", "TRANSFERENCIA", "MIXTO").forEach { method ->
                FilterChip(
                    selected = selectedPaymentMethod == method,
                    onClick = { selectedPaymentMethod = method },
                    label = { Text(method, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Moneda de la Operación:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("CUP", "USD", "EUR").forEach { curr ->
                FilterChip(
                    selected = selectedCurrency == curr,
                    onClick = { selectedCurrency = curr },
                    label = { Text(curr, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedCurrency != "CUP") {
            val rateText = when (selectedCurrency) {
                "USD" -> "1 USD = $tasaUsd CUP"
                "EUR" -> "1 EUR = $tasaEur CUP"
                else -> ""
            }
            Text(
                text = "Tasa de cambio: $rateText (Monto en $selectedCurrency: ${"%.2f".format(totalAmountInCurrency)})",
                fontSize = 12.sp,
                color = Slate500,
                fontWeight = FontWeight.Medium
            )
        }

        if (selectedPaymentMethod == "TRANSFERENCIA" || selectedPaymentMethod == "MIXTO") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (transferBreakdown.isLimited) Color(0xFFFFFBEB) else Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, if (transferBreakdown.isLimited) Color(0xFFFDE68A) else Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRANSFERENCIA:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Text("$${"%.2f".format(calculatedTransferInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = ElQadreNavy)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EFECTIVO:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (calculatedCashCUP > 0.0) Amber700 else Slate600)
                        Text("$${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (calculatedCashCUP > 0.0) Amber700 else Slate600)
                    }
                    if (transferBreakdown.isLimited && calculatedCashCUP > 0.0) {
                        Text(
                            text = "⚠️ La comanda supera las unidades permitidas por transferencia. El resto ($${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency) debe abonarse en EFECTIVO.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (selectedPaymentMethod == "EFECTIVO" || (selectedPaymentMethod == "TRANSFERENCIA" && calculatedCashCUP > 0.0) || selectedPaymentMethod == "MIXTO") {
            val cashNeededText = if (selectedPaymentMethod == "EFECTIVO") {
                "Efectivo Recibido ($selectedCurrency)"
            } else {
                "Efectivo Recibido para el resto ($${"%.2f".format(calculatedCashInCurrency)} $selectedCurrency)"
            }

            OutlinedTextField(
                value = cashReceivedText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                        cashReceivedText = input
                    }
                },
                placeholder = { Text(cashNeededText, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(10.dp)
            )

            if (cashReceivedInCurrency > 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cambio a Entregar:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${"%.2f".format(changeGivenInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Rose600)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalonHistorialTab(uiState: MainUiState) {
    var selectedSubTab by remember { mutableStateOf("ACTIVA") }
    var selectedArchivedJornada by remember { mutableStateOf<Jornada?>(null) }

    val activeJornada = uiState.activeJornada
    val cobradaOrders = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada?.id && it.status == "COBRADA" }
    }
    val cobradaItems = remember(uiState.allOrderItems, cobradaOrders) {
        val cobradaOrderIds = cobradaOrders.map { it.id }.toSet()
        uiState.allOrderItems.filter { cobradaOrderIds.contains(it.orderId) }
    }

    val totalMontoCobrado = cobradaOrders.sumOf { it.totalAmount }
    val totalCocina = cobradaOrders.sumOf { it.totalCocina }
    val totalBarra = cobradaOrders.sumOf { it.totalBarra }
    val totalCantidadProductos = cobradaItems.sumOf { it.quantity }

    val archivedJornadas = remember(uiState.allJornadas) {
        uiState.allJornadas.filter { !it.isOpen }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Historial y Archivo de Salón",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Consulta el registro de comandas cobradas de la jornada activa y turnos anteriores",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { selectedSubTab = "ACTIVA" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSubTab == "ACTIVA") ElQadreNavy else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "JORNADA ACTIVA (${cobradaOrders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selectedSubTab == "ACTIVA") Color.White else Slate700
                        )
                    }
                }

                Surface(
                    onClick = { selectedSubTab = "ARCHIVO" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSubTab == "ARCHIVO") ElQadreNavy else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "ARCHIVO DE JORNADAS (${archivedJornadas.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selectedSubTab == "ARCHIVO") Color.White else Slate700
                        )
                    }
                }
            }
        }

        if (selectedSubTab == "ACTIVA") {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CONTADORES DE LA JORNADA ACTIVA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFD1FAE5)) {
                                Text("Jornada #${activeJornada?.id ?: 0}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            }
                        }

                        HorizontalDivider(color = Slate100)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Monto Total Cobrado", fontSize = 10.sp, color = Slate500)
                                Text("$${"%.2f".format(totalMontoCobrado)} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Importe Registrado (Cocina)", fontSize = 10.sp, color = Slate500)
                                Text("$${"%.2f".format(totalCocina)} CUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E3A8A))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• Comandas Cobradas: ${cobradaOrders.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate700)
                            Text("• Productos Vendidos: $totalCantidadProductos ud.", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate700)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• Subtotal Cocina: $${"%.2f".format(totalCocina)} CUP", fontSize = 10.sp, color = Slate600)
                            Text("• Subtotal Barra: $${"%.2f".format(totalBarra)} CUP", fontSize = 10.sp, color = Slate600)
                        }

                        val kitchenItemsSold = remember(cobradaItems) {
                            cobradaItems.filter { it.destination == "COCINA" }
                        }
                        val totalPlatosCocinaVendidos = remember(kitchenItemsSold) {
                            kitchenItemsSold.sumOf { it.quantity }
                        }
                        val montoPorProducto = remember(activeJornada, uiState.currentUser) {
                            if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
                                activeJornada!!.utilidadSalonMontoUnitario
                            } else {
                                uiState.currentUser?.montoPorProducto ?: 0.0
                            }
                        }
                        val utilidadesPotenciales = totalPlatosCocinaVendidos * montoPorProducto

                        HorizontalDivider(color = Slate100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• Utilidades Potenciales:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text("$${"%.2f".format(utilidadesPotenciales)} CUP ($totalPlatosCocinaVendidos ud. × $${"%.2f".format(montoPorProducto)} CUP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                    }
                }
            }

            if (cobradaItems.isNotEmpty()) {
                item {
                    val groupedVariants = remember(cobradaItems) {
                        cobradaItems.groupBy { "${it.productName}${if (it.notes.isNotBlank()) " (${it.notes})" else ""}" }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Ventas por Producto y Variante (Agregados):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            HorizontalDivider(color = Slate100)

                            groupedVariants.forEach { (variantName, items) ->
                                val qty = items.sumOf { it.quantity }
                                val sum = items.sumOf { it.unitPrice * it.quantity }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• $variantName", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text("$qty ud. | $${"%.2f".format(sum)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Comandas Cobradas (${cobradaOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
            }

            if (cobradaOrders.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Aún no se han cobrado comandas en esta jornada.", fontSize = 12.sp, color = Slate400)
                        }
                    }
                }
            } else {
                items(cobradaOrders, key = { it.id }) { order ->
                    val orderItems = cobradaItems.filter { it.orderId == order.id }
                    ComandaCobradaCard(order = order, items = orderItems)
                }
            }
        } else {
            if (archivedJornadas.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Archive, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                                Text("No hay jornadas archivadas anteriores.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate600)
                                Text("Al realizar el Cierre de Turno, la jornada activa se guardará automáticamente en el Archivo.", fontSize = 11.sp, color = Slate400, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                items(archivedJornadas, key = { it.id }) { jornada ->
                    ArchivedJornadaCard(
                        jornada = jornada,
                        uiState = uiState,
                        onViewDetail = { selectedArchivedJornada = jornada }
                    )
                }
            }
        }
    }

    selectedArchivedJornada?.let { jornada ->
        ArchivedJornadaDetailDialog(
            jornada = jornada,
            uiState = uiState,
            onDismiss = { selectedArchivedJornada = null }
        )
    }
}

@Composable
private fun ComandaCobradaCard(order: TableOrder, items: List<OrderItem>) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val durSec = order.serviceDurationSeconds ?: 0L
    val durStr = "${durSec / 60}m ${durSec % 60}s"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = ElQadreNavy) {
                        Text("Comanda #${order.comandaNumber}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF3C7)) {
                        Text("Mesa ${order.tableNumber}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold, color = ElQadreNavy, fontSize = 11.sp)
                    }
                }

                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFD1FAE5)) {
                    Text("COBRADA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.Bold, color = Emerald800, fontSize = 10.sp)
                }
            }

            HorizontalDivider(color = Slate100)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${item.quantity}x ${item.productName}${if (item.notes.isNotBlank()) " [${item.notes}]" else ""}", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("$${"%.2f".format(item.unitPrice * item.quantity)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Cobrado: ${order.paymentMethod} • Hora: ${dateFormatter.format(Date(order.confirmedAt ?: order.createdAt))}", fontSize = 10.sp, color = Slate500)
                    Text("Tiempo servicio: $durStr • Registrado (Cocina): $${"%.2f".format(order.totalCocina)} CUP", fontSize = 10.sp, color = Slate500)
                }
                Text("$${"%.2f".format(order.totalAmount)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Emerald700)
            }
        }
    }
}

@Composable
private fun ArchivedJornadaCard(
    jornada: Jornada,
    uiState: MainUiState,
    onViewDetail: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val archivedOrders = remember(uiState.allOrders, jornada.id) {
        uiState.allOrders.filter { it.jornadaId == jornada.id && it.status == "COBRADA" }
    }
    val archivedOrderIds = remember(archivedOrders) { archivedOrders.map { it.id }.toSet() }
    val archivedItems = remember(uiState.allOrderItems, archivedOrderIds) {
        uiState.allOrderItems.filter { archivedOrderIds.contains(it.orderId) }
    }
    val totalMonto = archivedOrders.sumOf { it.totalAmount }
    val totalCocina = archivedOrders.sumOf { it.totalCocina }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Jornada Archivada #${jornada.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                    Text("CERRADA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
            }

            Text("Apertura: ${dateFormatter.format(Date(jornada.openedAt))} • Cierre: ${if (jornada.closedAt != null) dateFormatter.format(Date(jornada.closedAt)) else '-'}", fontSize = 10.sp, color = Slate500)
            Text("Operador: ${(jornada.closedBy ?: "").ifBlank { jornada.openedBy }}", fontSize = 10.sp, color = Slate500)

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Comandas Cobradas", fontSize = 10.sp, color = Slate500)
                    Text("${archivedOrders.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Importe Registrado (Cocina)", fontSize = 10.sp, color = Slate500)
                    Text("$${"%.2f".format(totalCocina)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Cobrado", fontSize = 10.sp, color = Slate500)
                    Text("$${"%.2f".format(totalMonto)} CUP", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Emerald700)
                }
            }

            val kitchenItemsSold = archivedItems.filter { it.destination == "COCINA" }
            val totalPlatosCocina = kitchenItemsSold.sumOf { it.quantity }
            val rate = jornada.utilidadSalonMontoUnitario
            val utilidadesPotenciales = totalPlatosCocina * rate

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Utilidades Potenciales:", fontSize = 10.sp, color = Slate500)
                Text("$${"%.2f".format(utilidadesPotenciales)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            }

            OutlinedButton(
                onClick = onViewDetail,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VER DETALLE DE COMANDAS ARCHIVADAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ArchivedJornadaDetailDialog(
    jornada: Jornada,
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val archivedOrders = remember(uiState.allOrders, jornada.id) {
        uiState.allOrders.filter { it.jornadaId == jornada.id }
    }
    val cobradaOrders = archivedOrders.filter { it.status == "COBRADA" }
    val cobradaOrderIds = cobradaOrders.map { it.id }.toSet()
    val archivedItems = remember(uiState.allOrderItems, cobradaOrderIds) {
        uiState.allOrderItems.filter { cobradaOrderIds.contains(it.orderId) }
    }

    val totalMonto = cobradaOrders.sumOf { it.totalAmount }
    val totalCocina = cobradaOrders.sumOf { it.totalCocina }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = "Jornada #${jornada.id} - Archivo",
        icon = Icons.Outlined.Archive,
        iconTint = ElQadreNavy,
        buttons = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("CERRAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = Slate50, border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Operador: ${(jornada.closedBy ?: "").ifBlank { jornada.openedBy }}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Apertura: ${dateFormatter.format(Date(jornada.openedAt))}", fontSize = 11.sp, color = Slate600)
                Text("Cierre: ${if (jornada.closedAt != null) dateFormatter.format(Date(jornada.closedAt)) else '-'}", fontSize = 11.sp, color = Slate600)
                HorizontalDivider(color = Slate200)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Comandas: ${cobradaOrders.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Registrado: $${"%.2f".format(totalCocina)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                    Text("Total: $${"%.2f".format(totalMonto)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Emerald700)
                }

                val kitchenItemsSold = archivedItems.filter { it.destination == "COCINA" }
                val totalPlatosCocina = kitchenItemsSold.sumOf { it.quantity }
                val rate = jornada.utilidadSalonMontoUnitario
                val utilidadesPotenciales = totalPlatosCocina * rate

                HorizontalDivider(color = Slate200)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Utilidades Potenciales:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Text("$${"%.2f".format(utilidadesPotenciales)} CUP ($totalPlatosCocina ud. × $${"%.2f".format(rate)} CUP)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                }
            }
        }

        Text("Detalle de Comandas Cobradas (${cobradaOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cobradaOrders.forEach { order ->
                val items = archivedItems.filter { it.orderId == order.id }
                Surface(shape = RoundedCornerShape(8.dp), color = Slate50, border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Comanda #${order.comandaNumber} • Mesa ${order.tableNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("$${"%.2f".format(order.totalAmount)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Emerald700)
                        }
                        items.forEach { item ->
                            Text("  • ${item.quantity}x ${item.productName}${if (item.notes.isNotBlank()) " (${item.notes})" else ""}", fontSize = 11.sp, color = Slate600)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalonCierreTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateToInicio: () -> Unit
) {
    val context = LocalContext.current
    var isReportGenerated by remember { mutableStateOf(false) }
    var generatedReportFile by remember { mutableStateOf<File?>(null) }
    var isInformeEnviado by remember { mutableStateOf(false) }

    var showAdminErrorModal by remember { mutableStateOf(false) }
    var showPendingWarningModal by remember { mutableStateOf(false) }
    var showCierreConfirmModal by remember { mutableStateOf(false) }

    val activeJornada = uiState.activeJornada
    val closedOrders = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada?.id && it.status == "COBRADA" }
    }
    val orderItems = uiState.allOrderItems

    val occupiedTables = remember(uiState.openOrders) {
        uiState.openOrders.filter { it.status != "COBRADA" && it.status != "CANCELADA" }
    }
    val pendingServirOrders = remember(occupiedTables) {
        occupiedTables.filter { it.status == "PENDIENTE_SERVIR" }
    }

    val totalMontoCobrado = closedOrders.sumOf { it.totalAmount }
    val totalCocina = closedOrders.sumOf { it.totalCocina }

    val adminPhone = uiState.businessConfig?.telefono?.trim() ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Cierre de Turno e Informes",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Genera el informe operativo de tu turno, envíalo al Administrador y realiza el Cierre de Jornada",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RESUMEN DE JORNADA ACTIVA #${activeJornada?.id ?: 0}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFD1FAE5)) {
                            Text("JORNADA ABIERTA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Comandas Cobradas", fontSize = 10.sp, color = Slate500)
                            Text("${closedOrders.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Importe Registrado (Cocina)", fontSize = 10.sp, color = Slate500)
                            Text("$${"%.2f".format(totalCocina)} CUP", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E3A8A))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Monto Total Cobrado", fontSize = 10.sp, color = Slate500)
                            Text("$${"%.2f".format(totalMontoCobrado)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Emerald700)
                        }
                    }
                }
            }
        }

        // Section: UTILIDADES POTENCIALES DE LA JORNADA
        item {
            val closedOrderIds = remember(closedOrders) { closedOrders.map { it.id }.toSet() }
            val kitchenItemsSold = remember(orderItems, closedOrderIds) {
                orderItems.filter { closedOrderIds.contains(it.orderId) && it.destination == "COCINA" }
            }
            val totalPlatosCocinaVendidos = remember(kitchenItemsSold) {
                kitchenItemsSold.sumOf { it.quantity }
            }
            val montoPorProducto = remember(activeJornada, uiState.currentUser) {
                if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
                    activeJornada!!.utilidadSalonMontoUnitario
                } else {
                    uiState.currentUser?.montoPorProducto ?: 0.0
                }
            }
            val utilidadesPotenciales = totalPlatosCocinaVendidos * montoPorProducto

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UTILIDADES POTENCIALES DE LA JORNADA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Emerald800
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "SALÓN",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFA7F3D0))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Platos Cocina Vendidos", fontSize = 10.sp, color = Slate600)
                            Text("$totalPlatosCocinaVendidos ud.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                        }
                        Column {
                            Text("Monto Asignado", fontSize = 10.sp, color = Slate600)
                            Text("$${"%.2f".format(montoPorProducto)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Utilidades Potenciales", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text("$${"%.2f".format(utilidadesPotenciales)} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
                        }
                    }

                    Text(
                        text = "Nota: Calculado multiplicando la cantidad total de platos de Cocina vendidos ($totalPlatosCocinaVendidos ud.) por el monto por producto asignado al usuario ($${"%.2f".format(montoPorProducto)} CUP).",
                        fontSize = 10.sp,
                        color = Slate600,
                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                }
            }
        }

        if (occupiedTables.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚠️ Operaciones Pendientes en Salón", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Rose600)
                            Text("Mesa(s) ocupada(s): ${occupiedTables.map { it.tableNumber }.distinct().joinToString(", ")}. No se puede cerrar la jornada si existen comandas pendientes de servir o cobrar.", fontSize = 10.sp, color = Slate700)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("PASOS PARA EL CIERRE DE JORNADA:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)

                    HorizontalDivider(color = Slate100)

                    Button(
                        onClick = {
                            if (activeJornada != null) {
                                val rateForPdf = if ((activeJornada.utilidadSalonMontoUnitario) > 0.0) activeJornada.utilidadSalonMontoUnitario else (uiState.currentUser?.montoPorProducto ?: 0.0)
                                val pdfFile = SalonReportPdfExporter.exportSalonReport(
                                    context = context,
                                    activeJornada = activeJornada,
                                    closedOrders = closedOrders,
                                    orderItems = orderItems,
                                    currentDependiente = uiState.currentUser?.username ?: "salondependiente",
                                    montoPorProducto = rateForPdf
                                )
                                if (pdfFile != null) {
                                    generatedReportFile = pdfFile
                                    isReportGenerated = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_generar_informe"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isReportGenerated) "1. INFORME GENERADO (VOLVER A GENERAR)" else "1. GENERAR INFORME DE JORNADA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            generatedReportFile?.let { file ->
                                SalonBackupManager.shareBackupFile(context, file)
                            }
                        },
                        enabled = isReportGenerated && generatedReportFile != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_descargar_pdf"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2. DESCARGAR / ABRIR PDF", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val file = generatedReportFile
                            if (file != null) {
                                if (adminPhone.isBlank()) {
                                    showAdminErrorModal = true
                                    isInformeEnviado = false
                                } else {
                                    val sent = SalonReportPdfExporter.shareSalonReportToAdmin(context, file, adminPhone)
                                    if (sent) {
                                        isInformeEnviado = true
                                    }
                                }
                            }
                        },
                        enabled = isReportGenerated && generatedReportFile != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_enviar_administrador"),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("3. ENVIAR AL ADMINISTRADOR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    if (isInformeEnviado) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFD1FAE5), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Emerald800, modifier = Modifier.size(16.dp))
                                Text("Informe enviado / preparado para el Administrador. Cierre de jornada habilitado.", fontSize = 10.sp, color = Emerald800, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Button(
                        onClick = {
                            if (occupiedTables.isNotEmpty()) {
                                showPendingWarningModal = true
                            } else {
                                showCierreConfirmModal = true
                            }
                        },
                        enabled = isInformeEnviado,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_cerrar_jornada"),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("4. CERRAR JORNADA DEFINITIVA", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    if (!isInformeEnviado) {
                        Text("* Para habilitar el Cierre de Jornada debe generar y enviar primero el informe al Administrador.", fontSize = 10.sp, color = Slate500)
                    }
                }
            }
        }
    }

    if (showAdminErrorModal) {
        SalonResponsiveDialog(
            onDismissRequest = { showAdminErrorModal = false },
            title = "Teléfono Requerido",
            icon = Icons.Outlined.Error,
            iconTint = Rose600,
            buttons = {
                Button(
                    onClick = { showAdminErrorModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("ENTENDIDO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("❌ No existe un número de teléfono de Administrador configurado en el sistema.", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                Text("Para enviar el informe y poder cerrar la jornada, el Administrador debe configurar su teléfono en los Ajustes del Negocio.", fontSize = 12.sp, color = Slate500)
            }
        }
    }

    if (showPendingWarningModal) {
        SalonResponsiveDialog(
            onDismissRequest = { showPendingWarningModal = false },
            title = "Operaciones Pendientes",
            icon = Icons.Outlined.Warning,
            iconTint = Rose600,
            buttons = {
                Button(
                    onClick = { showPendingWarningModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("VOLVER AL PANEL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No se puede cerrar la jornada porque existen comandas u operaciones abiertas:", fontSize = 13.sp, color = Slate700)
                occupiedTables.forEach { order ->
                    Text("• Mesa ${order.tableNumber}: Comanda #${order.comandaNumber} (${order.status}) - $${"%.2f".format(order.totalAmount)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rose600)
                }
                Text("Por favor sirva y cobre todas las mesas antes de realizar el Cierre de Jornada.", fontSize = 11.sp, color = Slate500)
            }
        }
    }

    if (showCierreConfirmModal) {
        SalonResponsiveDialog(
            onDismissRequest = { showCierreConfirmModal = false },
            title = "Confirmar Cierre de Jornada",
            icon = Icons.Outlined.Lock,
            iconTint = Rose600,
            buttons = {
                TextButton(
                    onClick = { showCierreConfirmModal = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        viewModel.closeJornadaSalon(onComplete = {
                            showCierreConfirmModal = false
                            isReportGenerated = false
                            isInformeEnviado = false
                            onNavigateToInicio()
                        })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("SÍ, CERRAR JORNADA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Está seguro de cerrar la jornada activa de Salón?", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                Text("• Las ${closedOrders.size} comandas cobradas pasarán al ARCHIVO DE JORNADAS.", fontSize = 12.sp, color = Slate600)
                Text("• Se reiniciará la mesa de trabajo e iniciará una nueva jornada limpia para el próximo turno.", fontSize = 12.sp, color = Slate600)
                Text("• La secuencia de números de comanda volverá a empezar desde el 1.", fontSize = 12.sp, color = Slate600)
            }
        }
    }
}

@Composable
private fun SalonAjustesTab(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var tableInputText by remember(uiState.salonTableCount) {
        mutableStateOf(uiState.salonTableCount.toString())
    }

    var showRestoreModal by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Ajustes de Salón",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Configuración del perfil operativo del dependiente y copia de seguridad",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        // Versión de la Aplicación y Buscar Actualizaciones
        item {
            com.example.ui.components.AppVersionSettingsCard()
        }

        // 1. Sincronización y URLs
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudSync,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "SINCRONIZACIÓN Y URLs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Direcciones de actualización configuradas en el sistema",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // URLs list
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text("URL Catálogo (qcatalogo.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlCatalogoJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlCatalogoJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                        Column {
                            Text("URL Inventario (qmercainv.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlMercainvJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlMercainvJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                        Column {
                            Text("URL Usuarios (qusuarios.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlUsuariosJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlUsuariosJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 2. Teléfono de Caja / Comandas
        item {
            val cajeroInfo = remember(uiState.users, uiState.activeJornada, uiState.generalConfig) {
                com.example.util.CajaPhoneHelper.resolveCajeroInfo(
                    uiState.users,
                    uiState.activeJornada,
                    uiState.generalConfig
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (cajeroInfo.isConfigured) Emerald200 else Amber200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().testTag("card_telefono_caja_salon")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (cajeroInfo.isConfigured) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (cajeroInfo.isConfigured) Emerald700 else Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "TELÉFONO DE CAJA / COMANDAS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Destinatario automático según qusuarios.json",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cajero Habilitado:", fontSize = 12.sp, color = Slate600)
                            Text(
                                text = cajeroInfo.cajeroUser?.let { "${it.fullName} (@${it.username})" } ?: "Sin usuario Cajero activo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cajeroInfo.cajeroUser != null) ElQadreNavy else Slate400
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Número de Teléfono:", fontSize = 12.sp, color = Slate600)
                            if (cajeroInfo.isConfigured) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFD1FAE5)
                                ) {
                                    Text(
                                        text = cajeroInfo.phoneNumber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald800
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "Caja no tiene teléfono configurado",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Solo lectura. Este número se administra exclusivamente desde qusuarios.json.",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.TableRestaurant,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "CANTIDAD DE MESAS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Define cuántas mesas están asignadas a tu visualización de Inicio",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cantidad actual:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Slate700
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreNavy
                        ) {
                            Text(
                                text = "${uiState.salonTableCount} Mesas",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedIconButton(
                            onClick = {
                                val current = tableInputText.toIntOrNull() ?: uiState.salonTableCount
                                if (current > 1) {
                                    tableInputText = (current - 1).toString()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Disminuir mesas")
                        }

                        OutlinedTextField(
                            value = tableInputText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    tableInputText = input
                                }
                            },
                            label = { Text("Número de Mesas") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("table_count_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                unfocusedBorderColor = Slate200
                            )
                        )

                        OutlinedIconButton(
                            onClick = {
                                val current = tableInputText.toIntOrNull() ?: uiState.salonTableCount
                                if (current < 100) {
                                    tableInputText = (current + 1).toString()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Aumentar mesas")
                        }
                    }

                    Text(
                        text = "Selección Rápida:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(4, 6, 8, 10, 12, 16, 20).forEach { preset ->
                            FilterChip(
                                selected = tableInputText == preset.toString(),
                                onClick = { tableInputText = preset.toString() },
                                label = { Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(6.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElQadreNavy,
                                    selectedLabelColor = Color.White,
                                    containerColor = Slate100,
                                    labelColor = Slate700
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val newCount = tableInputText.toIntOrNull()
                            if (newCount != null && newCount in 1..100) {
                                viewModel.updateSalonTableCount(newCount)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_save_table_count"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GUARDAR CANTIDAD DE MESAS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Cantidad de productos pagables por transferencia (Productos de Producción)
        item {
            var searchQuery by remember { mutableStateOf("") }
            val productionProducts = remember(uiState.products, uiState.productosElaborados) {
                uiState.products.filter { p ->
                    SalonTransferConfig.isProductionProduct(
                        p,
                        uiState.productosElaborados.any { pe -> pe.productId == p.id }
                    )
                }
            }
            val filteredProducts = remember(productionProducts, searchQuery) {
                if (searchQuery.isBlank()) {
                    productionProducts
                } else {
                    productionProducts.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.code.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            var refreshTrigger by remember { mutableStateOf(0) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE0E7FF),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "TRANSFERENCIA POR PRODUCTO (PRODUCCIÓN)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Configura cuántas unidades de cada producto aceptas cobrar por transferencia",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar producto de producción...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate200
                        )
                    )

                    if (filteredProducts.isEmpty()) {
                        Text(
                            text = if (productionProducts.isEmpty()) "No hay productos de producción registrados." else "No se encontraron productos coincidentes.",
                            fontSize = 12.sp,
                            color = Slate400,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            filteredProducts.forEach { product ->
                                key(product.id, refreshTrigger) {
                                    val currentLimit = remember(product.id, refreshTrigger) {
                                        SalonTransferConfig.getProductTransferLimit(context, product)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Slate50,
                                        border = BorderStroke(1.dp, Slate200),
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
                                                    Text(
                                                        text = product.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = ElQadreNavy
                                                    )
                                                    Text(
                                                        text = "${product.category} • $${"%.2f".format(product.price)} CUP",
                                                        fontSize = 11.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = when {
                                                        currentLimit == null -> Color(0xFFECFDF5)
                                                        currentLimit == 0 -> Color(0xFFFEF2F2)
                                                        else -> Color(0xFFEFF6FF)
                                                    },
                                                    border = BorderStroke(
                                                        1.dp,
                                                        when {
                                                            currentLimit == null -> Color(0xFFA7F3D0)
                                                            currentLimit == 0 -> Color(0xFFFECACA)
                                                            else -> Color(0xFFBFDBFE)
                                                        }
                                                    )
                                                ) {
                                                    Text(
                                                        text = when {
                                                            currentLimit == null -> "Sin límite"
                                                            currentLimit == 0 -> "Solo efectivo"
                                                            else -> "Máx $currentLimit unid${if (currentLimit == 1) "" else "s"}"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when {
                                                            currentLimit == null -> Emerald700
                                                            currentLimit == 0 -> Rose600
                                                            else -> Color(0xFF4F46E5)
                                                        },
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Límite transferencia:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Slate600,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                OutlinedIconButton(
                                                    onClick = {
                                                        val cur = currentLimit ?: 0
                                                        if (cur > 0) {
                                                            SalonTransferConfig.setProductTransferLimit(context, product, cur - 1)
                                                            refreshTrigger++
                                                        } else {
                                                            SalonTransferConfig.setProductTransferLimit(context, product, 0)
                                                            refreshTrigger++
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Disminuir", modifier = Modifier.size(14.dp))
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.White,
                                                    border = BorderStroke(1.dp, Slate300),
                                                    modifier = Modifier
                                                        .width(48.dp)
                                                        .height(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = (currentLimit ?: "∞").toString(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = ElQadreNavy
                                                        )
                                                    }
                                                }

                                                OutlinedIconButton(
                                                    onClick = {
                                                        val cur = currentLimit ?: 0
                                                        SalonTransferConfig.setProductTransferLimit(context, product, cur + 1)
                                                        refreshTrigger++
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(14.dp))
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf("Sin límite" to null, "0" to 0, "1" to 1, "2" to 2, "3" to 3, "5" to 5).forEach { (label, value) ->
                                                    val isSelected = currentLimit == value
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = {
                                                            SalonTransferConfig.setProductTransferLimit(context, product, value)
                                                            refreshTrigger++
                                                        },
                                                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                        shape = RoundedCornerShape(4.dp),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = ElQadreNavy,
                                                            selectedLabelColor = Color.White,
                                                            containerColor = Color.White,
                                                            labelColor = Slate700
                                                        ),
                                                        modifier = Modifier.height(26.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Los límites configurados se guardan localmente y no se recalculan ni sobrescriben por cambios de catálogo, inventario o tasas.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Backup,
                                    contentDescription = null,
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "RESPALDO Y RESTAURACIÓN DE DATOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Gestión de copias de seguridad completas en formato qdepsalon.json",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val jsonStr = SalonBackupManager.createBackupJson(uiState)
                                val backupFile = SalonBackupManager.exportBackupFile(context, jsonStr)
                                if (backupFile != null) {
                                    SalonBackupManager.shareBackupFile(context, backupFile)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_respaldar_datos"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESPALDAR DATOS (qdepsalon.json)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                restoreJsonInput = ""
                                showRestoreModal = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_restaurar_datos"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESTAURAR DATOS DESDE ARCHIVO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Inicializar Sistema
            com.example.ui.components.InitializeSystemCard(viewModel = viewModel)
        }
    }

    if (showRestoreModal) {
        SalonResponsiveDialog(
            onDismissRequest = { showRestoreModal = false },
            title = "Restaurar Datos",
            icon = Icons.Outlined.Warning,
            iconTint = Color(0xFFD97706),
            buttons = {
                TextButton(
                    onClick = { showRestoreModal = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            viewModel.restoreSalonBackupJson(restoreJsonInput, onComplete = { success ->
                                if (success) {
                                    showRestoreModal = false
                                }
                            })
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CONFIRMAR Y RESTAURAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ ATENCIÓN: Al restaurar, la información actual de mesas, comandas, historial y jornadas será reemplazada con los datos de la copia de seguridad.",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF92400E)
                )
            }

            OutlinedTextField(
                value = restoreJsonInput,
                onValueChange = { restoreJsonInput = it },
                label = { Text("Pega el contenido de qdepsalon.json aquí") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun UnifiedCatalogDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val products = uiState.products
    val availableProducts = remember(products) { products.filter { it.isAvailable } }

    SalonResponsiveDialog(
        onDismissRequest = onDismiss,
        title = "Catálogo Unificado (${availableProducts.size})",
        icon = Icons.Outlined.MenuBook,
        iconTint = ElQadreNavy,
        buttons = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("CERRAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Text(
            text = "Productos disponibles (Cocina + Barra unificados):",
            fontSize = 12.sp,
            color = Slate500
        )

        if (availableProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay productos disponibles en el catálogo.", fontSize = 12.sp, color = Slate400)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                availableProducts.forEach { product ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                Text(
                                    text = "Categoría: ${product.category}",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }

                            Text(
                                text = "$${"%.2f".format(product.price)} CUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Emerald700
                            )
                        }
                    }
                }
            }
        }
    }
}
