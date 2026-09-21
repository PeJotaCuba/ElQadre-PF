package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.ProductAgregadosDialog
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostSheetPdfExporter
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch

enum class ProductionTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PRODUCTOS("Productos y Recetas", Icons.Outlined.RestaurantMenu),
    MATERIAS("Materias Primas / Insumos", Icons.Outlined.Egg),
    GASTOS_GENERALES("Gastos Generales / Egresos", Icons.Outlined.ReceiptLong),
    TANDAS("Tandas de Producción", Icons.Outlined.History),
    COSTOS("Fichas de Costo y Balances", Icons.Outlined.TrendingUp)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductionWorkspaceDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ProductionTab.PRODUCTOS) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Forms active states
    var showAddMateriaDialog by remember { mutableStateOf(false) }
    var materiaToEdit by remember { mutableStateOf<MateriaPrima?>(null) }

    var showAddProductDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }

    var selectedProductForRecipe by remember { mutableStateOf<Product?>(null) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var ingredientToEdit by remember { mutableStateOf<RecetaIngrediente?>(null) }
    var showProductDetailDialog by remember { mutableStateOf<Product?>(null) }
    var showConvertInsumoDialog by remember { mutableStateOf<Product?>(null) }
    var showAddGastoDialog by remember { mutableStateOf(false) }
    var gastoToEdit by remember { mutableStateOf<com.example.data.local.model.GastoGeneral?>(null) }
    var showFichaCostoDialog by remember { mutableStateOf<com.example.data.local.model.Product?>(null) }
    var showAgregadosDialogForProduct by remember { mutableStateOf<Product?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showCargarTxtDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val exportQProduccionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = com.example.util.AdminJsonExportHelper.buildQProduccionJson(uiState)
            val success = com.example.util.AdminJsonExportHelper.writeJsonToUri(context, uri, json)
            if (success) {
                Toast.makeText(context, "Q_produccion.json exportado correctamente", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al exportar archivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                                        imageVector = Icons.Default.SoupKitchen,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PRODUCCIÓN",
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
                        ProductionTab.values().forEach { tab ->
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
                                    .testTag("drawer_item_prod_${tab.name.lowercase()}")
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
                                    Icon(
                                        imageVector = Icons.Default.SoupKitchen,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "PRODUCCIÓN: ${selectedTab.title.uppercase()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            letterSpacing = 0.5.sp
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
                                    modifier = Modifier.testTag("menu_button_production")
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
                                    modifier = Modifier.testTag("close_production_workspace")
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
                        ProductionTab.PRODUCTOS -> {
                            ProductosYRecetasPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onProductClick = { p -> showProductDetailDialog = p },
                                onAddProductClick = { showAddProductDialog = true },
                                onEditProductClick = { p ->
                                    productToEdit = p
                                    showAddProductDialog = true
                                },
                                onManageRecipeClick = { p -> selectedProductForRecipe = p },
                                onToggleProductActive = { p ->
                                    viewModel.updateProduct(p.copy(isAvailable = !p.isAvailable))
                                },
                                onShowFichaCostoClick = { p -> showFichaCostoDialog = p },
                                onConvertToInsumoClick = { p -> showConvertInsumoDialog = p },
                                onManageAgregadosClick = { p -> showAgregadosDialogForProduct = p },
                                onClearAllClick = { showClearAllDialog = true },
                                onImportTxtClick = { showCargarTxtDialog = true }
                            )
                        }
                        ProductionTab.MATERIAS -> {
                            MateriasPrimasPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onAddMateriaClick = { showAddMateriaDialog = true },
                                onEditMateriaClick = { m ->
                                    materiaToEdit = m
                                    showAddMateriaDialog = true
                                },
                                onToggleMateriaActive = { m ->
                                    viewModel.updateMateriaPrima(m.copy(isActive = !m.isActive))
                                }
                            )
                        }
                        ProductionTab.TANDAS -> {
                            TandasPane(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                        ProductionTab.COSTOS -> {
                            CostosAnalisisPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShowFichaCostoClick = { p -> showFichaCostoDialog = p }
                            )
                        }
                        ProductionTab.GASTOS_GENERALES -> {
                            GastosGeneralesPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                onAddGastoClick = { showAddGastoDialog = true },
                                onEditGastoClick = { g -> 
                                     gastoToEdit = g
                                     showAddGastoDialog = true
                                },
                                onToggleGastoActive = { g -> 
                                     viewModel.updateGastoGeneral(g.copy(isActive = !g.isActive))
                                }
                            )
                        }
                    }

                    // Botón flotante amarillo "+" abajo a la derecha: Agregar insumo / Agregar producto
                    var showFabMenu by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { showFabMenu = true },
                            containerColor = ElQadreGold,
                            contentColor = ElQadreNavy,
                            shape = CircleShape,
                            modifier = Modifier.testTag("fab_add_production")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Agregar",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFabMenu,
                            onDismissRequest = { showFabMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Agregar insumo", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                },
                                onClick = {
                                    showFabMenu = false
                                    materiaToEdit = null
                                    showAddMateriaDialog = true
                                },
                                modifier = Modifier.testTag("menu_item_add_insumo")
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Agregar producto", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                },
                                onClick = {
                                    showFabMenu = false
                                    productToEdit = null
                                    showAddProductDialog = true
                                },
                                modifier = Modifier.testTag("menu_item_add_producto")
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialogs
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("¿Limpiar todos los productos de Producción?", fontWeight = FontWeight.Bold) },
                text = { Text("Esta acción eliminará permanentemente de la base de datos local todos los productos de Producción y sus recetas asociadas. ¿Desea continuar?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllCocinaProducts()
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                        modifier = Modifier.testTag("confirm_clear_all_button")
                    ) {
                        Text("Sí, limpiar todo", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancelar", color = Slate500)
                    }
                }
            )
        }
        if (showCargarTxtDialog) {
            CargarProductosTxtDialog(
                onDismiss = { showCargarTxtDialog = false },
                viewModel = viewModel,
                uiState = uiState
            )
        }
        if (showAddMateriaDialog) {
            AddEditMateriaPrimaDialog(
                materia = materiaToEdit,
                isJornadaOpen = uiState.activeJornada?.isOpen == true,
                uiState = uiState,
                onDismiss = {
                    showAddMateriaDialog = false
                    materiaToEdit = null
                },
                onConfirm = { m, entQty, entUnit, entNotes, salQty, salUnit, salNotes ->
                    val currentUserStr = viewModel.uiState.value.currentUser?.username ?: "Admin"
                    if (m.id == 0L) {
                        viewModel.insertMateriaPrima(m)
                    } else {
                        // Update basic info and potentially initialStock
                        val raw = uiState.materiasPrimas.find { it.id == m.id }
                        if (raw != null) {
                            if (uiState.activeJornada?.isOpen != true) {
                                val oldInitialStock = raw.initialStock
                                val newInitialStock = m.initialStock
                                if (oldInitialStock != newInitialStock) {
                                    val logContent = "Administrador actualizó stock inicial de ${raw.name}: ${"%.2f".format(oldInitialStock)} ${raw.unit} → ${"%.2f".format(newInitialStock)} ${raw.unit}."
                                    viewModel.addBitacoraEntry(
                                        title = "Ajuste Stock Inicial",
                                        content = logContent,
                                        category = "PRODUCCION",
                                        priority = "NORMAL"
                                    )
                                }
                            }
                        }
                        
                        viewModel.updateMateriaPrima(m)
                        
                        // Process Entrada movement
                        if (entQty > 0.0) {
                            val entQtyBase = convertToBaseQty(entQty, entUnit)
                            viewModel.insertMovimientoMateriaPrima(
                                materiaPrimaId = m.id,
                                type = "ENTRADA",
                                quantity = entQtyBase,
                                notes = entNotes.ifEmpty { "Entrada registrada desde editor de materia prima" },
                                responsibleUser = currentUserStr
                            )
                        }
                        
                        // Process Salida movement
                        if (salQty > 0.0) {
                            val salQtyBase = convertToBaseQty(salQty, salUnit)
                            viewModel.insertMovimientoMateriaPrima(
                                materiaPrimaId = m.id,
                                type = "SALIDA",
                                quantity = salQtyBase,
                                notes = salNotes.ifEmpty { "Salida registrada desde editor de materia prima" },
                                responsibleUser = currentUserStr
                            )
                        }
                    }
                    showAddMateriaDialog = false
                    materiaToEdit = null
                }
            )
        }

    if (showAddProductDialog) {
        AddEditProductoElaboradoDialog(
            producto = productToEdit,
            uiState = uiState,
            onDismiss = {
                showAddProductDialog = false
                productToEdit = null
            },
            onConfirm = { p, ppdVal ->
                if (p.id == 0L) {
                    viewModel.createProductElaborado(p, ppdVal)
                } else {
                    viewModel.updateProduct(p)
                    viewModel.updatePpd(p.id, ppdVal)
                }
                showAddProductDialog = false
                productToEdit = null
            }
        )
    }

    selectedProductForRecipe?.let { p ->
        val productDetails = p
        val recipeIngredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == p.id }
        
        RecipeManagementDialog(
            product = p,
            productName = productDetails.name,
            salePrice = productDetails.price,
            ingredients = recipeIngredients,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedProductForRecipe = null },
            onAddIngredientClick = { showAddIngredientDialog = true },
            onEditIngredientClick = { ing -> ingredientToEdit = ing },
            onDeleteIngredient = { ing -> viewModel.deleteRecetaIngrediente(ing) }
        )

        if (showAddIngredientDialog) {
            AddRecipeIngredientDialog(
                productoElaboradoId = p.id,
                uiState = uiState,
                onDismiss = { showAddIngredientDialog = false },
                onConfirm = { ing ->
                    viewModel.insertRecetaIngrediente(ing)
                    showAddIngredientDialog = false
                }
            )
        }

        ingredientToEdit?.let { ing ->
            EditRecipeIngredientDialog(
                ingredient = ing,
                uiState = uiState,
                onDismiss = { ingredientToEdit = null },
                onConfirm = { updated ->
                    viewModel.updateRecetaIngrediente(updated)
                    ingredientToEdit = null
                },
                onDelete = {
                    viewModel.deleteRecetaIngrediente(ing)
                    ingredientToEdit = null
                }
            )
        }
    }

    showProductDetailDialog?.let { p ->
        ProductDetailDialog(
            product = p,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showProductDetailDialog = null },
            onManageRecipeClick = { prod ->
                showProductDetailDialog = null
                selectedProductForRecipe = prod
            },
            onShowFichaCostoClick = { prod ->
                showProductDetailDialog = null
                showFichaCostoDialog = prod
            },
            onEditProductClick = { prod ->
                showProductDetailDialog = null
                productToEdit = prod
                showAddProductDialog = true
            },
            onDeleteProductClick = { prod ->
                showProductDetailDialog = null
                viewModel.deleteProduct(prod.id)
            },
            onManageAgregadosClick = { prod ->
                showProductDetailDialog = null
                showAgregadosDialogForProduct = prod
            },
            onConvertToInsumoClick = { prod ->
                showConvertInsumoDialog = prod
            }
        )
    }

    showConvertInsumoDialog?.let { p ->
        ConvertirProductoInsumoDialog(
            product = p,
            onDismiss = { showConvertInsumoDialog = null },
            onConfirm = {
                viewModel.convertProductToInsumo(p)
                showConvertInsumoDialog = null
                if (showProductDetailDialog?.id == p.id) {
                    showProductDetailDialog = p.copy(isConvertedToInsumo = true)
                }
            }
        )
    }

    if (showAddGastoDialog) {
        AddEditGastoGeneralDialog(
            gasto = gastoToEdit,
            products = uiState.products,
            onDismiss = {
                showAddGastoDialog = false
                gastoToEdit = null
            },
            onConfirm = { g ->
                if (g.id == 0L) {
                    viewModel.insertGastoGeneral(g)
                } else {
                    viewModel.updateGastoGeneral(g)
                }
                showAddGastoDialog = false
                gastoToEdit = null
            }
        )
    }

    showFichaCostoDialog?.let { p ->
        FichaCostoDialog(
            product = p,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showFichaCostoDialog = null }
        )
    }

    showAgregadosDialogForProduct?.let { p ->
        ProductAgregadosDialog(
            product = p,
            onDismiss = { showAgregadosDialogForProduct = null },
            onSaveProduct = { updated ->
                viewModel.updateProduct(updated)
                showAgregadosDialogForProduct = updated
            }
        )
    }
    }
}

