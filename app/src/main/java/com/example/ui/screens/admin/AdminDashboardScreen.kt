package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

enum class AdminTab(val title: String, val icon: ImageVector) {
    INICIO("INICIO", Icons.Filled.Home),
    GESTION("GESTIÓN", Icons.Outlined.ManageAccounts),
    CATALOGO("CATÁLOGO", Icons.Outlined.LocalOffer),
    USUARIOS("USUARIOS", Icons.Outlined.Group),
    AJUSTES("AJUSTES", Icons.Outlined.Settings)
}

enum class AdminModule(val title: String, val icon: ImageVector) {
    PRODUCCION("Producción", Icons.Outlined.SoupKitchen),
    MERCADERIAS("Mercaderías", Icons.Outlined.ShoppingCart),
    GASTOS_CORRIENTES("Gastos corrientes", Icons.Outlined.ReceiptLong),
    INVERSIONES("Inversiones", Icons.Outlined.TrendingUp),
    ALMACEN("Almacén", Icons.Outlined.Inventory2),
    REPORTES("Reportes", Icons.Outlined.Leaderboard)
}

@Composable
fun AdminDashboardScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AdminTab.INICIO) }
    var activeModuleDialog by remember { mutableStateOf<AdminModule?>(null) }
    var showOpenJornadaDialog by remember { mutableStateOf(false) }
    var showCloseJornadaDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    val isAnyAdminModalOpen = activeModuleDialog != null ||
            showOpenJornadaDialog ||
            showCloseJornadaDialog ||
            showNotificationDialog

    BackHandler(enabled = isAnyAdminModalOpen || selectedTab != AdminTab.INICIO) {
        when {
            activeModuleDialog != null -> activeModuleDialog = null
            showOpenJornadaDialog -> showOpenJornadaDialog = false
            showCloseJornadaDialog -> showCloseJornadaDialog = false
            showNotificationDialog -> showNotificationDialog = false
            selectedTab != AdminTab.INICIO -> selectedTab = AdminTab.INICIO
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElQadreBackground,
        topBar = {
            AdminTopBar(
                businessName = uiState.businessName,
                notificationCount = 3,
                onMenuClick = { /* Menu */ },
                onNotificationClick = { showNotificationDialog = true },
                onLogoutClick = onLogout
            )
        },
        bottomBar = {
            AdminBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                AdminTab.INICIO -> {
                    AdminHomeContent(
                        uiState = uiState,
                        onModuleClick = { activeModuleDialog = it },
                        onOpenJornadaClick = { showOpenJornadaDialog = true },
                        onCloseJornadaClick = { showCloseJornadaDialog = true }
                    )
                }
                AdminTab.GESTION -> {
                    GestionSubScreen(uiState, viewModel)
                }
                AdminTab.CATALOGO -> {
                    CatalogoSubScreen(uiState, viewModel)
                }
                AdminTab.USUARIOS -> {
                    UsuariosSubScreen(uiState, viewModel)
                }
                AdminTab.AJUSTES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Ajustes del Sistema",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = ElQadreNavy
                        )
                        com.example.ui.components.AppVersionSettingsCard()
                        com.example.ui.components.InitializeSystemCard(viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showOpenJornadaDialog) {
        OpenJornadaDialog(
            onDismiss = { showOpenJornadaDialog = false },
            onConfirm = { initialCash ->
                viewModel.openJornada(initialCash)
                showOpenJornadaDialog = false
            }
        )
    }

    if (showCloseJornadaDialog) {
        CloseJornadaDialog(
            jornada = uiState.activeJornada,
            onDismiss = { showCloseJornadaDialog = false },
            onConfirm = { finalCash, notes ->
                viewModel.closeJornada(finalCash, notes)
                showCloseJornadaDialog = false
            }
        )
    }

    if (showNotificationDialog) {
        val lowStockCount = uiState.products.count { it.stock <= it.minStock }
        val openOrdersCount = uiState.openOrders.size
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text("Notificaciones del Sistema", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lowStockCount > 0) {
                        Text("• $lowStockCount producto(s) con stock bajo en inventario.", fontSize = 13.sp)
                    } else {
                        Text("• Sin alertas de stock bajo en inventario.", fontSize = 13.sp)
                    }
                    Text("• Sistema de respaldo local activo.", fontSize = 13.sp)
                    Text("• $openOrdersCount orden(es) activa(s) en salón.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showNotificationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    activeModuleDialog?.let { module ->
        when (module) {
            AdminModule.PRODUCCION -> {
                ProductionWorkspaceDialog(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { activeModuleDialog = null }
                )
            }
            AdminModule.MERCADERIAS -> {
                MercaderiasWorkspaceDialog(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { activeModuleDialog = null }
                )
            }
            AdminModule.GASTOS_CORRIENTES -> {
                GastosCorrientesWorkspaceDialog(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { activeModuleDialog = null }
                )
            }
            AdminModule.INVERSIONES -> {
                InversionesWorkspaceDialog(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { activeModuleDialog = null }
                )
            }
            else -> {
                ModuleDetailDialog(
                    module = module,
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { activeModuleDialog = null }
                )
            }
        }
    }
}

/**
 * Top Bar: Deep Navy background `#161C2C`, white text and icons, golden notification badge.
 */
@Composable
fun AdminTopBar(
    businessName: String,
    notificationCount: Int,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Surface(
        color = ElQadreNavy,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menú",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center: Business Name
            Text(
                text = businessName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )

            // Right: Notifications + Exit
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = onNotificationClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notificaciones",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (notificationCount > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .background(ElQadreGold, CircleShape)
                        ) {
                            Text(
                                text = "$notificationCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = "Cerrar sesión",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Main Home View: Split Jornada Hero Card + 6-Module Grid
 */
@Composable
fun AdminHomeContent(
    uiState: MainUiState,
    onModuleClick: (AdminModule) -> Unit,
    onOpenJornadaClick: () -> Unit,
    onCloseJornadaClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isJornadaOpen = uiState.activeJornada != null

    val dateFormatter = remember { SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES")) }
    val currentDateStr = remember { dateFormatter.format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ============================================================
        // SPLIT JORNADA HERO CARD (EXACT RECREATION OF REFERENCE)
        // ============================================================
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Half: Pale Gold/Cream Surface with Sun, Date, and Status Pill
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .background(ElQadreGoldSurface)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Sun Line Icon
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = ElQadreGoldDark,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "JORNADA ACTUAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = currentDateStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElQadreNavy
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Status Pill Badge (ABIERTA / CERRADA)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isJornadaOpen) ElQadreGold else Slate200,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (isJornadaOpen) "ABIERTA" else "CERRADA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isJornadaOpen) ElQadreNavy else Slate600,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Center Divider Connector Dot
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(0.dp)
                        .fillMaxHeight()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("o", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400)
                        }
                    }
                }

                // Right Half: Solid Golden Yellow with Action Box Icon & Title
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ElQadreGold)
                        .clickable {
                            if (isJornadaOpen) onCloseJornadaClick() else onOpenJornadaClick()
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isJornadaOpen) Icons.Outlined.Inventory2 else Icons.Outlined.AllInbox,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(34.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isJornadaOpen) "CERRAR\nJORNADA" else "ABRIR\nJORNADA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // ============================================================
        // 6-MODULE GRID (2 columns x 3 rows)
        // Fila 1: Producción, Mercancías
        // Fila 2: Gastos corrientes, Inversiones
        // Fila 3: Almacén, Reportes
        // ============================================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val rows = listOf(
                listOf(AdminModule.PRODUCCION, AdminModule.MERCADERIAS),
                listOf(AdminModule.GASTOS_CORRIENTES, AdminModule.INVERSIONES),
                listOf(AdminModule.ALMACEN, AdminModule.REPORTES)
            )
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (mod in row) {
                        Box(modifier = Modifier.weight(1f)) {
                            ModuleGridCard(
                                module = mod,
                                onClick = { onModuleClick(mod) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Module Card: Crisp White Surface, Soft Shadow, Centered Line Art with Gold Accent & Bold Title.
 */
@Composable
fun ModuleGridCard(
    module: AdminModule,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom Icon with a subtle yellow accent (offset circle) to mimic the dual-tone reference
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp)
            ) {
                // Subtle yellow accent mimicking the reference's yellow parts of the icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = 8.dp, y = 8.dp)
                        .background(ElQadreGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
                Icon(
                    imageVector = module.icon,
                    contentDescription = module.title,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = module.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ElQadreNavy,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Floating Bottom Navigation Bar: Pure White with Yellow Selected Item.
 */
@Composable
fun AdminBottomNavigation(
    selectedTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .width(36.dp)
                                    .height(3.dp)
                                    .background(ElQadreGold, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) ElQadreGold else Slate400,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ElQadreGold else Slate400,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SUB-SCREENS & DIALOGS
// ============================================================

@Composable
fun ModuleDetailDialog(
    module: AdminModule,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var entryTitle by remember { mutableStateOf("") }
    var entryContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(module.icon, contentDescription = null, tint = ElQadreGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(module.title, fontWeight = FontWeight.Bold, color = ElQadreNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (module) {
                    AdminModule.PRODUCCION -> {
                        Text("Lotes de preparación en Cocina / Barra:", fontSize = 12.sp, color = Slate500)
                        uiState.productionBatches.forEach { pb ->
                            Surface(shape = RoundedCornerShape(8.dp), color = ElQadreBackground, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("${pb.itemName} (${pb.quantity}u)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Destino: ${pb.destination} • Por: ${pb.createdBy}", fontSize = 11.sp, color = Slate600)
                                }
                            }
                        }
                    }
                    AdminModule.MERCADERIAS -> {
                        Text("Movimientos de entradas y salidas:", fontSize = 12.sp, color = Slate500)
                        uiState.stockMovements.forEach { sm ->
                            Surface(shape = RoundedCornerShape(8.dp), color = ElQadreBackground, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(sm.productName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${sm.type} • ${sm.reason}", fontSize = 11.sp, color = Slate600)
                                    }
                                    Text("${sm.quantity}u", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                }
                            }
                        }
                    }
                    AdminModule.GASTOS_CORRIENTES -> {
                        Text("Gastos corrientes registrados:", fontSize = 12.sp, color = Slate500)
                        uiState.gastosGenerales.forEach { g ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(g.name, fontSize = 12.sp)
                                Text("$${"%.2f".format(g.amount)}", fontWeight = FontWeight.Bold, color = Rose600)
                            }
                        }
                    }
                    AdminModule.INVERSIONES -> {
                        Text("Inversiones y depreciaciones registradas:", fontSize = 12.sp, color = Slate500)
                        uiState.inversiones.forEach { inv ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(inv.name, fontSize = 12.sp)
                                Text("$${"%.2f".format(inv.amount)}", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                        }
                    }
                    AdminModule.ALMACEN -> {
                        Text("Inventario de existencias críticas:", fontSize = 12.sp, color = Slate500)
                        uiState.products.forEach { pr ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pr.name, fontSize = 12.sp)
                                Text("${pr.stock}u", fontWeight = FontWeight.Bold, color = if (pr.stock <= pr.minStock) Rose600 else ElQadreNavy)
                            }
                        }
                    }
                    AdminModule.REPORTES -> {
                        Text("Resumen Financiero y Cuadraturas:", fontSize = 12.sp, color = Slate500)
                        val totalSales = uiState.allJornadas.sumOf { it.totalSales }
                        val activeSales = uiState.activeJornada?.totalSales ?: 0.0
                        Text("Ventas Jornada Actual: $${"%.2f".format(activeSales)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Ventas Acumuladas: $${"%.2f".format(totalSales)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun OpenJornadaDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var cashText by remember { mutableStateOf("15000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir Nueva Jornada", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ingrese el fondo inicial en caja (CUP):", fontSize = 13.sp, color = Slate600)
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("Fondo Inicial") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = cashText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold, contentColor = ElQadreNavy)
            ) {
                Text("Abrir Jornada", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun CloseJornadaDialog(jornada: Jornada?, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var finalCashText by remember { mutableStateOf("${jornada?.expectedCash?.toInt() ?: 0}") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar Jornada Actual", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Efectivo esperado según sistema: $${"%.2f".format(jornada?.expectedCash ?: 0.0)} CUP", fontSize = 12.sp, color = Slate600)
                OutlinedTextField(
                    value = finalCashText,
                    onValueChange = { finalCashText = it },
                    label = { Text("Efectivo real en caja") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas de cuadre") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = finalCashText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = Color.White)
            ) {
                Text("Confirmar Cierre", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
