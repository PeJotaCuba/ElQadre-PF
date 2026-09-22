package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONArray
import com.example.util.CostCalculationHelper
import com.example.util.CostSheetPdfExporter
import com.example.util.MercaderiaCostSheet
import com.example.ui.components.ProductAgregadosDialog
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Rediseño definitivo del Módulo Mercaderías (ElQadre).
 * Consta de ÚNICAMENTE 3 apartados:
 * 1. INVENTARIO (Apartado principal: productos, existencias, precios, fichas y control total)
 * 2. EGRESOS (Gastos e Inversiones propios de Mercaderías, con distinción Puntual vs General)
 * 3. BALANCES (Consolidación económica global, utilidades proyectadas y analítica por producto)
 */
enum class MercaderiasTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    INVENTARIO("Inventario", Icons.Outlined.Inventory2),
    EGRESOS("Egresos e Inversiones", Icons.Outlined.ReceiptLong),
    BALANCES("Balances y Fichas", Icons.Outlined.Assessment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercaderiasWorkspaceDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MercaderiasTab.INVENTARIO) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Dialog state for adding/editing a merchandise
    var showAddDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    val createInventoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("version", System.currentTimeMillis().toString())
                
                val mercaderiasArray = JSONArray()
                for (merc in uiState.mercaderias) {
                    val mercObj = JSONObject()
                    mercObj.put("id", merc.id)
                    mercObj.put("productId", merc.productId)
                    mercObj.put("acquisitionCost", merc.acquisitionCost)
                    mercObj.put("unitOfMeasure", merc.unitOfMeasure)
                    mercObj.put("initialStock", merc.initialStock)
                    mercObj.put("isActive", merc.isActive)
                    mercaderiasArray.put(mercObj)
                }
                jsonObject.put("mercaderias", mercaderiasArray)
                
                val jsonString = jsonObject.toString(4)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Toast.makeText(context, "Inventario generado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportQMercanciasLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = com.example.util.AdminJsonExportHelper.buildQMercanciasJson(uiState)
            val success = com.example.util.AdminJsonExportHelper.writeJsonToUri(context, uri, json)
            if (success) {
                Toast.makeText(context, "Q_mercaderias.json exportado correctamente", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al exportar archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var mercToEdit by remember { mutableStateOf<Mercaderia?>(null) }

    // Dialog state for registering inventory movement
    var showMovementDialog by remember { mutableStateOf(false) }
    var preselectedMercaderiaId by remember { mutableStateOf<Long?>(null) }

    // Dialog state for viewing movements history of a merchandise
    var showHistoryMercaderia by remember { mutableStateOf<Mercaderia?>(null) }

    // Dialog state for deleting a single merchandise
    var mercToDelete by remember { mutableStateOf<Mercaderia?>(null) }

    // Dialog state for "LIMPIAR TODO" confirmation
    var showClearAllConfirmation by remember { mutableStateOf(false) }

    // Dialog state for Cargar TXT
    var showCargarTxtDialog by remember { mutableStateOf(false) }

    // Dialog state for Ficha de Costo de Mercadería
    var selectedMercaderiaForFichaCosto by remember { mutableStateOf<Mercaderia?>(null) }

    // Dialog state for Egresos (Gastos / Inversiones)
    var showAddEgresoDialog by remember { mutableStateOf(false) }
    var egresoToEdit by remember { mutableStateOf<GastoGeneral?>(null) }
    var showAddInversionDialog by remember { mutableStateOf(false) }
    var inversionToEdit by remember { mutableStateOf<Inversion?>(null) }
    var productForAgregadosDialog by remember { mutableStateOf<Product?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    drawerContainerColor = ElQadreNavy,
                    drawerContentColor = Color.White,
                    windowInsets = WindowInsets.safeDrawing,
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Drawer Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ElQadreGold.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MERCADERÍAS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Menú de Navegación",
                                    fontSize = 11.sp,
                                    color = ElQadreGoldSoft
                                )
                            }
                        }

                        HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 12.dp))

                        // Drawer Navigation Items
                        MercaderiasTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) ElQadreNavy else ElQadreGold,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) ElQadreNavy else Color.White
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    selectedTab = tab
                                    scope.launch { drawerState.close() }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = ElQadreGold,
                                    unselectedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("drawer_item_merc_${tab.name.lowercase()}")
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 12.dp))

                        // Exit button in drawer
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Rose500,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Cerrar Workspace",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Rose500
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElQadreBackground),
                color = ElQadreBackground
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ElQadreGold.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = ElQadreGold,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "MERCADERÍAS: ${selectedTab.title.uppercase()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            letterSpacing = 0.5.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Toca ☰ para cambiar de sección",
                                            fontSize = 11.sp,
                                            color = ElQadreGoldSoft
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                        }
                                    },
                                    modifier = Modifier.testTag("menu_button_mercaderias")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menú",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_mercaderias_workspace")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = ElQadreNavy,
                                titleContentColor = Color.White
                            )
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .navigationBarsPadding()
                            .background(ElQadreBackground)
                    ) {
                        when (selectedTab) {
                        MercaderiasTab.INVENTARIO -> {
                            InventarioMercaderiasTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                onAddClick = {
                                    mercToEdit = null
                                    showAddDialog = true
                                },
                                onEditClick = { merc ->
                                    mercToEdit = merc
                                    showAddDialog = true
                                },
                                onRegisterMovementClick = { mercId ->
                                    preselectedMercaderiaId = mercId
                                    showMovementDialog = true
                                },
                                onViewHistoryClick = { merc ->
                                    showHistoryMercaderia = merc
                                },
                                onFichaCostoClick = { merc ->
                                    selectedMercaderiaForFichaCosto = merc
                                },
                                onDeleteClick = { merc ->
                                    mercToDelete = merc
                                },
                                onAgregadosClick = { merc, prod ->
                                    val targetProd = prod ?: uiState.products.find { it.id == merc.productId }
                                    if (targetProd != null) {
                                        productForAgregadosDialog = targetProd
                                    }
                                },
                                onClearAllClick = {
                                    showClearAllConfirmation = true
                                },
                                onCargarTxtClick = {
                                    showCargarTxtDialog = true
                                },
                                onGenerateInventoryClick = {
                                    createInventoryLauncher.launch("qmercainv.json")
                                }
                            )
                        }
                        MercaderiasTab.EGRESOS -> {
                            EgresosMercaderiasTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                onAddEgresoClick = {
                                    egresoToEdit = null
                                    showAddEgresoDialog = true
                                },
                                onEditEgresoClick = { gasto ->
                                    egresoToEdit = gasto
                                    showAddEgresoDialog = true
                                },
                                onAddInversionClick = {
                                    inversionToEdit = null
                                    showAddInversionDialog = true
                                },
                                onEditInversionClick = { inversion ->
                                    inversionToEdit = inversion
                                    showAddInversionDialog = true
                                }
                            )
                        }
                        MercaderiasTab.BALANCES -> {
                            BalancesMercaderiasTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                onFichaCostoClick = { merc ->
                                    selectedMercaderiaForFichaCosto = merc
                                }
                            )
                        }
                    }

                    // Botón flotante amarillo "+" abajo a la derecha: Crear Mercadería
                    FloatingActionButton(
                        onClick = {
                            mercToEdit = null
                            showAddDialog = true
                        },
                        containerColor = ElQadreGold,
                        contentColor = ElQadreNavy,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("fab_add_mercaderia")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear Mercadería",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAddDialog) {
        AddEditMercaderiaDialog(
            uiState = uiState,
            viewModel = viewModel,
            mercaderia = mercToEdit,
            onDismiss = {
                showAddDialog = false
                mercToEdit = null
            }
        )
    }

    if (showMovementDialog) {
        RegisterMovementDialog(
            uiState = uiState,
            viewModel = viewModel,
            initialMercaderiaId = preselectedMercaderiaId,
            onDismiss = {
                showMovementDialog = false
                preselectedMercaderiaId = null
            }
        )
    }

    if (showHistoryMercaderia != null) {
        MercaderiaMovementsHistoryDialog(
            mercaderia = showHistoryMercaderia!!,
            uiState = uiState,
            viewModel = viewModel,
            onRegisterMovement = {
                preselectedMercaderiaId = showHistoryMercaderia!!.id
                showHistoryMercaderia = null
                showMovementDialog = true
            },
            onDismiss = { showHistoryMercaderia = null }
        )
    }

    if (mercToDelete != null) {
        val prod = uiState.products.find { it.id == mercToDelete!!.productId }
        AlertDialog(
            onDismissRequest = { mercToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Rose600, modifier = Modifier.size(32.dp)) },
            title = { Text("Eliminar Mercadería", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Text(
                    "¿Está seguro de que desea eliminar definitivamente '${prod?.name ?: "esta mercadería"}' y todos sus movimientos de inventario?\n\nEsta acción dejará el inventario limpio sin afectar a otros módulos.",
                    color = Slate700,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMercaderiaPermanently(mercToDelete!!)
                        mercToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Eliminar Definitivamente", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { mercToDelete = null }) {
                    Text("Cancelar", color = Slate600)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Rose600, modifier = Modifier.size(36.dp)) },
            title = { Text("Limpiar Todo el Inventario", fontWeight = FontWeight.Bold, color = Rose600) },
            text = {
                Column {
                    Text(
                        "¿Está completamente seguro de limpiar todos los productos de Mercaderías?",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Esta acción eliminará todas las mercaderías registradas y sus movimientos asociados de forma coherente.\n\n• NO afectará materias primas ni recetas.\n• NO afectará productos elaborados de Cocina.\n• No dejará registros huérfanos en la base de datos.",
                        color = Slate700,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMercaderias()
                        showClearAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_confirm_clear_all_mercaderias")
                ) {
                    Text("Sí, Limpiar Todo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) {
                    Text("Cancelar", color = Slate600)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    if (selectedMercaderiaForFichaCosto != null) {
        FichaCostoMercaderiaDialog(
            mercaderia = selectedMercaderiaForFichaCosto!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                selectedMercaderiaForFichaCosto = null
            }
        )
    }

    if (showAddEgresoDialog) {
        AddEditEgresoMercaderiaDialog(
            uiState = uiState,
            viewModel = viewModel,
            gasto = egresoToEdit,
            onDismiss = {
                showAddEgresoDialog = false
                egresoToEdit = null
            }
        )
    }

    if (showAddInversionDialog) {
        AddEditInversionMercaderiaDialog(
            uiState = uiState,
            viewModel = viewModel,
            inversion = inversionToEdit,
            onDismiss = {
                showAddInversionDialog = false
                inversionToEdit = null
            }
        )
    }

    productForAgregadosDialog?.let { prod ->
        ProductAgregadosDialog(
            product = prod,
            onDismiss = { productForAgregadosDialog = null },
            onSaveProduct = { updated ->
                viewModel.updateProduct(updated)
                productForAgregadosDialog = updated
            }
        )
    }

    if (showCargarTxtDialog) {
        CargarMercaderiasTxtDialog(
            onDismiss = { showCargarTxtDialog = false },
            viewModel = viewModel,
            uiState = uiState
        )
    }
    }
}

/* ==========================================================================
   1. APARTADO: INVENTARIO (Principal de Mercaderías)
   ========================================================================== */
@Composable
fun InventarioMercaderiasTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onAddClick: () -> Unit,
    onEditClick: (Mercaderia) -> Unit,
    onRegisterMovementClick: (Long) -> Unit,
    onViewHistoryClick: (Mercaderia) -> Unit,
    onFichaCostoClick: (Mercaderia) -> Unit,
    onDeleteClick: (Mercaderia) -> Unit,
    onAgregadosClick: (Mercaderia, Product?) -> Unit = { _, _ -> },
    onClearAllClick: () -> Unit,
    onCargarTxtClick: () -> Unit = {},
    onGenerateInventoryClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("TODOS") } // "TODOS", "ACTIVOS", "INACTIVOS", "BAJO_STOCK"

    val filteredMercaderias = uiState.mercaderias.filter { m ->
        val associatedProduct = uiState.products.find { p -> p.id == m.productId }
        val currentStock = viewModel.getMercaderiaCurrentStock(m.id, m.initialStock)

        val matchesSearch = (associatedProduct?.name?.contains(searchQuery, ignoreCase = true) == true ||
                associatedProduct?.code?.contains(searchQuery, ignoreCase = true) == true ||
                associatedProduct?.category?.contains(searchQuery, ignoreCase = true) == true ||
                m.unitOfMeasure.contains(searchQuery, ignoreCase = true))

        val matchesStatus = when (filterStatus) {
            "ACTIVOS" -> m.isActive
            "INACTIVOS" -> !m.isActive
            "BAJO_STOCK" -> currentStock in 0.001..5.0
            else -> true
        }

        matchesSearch && matchesStatus
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Inventario de Mercaderías",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredMercaderias.size} de ${uiState.mercaderias.size} productos registrados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCargarTxtClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                    border = BorderStroke(1.dp, ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp).testTag("btn_cargar_txt_mercaderias")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreNavy)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cargar TXT", color = ElQadreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (uiState.mercaderias.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose300),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp).testTag("btn_limpiar_todo_mercaderias")
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose600)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar Todo", color = Rose600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onGenerateInventoryClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generar Inventario", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre, código o presentación...", color = Slate500) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate600) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElQadreNavy,
                unfocusedBorderColor = Slate300,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("TODOS", "ACTIVOS", "INACTIVOS", "BAJO_STOCK").forEach { f ->
                val isSelected = filterStatus == f
                val labelText = when (f) {
                    "BAJO_STOCK" -> "BAJO STOCK (≤5)"
                    else -> f
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { filterStatus = f },
                    label = { Text(labelText, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElQadreNavy,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = Slate700
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredMercaderias.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No se encontraron mercaderías",
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Crea una mercadería con el botón '+ Nueva Mercadería' para añadirla al catálogo e inventario.",
                        color = Slate600,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredMercaderias, key = { it.id }) { merc ->
                    val product = uiState.products.find { p -> p.id == merc.productId }
                    val currentStock = viewModel.getMercaderiaCurrentStock(merc.id, merc.initialStock)
                    val costSheet = CostCalculationHelper.calculateMercaderiaCostSheet(merc, uiState, viewModel)

                    MercaderiaInventoryCard(
                        mercaderia = merc,
                        product = product,
                        currentStock = currentStock,
                        costSheet = costSheet,
                        onEditClick = { onEditClick(merc) },
                        onToggleActive = {
                            val updated = merc.copy(isActive = !merc.isActive)
                            viewModel.updateMercaderia(updated)
                            if (product != null) {
                                viewModel.updateProduct(product.copy(isAvailable = updated.isActive))
                            }
                        },
                        onRegisterMovementClick = { onRegisterMovementClick(merc.id) },
                        onViewHistoryClick = { onViewHistoryClick(merc) },
                        onFichaCostoClick = { onFichaCostoClick(merc) },
                        onDeleteClick = { onDeleteClick(merc) },
                        onAgregadosClick = { onAgregadosClick(merc, product) }
                    )
                }
            }
        }
    }
}

