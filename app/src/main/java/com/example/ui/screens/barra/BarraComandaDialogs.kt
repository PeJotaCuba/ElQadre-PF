package com.example.ui.screens.barra

import android.content.Context
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.local.model.OrderItem
import com.example.data.local.model.Product
import com.example.data.local.model.TableOrder
import com.example.ui.theme.*
import com.example.ui.viewmodel.BarraCartItem
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraRegistrarComandaDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onConfirmOrder: (tableNumber: Int?, cartItems: List<BarraCartItem>) -> Unit
) {
    val barProducts = remember(uiState.products) {
        uiState.products.filter { it.destination == "BARRA" && it.isAvailable }
    }

    var selectedCategory by remember { mutableStateOf("TODOS") }
    var searchQuery by remember { mutableStateOf("") }
    var isParaLlevar by remember { mutableStateOf(false) }
    var tableNumberText by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<BarraCartItem>() }
    var customizingProduct by remember { mutableStateOf<Product?>(null) }
    var showConfirmationStep by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(barProducts, selectedCategory, searchQuery) {
        barProducts.filter { prod ->
            val sub = getBarraSubcategory(prod)
            val matchesCategory = when (selectedCategory) {
                "BEBIDAS" -> sub == "BEBIDAS"
                "CONFITERÍAS" -> sub == "CONFITERÍAS"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    prod.name.contains(searchQuery, ignoreCase = true) ||
                    prod.code.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val cartItemsList = cart

    fun getProductQtyInCart(productId: Long): Int {
        return cart.filter { it.product.id == productId }.sumOf { it.quantity }
    }

    fun addProductToCartDirectly(product: Product) {
        val subcategory = getBarraSubcategory(product)
        val existingIndex = cart.indexOfFirst { it.product.id == product.id && it.selectedAgregados.isEmpty() }
        if (existingIndex >= 0) {
            val existing = cart[existingIndex]
            cart[existingIndex] = existing.copy(quantity = existing.quantity + 1)
        } else {
            cart.add(BarraCartItem(product = product, quantity = 1, subcategory = subcategory))
        }
    }

    fun removeProductFromCartDirectly(product: Product) {
        val emptyIndex = cart.indexOfLast { it.product.id == product.id && it.selectedAgregados.isEmpty() }
        if (emptyIndex >= 0) {
            val existing = cart[emptyIndex]
            if (existing.quantity > 1) {
                cart[emptyIndex] = existing.copy(quantity = existing.quantity - 1)
            } else {
                cart.removeAt(emptyIndex)
            }
        } else {
            val index = cart.indexOfLast { it.product.id == product.id }
            if (index >= 0) {
                val existing = cart[index]
                if (existing.quantity > 1) {
                    cart[index] = existing.copy(quantity = existing.quantity - 1)
                } else {
                    cart.removeAt(index)
                }
            }
        }
    }

    val totalAmount = remember(cartItemsList.map { it.totalAmount }) {
        cartItemsList.sumOf { it.totalAmount }
    }
    val totalBebidasAmount = remember(cartItemsList.map { it.totalAmount }) {
        cartItemsList.filter { it.subcategory == "BEBIDAS" }.sumOf { it.totalAmount }
    }
    val totalConfiteriasAmount = remember(cartItemsList.map { it.totalAmount }) {
        cartItemsList.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.totalAmount }
    }
    val totalUnits = remember(cartItemsList.map { it.quantity }) {
        cartItemsList.sumOf { it.quantity }
    }

    val parsedTableNumber = if (isParaLlevar) null else (tableNumberText.toIntOrNull() ?: 0)

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
            if (showConfirmationStep) {
                // Summary & Final Confirmation Step
                BarraComandaConfirmationStep(
                    tableNumber = parsedTableNumber,
                    cartItems = cartItemsList,
                    totalAmount = totalAmount,
                    totalBebidasAmount = totalBebidasAmount,
                    totalConfiteriasAmount = totalConfiteriasAmount,
                    onBack = { showConfirmationStep = false },
                    onConfirm = {
                        onConfirmOrder(parsedTableNumber, cartItemsList)
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Top Header
                    Surface(color = ElQadreNavy, shadowElevation = 4.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Registrar Comanda de Barra",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Selecciona productos y asigna mesa",
                                    color = ElQadreGold,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }
                    }

                    // 2. Main Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Table Selection / Para Llevar Selector Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, if (isParaLlevar) Color(0xFFFCD34D) else if ((parsedTableNumber ?: 0) > 0) Emerald200 else Slate200),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Selector: Servicio en Mesa vs Para Llevar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { 
                                                isParaLlevar = false 
                                                validationError = null
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp).testTag("tab_comanda_mesa"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (!isParaLlevar) ElQadreNavy else Color.White,
                                                contentColor = if (!isParaLlevar) Color.White else Slate600
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (!isParaLlevar) ElQadreNavy else Slate200),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Icon(Icons.Outlined.TableRestaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Mesa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { 
                                                isParaLlevar = true 
                                                tableNumberText = ""
                                                validationError = null
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp).testTag("tab_comanda_llevar"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isParaLlevar) Amber500 else Color.White,
                                                contentColor = if (isParaLlevar) Color.White else Slate600
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (isParaLlevar) Amber500 else Slate200),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Para Llevar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (!isParaLlevar) {
                                        HorizontalDivider(color = Slate100, thickness = 1.dp)

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.TableRestaurant,
                                                    contentDescription = null,
                                                    tint = ElQadreNavy,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    "Número de Mesa (Obligatorio):",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = ElQadreNavy
                                                )
                                            }
                                            if ((parsedTableNumber ?: 0) > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Emerald50
                                                ) {
                                                    Text(
                                                        "MESA $parsedTableNumber",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp,
                                                        color = Emerald800,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = tableNumberText,
                                            onValueChange = {
                                                tableNumberText = it.filter { ch -> ch.isDigit() }
                                                validationError = null
                                            },
                                            placeholder = { Text("Ej. 1, 2, 3...", fontSize = 12.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("input_mesa_comanda_barra"),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            )
                                        )

                                        // Quick table selectors
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1, 2, 3, 4, 5, 6).forEach { num ->
                                                OutlinedButton(
                                                    onClick = {
                                                        tableNumberText = num.toString()
                                                        validationError = null
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp),
                                                    border = BorderStroke(1.dp, if (parsedTableNumber == num) ElQadreNavy else Slate200),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = if (parsedTableNumber == num) ElQadreNavy else Color.White,
                                                        contentColor = if (parsedTableNumber == num) Color.White else Slate700
                                                    )
                                                ) {
                                                    Text("M$num", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    } else {
                                        // Takeaway feedback
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Amber50,
                                            border = BorderStroke(1.dp, Amber100),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.ShoppingBag,
                                                    contentDescription = null,
                                                    tint = Amber800,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text(
                                                        "Comanda identificada como PARA LLEVAR",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Amber800
                                                    )
                                                    Text(
                                                        "No se asociará a ninguna mesa.",
                                                        fontSize = 11.sp,
                                                        color = Amber800
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search & Category Filters
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Category Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedCategory == "TODOS",
                                        onClick = { selectedCategory = "TODOS" },
                                        label = { Text("TODOS", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElQadreNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = selectedCategory == "BEBIDAS",
                                        onClick = { selectedCategory = "BEBIDAS" },
                                        label = { Text("BEBIDAS", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF1E3A8A),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = selectedCategory == "CONFITERÍAS",
                                        onClick = { selectedCategory = "CONFITERÍAS" },
                                        label = { Text("CONFITERÍAS", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Amber700,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }

                                // Search bar
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Buscar bebidas o confiterías...", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Slate400) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Limpiar", tint = Slate400)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("input_buscar_comanda_barra"),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    )
                                )
                            }
                        }

                        // Product Selection Cards
                        if (filteredProducts.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = Slate400)
                                        Text("No se encontraron productos de barra disponibles", fontSize = 12.sp, color = Slate600)
                                    }
                                }
                            }
                        } else {
                            items(filteredProducts, key = { it.id }) { prod ->
                                val qty = getProductQtyInCart(prod.id)
                                val subcategory = getBarraSubcategory(prod)
                                val isBebida = subcategory == "BEBIDAS"
                                val configuredAgregadosItems = cart.filter { it.product.id == prod.id && it.selectedAgregados.isNotEmpty() }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (qty > 0) Color(0xFFF8FAFC) else Color.White,
                                    border = BorderStroke(
                                        if (qty > 0) 1.5.dp else 1.dp,
                                        if (qty > 0) ElQadreNavy else Slate200
                                    ),
                                    shadowElevation = if (qty > 0) 2.dp else 0.5.dp,
                                    modifier = Modifier.fillMaxWidth().testTag("item_comanda_${prod.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prod.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = ElQadreNavy
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isBebida) Color(0xFFDBEAFE) else Amber100
                                                ) {
                                                    Text(
                                                        subcategory,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isBebida) Color(0xFF1E40AF) else Amber800,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(
                                                    "Disp: ${prod.stock} ud.",
                                                    fontSize = 10.sp,
                                                    color = if (prod.stock <= prod.minStock) Rose600 else Slate500,
                                                    fontWeight = if (prod.stock <= prod.minStock) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            Text(
                                                "$${"%.2f".format(prod.price)} CUP c/u",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Emerald700
                                            )

                                            if (isBebida) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = { customizingProduct = prod },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = Amber800),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Personalizar / Agregados", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (configuredAgregadosItems.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    configuredAgregadosItems.forEach { cItem ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "• ${cItem.quantity}x con ${cItem.selectedAgregados.joinToString(", ") { it.first }} (+$${"%.2f".format(cItem.selectedAgregados.sumOf { it.second })} CUP c/u)",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFFD97706),
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            IconButton(
                                                                onClick = { cart.remove(cItem) },
                                                                modifier = Modifier.size(16.dp)
                                                            ) {
                                                                Icon(Icons.Filled.Close, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(10.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Quantity Controls
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (qty > 0) {
                                                IconButton(
                                                    onClick = {
                                                        removeProductFromCartDirectly(prod)
                                                    },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Slate100, CircleShape)
                                                ) {
                                                    Icon(Icons.Filled.Remove, contentDescription = "Menos", tint = Slate700, modifier = Modifier.size(16.dp))
                                                }

                                                Text(
                                                    text = "$qty",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp,
                                                    color = ElQadreNavy,
                                                    modifier = Modifier.widthIn(min = 20.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    addProductToCartDirectly(prod)
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(if (qty > 0) ElQadreNavy else Emerald600, CircleShape)
                                            ) {
                                                Icon(Icons.Filled.Add, contentDescription = "Más", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Bottom Summary & Review Bar
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 72.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "$totalUnits productos seleccionados",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                    Text(
                                        "Mesa: ${if (isParaLlevar) "Para Llevar" else if ((parsedTableNumber ?: 0) > 0) "Mesa #$parsedTableNumber" else "Sin asignar"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isParaLlevar) Amber800 else if ((parsedTableNumber ?: 0) > 0) Emerald700 else Rose600
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "TOTAL COMANDA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate400
                                    )
                                    Text(
                                        "$${"%.2f".format(totalAmount)} CUP",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                }
                            }

                            validationError?.let { err ->
                                Text(
                                    text = err,
                                    color = Rose600,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isParaLlevar && (parsedTableNumber ?: 0) <= 0) {
                                        validationError = "Debe indicar un número de mesa válido"
                                        return@Button
                                    }
                                    if (cartItemsList.isEmpty()) {
                                        validationError = "Debe seleccionar al menos un producto"
                                        return@Button
                                    }
                                    showConfirmationStep = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_revisar_comanda_barra"),
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(10.dp),
                                enabled = cartItemsList.isNotEmpty()
                            ) {
                                Text(
                                    "Revisar y Confirmar Comanda",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val activeCustomProd = customizingProduct
    if (activeCustomProd != null) {
        PersonalizarBarraProductoDialog(
            product = activeCustomProd,
            subcategory = getBarraSubcategory(activeCustomProd),
            onDismiss = { customizingProduct = null },
            onAddCartItem = { newItem ->
                cart.add(newItem)
                customizingProduct = null
            }
        )
    }
}

@Composable
private fun BarraComandaConfirmationStep(
    tableNumber: Int?,
    cartItems: List<BarraCartItem>,
    totalAmount: Double,
    totalBebidasAmount: Double,
    totalConfiteriasAmount: Double,
    onBack: () -> Unit,
    onConfirm: () -> Unit
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                    Column {
                        Text(
                            "Resumen de Comanda",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            if (tableNumber == null) "PARA LLEVAR • Terminal de Barra" else "Mesa #$tableNumber • Terminal de Barra",
                            color = ElQadreGold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Header Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Origen:", fontSize = 12.sp, color = Slate500)
                            Text("Barra", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mesa Asignada:", fontSize = 12.sp, color = Slate500)
                            Text(
                                if (tableNumber == null) "PARA LLEVAR" else "Mesa #$tableNumber",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (tableNumber == null) Amber800 else Emerald700
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Productos:", fontSize = 12.sp, color = Slate500)
                            Text("${cartItems.sumOf { it.quantity }} unidades", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }
            }

            // Products Table List
            item {
                Text(
                    "Detalle de Productos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ElQadreNavy
                )
            }

            items(cartItems) { item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.product.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (item.subcategory == "BEBIDAS") Color(0xFFDBEAFE) else Amber100
                                ) {
                                    Text(
                                        item.subcategory,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.subcategory == "BEBIDAS") Color(0xFF1E40AF) else Amber800,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    "${item.quantity} ud. × $${"%.2f".format(item.product.price)}",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                        }

                        Text(
                            "$${"%.2f".format(item.totalAmount)} CUP",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Emerald700
                        )
                    }
                }
            }

            // Category Totals
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal Bebidas:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(totalBebidasAmount)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal Confiterías:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(totalConfiteriasAmount)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber800)
                        }
                        HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TOTAL A COBRAR:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text("$${"%.2f".format(totalAmount)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Emerald700)
                        }
                    }
                }
            }

            // Rule Note
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                        Text(
                            "Al confirmar, la comanda quedará registrada con número consecutivo. El inventario se descontará únicamente al procesar el COBRO.",
                            fontSize = 11.sp,
                            color = Color(0xFF1E40AF),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Modificar", color = Slate700)
                }

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp)
                        .testTag("btn_confirmar_comanda_final")
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirmar y Registrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOG: COBRO DE COMANDA DE BARRA
// -------------------------------------------------------------
@Composable
fun BarraCobroDialog(
    order: TableOrder,
    items: List<OrderItem>,
    products: List<Product>,
    tasaUsd: Double,
    tasaEur: Double,
    onDismiss: () -> Unit,
    onConfirmCobro: (paymentMethod: String, cashReceived: Double, changeGiven: Double, currency: String, exchangeRate: Double, amountInCurrency: Double) -> Unit
) {
    val barraItems = remember(items) { items.filter { it.destination == "BARRA" } }
    val totalBarra = remember(barraItems, order) {
        if (barraItems.isNotEmpty()) barraItems.sumOf { it.unitPrice * it.quantity }
        else order.totalAmount
    }

    // Pre-check stock sufficiency for each barra item
    val stockIssues = remember(barraItems, products) {
        barraItems.mapNotNull { item ->
            val prod = products.find { it.id == item.productId }
            val available = prod?.stock ?: 0
            if (available < item.quantity) {
                Triple(item.productName, available, item.quantity)
            } else null
        }
    }

    val hasInsufficientStock = stockIssues.isNotEmpty()

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("SessionPrefs", android.content.Context.MODE_PRIVATE) }
    val aceptarTransferencias = remember(prefs) { prefs.getBoolean("barra_aceptar_transferencias", true) }
    val paymentMethods = remember(aceptarTransferencias) {
        if (aceptarTransferencias) listOf("EFECTIVO", "TRANSFERENCIA", "MIXTO")
        else listOf("EFECTIVO")
    }

    var paymentMethod by remember { mutableStateOf("EFECTIVO") }
    var selectedCurrency by remember { mutableStateOf("CUP") }

    LaunchedEffect(aceptarTransferencias) {
        if (!aceptarTransferencias && paymentMethod != "EFECTIVO") {
            paymentMethod = "EFECTIVO"
        }
    }

    val activeRate = when (selectedCurrency) {
        "USD" -> if (tasaUsd > 0.0) tasaUsd else 1.0
        "EUR" -> if (tasaEur > 0.0) tasaEur else 1.0
        else -> 1.0
    }
    val totalBarraInCurrency = totalBarra / activeRate

    var cashReceivedText by remember { mutableStateOf("0") }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCurrency) {
        cashReceivedText = "%.2f".format(totalBarraInCurrency).replace(",", ".")
    }

    val cashReceived = cashReceivedText.toDoubleOrNull() ?: 0.0
    val changeGiven = maxOf(0.0, cashReceived - totalBarraInCurrency)

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
                                "Cobrar Comanda #${order.comandaNumber}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "Mesa #${order.tableNumber} • Origen: ${if (order.waiterUsername.contains("salon", ignoreCase = true)) "Salón" else "Barra"}",
                                color = ElQadreGold,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Alert if Insufficient Stock
                    if (hasInsufficientStock) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Rose50,
                                border = BorderStroke(1.5.dp, Rose300),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Rose700)
                                        Text(
                                            "STOCK INSUFICIENTE PARA COBRO",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Rose800
                                        )
                                    }
                                    Text(
                                        "No es posible procesar el cobro porque el inventario no puede quedar en negativo:",
                                        fontSize = 11.sp,
                                        color = Rose800
                                    )

                                    stockIssues.forEach { (name, avail, req) ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Rose200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                                                    Text("Disponible: $avail ud. • Solicitado: $req ud.", fontSize = 10.sp, color = Slate600)
                                                }
                                                Text(
                                                    "Faltan: ${req - avail} ud.",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    color = Rose700
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        "Por favor registre una entrada de inventario antes de cobrar.",
                                        fontSize = 10.sp,
                                        color = Rose800,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 2. Order Items Breakdown Card
                    item {
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
                                Text(
                                    "Productos a Descontar de Inventario",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ElQadreNavy
                                )

                                barraItems.forEach { item ->
                                    val prod = products.find { it.id == item.productId }
                                    val currentStock = prod?.stock ?: 0
                                    val newStock = currentStock - item.quantity

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate800)
                                            Text(
                                                "${item.quantity} ud. × $${"%.2f".format(item.unitPrice)} | Stock: $currentStock → $newStock ud.",
                                                fontSize = 10.sp,
                                                color = if (newStock < 0) Rose600 else Slate500
                                            )
                                        }
                                        Text(
                                            "$${"%.2f".format(item.unitPrice * item.quantity)} CUP",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Emerald700
                                        )
                                    }
                                    HorizontalDivider(color = Slate100)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("IMPORTE TOTAL:", fontWeight = FontWeight.Black, fontSize = 13.sp, color = ElQadreNavy)
                                    Text("$${"%.2f".format(totalBarra)} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Emerald700)
                                }
                            }
                        }
                    }

                        // 3. Currency Selection Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Moneda de la Operación",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("CUP", "USD", "EUR").forEach { curr ->
                                            OutlinedButton(
                                                onClick = { selectedCurrency = curr },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                                border = BorderStroke(1.dp, if (selectedCurrency == curr) ElQadreNavy else Slate200),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (selectedCurrency == curr) ElQadreNavy else Color.White,
                                                    contentColor = if (selectedCurrency == curr) Color.White else Slate700
                                                )
                                            ) {
                                                Text(curr, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (selectedCurrency != "CUP") {
                                        val rateText = when (selectedCurrency) {
                                            "USD" -> "1 USD = $tasaUsd CUP"
                                            "EUR" -> "1 EUR = $tasaEur CUP"
                                            else -> ""
                                        }
                                        Text(
                                            text = "Tasa de cambio: $rateText (Monto en $selectedCurrency: ${"%.2f".format(totalBarraInCurrency)})",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Payment Method Selection
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Método de Pago",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        paymentMethods.forEach { method ->
                                            OutlinedButton(
                                                onClick = { paymentMethod = method },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                                border = BorderStroke(1.dp, if (paymentMethod == method) ElQadreNavy else Slate200),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (paymentMethod == method) ElQadreNavy else Color.White,
                                                    contentColor = if (paymentMethod == method) Color.White else Slate700
                                                )
                                            ) {
                                                Text(method, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    when (paymentMethod) {
                                        "EFECTIVO" -> {
                                            Text("Efectivo Recibido ($selectedCurrency):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                            OutlinedTextField(
                                                value = cashReceivedText,
                                                onValueChange = {
                                                    cashReceivedText = it.filter { ch -> ch.isDigit() || ch == '.' }
                                                    validationError = null
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                prefix = { Text("$", fontSize = 12.sp, color = Slate500) },
                                                suffix = { Text(selectedCurrency, fontSize = 12.sp, color = Slate500) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("input_efectivo_recibido"),
                                                shape = RoundedCornerShape(8.dp),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = Color.White,
                                                    unfocusedContainerColor = Color.White
                                                )
                                            )

                                            // Quick cash chips
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val quickAmounts = if (selectedCurrency == "CUP") {
                                                    listOf(totalBarraInCurrency, totalBarraInCurrency + 50.0, totalBarraInCurrency + 100.0, 500.0, 1000.0)
                                                } else {
                                                    listOf(totalBarraInCurrency, totalBarraInCurrency + 5.0, totalBarraInCurrency + 10.0, 20.0, 50.0)
                                                }
                                                quickAmounts.forEach { amount ->
                                                    if (amount >= totalBarraInCurrency) {
                                                        OutlinedButton(
                                                            onClick = { cashReceivedText = amount.toInt().toString() },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("$${amount.toInt()}", fontSize = 10.sp, color = Slate700)
                                                        }
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Emerald50,
                                                border = BorderStroke(1.dp, Emerald200),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Vuelto a Entregar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                                                    Text("${"%.2f".format(changeGiven)} $selectedCurrency", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                                }
                                            }
                                        }
                                        "TRANSFERENCIA" -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFEFF6FF),
                                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text("Importe a Recibir por Transferencia:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                                    Text(
                                                        text = "${"%.2f".format(totalBarraInCurrency)} $selectedCurrency" + if (selectedCurrency != "CUP") " ($${"%.2f".format(totalBarra)} CUP)" else "",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF1E3A8A)
                                                    )
                                                    Text(
                                                        "Cobro por canal digital / transferencia confirmado.",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF3B82F6)
                                                    )
                                                }
                                            }
                                        }
                                        "MIXTO" -> {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Parte en Efectivo ($selectedCurrency):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                                OutlinedTextField(
                                                    value = cashReceivedText,
                                                    onValueChange = {
                                                        cashReceivedText = it.filter { ch -> ch.isDigit() || ch == '.' }
                                                        validationError = null
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    prefix = { Text("$", fontSize = 12.sp, color = Slate500) },
                                                    suffix = { Text(selectedCurrency, fontSize = 12.sp, color = Slate500) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("input_efectivo_recibido"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color.White,
                                                        unfocusedContainerColor = Color.White
                                                    )
                                                )

                                                val transferRemainder = maxOf(0.0, totalBarraInCurrency - cashReceived)
                                                val mixedChange = maxOf(0.0, cashReceived - totalBarraInCurrency)

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFEFF6FF),
                                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                                    modifier = Modifier.fillMaxWidth()
                                                 ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Efectivo recibido:", fontSize = 11.sp, color = Slate700)
                                                            Text("${"%.2f".format(minOf(cashReceived, totalBarraInCurrency))} $selectedCurrency", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Resto por Transferencia:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                                            Text("${"%.2f".format(transferRemainder)} $selectedCurrency", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E3A8A))
                                                        }
                                                        if (mixedChange > 0.0) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("Vuelto de efectivo:", fontSize = 11.sp, color = Emerald800)
                                                                Text("${"%.2f".format(mixedChange)} $selectedCurrency", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
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

                // Bottom Confirmation Button
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        validationError?.let {
                            Text(it, color = Rose600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Cancelar", color = Slate700)
                            }

                            Button(
                                onClick = {
                                    if (hasInsufficientStock) {
                                        validationError = "No se puede cobrar: hay productos con stock insuficiente"
                                        return@Button
                                    }
                                    if (paymentMethod == "EFECTIVO" && cashReceived < totalBarraInCurrency) {
                                        validationError = "El efectivo recibido no cubre el importe total"
                                        return@Button
                                    }
                                    val finalExchangeRate = activeRate
                                    val finalAmountInCurrency = totalBarra / finalExchangeRate
                                    val persistenceCash = when (paymentMethod) {
                                        "TRANSFERENCIA" -> 0.0
                                        "MIXTO" -> minOf(cashReceived * finalExchangeRate, totalBarra)
                                        else -> cashReceived * finalExchangeRate
                                    }
                                    val persistenceChange = when (paymentMethod) {
                                        "TRANSFERENCIA" -> 0.0
                                        "MIXTO" -> maxOf(0.0, (cashReceived * finalExchangeRate) - totalBarra)
                                        else -> maxOf(0.0, (cashReceived * finalExchangeRate) - totalBarra)
                                    }

                                    onConfirmCobro(paymentMethod, persistenceCash, persistenceChange, selectedCurrency, finalExchangeRate, finalAmountInCurrency)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp)
                                    .testTag("btn_confirmar_cobro_modal"),
                                enabled = !hasInsufficientStock
                            ) {
                                Icon(Icons.Outlined.Paid, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cobrar $${"%.2f".format(totalBarra)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

// Dialog to customize product with Agregados / Complements for Barra
@Composable
private fun PersonalizarBarraProductoDialog(
    product: Product,
    subcategory: String,
    onDismiss: () -> Unit,
    onAddCartItem: (BarraCartItem) -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    val selectedAgregados = remember { mutableStateListOf<Pair<String, Double>>() }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                Text("Precio base: $${"%.2f".format(product.price)} CUP", fontSize = 11.sp, color = Slate500)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Agregados / Complementos disponibles:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(agregadosDisponibles) { agreg ->
                        val isChecked = selectedAgregados.any { it.first == agreg.first }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
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
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedAgregados.add(agreg)
                                            else selectedAgregados.removeAll { it.first == agreg.first }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                    )
                                    Text(agreg.first, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Text("+$${"%.2f".format(agreg.second)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                            }
                        }
                    }
                }

                // Quantity Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cantidad:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Text("$quantity", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        OutlinedIconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                HorizontalDivider(color = Slate200)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PRECIO FINAL:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                    Text("$${"%.2f".format(totalPrice)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Emerald700)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddCartItem(
                        BarraCartItem(
                            product = product,
                            quantity = quantity,
                            subcategory = subcategory,
                            selectedAgregados = selectedAgregados.toList()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text("AGREGAR AL PEDIDO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text("CANCELAR", color = Slate500, fontWeight = FontWeight.Bold)
            }
        }
    )
}

