package com.example.ui.screens.cajero

import androidx.activity.compose.BackHandler
import android.widget.Toast
import com.example.util.CajeroBackupManager
import com.example.util.CostCalculationHelper
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.OrderItem
import com.example.data.local.model.TableOrder
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SalonCartItem
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Product
import com.example.data.local.model.Transferencia
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun rememberAssetImageBitmap(assetFileName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(assetFileName) {
        val candidates = listOf(
            assetFileName.replace(".png", ".jpeg"),
            assetFileName.replace(".png", ".jpg"),
            assetFileName
        )
        candidates.firstNotNullOfOrNull { candidateName ->
            try {
                context.assets.open(candidateName).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

val BILLETE_DENOMINATIONS = listOf(20000, 10000, 5000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5)

fun getBilleteAssetFileName(den: Int): String {
    return "Billete $den pesos.png"
}

enum class CajeroTab { COBRAR, CATALOGO, HISTORIAL, INFORME, CUADRE, AJUSTES }

@Composable
fun CajeroScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val smsPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Session continues normally regardless of granted/denied result
    }

    LaunchedEffect(Unit) {
        viewModel.processPendingSmsComandas()
        val receiveGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!receiveGranted || !readGranted) {
            smsPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        }
    }

    var currentTab by remember { mutableStateOf(CajeroTab.COBRAR) }
    var showQuickActions by remember { mutableStateOf(false) }
    var initialQuickActionTab by remember { mutableStateOf(1) }
    var showResumenConteoDialog by remember { mutableStateOf(false) }
    val billDenominationStacks = remember {
        val map = mutableStateMapOf<Int, SnapshotStateList<String>>()
        BILLETE_DENOMINATIONS.forEach { den ->
            val list = mutableStateListOf<String>()
            list.add("")
            map[den] = list
        }
        map
    }
    var showRegistrarCobroPane by remember { mutableStateOf(false) }
    var ordersToPay by remember { mutableStateOf<List<TableOrder>?>(null) }

    val activeJornada = uiState.activeJornada
    val isJornadaOpen = activeJornada != null && activeJornada.isOpen

    val configuration = LocalConfiguration.current
    val isTabletOrLandscape = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isAnyCajeroModalOpen = showResumenConteoDialog ||
            showRegistrarCobroPane ||
            showQuickActions

    BackHandler(enabled = isAnyCajeroModalOpen || currentTab != CajeroTab.COBRAR) {
        when {
            showResumenConteoDialog -> showResumenConteoDialog = false
            showRegistrarCobroPane -> showRegistrarCobroPane = false
            showQuickActions -> showQuickActions = false
            currentTab != CajeroTab.COBRAR -> currentTab = CajeroTab.COBRAR
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ElQadreBackground,
        topBar = {
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Terminal de Caja",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Cajero: ${uiState.currentUser?.fullName ?: "Cajero"}",
                            color = ElQadreGold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Salir",
                            tint = Color.White
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isJornadaOpen && !isTabletOrLandscape) {
                CajeroBottomNavigation(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        },
        floatingActionButton = {
            if (isJornadaOpen && (currentTab == CajeroTab.COBRAR || currentTab == CajeroTab.CATALOGO)) {
                FloatingActionButton(
                    onClick = {
                        initialQuickActionTab = if (currentTab == CajeroTab.COBRAR) 1 else 0
                        showQuickActions = true
                    },
                    containerColor = ElQadreGold,
                    contentColor = ElQadreNavy,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("btn_acciones_rapidas")
                ) {
                    Icon(
                        imageVector = if (currentTab == CajeroTab.COBRAR) Icons.Filled.Payments else Icons.Filled.Calculate,
                        contentDescription = "Acciones Rápidas"
                    )
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isJornadaOpen && isTabletOrLandscape) {
                CajeroSideNavigation(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (!isJornadaOpen) {
                    AperturaJornadaPane(
                        uiState = uiState,
                        onOpenJornada = { initialCash ->
                            viewModel.openJornada(initialCash)
                        }
                    )
                } else {
                    when (currentTab) {
                        CajeroTab.COBRAR -> {
                            CobrarCajaPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onRegistrarCobroClick = { showRegistrarCobroPane = true },
                                onOpenConteoBilletes = {
                                    initialQuickActionTab = 1
                                    showQuickActions = true
                                },
                                onPayOrdersClick = { ordersToPay = it }
                            )
                        }
                        CajeroTab.CATALOGO -> {
                            CatalogoCajaPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onComandaCreated = { currentTab = CajeroTab.COBRAR }
                            )
                        }
                        CajeroTab.HISTORIAL -> {
                            HistorialCajaPane(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                        CajeroTab.INFORME -> {
                            InformeCajaPane(
                                uiState = uiState
                            )
                        }
                        CajeroTab.CUADRE -> {
                            CuadreCajaPane(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                        CajeroTab.AJUSTES -> {
                            AjustesCajaPane(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickActions) {
        QuickActionsDialog(
            initialTab = initialQuickActionTab,
            billFields = billDenominationStacks,
            onShowResumen = {
                showResumenConteoDialog = true
            },
            onDismiss = { showQuickActions = false }
        )
    }

    if (showResumenConteoDialog) {
        ConteoResumenDialog(
            billFields = billDenominationStacks,
            onDismiss = { showResumenConteoDialog = false }
        )
    }

    if (showRegistrarCobroPane && activeJornada != null) {
        RegistrarCobroDialog(
            uiState = uiState,
            viewModel = viewModel,
            activeJornadaId = activeJornada.id,
            onDismiss = { showRegistrarCobroPane = false }
        )
    }

    if (ordersToPay != null && activeJornada != null) {
        PayOrdersDialog(
            orders = ordersToPay!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { ordersToPay = null }
        )
    }

    if (uiState.showTransferAlert && uiState.pendingTransferAlert != null) {
        TransferenciaAlertDialog(
            parsed = uiState.pendingTransferAlert!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissTransferAlert() }
        )
    }

    if (uiState.showComandaAlert && uiState.pendingComandaAlert != null) {
        val alertData = uiState.pendingComandaAlert!!
        ComandaSmsAlertDialog(
            alert = alertData,
            onDismiss = { viewModel.dismissComandaAlert() },
            onViewOrder = { targetOrderId ->
                viewModel.dismissComandaAlert()
                currentTab = CajeroTab.COBRAR
                val targetOrder = uiState.allOrders.firstOrNull { it.id == targetOrderId || it.comandaNumber == alertData.comandaNumber }
                if (targetOrder != null) {
                    ordersToPay = listOf(targetOrder)
                }
            }
        )
    }

    if (uiState.transferAlertDuplicateMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearTransferDuplicateMsg() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Amber500)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transferencia Ya Registrada", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(uiState.transferAlertDuplicateMsg!!) },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearTransferDuplicateMsg() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("ENTENDIDO")
                }
            }
        )
    }
}

@Composable
fun ComandaSmsAlertDialog(
    alert: com.example.ui.viewmodel.PendingComandaSmsAlert,
    onDismiss: () -> Unit,
    onViewOrder: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = if (alert.isUpdate) Amber600 else Emerald600,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (alert.isUpdate) "COMANDA ACTUALIZADA POR SMS" else "COMANDA NUEVA RECIBIDA POR SMS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (alert.isUpdate) Color(0xFFFEF3C7) else Color(0xFFD1FAE5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (alert.isUpdate) "Comanda #${alert.comandaNumber} (ACTUALIZADA)" else "Comanda #${alert.comandaNumber}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = if (alert.isUpdate) Color(0xFFB45309) else Emerald800
                        )
                        Text(
                            text = "Enviada por: ${alert.senderFullName} (@${alert.senderUsername})",
                            fontSize = 12.sp,
                            color = Slate700
                        )
                    }
                }

                Text(
                    text = if (alert.isUpdate) "Productos actualizados (Lista completa de la comanda):" else "Productos recibidos (Información legible para humanos):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Slate700
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    alert.itemsSummary.forEach { itemText ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("•", fontWeight = FontWeight.Bold, color = if (alert.isUpdate) Amber600 else Emerald600)
                            Text(text = itemText, fontSize = 12.sp, color = Slate800)
                        }
                    }
                }

                HorizontalDivider(color = Slate200)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Actualizado:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate700)
                    Text(
                        text = "$${"%.2f".format(alert.totalAmount)} CUP",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Emerald700
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Amber50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Amber600,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (alert.isUpdate) "Esta comanda ha sido actualizada en la lista POR COBRAR con la nueva lista de productos." else "Esta comanda ha sido incorporada a la lista POR COBRAR en estado Pendiente.",
                            fontSize = 11.sp,
                            color = Amber800
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onViewOrder(alert.orderId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("VER COMANDA POR COBRAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ENTENDIDO", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun CajeroSideNavigation(
    selectedTab: CajeroTab,
    onTabSelected: (CajeroTab) -> Unit
) {
    Surface(
        color = Color.White,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier
            .fillMaxHeight()
            .width(88.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top)
        ) {
            val items = listOf(
                Triple(CajeroTab.COBRAR, "Cobrar", Icons.Outlined.PointOfSale),
                Triple(CajeroTab.CATALOGO, "Catálogo", Icons.Outlined.MenuBook),
                Triple(CajeroTab.HISTORIAL, "Historial", Icons.Outlined.History),
                Triple(CajeroTab.INFORME, "Informe", Icons.Outlined.Analytics),
                Triple(CajeroTab.CUADRE, "Cuadre", Icons.Outlined.AccountBalanceWallet),
                Triple(CajeroTab.AJUSTES, "Ajustes", Icons.Outlined.Settings)
            )

            items.forEach { (tab, label, icon) ->
                val isSelected = selectedTab == tab
                val color = if (isSelected) ElQadreNavy else Slate400
                val fontW = if (isSelected) FontWeight.Bold else FontWeight.Medium
                val bgColor = if (isSelected) ElQadreGold.copy(alpha = 0.2f) else Color.Transparent

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, shape = RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("tab_side_${label.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = fontW,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CajeroBottomNavigation(
    selectedTab: CajeroTab,
    onTabSelected: (CajeroTab) -> Unit
) {
    Surface(
        color = Color.White,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple(CajeroTab.COBRAR, "Cobrar", Icons.Outlined.PointOfSale),
                Triple(CajeroTab.CATALOGO, "Catálogo", Icons.Outlined.MenuBook),
                Triple(CajeroTab.HISTORIAL, "Historial", Icons.Outlined.History),
                Triple(CajeroTab.INFORME, "Informe", Icons.Outlined.Analytics),
                Triple(CajeroTab.CUADRE, "Cuadre", Icons.Outlined.AccountBalanceWallet),
                Triple(CajeroTab.AJUSTES, "Ajustes", Icons.Outlined.Settings)
            )

            items.forEach { (tab, label, icon) ->
                val isSelected = selectedTab == tab
                val color = if (isSelected) ElQadreNavy else Slate400
                val fontW = if (isSelected) FontWeight.Bold else FontWeight.Medium

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("tab_${label.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = fontW
                    )
                }
            }
        }
    }
}

@Composable
fun AperturaJornadaPane(
    uiState: MainUiState,
    onOpenJornada: (Double) -> Unit
) {
    var initialCashText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, Slate100),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElQadreNavy.copy(alpha = 0.08f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Apertura de Jornada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = ElQadreNavy
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Establece el fondo inicial para comenzar a registrar comandas y cobros en caja.",
                    color = Slate500,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = initialCashText,
                    onValueChange = {
                        initialCashText = it
                        showError = false
                    },
                    label = { Text("Fondo Inicial de Caja (CUP)") },
                    placeholder = { Text("Ej. 5000") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AttachMoney,
                            contentDescription = null,
                            tint = ElQadreNavy
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fondo_inicial_input")
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Por favor ingresa un monto inicial válido mayor o igual a 0",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val cash = initialCashText.toDoubleOrNull()
                        if (cash != null && cash >= 0.0) {
                            onOpenJornada(cash)
                        } else {
                            showError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_iniciar_jornada")
                ) {
                    Text(
                        text = "Iniciar Jornada de Trabajo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CatalogoCajaPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onComandaCreated: () -> Unit
) {
    val tasaUSD = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEUR = uiState.generalConfig?.tasaEur ?: 0.0

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    val cartSelection = remember { mutableStateListOf<SalonCartItem>() }

    var productToCustomize by remember { mutableStateOf<Product?>(null) }
    var customizeQuantity by remember { mutableStateOf(1) }
    val selectedAgregados = remember { mutableStateListOf<Pair<String, Double>>() }
    var customNotes by remember { mutableStateOf("") }

    val allCategories = remember(uiState.products) {
        listOf("Todos") + uiState.products.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredProducts = remember(uiState.products, selectedCategory, searchQuery) {
        uiState.products.filter { prod ->
            val matchCategory = if (selectedCategory == "Todos") true else prod.category.equals(selectedCategory, ignoreCase = true)
            val matchQuery = if (searchQuery.isBlank()) true else prod.name.contains(searchQuery, ignoreCase = true) || prod.code.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val totalSelectionAmount = remember(cartSelection.toList()) {
        cartSelection.sumOf { it.totalItemPrice }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (cartSelection.isNotEmpty()) 130.dp else 16.dp)
        ) {
            // Header & Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Catálogo de Productos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ElQadreNavy
                )
                Text(
                    text = "Selección rápida para generar comandas de caja",
                    fontSize = 12.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre o código...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = Slate400
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Slate400)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate200
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("catalogo_search_input")
                )
            }

            // Categories horizontal scroll
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allCategories) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) ElQadreNavy else Slate100,
                        modifier = Modifier
                            .clickable { selectedCategory = cat }
                            .testTag("cat_chip_$cat")
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else Slate700,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Products Grid / List
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Slate300, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No se encontraron productos en el catálogo", color = Slate500, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts) { prod ->
                        val isCocina = prod.destination == "COCINA" || prod.category.contains("Cocina", ignoreCase = true) || prod.category.contains("Postres", ignoreCase = true)
                        val isBarra = prod.destination == "BARRA" || prod.category.contains("Bebidas", ignoreCase = true)
                        val supportsAgregados = isCocina || isBarra

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    productToCustomize = prod
                                    customizeQuantity = 1
                                    selectedAgregados.clear()
                                    customNotes = ""
                                }
                                .testTag("product_card_${prod.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (prod.destination == "COCINA") Rose100 else Amber100
                                        ) {
                                            Text(
                                                text = prod.destination,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (prod.destination == "COCINA") Rose600 else Amber800,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (supportsAgregados) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Emerald50
                                            ) {
                                                Text(
                                                    text = "Admite Agregados",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Emerald800,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Stock: ${prod.stock}",
                                            fontSize = 11.sp,
                                            color = Slate400
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$${"%.2f".format(prod.price)} CUP",
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy,
                                            fontSize = 15.sp
                                        )
                                        if (tasaUSD > 0 || tasaEUR > 0) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (tasaUSD > 0) {
                                                    Text(
                                                        text = "$${"%.2f".format(prod.price / tasaUSD)} USD",
                                                        fontSize = 10.sp,
                                                        color = Slate500,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                if (tasaEUR > 0) {
                                                    Text(
                                                        text = "• €${"%.2f".format(prod.price / tasaEUR)} EUR",
                                                        fontSize = 10.sp,
                                                        color = Slate500,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ElQadreNavy.copy(alpha = 0.08f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Seleccionar",
                                                tint = ElQadreNavy,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Accumulated Selection Tray / Panel
        if (cartSelection.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Summary header with clear button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Comanda en Selección (${cartSelection.sumOf { it.quantity }} items)",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                fontSize = 13.sp
                            )
                        }
                        TextButton(
                            onClick = { cartSelection.clear() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Limpiar", color = Rose600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Compact scroll of current selected items
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(cartSelection) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate50, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.product.name} x${item.quantity}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                    if (item.selectedAgregados.isNotEmpty()) {
                                        Text(
                                            text = "+ " + item.selectedAgregados.joinToString(", ") { "${it.first} ($${"%.0f".format(it.second)})" },
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$${"%.2f".format(item.totalItemPrice)} CUP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ElQadreNavy
                                    )
                                    IconButton(
                                        onClick = { cartSelection.remove(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Rose600, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // Total & Aceptar Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL COMANDA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            Text(
                                text = "$${"%.2f".format(totalSelectionAmount)} CUP",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.crearComandaCaja(
                                    tableNumber = 0,
                                    customerName = "Comanda Caja",
                                    cartItems = cartSelection.toList(),
                                    onComplete = {
                                        cartSelection.clear()
                                        onComandaCreated()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreGold,
                                contentColor = ElQadreNavy
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_aceptar_comanda")
                        ) {
                            Text("Aceptar (Crear Comanda)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal to customize product (quantity + agregados)
    if (productToCustomize != null) {
        val prod = productToCustomize!!
        val isCocina = prod.destination == "COCINA" || prod.category.contains("Cocina", ignoreCase = true) || prod.category.contains("Postres", ignoreCase = true)
        val isBarra = prod.destination == "BARRA" || prod.category.contains("Bebidas", ignoreCase = true)

        val agregadosDisponibles = remember(prod.admitsAgregados, prod.agregadosList) {
            if (!prod.admitsAgregados) emptyList() else {
                try {
                    val arr = org.json.JSONArray(prod.agregadosList)
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

        val agregadosSum = selectedAgregados.sumOf { it.second }
        val unitPriceWithAgregados = prod.price + agregadosSum
        val lineSubtotal = unitPriceWithAgregados * customizeQuantity

        AlertDialog(
            onDismissRequest = { productToCustomize = null },
            title = {
                Text(
                    text = prod.name,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Precio Base:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(prod.price)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                    }

                    // Quantity controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cantidad:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (customizeQuantity > 1) customizeQuantity-- },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Slate100, CircleShape)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                            Text(
                                text = "$customizeQuantity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { customizeQuantity++ },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Slate100, CircleShape)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                        }
                    }

                    // Agregados options
                    if (agregadosDisponibles.isNotEmpty()) {
                        HorizontalDivider(color = Slate100)
                        Text(
                            text = "Agregados disponibles (Catálogo):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ElQadreNavy
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(agregadosDisponibles) { (name, price) ->
                                val isSelected = selectedAgregados.any { it.first == name }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) {
                                                selectedAgregados.removeAll { it.first == name }
                                            } else {
                                                selectedAgregados.add(name to price)
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedAgregados.add(name to price)
                                            } else {
                                                selectedAgregados.removeAll { it.first == name }
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                    )
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        color = ElQadreNavy,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "+$${"%.2f".format(price)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreGoldDark
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal Línea:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Text("$${"%.2f".format(lineSubtotal)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreNavy)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newItem = SalonCartItem(
                            product = prod,
                            quantity = customizeQuantity,
                            selectedAgregados = selectedAgregados.toList(),
                            customNotes = customNotes
                        )
                        val existingIndex = cartSelection.indexOfFirst {
                            it.product.id == prod.id && it.selectedAgregados == newItem.selectedAgregados
                        }
                        if (existingIndex >= 0) {
                            val cur = cartSelection[existingIndex]
                            cartSelection[existingIndex] = cur.copy(quantity = cur.quantity + customizeQuantity)
                        } else {
                            cartSelection.add(newItem)
                        }
                        productToCustomize = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Agregar a Selección", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToCustomize = null }) {
                    Text("Cancelar", color = Slate500, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CobrarCajaPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onRegistrarCobroClick: () -> Unit,
    onOpenConteoBilletes: () -> Unit,
    onPayOrdersClick: (List<TableOrder>) -> Unit
) {
    val context = LocalContext.current
    val activeJornada = uiState.activeJornada ?: return

    val selectedOrderIds = remember { mutableStateListOf<Long>() }

    val ordersOfJornada = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada.id }
    }

    val comandasCobradas = remember(ordersOfJornada) {
        ordersOfJornada.count { it.status == "COBRADA" }
    }

    val ventasTotales = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }.sumOf { it.totalAmount }
    }

    val ventasCocina = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }.sumOf { it.totalCocina }
    }

    val ventasBarra = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }.sumOf { it.totalBarra }
    }

    val cajaEsperada = activeJornada.initialCash + ventasTotales

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val openOrdersList = remember(uiState.openOrders, activeJornada.id) {
        uiState.openOrders.filter { it.jornadaId == activeJornada.id }
    }

    val selectedOrders = remember(openOrdersList, selectedOrderIds.toList()) {
        openOrdersList.filter { it.id in selectedOrderIds }
    }

    val totalSelectedAmount = remember(selectedOrders) {
        selectedOrders.sumOf { it.totalAmount }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = if (selectedOrderIds.isNotEmpty()) 120.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Main Big Button: REGISTRAR COBRO (Cobro Rápido)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ElQadreGold,
                    onClick = onRegistrarCobroClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_registrar_cobro")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PointOfSale,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "REGISTRAR COBRO RÁPIDO",
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
                            color = ElQadreNavy
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Cobro directo en caja sin comanda previa",
                            fontSize = 12.sp,
                            color = ElQadreNavy.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                // Conteo de Billetes Quick Access Card in Cobrar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    onClick = onOpenConteoBilletes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_conteo_billetes_cobrar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ElQadreGoldSoft,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Payments,
                                        contentDescription = null,
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "CONTEO DE BILLETES",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "Desglose por denominación y compartir resumen",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Slate400
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Resumen de la Jornada",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                // Metrics Summary Grid
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate100),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("CAJA ESPERADA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Text(
                                    text = "$${"%.2f".format(cajaEsperada)} CUP",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    modifier = Modifier.testTag("caja_esperada_summary")
                                )
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = ElQadreGoldSoft, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.AttachMoney, contentDescription = null, tint = ElQadreNavy)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("VENTAS TOTALES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$${"%.2f".format(ventasTotales)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    modifier = Modifier.testTag("ventas_totales_summary")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("COMANDAS COBRADAS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$comandasCobradas",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    modifier = Modifier.testTag("comandas_cobradas_summary")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("SALÓN / COCINA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$${"%.2f".format(ventasCocina)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    modifier = Modifier.testTag("ventas_cocina_summary")
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("VENTAS DE BARRA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$${"%.2f".format(ventasBarra)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    modifier = Modifier.testTag("ventas_barra_summary")
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Section Title: Comandas Pendientes de Cobro
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comandas Pendientes de Cobro (${openOrdersList.size})",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 15.sp
                    )
                    if (openOrdersList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedOrderIds.size == openOrdersList.size) {
                                    selectedOrderIds.clear()
                                } else {
                                    selectedOrderIds.clear()
                                    selectedOrderIds.addAll(openOrdersList.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedOrderIds.size == openOrdersList.size) "Deseleccionar todas" else "Seleccionar todas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }
                }
            }

            if (openOrdersList.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate100),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Emerald600.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No hay comandas pendientes de cobro",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Las comandas generadas en Salón o en Catálogo aparecerán aquí para ser cobradas.",
                                color = Slate500,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(openOrdersList) { order ->
                    val isSelected = selectedOrderIds.contains(order.id)
                    var isExpanded by remember { mutableStateOf(false) }
                    val orderItems = remember(uiState.allOrderItems, order.id) {
                        uiState.allOrderItems.filter { it.orderId == order.id }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ElQadreGold else Slate100),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_order_card_${order.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedOrderIds.add(order.id)
                                            else selectedOrderIds.remove(order.id)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy),
                                        modifier = Modifier.testTag("order_checkbox_${order.id}")
                                    )

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Comanda #${order.comandaNumber}",
                                                fontWeight = FontWeight.Black,
                                                color = ElQadreNavy,
                                                fontSize = 15.sp
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if ((order.tableNumber ?: 0) > 0) ElQadreNavy.copy(alpha = 0.08f) else Amber100
                                            ) {
                                                Text(
                                                    text = if ((order.tableNumber ?: 0) > 0) "Mesa ${order.tableNumber}" else (if (order.customerName.isNotBlank()) order.customerName else "Caja"),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if ((order.tableNumber ?: 0) > 0) ElQadreNavy else Amber800,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Por: @${order.waiterUsername} • ${timeFormatter.format(Date(order.createdAt))}",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$${"%.2f".format(order.totalAmount)} CUP",
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy,
                                        fontSize = 16.sp
                                    )
                                    IconButton(
                                        onClick = { isExpanded = !isExpanded },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Detalles",
                                            tint = Slate400
                                        )
                                    }
                                }
                            }

                            // Items breakdown
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Slate100)
                                Spacer(modifier = Modifier.height(8.dp))

                                if (orderItems.isEmpty()) {
                                    Text("Sin detalle de productos", fontSize = 11.sp, color = Slate400)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        orderItems.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${item.productName} x${item.quantity}",
                                                        fontSize = 12.sp,
                                                        color = ElQadreNavy,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (item.notes.isNotBlank()) {
                                                        Text(
                                                            text = item.notes,
                                                            fontSize = 10.sp,
                                                            color = Slate500
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "$${"%.2f".format(item.unitPrice * item.quantity)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElQadreNavy
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Bottom Bar for Multi-Comanda Cobro
        if (selectedOrderIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color.White,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL SELECCIONADO (${selectedOrders.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                        Text(
                            text = "$${"%.2f".format(totalSelectedAmount)} CUP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            modifier = Modifier.testTag("cobrar_total_seleccionado")
                        )
                    }
                    Button(
                        onClick = {
                            onPayOrdersClick(selectedOrders)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreGold,
                            contentColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_cobrar_seleccionadas")
                    ) {
                        Text(
                            text = "Cobrar (${selectedOrders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistorialCajaPane(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val activeJornada = uiState.activeJornada
    var subTab by remember { mutableStateOf(0) } // 0 = Jornada Actual, 1 = Transferencias SMS, 2 = Archivo
    var expandedOrderId by remember { mutableStateOf<Long?>(null) }
    var selectedArchivedJornada by remember { mutableStateOf<com.example.data.local.model.Jornada?>(null) }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selector de Sub-pestañas (Segmented control)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate100, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (subTab == 0) ElQadreNavy else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { subTab = 0 }
                    .padding(vertical = 10.dp)
                    .testTag("tab_historial_comandas"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Comandas",
                    color = if (subTab == 0) Color.White else Slate500,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .background(if (subTab == 1) ElQadreNavy else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { subTab = 1 }
                    .padding(vertical = 10.dp)
                    .testTag("tab_historial_transferencias"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Transferencias",
                        color = if (subTab == 1) Color.White else Slate500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    val unassociatedCount = if (activeJornada != null) {
                        uiState.allTransferencias.count { it.jornadaId == activeJornada.id && it.status == "NO ASOCIADA" }
                    } else 0
                    if (unassociatedCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Amber500
                        ) {
                            Text(
                                text = "$unassociatedCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (subTab == 2) ElQadreNavy else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { subTab = 2 }
                    .padding(vertical = 10.dp)
                    .testTag("tab_historial_archivo"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Archivo",
                    color = if (subTab == 2) Color.White else Slate500,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (subTab == 0) {
            // JORNADA ACTUAL
            if (activeJornada == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.History, null, tint = Slate300, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No hay jornada de caja activa en este momento.", color = Slate500, fontSize = 13.sp)
                    }
                }
            } else {
                val closedOrders = remember(uiState.allOrders, activeJornada) {
                    uiState.allOrders.filter { it.jornadaId == activeJornada.id && it.status == "COBRADA" }
                }

                Text(
                    text = "Comandas Cobradas (Turno Abierto)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ElQadreNavy
                )

                if (closedOrders.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = Slate300, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Aún no hay comandas cobradas en este turno.", color = Slate500, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(closedOrders) { order ->
                            val isExpanded = expandedOrderId == order.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate100),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedOrderId = if (isExpanded) null else order.id }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Comanda #${order.comandaNumber} (Mesa ${order.tableNumber})",
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElQadreNavy,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Emerald600.copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        text = order.paymentMethod,
                                                        fontSize = 9.sp,
                                                        color = Emerald600,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Cajero: @${order.waiterUsername} | Hora: ${if (order.closedAt != null) timeFormatter.format(Date(order.closedAt)) else "--:--"}",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$${"%.2f".format(order.totalAmount)} CUP",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = ElQadreNavy
                                            )
                                            if (order.tip > 0) {
                                                Text(
                                                    text = "+ Propina: $${"%.2f".format(order.tip)}",
                                                    fontSize = 10.sp,
                                                    color = Emerald600,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Slate100)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Breakdowns
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Importe Cocina:", fontSize = 11.sp, color = Slate500)
                                            Text("$${"%.2f".format(order.totalCocina)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Importe Barra:", fontSize = 11.sp, color = Slate500)
                                            Text("$${"%.2f".format(order.totalBarra)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Productos:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)

                                        val orderItems = remember(uiState.allOrderItems, order.id) {
                                            uiState.allOrderItems.filter { it.orderId == order.id }
                                        }

                                        orderItems.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("- ${item.productName} (x${item.quantity})", fontSize = 11.sp, color = Slate700)
                                                    if (item.notes.isNotEmpty()) {
                                                        Text("  Notas: ${item.notes}", fontSize = 9.sp, color = Slate400)
                                                    }
                                                }
                                                Text("$${"%.2f".format(item.unitPrice * item.quantity)} CUP", fontSize = 11.sp, color = Slate700)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (subTab == 1) {
            // SUB-TAB: RECEPCIÓN DE TRANSFERENCIAS
            TransferenciasPane(
                uiState = uiState,
                viewModel = viewModel
            )
        } else {
            // ARCHIVO HISTORICO DE JORNADAS
            val closedJornadas = remember(uiState.allJornadas) {
                uiState.allJornadas.filter { !it.isOpen || it.closedAt != null }
            }

            Text(
                text = "Archivo Histórico de Turnos Cerrados",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = ElQadreNavy
            )

            if (closedJornadas.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Archive, null, tint = Slate300, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aún no hay turnos archivados.", color = Slate500, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(closedJornadas) { jornada ->
                        val ordersForJornada = remember(uiState.allOrders, jornada.id) {
                            uiState.allOrders.filter { it.jornadaId == jornada.id && it.status == "COBRADA" }
                        }

                        val totalVentas = ordersForJornada.sumOf { it.totalAmount }
                        val totalCocina = ordersForJornada.sumOf { it.totalCocina }
                        val totalBarra = ordersForJornada.sumOf { it.totalBarra }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate100),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedArchivedJornada = jornada }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Turno #${jornada.id} - ${dateOnlyFormatter.format(Date(jornada.openedAt))}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ElQadreNavy
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Apertura: ${timeFormatter.format(Date(jornada.openedAt))} | Cierre: ${if (jornada.closedAt != null) timeFormatter.format(Date(jornada.closedAt)) else "--:--"}",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                        Text(
                                            text = "Cajero: @${jornada.openedBy}${if (jornada.closedBy != null) " & @${jornada.closedBy}" else ""}",
                                            fontSize = 11.sp,
                                            color = Slate400
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$${"%.2f".format(totalVentas)} CUP",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "${ordersForJornada.size} comandas",
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Slate50)
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fondo Inicial: $${"%.2f".format(jornada.initialCash)} CUP", fontSize = 10.sp, color = Slate500)
                                    Text("Cocina: $${"%.2f".format(totalCocina)} | Barra: $${"%.2f".format(totalBarra)}", fontSize = 10.sp, color = Slate500)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOGO DETALLADO DE JORNADA ARCHIVADA
    if (selectedArchivedJornada != null) {
        val archived = selectedArchivedJornada!!
        val ordersForJornada = remember(uiState.allOrders, archived.id) {
            uiState.allOrders.filter { it.jornadaId == archived.id && it.status == "COBRADA" }
        }
        val totalVentas = ordersForJornada.sumOf { it.totalAmount }
        val totalCocina = ordersForJornada.sumOf { it.totalCocina }
        val totalBarra = ordersForJornada.sumOf { it.totalBarra }
        val totalPropinas = ordersForJornada.sumOf { it.tip }

        var nestedExpandedOrderId by remember { mutableStateOf<Long?>(null) }

        Dialog(
            onDismissRequest = { selectedArchivedJornada = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = ElQadreBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Archivo: Jornada #${archived.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Fecha: ${dateFormatter.format(Date(archived.openedAt))}",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        IconButton(onClick = { selectedArchivedJornada = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Resumen Financiero de Jornada Archivada
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate100),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Resumen Financiero", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                            HorizontalDivider(color = Slate100)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Apertura por / Hora:", fontSize = 11.sp, color = Slate500)
                                Text("@${archived.openedBy} a las ${timeFormatter.format(Date(archived.openedAt))}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cierre por / Hora:", fontSize = 11.sp, color = Slate500)
                                val closeTimeStr = if (archived.closedAt != null) "@${archived.closedBy} a las ${timeFormatter.format(Date(archived.closedAt))}" else "--"
                                Text(closeTimeStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fondo Inicial:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(archived.initialCash)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Cobrado:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(totalVentas)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ventas Cocina / Barra:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(totalCocina)} / $${"%.2f".format(totalBarra)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Propinas Acumuladas:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(totalPropinas)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                            }

                            if (archived.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Notas de Cierre:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Text(archived.notes, fontSize = 11.sp, color = Slate600)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Comandas Archivadas (${ordersForJornada.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (ordersForJornada.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No hay comandas registradas en esta jornada.", color = Slate500, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ordersForJornada) { order ->
                                val isNestedExpanded = nestedExpandedOrderId == order.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate100),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { nestedExpandedOrderId = if (isNestedExpanded) null else order.id }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Comanda #${order.comandaNumber} (Mesa ${order.tableNumber})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = ElQadreNavy
                                                )
                                                Text(
                                                    text = "Cajero: @${order.waiterUsername} | Método: ${order.paymentMethod}",
                                                    fontSize = 10.sp,
                                                    color = Slate500
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                if (order.currency.isNotEmpty() && order.currency != "CUP" && order.amountInCurrency > 0) {
                                                    Text(
                                                        text = "$${"%.2f".format(order.amountInCurrency)} ${order.currency}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Emerald700
                                                    )
                                                    Text(
                                                        text = "≈ $${"%.2f".format(order.totalAmount)} CUP",
                                                        fontSize = 10.sp,
                                                        color = Slate400
                                                    )
                                                } else {
                                                    Text(
                                                        text = "$${"%.2f".format(order.totalAmount)} CUP",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = ElQadreNavy
                                                    )
                                                }
                                            }
                                        }

                                        if (isNestedExpanded) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = Slate50)
                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Cocina: $${"%.2f".format(order.totalCocina)} CUP", fontSize = 10.sp, color = Slate500)
                                                Text("Barra: $${"%.2f".format(order.totalBarra)} CUP", fontSize = 10.sp, color = Slate500)
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            val orderItems = remember(uiState.allOrderItems, order.id) {
                                                uiState.allOrderItems.filter { it.orderId == order.id }
                                            }

                                            orderItems.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("- ${item.productName} (x${item.quantity})", fontSize = 10.sp, color = Slate700)
                                                    Text("$${"%.2f".format(item.unitPrice * item.quantity)} CUP", fontSize = 10.sp, color = Slate700)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InformeCajaPane(
    uiState: MainUiState
) {
    val activeJornada = uiState.activeJornada
    if (activeJornada == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Analytics, null, tint = Slate300, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No hay ninguna jornada activa para mostrar informes.", color = Slate500, fontSize = 14.sp)
            }
        }
        return
    }

    val context = LocalContext.current

    val ordersOfJornada = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada.id }
    }

    val closedOrders = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }
    }

    val ventasTotales = closedOrders.sumOf { it.totalAmount }
    val ventasEfectivo = closedOrders.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.totalAmount }
    val ventasTransferencia = closedOrders.filter { it.paymentMethod == "TRANSFERENCIA" }.sumOf { it.totalAmount }
    val propinasAcumuladas = closedOrders.sumOf { it.tip }

    val ordersCUP = remember(closedOrders) { closedOrders.filter { it.currency == "CUP" || it.currency.isBlank() } }
    val ordersUSD = remember(closedOrders) { closedOrders.filter { it.currency == "USD" } }
    val ordersEUR = remember(closedOrders) { closedOrders.filter { it.currency == "EUR" } }

    val totalCUP = remember(ordersCUP) { ordersCUP.sumOf { it.totalAmount } }
    val totalUSD = remember(ordersUSD) { ordersUSD.sumOf { if (it.amountInCurrency > 0) it.amountInCurrency else if (it.exchangeRate > 0) it.totalAmount / it.exchangeRate else 0.0 } }
    val totalEUR = remember(ordersEUR) { ordersEUR.sumOf { if (it.amountInCurrency > 0) it.amountInCurrency else if (it.exchangeRate > 0) it.totalAmount / it.exchangeRate else 0.0 } }

    val closedOrderIds = remember(closedOrders) { closedOrders.map { it.id }.toSet() }
    val closedOrderItems = remember(uiState.allOrderItems, closedOrderIds) {
        uiState.allOrderItems.filter { it.orderId in closedOrderIds }
    }

    val productSales = remember(closedOrderItems) {
        closedOrderItems.groupBy { it.productName }
            .mapValues { entry ->
                val qty = entry.value.sumOf { it.quantity }
                val tot = entry.value.sumOf { it.unitPrice * it.quantity }
                Pair(qty, tot)
            }.toList().sortedByDescending { pair -> pair.second.first }
    }

    val ventasCocina = closedOrders.sumOf { it.totalCocina }
    val ventasBarra = closedOrders.sumOf { it.totalBarra }

    var showAppReportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Informe del Turno",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ElQadreNavy
            )
            Text(
                text = "Resumen de operaciones de la jornada actual #${activeJornada.id}.",
                color = Slate500,
                fontSize = 12.sp
            )
        }

        // ACCIONES PRINCIPALES (Botonera de Reportes)

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Acciones de Reporte", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                    HorizontalDivider(color = Slate100)

                    // VER INFORME
                    Button(
                        onClick = { showAppReportDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_ver_informe_pantalla"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VER INFORME", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // DESCARGAR PDF
                        Button(
                            onClick = {
                                val jornadaTransfers = uiState.allTransferencias.filter { it.jornadaId == activeJornada.id }
                                val file = com.example.util.CajaReportPdfExporter.exportCajaReport(
                                    context, activeJornada, closedOrders, uiState.allOrderItems, activeJornada.openedBy,
                                    transferencias = jornadaTransfers
                                )
                                if (file != null) {
                                    android.widget.Toast.makeText(context, "PDF descargado: ${file.name}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_descargar_pdf_informe"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = ElQadreNavy)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DESCARGAR PDF", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                        }

                        // ENVIAR A ADMINISTRADOR
                        Button(
                            onClick = {
                                val adminPhone = uiState.businessConfig?.telefono
                                if (adminPhone.isNullOrBlank()) {
                                    android.widget.Toast.makeText(context, "No se puede enviar: El número del Administrador no está configurado.", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    val jornadaTransfers = uiState.allTransferencias.filter { it.jornadaId == activeJornada.id }
                                    val file = com.example.util.CajaReportPdfExporter.exportCajaReport(
                                        context, activeJornada, closedOrders, uiState.allOrderItems, activeJornada.openedBy,
                                        transferencias = jornadaTransfers
                                    )
                                    if (file != null) {
                                        com.example.util.CajaReportPdfExporter.shareCajaReportToAdmin(context, file, adminPhone)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_enviar_administrador"),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ENVIAR A ADMIN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Resumen de Propinas y Comandas

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Métricas de Operación",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 14.sp
                    )
                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fondo Inicial:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(activeJornada.initialCash)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Comandas Cobradas:", fontSize = 12.sp, color = Slate500)
                        Text("${closedOrders.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Monto Total Facturado:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(ventasTotales)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ventas Cocina:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(ventasCocina)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ventas Barra:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(ventasBarra)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }
                }
            }
        }

        // Ventas por Método de Pago

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ventas por Método de Pago",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 14.sp
                    )
                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cobrado en Efectivo:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(ventasEfectivo)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cobrado Transferencia:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(ventasTransferencia)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Propinas Totales Acumuladas:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(propinasAcumuladas)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                    }
                }
            }
        }

        // Ventas por Moneda

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ventas por Moneda",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 14.sp
                    )
                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cobrado en CUP:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(totalCUP)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cobrado en USD:", fontSize = 12.sp, color = Slate500)
                        Text("$${"%.2f".format(totalUSD)} USD", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cobrado en EUR:", fontSize = 12.sp, color = Slate500)
                        Text("€${"%.2f".format(totalEUR)} EUR", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }
                }
            }
        }

        // Ventas por Destino (Cocina vs Barra)

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ventas por Destino de Preparación",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Cocina Progress
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cocina:", fontSize = 12.sp, color = Slate500)
                            Text("$${"%.2f".format(ventasCocina)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val pctCocina = if (ventasTotales > 0) (ventasCocina / ventasTotales).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { pctCocina },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = ElQadreNavy,
                            trackColor = Slate100,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Barra Progress
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Barra / Bebidas:", fontSize = 12.sp, color = Slate500)
                            Text("$${"%.2f".format(ventasBarra)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val pctBarra = if (ventasTotales > 0) (ventasBarra / ventasTotales).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { pctBarra },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = ElQadreGold,
                            trackColor = Slate100,
                        )
                    }
                }
            }
        }

        // Productos Vendidos
        if (productSales.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Productos Más Vendidos",
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            productSales.forEach { (name, stats) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        Text("${stats.first} uds", fontSize = 10.sp, color = Slate500)
                                    }
                                    Text("$${"%.2f".format(stats.second)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOGO IMPRIMIBLE: VER INFORME COMPLETO
    if (showAppReportDialog) {
        val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val consumoReportItems = remember(uiState.consumoPersonalList, activeJornada) {
            uiState.consumoPersonalList.filter { it.jornadaId == activeJornada.id }
        }
        val totalConsumoReport = consumoReportItems.sumOf { it.totalAmount }

        Dialog(
            onDismissRequest = { showAppReportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "EL QADRE",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Informe Oficial de Cierre de Caja",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        IconButton(onClick = { showAppReportDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Printable area
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate100),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("INFORMACIÓN DE CONTROL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Slate500)
                                    HorizontalDivider(color = Slate100)
                                    Text("Jornada ID: #${activeJornada.id}", fontSize = 11.sp, color = Slate700)
                                    Text("Cajero de Apertura: @${activeJornada.openedBy}", fontSize = 11.sp, color = Slate700)
                                    Text("Fecha de Apertura: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(activeJornada.openedAt))}", fontSize = 11.sp, color = Slate700)
                                    Text("Fondo Inicial: $${"%.2f".format(activeJornada.initialCash)} CUP", fontSize = 11.sp, color = Slate700)
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate100),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("RESUMEN DE OPERACIONES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Slate500)
                                    HorizontalDivider(color = Slate100)

                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Comandas Cobradas:", fontSize = 11.sp, color = Slate600)
                                        Text("${closedOrders.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Ventas de Cocina:", fontSize = 11.sp, color = Slate600)
                                        Text("$${"%.2f".format(ventasCocina)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Ventas de Barra:", fontSize = 11.sp, color = Slate600)
                                        Text("$${"%.2f".format(ventasBarra)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Cobrado en Efectivo:", fontSize = 11.sp, color = Slate600)
                                        Text("$${"%.2f".format(ventasEfectivo)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Cobrado en Transferencia:", fontSize = 11.sp, color = Slate600)
                                        Text("$${"%.2f".format(ventasTransferencia)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Propinas Acumuladas:", fontSize = 11.sp, color = Slate600)
                                        Text("$${"%.2f".format(propinasAcumuladas)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                                    }
                                    if (totalConsumoReport > 0) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("Consumo Real Personal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                            Text("-$${"%.2f".format(totalConsumoReport)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                        }
                                    }
                                    HorizontalDivider(color = Slate100)
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("TOTAL FACTURADO NETO:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        Text("$${"%.2f".format(ventasTotales)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                    }
                                }
                            }
                        }

                        if (consumoReportItems.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("CONSUMO DE PERSONAL REGISTRADO", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                                        HorizontalDivider(color = Color(0xFFFDE68A))
                                        consumoReportItems.forEach { item ->
                                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                                Text("- ${item.productName} (x${item.quantity})", fontSize = 10.sp, color = Slate700)
                                                Text("-$${"%.2f".format(item.totalAmount)} CUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFFDE68A))
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("TOTAL DEDUCIDO DE CAJA:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                            Text("-$${"%.2f".format(totalConsumoReport)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("DETALLE DE COMANDAS COBRADAS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                        }

                        items(closedOrders) { order ->
                            val items = remember(uiState.allOrderItems, order.id) {
                                uiState.allOrderItems.filter { it.orderId == order.id }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate100),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("Comanda #${order.comandaNumber} - Mesa ${order.tableNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        Text("$${"%.2f".format(order.totalAmount)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                    Text("Cajero: @${order.waiterUsername} | Hora: ${if (order.closedAt != null) timeFormatter.format(Date(order.closedAt)) else "--:--"} | Método: ${order.paymentMethod}", fontSize = 9.sp, color = Slate500)
                                    Text("Cocina: $${"%.2f".format(order.totalCocina)} | Barra: $${"%.2f".format(order.totalBarra)}", fontSize = 9.sp, color = Slate500)
                                    HorizontalDivider(color = Slate50)
                                    items.forEach { item ->
                                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                            Text("- ${item.productName} (x${item.quantity})", fontSize = 9.5.sp, color = Slate700)
                                            Text("$${"%.2f".format(item.unitPrice * item.quantity)} CUP", fontSize = 9.5.sp, color = Slate700)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showAppReportDialog = false },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CERRAR VISTA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CuadreCajaPane(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val activeJornada = uiState.activeJornada
    val isJornadaOpen = activeJornada != null && activeJornada.isOpen

    if (!isJornadaOpen || activeJornada == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Text(
                            text = "JORNADA CERRADA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }

                    Text(
                        text = "Cuadre de Caja Bloqueado",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "El Cuadre de Caja solo puede utilizarse cuando exista una JORNADA ABIERTA.\n\nPrimero debe abrir la jornada para poder realizar el arqueo y registrar el cuadre de caja.",
                        fontSize = 14.sp,
                        color = Slate700,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("SessionPrefs", android.content.Context.MODE_PRIVATE) }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Salón, 1 = Barra

    // Read saved reconciliation state from SharedPreferences
    var salonCuadreRegistrado by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getBoolean("salon_cuadre_registrado_${activeJornada.id}", false))
    }
    var salonRealCashSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getFloat("salon_cuadre_real_cash_${activeJornada.id}", 0.0f).toDouble())
    }
    var salonExpectedCashSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getFloat("salon_cuadre_expected_cash_${activeJornada.id}", 0.0f).toDouble())
    }
    var salonDiferenciaSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getFloat("salon_cuadre_diferencia_${activeJornada.id}", 0.0f).toDouble())
    }
    var salonNotesSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getString("salon_cuadre_notes_${activeJornada.id}", "") ?: "")
    }

    val ordersOfJornada = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada.id }
    }

    val closedOrders = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }
    }

    // Amount expected must be calculated strictly from actually completed orders
    val comandasSalon = remember(closedOrders) {
        closedOrders.filter { it.totalCocina > 0 }
    }
    val cantidadComandasSalon = comandasSalon.size

    val totalVendidoSalon = remember(closedOrders) {
        closedOrders.sumOf { it.totalCocina }
    }

    val totalVendidoSalonEfectivo = remember(closedOrders) {
        closedOrders.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.totalCocina }
    }

    val totalVendidoSalonTransferencia = remember(closedOrders) {
        closedOrders.filter { it.paymentMethod == "TRANSFERENCIA" }.sumOf { it.totalCocina }
    }

    val fondoInicialCaja = activeJornada.initialCash

    // Toggle between: 0 = Efectivo Físico (Recommended for cash drawers), 1 = Ventas Totales
    var cuadreMode by remember { mutableStateOf(0) }

    val consumoPersonalItems = remember(uiState.consumoPersonalList, activeJornada) {
        uiState.consumoPersonalList.filter { it.jornadaId == activeJornada.id }
    }

    val totalConsumoPersonal = remember(consumoPersonalItems) {
        consumoPersonalItems.sumOf { it.totalAmount }
    }

    // "Debe haber" por ventas
    val expectedSalonCashBeforeConsumo = if (salonCuadreRegistrado) {
        salonExpectedCashSaved
    } else {
        if (cuadreMode == 0) {
            fondoInicialCaja + totalVendidoSalonEfectivo
        } else {
            fondoInicialCaja + totalVendidoSalon
        }
    }

    val expectedSalonCashFinal = if (salonCuadreRegistrado) {
        salonExpectedCashSaved
    } else {
        expectedSalonCashBeforeConsumo - totalConsumoPersonal
    }

    var realCashText by remember(salonCuadreRegistrado, salonRealCashSaved) {
        mutableStateOf(if (salonCuadreRegistrado) "%.2f".format(salonRealCashSaved) else "")
    }
    var notesText by remember(salonCuadreRegistrado, salonNotesSaved) {
        mutableStateOf(if (salonCuadreRegistrado) salonNotesSaved else "")
    }

    val realCash = realCashText.toDoubleOrNull() ?: 0.0
    val diferencia = if (salonCuadreRegistrado) {
        salonDiferenciaSaved
    } else {
        realCash - expectedSalonCashFinal
    }

    var showConfirmCloseDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.White,
            contentColor = ElQadreNavy,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                    color = ElQadreNavy
                )
            }
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("CUADRE DE SALÓN", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("CUADRE DE BARRA", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                icon = { Icon(Icons.Outlined.LocalBar, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (activeSubTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuadre de Salón",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Arqueo de caja correspondiente únicamente al área de Salón/Cocina.",
                        color = Slate500,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElQadreGoldSoft,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Restaurant,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (salonCuadreRegistrado) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Emerald50,
                    border = BorderStroke(1.dp, Emerald200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arqueo Registrado",
                                fontWeight = FontWeight.Bold,
                                color = Emerald800,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "El cuadre de salón de este turno ha sido guardado exitosamente. Los detalles están bloqueados para la contabilidad actual.",
                                color = Emerald700,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 1. VENTAS DE SALÓN

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VENTAS DE SALÓN",
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Slate100,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "Cocina/Salón",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$${"%.2f".format(totalVendidoSalon)} CUP",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                modifier = Modifier.testTag("total_vendido_salon_display")
                            )
                            Text(
                                text = "$cantidadComandasSalon comandas en total",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Emerald500, CircleShape)
                                )
                                Text(
                                    text = "Efectivo: $${"%.2f".format(totalVendidoSalonEfectivo)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate700
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(ElQadreNavy, CircleShape)
                                )
                                Text(
                                    text = "Transf: $${"%.2f".format(totalVendidoSalonTransferencia)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate700
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1.5 CONSUMO DE PERSONAL (SECCIÓN REGISTRO EN EL CIERRE)

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Group,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "CONSUMO DE PERSONAL",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Consumo real ocurrido durante la jornada",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "Deducción de Caja",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!salonCuadreRegistrado) {
                        var selectedProduct by remember { mutableStateOf<Product?>(null) }
                        var qtyText by remember { mutableStateOf("1") }
                        var productDropdownExpanded by remember { mutableStateOf(false) }

                        val availableProducts = remember(uiState.products) {
                            uiState.products.filter { it.isAvailable }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate50, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Registrar Consumo de Personal en Cierre",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ElQadreNavy
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedProduct?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Seleccionar Producto") },
                                    placeholder = { Text("Elige un producto...") },
                                    trailingIcon = {
                                        IconButton(onClick = { productDropdownExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Desplegar")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { productDropdownExpanded = true }
                                        .testTag("consumo_personal_product_select"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate200
                                    )
                                )

                                DropdownMenu(
                                    expanded = productDropdownExpanded,
                                    onDismissRequest = { productDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    if (availableProducts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No hay productos disponibles", fontSize = 12.sp, color = Slate500) },
                                            onClick = { productDropdownExpanded = false }
                                        )
                                    } else {
                                        availableProducts.forEach { product ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(product.name, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                                        Text("$${"%.2f".format(product.price)} CUP", fontWeight = FontWeight.Bold, color = ElQadreNavy, fontSize = 12.sp)
                                                    }
                                                },
                                                onClick = {
                                                    selectedProduct = product
                                                    productDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = qtyText,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { it.isDigit() }) {
                                            qtyText = input
                                        }
                                    },
                                    label = { Text("Cantidad") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("consumo_personal_qty_input"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate200
                                    )
                                )

                                val qty = qtyText.toIntOrNull() ?: 0
                                val calculatedValue = (selectedProduct?.price ?: 0.0) * qty

                                Column(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text("Importe Calculado", fontSize = 9.sp, color = Slate500)
                                    Text(
                                        text = "$${"%.2f".format(calculatedValue)} CUP",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val prod = selectedProduct
                                    val qty = qtyText.toIntOrNull() ?: 1
                                    if (prod != null && qty > 0) {
                                        viewModel.addConsumoPersonalItem(
                                            productId = prod.id,
                                            productName = prod.name,
                                            unitPrice = prod.price,
                                            quantity = qty
                                        )
                                        selectedProduct = null
                                        qtyText = "1"
                                    }
                                },
                                enabled = selectedProduct != null && (qtyText.toIntOrNull() ?: 0) > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_add_consumo_personal"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD97706),
                                    disabledContainerColor = Slate200,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AGREGAR AL CONSUMO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Table / List of consumed items
                    if (consumoPersonalItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin consumo de personal registrado en esta jornada",
                                fontSize = 11.sp,
                                color = Slate400,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Detalle del Consumo:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Slate600
                            )

                            consumoPersonalItems.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${item.productName} x${item.quantity}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "Precio unitario: $${"%.2f".format(item.unitPrice)} CUP | Registrado por: @${item.recordedBy}",
                                                fontSize = 10.sp,
                                                color = Slate500
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "-$${"%.2f".format(item.totalAmount)} CUP",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = Color(0xFFD97706)
                                            )

                                            if (!salonCuadreRegistrado) {
                                                IconButton(
                                                    onClick = { viewModel.deleteConsumoPersonalItem(item.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Eliminar",
                                                        tint = Rose600,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Total Consumo Summary Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL CONSUMO PERSONAL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309)
                                )
                                Text(
                                    text = "-$${"%.2f".format(totalConsumoPersonal)} CUP",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. CAJA ESPERADA POR VENTAS

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CAJA ESPERADA POR VENTAS",
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!salonCuadreRegistrado) {
                        // Segmented control to choose between physical Cash and total sales
                        Row(
                            modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                if (cuadreMode == 0) Color.White else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { cuadreMode = 0 }
                                            .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Efectivo Físico",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cuadreMode == 0) ElQadreNavy else Slate500
                                )
                            }
                            Box(
                                modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                if (cuadreMode == 1) Color.White else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { cuadreMode = 1 }
                                            .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Contabilidad Total",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cuadreMode == 1) ElQadreNavy else Slate500
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Criterio de Cuadre Guardado:",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                            Text(
                                text = if (salonExpectedCashSaved == fondoInicialCaja + totalVendidoSalonEfectivo) "Efectivo Físico" else "Contabilidad Total",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$${"%.2f".format(expectedSalonCashBeforeConsumo)} CUP",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                modifier = Modifier.testTag("debe_haber_salon_display")
                            )
                            Text(
                                text = if (salonCuadreRegistrado) {
                                    "Importe teórico por ventas guardado"
                                } else if (cuadreMode == 0) {
                                    "Fondo Inicial ($${"%.2f".format(fondoInicialCaja)}) + Ventas Salón Efectivo ($${"%.2f".format(totalVendidoSalonEfectivo)})"
                                } else {
                                    "Fondo Inicial ($${"%.2f".format(fondoInicialCaja)}) + Ventas Salón Totales ($${"%.2f".format(totalVendidoSalon)})"
                                },
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachMoney,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2.5 RESUMEN DE CUADRE FINAL (DESGLOSE REQUERIDO: VENTAS, CAJA ESPERADA, CONSUMO PERSONAL Y CAJA ESPERADA FINAL)

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CUADRE DE CAJA ESPERADA",
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )

                    // VENTAS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VENTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        Text("$${"%.2f".format(totalVendidoSalon)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    // CAJA ESPERADA POR VENTAS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CAJA ESPERADA POR VENTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        Text("$${"%.2f".format(expectedSalonCashBeforeConsumo)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    // CONSUMO DE PERSONAL (indicador amarillo)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFD97706), CircleShape)
                                )
                                Text(
                                    text = "CONSUMO DE PERSONAL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                            Text(
                                text = "-$${"%.2f".format(totalConsumoPersonal)} CUP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // CAJA ESPERADA FINAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CAJA ESPERADA FINAL", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Text("Caja por ventas - Consumo de personal", fontSize = 10.sp, color = Slate500)
                        }
                        Text(
                            text = "$${"%.2f".format(expectedSalonCashFinal)} CUP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Emerald700,
                            modifier = Modifier.testTag("caja_esperada_final_display")
                        )
                    }
                }
            }
        }

        // 3. DINERO CONTADO

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DINERO CONTADO",
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (!salonCuadreRegistrado) {
                            Text(
                                text = "Arqueo exacto",
                                color = Emerald600,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        realCashText = "%.2f".format(expectedSalonCashFinal)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = realCashText,
                        onValueChange = { input ->
                            if (!salonCuadreRegistrado) {
                                if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                                    realCashText = input
                                }
                            }
                        },
                        label = { Text("Efectivo Físico Contado (CUP)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        enabled = !salonCuadreRegistrado,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dinero_contado_salon_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy,
                            unfocusedBorderColor = Slate200,
                            disabledBorderColor = Slate100,
                            disabledLabelColor = Slate400,
                            disabledTextColor = Slate700
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { input ->
                            if (!salonCuadreRegistrado) {
                                notesText = input
                            }
                        },
                        label = { Text("Notas de Salón / Observaciones") },
                        placeholder = { Text("Opcional: Escribe detalles sobre faltantes o sobrantes...") },
                        enabled = !salonCuadreRegistrado,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("notas_salon_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy,
                            unfocusedBorderColor = Slate200,
                            disabledBorderColor = Slate100,
                            disabledLabelColor = Slate400,
                            disabledTextColor = Slate700
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )
                }
            }
        }

        // 4. RESULTADO GRANDE: CUADRA / SOBRANTE / FALTANTE
        if (realCashText.isNotEmpty()) {
            item {
                val bannerColor = when {
                    diferencia == 0.0 -> Emerald50
                    diferencia > 0.0 -> ElQadreGoldSoft
                    else -> Rose50
                }
                val borderColor = when {
                    diferencia == 0.0 -> Emerald200
                    diferencia > 0.0 -> ElQadreGold
                    else -> Rose200
                }
                val titleColor = when {
                    diferencia == 0.0 -> Emerald800
                    diferencia > 0.0 -> ElQadreGoldDark
                    else -> Rose800
                }
                val descriptionColor = when {
                    diferencia == 0.0 -> Emerald600
                    diferencia > 0.0 -> ElQadreGoldDark.copy(alpha = 0.8f)
                    else -> Rose600
                }
                val statusText = when {
                    diferencia == 0.0 -> "CUADRA"
                    diferencia > 0.0 -> "SOBRANTE"
                    else -> "FALTANTE"
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bannerColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = titleColor,
                            modifier = Modifier.testTag("resultado_status_display")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${if (diferencia >= 0.0) "+" else ""}$${"%.2f".format(diferencia)} CUP",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = titleColor,
                            modifier = Modifier.testTag("resultado_diferencia_display")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                diferencia == 0.0 -> "El efectivo físico coincide exactamente con el teórico esperado de Salón."
                                diferencia > 0.0 -> "Hay un excedente de dinero físico en la caja de salón respecto al teórico esperado."
                                else -> "Hace falta dinero físico en la caja de salón respecto al teórico esperado."
                            },
                            color = descriptionColor,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Acciones
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!salonCuadreRegistrado) {
                    Button(
                        onClick = { showConfirmCloseDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_registrar_cuadre_salon"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (realCashText.isNotEmpty() && diferencia == 0.0) Emerald600 else ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = realCashText.isNotEmpty()
                    ) {
                        Text(
                            text = "REGISTRAR CUADRE DE SALÓN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetCuadreSalon(activeJornada.id)
                            salonCuadreRegistrado = false
                            realCashText = ""
                            notesText = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_volver_hacer_arqueo"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Slate600
                        ),
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "VOLVER A REALIZAR ARQUEO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
    } else {
        BarraCuadrePane(
            uiState = uiState,
            viewModel = viewModel,
            activeJornada = activeJornada,
            sharedPreferences = sharedPreferences
        )
    }
}

    if (showConfirmCloseDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCloseDialog = false },
            title = {
                Text(
                    text = "Confirmar Cuadre de Salón",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de que desea registrar este arqueo de Salón con un efectivo contado de $${"%.2f".format(realCash)} CUP? Esta acción guardará el estado de las ventas de salón para este turno de caja.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCloseDialog = false
                        viewModel.registrarCuadreSalon(
                            activeJornada.id,
                            realCash,
                            expectedSalonCashFinal,
                            diferencia,
                            notesText
                        )
                        // Update local states
                        salonExpectedCashSaved = expectedSalonCashFinal
                        salonRealCashSaved = realCash
                        salonDiferenciaSaved = diferencia
                        salonNotesSaved = notesText
                        salonCuadreRegistrado = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White)
                ) {
                    Text("REGISTRAR CUADRE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCloseDialog = false }) {
                    Text("Cancelar", color = Slate500, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsDialog(
    initialTab: Int = 1,
    billFields: SnapshotStateMap<Int, SnapshotStateList<String>> = remember { mutableStateMapOf<Int, SnapshotStateList<String>>() },
    onShowResumen: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var dialogTab by remember { mutableStateOf(initialTab) } // 0: Calculadora, 1: Conteo de Billetes

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(top = 16.dp, bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElQadreGoldSoft,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (dialogTab == 0) Icons.Outlined.Calculate else Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (dialogTab == 0) "Calculadora de Caja" else "Desglose y Conteo de Billetes",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (dialogTab == 0) "Operaciones matemáticas rápidas" else "Conteo de efectivo físico por denominación",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Slate100, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Slate600, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(2.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (dialogTab == 0) ElQadreNavy else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { dialogTab = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Calculate,
                                    contentDescription = null,
                                    tint = if (dialogTab == 0) Color.White else Slate600,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Calculadora",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (dialogTab == 0) Color.White else Slate600
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (dialogTab == 1) ElQadreNavy else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { dialogTab = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = if (dialogTab == 1) Color.White else Slate600,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Conteo de Billetes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (dialogTab == 1) Color.White else Slate600
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (dialogTab == 0) {
                        CalculadoraPane()
                    } else {
                        ConteoBilletesPane(billFields = billFields)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Button
                if (dialogTab == 1) {
                    Button(
                        onClick = {
                            onShowResumen()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_listo_conteo")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LISTO / VER RESUMEN", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Listo / Aceptar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

private fun formatCalculadoraNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    val df = java.text.DecimalFormat("#.########", java.text.DecimalFormatSymbols(java.util.Locale.US))
    df.maximumFractionDigits = 8
    df.isGroupingUsed = false
    val formatted = df.format(value)
    return if (formatted == "-0") "0" else formatted
}

@Composable
fun CalculadoraPane() {
    var display by remember { mutableStateOf("0") }
    var expressionHistory by remember { mutableStateOf("") }
    var shouldClearOnNextKey by remember { mutableStateOf(false) }

    fun onKey(key: String) {
        when (key) {
            "C" -> {
                display = "0"
                expressionHistory = ""
                shouldClearOnNextKey = false
            }
            "⌫" -> {
                if (!shouldClearOnNextKey && display != "Error" && display != "0") {
                    display = display.dropLast(1)
                    if (display.isEmpty() || display == "-") {
                        display = "0"
                    }
                }
            }
            "%" -> {
                if (display == "Error") return
                val current = display.toDoubleOrNull()
                if (current != null) {
                    val baseValue = if (expressionHistory.isNotEmpty() && (expressionHistory.endsWith("+ ") || expressionHistory.endsWith("- "))) {
                        val exprWithoutOp = expressionHistory.dropLast(2).trim()
                        com.example.util.MathUtils.evaluateCalculadoraExpression(exprWithoutOp)
                    } else {
                        null
                    }
                    
                    val pct = if (baseValue != null) {
                        baseValue * (current / 100.0)
                    } else {
                        current / 100.0
                    }
                    display = formatCalculadoraNumber(pct)
                    shouldClearOnNextKey = false
                }
            }
            "+", "-", "*", "/" -> {
                if (display == "Error") return
                
                if (shouldClearOnNextKey && expressionHistory.isNotEmpty() && !expressionHistory.endsWith("= ")) {
                    // Changing operator
                    expressionHistory = expressionHistory.dropLast(2) + "$key "
                } else {
                    if (expressionHistory.endsWith("= ")) {
                        expressionHistory = "${display} $key "
                    } else {
                        expressionHistory += "${display} $key "
                    }
                    shouldClearOnNextKey = true
                }
            }
            "=" -> {
                if (display == "Error" || expressionHistory.isEmpty() || expressionHistory.endsWith("= ")) return
                
                val fullExpr = expressionHistory + display
                val res = com.example.util.MathUtils.evaluateCalculadoraExpression(fullExpr)
                if (res == null) {
                    display = "Error"
                    expressionHistory = ""
                    shouldClearOnNextKey = true
                } else {
                    val formattedRes = formatCalculadoraNumber(res)
                    expressionHistory = "$fullExpr = "
                    display = formattedRes
                    shouldClearOnNextKey = true
                }
            }
            "." -> {
                if (shouldClearOnNextKey || display == "Error") {
                    display = "0."
                    shouldClearOnNextKey = false
                } else if (!display.contains(".")) {
                    display = if (display.isEmpty()) "0." else "$display."
                }
            }
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "00" -> {
                if (shouldClearOnNextKey || display == "0" || display == "Error") {
                    display = if (key == "00") "0" else key
                    shouldClearOnNextKey = false
                } else {
                    if (display.length < 14) {
                        display += key
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Display
        Surface(
            color = Slate900,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (expressionHistory.isNotEmpty()) {
                    Text(
                        text = expressionHistory,
                        fontSize = 11.sp,
                        color = Slate400,
                        maxLines = 1
                    )
                }
                Text(
                    text = display.ifEmpty { "0" },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreGold,
                    maxLines = 1
                )
            }
        }

        // Keys Grid: 5 rows x 4 columns with standard layout, single '=' key
        val keys = listOf(
            listOf("C", "⌫", "%", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("00", "0", ".", "=")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        val isAction = key in listOf("+", "-", "*", "/", "%")
                        val isEqual = key == "="
                        val isClear = key in listOf("C", "⌫")
                        val btnBg = when {
                            isEqual -> ElQadreGold
                            isAction -> ElQadreNavy
                            isClear -> Rose100
                            else -> Slate100
                        }
                        val textColor = when {
                            isEqual -> ElQadreNavy
                            isAction -> Color.White
                            isClear -> Rose600
                            else -> ElQadreNavy
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = btnBg,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onKey(key) }
                                .testTag("calc_key_$key")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DenominationBillCard(
    denomination: Int,
    fields: List<String>,
    onFieldChanged: (index: Int, newValue: String) -> Unit,
    onAddField: () -> Unit,
    onRemoveField: (index: Int) -> Unit
) {
    val assetBitmap = rememberAssetImageBitmap(getBilleteAssetFileName(denomination))
    val totalCount = fields.sumOf { it.toIntOrNull() ?: 0 }
    val subtotal = totalCount * denomination
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, if (totalCount > 0) ElQadreNavy else Slate200),
        shadowElevation = if (totalCount > 0) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!isLandscape) {
            // PORTRAIT / MOBILE VERTICAL LAYOUT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Header: Denomination Title & Subtotal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$${"%,d".format(denomination)} CUP",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Denominación de billete",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$${"%,d".format(subtotal)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = if (totalCount > 0) ElQadreNavy else Slate400
                        )
                        Text(
                            text = if (totalCount > 0) "$totalCount billete${if (totalCount > 1) "s" else ""}" else "0 billetes",
                            fontSize = 11.sp,
                            fontWeight = if (totalCount > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (totalCount > 0) Emerald700 else Slate400
                        )
                    }
                }

                // Large Bill Thumbnail (A casi todo lo largo del cuadro, grande)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    if (assetBitmap != null) {
                        Image(
                            bitmap = assetBitmap,
                            contentDescription = "Billete de $denomination CUP",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "$$denomination CUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                // Input fields stacked vertically (Grandes)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    fields.forEachIndexed { index, fieldValue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (fields.size > 1) {
                                Text(
                                    text = "#${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500,
                                    modifier = Modifier.width(22.dp)
                                )
                            }

                            val countInt = fieldValue.toIntOrNull() ?: 0
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { input ->
                                    if (input.length <= 6 && input.all { it.isDigit() }) {
                                        onFieldChanged(index, input)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = ElQadreNavy,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(
                                                color = if (countInt > 0) ElQadreGoldSoft else Slate50,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = if (countInt > 0) ElQadreNavy else Slate300,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                    ) {
                                        if (fieldValue.isEmpty()) {
                                            Text(
                                                text = "Cantidad de billetes",
                                                color = Slate400,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (fields.size > 1 || fieldValue.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate100,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable { onRemoveField(index) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Eliminar fajo",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Plus button below fields to add another stack input field
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, ElQadreNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { onAddField() }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir fajo",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Añadir fajo / nuevo grupo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )
                    }
                }
            }
        } else {
            // LANDSCAPE / HORIZONTAL LAYOUT (Cajero / Tablet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left Column: Bill Image (Grande) & Denomination Label
                Column(
                    modifier = Modifier.width(160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                    ) {
                        if (assetBitmap != null) {
                            Image(
                                bitmap = assetBitmap,
                                contentDescription = "Billete de $denomination CUP",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "$$denomination",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = ElQadreNavy
                                )
                            }
                        }
                    }

                    Text(
                        text = "$${"%,d".format(denomination)} CUP",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = ElQadreNavy
                    )
                }

                // Right Column: Fields, Subtotal & Add Button
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fajos / Cantidades:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$${"%,d".format(subtotal)} CUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (totalCount > 0) ElQadreNavy else Slate400
                            )
                            if (totalCount > 0) {
                                Text(
                                    text = "($totalCount bll)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald700
                                )
                            }
                        }
                    }

                    fields.forEachIndexed { index, fieldValue ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (fields.size > 1) {
                                Text(
                                    text = "#${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500,
                                    modifier = Modifier.width(22.dp)
                                )
                            }

                            val countInt = fieldValue.toIntOrNull() ?: 0
                            BasicTextField(
                                value = fieldValue,
                                onValueChange = { input ->
                                    if (input.length <= 6 && input.all { it.isDigit() }) {
                                        onFieldChanged(index, input)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = ElQadreNavy,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .background(
                                                color = if (countInt > 0) ElQadreGoldSoft else Slate50,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = if (countInt > 0) ElQadreNavy else Slate300,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        if (fieldValue.isEmpty()) {
                                            Text(
                                                text = "Cantidad de billetes",
                                                color = Slate400,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (fields.size > 1 || fieldValue.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate100,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { onRemoveField(index) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Eliminar fajo",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Plus button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, ElQadreNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { onAddField() }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir fajo",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ Añadir otro fajo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = ElQadreNavy
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConteoBilletesPane(
    initialCounts: Map<Int, Int> = emptyMap(),
    billFields: SnapshotStateMap<Int, SnapshotStateList<String>> = remember {
        val map = mutableStateMapOf<Int, SnapshotStateList<String>>()
        BILLETE_DENOMINATIONS.forEach { den ->
            val list = mutableStateListOf<String>()
            val init = initialCounts[den] ?: 0
            list.add(if (init > 0) init.toString() else "")
            map[den] = list
        }
        map
    },
    onTotalCalculated: ((Double) -> Unit)? = null
) {
    // Ensure denominations are populated
    LaunchedEffect(billFields) {
        BILLETE_DENOMINATIONS.forEach { den ->
            if (!billFields.containsKey(den) || billFields[den]?.isEmpty() == true) {
                val list = mutableStateListOf<String>()
                list.add("")
                billFields[den] = list
            }
        }
    }

    val totalBillsValue by remember {
        derivedStateOf {
            var sum = 0
            for (den in BILLETE_DENOMINATIONS) {
                val list = billFields[den]
                val sumCount = list?.sumOf { str -> str.toIntOrNull() ?: 0 } ?: 0
                sum += sumCount * den
            }
            sum
        }
    }

    val totalBillsCount by remember {
        derivedStateOf {
            var count = 0
            for (den in BILLETE_DENOMINATIONS) {
                val list = billFields[den]
                count += list?.sumOf { str -> str.toIntOrNull() ?: 0 } ?: 0
            }
            count
        }
    }

    LaunchedEffect(totalBillsValue) {
        onTotalCalculated?.invoke(totalBillsValue.toDouble())
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Real-time Sum Display Header Card
        Surface(
            color = ElQadreNavy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL EFECTIVO CONTADO",
                        color = Slate300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$${"%,d".format(totalBillsValue)}",
                            color = ElQadreGold,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "CUP",
                            color = ElQadreGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    if (totalBillsCount > 0) {
                        Text(
                            text = "$totalBillsCount billetes ingresados",
                            color = Emerald200,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (totalBillsValue > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier.clickable {
                            BILLETE_DENOMINATIONS.forEach { den ->
                                billFields[den]?.let { list ->
                                    list.clear()
                                    list.add("")
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Limpiar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // List of Denominations with Assets
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(BILLETE_DENOMINATIONS) { den ->
                val fields = billFields[den] ?: remember { mutableStateListOf("") }
                DenominationBillCard(
                    denomination = den,
                    fields = fields,
                    onFieldChanged = { index, newValue ->
                        if (index in fields.indices) {
                            fields[index] = newValue
                        }
                    },
                    onAddField = {
                        fields.add("")
                    },
                    onRemoveField = { index ->
                        if (fields.size > 1 && index in fields.indices) {
                            fields.removeAt(index)
                        } else if (fields.size == 1 && index == 0) {
                            fields[0] = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ConteoResumenDialog(
    billFields: Map<Int, List<String>>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentDateTimeStr = remember {
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
    }

    data class DenomSummary(
        val denomination: Int,
        val count: Int,
        val subtotal: Int
    )

    val items = remember(billFields) {
        BILLETE_DENOMINATIONS.mapNotNull { den ->
            val list = billFields[den] ?: emptyList()
            val count = list.sumOf { it.toIntOrNull() ?: 0 }
            if (count > 0) {
                DenomSummary(denomination = den, count = count, subtotal = count * den)
            } else null
        }
    }

    val totalAmount = remember(items) { items.sumOf { it.subtotal } }
    val totalBills = remember(items) { items.sumOf { it.count } }

    val formattedSummaryText = remember(items, totalAmount, currentDateTimeStr) {
        val sb = StringBuilder()
        sb.appendLine("CONTEO DE BILLETES — $currentDateTimeStr")
        items.forEach { item ->
            sb.appendLine("${item.denomination} CUP x ${item.count} = $${"%,d".format(item.subtotal)}")
        }
        sb.appendLine("TOTAL: $${"%,d".format(totalAmount)} CUP")
        sb.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp)
                .navigationBarsPadding()
                .testTag("dialog_resumen_conteo")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Emerald50
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "RESUMEN DE CONTEO",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = currentDateTimeStr,
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Slate100, CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Slate600, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Slate100)

                // Total Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL GENERAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate300)
                            Text(
                                text = "$${"%,d".format(totalAmount)} CUP",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreGold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$totalBills billetes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Breakdown list / table
                Text(
                    text = "DESGLOSE POR DENOMINACIÓN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se ha ingresado ninguna cantidad de billetes.",
                            fontSize = 13.sp,
                            color = Slate400,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Denominación", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600, modifier = Modifier.weight(1.2f))
                                Text("Cantidad", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("Importe", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                            }
                        }

                        items(items) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$${"%,d".format(item.denomination)} CUP",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    Text(
                                        text = "${item.count} bll",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate700,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "$${"%,d".format(item.subtotal)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Emerald700,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons: COPIAR and ENVIAR POR WHATSAPP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Conteo de Billetes", formattedSummaryText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Desglose copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                        border = BorderStroke(1.5.dp, ElQadreNavy)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("COPIAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, formattedSummaryText)
                                    type = "text/plain"
                                    setPackage("com.whatsapp")
                                }
                                context.startActivity(sendIntent)
                            } catch (e: Exception) {
                                // If WhatsApp is not installed or direct launch failed, open chooser
                                val chooserIntent = android.content.Intent.createChooser(
                                    android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, formattedSummaryText)
                                        type = "text/plain"
                                    },
                                    "Compartir Conteo de Billetes"
                                )
                                context.startActivity(chooserIntent)
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WHATSAPP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- NEW COMPONENTS FOR COBRO AND COMANDAS ----------------

data class CartItem(
    val product: Product,
    val quantity: Int,
    val selectedAgregados: List<String>,
    val finalUnitPrice: Double
)

@Composable
fun AjustesCajaPane(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val tasaUsd = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEur = uiState.generalConfig?.tasaEur ?: 0.0

    var showRestoreModal by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Información y Ajustes de Caja",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = ElQadreNavy
        )

        // Versión de la Aplicación y Buscar Actualizaciones
        com.example.ui.components.AppVersionSettingsCard()

        // Respaldo Local de Datos de Caja
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
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = Color(0xFF0369A1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "RESPALDO LOCAL DE DATOS DE CAJA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Exporta jornadas, comandas, menú, transferencias recibidas y bitácora",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                HorizontalDivider(color = Slate100)

                Text(
                    text = "El archivo qdepcajero.json contiene la totalidad del estado operativo necesario para reconstruir el terminal de Caja sin pérdida de datos.",
                    fontSize = 11.sp,
                    color = Slate600
                )

                Button(
                    onClick = {
                        val jsonStr = CajeroBackupManager.createBackupJson(uiState)
                        val backupFile = CajeroBackupManager.exportBackupFile(context, jsonStr)
                        if (backupFile != null) {
                            Toast.makeText(context, "Respaldo qdepcajero.json creado con éxito", Toast.LENGTH_SHORT).show()
                            CajeroBackupManager.shareBackupFile(context, backupFile)
                        } else {
                            Toast.makeText(context, "Error al generar archivo qdepcajero.json", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("btn_respaldar_datos_cajero"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESPALDAR DATOS (qdepcajero.json)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Restauración de Datos de Caja
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
                                imageVector = Icons.Outlined.Restore,
                                contentDescription = null,
                                tint = Amber800,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "RESTAURACIÓN DE DATOS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Recupera el estado del panel a partir de qdepcajero.json",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                HorizontalDivider(color = Slate100)

                Text(
                    text = "Restaura jornadas, comandas, transferencias recibidas por SMS/manuales, cobros y bitácora de cierres.",
                    fontSize = 11.sp,
                    color = Slate600
                )

                OutlinedButton(
                    onClick = {
                        restoreJsonInput = ""
                        showRestoreModal = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("btn_restaurar_datos_cajero"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber800),
                    border = BorderStroke(1.dp, Amber800)
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTAURAR DATOS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Inicializar Sistema
        com.example.ui.components.InitializeSystemCard(viewModel = viewModel)

        // Sincronización y URLs
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.updateMercainv() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Teléfono de Caja / Comandas
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
            modifier = Modifier.fillMaxWidth().testTag("card_telefono_caja_cajero")
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
                            text = "Número de recepción configurado en qusuarios.json",
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
                            text = "Solo lectura. Administrado centralmente en qusuarios.json.",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }
                }
            }
        }

        // Tasas de cambio administradas (Read-only informativa para el Cajero)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Emerald50,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.CurrencyExchange,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "TASAS DE CAMBIO OFICIALES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tasas establecidas por el Administrador (CUP moneda base)",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                HorizontalDivider(color = Slate100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // USD Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Dólar Estadounidense", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (tasaUsd > 0) "1 USD = $${"%.2f".format(tasaUsd)} CUP" else "No configurada",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (tasaUsd > 0) ElQadreNavy else Slate400
                            )
                        }
                    }

                    // EUR Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Euro", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (tasaEur > 0) "1 EUR = $${"%.2f".format(tasaEur)} CUP" else "No configurada",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (tasaEur > 0) ElQadreNavy else Slate400
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Las tasas de cambio son gestionadas exclusivamente por la Administración en la sección de Configuración General.",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showRestoreModal) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFD97706))
                    Text("Restaurar Datos (qdepcajero.json)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "⚠️ ADVERTENCIA CRÍTICA:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Rose700
                            )
                            Text(
                                text = "Los datos actuales de jornadas, comandas, transferencias recibidas y bitácora de caja serán reemplazados por el contenido exacto del respaldo.",
                                fontSize = 11.sp,
                                color = Rose600
                            )
                        }
                    }

                    Text(
                        text = "Pega a continuación el contenido de tu archivo qdepcajero.json:",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        placeholder = { Text("{\"appIdentifier\": \"QDEPCAJERO_BACKUP_V1\", ...}", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .testTag("input_restore_json_cajero"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            isRestoring = true
                            viewModel.restoreCajeroBackupJson(
                                jsonString = restoreJsonInput,
                                onComplete = { success ->
                                    isRestoring = false
                                    if (success) {
                                        showRestoreModal = false
                                        Toast.makeText(context, "Respaldo qdepcajero.json restaurado exitosamente.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank() && !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.testTag("btn_confirm_restore_cajero")
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("CONFIRMAR Y RESTAURAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreModal = false },
                    enabled = !isRestoring
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
fun RegistrarCobroDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    activeJornadaId: Long,
    onDismiss: () -> Unit
) {
    val tasaUSD = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEUR = uiState.generalConfig?.tasaEur ?: 0.0

    var selectedCategory by remember { mutableStateOf("Todos") }
    val cartItems = remember { mutableStateListOf<CartItem>() }
    var dialogTab by remember { mutableStateOf(0) } // 0: Catálogo, 1: Checkout

    // Customize product state
    var productToCustomize by remember { mutableStateOf<Product?>(null) }
    var customizeQuantity by remember { mutableStateOf(1) }
    val selectedAgregados = remember { mutableStateListOf<String>() }

    // Checkout payment state
    var selectedCurrency by remember { mutableStateOf("CUP") } // CUP, USD, EUR
    var paymentMethod by remember { mutableStateOf("EFECTIVO") } // EFECTIVO, TRANSFERENCIA, MIXTO
    var cashReceivedText by remember { mutableStateOf("") }
    var transferAmountText by remember { mutableStateOf("") }
    var tipText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val currentExchangeRate = when (selectedCurrency) {
        "USD" -> if (tasaUSD > 0) tasaUSD else 1.0
        "EUR" -> if (tasaEUR > 0) tasaEUR else 1.0
        else -> 1.0
    }

    val categories = remember(uiState.products) {
        listOf("Todos") + uiState.products.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val filteredProducts = remember(uiState.products, selectedCategory) {
        if (selectedCategory == "Todos") uiState.products
        else uiState.products.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    // Directly compute total sum from cart items so it automatically updates on cart changes
    val totalAmountCUP = cartItems.sumOf { it.finalUnitPrice * it.quantity }

    val totalAmountInCurrency = if (selectedCurrency == "CUP") {
        totalAmountCUP
    } else {
        totalAmountCUP / currentExchangeRate
    }

    val tipInCurrency = tipText.toDoubleOrNull() ?: 0.0
    val totalWithTipInCurrency = totalAmountInCurrency + tipInCurrency
    val totalWithTipCUP = if (selectedCurrency == "CUP") totalWithTipInCurrency else totalWithTipInCurrency * currentExchangeRate

    val transferAmountInCurrency = if (paymentMethod == "MIXTO") {
        transferAmountText.toDoubleOrNull() ?: 0.0
    } else if (paymentMethod == "TRANSFERENCIA") {
        totalWithTipInCurrency
    } else {
        0.0
    }

    val remainingCashToPayInCurrency = if (paymentMethod == "MIXTO") {
        (totalWithTipInCurrency - transferAmountInCurrency).coerceAtLeast(0.0)
    } else if (paymentMethod == "EFECTIVO") {
        totalWithTipInCurrency
    } else {
        0.0
    }

    val cashReceivedInCurrency = if (paymentMethod == "TRANSFERENCIA") {
        0.0
    } else {
        cashReceivedText.toDoubleOrNull() ?: 0.0
    }

    val changeInCurrency = if (paymentMethod == "TRANSFERENCIA") {
        0.0
    } else if (paymentMethod == "EFECTIVO") {
        if (cashReceivedInCurrency >= totalWithTipInCurrency) cashReceivedInCurrency - totalWithTipInCurrency else 0.0
    } else { // MIXTO
        if (cashReceivedInCurrency >= remainingCashToPayInCurrency) cashReceivedInCurrency - remainingCashToPayInCurrency else 0.0
    }
    val changeInCUP = if (selectedCurrency == "CUP") changeInCurrency else changeInCurrency * currentExchangeRate

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ElQadreBackground
        ) {
            Scaffold(
                topBar = {
                    Surface(color = ElQadreNavy) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onDismiss) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cerrar",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Registrar Cobro Rápido",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = ElQadreGold,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "$${"%.2f".format(totalAmountCUP)} CUP",
                                        color = ElQadreNavy,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            TabRow(
                                selectedTabIndex = dialogTab,
                                containerColor = Color.Transparent,
                                contentColor = ElQadreGold
                            ) {
                                Tab(
                                    selected = dialogTab == 0,
                                    onClick = { dialogTab = 0 },
                                    text = { Text("Catálogo", fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = dialogTab == 1,
                                    onClick = { dialogTab = 1 },
                                    text = { Text("Cobrar (${cartItems.size})", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (dialogTab == 0) {
                        // CATÁLOGO TAB
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Category Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSelected = selectedCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) ElQadreNavy else Color.White,
                                        border = BorderStroke(1.dp, if (isSelected) ElQadreNavy else Slate200),
                                        modifier = Modifier
                                            .clickable { selectedCategory = cat }
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else Slate600,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Products Grid/List
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredProducts) { prod ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate100),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                productToCustomize = prod
                                                customizeQuantity = 1
                                                selectedAgregados.clear()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = prod.name,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElQadreNavy,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (prod.destination == "COCINA") Rose100 else Amber100
                                                    ) {
                                                        Text(
                                                            text = prod.destination,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (prod.destination == "COCINA") Rose600 else Amber800,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Stock: ${prod.stock}",
                                                        fontSize = 11.sp,
                                                        color = Slate500
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "$${"%.2f".format(prod.price)} CUP",
                                                fontWeight = FontWeight.Black,
                                                color = ElQadreNavy,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // CHECKOUT TAB
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (cartItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.ShoppingCart,
                                            contentDescription = null,
                                            tint = Slate300,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "El carrito está vacío. Agrega productos desde el catálogo.",
                                            color = Slate500,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 32.dp)
                                        )
                                    }
                                }
                            } else {
                                // Cart Items list
                                Text(
                                    text = "Productos Seleccionados",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ElQadreNavy
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(cartItems) { item ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Slate100),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${item.product.name} x${item.quantity}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = ElQadreNavy,
                                                        fontSize = 13.sp
                                                    )
                                                    if (item.selectedAgregados.isNotEmpty()) {
                                                        Text(
                                                            text = "+ ${item.selectedAgregados.joinToString(", ")}",
                                                            fontSize = 10.sp,
                                                            color = Slate500,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "$${"%.2f".format(item.finalUnitPrice * item.quantity)}",
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy,
                                                        fontSize = 13.sp
                                                    )
                                                    IconButton(
                                                        onClick = { cartItems.remove(item) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Quitar",
                                                            tint = Rose600,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Divider(color = Slate100)

                                // Multi-Currency, Total, Cash Received, Change
                                Column(
                                    modifier = Modifier.weight(0.65f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Currency Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Moneda de Cobro:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("CUP", "USD", "EUR").forEach { curr ->
                                                val cSel = selectedCurrency == curr
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (cSel) ElQadreGold else Slate100,
                                                    modifier = Modifier.clickable {
                                                        selectedCurrency = curr
                                                    }
                                                ) {
                                                    Text(
                                                        text = curr,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (cSel) ElQadreNavy else Slate600,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (selectedCurrency != "CUP") {
                                        val rateMsg = if (selectedCurrency == "USD") "Tasa USD: 1 USD = $${"%.2f".format(tasaUSD)} CUP" else "Tasa EUR: 1 EUR = $${"%.2f".format(tasaEUR)} CUP"
                                        Text(
                                            text = rateMsg,
                                            fontSize = 11.sp,
                                            color = if (currentExchangeRate > 1.0) Emerald600 else Rose600,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Payment Method Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Método de Pago:", fontSize = 12.sp, color = Slate600)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("EFECTIVO", "TRANSFERENCIA").forEach { method ->
                                                val mSel = paymentMethod == method
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (mSel) ElQadreNavy else Slate100,
                                                    modifier = Modifier.clickable { paymentMethod = method }
                                                ) {
                                                    Text(
                                                        text = method,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (mSel) Color.White else Slate500,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Input Propina
                                    OutlinedTextField(
                                        value = tipText,
                                        onValueChange = { tipText = it },
                                        label = { Text("Propina (${selectedCurrency}) - Opcional") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("propina_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    if (paymentMethod == "MIXTO") {
                                        OutlinedTextField(
                                            value = transferAmountText,
                                            onValueChange = { transferAmountText = it },
                                            label = { Text("IMPORTE EN TRANSFERENCIA (${selectedCurrency})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("transferencia_mixto_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    if (paymentMethod == "EFECTIVO" || paymentMethod == "MIXTO") {
                                        // Input Efectivo Recibido
                                        OutlinedTextField(
                                            value = cashReceivedText,
                                            onValueChange = { cashReceivedText = it },
                                            label = { Text("EFECTIVO RECIBIDO (${selectedCurrency})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("efectivo_recibido_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        // Cambio row
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600.copy(alpha = 0.08f) else Rose100.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (cashReceivedInCurrency >= totalWithTipInCurrency) "CAMBIO A DEVOLVER:" else "EFECTIVO INSUFICIENTE",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600 else Rose600
                                                )
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "$${"%.2f".format(changeInCurrency)} $selectedCurrency",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600 else Rose600
                                                    )
                                                    if (selectedCurrency != "CUP" && changeInCurrency > 0) {
                                                        Text(
                                                            text = "(≈ $${"%.2f".format(changeInCUP)} CUP)",
                                                            fontSize = 10.sp,
                                                            color = Slate500
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Grand Total row
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Slate100,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("TOTAL A COBRAR:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                                Text("$${"%.2f".format(totalWithTipInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = ElQadreNavy)
                                            }
                                            if (selectedCurrency != "CUP") {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Contravalor Base en CUP:", fontSize = 11.sp, color = Slate500)
                                                    Text("$${"%.2f".format(totalWithTipCUP)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                                }
                                            }
                                        }
                                    }

                                    val isAllowedToPay = cartItems.isNotEmpty() && totalAmountCUP > 0.0 && when (paymentMethod) {
                                        "EFECTIVO" -> cashReceivedInCurrency >= totalWithTipInCurrency
                                        "TRANSFERENCIA" -> true
                                        "MIXTO" -> transferAmountInCurrency > 0.0 && transferAmountInCurrency < totalWithTipInCurrency && cashReceivedInCurrency >= remainingCashToPayInCurrency
                                        else -> false
                                    }
                                    Button(
                                        onClick = {
                                            if (isProcessing) return@Button
                                            isProcessing = true
                                            val maxComanda = uiState.allOrders.maxOfOrNull { it.comandaNumber } ?: 0
                                            val nextComandaNumber = maxComanda + 1
                                            val hasCocina = cartItems.any { it.product.destination == "COCINA" }
                                            val hasBarra = cartItems.any { it.product.destination == "BARRA" }
                                            val maxCocina = uiState.allOrders.maxOfOrNull { it.cocinaNumber } ?: 0
                                            val nextCocinaNumber = if (hasCocina) maxCocina + 1 else 0
                                            val maxBarra = uiState.allOrders.maxOfOrNull { it.barraNumber } ?: 0
                                            val nextBarraNumber = if (hasBarra) maxBarra + 1 else 0
                                            val totalCocina = cartItems.filter { it.product.destination == "COCINA" }.sumOf { it.finalUnitPrice * it.quantity }
                                            val totalBarra = cartItems.filter { it.product.destination == "BARRA" }.sumOf { it.finalUnitPrice * it.quantity }
                                            
                                            val newOrder = TableOrder(
                                                tableNumber = 99, // comanda rápida sin mesa
                                                customerName = "Comanda Rápida #${nextComandaNumber}",
                                                waiterUsername = uiState.currentUser?.username ?: "cajero",
                                                jornadaId = activeJornadaId,
                                                status = "COBRADA",
                                                totalAmount = totalAmountCUP,
                                                paymentMethod = paymentMethod,
                                                tip = if (selectedCurrency == "CUP") tipInCurrency else tipInCurrency * currentExchangeRate,
                                                comandaNumber = nextComandaNumber,
                                                cocinaNumber = nextCocinaNumber,
                                                barraNumber = nextBarraNumber,
                                                totalCocina = totalCocina,
                                                totalBarra = totalBarra,
                                                cashReceived = if (selectedCurrency == "CUP") cashReceivedInCurrency else cashReceivedInCurrency * currentExchangeRate,
                                                changeGiven = if (selectedCurrency == "CUP") changeInCurrency else changeInCurrency * currentExchangeRate,
                                                currency = selectedCurrency,
                                                exchangeRate = currentExchangeRate,
                                                originalAmount = totalAmountCUP,
                                                amountInCurrency = totalWithTipInCurrency,
                                                createdAt = System.currentTimeMillis(),
                                                closedAt = System.currentTimeMillis()
                                            )
                                            val itemsToSave = cartItems.map { cItem ->
                                                val noteText = if (cItem.selectedAgregados.isNotEmpty()) {
                                                    "Agregados: " + cItem.selectedAgregados.joinToString(", ")
                                                } else ""
                                                OrderItem(
                                                    orderId = 0,
                                                    productId = cItem.product.id,
                                                    productName = cItem.product.name,
                                                    unitPrice = cItem.finalUnitPrice,
                                                    quantity = cItem.quantity,
                                                    destination = cItem.product.destination,
                                                    notes = noteText,
                                                    status = "ENTREGADO",
                                                    productCode = cItem.product.code
                                                )
                                            }

                                            viewModel.registrarCobroComanda(newOrder, itemsToSave) {
                                                onDismiss()
                                            }
                                        },
                                        enabled = isAllowedToPay && !isProcessing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElQadreGold,
                                            contentColor = ElQadreNavy,
                                            disabledContainerColor = Slate100,
                                            disabledContentColor = Slate300
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("btn_confirmar_cobro_rapido")
                                    ) {
                                        Text("CONFIRMAR COBRO Y REGISTRAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Customize product dialog overlay
    if (productToCustomize != null) {
        val prod = productToCustomize!!
        val isCocina = prod.category.equals("Cocina", ignoreCase = true) || prod.category.equals("Postres", ignoreCase = true)
        val isBarra = prod.category.equals("Bebidas", ignoreCase = true)

        val agregadosDisponibles = remember(prod.admitsAgregados, prod.agregadosList) {
            if (!prod.admitsAgregados) emptyList() else {
                try {
                    val arr = org.json.JSONArray(prod.agregadosList)
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

        val agregadosPriceSum = selectedAgregados.sumOf { name ->
            agregadosDisponibles.find { it.first == name }?.second ?: 0.0
        }
        val unitPriceWithAgregados = prod.price + agregadosPriceSum

        AlertDialog(
            onDismissRequest = { productToCustomize = null },
            title = {
                Text(
                    text = "Personalizar: ${prod.name}",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Precio Base: $${"%.2f".format(prod.price)} CUP",
                        color = Slate500,
                        fontSize = 13.sp
                    )

                    // Quantity Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cantidad:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { if (customizeQuantity > 1) customizeQuantity-- },
                                modifier = Modifier.size(32.dp).background(Slate100, CircleShape)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                            Text(
                                text = "$customizeQuantity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { customizeQuantity++ },
                                modifier = Modifier.size(32.dp).background(Slate100, CircleShape)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                        }
                    }

                    if (agregadosDisponibles.isNotEmpty()) {
                        Divider(color = Slate100)
                        Text(
                            text = "Agregados / Complementos:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            agregadosDisponibles.forEach { (name, price) ->
                                val hasAgregado = selectedAgregados.contains(name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (hasAgregado) selectedAgregados.remove(name)
                                            else selectedAgregados.add(name)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = hasAgregado,
                                        onCheckedChange = {
                                            if (hasAgregado) selectedAgregados.remove(name)
                                            else selectedAgregados.add(name)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                    )
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        color = ElQadreNavy,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "+$${"%.2f".format(price)} CUP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreGold
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Precio Unitario Final:", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Slate600)
                        Text("$${"%.2f".format(unitPriceWithAgregados)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Subtotal:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                        Text("$${"%.2f".format(unitPriceWithAgregados * customizeQuantity)} CUP", fontWeight = FontWeight.Black, fontSize = 15.sp, color = ElQadreNavy)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val existingIndex = cartItems.indexOfFirst {
                            it.product.id == prod.id && it.selectedAgregados == selectedAgregados.toList()
                        }
                        if (existingIndex >= 0) {
                            val existing = cartItems[existingIndex]
                            cartItems[existingIndex] = existing.copy(quantity = existing.quantity + customizeQuantity)
                        } else {
                            cartItems.add(
                                CartItem(
                                    product = prod,
                                    quantity = customizeQuantity,
                                    selectedAgregados = selectedAgregados.toList(),
                                    finalUnitPrice = unitPriceWithAgregados
                                )
                            )
                        }
                        productToCustomize = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Agregar al Carrito", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToCustomize = null }) {
                    Text("Cancelar", color = Slate500, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun PayOrdersDialog(
    orders: List<TableOrder>,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val tasaUSD = uiState.generalConfig?.tasaUsd ?: 0.0
    val tasaEUR = uiState.generalConfig?.tasaEur ?: 0.0

    var selectedCurrency by remember { mutableStateOf("CUP") } // CUP, USD, EUR
    var paymentMethod by remember { mutableStateOf("EFECTIVO") }
    var cashReceivedText by remember { mutableStateOf("") }
    var tipText by remember { mutableStateOf("") }

    val currentExchangeRate = when (selectedCurrency) {
        "USD" -> if (tasaUSD > 0) tasaUSD else 1.0
        "EUR" -> if (tasaEUR > 0) tasaEUR else 1.0
        else -> 1.0
    }

    val orderIds = orders.map { it.id }.toSet()
    val orderItems = remember(uiState.allOrderItems, orderIds) {
        uiState.allOrderItems.filter { it.orderId in orderIds }
    }

    val totalAmountCUP = orders.sumOf { it.totalAmount }
    val totalAmountInCurrency = if (selectedCurrency == "CUP") {
        totalAmountCUP
    } else {
        totalAmountCUP / currentExchangeRate
    }

    val tipInCurrency = tipText.toDoubleOrNull() ?: 0.0
    val totalWithTipInCurrency = totalAmountInCurrency + tipInCurrency
    val totalWithTipCUP = if (selectedCurrency == "CUP") totalWithTipInCurrency else totalWithTipInCurrency * currentExchangeRate

    val cashReceivedInCurrency = if (paymentMethod == "EFECTIVO") {
        cashReceivedText.toDoubleOrNull() ?: 0.0
    } else {
        totalWithTipInCurrency
    }

    val changeInCurrency = if (paymentMethod == "EFECTIVO" && cashReceivedInCurrency >= totalWithTipInCurrency) {
        cashReceivedInCurrency - totalWithTipInCurrency
    } else 0.0
    val changeInCUP = if (selectedCurrency == "CUP") changeInCurrency else changeInCurrency * currentExchangeRate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cobrar Comandas seleccionadas (${orders.size})",
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Múltiples comandas seleccionadas",
                    fontSize = 11.sp,
                    color = Slate500
                )

                // List items in order
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 120.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(orderItems) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.productName} x${item.quantity}",
                                fontSize = 12.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$${"%.2f".format(item.unitPrice * item.quantity)} CUP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // Currency Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Moneda de Cobro:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CUP", "USD", "EUR").forEach { curr ->
                            val cSel = selectedCurrency == curr
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (cSel) ElQadreGold else Slate100,
                                modifier = Modifier.clickable {
                                    selectedCurrency = curr
                                }
                            ) {
                                Text(
                                    text = curr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cSel) ElQadreNavy else Slate600,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (selectedCurrency != "CUP") {
                    val rateMsg = if (selectedCurrency == "USD") "Tasa USD: 1 USD = $${"%.2f".format(tasaUSD)} CUP" else "Tasa EUR: 1 EUR = $${"%.2f".format(tasaEUR)} CUP"
                    Text(
                        text = rateMsg,
                        fontSize = 11.sp,
                        color = if (currentExchangeRate > 1.0) Emerald600 else Rose600,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal:", fontSize = 13.sp, color = Slate500)
                    Text("$${"%.2f".format(totalAmountInCurrency)} $selectedCurrency", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                }

                // Payment Method Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Método de Pago:", fontSize = 12.sp, color = Slate500)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EFECTIVO", "TRANSFERENCIA").forEach { method ->
                            val mSel = paymentMethod == method
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (mSel) ElQadreNavy else Slate100,
                                modifier = Modifier.clickable { paymentMethod = method }
                            ) {
                                Text(
                                    text = method,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mSel) Color.White else Slate500,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Tip Input
                OutlinedTextField(
                    value = tipText,
                    onValueChange = { tipText = it },
                    label = { Text("Propina (${selectedCurrency}) - Opcional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                if (paymentMethod == "EFECTIVO") {
                    // Input Cash Received
                    OutlinedTextField(
                        value = cashReceivedText,
                        onValueChange = { cashReceivedText = it },
                        label = { Text("EFECTIVO RECIBIDO (${selectedCurrency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("checkout_efectivo_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Cambio row
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600.copy(alpha = 0.08f) else Rose100.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cashReceivedInCurrency >= totalWithTipInCurrency) "CAMBIO A DEVOLVER:" else "EFECTIVO INSUFICIENTE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600 else Rose600
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${"%.2f".format(changeInCurrency)} $selectedCurrency",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (cashReceivedInCurrency >= totalWithTipInCurrency) Emerald600 else Rose600
                                )
                                if (selectedCurrency != "CUP" && changeInCurrency > 0) {
                                    Text(
                                        text = "(≈ $${"%.2f".format(changeInCUP)} CUP)",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // Grand Total
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TOTAL A COBRAR:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                            Text("$${"%.2f".format(totalWithTipInCurrency)} $selectedCurrency", fontWeight = FontWeight.Black, fontSize = 16.sp, color = ElQadreNavy)
                        }
                        if (selectedCurrency != "CUP") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Contravalor Base en CUP:", fontSize = 11.sp, color = Slate500)
                                Text("$${"%.2f".format(totalWithTipCUP)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isAllowedToPay = paymentMethod == "TRANSFERENCIA" || cashReceivedInCurrency >= totalWithTipInCurrency

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val updatedOrders = orders.map { baseOrder ->
                        val ratio = if (totalAmountCUP > 0) baseOrder.totalAmount / totalAmountCUP else 1.0
                        val orderTipCUP = (if (selectedCurrency == "CUP") tipInCurrency else tipInCurrency * currentExchangeRate) * ratio
                        val orderCashRecCUP = (if (selectedCurrency == "CUP") cashReceivedInCurrency else cashReceivedInCurrency * currentExchangeRate) * ratio
                        val orderChangeCUP = (if (selectedCurrency == "CUP") changeInCurrency else changeInCurrency * currentExchangeRate) * ratio
                        val orderAmountInCurr = totalWithTipInCurrency * ratio

                        baseOrder.copy(
                            status = "COBRADA",
                            closedAt = now,
                            paymentMethod = paymentMethod,
                            tip = orderTipCUP,
                            cashReceived = orderCashRecCUP,
                            changeGiven = orderChangeCUP,
                            currency = selectedCurrency,
                            exchangeRate = currentExchangeRate,
                            originalAmount = baseOrder.totalAmount,
                            amountInCurrency = orderAmountInCurr
                        )
                    }

                    viewModel.closeAndPayOrdersBatch(updatedOrders) {
                        onDismiss()
                    }
                },
                enabled = isAllowedToPay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreGold,
                    contentColor = ElQadreNavy,
                    disabledContainerColor = Slate100,
                    disabledContentColor = Slate300
                )
            ) {
                Text("REGISTRAR COBRO", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Slate500, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun BarraCuadrePane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    activeJornada: com.example.data.local.model.Jornada,
    sharedPreferences: android.content.SharedPreferences
) {
    val barMercaderias = remember(uiState.mercaderias, uiState.products) {
        uiState.mercaderias.filter { merc ->
            val prod = uiState.products.find { it.id == merc.productId }
            prod != null && prod.destination == "BARRA"
        }
    }

    var barraCuadreRegistrado by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getBoolean("barra_cuadre_registrado_${activeJornada.id}", false))
    }
    var barraExpectedIncomeSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getFloat("barra_expected_income_${activeJornada.id}", 0.0f).toDouble())
    }
    var barraRegisteredIncomeSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getFloat("barra_registered_income_${activeJornada.id}", 0.0f).toDouble())
    }
    var barraNotesSaved by remember(activeJornada.id) {
        mutableStateOf(sharedPreferences.getString("barra_notes_${activeJornada.id}", "") ?: "")
    }

    val physicalCounts = remember(activeJornada.id, barMercaderias) {
        val map = mutableStateMapOf<Long, String>()
        barMercaderias.forEach { merc ->
            val savedValue = sharedPreferences.getFloat("barra_physical_count_${activeJornada.id}_${merc.id}", -1.0f)
            map[merc.id] = if (savedValue >= 0f) "%.1f".format(savedValue) else ""
        }
        map
    }

    var barraNotesText by remember(barraCuadreRegistrado, barraNotesSaved) {
        mutableStateOf(if (barraCuadreRegistrado) barraNotesSaved else "")
    }

    var showConfirmBarraDialog by remember { mutableStateOf(false) }

    val ordersOfJornada = remember(uiState.allOrders, activeJornada) {
        uiState.allOrders.filter { it.jornadaId == activeJornada.id }
    }
    val closedOrders = remember(ordersOfJornada) {
        ordersOfJornada.filter { it.status == "COBRADA" }
    }
    val closedOrderIds = remember(closedOrders) {
        closedOrders.map { it.id }.toSet()
    }
    val itemsOfJornada = remember(uiState.allOrderItems, closedOrderIds) {
        uiState.allOrderItems.filter { it.orderId in closedOrderIds }
    }

    data class BarProductReconciliation(
        val mercaderiaId: Long,
        val productName: String,
        val unit: String,
        val price: Double,
        val existenciaInicial: Double,
        val entradas: Double,
        val ventas: Double,
        val existenciaTeorica: Double,
        val physicalText: String,
        val physicalValue: Double?,
        val diffQty: Double?,
        val itemIncome: Double,
        val isCoherent: Boolean
    )

    val barReconciliations = remember(barMercaderias, uiState.movimientosMercaderia, itemsOfJornada, physicalCounts, uiState.products, activeJornada.openedAt) {
        barMercaderias.map { merc ->
            val product = uiState.products.find { it.id == merc.productId }
            val productName = product?.name ?: "Producto ${merc.productId}"
            val price = product?.price ?: 0.0

            val movementsBeforeShift = uiState.movimientosMercaderia.filter { 
                it.mercaderiaId == merc.id && it.date < activeJornada.openedAt 
            }
            val existenciaInicial = CostCalculationHelper.getMercaderiaCurrentStock(
                mercaderiaId = merc.id,
                initialStock = merc.initialStock,
                movimientos = movementsBeforeShift
            )

            val entradas = uiState.movimientosMercaderia.filter {
                it.mercaderiaId == merc.id &&
                it.type.uppercase() == "ENTRADA" &&
                it.date >= activeJornada.openedAt
            }.sumOf { it.quantity }

            val ventas = itemsOfJornada.filter { it.productId == merc.productId }.sumOf { it.quantity }.toDouble()

            val existenciaTeorica = existenciaInicial + entradas - ventas

            val physicalText = physicalCounts[merc.id] ?: ""
            val physicalValue = physicalText.toDoubleOrNull()

            val diffQty = if (physicalValue != null) physicalValue - existenciaTeorica else null

            val itemIncome = itemsOfJornada.filter { it.productId == merc.productId }.sumOf { it.quantity * it.unitPrice }

            val isCoherent = physicalValue != null && physicalValue == existenciaTeorica

            BarProductReconciliation(
                mercaderiaId = merc.id,
                productName = productName,
                unit = merc.unitOfMeasure,
                price = price,
                existenciaInicial = existenciaInicial,
                entradas = entradas,
                ventas = ventas,
                existenciaTeorica = existenciaTeorica,
                physicalText = physicalText,
                physicalValue = physicalValue,
                diffQty = diffQty,
                itemIncome = itemIncome,
                isCoherent = isCoherent
            )
        }
    }

    val expectedBarIncome = if (barraCuadreRegistrado) {
        barraExpectedIncomeSaved
    } else {
        barReconciliations.sumOf { it.itemIncome }
    }

    val registeredBarIncome = if (barraCuadreRegistrado) {
        barraRegisteredIncomeSaved
    } else {
        closedOrders.sumOf { it.totalBarra }
    }

    val diferenciaMonetariaBarra = registeredBarIncome - expectedBarIncome

    val allCountsEntered = barReconciliations.isNotEmpty() && barReconciliations.all { it.physicalValue != null }
    val existenciasCoherent = barReconciliations.all { it.isCoherent }
    val moneyCoherent = diferenciaMonetariaBarra == 0.0

    val unifiedVerdict = when {
        barReconciliations.isEmpty() -> "PENDIENTE_CONTEO"
        !allCountsEntered -> "PENDIENTE_CONTEO"
        existenciasCoherent && moneyCoherent -> "CUADRA_COMPLETAMENTE"
        existenciasCoherent && !moneyCoherent -> "DESCUADRE_DINERO"
        !existenciasCoherent && moneyCoherent -> "DESCUADRE_EXISTENCIAS"
        else -> "DESCUADRE_AMBOS"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuadre de Barra",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Reconciliación monetaria y control físico de existencias de Barra.",
                        color = Slate500,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElQadreNavy,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.LocalBar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (barraCuadreRegistrado) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Emerald50,
                    border = BorderStroke(1.dp, Emerald200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arqueo de Barra Registrado",
                                fontWeight = FontWeight.Bold,
                                color = Emerald800,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "El cuadre de barra (dinero y existencias) ha sido guardado exitosamente para esta jornada.",
                                color = Emerald700,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // SECCIÓN 1: CONTROL DE EXISTENCIAS
        item {
            Text(
                text = "CONTROL DE EXISTENCIAS (INVENTARIO DE BARRA)",
                fontWeight = FontWeight.Bold,
                color = Slate500,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }

        if (barReconciliations.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay productos configurados como Mercadería de Barra.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = Slate500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(barReconciliations.size) { index ->
                val recon = barReconciliations[index]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = recon.productName,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Precio Unitario: $${"%.2f".format(recon.price)} CUP | Presentación: ${recon.unit}",
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                            if (!barraCuadreRegistrado) {
                                Text(
                                    text = "Copiar Teórica",
                                    color = ElQadreNavy,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            physicalCounts[recon.mercaderiaId] = "%.1f".format(recon.existenciaTeorica)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Inicial", fontSize = 10.sp, color = Slate400)
                                Text("${"%.1f".format(recon.existenciaInicial)}", fontWeight = FontWeight.Bold, color = Slate700, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Entradas (+)", fontSize = 10.sp, color = Slate400)
                                Text("${"%.1f".format(recon.entradas)}", fontWeight = FontWeight.Bold, color = if (recon.entradas > 0) Emerald600 else Slate700, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ventas (-)", fontSize = 10.sp, color = Slate400)
                                Text("${"%.1f".format(recon.ventas)}", fontWeight = FontWeight.Bold, color = if (recon.ventas > 0) Rose600 else Slate700, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Teórica Final", fontSize = 10.sp, color = ElQadreNavy, fontWeight = FontWeight.Medium)
                                Text("${"%.1f".format(recon.existenciaTeorica)}", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = recon.physicalText,
                                onValueChange = { input ->
                                    if (!barraCuadreRegistrado) {
                                        if (input.isEmpty() || input.all { it.isDigit() || it == '.' || it == '-' }) {
                                            physicalCounts[recon.mercaderiaId] = input
                                        }
                                    }
                                },
                                label = { Text("Existencia Física Final") },
                                placeholder = { Text("Conteo real") },
                                singleLine = true,
                                enabled = !barraCuadreRegistrado,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("barra_physical_input_${recon.mercaderiaId}"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElQadreNavy,
                                    focusedLabelColor = ElQadreNavy,
                                    unfocusedBorderColor = Slate200,
                                    disabledTextColor = Slate700,
                                    disabledBorderColor = Slate100
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                if (recon.physicalValue == null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Slate100,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("Pendiente", color = Slate500, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    val badgeColor = when {
                                        recon.diffQty == 0.0 -> Emerald50
                                        recon.diffQty!! > 0.0 -> ElQadreGoldSoft
                                        else -> Rose50
                                    }
                                    val badgeBorder = when {
                                        recon.diffQty == 0.0 -> Emerald200
                                        recon.diffQty!! > 0.0 -> ElQadreGold
                                        else -> Rose200
                                    }
                                    val badgeText = when {
                                        recon.diffQty == 0.0 -> "Coincide"
                                        recon.diffQty!! > 0.0 -> "Sobrante\n(+${"%.1f".format(recon.diffQty)})"
                                        else -> "Faltante\n(${"%.1f".format(recon.diffQty)})"
                                    }
                                    val badgeTextColor = when {
                                        recon.diffQty == 0.0 -> Emerald800
                                        recon.diffQty!! > 0.0 -> ElQadreGoldDark
                                        else -> Rose800
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeColor,
                                        border = BorderStroke(1.dp, badgeBorder),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                color = badgeTextColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECCIÓN 2: CONTROL DE DINERO (CUADRE MONETARIO DE BARRA)
        item {
            Text(
                text = "CONTROL DE DINERO (INGRESOS DE BARRA)",
                fontWeight = FontWeight.Bold,
                color = Slate500,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }


        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ingresos Esperados (Teórico)", fontSize = 11.sp, color = Slate500)
                            Text(
                                text = "$${"%.2f".format(expectedBarIncome)} CUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.testTag("barra_dinero_esperado")
                            )
                            Text("Calculado de cantidad vendida", fontSize = 9.sp, color = Slate400)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Registrado en Caja", fontSize = 11.sp, color = Slate500)
                            Text(
                                text = "$${"%.2f".format(registeredBarIncome)} CUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.testTag("barra_dinero_registrado")
                            )
                            Text("Cobros de barra de jornada", fontSize = 9.sp, color = Slate400)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Slate100)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Diferencia Monetaria Barra:", fontWeight = FontWeight.Medium, color = Slate700, fontSize = 13.sp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                diferenciaMonetariaBarra == 0.0 -> Emerald50
                                diferenciaMonetariaBarra > 0.0 -> ElQadreGoldSoft
                                else -> Rose50
                            },
                            border = BorderStroke(1.dp, when {
                                diferenciaMonetariaBarra == 0.0 -> Emerald200
                                diferenciaMonetariaBarra > 0.0 -> ElQadreGold
                                else -> Rose200
                            }),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "${if (diferenciaMonetariaBarra >= 0.0) "+" else ""}$${"%.2f".format(diferenciaMonetariaBarra)} CUP",
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("barra_dinero_diferencia"),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = when {
                                    diferenciaMonetariaBarra == 0.0 -> Emerald800
                                    diferenciaMonetariaBarra > 0.0 -> ElQadreGoldDark
                                    else -> Rose800
                                }
                            )
                        }
                    }
                }
            }
        }

        // SECCIÓN 3: RESULTADO FINAL (UNIFIED VERDICT BANNER)
        item {
            val verdictTitle: String
            val verdictDesc: String
            val verdictColor: Color
            val verdictBorder: Color
            val verdictText: Color

            when (unifiedVerdict) {
                "PENDIENTE_CONTEO" -> {
                    verdictTitle = "CONTEO INCOMPLETO"
                    verdictDesc = "Falta introducir la existencia física de todos los productos para computar el cuadre."
                    verdictColor = Slate100
                    verdictBorder = Slate300
                    verdictText = Slate700
                }
                "CUADRA_COMPLETAMENTE" -> {
                    verdictTitle = "BARRA CUADRADA COMPLETAMENTE"
                    verdictDesc = "¡Excelente! Coinciden con precisión matemática tanto el efectivo recaudado como el inventario físico."
                    verdictColor = Emerald50
                    verdictBorder = Emerald200
                    verdictText = Emerald800
                }
                "DESCUADRE_DINERO" -> {
                    verdictTitle = "DESCUADRE EN DINERO"
                    verdictDesc = "El inventario de mercaderías coincide, pero hay diferencias de dinero registrado frente al esperado."
                    verdictColor = Rose50
                    verdictBorder = Rose200
                    verdictText = Rose800
                }
                "DESCUADRE_EXISTENCIAS" -> {
                    verdictTitle = "DESCUADRE EN EXISTENCIAS"
                    verdictDesc = "La contabilidad monetaria cuadra, pero existen sobrantes o faltantes en el conteo físico de productos."
                    verdictColor = Rose50
                    verdictBorder = Rose200
                    verdictText = Rose800
                }
                else -> {
                    verdictTitle = "DESCUADRE EN DINERO Y EXISTENCIAS"
                    verdictDesc = "Se detectaron discrepancias tanto en los ingresos registrados en caja como en las existencias físicas del inventario."
                    verdictColor = Rose50
                    verdictBorder = Rose200
                    verdictText = Rose800
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = verdictColor,
                border = BorderStroke(1.dp, verdictBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = verdictTitle,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = verdictText,
                        modifier = Modifier.testTag("barra_veredicto_titulo"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = verdictDesc,
                        color = verdictText.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // NOTAS DE BARRA

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = barraNotesText,
                        onValueChange = { input ->
                            if (!barraCuadreRegistrado) {
                                barraNotesText = input
                            }
                        },
                        label = { Text("Notas de Barra / Observaciones") },
                        placeholder = { Text("Opcional: Escribe explicaciones adicionales del arqueo de barra...") },
                        enabled = !barraCuadreRegistrado,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("notas_barra_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy,
                            unfocusedBorderColor = Slate200,
                            disabledBorderColor = Slate100,
                            disabledLabelColor = Slate400,
                            disabledTextColor = Slate700
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )
                }
            }
        }

        // ACCIONES DE BARRA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!barraCuadreRegistrado) {
                    Button(
                        onClick = { showConfirmBarraDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_registrar_cuadre_barra"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allCountsEntered && existenciasCoherent && moneyCoherent) Emerald600 else ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = allCountsEntered
                    ) {
                        Text(
                            text = "REGISTRAR CUADRE DE BARRA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val mercIds = barMercaderias.map { it.id }
                            viewModel.resetCuadreBarra(activeJornada.id, mercIds)
                            barraCuadreRegistrado = false
                            barMercaderias.forEach { physicalCounts[it.id] = "" }
                            barraNotesText = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_volver_hacer_arqueo_barra"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Slate600
                        ),
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "VOLVER A REALIZAR ARQUEO BARRA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // CONFIRM DIALOG
    if (showConfirmBarraDialog) {
        val physicalMapFloat = barReconciliations.associate { it.mercaderiaId to (it.physicalValue ?: 0.0) }
        AlertDialog(
            onDismissRequest = { showConfirmBarraDialog = false },
            title = {
                Text(
                    text = "Confirmar Cuadre de Barra",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de que desea registrar este arqueo de Barra? Se guardarán el conteo de existencias y los importes registrados.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmBarraDialog = false
                        viewModel.registrarCuadreBarra(
                            activeJornada.id,
                            expectedBarIncome,
                            registeredBarIncome,
                            barraNotesText,
                            physicalMapFloat
                        )
                        barraExpectedIncomeSaved = expectedBarIncome
                        barraRegisteredIncomeSaved = registeredBarIncome
                        barraNotesSaved = barraNotesText
                        barraCuadreRegistrado = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White)
                ) {
                    Text("REGISTRAR CUADRE BARRA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBarraDialog = false }) {
                    Text("Cancelar", color = Slate500, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