@Composable
fun MercaderiaInventoryCard(
    mercaderia: Mercaderia,
    product: Product?,
    currentStock: Double,
    costSheet: MercaderiaCostSheet,
    onEditClick: () -> Unit,
    onToggleActive: () -> Unit,
    onRegisterMovementClick: () -> Unit,
    onViewHistoryClick: () -> Unit,
    onFichaCostoClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAgregadosClick: () -> Unit = {}
) {
    val stockColor = when {
        currentStock <= 0.0 -> Rose600
        currentStock <= 5.0 -> Amber600
        else -> Emerald600
    }
    val stockBg = when {
        currentStock <= 0.0 -> Rose50
        currentStock <= 5.0 -> Color(0xFFFFFBEB)
        else -> Emerald50
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, if (mercaderia.isActive) Slate200 else Slate300)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Product Name, Code, Category, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product?.name ?: "Mercadería #${mercaderia.id}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (mercaderia.isActive) ElQadreNavy else Slate500
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ElQadreGold.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "BARRA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Código: ${product?.code ?: "N/A"} • Presentación: ${mercaderia.unitOfMeasure} • Catálogo: ${product?.category ?: "Barra"}",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (mercaderia.isActive) Emerald50 else Slate100,
                    border = BorderStroke(1.dp, if (mercaderia.isActive) Emerald500 else Slate300),
                    modifier = Modifier.clickable { onToggleActive() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (mercaderia.isActive) Emerald600 else Slate400)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (mercaderia.isActive) "ACTIVO" else "INACTIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mercaderia.isActive) Emerald700 else Slate600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            // Joint Metrics Grid: Existencias, Costo Adquisición, Precio Referencia, Precio Definitivo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock
                Column(
                    modifier = Modifier
                        .clickable { onViewHistoryClick() }
                        .padding(2.dp)
                ) {
                    Text("EXISTENCIAS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stockBg,
                        border = BorderStroke(1.dp, stockColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${"%.0f".format(currentStock)} u",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = stockColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Costo Adquisición
                Column {
                    Text("COSTO ADQUISICIÓN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Text(
                        text = "$${"%.2f".format(mercaderia.acquisitionCost)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Slate800
                    )
                    Text("Base original", fontSize = 9.sp, color = Slate400)
                }

                // Precio de Referencia
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PRECIO REF.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Text(
                        text = "$${"%.2f".format(costSheet.precioReferencia)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreGoldDark
                    )
                    Text("+30% s/CRU", fontSize = 9.sp, color = Slate400)
                }

                // Precio Definitivo
                Column(horizontalAlignment = Alignment.End) {
                    Text("PRECIO VENTA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Text(
                        text = "$${"%.2f".format(product?.price ?: 0.0)} CUP",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                    Text("En Catálogo", fontSize = 9.sp, color = Slate400)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Ficha de Costo, Movimiento, Editar, Eliminar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onFichaCostoClick,
                    modifier = Modifier.weight(1.3f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ElQadreGoldDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ficha Costo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onRegisterMovementClick,
                    modifier = Modifier.weight(1.2f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ElQadreNavy),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Movimiento", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(38.dp).background(Slate100, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Slate700, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(38.dp).background(Rose50, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onAgregadosClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("open_agregados_mercaderia_${mercaderia.id}"),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AGREGADOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ==========================================================================
   2. APARTADO: EGRESOS (Gastos e Inversiones propios de Mercaderías)
   ========================================================================== */
@Composable
fun EgresosMercaderiasTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onAddEgresoClick: () -> Unit,
    onEditEgresoClick: (GastoGeneral) -> Unit,
    onAddInversionClick: () -> Unit,
    onEditInversionClick: (Inversion) -> Unit
) {
    var subTab by remember { mutableStateOf(0) } // 0: Gastos (Operativos), 1: Inversiones (Activos)
    var egresoToDelete by remember { mutableStateOf<GastoGeneral?>(null) }
    var inversionToDelete by remember { mutableStateOf<Inversion?>(null) }

    // Summary calculations for Mercaderías
    val totalGastosMensuales = uiState.gastosGenerales.filter { it.isActive && it.inversionId == null && it.scope == "MERCADERIAS" }.sumOf { g ->
        g.dailyCost() * 30.0
    }

    val totalDepreciacionMensual = uiState.inversiones.filter { it.scope == "MERCADERIAS" }.sumOf { inv ->
        inv.monthlyDepreciation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Gastos Indirectos Mes", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("$${"%.2f".format(totalGastosMensuales)} CUP", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                    Text("Operativos corrientes", fontSize = 9.sp, color = Slate500)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Depreciación Activos Mes", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("$${"%.2f".format(totalDepreciacionMensual)} CUP", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                    Text("Amortización inversiones", fontSize = 9.sp, color = Slate500)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtabs: Gastos vs Inversiones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = subTab,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                containerColor = Slate100,
                indicator = {},
                divider = {}
            ) {
                Tab(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    text = { Text("Gastos (${uiState.gastosGenerales.count { it.inversionId == null && it.scope == "MERCADERIAS" }})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    selectedContentColor = ElQadreNavy,
                    unselectedContentColor = Slate500
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    text = { Text("Inversiones (${uiState.inversiones.count { it.scope == "MERCADERIAS" }})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    selectedContentColor = ElQadreNavy,
                    unselectedContentColor = Slate500
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (subTab == 0) onAddEgresoClick() else onAddInversionClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).testTag("btn_add_egreso_or_inversion")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (subTab == 0) "Nuevo Gasto" else "Nueva Inversión", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (subTab == 0) {
            // Gastos List
            if (uiState.gastosGenerales.none { it.inversionId == null && it.scope == "MERCADERIAS" }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Receipt, contentDescription = null, tint = Slate400, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No hay gastos registrados", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        Text("Registra gastos indirectos como transporte, hielo o electricidad.", fontSize = 11.sp, color = Slate600)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.gastosGenerales.filter { it.inversionId == null && it.scope == "MERCADERIAS" }) { gasto ->
                        val targetIds = mutableListOf<Long>()
                        if (!gasto.targetProductIds.isNullOrBlank()) {
                            gasto.targetProductIds.split(",").mapNotNull { it.trim().toLongOrNull() }.let { targetIds.addAll(it) }
                        } else if (gasto.targetProductId != null && gasto.targetProductId != 0L) {
                            targetIds.add(gasto.targetProductId)
                        }
                        val isPuntual = targetIds.isNotEmpty()
                        val targetedNames = if (isPuntual) {
                            val names = targetIds.mapNotNull { id -> uiState.products.find { it.id == id }?.name }
                            if (names.isNotEmpty()) names.joinToString(", ") else "Productos seleccionados"
                        } else null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(gasto.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isPuntual) Amber50 else Color(0xFFEFF6FF)
                                        ) {
                                            Text(
                                                text = if (isPuntual) "PUNTUAL (${targetedNames})" else "GENERAL (Todas)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPuntual) Amber700 else ElQadreNavy,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Monto: $${"%.2f".format(gasto.amount)} CUP • Período: ${gasto.period} • Categoría: ${gasto.category}",
                                        fontSize = 11.sp,
                                        color = Slate600
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditEgresoClick(gasto) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Slate700, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { egresoToDelete = gasto }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Inversiones List
            if (uiState.inversiones.none { it.scope == "MERCADERIAS" }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = Slate400, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No hay inversiones registradas", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        Text("Registra activos como enfriadores, vitrinas o licuadoras para calcular su depreciación.", fontSize = 11.sp, color = Slate600)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.inversiones.filter { it.scope == "MERCADERIAS" }) { inversion ->
                        val targetIds = mutableListOf<Long>()
                        if (!inversion.targetProductIds.isNullOrBlank()) {
                            inversion.targetProductIds.split(",").mapNotNull { it.trim().toLongOrNull() }.let { targetIds.addAll(it) }
                        } else if (inversion.targetProductId != null && inversion.targetProductId != 0L) {
                            targetIds.add(inversion.targetProductId)
                        }
                        val isPuntual = targetIds.isNotEmpty()
                        val targetedNames = if (isPuntual) {
                            val names = targetIds.mapNotNull { id -> uiState.products.find { it.id == id }?.name }
                            if (names.isNotEmpty()) names.joinToString(", ") else "Productos seleccionados"
                        } else null
                        val depMensual = inversion.monthlyDepreciation()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(inversion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isPuntual) Amber50 else Color(0xFFEFF6FF)
                                        ) {
                                            Text(
                                                text = if (isPuntual) "PUNTUAL (${targetedNames})" else "GENERAL (Todas)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPuntual) Amber700 else ElQadreNavy,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Monto: $${"%.2f".format(inversion.amount)} CUP • Vida útil: ${inversion.usefulLife.toInt()} ${inversion.usefulLifeUnit.lowercase()} • Deprec: $${"%.2f".format(depMensual)}/mes",
                                        fontSize = 11.sp,
                                        color = Slate600
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditInversionClick(inversion) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Slate700, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { inversionToDelete = inversion }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (egresoToDelete != null) {
        AlertDialog(
            onDismissRequest = { egresoToDelete = null },
            title = { Text("Eliminar Gasto", fontWeight = FontWeight.Bold) },
            text = { Text("¿Desea eliminar el gasto '${egresoToDelete!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGastoGeneral(egresoToDelete!!)
                        egresoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { egresoToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (inversionToDelete != null) {
        AlertDialog(
            onDismissRequest = { inversionToDelete = null },
            title = { Text("Eliminar Inversión", fontWeight = FontWeight.Bold) },
            text = { Text("¿Desea eliminar la inversión '${inversionToDelete!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInversion(inversionToDelete!!)
                        inversionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { inversionToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

/* ==========================================================================
   3. APARTADO: BALANCES (Consolidación económica completa de Mercaderías)
   ========================================================================== */
@Composable
fun BalancesMercaderiasTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onFichaCostoClick: (Mercaderia) -> Unit
) {
    val context = LocalContext.current
    val activeMercaderias = uiState.mercaderias.filter { it.isActive }

    // Pre-calculate cost sheets and metrics for active items
    val itemsWithCalculations = activeMercaderias.map { merc ->
        val product = uiState.products.find { p -> p.id == merc.productId }
        val currentStock = viewModel.getMercaderiaCurrentStock(merc.id, merc.initialStock)
        val costSheet = CostCalculationHelper.calculateMercaderiaCostSheet(merc, uiState, viewModel)
        val salePrice = product?.price ?: 0.0
        val effectiveStock = if (currentStock > 0) currentStock else 1.0

        object {
            val mercaderia = merc
            val productObj = product
            val stock = currentStock
            val sheet = costSheet
            val price = salePrice
            val totalAcq = merc.acquisitionCost * effectiveStock
            val totalEgresos = costSheet.gastoIndirectoUnitario * effectiveStock
            val totalReal = costSheet.costoRealUnitario * effectiveStock
            val totalSales = salePrice * effectiveStock
            val totalProfit = (salePrice - costSheet.costoRealUnitario) * effectiveStock
        }
    }

    val totalAcquisitionCost = itemsWithCalculations.sumOf { it.totalAcq }
    val totalEgresosAssigned = itemsWithCalculations.sumOf { it.totalEgresos }
    val totalRealStockCost = itemsWithCalculations.sumOf { it.totalReal }
    val totalPotentialSales = itemsWithCalculations.sumOf { it.totalSales }
    val totalPotentialProfit = itemsWithCalculations.sumOf { it.totalProfit }
    val globalMarginPercent = if (totalPotentialSales > 0) (totalPotentialProfit / totalPotentialSales) * 100.0 else 0.0
    val totalStockUnits = itemsWithCalculations.sumOf { it.stock }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title & Export ZIP button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Balances Económicos",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Consolidado de costos reales, ingresos y rentabilidad",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600
                )
            }

            OutlinedButton(
                onClick = {
                    CostSheetPdfExporter.exportAllMercaderiasCostSheetsZip(context, uiState)
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ElQadreGoldDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark),
                modifier = Modifier.testTag("btn_export_all_zip_balances")
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGoldDark)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exportar Fichas (ZIP)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 1: Costo Adquisición vs Egresos Asignados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Costo Adquisición Total", color = Slate600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalAcquisitionCost)} CUP", color = ElQadreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Inversión directa en compra", color = Slate500, fontSize = 9.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Egresos Asignados", color = Slate600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalEgresosAssigned)} CUP", color = Amber700, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Gastos + Depreciación", color = Slate500, fontSize = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Costo Real vs Venta Potencial
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Rose200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Costo Real Global", color = Rose700, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalRealStockCost)} CUP", color = Rose600, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Adq. + Egresos totales", color = Slate500, fontSize = 9.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Venta Potencial Bruta", color = Slate600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$${"%.2f".format(totalPotentialSales)} CUP", color = ElQadreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("A precios definitivos", color = Slate500, fontSize = 9.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main KPI Banner: Utilidad Neta Proyectada & Margen
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (totalPotentialProfit >= 0) Emerald200 else Rose200)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Utilidad Neta Proyectada", color = Slate600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$${"%.2f".format(totalPotentialProfit)} CUP",
                        color = if (totalPotentialProfit >= 0) Emerald600 else Rose600,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("En inventario total (${"%.0f".format(totalStockUnits)} unidades)", fontSize = 10.sp, color = Slate500)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (globalMarginPercent >= 30.0) Emerald50 else if (globalMarginPercent >= 15.0) Color(0xFFFFFBEB) else Rose50,
                    border = BorderStroke(1.dp, if (globalMarginPercent >= 30.0) Emerald600 else if (globalMarginPercent >= 15.0) Amber600 else Rose600)
                ) {
                    Text(
                        text = "${"%.1f".format(globalMarginPercent)}% Margen Global",
                        color = if (globalMarginPercent >= 30.0) Emerald700 else if (globalMarginPercent >= 15.0) Amber700 else Rose700,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Desglose Analítico por Mercadería",
            style = MaterialTheme.typography.titleMedium,
            color = ElQadreNavy,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        itemsWithCalculations.forEach { item ->
            val merc = item.mercaderia
            val product = item.productObj
            val sheet = item.sheet
            val unitProfit = sheet.utilidadUnitaria
            val unitMargin = sheet.margenPorcentual

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product?.name ?: "Mercadería #${merc.id}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Código: ${product?.code ?: "N/A"} • Stock: ${"%.0f".format(item.stock)} ${merc.unitOfMeasure}",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (unitMargin >= 30.0) Emerald50 else if (unitMargin >= 15.0) Color(0xFFFFFBEB) else Rose50
                        ) {
                            Text(
                                text = "${"%.1f".format(unitMargin)}% Margen",
                                color = if (unitMargin >= 30.0) Emerald700 else if (unitMargin >= 15.0) Amber700 else Rose700,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Slate100)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Costo Adq.", fontSize = 9.sp, color = Slate500)
                            Text("$${"%.2f".format(sheet.acquisitionCost)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Egresos Asig.", fontSize = 9.sp, color = Slate500)
                            Text("+$${"%.2f".format(sheet.gastoIndirectoUnitario)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Amber700)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Costo Real", fontSize = 9.sp, color = Slate500, fontWeight = FontWeight.Bold)
                            Text("$${"%.2f".format(sheet.costoRealUnitario)}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Rose600)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Precio Venta", fontSize = 9.sp, color = Slate500)
                            Text("$${"%.2f".format(item.price)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Utilidad Unit.", fontSize = 9.sp, color = Slate500)
                            Text(
                                text = "+$${"%.2f".format(unitProfit)}",
                                fontSize = 12.sp,
                                color = if (unitProfit >= 0) Emerald600 else Rose600,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { onFichaCostoClick(merc) },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ElQadreGoldDark),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark)
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver Ficha de Costo Completa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ==========================================================================
   DIALOGS AUXILIARES
   ========================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMercaderiaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    mercaderia: Mercaderia?,
    onDismiss: () -> Unit
) {
    val isEdit = mercaderia != null
    val existingProduct = if (isEdit) uiState.products.find { it.id == mercaderia?.productId } else null

    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var category by remember { mutableStateOf(existingProduct?.category ?: "Bebidas") }
    var code by remember { 
        mutableStateOf(
            existingProduct?.code ?: com.example.util.ProductCodeHelper.generateNextProductCode("BARRA", category, uiState.products)
        ) 
    }
    var unitOfMeasure by remember { mutableStateOf(mercaderia?.unitOfMeasure ?: "Lata 355ml") }

    // FORMA DE COMPRA Y CAMPOS NUMÉRICOS (Valores iniciales vacíos con placeholder 0.00)
    var purchaseMode by remember { mutableStateOf(mercaderia?.purchaseMode ?: "POR UNIDAD") }
    var purchasePriceStr by remember { 
        mutableStateOf(
            if (mercaderia?.purchasePrice != null && mercaderia.purchasePrice > 0.0) mercaderia.purchasePrice.toString()
            else if (mercaderia?.acquisitionCost != null && mercaderia.acquisitionCost > 0.0) mercaderia.acquisitionCost.toString() else ""
        ) 
    }
    var purchaseQtyStr by remember { mutableStateOf("") }
    var unitsPerLotStr by remember { 
        mutableStateOf(
            if (mercaderia != null && mercaderia.unitsPerLot > 0.0) mercaderia.unitsPerLot.toInt().toString() else ""
        ) 
    }
    // GASTOS DE LA COMPRA Y COMPARTIDO (1-5)
    val initialUnitsForExpenses = if (mercaderia != null) {
        if (mercaderia.initialStock > 0.0) mercaderia.initialStock else 1.0
    } else 1.0

    var purchaseExpensesStr by remember { 
        mutableStateOf(
            if (mercaderia != null && mercaderia.directExpenses > 0.0) {
                "%.2f".format(mercaderia.directExpenses * initialUnitsForExpenses).replace(",", ".")
            } else ""
        ) 
    }
    var sharedDivisorStr by remember { mutableStateOf("1") }

    var desiredMarginPercentStr by remember { mutableStateOf("") }
    var definitivePriceStr by remember { mutableStateOf(existingProduct?.price?.let { if (it > 0.0) it.toString() else "" } ?: "") }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // DEDUCCIÓN Y PRORRATEO DE COSTOS
    val sharedDivisor = (sharedDivisorStr.toIntOrNull() ?: 1).coerceIn(1, 5)
    val purchasePriceVal = purchasePriceStr.toDoubleOrNull() ?: 0.0
    val purchaseQtyVal = purchaseQtyStr.toDoubleOrNull() ?: 0.0
    val unitsPerLotVal = if (purchaseMode == "POR LOTE") (unitsPerLotStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(1.0) else 1.0

    // Cantidad total de unidades compradas
    val totalUnitsPurchased = if (purchaseMode == "POR LOTE") purchaseQtyVal * unitsPerLotVal else purchaseQtyVal
    val autoInitialStock = maxOf(0.0, totalUnitsPurchased)

    val effectiveUnitsForExpenses = if (totalUnitsPurchased > 0.0) {
        totalUnitsPurchased
    } else if (mercaderia != null && mercaderia.initialStock > 0.0) {
        mercaderia.initialStock
    } else {
        1.0
    }

    // 1. Costo base de adquisición por unidad
    val baseAcquisitionCostPerUnit = when {
        purchaseMode == "POR LOTE" -> if (unitsPerLotVal > 0.0) purchasePriceVal / unitsPerLotVal else 0.0
        else -> if (purchaseQtyVal > 0.0) purchasePriceVal / purchaseQtyVal else purchasePriceVal
    }

    // 2. Gastos de compra asignados al producto
    val totalPurchaseExpenses = purchaseExpensesStr.toDoubleOrNull() ?: 0.0
    val assignedExpensesToProduct = totalPurchaseExpenses / sharedDivisor
    val purchaseExpenseIncidencePerUnit = if (effectiveUnitsForExpenses > 0.0) assignedExpensesToProduct / effectiveUnitsForExpenses else 0.0

    // 3. Prorrateo de Gastos Generales e Inversiones de ElQadre
    val prorrateoResult = CostCalculationHelper.calcularBaseProrrateoGastosGenerales(
        products = uiState.products,
        productosElaborados = uiState.productosElaborados,
        mercaderias = uiState.mercaderias,
        movimientosMercaderia = uiState.movimientosMercaderia,
        gastosGenerales = uiState.gastosGenerales,
        inversiones = uiState.inversiones,
        recetaIngredientes = uiState.recetaIngredientes,
        materiasPrimas = uiState.materiasPrimas
    )

    val activeGastosDiarios = uiState.gastosGenerales.filter { it.isActive }.sumOf { it.dailyCost() }
    val activeInversionesDeprDiaria = uiState.inversiones.sumOf { it.dailyDepreciation() }
    val totalEgresosDiarios = activeGastosDiarios + activeInversionesDeprDiaria

    val directCost = baseAcquisitionCostPerUnit + purchaseExpenseIncidencePerUnit
    val myUnits = if (effectiveUnitsForExpenses > 0.0) effectiveUnitsForExpenses else 1.0
    val myBaseValue = myUnits * directCost

    val baseTotal = if (prorrateoResult.baseTotal > 0.0) prorrateoResult.baseTotal else myBaseValue
    val myShare = if (baseTotal > 0.0) myBaseValue / baseTotal else 1.0
    val allocatedDaily = totalEgresosDiarios * myShare
    val prorratedIndirectCostPerUnit = if (myUnits > 0.0) allocatedDaily / myUnits else 0.0

    // COSTO UNITARIO REAL
    val costoUnitarioReal = baseAcquisitionCostPerUnit + purchaseExpenseIncidencePerUnit + prorratedIndirectCostPerUnit

    // Cálculo sugerido de precio de venta si cambia el margen (Margen sobre precio de venta: Precio = Costo / (1 - Margen/100))
    val marginVal = desiredMarginPercentStr.toDoubleOrNull() ?: 0.0
    val suggestedPrice = if (costoUnitarioReal > 0.0 && marginVal > 0.0 && marginVal < 100.0) {
        costoUnitarioReal / (1.0 - marginVal / 100.0)
    } else if (costoUnitarioReal > 0.0 && marginVal >= 100.0) {
        costoUnitarioReal * 2.0
    } else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(6.dp),
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEdit) "Editar Mercadería" else "Nuevo Producto de Mercadería",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            color = ElQadreNavy,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Formulario de alta amplia y accesible para Mercaderías (BARRA)",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate600)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Nombre
                Text("Nombre del Producto de Mercadería *", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("input_mercaderia_name"),
                    placeholder = { Text("Ej. Cerveza Bucanero Max", color = Slate400) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate300
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Código y Categoría
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Código", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            modifier = Modifier.fillMaxWidth().testTag("input_mercaderia_code"),
                            placeholder = { Text("Ej. BEB06", color = Slate400) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                unfocusedBorderColor = Slate300
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Categoría", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElQadreNavy,
                                    unfocusedBorderColor = Slate300
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                listOf("Bebidas", "Barra", "Cigarros", "Snacks", "Cafetería").forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, fontSize = 14.sp) },
                                        onClick = {
                                            category = cat
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presentación / Unidad de Medida
                Text("Presentación / Unidad de Medida *", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = unitOfMeasure,
                    onValueChange = { unitOfMeasure = it },
                    modifier = Modifier.fillMaxWidth().testTag("input_unit_of_measure"),
                    placeholder = { Text("Ej. lata 355ml, kg, lb, unidad, botella...", color = Slate400) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate300
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("unidades", "latas", "botellas", "gramos", "kilogramos", "libras", "cajas", "sacos").forEach { u ->
                        FilterChip(
                            selected = unitOfMeasure.lowercase() == u,
                            onClick = { unitOfMeasure = u },
                            label = { Text(u, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // SECCIÓN: FORMA DE COMPRA
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Forma de Compra *", fontSize = 13.sp, color = Slate800, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isPorUnidad = purchaseMode == "POR UNIDAD"
                            Button(
                                onClick = { purchaseMode = "POR UNIDAD" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPorUnidad) ElQadreNavy else Slate100,
                                    contentColor = if (isPorUnidad) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Por unidad", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            val isPorLote = purchaseMode == "POR LOTE"
                            Button(
                                onClick = { purchaseMode = "POR LOTE" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPorLote) ElQadreNavy else Slate100,
                                    contentColor = if (isPorLote) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Por lote", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Fila: Precio de compra | Cantidad [| Unidades por lote]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Precio de compra ($)*", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = purchasePriceStr,
                                    onValueChange = { purchasePriceStr = it },
                                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_price"),
                                    placeholder = { Text("0.00", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (purchaseMode == "POR LOTE") "Cantidad de lotes" else "Cantidad",
                                    fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = purchaseQtyStr,
                                    onValueChange = { purchaseQtyStr = it },
                                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_qty"),
                                    placeholder = { Text("0.00", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }

                            if (purchaseMode == "POR LOTE") {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Unidades por lote", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = unitsPerLotStr,
                                        onValueChange = { unitsPerLotStr = it },
                                        modifier = Modifier.fillMaxWidth().testTag("input_units_per_lot"),
                                        placeholder = { Text("0.00", color = Slate400) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElQadreNavy,
                                            unfocusedBorderColor = Slate300
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // SECCIÓN: GASTOS DE LA COMPRA Y COMPARTIDO (1-5)
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Gastos de la Compra", fontSize = 13.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Gastos ($)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = purchaseExpensesStr,
                                    onValueChange = { purchaseExpensesStr = it },
                                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_expenses"),
                                    placeholder = { Text("0.00", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreGoldDark,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }

                            Column(modifier = Modifier.weight(0.8f)) {
                                Text("Compartido (1-5)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = sharedDivisorStr,
                                    onValueChange = { input ->
                                        if (input.isEmpty()) {
                                            sharedDivisorStr = ""
                                        } else {
                                            val v = input.toIntOrNull()
                                            if (v != null) {
                                                sharedDivisorStr = v.coerceIn(1, 5).toString()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("input_shared_divisor"),
                                    placeholder = { Text("1", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreGoldDark,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }
                        }

                        if (totalPurchaseExpenses > 0.0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gasto asignado a esta compra: $${"%.2f".format(assignedExpensesToProduct)} CUP (dividido entre $sharedDivisor). Incidencia por unidad: $${"%.2f".format(purchaseExpenseIncidencePerUnit)} CUP.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // BANDA DE COSTO UNITARIO REAL
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "COSTO UNITARIO REAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${"%.2f".format(costoUnitarioReal)} CUP",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Base compra: $${"%.2f".format(baseAcquisitionCostPerUnit)} + Gasto compra: $${"%.2f".format(purchaseExpenseIncidencePerUnit)} + Indirectos/Inversiones: $${"%.2f".format(prorratedIndirectCostPerUnit)}",
                            fontSize = 11.sp,
                            color = Slate300,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // SECCIÓN: MARGEN DESEADO Y PRECIO SUGERIDO
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Margen deseado (%)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = desiredMarginPercentStr,
                            onValueChange = { 
                                desiredMarginPercentStr = it
                                val m = it.toDoubleOrNull()
                                if (m != null && m > 0.0 && m < 100.0 && costoUnitarioReal > 0.0) {
                                    val sug = costoUnitarioReal / (1.0 - m / 100.0)
                                    definitivePriceStr = "%.2f".format(sug)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("input_desired_margin"),
                            placeholder = { Text("0.00", color = Slate400) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                unfocusedBorderColor = Slate300
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Precio de venta (Sugerido)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate300),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = if (suggestedPrice > 0.0) "$${"%.2f".format(suggestedPrice)} CUP" else "$0.00 CUP",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // SECCIÓN: INVENTARIO INICIAL Y PRECIO FINAL (DEFINITIVO)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Inventario inicial (Auto)", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = "${autoInitialStock.toInt()} unidades",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Precio final (Definitivo) *", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = definitivePriceStr,
                            onValueChange = { 
                                definitivePriceStr = it
                                val price = it.toDoubleOrNull()
                                if (price != null && price > 0.0 && costoUnitarioReal > 0.0) {
                                    val margin = ((price - costoUnitarioReal) / price) * 100.0
                                    desiredMarginPercentStr = "%.1f".format(margin)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("input_definitive_price"),
                            placeholder = { Text("0.00", color = Slate400) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF047857),
                                unfocusedBorderColor = Slate300
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // BOTONES INFERIORES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancelar", fontSize = 14.sp, color = Slate600)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val canSave = name.isNotBlank() &&
                            costoUnitarioReal > 0.0 &&
                            (definitivePriceStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
                            unitOfMeasure.isNotBlank()

                    Button(
                        onClick = {
                            val price = definitivePriceStr.toDoubleOrNull() ?: 0.0

                             if (isEdit && mercaderia != null) {
                                viewModel.updateMercaderiaAndProduct(
                                    mercaderia = mercaderia,
                                    name = name,
                                    code = code,
                                    category = category,
                                    acquisitionCost = baseAcquisitionCostPerUnit,
                                    definitivePrice = price,
                                    unitOfMeasure = unitOfMeasure,
                                    directExpenses = purchaseExpenseIncidencePerUnit,
                                    purchaseMode = purchaseMode,
                                    purchasePrice = purchasePriceVal,
                                    unitsPerLot = unitsPerLotVal
                                )
                            } else {
                                viewModel.createMercaderiaProduct(
                                    name = name,
                                    code = code,
                                    category = category,
                                    acquisitionCost = baseAcquisitionCostPerUnit,
                                    definitivePrice = price,
                                    unitOfMeasure = unitOfMeasure,
                                    initialStock = autoInitialStock,
                                    responsibleAdmin = uiState.currentUser?.username ?: "adminq",
                                    directExpenses = purchaseExpenseIncidencePerUnit,
                                    purchaseMode = purchaseMode,
                                    purchasePrice = purchasePriceVal,
                                    unitsPerLot = unitsPerLotVal
                                )
                            }
                            onDismiss()
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp).testTag("btn_submit_mercaderia")
                    ) {
                        Text("Guardar Producto", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMovementDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    initialMercaderiaId: Long?,
    onDismiss: () -> Unit
) {
    val activeMercaderias = uiState.mercaderias.filter { it.isActive }

    var selectedMercaderiaId by remember {
        mutableStateOf(
            initialMercaderiaId ?: activeMercaderias.firstOrNull()?.id ?: 0L
        )
    }

    var movementType by remember { mutableStateOf("ENTRADA") } // "ENTRADA", "SALIDA", "AJUSTE_POSITIVO", "AJUSTE_NEGATIVO"
    var quantityStr by remember { mutableStateOf("10") }
    var responsibleAdmin by remember { mutableStateOf(uiState.currentUser?.username ?: "adminq") }
    var notes by remember { mutableStateOf("") }

    var mercDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val selectedMerc = uiState.mercaderias.find { it.id == selectedMercaderiaId }
    val selectedProduct = uiState.products.find { it.id == (selectedMerc?.productId ?: 0) }
    val currentStock = if (selectedMerc != null) viewModel.getMercaderiaCurrentStock(selectedMerc.id, selectedMerc.initialStock) else 0.0

    val qty = quantityStr.toDoubleOrNull() ?: 0.0
    val resultingStock = when (movementType) {
        "ENTRADA", "AJUSTE_POSITIVO" -> currentStock + qty
        "SALIDA", "AJUSTE_NEGATIVO" -> maxOf(0.0, currentStock - qty)
        else -> currentStock
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Registrar Movimiento",
                        style = MaterialTheme.typography.titleLarge,
                        color = ElQadreNavy,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate600)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Mercadería Selector
                Text("Producto de Mercadería *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = mercDropdownExpanded,
                    onExpandedChange = { mercDropdownExpanded = !mercDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: "Seleccione un producto...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mercDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = mercDropdownExpanded,
                        onDismissRequest = { mercDropdownExpanded = false }
                    ) {
                        activeMercaderias.forEach { m ->
                            val prod = uiState.products.find { it.id == m.productId }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(prod?.name ?: "Mercadería #${m.id}", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        Text("Presentación: ${m.unitOfMeasure} • Stock: ${viewModel.getMercaderiaCurrentStock(m.id, m.initialStock)} u", fontSize = 11.sp, color = Slate600)
                                    }
                                },
                                onClick = {
                                    selectedMercaderiaId = m.id
                                    mercDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tipo de Movimiento
                Text("Tipo de Movimiento *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = when (movementType) {
                            "ENTRADA" -> "ENTRADA (Abastecimiento / Compra)"
                            "SALIDA" -> "SALIDA (Baja / Merma / Consumo)"
                            "AJUSTE_POSITIVO" -> "AJUSTE POSITIVO (Sobrante)"
                            "AJUSTE_NEGATIVO" -> "AJUSTE NEGATIVO (Faltante)"
                            else -> movementType
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ENTRADA (Abastecimiento / Compra)", color = Emerald600, fontWeight = FontWeight.Bold) },
                            onClick = { movementType = "ENTRADA"; typeDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("SALIDA (Baja / Merma / Consumo)", color = Rose600, fontWeight = FontWeight.Bold) },
                            onClick = { movementType = "SALIDA"; typeDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("AJUSTE POSITIVO (Sobrante)", color = Amber600, fontWeight = FontWeight.Bold) },
                            onClick = { movementType = "AJUSTE_POSITIVO"; typeDropdownExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("AJUSTE NEGATIVO (Faltante)", color = Rose600, fontWeight = FontWeight.Bold) },
                            onClick = { movementType = "AJUSTE_NEGATIVO"; typeDropdownExpanded = false }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cantidad
                Text("Cantidad (${selectedMerc?.unitOfMeasure ?: "unidades"}) *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    modifier = Modifier.fillMaxWidth().testTag("input_movement_quantity"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate300
                    )
                )

                // Stock Preview
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElQadreBackground,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Stock Actual", fontSize = 11.sp, color = Slate600)
                            Text("${"%.0f".format(currentStock)} u", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Slate400)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Stock Resultante", fontSize = 11.sp, color = Slate600)
                            Text(
                                "${"%.0f".format(resultingStock)} u",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (resultingStock > 0.0) Emerald600 else Rose600
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notas
                Text("Motivo / Observaciones", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. Abastecimiento de 2 cajas por factura #4402", color = Slate400) },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate300
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Responsable
                Text("Administrador Responsable", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = responsibleAdmin,
                    onValueChange = { responsibleAdmin = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = Slate300
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Slate600) }
                    Spacer(modifier = Modifier.width(8.dp))

                    val canConfirm = selectedMerc != null && (quantityStr.toDoubleOrNull() ?: 0.0) > 0.0

                    Button(
                        onClick = {
                            val quantity = quantityStr.toDoubleOrNull() ?: 0.0
                            if (selectedMerc != null && quantity > 0.0) {
                                viewModel.registerMercaderiaMovement(
                                    mercaderiaId = selectedMerc.id,
                                    type = movementType,
                                    quantity = quantity,
                                    responsibleAdmin = responsibleAdmin,
                                    notes = notes
                                )
                                onDismiss()
                            }
                        },
                        enabled = canConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Confirmar Movimiento", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun MercaderiaMovementsHistoryDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onRegisterMovement: () -> Unit,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val movements = uiState.movimientosMercaderia.filter { it.mercaderiaId == mercaderia.id }
    val currentStock = viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Historial de Movimientos",
                            style = MaterialTheme.typography.titleLarge,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${product?.name ?: "Mercadería"} • Stock Actual: ${"%.0f".format(currentStock)} u",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate600)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate200)

                if (movements.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay movimientos registrados para este producto.", color = Slate500, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(movements) { mov ->
                            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(mov.date))
                            val isPositive = mov.type == "INVENTARIO_INICIAL" || mov.type == "ENTRADA" || mov.type == "AJUSTE_POSITIVO"

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(mov.type, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isPositive) Emerald700 else Rose700)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(dateStr, fontSize = 10.sp, color = Slate500)
                                        }
                                        if (mov.notes.isNotBlank()) {
                                            Text(mov.notes, fontSize = 11.sp, color = Slate700)
                                        }
                                        Text("Responsable: ${mov.responsibleAdmin}", fontSize = 10.sp, color = Slate500)
                                    }

                                    Text(
                                        text = "${if (isPositive) "+" else "-"}${"%.0f".format(Math.abs(mov.quantity))} u",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (isPositive) Emerald600 else Rose600
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cerrar")
                    }

                    Button(
                        onClick = onRegisterMovement,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo Movimiento", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEgresoMercaderiaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    gasto: GastoGeneral?,
    onDismiss: () -> Unit
) {
    val isEdit = gasto != null
    var name by remember { mutableStateOf(gasto?.name ?: "") }
    var amountStr by remember { mutableStateOf(gasto?.amount?.toString() ?: "") }
    var period by remember { mutableStateOf(gasto?.period ?: "MENSUAL") }
    var category by remember { mutableStateOf(gasto?.category ?: "Servicios") }
    var isPuntual by remember { mutableStateOf(gasto?.targetProductId != null || !gasto?.targetProductIds.isNullOrBlank()) }
    var selectedProductIds by remember { 
        mutableStateOf(
            gasto?.targetProductIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet() 
            ?: (gasto?.targetProductId?.let { setOf(it) } ?: emptySet())
        )
    }
    
    var startDateStr by remember { 
        mutableStateOf(gasto?.startDate?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "") 
    }
    var endDateStr by remember { 
        mutableStateOf(gasto?.endDate?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "") 
    }

    var periodDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var mercDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Editar Egreso (Gasto)" else "Nuevo Egreso (Gasto)",
                        style = MaterialTheme.typography.titleLarge,
                        color = ElQadreNavy,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate600)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Slate200)

                // Concepto
                Text("Concepto del Gasto *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. Electricidad, Transporte de Cervezas, Hielo...", color = Slate400) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Monto y Período
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monto ($ CUP) *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej. 5000", color = Slate400) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recurrencia", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = periodDropdownExpanded,
                            onExpandedChange = { periodDropdownExpanded = !periodDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = period,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = periodDropdownExpanded,
                                onDismissRequest = { periodDropdownExpanded = false }
                            ) {
                                listOf("DÍA", "SEMANA", "SEMANA LABORABLE", "MES", "MES LABORABLE", "AÑO", "AÑO LABORABLE").forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = { period = p; periodDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fechas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fecha Inicial (opcional)", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = startDateStr,
                            onValueChange = { startDateStr = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("DD/MM/YYYY", color = Slate400) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fecha Final (opcional)", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = endDateStr,
                            onValueChange = { endDateStr = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("DD/MM/YYYY", color = Slate400) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tipo de Asignación: General vs Puntual
                Text("Tipo de Asignación a Mercaderías *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isPuntual,
                        onClick = { isPuntual = false; selectedProductIds = emptySet() },
                        label = { Text("A) GENERAL (Prorrateo)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isPuntual,
                        onClick = {
                            isPuntual = true
                            if (selectedProductIds.isEmpty()) {
                                uiState.mercaderias.firstOrNull()?.let { selectedProductIds = setOf(it.productId) }
                            }
                        },
                        label = { Text("B) PUNTUAL (Selectivo)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isPuntual) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Mercaderías Afectadas (${selectedProductIds.size}) *", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(uiState.mercaderias.filter { it.isActive }) { merc ->
                                val prod = uiState.products.find { it.id == merc.productId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            val newSet = selectedProductIds.toMutableSet()
                                            if (newSet.contains(merc.productId)) newSet.remove(merc.productId)
                                            else newSet.add(merc.productId)
                                            selectedProductIds = newSet
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedProductIds.contains(merc.productId),
                                        onCheckedChange = { checked ->
                                            val newSet = selectedProductIds.toMutableSet()
                                            if (checked) newSet.add(merc.productId)
                                            else newSet.remove(merc.productId)
                                            selectedProductIds = newSet
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(prod?.name ?: "Mercadería #${merc.id}", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))

                    val canSave = name.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 && (!isPuntual || selectedProductIds.isNotEmpty())

                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            val targetIdsStr = if (isPuntual) selectedProductIds.joinToString(",") else null
                            val pDays = when (period.uppercase().trim()) {
                                "ÚNICO", "UNICO", "DÍA", "DIA", "DIARIO" -> 1
                                "SEMANA", "SEMANAL" -> 7
                                "SEMANA LABORABLE" -> 6
                                "MES", "MENSUAL" -> 30
                                "MES LABORABLE" -> 26
                                "AÑO", "ANO", "ANUAL" -> 360
                                "AÑO LABORABLE", "ANO LABORABLE" -> 312
                                else -> 26
                            }
                            
                            val targetIdSingle = if (isPuntual && selectedProductIds.size == 1) selectedProductIds.first() else null
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            val sDate = try { sdf.parse(startDateStr)?.time } catch (e: Exception) { null }
                            val eDate = try {
                                sdf.parse(endDateStr)?.let {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.time = it
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                    cal.set(java.util.Calendar.MINUTE, 59)
                                    cal.set(java.util.Calendar.SECOND, 59)
                                    cal.set(java.util.Calendar.MILLISECOND, 999)
                                    cal.timeInMillis
                                }
                            } catch (e: Exception) { null }

                            if (isEdit && gasto != null) {
                                viewModel.updateGastoGeneral(
                                    gasto.copy(
                                        name = name,
                                        amount = amt,
                                        period = period,
                                        periodDays = pDays,
                                        category = category,
                                        targetProductId = targetIdSingle,
                                        targetProductIds = targetIdsStr,
                                        startDate = sDate,
                                        endDate = eDate,
                                        scope = "MERCADERIAS"
                                    )
                                )
                            } else {
                                viewModel.insertGastoGeneral(
                                    GastoGeneral(
                                        name = name,
                                        amount = amt,
                                        period = period,
                                        periodDays = pDays,
                                        category = category,
                                        targetProductId = targetIdSingle,
                                        targetProductIds = targetIdsStr,
                                        startDate = sDate,
                                        endDate = eDate,
                                        scope = "MERCADERIAS"
                                    )
                                )
                            }
                            onDismiss()
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInversionMercaderiaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    inversion: Inversion?,
    onDismiss: () -> Unit
) {
    val isEdit = inversion != null
    var name by remember { mutableStateOf(inversion?.name ?: "") }
    var amountStr by remember { mutableStateOf(inversion?.amount?.toString() ?: "") }
    var usefulLifeStr by remember { mutableStateOf(inversion?.usefulLife?.toInt()?.toString() ?: "12") }
    var usefulLifeUnit by remember { mutableStateOf(inversion?.usefulLifeUnit ?: "MESES") }
    var isPuntual by remember { mutableStateOf(inversion?.targetProductId != null || !inversion?.targetProductIds.isNullOrBlank()) }
    var selectedProductIds by remember { 
        mutableStateOf(
            inversion?.targetProductIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet() 
            ?: (inversion?.targetProductId?.let { setOf(it) } ?: emptySet())
        )
    }
    var observation by remember { mutableStateOf(inversion?.observation ?: "") }
    
    var startDateStr by remember { 
        mutableStateOf(inversion?.startDate?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "") 
    }
    var endDateStr by remember { 
        mutableStateOf(inversion?.endDate?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "") 
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (Fixed Top Bar)
                Surface(
                    color = ElQadreNavy,
                    modifier = Modifier.fillMaxWidth()
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = ElQadreGold.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isEdit) "EDITAR INVERSIÓN" else "NUEVA INVERSIÓN",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "Módulo Mercaderías (Barra)",
                                    fontSize = 12.sp,
                                    color = Slate300
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Concepto
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Concepto del Activo / Inversión *",
                            fontSize = 14.sp,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inversion_name_input"),
                            placeholder = {
                                Text(
                                    "Ej. Vitrina refrigerada, Licuadora para barra...",
                                    color = Slate400,
                                    fontSize = 14.sp
                                )
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = ElQadreNavy
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                unfocusedBorderColor = Slate300,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                    }

                    // Section 2: Monto y Vida útil
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Monto Total ($ CUP) *",
                                fontSize = 14.sp,
                                color = ElQadreNavy,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inversion_amount_input"),
                                placeholder = { Text("Ej. 45000", color = Slate400, fontSize = 14.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElQadreNavy
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElQadreNavy,
                                    unfocusedBorderColor = Slate300,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Vida Útil (meses) *",
                                fontSize = 14.sp,
                                color = ElQadreNavy,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = usefulLifeStr,
                                onValueChange = { usefulLifeStr = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inversion_life_input"),
                                placeholder = { Text("Ej. 12", color = Slate400, fontSize = 14.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElQadreNavy
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElQadreNavy,
                                    unfocusedBorderColor = Slate300,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }
                    }

                    // Live Calculation Preview Card
                    val amtVal = amountStr.toDoubleOrNull() ?: 0.0
                    val lifeMonthsVal = usefulLifeStr.toDoubleOrNull() ?: 0.0
                    if (amtVal > 0.0 && lifeMonthsVal > 0.0) {
                        val monthlyVal = amtVal / lifeMonthsVal
                        val dailyVal = monthlyVal / 30.0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElQadreGoldSoft,
                            border = BorderStroke(1.dp, ElQadreGoldDark.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("DEPRECIACIÓN ESTIMADA", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                                    Text(
                                        "$${"%.2f".format(monthlyVal)} CUP / mes",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("DIARIO DEPRECIACIÓN", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                                    Text(
                                        "$${"%.2f".format(dailyVal)} CUP / día",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreGoldDark
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Fechas de Amortización
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Período de Amortización (Opcional)",
                            fontSize = 14.sp,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Fecha Inicio", fontSize = 12.sp, color = Slate600)
                                OutlinedTextField(
                                    value = startDateStr,
                                    onValueChange = { startDateStr = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("DD/MM/YYYY", color = Slate400, fontSize = 13.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = ElQadreNavy),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Fecha Fin", fontSize = 12.sp, color = Slate600)
                                OutlinedTextField(
                                    value = endDateStr,
                                    onValueChange = { endDateStr = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("DD/MM/YYYY", color = Slate400, fontSize = 13.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = ElQadreNavy),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        unfocusedBorderColor = Slate300
                                    )
                                )
                            }
                        }
                    }

                    // Section 4: Asignación a Mercaderías
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tipo de Asignación a Mercaderías *",
                            fontSize = 14.sp,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = { isPuntual = false; selectedProductIds = emptySet() },
                                shape = RoundedCornerShape(12.dp),
                                color = if (!isPuntual) ElQadreNavy else Slate100,
                                border = BorderStroke(1.5.dp, if (!isPuntual) ElQadreNavy else Slate300),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A) GENERAL\n(Prorrateo)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (!isPuntual) Color.White else Slate700
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    isPuntual = true
                                    if (selectedProductIds.isEmpty()) {
                                        uiState.mercaderias.firstOrNull()?.let { selectedProductIds = setOf(it.productId) }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPuntual) ElQadreNavy else Slate100,
                                border = BorderStroke(1.5.dp, if (isPuntual) ElQadreNavy else Slate300),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 50.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "B) PUNTUAL\n(Selectivo)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isPuntual) Color.White else Slate700
                                    )
                                }
                            }
                        }

                        if (isPuntual) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mercaderías Afectadas (${selectedProductIds.size}) *",
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Slate300),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(uiState.mercaderias.filter { it.isActive }) { merc ->
                                        val prod = uiState.products.find { it.id == merc.productId }
                                        val isChecked = selectedProductIds.contains(merc.productId)
                                        Surface(
                                            onClick = {
                                                val newSet = selectedProductIds.toMutableSet()
                                                if (isChecked) newSet.remove(merc.productId)
                                                else newSet.add(merc.productId)
                                                selectedProductIds = newSet
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isChecked) ElQadreGoldSoft else Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        val newSet = selectedProductIds.toMutableSet()
                                                        if (checked) newSet.add(merc.productId)
                                                        else newSet.remove(merc.productId)
                                                        selectedProductIds = newSet
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = ElQadreNavy
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = prod?.name ?: "Mercadería #${merc.id}",
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                    color = ElQadreNavy
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 5: Observaciones
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Observaciones o Notas (Opcional)",
                            fontSize = 14.sp,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = observation,
                            onValueChange = { observation = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inversion_obs_input"),
                            placeholder = { Text("Ej. Garantía de 1 año con proveedor...", color = Slate400, fontSize = 13.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = ElQadreNavy),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                unfocusedBorderColor = Slate300
                            )
                        )
                    }
                }

                // Fixed Bottom Action Buttons Container (Vertical Stack, Raised, Accessibility Optimized)
                Surface(
                    color = Color.White,
                    shadowElevation = 12.dp,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val canSave = name.isNotBlank() &&
                                (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
                                (usefulLifeStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
                                (!isPuntual || selectedProductIds.isNotEmpty())

                        Button(
                            onClick = {
                                val amt = amountStr.toDoubleOrNull() ?: 0.0
                                val life = usefulLifeStr.toDoubleOrNull() ?: 12.0
                                val targetIdsStr = if (isPuntual) selectedProductIds.joinToString(",") else null
                                val targetIdSingle = if (isPuntual && selectedProductIds.size == 1) selectedProductIds.first() else null
                                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                val sDate = try { sdf.parse(startDateStr)?.time } catch (e: Exception) { null }
                                val eDate = try {
                                    sdf.parse(endDateStr)?.let {
                                        val cal = java.util.Calendar.getInstance()
                                        cal.time = it
                                        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                        cal.set(java.util.Calendar.MINUTE, 59)
                                        cal.set(java.util.Calendar.SECOND, 59)
                                        cal.set(java.util.Calendar.MILLISECOND, 999)
                                        cal.timeInMillis
                                    }
                                } catch (e: Exception) { null }

                                if (isEdit && inversion != null) {
                                    viewModel.updateInversion(
                                        inversion.copy(
                                            name = name,
                                            amount = amt,
                                            usefulLife = life,
                                            usefulLifeUnit = usefulLifeUnit,
                                            targetProductId = targetIdSingle,
                                            targetProductIds = targetIdsStr,
                                            observation = observation,
                                            startDate = sDate,
                                            endDate = eDate,
                                            scope = "MERCADERIAS"
                                        )
                                    )
                                } else {
                                    viewModel.insertInversion(
                                        Inversion(
                                            name = name,
                                            category = "Equipamiento",
                                            amount = amt,
                                            usefulLife = life,
                                            usefulLifeUnit = usefulLifeUnit,
                                            targetProductId = targetIdSingle,
                                            targetProductIds = targetIdsStr,
                                            observation = observation,
                                            startDate = sDate,
                                            endDate = eDate,
                                            scope = "MERCADERIAS"
                                        )
                                    )
                                }
                                onDismiss()
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_save_inversion"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                disabledContainerColor = Slate300,
                                disabledContentColor = Slate500
                            )
                        ) {
                            Text(
                                text = if (isEdit) "ACTUALIZAR INVERSIÓN" else "REGISTRAR INVERSIÓN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_cancel_inversion"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Slate700
                            ),
                            border = BorderStroke(1.5.dp, Slate300)
                        ) {
                            Text(
                                text = "CANCELAR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargarMercaderiasTxtDialog(
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val context = LocalContext.current
    var txtContent by remember { mutableStateOf("") }
    var errorsResult by remember { mutableStateOf<List<String>>(emptyList()) }
    var importedSuccessCount by remember { mutableStateOf<Int?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    txtContent = content
                    errorsResult = emptyList()
                    importedSuccessCount = null
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = ElQadreNavy)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Carga Masiva de Mercaderías (TXT)", fontWeight = FontWeight.Bold, color = ElQadreNavy, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Formato requerido por línea:",
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate700,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Slate300),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Nombre; Código; Categoría; CostoAdquisición; PrecioVenta; UnidadMedida; StockInicial; [Modalidad]; [CantLote]; [GastosDirectos]",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ElQadreNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Ejemplo 1 (POR UNIDAD):\nCerveza Cristal 355ml; BEB-01; Bebidas; 120; 250; lata; 48; POR UNIDAD; 1; 0\n\nEjemplo 2 (POR LOTE - 24 u):\nRefresco Cola Caja 24; BEB-02; Bebidas; 2400; 4800; caja; 5; POR LOTE; 24; 100\n\nEjemplo 3 (POR LOTE - 25 kg):\nSaco Arroz 25kg; INS-01; Snacks; 5000; 7500; saco; 2; POR LOTE; 25; 50",
                            fontSize = 10.sp,
                            color = Slate600
                        )
                    }
                }

                Button(
                    onClick = { filePickerLauncher.launch("text/plain") },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                    modifier = Modifier.fillMaxWidth().testTag("btn_select_txt_file_mercaderias")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = ElQadreNavy)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar archivo TXT", color = ElQadreNavy, fontWeight = FontWeight.Bold)
                }

                Text(
                    "O pegue/edite el contenido TXT aquí:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate700
                )

                OutlinedTextField(
                    value = txtContent,
                    onValueChange = {
                        txtContent = it
                        errorsResult = emptyList()
                        importedSuccessCount = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("input_txt_content_mercaderias"),
                    placeholder = { Text("Cerveza Cristal; BEB-01; Bebidas; 120; 250; lata; 48; POR UNIDAD; 1; 0", color = Slate400, fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                if (importedSuccessCount != null) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "✓ Se importaron exitosamente $importedSuccessCount productos de mercadería.",
                            color = Color(0xFF166534),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (errorsResult.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Errores detectados en la carga:",
                                color = Rose600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            errorsResult.forEach { err ->
                                Text("• $err", color = Rose600, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (txtContent.isBlank()) return@Button
                    isProcessing = true
                    viewModel.importMercaderiasFromTxtContent(txtContent) { count, errs ->
                        isProcessing = false
                        importedSuccessCount = count
                        errorsResult = errs
                        if (errs.isEmpty() && count > 0) {
                            Toast.makeText(context, "Carga completada: $count mercaderías añadidas", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = txtContent.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.testTag("btn_confirm_import_txt_mercaderias")
            ) {
                Text(if (isProcessing) "Procesando..." else "Importar Mercaderías", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Slate600)
            }
        }
    )
}
