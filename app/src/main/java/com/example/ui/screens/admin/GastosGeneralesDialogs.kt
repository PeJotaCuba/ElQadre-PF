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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostCalculationHelper
import com.example.util.CostSheetPdfExporter
import com.example.util.IngredientCostDetail
import androidx.compose.ui.platform.LocalContext

private fun normalizePeriodLabel(p: String, days: Int): String {
    val upper = p.uppercase().trim()
    return when {
        upper == "SEMANA LABORABLE" || (upper == "SEMANAL" && days == 6) -> "SEMANA LABORABLE"
        upper == "SEMANA" || upper == "SEMANAL" -> "SEMANA"
        upper == "MES LABORABLE" || (upper == "MENSUAL" && days == 26) -> "MES LABORABLE"
        upper == "MES" || upper == "MENSUAL" -> "MES"
        upper == "AÑO LABORABLE" || upper == "ANO LABORABLE" || (upper == "ANUAL" && days == 312) -> "AÑO LABORABLE"
        upper == "AÑO" || upper == "ANO" || upper == "ANUAL" -> "AÑO"
        upper == "DÍA" || upper == "DIA" || upper == "DIARIO" || upper == "ÚNICO" || upper == "UNICO" -> "DÍA"
        else -> "MES LABORABLE"
    }
}