// ============================================================
// PANEL 1: PRODUCTOS ELABORADOS Y RECETAS
// ============================================================
@Composable
fun ProductosYRecetasPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onProductClick: (Product) -> Unit,
    onAddProductClick: () -> Unit,
    onEditProductClick: (Product) -> Unit,
    onManageRecipeClick: (Product) -> Unit,
    onToggleProductActive: (Product) -> Unit,
    onShowFichaCostoClick: (com.example.data.local.model.Product) -> Unit,
    onConvertToInsumoClick: (Product) -> Unit = {},
    onManageAgregadosClick: (Product) -> Unit = {},
    onClearAllClick: () -> Unit = {},
    onImportTxtClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Catálogo de Cocina y Recetas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Toque un producto para ver su detalle completo o gestionar su receta",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onImportTxtClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, ElQadreNavy),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_cargar_txt_produccion")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cargar TXT", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onClearAllClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, Rose600),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("clear_all_products_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpiar", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        if (uiState.products.none { it.destination == "COCINA" }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.SoupKitchen,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No hay productos configurados.",
                        color = ElQadreNavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Vincule un artículo de cocina existente o cargue masivamente desde TXT para comenzar.",
                        color = Slate500,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onImportTxtClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, ElQadreNavy),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_cargar_txt_produccion_empty")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cargar desde TXT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.products.filter { it.destination == "COCINA" }) { p ->
                    val catalogItem = p
                    val ingredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == p.id }

                    // Calculate production cost for display
                    val productionCost = ingredients.sumOf { ing ->
                        val materia = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                        val unitCost = materia?.unitCost ?: 0.0
                        ing.quantity * unitCost
                    }

                    val prodElaborado = uiState.productosElaborados.find { it.productId == p.id }
                    val ppdVal = prodElaborado?.effectivePpd ?: 10.0
                    val baseYield = prodElaborado?.baseYield ?: 1.0
                    val unitCostCalculated = if (baseYield > 0.0) productionCost / baseYield else productionCost

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, if (p.isAvailable) ElQadreBorderLight else Slate200),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProductClick(p) }
                            .testTag("product_item_card_${p.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Avatar, Name, Code, Category, Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (p.isAvailable) ElQadreNavy else Slate300),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestaurantMenu,
                                            contentDescription = null,
                                            tint = if (p.isAvailable) ElQadreGold else Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = catalogItem.name,
                                                fontWeight = FontWeight.Black,
                                                color = ElQadreNavy,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (p.isConvertedToInsumo) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFECFDF5),
                                                    border = BorderStroke(1.dp, Emerald600)
                                                ) {
                                                    Text(
                                                        text = "INSUMO",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Emerald600,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val prodCode = catalogItem.code.ifBlank { "PF-C-0001" }
                                            Text(
                                                text = "Código: $prodCode",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "• Categoría: ${catalogItem.category.ifBlank { "Cocina" }}",
                                                fontSize = 12.sp,
                                                color = Slate600
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Switch(
                                        checked = p.isAvailable,
                                        onCheckedChange = { onToggleProductActive(p) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ElQadreGold,
                                            checkedTrackColor = ElQadreNavy
                                        ),
                                        modifier = Modifier.scale(0.95f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Slate200)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Metric indicators: 4 columns
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate50, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("PRECIO VENTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$${"%.2f".format(catalogItem.price)} CUP", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 14.sp)
                                }
                                Column {
                                    Text("PPD (PROMEDIO)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("${if (ppdVal % 1.0 == 0.0) ppdVal.toLong().toString() else ppdVal} ud/día", fontWeight = FontWeight.Bold, color = ElQadreNavy, fontSize = 13.sp)
                                }
                                Column {
                                    Text("COSTO UNIT.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$${"%.2f".format(unitCostCalculated)} CUP", fontWeight = FontWeight.Bold, color = ElQadreGoldDark, fontSize = 13.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("RECETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("${ingredients.size} ingred.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 4 PROMINENT BUTTONS: RECETA | FICHA | EDITAR | ELIMINAR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onManageRecipeClick(p) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(48.dp)
                                        .testTag("open_recipe_${p.id}")
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RECETA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = { onShowFichaCostoClick(p) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(48.dp)
                                        .testTag("open_ficha_costo_${p.id}")
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("FICHA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                OutlinedButton(
                                    onClick = { onEditProductClick(p) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, ElQadreNavy),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("edit_product_${p.id}")
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("EDITAR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.deleteProduct(p.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, Rose600),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(48.dp)
                                        .testTag("delete_product_${p.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("ELIM.", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



// ============================================================
// PANEL 2: MATERIAS PRIMAS
// ============================================================
@Composable
fun MateriasPrimasPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onAddMateriaClick: () -> Unit,
    onEditMateriaClick: (MateriaPrima) -> Unit,
    onToggleMateriaActive: (MateriaPrima) -> Unit
) {
    val isJornadaOpen = uiState.activeJornada?.isOpen == true
    var showDeleteMateriaConfirmDialog by remember { mutableStateOf<MateriaPrima?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Insumos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
                Text(
                    text = "Ingredientes básicos y costos unitarios",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }

            Button(
                onClick = onAddMateriaClick,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("add_materia_prima")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nuevo", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        if (uiState.materiasPrimas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay insumos registrados.",
                    color = Slate500,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.materiasPrimas) { m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = m.name,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreNavy,
                                            fontSize = 16.sp
                                        )
                                        if (m.isAgregado) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                val assocName = uiState.products.find { it.id == m.productId }?.name
                                                Text(
                                                    text = if (assocName != null) "AGREGADO ($assocName)" else "AGREGADO",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Stock actual: ",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Slate600
                                        )
                                        val stockColor = if (m.stock <= 0.0) Rose600 else if (m.stock < 5.0) ElQadreGoldDark else Emerald600
                                        Text(
                                            text = "${"%.2f".format(m.stock)} ${m.unit}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = stockColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isJornadaOpen) "Stock Inicial de Jornada: " else "Stock Inicial: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate500
                                        )
                                        Text(
                                            text = "${"%.2f".format(m.initialStock)} ${m.unit}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate600
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Precio de Compra: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate500
                                        )
                                        Text(
                                            text = "$${"%.2f".format(m.purchasePrice)} CUP / ${m.purchaseUnit}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreGoldDark
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(Costo base: $${"%.4f".format(m.unitCost)} CUP/${m.unit})",
                                            fontSize = 10.sp,
                                            color = Slate400,
                                            style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Button EDITAR
                                    IconButton(
                                        onClick = { onEditMateriaClick(m) },
                                        modifier = Modifier.size(36.dp).testTag("edit_materia_${m.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit, 
                                            contentDescription = "Editar", 
                                            tint = ElQadreNavy, 
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Selector ACTIVA / INACTIVA (Switch)
                                    Switch(
                                        checked = m.isActive,
                                        onCheckedChange = { onToggleMateriaActive(m) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ElQadreGold,
                                            checkedTrackColor = ElQadreNavy
                                        ),
                                        modifier = Modifier.scale(0.8f).testTag("toggle_materia_active_${m.id}")
                                    )

                                    // Button ELIMINAR
                                    IconButton(
                                        onClick = { showDeleteMateriaConfirmDialog = m },
                                        modifier = Modifier.size(36.dp).testTag("delete_materia_${m.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline, 
                                            contentDescription = "Eliminar", 
                                            tint = Rose600, 
                                            modifier = Modifier.size(20.dp)
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

    showDeleteMateriaConfirmDialog?.let { m ->
        val hasDependencies = uiState.recetaIngredientes.any { it.materiaPrimaId == m.id } ||
                              uiState.movimientosMateriaPrima.any { it.materiaPrimaId == m.id }
        
        AlertDialog(
            onDismissRequest = { showDeleteMateriaConfirmDialog = null },
            title = { Text("Confirmar Eliminación", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Está seguro de que desea eliminar la materia prima \"${m.name}\"?")
                    if (hasDependencies) {
                        Surface(
                            color = Rose50,
                            border = BorderStroke(1.dp, Rose100),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Esta materia prima tiene información histórica relacionada (recetas o movimientos). Para preservar la integridad de los datos históricos, se recomienda desactivarla utilizando el selector ACTIVA/INACTIVA en la tarjeta.",
                                fontSize = 12.sp,
                                color = Rose700,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMateriaPrima(m)
                        showDeleteMateriaConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    modifier = Modifier.testTag("confirm_delete_materia")
                ) {
                    Text("Eliminar permanentemente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMateriaConfirmDialog = null }) {
                    Text("Cancelar", color = Slate500)
                }
            }
        )
    }
}


// ============================================================
// PANEL 3: ANÁLISIS DE COSTOS Y MÁRGENES (AUTOMÁTICO)
// ============================================================
@Composable
fun CostosAnalisisPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onShowFichaCostoClick: (Product) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Balances",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )
            Text(
                text = "Cálculo en tiempo real de márgenes de ganancia",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )
        }

        val context = LocalContext.current
        Button(
            onClick = { CostSheetPdfExporter.exportAllCostSheets(context, uiState) },
            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("download_all_cost_sheets_pdf")
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Descargar Todas las Fichas en PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (uiState.products.none { it.destination == "COCINA" }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No hay productos para analizar.",
                    color = Slate600,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // General business summary of elaborate metrics
            var totalPotentialSales = 0.0
            var totalEstCosts = 0.0

            uiState.productosElaborados.forEach { p ->
                val cat = uiState.products.find { it.id == p.productId }
                if (cat != null) {
                    totalPotentialSales += cat.price
                    val ingredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == p.id }
                    val productionCost = ingredients.sumOf { ing ->
                        val materia = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                        val unitCost = materia?.unitCost ?: 0.0
                        ing.quantity * unitCost
                    }
                    totalEstCosts += productionCost
                }
            }

            val totalMargin = totalPotentialSales - totalEstCosts
            val marginPercent = if (totalPotentialSales > 0) (totalMargin / totalPotentialSales) * 100 else 0.0

            Card(
                colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RESUMEN GLOBAL DE MÁRGENES", color = ElQadreGoldSoft, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Venta Estimada", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("$${"%.2f".format(totalPotentialSales)} CUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column {
                            Text("Costo Estimado", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("$${"%.2f".format(totalEstCosts)} CUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Margen Promedio", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("${"%.1f".format(marginPercent)}%", color = ElQadreGold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.products.filter { it.destination == "COCINA" }) { p ->
                    val catalogItem = p
                    val ingredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == p.id }

                    val productionCost = ingredients.sumOf { ing ->
                        val materia = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                        val unitCost = materia?.unitCost ?: 0.0
                        ing.quantity * unitCost
                    }

                    val price = catalogItem?.price ?: 0.0
                    val marginAmount = price - productionCost
                    val percent = if (price > 0) (marginAmount / price) * 100 else 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = catalogItem.name,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (percent >= 50.0) Emerald50 else if (percent >= 25.0) Color(0xFFFFF7ED) else Rose50,
                                    border = BorderStroke(1.dp, if (percent >= 50.0) Emerald600 else if (percent >= 25.0) ElQadreGoldDark else Rose600)
                                ) {
                                    Text(
                                        text = "${"%.1f".format(percent)}% margen",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (percent >= 50.0) Emerald600 else if (percent >= 25.0) ElQadreGoldDark else Rose600,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                            }
                            }

                            HorizontalDivider(color = ElQadreBorderLight)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("COSTO TOTAL", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(productionCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreGoldDark)
                            }
                                Column {
                                    Text("VENTA PÚBLICO", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(price)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("GANANCIA ESTIMADA", fontSize = 10.sp, color = Slate500)
                                    Text("+$${"%.2f".format(marginAmount)} CUP", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = if (marginAmount >= 0) Emerald600 else Rose600)
                            }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onShowFichaCostoClick(p) },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElQadreGold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ver Ficha Completa", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = { CostSheetPdfExporter.exportSingleCostSheet(context, p, uiState) },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, ElQadreGoldDark),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("download_individual_pdf_${p.id}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: AGREGAR/EDITAR MATERIA PRIMA
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMateriaPrimaDialog(
    materia: MateriaPrima?,
    isJornadaOpen: Boolean,
    uiState: MainUiState? = null,
    onDismiss: () -> Unit,
    onConfirm: (MateriaPrima, Double, String, String, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf(materia?.name ?: "") }
    
    // Determine initial base unit category
    var baseUnitCategory by remember { 
        mutableStateOf(
            if (materia != null) getBaseUnit(materia.unit) else "g"
        )
    }
    
    var purchasePriceText by remember { 
        mutableStateOf(
            if (materia != null && materia.purchasePrice > 0.0) materia.purchasePrice.toString() else ""
        )
    }
    
    var purchaseUnit by remember { 
        mutableStateOf(
            if (materia != null) materia.purchaseUnit.ifEmpty { materia.unit } else "g"
        )
    }
    
    var purchaseQtyText by remember {
        mutableStateOf(
            if (materia != null && materia.purchaseQuantity > 0.0) materia.purchaseQuantity.toString() else ""
        )
    }
    
    var numPackagesText by remember { mutableStateOf("1") }
    
    // Purchase Mode & Expenses (Gastos Compartidos) & Linked Product
    var purchaseMode by remember { mutableStateOf("POR UNIDAD") } // "POR UNIDAD", "POR LOTE"
    
    // Dedicated state variables per mode to ensure clear separation and immediate reactive updates
    var unidadQtyText by remember {
        mutableStateOf(
            if (materia != null && materia.purchaseQuantity > 0.0) materia.purchaseQuantity.toString() else ""
        )
    }
    var unidadPriceText by remember {
        mutableStateOf(
            if (materia != null && materia.purchasePrice > 0.0) materia.purchasePrice.toString() else ""
        )
    }
    var loteUnitsText by remember {
        mutableStateOf(
            if (materia != null && materia.purchaseQuantity > 0.0) materia.purchaseQuantity.toString() else ""
        )
    }
    var lotePriceText by remember {
        mutableStateOf(
            if (materia != null && materia.purchasePrice > 0.0) materia.purchasePrice.toString() else ""
        )
    }
    var numLotesText by remember { mutableStateOf("1") }

    var purchaseExpensesStr by remember { mutableStateOf("") }
    var sharedDivisorStr by remember { mutableStateOf("1") }
    var linkedProductId by remember { mutableStateOf<Long?>(materia?.productId) }

    // Movements state (if jornada is open)
    var movementType by remember { mutableStateOf("NINGUNO") } // "NINGUNO", "ENTRADA", "SALIDA"
    var movementQtyText by remember { mutableStateOf("") }
    var movementUnit by remember { 
        mutableStateOf(
            if (materia != null) getBaseUnit(materia.unit) else "g"
        )
    }
    var movementNotes by remember { mutableStateOf("") }

    // Agregados State
    var isAgregado by remember { mutableStateOf(materia?.isAgregado ?: false) }
    var rationUnitStr by remember { mutableStateOf(materia?.rationUnit ?: "") }
    var showRationUnitDropdown by remember { mutableStateOf(false) }
    var showAssocProdDropdown by remember { mutableStateOf(false) }
    var rationQuantityText by remember { 
        mutableStateOf(if (materia != null && materia.rationQuantity > 0.0) materia.rationQuantity.toString() else "")
    }
    var salePriceText by remember {
        mutableStateOf(if (materia != null && materia.salePrice > 0.0) materia.salePrice.toString() else "")
    }
    
    var isActive by remember { mutableStateOf(materia?.isActive ?: true) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val compatibleUnits = getCompatibleUnits(baseUnitCategory)
    
    // Purchase Mode calculations accessible to entire dialog
    val isPorUnidad = purchaseMode == "POR UNIDAD"
    val unidadQtyVal = unidadQtyText.trim().toDoubleOrNull() ?: 0.0
    val unidadPriceVal = unidadPriceText.trim().toDoubleOrNull() ?: 0.0

    val loteUnitsVal = loteUnitsText.trim().toDoubleOrNull() ?: 0.0
    val lotePriceVal = lotePriceText.trim().toDoubleOrNull() ?: 0.0
    val numLotesVal = numLotesText.trim().toDoubleOrNull() ?: 1.0

    val totalPurchaseExpenses = purchaseExpensesStr.trim().toDoubleOrNull() ?: 0.0
    val sharedDivisor = (sharedDivisorStr.toIntOrNull() ?: 1).coerceIn(1, 5)
    val assignedExpensesToProduct = totalPurchaseExpenses / sharedDivisor

    val activeQtyVal = if (isPorUnidad) unidadQtyVal else (loteUnitsVal * numLotesVal)
    val activeInvestment = if (isPorUnidad) (unidadPriceVal * unidadQtyVal) else (lotePriceVal * numLotesVal)
    val activeTotalFinalCost = activeInvestment + assignedExpensesToProduct
    val activeRawUnitCost = if (isPorUnidad) {
        unidadPriceVal
    } else {
        if (loteUnitsVal > 0.0) lotePriceVal / loteUnitsVal else 0.0
    }
    val activeUnitCostPurchased = if (activeQtyVal > 0.0) activeTotalFinalCost / activeQtyVal else activeRawUnitCost
    val activeInitialStockBase = convertToBaseQty(activeQtyVal, purchaseUnit)

    // Ensure purchaseUnit is in compatible list
    LaunchedEffect(baseUnitCategory) {
        val list = getCompatibleUnits(baseUnitCategory)
        if (purchaseUnit !in list) {
            purchaseUnit = list.first()
        }
        if (movementUnit !in list) {
            movementUnit = list.first()
        }
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
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR PANTALLA COMPLETA
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (materia == null) Icons.Default.AddCircle else Icons.Default.Edit,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = if (materia == null) "NUEVO INSUMO DE PRODUCCIÓN" else "EDITAR INSUMO",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 21.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO DEL FORMULARIO SCROLLABLE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // ASOCIAR A PRODUCTO CONVERTIBLE (OPCIONAL)
                    if (materia == null && uiState != null) {
                        val convertibleProducts = uiState.products.filter { it.destination == "COCINA" && it.isConvertedToInsumo }
                        if (convertibleProducts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "VINCULAR A PRODUCTO DE ADMINISTRADOR (OPCIONAL)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                                var showProdDropdown by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showProdDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFF0F766E)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("select_convertible_product_btn")
                                    ) {
                                        val selectedProd = convertibleProducts.find { it.id == linkedProductId }
                                        Text(
                                            text = selectedProd?.let { "✓ Vinculado a: ${it.name}" } ?: "Seleccionar Producto Convertible...",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }

                                    DropdownMenu(
                                        expanded = showProdDropdown,
                                        onDismissRequest = { showProdDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Ninguno / Crear Insumo Independiente", color = Slate500) },
                                            onClick = {
                                                linkedProductId = null
                                                showProdDropdown = false
                                            }
                                        )
                                        convertibleProducts.forEach { prod ->
                                            DropdownMenuItem(
                                                text = { Text(prod.name, fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    linkedProductId = prod.id
                                                    name = prod.name
                                                    // Map unit of measure to nearest baseUnitCategory
                                                    val u = prod.unitOfMeasure.lowercase()
                                                    baseUnitCategory = when {
                                                        u.contains("kg") || u.contains("g") || u.contains("libra") || u.contains("lb") -> "g"
                                                        u.contains("l") || u.contains("ml") -> "ml"
                                                        u.contains("huevo") || u.contains("carton") -> "file"
                                                        else -> "u"
                                                    }
                                                    purchaseUnit = prod.unitOfMeasure
                                                    showProdDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // NOMBRE DEL INSUMO
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "NOMBRE DEL INSUMO (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Ej. Harina de Trigo, Azúcar, Queso, Queso Rallado...", fontSize = 16.sp, color = Slate400) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("materia_name_input")
                        )
                    }

                    // CONFIGURACIÓN DE AGREGADO (PORCIONADO / VENTA DE RACIONES)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AGREGADO PARA VENTA",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF15803D)
                                    )
                                    Text(
                                        text = "¿Este insumo se vende o porciona como agregado?",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                                Switch(
                                    checked = isAgregado,
                                    onCheckedChange = { isAgregado = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF16A34A)
                                    ),
                                    modifier = Modifier.testTag("switch_is_agregado")
                                )
                            }

                            if (isAgregado) {
                                HorizontalDivider(color = Color(0xFFDCFCE7))

                                val compatibleRationUnits = remember(baseUnitCategory) { getCompatibleUnits(baseUnitCategory) }
                                if (rationUnitStr.isBlank() || rationUnitStr !in compatibleRationUnits) {
                                    rationUnitStr = compatibleRationUnits.firstOrNull() ?: baseUnitCategory
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = rationQuantityText,
                                            onValueChange = { rationQuantityText = it },
                                            label = { Text("Cant. por Ración (*)", fontSize = 12.sp) },
                                            placeholder = { Text("Ej. 30", fontSize = 14.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).testTag("input_ration_quantity")
                                        )

                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { showRationUnitDropdown = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Slate300),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800),
                                                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("select_ration_unit_btn")
                                            ) {
                                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                                    Text("Unidad Ración (*)", fontSize = 10.sp, color = Slate500)
                                                    Text(rationUnitStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                                }
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Slate600)
                                            }

                                            DropdownMenu(
                                                expanded = showRationUnitDropdown,
                                                onDismissRequest = { showRationUnitDropdown = false }
                                            ) {
                                                compatibleRationUnits.forEach { unitItem ->
                                                    DropdownMenuItem(
                                                        text = { Text(unitItem, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            rationUnitStr = unitItem
                                                            showRationUnitDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // SELECCIÓN DE PRODUCTO EXISTENTE ASOCIADO
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "PRODUCTO ASOCIADO (CATÁLOGO) (*)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                        val availableProducts = uiState?.products ?: emptyList()
                                        val selectedProd = availableProducts.find { it.id == linkedProductId }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { showAssocProdDropdown = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D)),
                                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("select_associated_product_btn")
                                            ) {
                                                Text(
                                                    text = selectedProd?.let { "✓ Asociado a: ${it.name}" } ?: "Seleccionar Producto Existente...",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }

                                            DropdownMenu(
                                                expanded = showAssocProdDropdown,
                                                onDismissRequest = { showAssocProdDropdown = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Ninguno / Agregado General", color = Slate500) },
                                                    onClick = {
                                                        linkedProductId = null
                                                        showAssocProdDropdown = false
                                                    }
                                                )
                                                availableProducts.forEach { prod ->
                                                    DropdownMenuItem(
                                                        text = { Text("${prod.name} (${prod.code})", fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            linkedProductId = prod.id
                                                            showAssocProdDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val rationQtyVal = rationQuantityText.trim().toDoubleOrNull() ?: 0.0
                                val unitCostCalc = activeUnitCostPurchased
                                val costoRacionCalc = if (rationQtyVal > 0.0) unitCostCalc * rationQtyVal else 0.0
                                val sugeridoCalc = if (costoRacionCalc > 0.0) costoRacionCalc / 0.70 else 0.0
                                val stockInBase = activeInitialStockBase
                                val racionesDisp = if (rationQtyVal > 0.0) stockInBase / rationQtyVal else 0.0

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Costo por ración:", fontSize = 12.sp, color = Slate600)
                                            Text("$${"%.2f".format(costoRacionCalc)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Raciones disponibles en almacén:", fontSize = 12.sp, color = Slate600)
                                            Text("${"%.1f".format(racionesDisp)} raciones", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Precio Sugerido (+30% margen):", fontSize = 12.sp, color = Slate600)
                                            Text("$${"%.2f".format(sugeridoCalc)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = salePriceText,
                                    onValueChange = { salePriceText = it },
                                    label = { Text("Precio de Venta Establecido (Opcional)", fontSize = 12.sp) },
                                    placeholder = { Text("Sugerido: $${"%.2f".format(sugeridoCalc)}", fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_sale_price")
                                )
                            }
                        }
                    }

                    // TIPO DE INSUMO / UNIDAD BASE
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TIPO DE INSUMO / UNIDAD BASE (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )

                        if (materia == null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "g" to "Sólido (Gramos - g / Kilogramos - kg)",
                                    "ml" to "Líquido (Mililitros - ml / Litros - l)",
                                    "u" to "Unidad (Unidades sueltas - u)",
                                    "file" to "Huevo (File - 30 u / Unidad - u)"
                                ).forEach { (code, label) ->
                                    Surface(
                                        onClick = { baseUnitCategory = code },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (baseUnitCategory == code) ElQadreNavy else Color.White,
                                        border = BorderStroke(1.5.dp, if (baseUnitCategory == code) ElQadreNavy else Slate300),
                                        shadowElevation = if (baseUnitCategory == code) 2.dp else 0.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = baseUnitCategory == code,
                                                onClick = { baseUnitCategory = code },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = Color.White,
                                                    unselectedColor = Slate500
                                                )
                                            )
                                            Text(
                                                text = label,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (baseUnitCategory == code) Color.White else Slate800
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Slate100,
                                border = BorderStroke(1.dp, Slate300),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Categoría de unidad fijada: ", fontSize = 15.sp, color = Slate600)
                                    val typeLabel = when (baseUnitCategory) {
                                        "g" -> "Sólido (g)"
                                        "ml" -> "Líquido (ml)"
                                        "u" -> "Unidad (u)"
                                        "file" -> "Huevo (file)"
                                        else -> baseUnitCategory
                                    }
                                    Text(typeLabel, fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }
                            }
                        }
                    }

                    // FORMA DE COMPRA
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TIPO DE COMPRA (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                "POR UNIDAD" to "POR UNIDAD",
                                "POR LOTE" to "POR LOTE"
                            ).forEach { (mode, label) ->
                                Surface(
                                    onClick = { purchaseMode = mode },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (purchaseMode == mode) ElQadreNavy else Color.White,
                                    border = BorderStroke(1.5.dp, if (purchaseMode == mode) ElQadreNavy else Slate300),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (purchaseMode == mode) Color.White else Slate700,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isPorUnidad) "REGISTRO POR UNIDAD (*)" else "REGISTRO POR LOTE (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )

                        if (isPorUnidad) {
                            // POR UNIDAD: CANTIDAD DE UNIDADES, UNIDAD DE MEDIDA, PRECIO POR UNIDAD
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = unidadQtyText,
                                    onValueChange = { 
                                        unidadQtyText = it
                                        showError = false
                                    },
                                    label = { Text("Cantidad Unidades (*)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("Ej. 10", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF0F766E),
                                        focusedLabelColor = Color(0xFF0F766E)
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(64.dp)
                                        .testTag("purchase_qty_input")
                                )

                                // Dropdown Unidad de Compra
                                var expandedUnit by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedUnit = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .testTag("purchase_unit_selector"),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFF0F766E))
                                    ) {
                                        Text(purchaseUnit, color = Color(0xFF0F766E), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(22.dp))
                                    }
                                    DropdownMenu(
                                        expanded = expandedUnit,
                                        onDismissRequest = { expandedUnit = false }
                                    ) {
                                        compatibleUnits.forEach { u ->
                                            DropdownMenuItem(
                                                text = { Text(u, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    purchaseUnit = u
                                                    expandedUnit = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = unidadPriceText,
                                onValueChange = { 
                                    unidadPriceText = it
                                    showError = false
                                },
                                label = { Text("Precio por Unidad ($) (*)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                placeholder = { Text("Ej. 250.00", color = Slate400) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0F766E),
                                    focusedLabelColor = Color(0xFF0F766E)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("purchase_price_input")
                            )
                        } else {
                            // POR LOTE: CANTIDAD DE UNIDADES EN LOTE, UNIDAD DE MEDIDA, PRECIO TOTAL LOTE, CANTIDAD DE LOTES
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = loteUnitsText,
                                    onValueChange = { 
                                        loteUnitsText = it
                                        showError = false
                                    },
                                    label = { Text("Unidades del Lote (*)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("Ej. 25", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF1E3A8A),
                                        focusedLabelColor = Color(0xFF1E3A8A)
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(64.dp)
                                        .testTag("purchase_qty_input")
                                )

                                var expandedUnit by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { expandedUnit = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .testTag("purchase_unit_selector"),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFF1E3A8A))
                                    ) {
                                        Text(purchaseUnit, color = Color(0xFF1E3A8A), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF1E3A8A), modifier = Modifier.size(22.dp))
                                    }
                                    DropdownMenu(
                                        expanded = expandedUnit,
                                        onDismissRequest = { expandedUnit = false }
                                    ) {
                                        compatibleUnits.forEach { u ->
                                            DropdownMenuItem(
                                                text = { Text(u, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    purchaseUnit = u
                                                    expandedUnit = false
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
                                    value = lotePriceText,
                                    onValueChange = { 
                                        lotePriceText = it
                                        showError = false
                                    },
                                    label = { Text("Precio Total del Lote ($) (*)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("Ej. 5000.00", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF1E3A8A),
                                        focusedLabelColor = Color(0xFF1E3A8A)
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(64.dp)
                                        .testTag("purchase_price_input")
                                )

                                OutlinedTextField(
                                    value = numLotesText,
                                    onValueChange = { 
                                        numLotesText = it
                                        showError = false
                                    },
                                    label = { Text("Cant. Lotes", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    placeholder = { Text("1", color = Slate400) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF1E3A8A),
                                        focusedLabelColor = Color(0xFF1E3A8A)
                                    ),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(64.dp)
                                        .testTag("num_packages_input")
                                )
                            }
                        }

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
                            }
                        }

                        // CUADRO DE CÁLCULO EN VIVO
                        if ((isPorUnidad && unidadPriceVal > 0.0 && unidadQtyVal > 0.0) || (!isPorUnidad && lotePriceVal > 0.0 && loteUnitsVal > 0.0)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPorUnidad) Color(0xFFF0FDF4) else Color(0xFFEFF6FF),
                                border = BorderStroke(1.5.dp, if (isPorUnidad) Emerald600 else Color(0xFF3B82F6)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!isPorUnidad) {
                                        Text(
                                            text = "💡 Costo unitario = precio total del lote ÷ unidades del lote",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1E3A8A)
                                        )
                                        Text(
                                            text = "Cálculo: $${"%.2f".format(lotePriceVal)} ÷ ${"%.1f".format(loteUnitsVal)} $purchaseUnit = $${"%.2f".format(activeRawUnitCost)} CUP / $purchaseUnit",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F766E)
                                        )
                                    } else {
                                        Text(
                                            text = "💡 Compra por Unidad: $${"%.2f".format(unidadPriceVal)} CUP / $purchaseUnit",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F766E)
                                        )
                                    }
                                    Text(
                                        text = "Inversión Total: $${"%.2f".format(activeInvestment)} CUP" + (if (assignedExpensesToProduct > 0.0) " + Gasto Asignado: $${"%.2f".format(assignedExpensesToProduct)} CUP" else ""),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                    Text(
                                        text = "Costo unitario final resultante: $${"%.2f".format(activeUnitCostPurchased)} CUP / $purchaseUnit (${"%.4f".format(getNormalizedCost(activeUnitCostPurchased, purchaseUnit))} CUP / $baseUnitCategory)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate700
                                    )
                                }
                            }
                        }

                        // ACUMULADO / EXISTENCIA INICIAL
                        Text(
                            text = "EXISTENCIA INICIAL VINCULADA A LA COMPRA (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Slate100,
                                border = BorderStroke(1.dp, Slate300),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("EXISTENCIA AGREGADA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Slate500)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (activeQtyVal > 0.0) "${"%.1f".format(activeQtyVal)} $purchaseUnit" else "0 $purchaseUnit",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.5.dp, Emerald600),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("EXISTENCIA INICIAL (AUTO)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (activeQtyVal > 0.0) {
                                            if (purchaseUnit.equals(baseUnitCategory, ignoreCase = true)) {
                                                "${"%.1f".format(activeQtyVal)} $purchaseUnit"
                                            } else {
                                                "${"%.1f".format(activeQtyVal)} $purchaseUnit (${"%.1f".format(activeInitialStockBase)} $baseUnitCategory)"
                                            }
                                        } else "0.0 $baseUnitCategory",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Emerald700
                                    )
                                }
                            }
                        }
                    }

                    // MOVIMIENTO DE INVENTARIO DURANTE JORNADA ABIERTA
                    if (isJornadaOpen && materia != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "REGISTRAR MOVIMIENTO DE INVENTARIO",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreNavy
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "NINGUNO" to "Sin cambios",
                                        "ENTRADA" to "Entrada (+)",
                                        "SALIDA" to "Salida (-)"
                                    ).forEach { (type, label) ->
                                        Button(
                                            onClick = { movementType = type },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = when {
                                                    movementType == type && type == "ENTRADA" -> Emerald600
                                                    movementType == type && type == "SALIDA" -> Rose600
                                                    movementType == type && type == "NINGUNO" -> ElQadreNavy
                                                    else -> Slate200
                                                },
                                                contentColor = if (movementType == type) Color.White else Slate700
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                        ) {
                                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                if (movementType != "NINGUNO") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = movementQtyText,
                                            onValueChange = { movementQtyText = it },
                                            label = { Text("Cantidad", fontSize = 14.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                            modifier = Modifier
                                                .weight(1.5f)
                                                .height(64.dp)
                                                .testTag("movement_qty_input")
                                        )

                                        // Movement Unit Dropdown
                                        var expandedMovUnit by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { expandedMovUnit = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(64.dp)
                                                    .testTag("movement_unit_selector"),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(movementUnit, color = ElQadreNavy, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ElQadreNavy)
                                            }
                                            DropdownMenu(
                                                expanded = expandedMovUnit,
                                                onDismissRequest = { expandedMovUnit = false }
                                            ) {
                                                compatibleUnits.forEach { u ->
                                                    DropdownMenuItem(
                                                        text = { Text(u, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            movementUnit = u
                                                            expandedMovUnit = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = movementNotes,
                                        onValueChange = { movementNotes = it },
                                        label = { Text("Notas / Justificación del Movimiento", fontSize = 14.sp) },
                                        placeholder = { Text("Ej. Entrada por compra semanal", fontSize = 14.sp) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp)
                                            .testTag("movement_notes_input")
                                    )
                                }
                            }
                        }
                    }

                    // ESTADO ACTIVO DEL INSUMO
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(24.dp))
                                Text("Insumo Activo (Disponible)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                            Switch(
                                checked = isActive,
                                onCheckedChange = { isActive = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = ElQadreGold, checkedTrackColor = ElQadreNavy),
                                modifier = Modifier
                                    .scale(1.2f)
                                    .testTag("materia_active_switch")
                            )
                        }
                    }

                    if (showError) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFB91C1C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // ACCIONES INFERIORES: CANCELAR Y GUARDAR (COMPLETAMENTE POR ENCIMA DE LA NAVEGACIÓN DE ANDROID)
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Slate300),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate700)
                        }

                        Button(
                            onClick = {
                                val isUnit = purchaseMode == "POR UNIDAD"
                                val qtyToUse = if (isUnit) unidadQtyVal else loteUnitsVal
                                val priceToUse = if (isUnit) unidadPriceVal else lotePriceVal
                                val cantidadLotes = if (isUnit) 1.0 else numLotesVal
                                val totalQuantity = if (isUnit) qtyToUse else (qtyToUse * cantidadLotes)
                                val totalInvestmentCalc = if (isUnit) (priceToUse * qtyToUse) else (priceToUse * cantidadLotes)
                                
                                if (name.isBlank()) {
                                    errorMessage = "El nombre del insumo no puede estar vacío."
                                    showError = true
                                    return@Button
                                }
                                if (priceToUse < 0.0) {
                                    errorMessage = "El precio no puede ser negativo."
                                    showError = true
                                    return@Button
                                }
                                if (qtyToUse <= 0.0 && materia == null) {
                                    errorMessage = if (isUnit) "La cantidad de unidades debe ser mayor a 0." else "Las unidades del lote deben ser mayor a 0."
                                    showError = true
                                    return@Button
                                }
                                if (cantidadLotes <= 0.0 && materia == null) {
                                    errorMessage = "La cantidad de lotes debe ser mayor a 0."
                                    showError = true
                                    return@Button
                                }

                                var entQty = 0.0
                                var entUnitStr = movementUnit
                                var entNotesStr = movementNotes
                                var salQty = 0.0
                                var salUnitStr = movementUnit
                                var salNotesStr = movementNotes

                                if (isJornadaOpen && materia != null && movementType != "NINGUNO") {
                                    val movQty = movementQtyText.trim().toDoubleOrNull() ?: 0.0
                                    if (movQty <= 0.0) {
                                        errorMessage = "La cantidad del movimiento debe ser mayor a 0."
                                        showError = true
                                        return@Button
                                    }
                                    if (movementType == "ENTRADA") {
                                        entQty = movQty
                                        entUnitStr = movementUnit
                                        entNotesStr = movementNotes
                                    } else {
                                        salQty = movQty
                                        salUnitStr = movementUnit
                                        salNotesStr = movementNotes
                                    }
                                }

                                val baseUnit = baseUnitCategory
                                val totalPurchaseExpenses = purchaseExpensesStr.trim().toDoubleOrNull() ?: 0.0
                                val sharedDivisor = (sharedDivisorStr.toIntOrNull() ?: 1).coerceIn(1, 5)
                                val assignedExpensesToProduct = totalPurchaseExpenses / sharedDivisor
                                val totalFinalCostOfPurchase = totalInvestmentCalc + assignedExpensesToProduct
                                val rawUnitCost = if (isUnit) priceToUse else (if (qtyToUse > 0.0) priceToUse / qtyToUse else 0.0)
                                val unitCostPurchased = if (totalQuantity > 0.0) totalFinalCostOfPurchase / totalQuantity else rawUnitCost
                                val costPerBase = if (totalQuantity > 0.0) {
                                    val computed = getNormalizedCost(unitCostPurchased, purchaseUnit)
                                    if (materia != null) maxOf(materia.unitCost, computed) else computed
                                } else {
                                    materia?.unitCost ?: 0.0
                                }
                                val initialStockInBase = convertToBaseQty(totalQuantity, purchaseUnit)

                                val finalInitialStock = if (totalQuantity > 0.0) initialStockInBase else (materia?.initialStock ?: 0.0)
                                val finalStock = if (materia == null) {
                                    initialStockInBase
                                } else if (!isJornadaOpen) {
                                    if (totalQuantity > 0.0) initialStockInBase else materia.stock
                                } else {
                                    materia.stock
                                }

                                val rationQtyVal = rationQuantityText.trim().toDoubleOrNull() ?: 0.0
                                val costoRacionCalc = if (rationQtyVal > 0.0) costPerBase * rationQtyVal else 0.0
                                val sugeridoCalc = if (costoRacionCalc > 0.0) costoRacionCalc / 0.70 else 0.0
                                val manualSalePriceVal = salePriceText.trim().toDoubleOrNull() ?: 0.0

                                val updatedMateria = MateriaPrima(
                                    id = materia?.id ?: 0L,
                                    name = name.trim(),
                                    unit = baseUnit,
                                    unitCost = costPerBase,
                                    isActive = isActive,
                                    stock = finalStock,
                                    initialStock = finalInitialStock,
                                    purchasePrice = priceToUse,
                                    purchaseUnit = purchaseUnit,
                                    purchaseQuantity = if (qtyToUse > 0.0) qtyToUse else (materia?.purchaseQuantity ?: 1.0),
                                    productId = linkedProductId,
                                    isAgregado = isAgregado,
                                    rationQuantity = rationQtyVal,
                                    rationUnit = rationUnitStr.ifBlank { baseUnit },
                                    suggestedPrice = sugeridoCalc,
                                    salePrice = manualSalePriceVal,
                                    stockEnVenta = materia?.stockEnVenta ?: 0.0,
                                    racionesEnVenta = materia?.racionesEnVenta ?: 0.0
                                )

                                onConfirm(
                                    updatedMateria,
                                    entQty,
                                    entUnitStr,
                                    entNotesStr,
                                    salQty,
                                    salUnitStr,
                                    salNotesStr
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(58.dp)
                                .testTag("submit_materia_prima")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("GUARDAR INSUMO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: DETALLE DEL PRODUCTO (ACCESIBLE ADULTO MAYOR)
// ============================================================
@Composable
fun ProductDetailDialog(
    product: Product,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onManageRecipeClick: (Product) -> Unit,
    onShowFichaCostoClick: (Product) -> Unit,
    onEditProductClick: (Product) -> Unit,
    onDeleteProductClick: (Product) -> Unit,
    onManageAgregadosClick: (Product) -> Unit,
    onConvertToInsumoClick: (Product) -> Unit
) {
    val prodElaborado = remember(uiState.productosElaborados, product.id) {
        uiState.productosElaborados.find { it.productId == product.id }
    }
    val ingredients = remember(uiState.recetaIngredientes, product.id) {
        uiState.recetaIngredientes.filter { it.productoElaboradoId == product.id }
    }
    val recipeCost = ingredients.sumOf { ing ->
        val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
        val unitCost = raw?.unitCost ?: 0.0
        ing.quantity * unitCost
    }
    val baseYield = prodElaborado?.baseYield ?: 1.0
    val productionUnit = prodElaborado?.productionUnit ?: product.unitOfMeasure
    val unitCost = if (baseYield > 0.0) recipeCost / baseYield else recipeCost
    val marginAmt = product.price - unitCost
    val marginPct = if (product.price > 0.0) (marginAmt / product.price) * 100.0 else 0.0
    val ppdVal = prodElaborado?.effectivePpd ?: 10.0
    val presentaciones = remember(product.presentacionesEspeciales) {
        parsePresentacionesEspeciales(product.presentacionesEspeciales)
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
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR PANTALLA COMPLETA
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "DETALLE DEL PRODUCTO",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 19.sp
                            )
                            Text(
                                text = product.name,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO SCROLLABLE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // BOTONES DE ACCIÓN PRINCIPALES DESTACADOS: RECETA | FICHA | EDITAR | ELIMINAR
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ACCIONES PRINCIPALES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate500,
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onManageRecipeClick(product) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("detail_btn_receta")
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RECETA", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }

                                Button(
                                    onClick = { onShowFichaCostoClick(product) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("detail_btn_ficha")
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("FICHA", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onEditProductClick(product) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(2.dp, ElQadreNavy),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("detail_btn_editar")
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EDITAR", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }

                                OutlinedButton(
                                    onClick = { onDeleteProductClick(product) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(2.dp, Rose600),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("detail_btn_eliminar")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ELIMINAR", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }

                    // TARJETA 1: IDENTIFICACIÓN Y ESTADO
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DATOS DEL PRODUCTO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (product.isAvailable) Color(0xFFECFDF5) else Color(0xFFFEE2E2),
                                    border = BorderStroke(1.dp, if (product.isAvailable) Emerald600 else Rose600)
                                ) {
                                    Text(
                                        text = if (product.isAvailable) "ACTIVO PARA VENTA" else "INACTIVO",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (product.isAvailable) Emerald600 else Rose600,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CÓDIGO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(product.code.ifBlank { "N/A" }, fontSize = 15.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CATEGORÍA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(product.category.ifBlank { "Cocina" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("UNIDAD DE VENTA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(product.unitOfMeasure.ifBlank { "Unidad" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                }
                            }

                            if (product.description.isNotBlank()) {
                                Column {
                                    Text("DESCRIPCIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(product.description, fontSize = 14.sp, color = Slate700)
                                }
                            }
                        }
                    }

                    // TARJETA 2: COSTOS, RENDIMIENTO Y PRECIO DE VENTA
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "ANÁLISIS DE COSTO Y MARGEN UNITARIO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )

                            HorizontalDivider(color = Slate200)

                            // 2x2 Grid of Metrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("PRECIO VENTA PÚBLICO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                        Text("$${"%.2f".format(product.price)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Por ${product.unitOfMeasure}", fontSize = 10.sp, color = Slate500)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("COSTO TOTAL RECETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                        Text("$${"%.2f".format(recipeCost)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Para ${baseYield} ${productionUnit}", fontSize = 10.sp, color = Slate500)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, ElQadreGold),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("COSTO UNITARIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                        Text("$${"%.2f".format(unitCost)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Costo total ÷ Rendimiento", fontSize = 10.sp, color = Slate600)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (marginPct >= 50.0) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (marginPct >= 50.0) Emerald600 else Slate300),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("MARGEN ESTIMADO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (marginPct >= 50.0) Emerald600 else Slate600)
                                        Text("${"%.1f".format(marginPct)}%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (marginPct >= 50.0) Emerald600 else ElQadreNavy)
                                        Text("$${"%.2f".format(marginAmt)} CUP / ud", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    }
                                }
                            }

                            // Info Banner
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFF93C5FD))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Producción Promedio Diaria (PPD): ${if (ppdVal % 1.0 == 0.0) ppdVal.toLong().toString() else ppdVal} ${product.unitOfMeasure}/día.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }
                        }
                    }

                    // TARJETA 3: PRESENTACIONES ESPECIALES
                    if (presentaciones.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "PRESENTACIONES ESPECIALES",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                HorizontalDivider(color = Slate200)

                                presentaciones.forEach { pres ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Slate50,
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(pres.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                            Text(
                                                "= ${pres.baseEquivalence} ${product.unitOfMeasure}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = ElQadreGoldDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TARJETA 4: INGREDIENTES DE LA RECETA
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "INGREDIENTES (${ingredients.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                TextButton(
                                    onClick = { onManageRecipeClick(product) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("GESTIONAR RECETA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            if (ingredients.isEmpty()) {
                                Text(
                                    "No hay ingredientes en la receta de este producto.",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )
                            } else {
                                ingredients.forEach { ing ->
                                    val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                    val costIng = ing.quantity * (raw?.unitCost ?: 0.0)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Slate50,
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(raw?.name ?: "Ingrediente", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                                Text("Cantidad: ${ing.quantity} ${ing.unit}", fontSize = 12.sp, color = Slate600)
                                            }
                                            Text("$${"%.2f".format(costIng)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreGoldDark)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TARJETA 5: CONVERSIÓN A INSUMO Y AGREGADOS
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "CONVERSIÓN Y AGREGADOS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            HorizontalDivider(color = Slate200)

                            if (product.isConvertedToInsumo) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFECFDF5),
                                    border = BorderStroke(1.5.dp, Emerald600),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(24.dp))
                                        Column {
                                            Text("Convertido en Insumo / Materia Prima", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Emerald600)
                                            Text("Disponible en el almacén de recetas conservando stock e historial.", fontSize = 12.sp, color = Slate600)
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onConvertToInsumoClick(product) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, Emerald600),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald600),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("detail_btn_convertir_insumo")
                                ) {
                                    Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CONVERTIR EN INSUMO (MATERIA PRIMA)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            OutlinedButton(
                                onClick = { onManageAgregadosClick(product) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF0284C7)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("detail_btn_agregados")
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("GESTIONAR AGREGADOS / EXTRAS", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // BOTÓN DE CIERRE
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("CERRAR DETALLE", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: CONFIRMAR CONVERSIÓN DE PRODUCTO A INSUMO
// ============================================================
@Composable
fun ConvertirProductoInsumoDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Transform, contentDescription = null, tint = Emerald600, modifier = Modifier.size(28.dp))
                Text("Convertir a Insumo", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "¿Desea convertir '${product.name}' en un insumo (materia prima)?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ElQadreNavy
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• Se habilitará como ingrediente en las recetas de cocina.", fontSize = 13.sp, color = Slate700)
                        Text("• Conservará su stock actual (${product.stock} ${product.unitOfMeasure}).", fontSize = 13.sp, color = Slate700)
                        Text("• Mantendrá su historial y catálogo sin duplicar inventario.", fontSize = 13.sp, color = Slate700)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("CONVERTIR A INSUMO", fontWeight = FontWeight.Black, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("CANCELAR", fontWeight = FontWeight.Bold, color = Slate600)
            }
        }
    )
}

// ============================================================
// DIÁLOGO: AGREGAR/EDITAR PRODUCTO ELABORADO (VINCULAR CON CATÁLOGO)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductoElaboradoDialog(
    producto: Product?,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onConfirm: (Product, Double) -> Unit
) {
    val existingProdElaborado = remember(producto, uiState.productosElaborados) {
        uiState.productosElaborados.find { it.productId == producto?.id }
    }
    var name by remember { mutableStateOf(producto?.name ?: "") }
    var category by remember { mutableStateOf(producto?.category ?: "Cocina") }
    var unitOfMeasure by remember { mutableStateOf(producto?.unitOfMeasure ?: "Unidad") }
    var ppdText by remember { mutableStateOf((existingProdElaborado?.effectivePpd ?: 0.0).let { if (it > 0.0) (if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()) else "" }) }
    var description by remember { mutableStateOf(producto?.description ?: "") }
    var isAvailable by remember { mutableStateOf(producto?.isAvailable ?: true) }
    
    // Presentaciones Especiales
    var presentacionesList by remember {
        mutableStateOf(parsePresentacionesEspeciales(producto?.presentacionesEspeciales))
    }
    var showAddPresentacionDialog by remember { mutableStateOf(false) }
    var presNameToAdd by remember { mutableStateOf("") }
    var presEquivToAdd by remember { mutableStateOf("1.0") }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR PANTALLA COMPLETA
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (producto == null) Icons.Default.AddCircle else Icons.Default.Edit,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = if (producto == null) "NUEVO PRODUCTO ELABORADO" else "EDITAR PRODUCTO",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO SCROLLABLE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // NOMBRE DEL PRODUCTO
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "NOMBRE DEL PRODUCTO (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Ej. Pizza Napolitana, Hamburguesa Especial...", fontSize = 16.sp, color = Slate400) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("input_product_name")
                        )
                    }

                    // CATEGORÍA Y UNIDAD DE VENTA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "CATEGORÍA (*)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                placeholder = { Text("Ej. Cocina", fontSize = 15.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "UNIDAD DE VENTA (*)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = unitOfMeasure,
                                onValueChange = { unitOfMeasure = it },
                                placeholder = { Text("Ej. Ración, Unidad", fontSize = 15.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                            )
                        }
                    }

                    // PPD (PRODUCCIÓN PROMEDIO DIARIA)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "PRODUCCIÓN PROMEDIO DIARIA (PPD)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = ppdText,
                            onValueChange = { ppdText = it },
                            placeholder = { Text("Ej. 10.00", fontSize = 16.sp, color = Slate400) },
                            supportingText = {
                                Text("Cantidad promedio producida/día para distribuir costos indirectos", fontSize = 12.sp, color = Slate600)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .testTag("input_product_ppd")
                        )
                    }

                    // SECCIÓN: PRESENTACIONES ESPECIALES
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
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
                                Column {
                                    Text(
                                        text = "PRESENTACIONES ESPECIALES (OPCIONAL)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "Equivalencia en unidades base (ej. Familiar = 2.0)",
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                }
                                Button(
                                    onClick = {
                                        presNameToAdd = ""
                                        presEquivToAdd = "1.0"
                                        showAddPresentacionDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AÑADIR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (presentacionesList.isEmpty()) {
                                Text(
                                    "No hay presentaciones especiales configuradas.",
                                    fontSize = 13.sp,
                                    color = Slate400
                                )
                            } else {
                                presentacionesList.forEachIndexed { index, pres ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Slate50,
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(pres.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                                Text("Equivalencia: ${pres.baseEquivalence} ${unitOfMeasure.ifBlank { "unidades" }}", fontSize = 12.sp, color = Slate600)
                                            }
                                            IconButton(
                                                onClick = {
                                                    presentacionesList = presentacionesList.filterIndexed { i, _ -> i != index }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // DESCRIPCIÓN
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DESCRIPCIÓN (OPCIONAL)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Detalles del plato o elaboración...", fontSize = 15.sp, color = Slate400) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ESTADO ACTIVO
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(24.dp))
                                Text("Producto Activo (Visible en Venta)", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = ElQadreNavy)
                            }
                            Switch(
                                checked = isAvailable,
                                onCheckedChange = { isAvailable = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = ElQadreGold, checkedTrackColor = ElQadreNavy),
                                modifier = Modifier.scale(1.2f)
                            )
                        }
                    }

                    if (showError) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage.ifEmpty { "Por favor, complete todos los campos obligatorios." },
                                color = Color(0xFFB91C1C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // ACCIONES INFERIORES: CANCELAR Y GUARDAR
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                        }

                        Button(
                            onClick = {
                                val ppdVal = ppdText.toDoubleOrNull() ?: 10.0
                                if (name.isBlank()) {
                                    errorMessage = "El nombre del producto no puede estar vacío."
                                    showError = true
                                } else if (category.isBlank()) {
                                    errorMessage = "La categoría no puede estar vacía."
                                    showError = true
                                } else if (unitOfMeasure.isBlank()) {
                                    errorMessage = "La unidad de venta no puede estar vacía."
                                    showError = true
                                } else {
                                    val generatedCode = producto?.code ?: com.example.util.ProductCodeHelper.generateNextProductCode("COCINA", category, uiState.products)
                                    onConfirm(
                                        Product(
                                            id = producto?.id ?: 0L,
                                            code = generatedCode,
                                            name = name.trim(),
                                            category = category.trim(),
                                            price = producto?.price ?: 0.0,
                                            cost = producto?.cost ?: 0.0,
                                            destination = "COCINA",
                                            isAvailable = isAvailable,
                                            description = description.trim(),
                                            unitOfMeasure = unitOfMeasure.trim(),
                                            imagePath = producto?.imagePath,
                                            presentacionesEspeciales = serializePresentacionesEspeciales(presentacionesList),
                                            isConvertedToInsumo = producto?.isConvertedToInsumo ?: false
                                        ),
                                        ppdVal
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(58.dp)
                                .testTag("submit_product_elaborado")
                        ) {
                            Text("GUARDAR PRODUCTO", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showAddPresentacionDialog) {
        AlertDialog(
            onDismissRequest = { showAddPresentacionDialog = false },
            title = {
                Text("Nueva Presentación Especial", fontWeight = FontWeight.Bold, color = ElQadreNavy, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = presNameToAdd,
                        onValueChange = { presNameToAdd = it },
                        label = { Text("Nombre (ej. Familiar, Doble)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = presEquivToAdd,
                        onValueChange = { presEquivToAdd = it },
                        label = { Text("Equivalencia en unidades base") },
                        placeholder = { Text("Ej. 2.0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val equiv = presEquivToAdd.toDoubleOrNull() ?: 1.0
                        if (presNameToAdd.isNotBlank() && equiv > 0.0) {
                            presentacionesList = presentacionesList + PresentacionEspecial(name = presNameToAdd.trim(), baseEquivalence = equiv)
                            showAddPresentacionDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("AGREGAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresentacionDialog = false }) {
                    Text("CANCELAR", color = Slate600)
                }
            }
        )
    }
}

// ============================================================
// DIÁLOGO: GESTIÓN DE RECETA (ACCESIBLE Y CÁLCULOS CORREGIDOS)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeManagementDialog(
    product: Product,
    productName: String,
    salePrice: Double,
    ingredients: List<RecetaIngrediente>,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onAddIngredientClick: () -> Unit,
    onEditIngredientClick: (RecetaIngrediente) -> Unit = {},
    onDeleteIngredient: (RecetaIngrediente) -> Unit
) {
    val prodElaborado = remember(uiState.productosElaborados, product.id) {
        uiState.productosElaborados.find { it.productId == product.id }
    }
    
    var recipeName by remember(prodElaborado) { mutableStateOf(prodElaborado?.recipeName ?: "") }
    var productionUnit by remember(prodElaborado) { mutableStateOf(prodElaborado?.productionUnit ?: "unidades") }
    var baseYieldText by remember(prodElaborado) { mutableStateOf(prodElaborado?.baseYield?.toString() ?: "1.0") }
    var baseMateriaPrimaId by remember(prodElaborado) { mutableStateOf(prodElaborado?.baseMateriaPrimaId ?: 0L) }
    var baseQuantityText by remember(prodElaborado) { mutableStateOf(prodElaborado?.baseQuantity?.toString() ?: "0.0") }
    var baseIngredienteExpanded by remember { mutableStateOf(false) }

    val baseYield = baseYieldText.toDoubleOrNull() ?: (prodElaborado?.baseYield ?: 1.0)
    
    // CÁLCULOS CORREGIDOS DE LA RECETA
    // costo total = costo completo de la receta
    val recipeCost = ingredients.sumOf { ing ->
        val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
        val unitCost = raw?.unitCost ?: 0.0
        ing.quantity * unitCost
    }
    // costo unitario = costo total ÷ rendimiento
    val unitCost = if (baseYield > 0.0) recipeCost / baseYield else recipeCost
    // precio de venta = precio por unidad
    // margen estimado = calcular usando costo unitario y precio de venta por unidad
    val marginAmt = salePrice - unitCost
    val marginPct = if (salePrice > 0.0) (marginAmt / salePrice) * 100.0 else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ENCABEZADO SUPERIOR PANTALLA COMPLETA
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "RECETA Y RENDIMIENTO",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 19.sp
                            )
                            Text(
                                text = productName,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO SCROLLABLE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // RESUMEN FINANCIERO Y CÁLCULOS UNITARIOS
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "CÁLCULO DE COSTOS Y MARGEN UNITARIO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate500,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("COSTO TOTAL RECETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                        Text("$${"%.2f".format(recipeCost)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Para ${baseYield} ${productionUnit}", fontSize = 10.sp, color = Slate500)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("RENDIMIENTO BASE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                        Text("${baseYield} ${productionUnit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Unidades producidas", fontSize = 10.sp, color = Slate500)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, ElQadreGold),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("COSTO UNITARIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                        Text("$${"%.2f".format(unitCost)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        Text("Total ÷ Rendimiento", fontSize = 10.sp, color = Slate600)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (marginPct >= 50.0) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (marginPct >= 50.0) Emerald600 else Slate300),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("MARGEN ESTIMADO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (marginPct >= 50.0) Emerald600 else Slate600)
                                        Text("${"%.1f".format(marginPct)}%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (marginPct >= 50.0) Emerald600 else ElQadreNavy)
                                        Text("Venta $${"%.2f".format(salePrice)} CUP", fontSize = 10.sp, color = Slate600)
                                    }
                                }
                            }
                        }
                    }

                    // LISTA DE INGREDIENTES
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
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
                                    text = "INGREDIENTES DE LA RECETA (${ingredients.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Toque para editar",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }

                            HorizontalDivider(color = Slate200)

                            if (ingredients.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate50, RoundedCornerShape(12.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.Egg, contentDescription = null, tint = Slate400, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("La receta aún no tiene ingredientes.", fontWeight = FontWeight.Bold, color = Slate600, fontSize = 14.sp)
                                        Text("Pulse el botón de abajo para añadir insumos.", color = Slate400, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                ingredients.forEach { ing ->
                                    val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                    val totalIngCost = ing.quantity * (raw?.unitCost ?: 0.0)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, Slate200),
                                        color = Slate50,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onEditIngredientClick(ing) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = raw?.name ?: "Ingrediente desconocido",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 15.sp,
                                                        color = ElQadreNavy
                                                    )
                                                    Text(
                                                        text = "Cantidad: ${ing.quantity} ${ing.unit} • Costo: $${"%.2f".format(totalIngCost)} CUP",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Slate600
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { onDeleteIngredient(ing) },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // BOTÓN AGREGAR INGREDIENTE GRANDE Y ACCESIBLE
                            Button(
                                onClick = onAddIngredientClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("add_recipe_ingredient_trigger")
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AGREGAR INGREDIENTE", fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // CONFIGURACIÓN DE LOTE Y PARÁMETROS DE PRODUCCIÓN
                    if (prodElaborado != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "PARÁMETROS DE LOTE / TANDA BASE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                HorizontalDivider(color = Slate200)

                                OutlinedTextField(
                                    value = recipeName,
                                    onValueChange = { recipeName = it },
                                    label = { Text("Nombre de la receta (opcional)", fontSize = 14.sp) },
                                    placeholder = { Text("Ej. Receta Base Masa Napolitana", fontSize = 14.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                    modifier = Modifier.fillMaxWidth().testTag("recipe_name_input")
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = baseYieldText,
                                        onValueChange = { baseYieldText = it },
                                        label = { Text("Rendimiento Base (*)", fontSize = 14.sp) },
                                        placeholder = { Text("Ej. 10.0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier.weight(1f).testTag("recipe_base_yield_input")
                                    )
                                    OutlinedTextField(
                                        value = productionUnit,
                                        onValueChange = { productionUnit = it },
                                        label = { Text("Unidad Producida (*)", fontSize = 14.sp) },
                                        placeholder = { Text("Ej. raciones, pizzas") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier.weight(1f).testTag("recipe_production_unit_input")
                                    )
                                }

                                // Base Ingredient Selection Dropdown
                                val baseMpSelected = uiState.materiasPrimas.find { it.id == baseMateriaPrimaId }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = baseMpSelected?.name ?: "Seleccionar Insumo Base (opcional)...",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Insumo Base de Tanda", fontSize = 14.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = { baseIngredienteExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { baseIngredienteExpanded = true }
                                            .testTag("recipe_base_materia_trigger")
                                    )

                                    DropdownMenu(
                                        expanded = baseIngredienteExpanded,
                                        onDismissRequest = { baseIngredienteExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        if (ingredients.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Agregue ingredientes a la receta primero", fontSize = 13.sp) },
                                                onClick = {}
                                            )
                                        } else {
                                            ingredients.forEach { ing ->
                                                val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                                if (raw != null) {
                                                    DropdownMenuItem(
                                                        text = { Text("${raw.name} (${ing.quantity} ${ing.unit})", fontSize = 14.sp) },
                                                        onClick = {
                                                            baseMateriaPrimaId = raw.id
                                                            baseQuantityText = ing.quantity.toString()
                                                            baseIngredienteExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = baseQuantityText,
                                    onValueChange = { baseQuantityText = it },
                                    label = { Text("Cantidad Base" + (baseMpSelected?.let { " (${it.unit})" } ?: ""), fontSize = 14.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                    modifier = Modifier.fillMaxWidth().testTag("recipe_base_qty_input")
                                )

                                Button(
                                    onClick = {
                                        val yieldVal = baseYieldText.toDoubleOrNull() ?: 1.0
                                        val qtyVal = baseQuantityText.toDoubleOrNull() ?: 0.0
                                        viewModel.updateProductoElaborado(
                                            prodElaborado.copy(
                                                recipeName = recipeName.trim(),
                                                productionUnit = productionUnit.trim(),
                                                baseYield = yieldVal,
                                                baseMateriaPrimaId = baseMateriaPrimaId,
                                                baseQuantity = qtyVal
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_recipe_base_parameters")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("GUARDAR PARÁMETROS DE TANDA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // BOTÓN DE CIERRE
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .navigationBarsPadding()
                        .testTag("close_recipe_dialog")
                ) {
                    Text("CERRAR RECETA", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: EDITAR INGREDIENTE DE LA RECETA (CANTIDAD Y UNIDAD)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeIngredientDialog(
    ingredient: RecetaIngrediente,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onConfirm: (RecetaIngrediente) -> Unit,
    onDelete: () -> Unit
) {
    val raw = remember(ingredient.materiaPrimaId, uiState.materiasPrimas) {
        uiState.materiasPrimas.find { it.id == ingredient.materiaPrimaId }
    }
    val compatibleUnits = remember(raw) {
        raw?.let { getCompatibleUnits(it.unit) } ?: listOf("g")
    }
    var quantityText by remember { mutableStateOf(ingredient.quantity.toString()) }
    var selectedUnit by remember { mutableStateOf(ingredient.unit) }
    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val qtyDouble = quantityText.toDoubleOrNull() ?: 0.0
    val qtyInBase = convertToBaseQty(qtyDouble, selectedUnit)
    val estimatedCost = qtyInBase * (raw?.unitCost ?: 0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = ElQadreNavy, modifier = Modifier.size(28.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("EDITAR INGREDIENTE", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 19.sp)
                            Text(raw?.name ?: "Ingrediente", fontWeight = FontWeight.Bold, color = ElQadreGoldDark, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "MODIFICAR CANTIDAD Y UNIDAD",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            HorizontalDivider(color = Slate200)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("CANTIDAD (*)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                    OutlinedTextField(
                                        value = quantityText,
                                        onValueChange = { quantityText = it },
                                        placeholder = { Text("Ej. 100") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier.fillMaxWidth().height(64.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("UNIDAD (*)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = selectedUnit,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(onClick = { unitDropdownExpanded = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                            modifier = Modifier.fillMaxWidth().height(64.dp).clickable { unitDropdownExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = unitDropdownExpanded,
                                            onDismissRequest = { unitDropdownExpanded = false }
                                        ) {
                                            compatibleUnits.forEach { u ->
                                                DropdownMenuItem(
                                                    text = { Text(u, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        selectedUnit = u
                                                        unitDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Live Cost Preview
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("COSTO ESTIMADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                        Text("Insumo: $${raw?.unitCost ?: 0.0}/${raw?.unit ?: "u"}", fontSize = 12.sp, color = Slate600)
                                    }
                                    Text("$${"%.2f".format(estimatedCost)} CUP", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ElQadreGoldDark)
                                }
                            }

                            if (showError) {
                                Text(
                                    "Por favor escriba una cantidad válida mayor a cero.",
                                    color = Rose600,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Button(
                        onClick = {
                            val qty = quantityText.trim().toDoubleOrNull()
                            if (qty == null || qty <= 0.0 || raw == null) {
                                showError = true
                            } else {
                                val qtyInBaseUnit = convertToBaseQty(qty, selectedUnit)
                                onConfirm(
                                    ingredient.copy(
                                        quantity = qtyInBaseUnit,
                                        unit = raw.unit
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Rose600),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ELIMINAR INGREDIENTE", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: AGREGAR INGREDIENTE A LA RECETA (GRANDE, SENCILLO Y ACCESIBLE)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeIngredientDialog(
    productoElaboradoId: Long,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onConfirm: (RecetaIngrediente) -> Unit
) {
    val activeMaterias = remember(uiState.materiasPrimas) {
        uiState.materiasPrimas.filter { it.isActive }
    }

    var selectedMateriaId by remember { mutableStateOf(0L) }
    var quantityText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val selectedMateria = activeMaterias.find { it.id == selectedMateriaId }
    val compatibleUnits = remember(selectedMateria) {
        selectedMateria?.let { getCompatibleUnits(it.unit) } ?: listOf("g")
    }
    var selectedUnitOfIngredient by remember(selectedMateria) {
        mutableStateOf(selectedMateria?.unit ?: "g")
    }
    var unitDropdownExpanded by remember { mutableStateOf(false) }

    val qtyDouble = quantityText.toDoubleOrNull() ?: 0.0
    val qtyInBase = convertToBaseQty(qtyDouble, selectedUnitOfIngredient)
    val estimatedCost = qtyInBase * (selectedMateria?.unitCost ?: 0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = ElQadreNavy, modifier = Modifier.size(28.dp))
                        }
                        Text("AÑADIR INGREDIENTE", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("SELECCIONAR INSUMO", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            HorizontalDivider(color = Slate200)

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedMateria?.name ?: "Tocar para seleccionar Insumo...",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Insumo / Materia Prima (*)", fontSize = 14.sp) },
                                    trailingIcon = {
                                        IconButton(onClick = { dropdownExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(30.dp))
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clickable { dropdownExpanded = true }
                                        .testTag("select_materia_trigger")
                                )

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    if (activeMaterias.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No hay insumos activos", fontSize = 14.sp) },
                                            onClick = {}
                                        )
                                    } else {
                                        activeMaterias.forEach { raw ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(raw.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text("Costo: $${raw.unitCost}/${raw.unit}", fontSize = 12.sp, color = Slate500)
                                                    }
                                                },
                                                onClick = {
                                                    selectedMateriaId = raw.id
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("CANTIDAD (*)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                    OutlinedTextField(
                                        value = quantityText,
                                        onValueChange = { quantityText = it },
                                        placeholder = { Text("Ej. 100") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                        modifier = Modifier.fillMaxWidth().height(64.dp).testTag("ingredient_qty_input")
                                    )
                                }

                                Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("UNIDAD (*)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = selectedUnitOfIngredient,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = {
                                                IconButton(onClick = { unitDropdownExpanded = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                                            modifier = Modifier.fillMaxWidth().height(64.dp).clickable { unitDropdownExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = unitDropdownExpanded,
                                            onDismissRequest = { unitDropdownExpanded = false }
                                        ) {
                                            compatibleUnits.forEach { u ->
                                                DropdownMenuItem(
                                                    text = { Text(u, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                                    onClick = {
                                                        selectedUnitOfIngredient = u
                                                        unitDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedMateria != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("COSTO ESTIMADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                            Text("Insumo: $${selectedMateria.unitCost}/${selectedMateria.unit}", fontSize = 12.sp, color = Slate600)
                                        }
                                        Text("$${"%.2f".format(estimatedCost)} CUP", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ElQadreGoldDark)
                                    }
                                }
                            }

                            if (showError) {
                                Text(
                                    "Por favor seleccione un ingrediente y escriba una cantidad válida mayor a cero.",
                                    color = Rose600,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(58.dp)
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                    }

                    Button(
                        onClick = {
                            val qty = quantityText.trim().toDoubleOrNull()
                            if (selectedMateriaId == 0L || qty == null || qty <= 0 || selectedMateria == null) {
                                showError = true
                            } else {
                                val qtyInBaseUnit = convertToBaseQty(qty, selectedUnitOfIngredient)
                                onConfirm(
                                    RecetaIngrediente(
                                        productoElaboradoId = productoElaboradoId,
                                        materiaPrimaId = selectedMateriaId,
                                        quantity = qtyInBaseUnit,
                                        unit = selectedMateria.unit
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f).height(58.dp).testTag("submit_recipe_ingredient")
                    ) {
                        Text("AGREGAR", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

// ============================================================
// CONVERSIÓN Y HOMOLOGACIÓN DE UNIDADES DE INVENTARIO
// ============================================================
fun getBaseUnit(unit: String): String {
    return when (unit.lowercase().trim()) {
        "g", "lb", "kg", "oz" -> "g"
        "l", "ml" -> "ml"
        "u" -> "u"
        "file" -> "file"
        else -> "g"
    }
}

fun convertToBaseQty(qty: Double, fromUnit: String): Double {
    return when (fromUnit.lowercase().trim()) {
        "kg" -> qty * 1000.0
        "lb" -> qty * 453.592
        "oz" -> qty * 28.3495
        "l" -> qty * 1000.0
        else -> qty // "g", "ml", "u", "file"
    }
}

fun convertFromBaseQty(baseQty: Double, toUnit: String): Double {
    return when (toUnit.lowercase().trim()) {
        "kg" -> baseQty / 1000.0
        "lb" -> baseQty / 453.592
        "oz" -> baseQty / 28.3495
        "l" -> baseQty / 1000.0
        else -> baseQty
    }
}

fun getNormalizedCost(price: Double, unit: String): Double {
    if (price <= 0.0) return 0.0
    return when (unit.lowercase().trim()) {
        "kg" -> price / 1000.0
        "lb" -> price / 453.592
        "oz" -> price / 28.3495
        "l" -> price / 1000.0
        else -> price // "g", "ml", "u", "file"
    }
}

fun getCompatibleUnits(baseUnit: String): List<String> {
    return when (baseUnit.lowercase().trim()) {
        "g" -> listOf("g", "lb", "kg", "oz")
        "ml" -> listOf("ml", "l")
        "u" -> listOf("u")
        "file" -> listOf("file")
        else -> listOf("g")
    }
}

@Composable
fun CargarProductosTxtDialog(
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
                Text("Carga Masiva desde TXT", fontWeight = FontWeight.Bold, color = ElQadreNavy)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Nombre; Categoría; Unidad; PPD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ElQadreNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ejemplo:\nPizza Napolitana Familiar; Cocina; unidad; 300\nPizza Napolitana Personal; Cocina; unidad; 250\nLomo de Cerdo Asado; Cocina; porción; 2300",
                            fontSize = 10.sp,
                            color = Slate600
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_seleccionar_archivo_txt")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Seleccionar archivo TXT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = txtContent,
                    onValueChange = { 
                        txtContent = it
                        errorsResult = emptyList()
                        importedSuccessCount = null
                    },
                    label = { Text("Contenido del archivo TXT o pegado") },
                    placeholder = { Text("Pizza Napolitana Familiar; Cocina; unidad; 300") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .testTag("input_txt_content"),
                    maxLines = 10
                )

                if (importedSuccessCount != null && importedSuccessCount!! > 0) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "¡$importedSuccessCount producto(s) creado(s) exitosamente!",
                                color = Emerald600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
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
                                "Reporte de Líneas con Error (${errorsResult.size}):",
                                color = Rose600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            errorsResult.forEach { err ->
                                Text("• $err", fontSize = 11.sp, color = Rose600)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (txtContent.isBlank()) {
                        Toast.makeText(context, "Ingrese o seleccione el contenido de un archivo TXT", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isProcessing = true
                    viewModel.importProductsFromTxtContent(txtContent) { count, errs ->
                        isProcessing = false
                        importedSuccessCount = count
                        errorsResult = errs
                        if (errs.isEmpty() && count > 0) {
                            Toast.makeText(context, "Carga masiva completada exitosamente", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !isProcessing && txtContent.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirmar_importar_txt")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Importar Productos", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Slate600)
            }
        }
    )
}

/**
 * Diálogo para activar/desactivar o configurar el modo Agregado de un insumo existente,
 * permitiendo asociar el producto del catálogo, raciones y precio de venta.
 */
@Composable
fun ToggleAgregadoInsumoDialog(
    materiaPrima: MateriaPrima,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (MateriaPrima) -> Unit
) {
    var isAgregado by remember { mutableStateOf(materiaPrima.isAgregado) }
    var selectedProductId by remember { mutableStateOf<Long?>(materiaPrima.productId) }
    val baseUnit = remember(materiaPrima.unit) { getBaseUnit(materiaPrima.unit) }
    val compatibleUnits = remember(baseUnit) { getCompatibleUnits(baseUnit) }
    var rationUnit by remember {
        mutableStateOf(
            if (materiaPrima.rationUnit.isNotBlank() && materiaPrima.rationUnit in compatibleUnits) {
                materiaPrima.rationUnit
            } else {
                compatibleUnits.firstOrNull() ?: baseUnit
            }
        )
    }
    var rationQuantityText by remember {
        mutableStateOf(
            if (materiaPrima.rationQuantity > 0.0) materiaPrima.rationQuantity.toString() else ""
        )
    }
    var salePriceText by remember {
        mutableStateOf(
            if (materiaPrima.salePrice > 0.0) materiaPrima.salePrice.toString() else ""
        )
    }
    var showAssocDropdown by remember { mutableStateOf(false) }
    var showRationUnitDropdown by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedProd = products.find { it.id == selectedProductId }
    val rationQty = rationQuantityText.trim().toDoubleOrNull() ?: 0.0
    val unitCost = materiaPrima.unitCost
    val costoRacion = if (rationQty > 0.0) unitCost * rationQty else 0.0
    val precioSugerido = if (costoRacion > 0.0) costoRacion / 0.70 else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    tint = if (isAgregado) Color(0xFF16A34A) else Slate600
                )
                Text(
                    text = if (materiaPrima.isAgregado) "Configurar / Desactivar Agregado" else "Convertir Insumo en Agregado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Insumo: ${materiaPrima.name}",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Stock actual: ${"%.2f".format(materiaPrima.stock)} ${materiaPrima.unit} • Costo base: $${"%.4f".format(materiaPrima.unitCost)} CUP/${materiaPrima.unit}",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }

                // SWITCH ACTIVAR / DESACTIVAR
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAgregado) Color(0xFFF0FDF4) else Slate100,
                    border = BorderStroke(1.dp, if (isAgregado) Color(0xFF86EFAC) else Slate300),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAgregado) "MODO AGREGADO ACTIVO" else "MODO AGREGADO DESACTIVADO",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (isAgregado) Color(0xFF15803D) else Slate700
                            )
                            Text(
                                text = if (isAgregado) "Este insumo se porciona y vende como agregado/extra." else "Este insumo funciona únicamente como materia prima base.",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                        Switch(
                            checked = isAgregado,
                            onCheckedChange = { isAgregado = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF16A34A)
                            ),
                            modifier = Modifier.testTag("switch_toggle_agregado")
                        )
                    }
                }

                if (isAgregado) {
                    // ASOCIAR A PRODUCTO DEL CATÁLOGO
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "PRODUCTO ASOCIADO DEL CATÁLOGO (*)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showAssocDropdown = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_select_assoc_product")
                            ) {
                                Text(
                                    text = selectedProd?.let { "✓ Asociado a: ${it.name} (${it.code})" } ?: "Seleccionar Producto Existente...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = showAssocDropdown,
                                onDismissRequest = { showAssocDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ninguno / Agregado General (Libre)", color = Slate500) },
                                    onClick = {
                                        selectedProductId = null
                                        showAssocDropdown = false
                                    }
                                )
                                products.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text("${prod.name} (${prod.code})", fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            selectedProductId = prod.id
                                            showAssocDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // CANTIDAD POR RACIÓN Y UNIDAD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rationQuantityText,
                            onValueChange = { rationQuantityText = it },
                            label = { Text("Cant. por Ración *", fontSize = 12.sp) },
                            placeholder = { Text("Ej. 30", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("input_toggle_ration_quantity")
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showRationUnitDropdown = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Slate300),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                    Text("Unidad", fontSize = 10.sp, color = Slate500)
                                    Text(rationUnit, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Slate600)
                            }

                            DropdownMenu(
                                expanded = showRationUnitDropdown,
                                onDismissRequest = { showRationUnitDropdown = false }
                            ) {
                                compatibleUnits.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            rationUnit = u
                                            showRationUnitDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // PRECIO DE VENTA EFECTIVO
                    OutlinedTextField(
                        value = salePriceText,
                        onValueChange = { salePriceText = it },
                        label = { Text("Precio de Venta CUP (Efectivo)", fontSize = 12.sp) },
                        placeholder = { Text(if (precioSugerido > 0.0) "$${"%.2f".format(precioSugerido)}" else "0.0", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_toggle_sale_price")
                    )

                    // RESUMEN FINANCIERO
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo unitario base:", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.4f".format(materiaPrima.unitCost)} CUP/${materiaPrima.unit}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo por ración:", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(costoRacion)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Precio sugerido (+30% margen):", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(precioSugerido)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            }
                        }
                    }
                }

                errorMsg?.let { msg ->
                    Text(msg, color = Rose600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAgregado) {
                        val rq = rationQuantityText.trim().toDoubleOrNull() ?: 0.0
                        if (rq <= 0.0) {
                            errorMsg = "Debe ingresar una cantidad por ración mayor a 0."
                            return@Button
                        }
                        val sp = salePriceText.trim().toDoubleOrNull() ?: (if (precioSugerido > 0.0) precioSugerido else 0.0)
                        val updated = materiaPrima.copy(
                            isAgregado = true,
                            productId = selectedProductId,
                            rationQuantity = rq,
                            rationUnit = rationUnit,
                            suggestedPrice = precioSugerido,
                            salePrice = sp
                        )
                        onSave(updated)
                    } else {
                        val updated = materiaPrima.copy(
                            isAgregado = false,
                            productId = null,
                            salePrice = 0.0
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isAgregado) Color(0xFF16A34A) else ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_save_toggle_agregado")
            ) {
                Text(if (isAgregado) "Guardar como Agregado" else "Guardar como Insumo Base", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate600)
            }
        }
    )
}

