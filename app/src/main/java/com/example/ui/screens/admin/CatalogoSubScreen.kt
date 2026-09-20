package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Category
import com.example.data.local.model.Product
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel

import android.widget.Toast
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.licensing.BusinessCodeHelper
import com.example.util.CatalogoDataManager
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun CatalogoSubScreen(uiState: MainUiState, viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Products, 1: Categories
    
    val context = LocalContext.current
    val currentBizCode = remember(uiState.businessConfig, uiState.generalConfig) {
        val raw = BusinessCodeHelper.resolveBusinessCode(context, uiState.businessConfig, uiState.generalConfig)
        BusinessCodeHelper.formatCode(raw)
    }
    val businessName = uiState.businessConfig?.nombreNegocio?.ifBlank { uiState.businessName } ?: uiState.businessName

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Catálogo",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Q_${currentBizCode}catalogo.json",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }
            
            Button(
                onClick = {
                    try {
                        val file = CatalogoDataManager.generateCatalogoJson(
                            context = context,
                            categories = uiState.categories,
                            products = uiState.catalogoProducts,
                            configNegocio = uiState.businessConfig,
                            configGeneral = uiState.generalConfig
                        )
                        CatalogoDataManager.shareCatalogoViaWhatsApp(
                            context = context,
                            jsonFile = file,
                            businessNumber = currentBizCode,
                            businessName = businessName
                        )
                        Toast.makeText(
                            context,
                            "Catálogo Q_${currentBizCode}catalogo.json listo para compartir",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al compartir catálogo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreGold,
                    contentColor = ElQadreNavy
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_compartir_catalogo")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "COMPARTIR CATÁLOGO",
                    color = ElQadreNavy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = ElQadreNavy
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Productos", fontSize = 14.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Categorías", fontSize = 14.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }
        
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (selectedTab == 0) {
                ProductosTab(uiState, viewModel)
            } else {
                CategoriasTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun CategoriasTab(uiState: MainUiState, viewModel: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Category?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Gestión de Categorías", style = MaterialTheme.typography.titleLarge, color = ElQadreNavy, fontWeight = FontWeight.Bold)
            }
            items(uiState.categories) { cat ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cat.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = ElQadreNavy
                                )
                                if (cat.description.isNotBlank()) {
                                    Text(
                                        text = cat.description,
                                        fontSize = 13.sp,
                                        color = Slate600
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (cat.isActive) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                            ) {
                                Text(
                                    text = if (cat.isActive) "ACTIVA" else "INACTIVA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cat.isActive) Color(0xFF065F46) else Color(0xFF991B1B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Slate100)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.updateCategory(cat.copy(isActive = !cat.isActive)) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (cat.isActive) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff,
                                    contentDescription = "Alternar estado",
                                    tint = if (cat.isActive) Emerald600 else Slate400,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { selectedCategory = cat; showDialog = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = ElQadreNavy, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = cat },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = { showDialog = true },
            containerColor = ElQadreGold,
            contentColor = ElQadreNavy,
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Crear Categoría")
        }
    }

    if (showDialog) {
        CategoryDialog(
            category = selectedCategory,
            onDismiss = { showDialog = false; selectedCategory = null },
            onConfirm = { cat ->
                if (selectedCategory == null) viewModel.createCategory(cat) else viewModel.updateCategory(cat)
                showDialog = false
                selectedCategory = null
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Categoría", fontWeight = FontWeight.Bold, color = Rose600) },
            text = { Text("¿Deseas eliminar la categoría ${showDeleteConfirm?.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(showDeleteConfirm!!.id)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = Color.White)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun CategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var description by remember { mutableStateOf(category?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Nueva Categoría" else "Editar Categoría", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            Category(
                                id = category?.id ?: 0,
                                name = name.trim(),
                                description = description.trim(),
                                isActive = category?.isActive ?: true
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosTab(uiState: MainUiState, viewModel: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Product?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("TODAS") }
    var filterType by remember { mutableStateOf("TODOS") }
    var filterState by remember { mutableStateOf("TODOS") }

    val filteredProducts = uiState.catalogoProducts.filter { p ->
        (searchQuery.isEmpty() || p.name.contains(searchQuery, ignoreCase = true) || p.code.contains(searchQuery, ignoreCase = true)) &&
        (filterCategory == "TODAS" || p.category == filterCategory) &&
        (filterType == "TODOS" || p.destination == filterType) &&
        (filterState == "TODOS" || (filterState == "ACTIVO" && p.isAvailable) || (filterState == "INACTIVO" && !p.isAvailable))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Gestión de Productos", style = MaterialTheme.typography.titleLarge, color = ElQadreNavy, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre o código...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
            // Filters
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var expCat by remember { mutableStateOf(false) }
                var expType by remember { mutableStateOf(false) }
                var expState by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expCat,
                    onExpandedChange = { expCat = !expCat },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filterCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría", fontSize = 10.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expCat) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    ExposedDropdownMenu(expanded = expCat, onDismissRequest = { expCat = false }) {
                        DropdownMenuItem(text = { Text("TODAS") }, onClick = { filterCategory = "TODAS"; expCat = false })
                        uiState.categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { filterCategory = c.name; expCat = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expType,
                    onExpandedChange = { expType = !expType },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filterType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo", fontSize = 10.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expType) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    ExposedDropdownMenu(expanded = expType, onDismissRequest = { expType = false }) {
                        listOf("TODOS", "COCINA", "BARRA").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { filterType = t; expType = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expState,
                    onExpandedChange = { expState = !expState },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = filterState,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado", fontSize = 10.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expState) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                    ExposedDropdownMenu(expanded = expState, onDismissRequest = { expState = false }) {
                        listOf("TODOS", "ACTIVO", "INACTIVO").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { filterState = s; expState = false })
                        }
                    }
                }
            }
            
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "El catálogo está vacío.\nLos productos aparecerán automáticamente al agregarlos en Producción (Cocina) o Mercaderías (Barra).",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = Slate600,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProducts) { p ->
                        val isCocina = p.destination == "COCINA"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "Categoría: ${p.category}",
                                            fontSize = 13.sp,
                                            color = Slate600
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ElQadreGold.copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "$${"%.2f".format(p.price)} CUP",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElQadreNavy,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Destination badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCocina) Color(0xFFFEF3C7) else Color(0xFFE0E7FF)
                                    ) {
                                        Text(
                                            text = p.destination,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCocina) Color(0xFF92400E) else Color(0xFF3730A3),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Active state badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (p.isAvailable) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = if (p.isAvailable) "ACTIVO" else "INACTIVO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (p.isAvailable) Color(0xFF065F46) else Color(0xFF991B1B),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Slate100)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCocina) {
                                        Text(
                                            text = "Gestión en Producción",
                                            fontSize = 12.sp,
                                            color = Slate500,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (p.isConvertedToInsumo) "CONVERTIBLE A INSUMO" else "NO CONVERTIBLE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p.isConvertedToInsumo) Color(0xFF0F766E) else Slate500
                                            )
                                            Switch(
                                                checked = p.isConvertedToInsumo,
                                                onCheckedChange = { isChecked ->
                                                    viewModel.updateProduct(p.copy(isConvertedToInsumo = isChecked))
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = ElQadreGold,
                                                    checkedTrackColor = Color(0xFF0F766E),
                                                    uncheckedThumbColor = Slate400,
                                                    uncheckedTrackColor = Slate200
                                                ),
                                                modifier = Modifier.scale(0.85f).testTag("convert_product_${p.id}_switch")
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Gestión en Mercaderías",
                                            fontSize = 12.sp,
                                            color = Slate500,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDialog(
    product: Product?,
    categories: List<Category>,
    existingProducts: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Product) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var destination by remember { mutableStateOf(product?.destination ?: "BARRA") }
    var category by remember { mutableStateOf(product?.category ?: (if (categories.isNotEmpty()) categories[0].name else "")) }
    var code by remember { 
        mutableStateOf(
            product?.code ?: com.example.util.ProductCodeHelper.generateNextProductCode(destination, category, existingProducts)
        ) 
    }
    var priceText by remember { mutableStateOf(product?.price?.toString() ?: "") }
    
    var admitsAgregados by remember { mutableStateOf(product?.admitsAgregados ?: false) }
    val initialAgregados = remember {
        try {
            val jsonArray = JSONArray(product?.agregadosList ?: "[]")
            val list = mutableListOf<Pair<String, Double>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Pair(obj.getString("name"), obj.getDouble("price")))
            }
            list
        } catch (e: Exception) {
            mutableListOf<Pair<String, Double>>()
        }
    }
    val agregadosList = remember { mutableStateListOf<Pair<String, Double>>().apply { addAll(initialAgregados) } }
    var showAddAgregado by remember { mutableStateOf(false) }
    var newAgregadoName by remember { mutableStateOf("") }
    var newAgregadoPrice by remember { mutableStateOf("") }
    
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedDestination by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Nuevo Producto" else "Editar Producto", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Precio") },
                    singleLine = true
                )
                
                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    category = cat.name
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }
                
                // Destination (Tipo) Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedDestination,
                    onExpandedChange = { expandedDestination = !expandedDestination }
                ) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Tipo de Producto") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDestination) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDestination,
                        onDismissRequest = { expandedDestination = false }
                    ) {
                        val options = listOf("BARRA", "COCINA")
                        options.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest) },
                                onClick = {
                                    val previousDest = destination
                                    destination = dest
                                    expandedDestination = false
                                    if (product == null && (code.isBlank() || code.startsWith("PF-C-") || code.startsWith("PF-B-"))) {
                                        code = com.example.util.ProductCodeHelper.generateNextProductCode(dest, category, existingProducts)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = admitsAgregados,
                        onCheckedChange = { admitsAgregados = it }
                    )
                    Text("Admite agregados", fontSize = 14.sp)
                }

                if (admitsAgregados) {
                    Text("Agregados configurados:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    agregadosList.forEachIndexed { index, agregado ->
                        val formattedId = String.format("%02d", index + 1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$formattedId ${agregado.first} ($${agregado.second})", fontSize = 12.sp)
                            IconButton(
                                onClick = { agregadosList.remove(agregado) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (showAddAgregado) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newAgregadoName,
                                onValueChange = { newAgregadoName = it },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                placeholder = { Text("Nombre", fontSize = 12.sp) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newAgregadoPrice,
                                onValueChange = { newAgregadoPrice = it },
                                modifier = Modifier.width(80.dp),
                                placeholder = { Text("Precio", fontSize = 12.sp) },
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    val pr = newAgregadoPrice.toDoubleOrNull()
                                    if (newAgregadoName.isNotBlank() && pr != null) {
                                        agregadosList.add(Pair(newAgregadoName.trim(), pr))
                                        newAgregadoName = ""
                                        newAgregadoPrice = ""
                                        showAddAgregado = false
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Guardar", tint = ElQadreNavy)
                            }
                        }
                    } else {
                        TextButton(onClick = { showAddAgregado = true }) {
                            Text("+ Añadir Agregado", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    val finalCode = code.trim().ifBlank {
                        com.example.util.ProductCodeHelper.generateNextProductCode(destination, category, existingProducts)
                    }
                    if (name.isNotBlank() && finalCode.isNotBlank() && price != null) {
                        val agregadosJsonArray = JSONArray()
                        agregadosList.forEachIndexed { index, it ->
                            val obj = JSONObject()
                            obj.put("id", String.format("%02d", index + 1))
                            obj.put("name", it.first)
                            obj.put("price", it.second)
                            agregadosJsonArray.put(obj)
                        }

                        onConfirm(
                            Product(
                                id = product?.id ?: 0,
                                code = finalCode,
                                name = name.trim(),
                                category = category,
                                price = price,
                                destination = destination,
                                isAvailable = product?.isAvailable ?: true,
                                admitsAgregados = admitsAgregados,
                                agregadosList = agregadosJsonArray.toString()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && priceText.toDoubleOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