private fun getPeriodDaysForPeriod(p: String): Int {
    return when (p.uppercase().trim()) {
        "DÍA", "DIA", "DIARIO", "ÚNICO", "UNICO" -> 1
        "SEMANA", "SEMANAL" -> 7
        "SEMANA LABORABLE" -> 6
        "MES", "MENSUAL" -> 30
        "MES LABORABLE" -> 26
        "AÑO", "ANO", "ANUAL" -> 360
        "AÑO LABORABLE", "ANO LABORABLE" -> 312
        else -> 26
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGastoGeneralDialog(
    gasto: GastoGeneral?,
    products: List<Product> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (GastoGeneral) -> Unit
) {
    var name by remember { mutableStateOf(gasto?.name ?: "") }
    var description by remember { mutableStateOf(gasto?.description ?: "") }
    var amount by remember { mutableStateOf(if (gasto != null && gasto.amount > 0.0) gasto.amount.toString() else "") }
    var period by remember { 
        mutableStateOf(
            if (gasto != null) normalizePeriodLabel(gasto.period, gasto.periodDays) 
            else "MES LABORABLE"
        ) 
    }
    var periodDaysText by remember { 
        mutableStateOf(
            (gasto?.periodDays?.takeIf { it > 0 } ?: getPeriodDaysForPeriod(period)).toString()
        ) 
    }
    var category by remember { mutableStateOf(gasto?.category ?: "Otros") }
    var isActive by remember { mutableStateOf(gasto?.isActive ?: true) }

    val periods = listOf(
        "DÍA",
        "SEMANA",
        "SEMANA LABORABLE",
        "MES",
        "MES LABORABLE",
        "AÑO",
        "AÑO LABORABLE"
    )
    val categories = listOf(
        "Personal",
        "Electricidad",
        "Transporte",
        "Limpieza",
        "Insumos indirectos",
        "Agua",
        "Seguridad",
        "Mantenimiento",
        "Otros"
    )

    var expandedPeriod by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf(false) }

    // Live calculation for preview
    val amtVal = amount.toDoubleOrNull() ?: 0.0
    val daysVal = periodDaysText.toIntOrNull() ?: getPeriodDaysForPeriod(period)
    val dailyEquivalent = if (amtVal > 0.0 && daysVal > 0) {
        amtVal / daysVal
    } else 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp)
                .background(Color.Transparent),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (gasto == null) "Nuevo Gasto General" else "Editar Gasto General",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Gastos generales del negocio (sin asociación a producto)",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = ElQadreBorderLight)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Gasto (ej. Electricidad hornos)") },
                    modifier = Modifier.fillMaxWidth().testTag("gasto_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción o notas (opcional)") },
                    modifier = Modifier.fillMaxWidth().testTag("gasto_desc_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    )
                )

                // Category dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría del Gasto") },
                        trailingIcon = {
                            IconButton(onClick = { expandedCategory = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedCategory = true }
                            .testTag("gasto_category_trigger")
                    )
                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c, fontSize = 13.sp) },
                                onClick = {
                                    category = c
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Importe ($ CUP)") },
                        placeholder = { Text("0.00", color = Slate400) },
                        modifier = Modifier.weight(1.2f).testTag("gasto_amount_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    // Period dropdown
                    Box(modifier = Modifier.weight(1.3f)) {
                        OutlinedTextField(
                            value = period,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Periodicidad") },
                            trailingIcon = {
                                IconButton(onClick = { expandedPeriod = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedPeriod = true }
                                .testTag("gasto_period_trigger")
                        )
                        DropdownMenu(
                            expanded = expandedPeriod,
                            onDismissRequest = { expandedPeriod = false }
                        ) {
                            periods.forEach { p ->
                                val labelDays = when (p) {
                                    "DÍA" -> "1 día"
                                    "SEMANA" -> "7 días"
                                    "SEMANA LABORABLE" -> "6 días"
                                    "MES" -> "30 días"
                                    "MES LABORABLE" -> "26 días"
                                    "AÑO" -> "360 días"
                                    "AÑO LABORABLE" -> "312 días"
                                    else -> ""
                                }
                                DropdownMenuItem(
                                    text = { Text("$p ($labelDays)", fontSize = 13.sp) },
                                    onClick = {
                                        period = p
                                        periodDaysText = when (p) {
                                            "DÍA" -> "1"
                                            "SEMANA" -> "7"
                                            "SEMANA LABORABLE" -> "6"
                                            "MES" -> "30"
                                            "MES LABORABLE" -> "26"
                                            "AÑO" -> "360"
                                            "AÑO LABORABLE" -> "312"
                                            else -> "26"
                                        }
                                        expandedPeriod = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = periodDaysText,
                        onValueChange = { periodDaysText = it },
                        label = { Text("Días base") },
                        modifier = Modifier.weight(0.8f).testTag("gasto_days_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )
                }

                // Live Calculation Preview Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElQadreGoldSoft,
                    border = BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("EQUIVALENTE DIARIO NORMALIZADO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                            Text(
                                "$${"%.2f".format(dailyEquivalent)} CUP / día",
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy,
                                fontSize = 13.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BASE DE CÁLCULO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                            Text(
                                "$daysVal días ($period)",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Status Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estado del Gasto", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        Text(
                            if (isActive) "Gasto activo (se calcula costo diario equivalente)" else "Gasto pausado",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElQadreGold,
                            checkedTrackColor = ElQadreNavy
                        ),
                        modifier = Modifier.testTag("gasto_active_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar", color = Slate600)
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            val days = periodDaysText.toIntOrNull() ?: getPeriodDaysForPeriod(period)
                            if (name.isNotBlank() && amt > 0.0) {
                                val newGasto = GastoGeneral(
                                    id = gasto?.id ?: 0,
                                    name = name.trim(),
                                    description = description.trim(),
                                    amount = amt,
                                    period = period,
                                    periodDays = days,
                                    category = category,
                                    isActive = isActive,
                                    targetProductId = null,
                                    targetProductIds = null
                                )
                                onConfirm(newGasto)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.weight(1f).testTag("save_gasto_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIÁLOGO: FICHA DE COSTO DE PRODUCCIÓN
 * Implementa las 7 secciones completas de cálculo y decisión económica
 */
@Composable
fun FichaCostoDialog(
    product: Product,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentProduct = uiState.products.find { it.id == product.id } ?: product

    val costSheet = remember(
        currentProduct,
        uiState.products,
        uiState.productosElaborados,
        uiState.recetaIngredientes,
        uiState.materiasPrimas,
        uiState.gastosGenerales,
        uiState.inversiones,
        uiState.mercaderias,
        uiState.movimientosMercaderia
    ) {
        CostCalculationHelper.calculateCostSheet(
            product = currentProduct,
            products = uiState.products,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones,
            mercaderias = uiState.mercaderias,
            movimientosMercaderia = uiState.movimientosMercaderia
        )
    }

    var manualPriceText by remember(costSheet.precioDefinitivo, costSheet.precioReferencia) {
        mutableStateOf(
            if (costSheet.hasPrecioDefinitivo) "%.2f".format(costSheet.precioDefinitivo)
            else if (costSheet.precioReferencia > 0.0) "%.2f".format(costSheet.precioReferencia)
            else "0.00"
        )
    }

    var estimatedQtyText by remember(costSheet.ppd) {
        mutableStateOf(if (costSheet.ppd % 1.0 == 0.0) costSheet.ppd.toLong().toString() else costSheet.ppd.toString())
    }

    var isPagoCocinaFijo by remember(costSheet.isPagoCocinaFijo) {
        mutableStateOf(costSheet.isPagoCocinaFijo)
    }

    var pagoCocinaText by remember(costSheet.pagoCocinaUnitario) {
        mutableStateOf(if (costSheet.pagoCocinaUnitario > 0.0) if (costSheet.pagoCocinaUnitario % 1.0 == 0.0) costSheet.pagoCocinaUnitario.toLong().toString() else "%.2f".format(costSheet.pagoCocinaUnitario) else "")
    }

    var cantidadCocinerosText by remember(costSheet.cantidadCocineros) {
        mutableStateOf(if (costSheet.cantidadCocineros > 0) costSheet.cantidadCocineros.toString() else "1")
    }

    var pagoDependienteText by remember(costSheet.pagoDependienteUnitario) {
        mutableStateOf(if (costSheet.pagoDependienteUnitario > 0.0) if (costSheet.pagoDependienteUnitario % 1.0 == 0.0) costSheet.pagoDependienteUnitario.toLong().toString() else "%.2f".format(costSheet.pagoDependienteUnitario) else "")
    }

    var pagoCajeroText by remember(costSheet.pagoCajeroUnitario) {
        mutableStateOf(if (costSheet.pagoCajeroUnitario > 0.0) if (costSheet.pagoCajeroUnitario % 1.0 == 0.0) costSheet.pagoCajeroUnitario.toLong().toString() else "%.2f".format(costSheet.pagoCajeroUnitario) else "")
    }

    val presentaciones = remember(currentProduct.presentacionesEspeciales) {
        parsePresentacionesEspeciales(currentProduct.presentacionesEspeciales)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .fillMaxHeight(0.80f),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.5.dp, Slate200),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // DIALOG HEADER PANTALLA COMPLETA SENIOR
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = ElQadreGoldDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "FICHA DE COSTO",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = ElQadreNavy
                                    )
                                }
                                Text(
                                    text = "${currentProduct.name} (Catálogo Cocina)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGoldDark,
                                    maxLines = 1
                                )
                            }

                            // ACTIVAR / DESACTIVAR PRODUCTO DIRECTAMENTE
                            val isProdActive = currentProduct.isAvailable
                            Surface(
                                onClick = {
                                    val newAvailable = !isProdActive
                                    viewModel.updateProduct(currentProduct.copy(isAvailable = newAvailable))
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isProdActive) Emerald50 else Rose50,
                                border = BorderStroke(1.5.dp, if (isProdActive) Emerald500 else Rose500),
                                modifier = Modifier.testTag("toggle_product_active_ficha")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isProdActive) Emerald600 else Rose600)
                                    )
                                    Text(
                                        text = if (isProdActive) "ACTIVO" else "INACTIVO",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = if (isProdActive) Emerald700 else Rose700
                                    )
                                    Switch(
                                        checked = isProdActive,
                                        onCheckedChange = { isChecked ->
                                            viewModel.updateProduct(currentProduct.copy(isAvailable = isChecked))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Emerald600,
                                            checkedTrackColor = Emerald100,
                                            uncheckedThumbColor = Slate400,
                                            uncheckedTrackColor = Slate200
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                            }
                        }
                    }

                // SCROLLABLE BODY WITH 7 SECTIONS
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!costSheet.isComplete) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Rose50,
                            border = BorderStroke(1.dp, Rose500),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Rose600,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Ficha de costo no disponible",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Rose700
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    costSheet.missingDataReason ?: "Faltan datos de receta / materia prima / gastos generales.",
                                    fontSize = 12.sp,
                                    color = Slate700,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 1: IDENTIFICACIÓN DEL PRODUCTO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "1. IDENTIFICACIÓN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (costSheet.hasPrecioDefinitivo) Emerald100 else Rose100
                                ) {
                                    Text(
                                        text = if (costSheet.hasPrecioDefinitivo) "PRECIO DEFINITIVO CONFIGURADO" else "SIN PRECIO DEFINITIVO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (costSheet.hasPrecioDefinitivo) Emerald700 else Rose700,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Artículo", fontSize = 10.sp, color = Slate500)
                                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                }
                                Column {
                                    Text("Categoría", fontSize = 10.sp, color = Slate500)
                                    Text(product.category, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Slate700)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Unidad de Medida", fontSize = 10.sp, color = Slate500)
                                    Text(costSheet.productionUnit, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Slate700)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 2: RECETA Y RENDIMIENTO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ElQadreBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "2. RECETA E INSUMOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    "Rendimiento Base: ${costSheet.baseYield} ${costSheet.productionUnit}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGoldDark
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (costSheet.ingredientDetails.isEmpty()) {
                                Text(
                                    "No hay ingredientes registrados en la receta. Configure la receta para calcular costos directos.",
                                    fontSize = 11.sp,
                                    color = Rose600,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    costSheet.ingredientDetails.forEach { ing ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Slate50, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1.5f)) {
                                                Text(
                                                    ing.materiaPrima?.name ?: "Ingrediente",
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp,
                                                    color = ElQadreNavy
                                                )
                                                Text(
                                                    "Costo unitario MP: $${"%.2f".format(ing.unitCost)} / ${ing.materiaPrima?.unit ?: ing.unit}",
                                                    fontSize = 10.sp,
                                                    color = Slate500
                                                )
                                            }
                                            Text(
                                                "${ing.quantity} ${ing.unit}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate700,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                "$${"%.2f".format(ing.totalCost)} CUP",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 3: COSTO DIRECTO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "3. COSTO DIRECTO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Costo Total Receta Base", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.totalDirectRecipeCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                }
                                Column {
                                    Text("Rendimiento", fontSize = 10.sp, color = Slate500)
                                    Text("${costSheet.baseYield} ${costSheet.productionUnit}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate700)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("COSTO DIRECTO UNITARIO (CDU)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                    Text(
                                        "$${"%.2f".format(costSheet.costoDirectoUnitario)} CUP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = ElQadreGoldDark
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 4: PRODUCCIÓN PROMEDIO DIARIA (PPD)
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ElQadreBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "4. PRODUCCIÓN PROMEDIO DIARIA (PPD)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = estimatedQtyText,
                                    onValueChange = { estimatedQtyText = it },
                                    label = { Text("PPD - Producción Promedio Diaria (${costSheet.productionUnit}/día)") },
                                    modifier = Modifier.weight(1.3f).testTag("ficha_estimated_qty_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                Button(
                                    onClick = {
                                        val newPpd = estimatedQtyText.toDoubleOrNull() ?: 10.0
                                        if (newPpd > 0.0) {
                                            viewModel.updatePpd(product.id, newPpd)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp).testTag("ficha_update_qty_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guardar PPD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate50, RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PPD Producto", fontSize = 10.sp, color = Slate500)
                                    Text("${if (costSheet.ppd % 1.0 == 0.0) costSheet.ppd.toLong().toString() else costSheet.ppd} ${costSheet.productionUnit}/día", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Participación en Inventario Económico", fontSize = 10.sp, color = Slate500)
                                    Text("${"%.2f".format(costSheet.porcentajeParticipacionPpd)}%", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = ElQadreGoldDark)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 5: GASTOS E INVERSIONES - PRORRATEO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "5. EGRESOS - PRORRATEO ECONÓMICO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Gastos Generales Diarios", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.gastosGeneralesDiariosTotales)} / día", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                                }
                                Column {
                                    Text("Depreciación Inversiones", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.depreciacionInversionesDiariaTotales)} / día", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Slate700)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Costos Indirectos Totales", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.costosIndirectosDiariosTotales)} / día", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = ElQadreNavy)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Slate200)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Asignación directa (${"%.2f".format(costSheet.porcentajeParticipacionPpd)}%)", fontSize = 10.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.gastoIndirectoAsignado)} CUP / día", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("GASTO INDIRECTO UNITARIO (GIU)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                    Text("$${"%.2f".format(costSheet.gastoIndirectoUnitario)} CUP / ud", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = ElQadreGoldDark)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 6: PAGOS DE PERSONAL ASOCIADOS
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Color(0xFF6366F1))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.People,
                                        contentDescription = null,
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "6. PAGOS DE PERSONAL ASOCIADOS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }

                                    val currentPpdForPersonal = costSheet.ppd.takeIf { it > 0.0 } ?: estimatedQtyText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                                    val cocinaUnitLive = pagoCocinaText.toDoubleOrNull() ?: 0.0
                                    val cocinerosCountLive = cantidadCocinerosText.toIntOrNull() ?: 1
                                    val subtotalCocinaLive = if (isPagoCocinaFijo) {
                                        if (currentPpdForPersonal > 0.0) cocinaUnitLive / currentPpdForPersonal else 0.0
                                    } else {
                                        cocinaUnitLive * (if (cocinerosCountLive > 0) cocinerosCountLive else 1)
                                    }
                                    val depValLive = pagoDependienteText.toDoubleOrNull() ?: 0.0
                                    val cajeroValLive = pagoCajeroText.toDoubleOrNull() ?: 0.0
                                    val totalPersonalLive = subtotalCocinaLive + depValLive + cajeroValLive

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFEEF2FF),
                                        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                    ) {
                                        Text(
                                            text = "$${"%.2f".format(totalPersonalLive)} CUP / ud",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF4338CA),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                            }

                            Text(
                                "Configure los pagos de personal por unidad producida/vendida asociados a este producto de Producción:",
                                fontSize = 11.sp,
                                color = Slate600
                            )

                            // 1. COCINA
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val cocinaUnit = pagoCocinaText.toDoubleOrNull() ?: 0.0
                                    val cocinerosCount = cantidadCocinerosText.toIntOrNull() ?: 1
                                    val currentPpd = costSheet.ppd.takeIf { it > 0.0 } ?: estimatedQtyText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                                    val subtotalCocina = if (isPagoCocinaFijo) {
                                        if (currentPpd > 0.0) cocinaUnit / currentPpd else 0.0
                                    } else {
                                        cocinaUnit * (if (cocinerosCount > 0) cocinerosCount else 1)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("COCINA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)

                                            // CONTROL: EL PAGO FIJO (ACTIVADO / DESACTIVADO)
                                            Surface(
                                                onClick = { isPagoCocinaFijo = !isPagoCocinaFijo },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isPagoCocinaFijo) Color(0xFFEEF2FF) else Slate100,
                                                border = BorderStroke(1.dp, if (isPagoCocinaFijo) Color(0xFF6366F1) else Slate300),
                                                modifier = Modifier.testTag("control_el_pago_fijo")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "EL PAGO FIJO",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPagoCocinaFijo) Color(0xFF4338CA) else Slate600
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isPagoCocinaFijo) Color(0xFF4F46E5) else Slate300
                                                    ) {
                                                        Text(
                                                            text = if (isPagoCocinaFijo) "ACTIVADO" else "DESACTIVADO",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = if (isPagoCocinaFijo) Color.White else Slate700,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Switch(
                                                        checked = isPagoCocinaFijo,
                                                        onCheckedChange = { isPagoCocinaFijo = it },
                                                        modifier = Modifier.scale(0.7f).height(20.dp).testTag("switch_el_pago_fijo"),
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = Color.White,
                                                            checkedTrackColor = Color(0xFF4F46E5),
                                                            uncheckedThumbColor = Slate400,
                                                            uncheckedTrackColor = Slate200
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (isPagoCocinaFijo) "Costo: $${"%.2f".format(subtotalCocina)} CUP / ud" else "Subtotal: $${"%.2f".format(subtotalCocina)} CUP / ud",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

                                    if (isPagoCocinaFijo) {
                                        // CUANDO ESTÁ ACTIVADO
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedTextField(
                                                value = pagoCocinaText,
                                                onValueChange = { pagoCocinaText = it },
                                                label = { Text("Pago Fijo Diario ($ CUP)") },
                                                placeholder = { Text("Ej. 100") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("pago_cocina_fijo_diario_input"),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF6366F1),
                                                    focusedLabelColor = Color(0xFF4F46E5)
                                                )
                                            )

                                            val costoUnitarioFijo = if (currentPpd > 0.0) cocinaUnit / currentPpd else 0.0

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFEEF2FF),
                                                border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "PAGO FIJO DIARIO ÷ PPD = COSTO DE PAGO FIJO POR UNIDAD",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF4338CA)
                                                    )
                                                    Text(
                                                        text = "$${"%.2f".format(cocinaUnit)} CUP (pago fijo diario) ÷ ${"%.1f".format(currentPpd)} ud (PPD) = $${"%.2f".format(costoUnitarioFijo)} CUP por unidad",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ElQadreNavy
                                                    )
                                                    Text(
                                                        text = "Es fijo durante la jornada, se paga una sola vez por día y no depende directamente de las unidades producidas. El pago fijo real sigue siendo un solo pago diario de $${"%.2f".format(cocinaUnit)} CUP.",
                                                        fontSize = 9.sp,
                                                        color = Slate600
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // CUANDO ESTÁ DESACTIVADO
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = pagoCocinaText,
                                                onValueChange = { pagoCocinaText = it },
                                                label = { Text("Pago por unidad ($ CUP)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1.3f).testTag("pago_cocina_unitario_input"),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF6366F1),
                                                    focusedLabelColor = Color(0xFF4F46E5)
                                                )
                                            )

                                            OutlinedTextField(
                                                value = cantidadCocinerosText,
                                                onValueChange = { cantidadCocinerosText = it },
                                                label = { Text("Cant. Cocineros") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f).testTag("cantidad_cocineros_input"),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF6366F1),
                                                    focusedLabelColor = Color(0xFF4F46E5)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. DEPENDIENTE
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("DEPENDIENTE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Slate200
                                            ) {
                                                Text(
                                                    "1 dependiente",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate700,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        val depUnit = pagoDependienteText.toDoubleOrNull() ?: 0.0
                                        Text(
                                            "Subtotal: $${"%.2f".format(depUnit)} CUP / ud",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = pagoDependienteText,
                                        onValueChange = { pagoDependienteText = it },
                                        label = { Text("Pago por unidad producida/vendida ($ CUP)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("pago_dependiente_unitario_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6366F1),
                                            focusedLabelColor = Color(0xFF4F46E5)
                                        )
                                    )
                                }
                            }

                            // 3. CAJERO
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("CAJERO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        val cajeroUnit = pagoCajeroText.toDoubleOrNull() ?: 0.0
                                        Text(
                                            "Subtotal: $${"%.2f".format(cajeroUnit)} CUP / ud",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = pagoCajeroText,
                                        onValueChange = { pagoCajeroText = it },
                                        label = { Text("Pago por unidad producida/vendida ($ CUP)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("pago_cajero_unitario_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6366F1),
                                            focusedLabelColor = Color(0xFF4F46E5)
                                        )
                                    )
                                }
                            }

                            // BOTÓN GUARDAR PAGOS DE PERSONAL
                            Button(
                                onClick = {
                                    val cocinaVal = pagoCocinaText.toDoubleOrNull() ?: 0.0
                                    val cocinerosVal = cantidadCocinerosText.toIntOrNull() ?: 1
                                    val depVal = pagoDependienteText.toDoubleOrNull() ?: 0.0
                                    val cajeroVal = pagoCajeroText.toDoubleOrNull() ?: 0.0
                                    viewModel.updatePagosPersonalProductoElaborado(
                                        productId = product.id,
                                        pagoCocinaUnitario = cocinaVal,
                                        cantidadCocineros = cocinerosVal,
                                        pagoDependienteUnitario = depVal,
                                        pagoCajeroUnitario = cajeroVal,
                                        isPagoCocinaFijo = isPagoCocinaFijo
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_pagos_personal_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GUARDAR PAGOS DE PERSONAL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 7: PRESENTACIONES Y EQUIVALENCIAS (SI APLICA)
                    // ==========================================
                    if (presentaciones.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "7. PRESENTACIONES Y EQUIVALENCIAS CONFIGURADAS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = ElQadreGoldSoft
                                    ) {
                                        Text(
                                            "${presentaciones.size} presentación(es)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreGoldDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    "Materia prima y gastos se calculan según la equivalencia. El pago a personal corresponde al de 1 unidad simple:",
                                    fontSize = 10.5.sp,
                                    color = Slate600
                                )

                                presentaciones.forEach { pres ->
                                    val factor = pres.baseEquivalence
                                    val presMatPrima = costSheet.costoDirectoUnitario * factor
                                    val presGastos = costSheet.gastoIndirectoUnitario * factor
                                    val presPersonal = costSheet.totalPagoPersonalUnitario
                                    val presCostoTotal = presMatPrima + presGastos + presPersonal
                                    val targetMargin = costSheet.productoElaborado?.targetMarginPct ?: 30.0
                                    val presPrecioRef = if (presCostoTotal > 0.0) presCostoTotal * (1.0 + targetMargin / 100.0) else 0.0

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(pres.name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = ElQadreNavy)
                                                Text(
                                                    "= ${pres.baseEquivalence} ${costSheet.productionUnit}",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp,
                                                    color = ElQadreGoldDark
                                                )
                                            }

                                            HorizontalDivider(color = Slate100)

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Mat. Prima", fontSize = 9.5.sp, color = Slate500)
                                                    Text("$${"%.2f".format(presMatPrima)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                                }
                                                Column {
                                                    Text("Gastos Grales", fontSize = 9.5.sp, color = Slate500)
                                                    Text("$${"%.2f".format(presGastos)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                                }
                                                Column {
                                                    Text("Pagos Personal", fontSize = 9.5.sp, color = Slate500)
                                                    Text("$${"%.2f".format(presPersonal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Costo Teórico", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                                                    Text("$${"%.2f".format(presCostoTotal)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                                }
                                            }

                                            if (presPrecioRef > 0.0) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Slate50, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Precio Referencia Sugerido (+30%):", fontSize = 10.sp, color = Slate600)
                                                    Text("$${"%.2f".format(presPrecioRef)} CUP", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 8: ESTRUCTURA DE COSTO UNITARIO FINAL
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElQadreNavy,
                        border = BorderStroke(1.dp, ElQadreNavy)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "8. ESTRUCTURA DE COSTO UNITARIO FINAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGold
                            )

                            Column(modifier = Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• Costo Directo Unitario:", fontSize = 11.sp, color = Color.White)
                                    Text("$${"%.2f".format(costSheet.costoDirectoUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• Gasto Indirecto Unitario:", fontSize = 11.sp, color = Color.White)
                                    Text("$${"%.2f".format(costSheet.gastoIndirectoUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• Pago a Personal por Unidad:", fontSize = 11.sp, color = Color.White)
                                    Text("$${"%.2f".format(costSheet.totalPagoPersonalUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                HorizontalDivider(color = Slate600, modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "COSTO UNITARIO FINAL",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElQadreGold
                                        )
                                        Text(
                                            "Por ${costSheet.productionUnit} producida/vendida",
                                            fontSize = 9.sp,
                                            color = Slate300
                                        )
                                    }
                                    Text(
                                        text = "$${"%.2f".format(costSheet.costoTotalUnitario)} CUP",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ElQadreGold
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 9: PRECIO DE VENTA Y DECISIÓN DE FIJACIÓN
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, ElQadreGold)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "9. PRECIO DE VENTA Y DECISIÓN DE FIJACIÓN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            // Status and Reference Price Summary
                            val precioCatalogo = currentProduct.price
                            val margenCatalogoPct = if (costSheet.costoTotalUnitario > 0.0 && precioCatalogo > 0.0) {
                                ((precioCatalogo - costSheet.costoTotalUnitario) / costSheet.costoTotalUnitario) * 100.0
                            } else null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ElQadreGoldSoft, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PRECIO DE REFERENCIA (+30% MARGEN)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(costSheet.precioReferencia)} CUP",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ElQadreNavy
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val catLabel = if (margenCatalogoPct != null) {
                                        "PRECIO EN CATÁLOGO (${if (margenCatalogoPct >= 0) "+" else ""}${"%.1f".format(margenCatalogoPct)}% GANANCIA)"
                                    } else {
                                        "PRECIO EN CATÁLOGO"
                                    }
                                    Text(catLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(currentProduct.price)} CUP",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (costSheet.hasPrecioDefinitivo) Emerald700 else Slate600
                                    )
                                }
                            }

                            // OPCIÓN A: USAR PRECIO DE REFERENCIA
                            Button(
                                onClick = {
                                    viewModel.setProductoDefinitivePrice(
                                        productId = currentProduct.id,
                                        definitivePrice = costSheet.precioReferencia,
                                        calculatedCost = costSheet.costoRealUnitario
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("use_reference_price_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Usar Precio de Referencia ($${"%.2f".format(costSheet.precioReferencia)} CUP)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // OPCIÓN B: ESTABLECER OTRO PRECIO
                            val manualPrice = manualPriceText.toDoubleOrNull() ?: 0.0
                            val manualMarginPct = if (costSheet.costoTotalUnitario > 0.0 && manualPrice > 0.0) {
                                ((manualPrice - costSheet.costoTotalUnitario) / costSheet.costoTotalUnitario) * 100.0
                            } else null
                            val manualGanancia = if (costSheet.costoTotalUnitario > 0.0 && manualPrice > 0.0) {
                                manualPrice - costSheet.costoTotalUnitario
                            } else null

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualPriceText,
                                    onValueChange = { manualPriceText = it },
                                    label = { Text("Establecer otro precio ($ CUP)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("manual_price_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                Button(
                                    onClick = {
                                        val p = manualPriceText.toDoubleOrNull() ?: 0.0
                                        if (p > 0.0) {
                                            viewModel.setProductoDefinitivePrice(
                                                productId = currentProduct.id,
                                                definitivePrice = p,
                                                calculatedCost = costSheet.costoRealUnitario
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp).testTag("save_definitive_price_button")
                                ) {
                                    Text("Fijar Definitivo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            if (manualPrice > 0.0 && manualMarginPct != null && manualGanancia != null) {
                                Surface(
                                    color = if (manualMarginPct >= 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (manualMarginPct >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ganancia: $${"%.2f".format(manualGanancia)} CUP",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (manualMarginPct >= 0) Emerald700 else Color(0xFFDC2626)
                                        )
                                        Text(
                                            text = "% de ganancia: ${if (manualMarginPct >= 0) "+" else ""}${"%.1f".format(manualMarginPct)}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (manualMarginPct >= 0) Emerald700 else Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }

                            // ==========================================
                            // PRESENTACIONES ESPECIALES - BLOQUES EQUIVALENTES
                            // ==========================================
                            if (presentaciones.isNotEmpty()) {
                                presentaciones.forEach { pres ->
                                    HorizontalDivider(
                                        color = Slate200,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    PresentacionEspecialPrecioBlock(
                                        pres = pres,
                                        product = currentProduct,
                                        costSheet = costSheet,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ElQadreBorderLight)

                // BOTTOM ACTION
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { CostSheetPdfExporter.exportSingleCostSheet(context, product, uiState) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, ElQadreNavy),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(54.dp)
                                .testTag("download_pdf_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DESCARGAR FICHA DE COSTO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.8f)
                                .height(54.dp)
                                .testTag("close_ficha_costo_button")
                        ) {
                            Text("LISTO", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
fun AddEditInversionDialog(
    inversion: Inversion?,
    products: List<Product> = emptyList(),
    generalConfig: ConfiguracionGeneral? = null,
    onDismiss: () -> Unit,
    onConfirm: (Inversion) -> Unit
) {
    var name by remember { mutableStateOf(inversion?.name ?: "") }
    var category by remember { mutableStateOf(inversion?.category ?: "Maquinaria y Equipos") }
    var currency by remember { mutableStateOf(inversion?.currency ?: "CUP") }
    var originalAmountText by remember { 
        mutableStateOf(
            if (inversion != null && inversion.originalAmount > 0.0) inversion.originalAmount.toString()
            else inversion?.amount?.toString() ?: ""
        ) 
    }
    val date by remember { mutableStateOf(inversion?.date ?: System.currentTimeMillis()) }
    var usefulLifeText by remember { mutableStateOf(inversion?.usefulLife?.toString() ?: "12") }
    var usefulLifeUnit by remember { 
        mutableStateOf(
            when (inversion?.usefulLifeUnit?.uppercase()) {
                "DIAS", "DÍAS", "DIA", "DÍA" -> "DÍAS"
                "MESES", "MES" -> "MESES"
                "AÑOS", "AÑO", "ANUAL" -> "AÑOS"
                else -> "MESES"
            }
        ) 
    }
    var observation by remember { mutableStateOf(inversion?.observation ?: "") }
    var targetProductId by remember { mutableStateOf<Long?>(inversion?.targetProductId) }
    var method by remember { mutableStateOf(inversion?.method ?: "VIDA_UTIL") }

    val tasaUsd = generalConfig?.tasaUsd ?: 0.0
    val tasaEur = generalConfig?.tasaEur ?: 0.0

    val exchangeRate = remember(currency, tasaUsd, tasaEur) {
        when (currency) {
            "USD" -> if (tasaUsd > 0.0) tasaUsd else 1.0
            "EUR" -> if (tasaEur > 0.0) tasaEur else 1.0
            else -> 1.0
        }
    }

    val categories = listOf(
        "Maquinaria y Equipos",
        "Herramientas",
        "Instalaciones",
        "Mobiliario",
        "Vehículos",
        "Tecnología",
        "Otros"
    )
    val lifeUnits = listOf("DÍAS", "MESES", "AÑOS")

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedUnit by remember { mutableStateOf(false) }

    val originalAmtVal = originalAmountText.toDoubleOrNull() ?: 0.0
    val convertedAmtVal = originalAmtVal * exchangeRate
    val lifeVal = usefulLifeText.toDoubleOrNull() ?: 1.0

    val dailyDep = if (convertedAmtVal > 0.0 && lifeVal > 0.0) {
        val days = when (usefulLifeUnit.uppercase()) {
            "DÍAS", "DIAS", "DÍA", "DIA" -> lifeVal
            "MESES", "MES" -> lifeVal * 30.0
            "AÑOS", "AÑO", "ANUAL" -> lifeVal * 360.0
            else -> lifeVal
        }
        if (days > 0.0) convertedAmtVal / days else 0.0
    } else 0.0

    val monthlyDep = dailyDep * 30.0

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
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (inversion == null) "Registrar Inversión" else "Editar Inversión",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "La depreciación participará automáticamente como costo indirecto",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = ElQadreBorderLight)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la Inversión (ej. Horno Convector)") },
                    modifier = Modifier.fillMaxWidth().testTag("inversion_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    )
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría de la Inversión") },
                        trailingIcon = {
                            IconButton(onClick = { expandedCategory = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expandedCategory = true }.testTag("inversion_category_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )
                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false },
                        modifier = Modifier.fillMaxWidth(0.84f)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                // Selección de Moneda (CUP, USD, EUR)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Moneda de la Operación", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("CUP", "USD", "EUR").forEach { curr ->
                            val isSelected = currency == curr
                            Surface(
                                onClick = { currency = curr },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ElQadreNavy else Slate100,
                                border = BorderStroke(1.dp, if (isSelected) ElQadreNavy else Slate300),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = curr,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Slate700
                                    )
                                }
                            }
                        }
                    }
                }

                // Selección de Método (Vida Útil, Recuperación)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Método", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("VIDA_UTIL" to "Vida Útil", "RECUPERACION" to "Recuperación").forEach { (mKey, mLabel) ->
                            val isSelected = method == mKey
                            Surface(
                                onClick = { method = mKey },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ElQadreNavy else Slate100,
                                border = BorderStroke(1.dp, if (isSelected) ElQadreNavy else Slate300),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("inversion_method_${mKey.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mLabel,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Slate700
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = originalAmountText,
                        onValueChange = { originalAmountText = it },
                        label = { Text("Monto ($currency)") },
                        placeholder = { Text("0.00", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("inversion_amount_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    OutlinedTextField(
                        value = usefulLifeText,
                        onValueChange = { usefulLifeText = it },
                        label = { Text("Cantidad (${if (method == "VIDA_UTIL") "Vida útil" else "Recuperación"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("inversion_life_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )
                }

                if (currency != "CUP") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Conversión (Tasa: 1 $currency = $exchangeRate CUP):", fontSize = 11.sp, color = Slate600)
                            Text(
                                "$${"%.2f".format(convertedAmtVal)} CUP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = usefulLifeUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unidad de tiempo") },
                        trailingIcon = {
                            IconButton(onClick = { expandedUnit = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { expandedUnit = true }.testTag("inversion_life_unit_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )
                    DropdownMenu(
                        expanded = expandedUnit,
                        onDismissRequest = { expandedUnit = false },
                        modifier = Modifier.fillMaxWidth(0.84f)
                    ) {
                        lifeUnits.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    usefulLifeUnit = unit
                                    expandedUnit = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text("Observación") },
                    modifier = Modifier.fillMaxWidth().testTag("inversion_obs_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    )
                )

                // Target Product (Optional)
                if (products.isNotEmpty()) {
                    var expandedTargetProduct by remember { mutableStateOf(false) }
                    val selectedTargetProduct = remember(targetProductId, products) {
                        products.find { it.id == targetProductId }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedTargetProduct?.name ?: "Todos los productos (Compartido)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Asociación / Producto Destino") },
                            trailingIcon = {
                                IconButton(onClick = { expandedTargetProduct = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedTargetProduct = true }
                                .testTag("inversion_target_product_trigger")
                        )
                        DropdownMenu(
                            expanded = expandedTargetProduct,
                            onDismissRequest = { expandedTargetProduct = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todos los productos (Compartido)", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    targetProductId = null
                                    expandedTargetProduct = false
                                }
                            )
                            HorizontalDivider()
                            products.filter { it.isAvailable }.forEach { p ->
                                val destLabel = if (p.destination == "BARRA") " [BARRA / MERCADERÍA]" else " [COCINA]"
                                DropdownMenuItem(
                                    text = { Text("${p.name}$destLabel", fontSize = 13.sp) },
                                    onClick = {
                                        targetProductId = p.id
                                        expandedTargetProduct = false
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElQadreGoldSoft.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, ElQadreGoldLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (method == "VIDA_UTIL") "VISTA PREVIA DE DEPRECIACIÓN" else "VISTA PREVIA DE RECUPERACIÓN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGoldDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (method == "VIDA_UTIL") "Depreciación Mensual:" else "Recuperación Mensual:", fontSize = 12.sp, color = Slate700)
                            Text(
                                "$${"%.2f".format(monthlyDep)} CUP/mes",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                fontSize = 12.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (method == "VIDA_UTIL") "Depreciación Diaria:" else "Monto Diario a Recuperar:", fontSize = 12.sp, color = Slate700)
                            Text(
                                "$${"%.4f".format(dailyDep)} CUP/día",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        val canSave = name.isNotBlank() && originalAmtVal > 0.0 && lifeVal > 0.0

                        Button(
                            onClick = {
                                if (canSave) {
                                    val inv = Inversion(
                                        id = inversion?.id ?: 0,
                                        name = name,
                                        category = category,
                                        amount = convertedAmtVal,
                                        date = date,
                                        usefulLife = lifeVal,
                                        usefulLifeUnit = usefulLifeUnit,
                                        observation = observation,
                                        targetProductId = targetProductId,
                                        currency = currency,
                                        originalAmount = originalAmtVal,
                                        exchangeRate = exchangeRate,
                                        convertedAmount = convertedAmtVal,
                                        scope = inversion?.scope ?: "PRODUCCION",
                                        method = method,
                                        dailyAmount = dailyDep
                                    )
                                    onConfirm(inv)
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                disabledContainerColor = Slate300,
                                disabledContentColor = Slate500
                            )
                        ) {
                            Text(
                                text = if (inversion == null) "REGISTRAR INVERSIÓN" else "ACTUALIZAR INVERSIÓN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
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

/**
 * BLOQUE EQUIVALENTE DE PRECIO DE VENTA PARA PRESENTACIÓN ESPECIAL
 * Muestra precio de referencia, precio en catálogo, campo para establecer precio,
 * botón fijar definitivo e información de ganancia y % de ganancia.
 */
@Composable
fun PresentacionEspecialPrecioBlock(
    pres: PresentacionEspecial,
    product: Product,
    costSheet: com.example.util.ProductCostSheet,
    viewModel: MainViewModel
) {
    val factor = pres.baseEquivalence
    val presMatPrima = costSheet.costoDirectoUnitario * factor
    val presGastos = costSheet.gastoIndirectoUnitario * factor
    val presPersonal = costSheet.totalPagoPersonalUnitario // El pago a personal no se multiplica por el factor (corresponde a 1 unidad simple)
    val presCostoTeorico = presMatPrima + presGastos + presPersonal

    // Precio de referencia sugerido (+30% margen de ganancia)
    val targetMargin = costSheet.productoElaborado?.targetMarginPct ?: 30.0
    val presPrecioReferencia = if (presCostoTeorico > 0.0) {
        presCostoTeorico * (1.0 + targetMargin / 100.0)
    } else 0.0

    // Precio establecido de la unidad simple
    val precioUnidadSimpleEstablecido = if (product.price > 0.0) {
        product.price
    } else if (costSheet.hasPrecioDefinitivo) {
        costSheet.precioDefinitivo
    } else {
        costSheet.precioReferencia
    }

    // Precio inicial sugerido por equivalencia: precio establecido unidad simple × número de unidades equivalentes
    val defaultPresPrice = precioUnidadSimpleEstablecido * factor

    // Precio en catálogo
    val hasPresDefinitivo = pres.price > 0.0
    val presPrecioCatalogo = if (hasPresDefinitivo) pres.price else defaultPresPrice
    val margenPresCatPct = if (presCostoTeorico > 0.0 && presPrecioCatalogo > 0.0) {
        ((presPrecioCatalogo - presCostoTeorico) / presCostoTeorico) * 100.0
    } else null

    // Campo ESTABLECER OTRO PRECIO (aparece inicialmente precio simple × número de unidades equivalentes, editable)
    var manualPresPriceText by remember(pres.name, pres.price, precioUnidadSimpleEstablecido, factor) {
        val initVal = if (pres.price > 0.0) pres.price else defaultPresPrice
        mutableStateOf(
            if (initVal % 1.0 == 0.0) initVal.toLong().toString() else "%.2f".format(initVal)
        )
    }

    val manualPresPrice = manualPresPriceText.toDoubleOrNull() ?: 0.0
    val manualPresMarginPct = if (presCostoTeorico > 0.0 && manualPresPrice > 0.0) {
        ((manualPresPrice - presCostoTeorico) / presCostoTeorico) * 100.0
    } else null
    val manualPresGanancia = if (presCostoTeorico > 0.0 && manualPresPrice > 0.0) {
        manualPresPrice - presCostoTeorico
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRESENTACIÓN ESPECIAL: ${pres.name.uppercase()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ElQadreGoldSoft
            ) {
                val eqStr = if (factor % 1.0 == 0.0) factor.toLong().toString() else "%.1f".format(factor)
                Text(
                    text = "= $eqStr ${costSheet.productionUnit}",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreGoldDark,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // PRECIO DE REFERENCIA Y PRECIO EN CATÁLOGO
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElQadreGoldSoft, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PRECIO DE REFERENCIA (+30% MARGEN)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Text(
                    "$${"%.2f".format(presPrecioReferencia)} CUP",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ElQadreNavy
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val catLabel = if (margenPresCatPct != null) {
                    "PRECIO EN CATÁLOGO (${if (margenPresCatPct >= 0) "+" else ""}${"%.1f".format(margenPresCatPct)}% GANANCIA)"
                } else {
                    "PRECIO EN CATÁLOGO"
                }
                Text(catLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Text(
                    "$${"%.2f".format(presPrecioCatalogo)} CUP",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (hasPresDefinitivo) Emerald700 else Slate600
                )
            }
        }

        // OPCIÓN A: USAR PRECIO DE REFERENCIA
        Button(
            onClick = {
                viewModel.setPresentacionEspecialDefinitivePrice(
                    productId = product.id,
                    presentationName = pres.name,
                    definitivePrice = presPrecioReferencia
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("use_pres_reference_price_button_${pres.name}")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Usar Precio de Referencia ($${"%.2f".format(presPrecioReferencia)} CUP)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // OPCIÓN B: ESTABLECER OTRO PRECIO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualPresPriceText,
                onValueChange = { manualPresPriceText = it },
                label = { Text("Establecer otro precio ($ CUP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("manual_pres_price_input_${pres.name}"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElQadreNavy,
                    focusedLabelColor = ElQadreNavy
                )
            )

            Button(
                onClick = {
                    val p = manualPresPriceText.toDoubleOrNull() ?: 0.0
                    if (p > 0.0) {
                        viewModel.setPresentacionEspecialDefinitivePrice(
                            productId = product.id,
                            presentationName = pres.name,
                            definitivePrice = p
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(52.dp).testTag("save_definitive_pres_price_button_${pres.name}")
            ) {
                Text("Fijar Definitivo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // INFORMACIÓN DE GANANCIA Y % DE GANANCIA
        if (manualPresPrice > 0.0 && manualPresMarginPct != null && manualPresGanancia != null) {
            Surface(
                color = if (manualPresMarginPct >= 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (manualPresMarginPct >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ganancia: $${"%.2f".format(manualPresGanancia)} CUP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (manualPresMarginPct >= 0) Emerald700 else Color(0xFFDC2626)
                    )
                    Text(
                        text = "% de ganancia: ${if (manualPresMarginPct >= 0) "+" else ""}${"%.1f".format(manualPresMarginPct)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (manualPresMarginPct >= 0) Emerald700 else Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

/**
 * DIÁLOGO: FICHA DE COSTO DE MERCADERÍAS (BARRA)
 * Implementa la estructura completa de cálculo:
 * Costo de Adquisición + Gastos Indirectos Prorrateados/Específicos + Depreciación Inversiones = Costo Real
 * Permite ajustar el Precio Definitivo y sincronizar con Catálogo.
 */
@Composable
fun FichaCostoMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val costSheet = remember(
        mercaderia,
        uiState.mercaderias,
        uiState.products,
        uiState.movimientosMercaderia,
        uiState.gastosGenerales,
        uiState.inversiones,
        uiState.productosElaborados,
        uiState.recetaIngredientes,
        uiState.materiasPrimas,
        uiState.tarifasPagoBebidas
    ) {
        CostCalculationHelper.calculateMercaderiaCostSheet(
            mercaderia = mercaderia,
            mercaderias = uiState.mercaderias,
            products = uiState.products,
            movimientos = uiState.movimientosMercaderia,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas,
            tarifasPagoBebidas = uiState.tarifasPagoBebidas
        )
    }

    var definitivePriceInput by remember(costSheet.precioDefinitivo, costSheet.precioReferencia) {
        mutableStateOf(
            if (costSheet.hasPrecioDefinitivo) "%.2f".format(costSheet.precioDefinitivo)
            else if (costSheet.precioReferencia > 0.0) "%.2f".format(costSheet.precioReferencia)
            else "0.00"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .fillMaxHeight(0.80f),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Slate200),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    val currentMerc = uiState.mercaderias.find { it.id == mercaderia.id } ?: mercaderia
                    val currentProd = uiState.products.find { it.id == currentMerc.productId }
                    val isMercActive = currentMerc.isActive

                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(44.dp).testTag("close_ficha_costo_mercaderia")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate700, modifier = Modifier.size(24.dp))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = ElQadreGoldDark,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "FICHA DE COSTO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = ElQadreNavy
                                )
                            }
                            Text(
                                text = "${costSheet.product.name} • ${costSheet.product.code} (Catálogo Barra)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                maxLines = 1
                            )
                        }

                        // ACTIVAR / DESACTIVAR MERCADERÍA DIRECTAMENTE
                        Surface(
                            onClick = {
                                val newActive = !isMercActive
                                viewModel.updateMercaderia(currentMerc.copy(isActive = newActive))
                                if (currentProd != null) {
                                    viewModel.updateProduct(currentProd.copy(isAvailable = newActive))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMercActive) Emerald50 else Rose50,
                            border = BorderStroke(1.5.dp, if (isMercActive) Emerald500 else Rose500),
                            modifier = Modifier.testTag("toggle_mercaderia_active_ficha")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isMercActive) Emerald600 else Rose600)
                                )
                                Text(
                                    text = if (isMercActive) "ACTIVO" else "INACTIVO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (isMercActive) Emerald700 else Rose700
                                )
                                Switch(
                                    checked = isMercActive,
                                    onCheckedChange = { isChecked ->
                                        viewModel.updateMercaderia(currentMerc.copy(isActive = isChecked))
                                        if (currentProd != null) {
                                            viewModel.updateProduct(currentProd.copy(isAvailable = isChecked))
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Emerald600,
                                        checkedTrackColor = Emerald100,
                                        uncheckedThumbColor = Slate400,
                                        uncheckedTrackColor = Slate200
                                    ),
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ElQadreBorderLight)

                // SCROLLABLE BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ==========================================
                    // SECCIÓN 1: INFORMACIÓN DEL PRODUCTO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "1. INFORMACIÓN DEL PRODUCTO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (costSheet.hasPrecioDefinitivo) Emerald100 else Rose100
                                ) {
                                    Text(
                                        text = if (costSheet.hasPrecioDefinitivo) "PRECIO DEFINITIVO EN CATÁLOGO" else "SIN PRECIO DEFINITIVO",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (costSheet.hasPrecioDefinitivo) Emerald700 else Rose700,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Producto", fontSize = 10.sp, color = Slate500)
                                    Text(costSheet.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                    Text("Código: ${costSheet.product.code}", fontSize = 11.sp, color = Slate600)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Presentación / Categoría", fontSize = 10.sp, color = Slate500)
                                    Text("${costSheet.product.category} (BARRA)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate700)
                                    Text("Unidad: ${mercaderia.unitOfMeasure}", fontSize = 11.sp, color = Slate600)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("Existencia Actual", fontSize = 10.sp, color = Slate500)
                                    Text(
                                        "${costSheet.currentStock} ${mercaderia.unitOfMeasure}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (costSheet.currentStock > 0) Emerald700 else Rose700
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Slate200)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("COSTO DE ADQUISICIÓN ORIGINAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(costSheet.acquisitionCost)} CUP / ${mercaderia.unitOfMeasure}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = ElQadreNavy
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("VALOR TOTAL EN STOCK", fontSize = 10.sp, color = Slate500)
                                    Text(
                                        "$${"%.2f".format(costSheet.totalAcquisitionValue)} CUP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate800
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("PARTICIPACIÓN EN PRORRATEO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                    Text(
                                        "${"%.2f".format(costSheet.porcentajeParticipacion)}%",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = ElQadreGoldDark
                                    )
                                }
                            }
                        }
                    }

                    // ==========================================
                    // APARTADO 2: PROMEDIO DE VENTA DIARIO
                    // ==========================================
                    var dailySalesAvgInput by remember(mercaderia.dailySalesAverage) {
                        mutableStateOf(if (mercaderia.dailySalesAverage > 0.0) "%.1f".format(mercaderia.dailySalesAverage) else "1.0")
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "2. PROMEDIO DE VENTA DIARIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Text(
                                "Introduzca la cantidad promedio de unidades vendidas diariamente para convertir el costo indirecto diario en costo indirecto por unidad.",
                                fontSize = 11.sp,
                                color = Slate600
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = dailySalesAvgInput,
                                    onValueChange = { clean ->
                                        dailySalesAvgInput = clean.filter { c -> c.isDigit() || c == '.' }
                                    },
                                    label = { Text("Promedio de Venta Diario") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mercaderia_daily_sales_avg_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                Button(
                                    onClick = {
                                        val newAvg = dailySalesAvgInput.toDoubleOrNull() ?: 1.0
                                        if (newAvg > 0.0) {
                                            viewModel.updateMercaderia(mercaderia.copy(dailySalesAverage = newAvg))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("save_mercaderia_daily_sales_avg_button")
                                ) {
                                    Text("Guardar Promedio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 3: EGRESOS POR RATEO ECONÓMICO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "3. EGRESOS - PRORRATEO ECONÓMICO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("GASTOS INDIRECTOS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate500)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("$${"%.2f".format(costSheet.gastosGeneralesDiariosTotales)} / día", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("DEPRECIACIÓN", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate500)
                                    Text("INVERSIONES", fontSize = 9.sp, color = Slate500)
                                    Text("$${"%.2f".format(costSheet.depreciacionInversionesDiariaTotales)} / día", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Slate700)
                                }
                                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                                    Text("COSTOS INDIRECTOS DEL DÍA", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate500, textAlign = TextAlign.End)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("$${"%.2f".format(costSheet.costosIndirectosDiariosTotales)} / día", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = ElQadreNavy)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ASIGNACIÓN DIRECTA POR DÍA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(
                                        "$${"%.2f".format(costSheet.totalCostosIndirectosAsignados)} CUP / día",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ElQadreNavy
                                    )
                                    Text("(Costos ind. × ${"%.2f".format(costSheet.porcentajeParticipacion)}% part.)", fontSize = 9.sp, color = Slate500)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("GASTO UNITARIO INDIRECTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark, textAlign = TextAlign.End)
                                    Text(
                                        "$${"%.2f".format(costSheet.gastoIndirectoUnitario)} CUP / ud",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = ElQadreGoldDark
                                    )
                                    Text("(Asignación diaria ÷ prom. venta)", fontSize = 9.sp, color = Slate500, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 4: ESTRUCTURA DEL COSTO UNITARIO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElQadreGoldSoft.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, ElQadreGold)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "4. ESTRUCTURA DEL COSTO UNITARIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )

                            // Bloque 1: Costo Directo Unitario = Costo de Adquisición + Gastos Directos
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate300)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Costo Adquisición", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        Text("$${"%.2f".format(costSheet.acquisitionCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate800)
                                    }
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate500)
                                    Column {
                                        Text("Gastos Directos", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        Text("$${"%.2f".format(costSheet.directExpenses)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate800)
                                    }
                                    Text("=", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("COSTO DIRECTO UNITARIO", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                        Text("$${"%.2f".format(costSheet.costoDirectoUnitario)} CUP", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = ElQadreNavy)
                                    }
                                }
                            }

                            // Bloque 2: Estructura del costo unitario final
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElQadreNavy,
                                border = BorderStroke(1.dp, ElQadreNavy)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "ESTRUCTURA DE COSTO UNITARIO FINAL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreGold
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• Costo Directo Unitario:", fontSize = 11.sp, color = Color.White)
                                        Text("$${"%.2f".format(costSheet.costoDirectoUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• Gasto Unitario Indirecto:", fontSize = 11.sp, color = Color.White)
                                        Text("$${"%.4f".format(costSheet.gastoIndirectoUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("• Pago a Personal por Unidad:", fontSize = 11.sp, color = Color.White)
                                        Text("$${"%.2f".format(costSheet.totalPagoPersonalUnitario)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    HorizontalDivider(color = Slate600, modifier = Modifier.padding(vertical = 2.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "COSTO UNITARIO FINAL",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElQadreGold
                                        )
                                        Text(
                                            text = "$${"%.2f".format(costSheet.costoTotalUnitario)} CUP",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElQadreGold
                                        )
                                    }
                                }
                            }

                            Text(
                                "Nota: El costo de adquisición original ($${"%.2f".format(costSheet.acquisitionCost)} CUP) y sus gastos directos se conservan intactos en el inventario.",
                                fontSize = 9.5.sp,
                                color = Slate600,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    // ==========================================
                    // SECCIÓN 5: PRECIO DE REFERENCIA Y PRECIO DEFINITIVO
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ElQadreBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "5. PRECIO DE REFERENCIA Y PRECIO DEFINITIVO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Margen de Referencia", fontSize = 10.sp, color = Slate500)
                                    Text("+${costSheet.targetMarginPct.toInt()}% sobre Costo Unitario Final", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate700)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("PRECIO DE REFERENCIA SUGERIDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(costSheet.precioReferencia)} CUP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate800
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Slate200)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = definitivePriceInput,
                                    onValueChange = { definitivePriceInput = it },
                                    label = { Text("Precio Definitivo en Catálogo (CUP)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mercaderia_definitive_price_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                Button(
                                    onClick = {
                                        val newDefPrice = definitivePriceInput.toDoubleOrNull() ?: 0.0
                                        if (newDefPrice > 0.0) {
                                            viewModel.setMercaderiaDefinitivePrice(
                                                productId = costSheet.product.id,
                                                definitivePrice = newDefPrice,
                                                calculatedRealCost = costSheet.costoRealUnitario
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("save_mercaderia_definitive_price_button")
                                ) {
                                    Text("Actualizar Catálogo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN: ANÁLISIS DE RENTABILIDAD Y UTILIDAD REAL
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (costSheet.utilidadUnitaria >= 0) Emerald50 else Rose50,
                        border = BorderStroke(1.dp, if (costSheet.utilidadUnitaria >= 0) Emerald500 else Rose500)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "ANÁLISIS DE RENTABILIDAD Y UTILIDAD REAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (costSheet.utilidadUnitaria >= 0) Emerald700 else Rose700
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("UTILIDAD REAL UNITARIA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "${if (costSheet.utilidadUnitaria >= 0) "+" else ""}$${"%.2f".format(costSheet.utilidadUnitaria)} CUP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = if (costSheet.utilidadUnitaria >= 0) Emerald700 else Rose700
                                    )
                                    Text("Precio Def. - Costo Unitario Final", fontSize = 9.sp, color = Slate500)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("MARGEN DE GANANCIA REAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "${"%.2f".format(costSheet.margenPorcentual)}%",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = if (costSheet.margenPorcentual >= 0) Emerald700 else Rose700
                                    )
                                    Text("Utilidad / Precio Definitivo", fontSize = 9.sp, color = Slate500)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("UTILIDAD PROYECTADA TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(costSheet.totalUtilidadProyectada)} CUP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (costSheet.totalUtilidadProyectada >= 0) Emerald700 else Rose700
                                    )
                                    Text("En existencias (${costSheet.currentStock} u)", fontSize = 9.sp, color = Slate500)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // FOOTER ACTIONS: ÚNICAMENTE DESCARGAR PDF Y CERRAR
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            CostSheetPdfExporter.exportSingleMercaderiaCostSheet(context, mercaderia, uiState)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_export_pdf_ficha_mercaderia"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ElQadreNavy),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = Rose700)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DESCARGAR FICHA EN PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier
                            .weight(0.7f)
                            .height(50.dp)
                            .testTag("close_ficha_costo_mercaderia_footer"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CERRAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    }
}

