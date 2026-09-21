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
    val costSheet = remember(
        product,
        uiState.products,
        uiState.productosElaborados,
        uiState.recetaIngredientes,
        uiState.materiasPrimas,
        uiState.gastosGenerales,
        uiState.inversiones
    ) {
        CostCalculationHelper.calculateCostSheet(
            product = product,
            products = uiState.products,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones
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
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = ElQadreGoldDark,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "FICHA DE COSTO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = ElQadreNavy
                                )
                            }
                            Text(
                                text = "${product.name} (Catálogo Cocina)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
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
                                Column {
                                    Text("PPD Total Cocina", fontSize = 10.sp, color = Slate500)
                                    Text("${if (costSheet.totalKitchenPpd % 1.0 == 0.0) costSheet.totalKitchenPpd.toLong().toString() else "%.1f".format(costSheet.totalKitchenPpd)} ud/día", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate700)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Participación Productiva", fontSize = 10.sp, color = Slate500)
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
                                "5. EGRESOS - PRORRATEO DINÁMICO DE COSTOS INDIRECTOS",
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
                                    Text("Asignación Indirecta (${"%.2f".format(costSheet.porcentajeParticipacionPpd)}% PPD)", fontSize = 10.sp, color = Slate500)
                                    Text("G. Gen: $${"%.2f".format(costSheet.gastoGeneralAsignado)} | Inv: $${"%.2f".format(costSheet.depreciacionAsignada)}", fontSize = 10.5.sp, color = Slate700)
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

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEEF2FF),
                                    border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                ) {
                                    Text(
                                        text = "$${"%.2f".format(costSheet.totalPagoPersonalUnitario)} CUP / ud",
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("COCINA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        val cocinaUnit = pagoCocinaText.toDoubleOrNull() ?: 0.0
                                        val cocinerosCount = cantidadCocinerosText.toIntOrNull() ?: 1
                                        val subtotalCocina = cocinaUnit * (if (cocinerosCount > 0) cocinerosCount else 1)
                                        Text(
                                            "Subtotal: $${"%.2f".format(subtotalCocina)} CUP / ud",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

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
                                        pagoCajeroUnitario = cajeroVal
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
                                    "Los costos y pagos de personal se calculan proporcionalmente según la equivalencia configurada para cada presentación:",
                                    fontSize = 10.5.sp,
                                    color = Slate600
                                )

                                presentaciones.forEach { pres ->
                                    val factor = pres.baseEquivalence
                                    val presMatPrima = costSheet.costoDirectoUnitario * factor
                                    val presGastos = costSheet.gastoIndirectoUnitario * factor
                                    val presPersonal = costSheet.totalPagoPersonalUnitario * factor
                                    val presCostoTotal = costSheet.costoRealUnitario * factor
                                    val presPrecioRef = costSheet.precioReferencia * factor

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
                    // SECCIÓN 8: COSTO TEÓRICO / COSTO REAL UNITARIO (CRU)
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElQadreNavy,
                        border = BorderStroke(1.dp, ElQadreNavy)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "8. COSTO TEÓRICO UNITARIO (CRU)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGold
                            )

                            // Desglose de 4 componentes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Materias Primas", fontSize = 9.5.sp, color = Slate400)
                                    Text("$${"%.2f".format(costSheet.costoDirectoUnitario)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text("Gastos Generales", fontSize = 9.5.sp, color = Slate400)
                                    Text("$${"%.2f".format(costSheet.gastoIndirectoUnitario)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text("Pagos Personal", fontSize = 9.5.sp, color = Slate400)
                                    Text("$${"%.2f".format(costSheet.totalPagoPersonalUnitario)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Costo Total Resultante",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Por ${costSheet.productionUnit} producida/vendida",
                                        fontSize = 10.5.sp,
                                        color = Slate300
                                    )
                                }

                                Text(
                                    text = "$${"%.2f".format(costSheet.costoRealUnitario)} CUP",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreGold
                                )
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
                                    Text("PRECIO EN CATÁLOGO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text(
                                        "$${"%.2f".format(product.price)} CUP",
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
                                        productId = product.id,
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
                                                productId = product.id,
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
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ElQadreBorderLight)

                // BOTTOM ACTION
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
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
                                .weight(1f)
                                .height(54.dp)
                                .testTag("download_pdf_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF INDIVIDUAL", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { CostSheetPdfExporter.exportAllCostSheets(context, uiState) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, ElQadreGoldDark),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("download_all_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF TODAS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.9f)
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
    var usefulLifeUnit by remember { mutableStateOf(inversion?.usefulLifeUnit ?: "MESES") }
    var observation by remember { mutableStateOf(inversion?.observation ?: "") }
    var targetProductId by remember { mutableStateOf<Long?>(inversion?.targetProductId) }

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
    val lifeUnits = listOf("DÍAS", "SEMANAS", "MESES", "AÑOS")

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedUnit by remember { mutableStateOf(false) }

    val originalAmtVal = originalAmountText.toDoubleOrNull() ?: 0.0
    val convertedAmtVal = originalAmtVal * exchangeRate
    val lifeVal = usefulLifeText.toDoubleOrNull() ?: 1.0

    val dailyDep = if (convertedAmtVal > 0.0 && lifeVal > 0.0) {
        when (usefulLifeUnit.uppercase()) {
            "DÍAS", "DIAS", "DÍA", "DIA" -> convertedAmtVal / lifeVal
            "SEMANAS", "SEMANA" -> convertedAmtVal / (lifeVal * 7.0)
            "MESES", "MES" -> convertedAmtVal / (lifeVal * 30.0)
            "AÑOS", "AÑO", "ANUAL" -> convertedAmtVal / (lifeVal * 365.0)
            else -> (convertedAmtVal / lifeVal) / 30.0
        }
    } else 0.0

    val monthlyDep = dailyDep * 30.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 660.dp)
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
                        label = { Text("Vida Útil") },
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
                        label = { Text("Unidad de Vida Útil") },
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
                            text = "VISTA PREVIA DE DEPRECIACIÓN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGoldDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Depreciación Mensual:", fontSize = 12.sp, color = Slate700)
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
                            Text("Costo Diario Equivalente:", fontSize = 12.sp, color = Slate700)
                            Text(
                                "$${"%.4f".format(dailyDep)} CUP/día",
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGoldDark,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate600),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && originalAmtVal > 0.0 && lifeVal > 0.0) {
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
                                    scope = inversion?.scope ?: "PRODUCCION"
                                )
                                onConfirm(inv)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        enabled = name.isNotBlank() && originalAmtVal > 0.0 && lifeVal > 0.0
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
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
        uiState.inversiones
    ) {
        CostCalculationHelper.calculateMercaderiaCostSheet(
            mercaderia = mercaderia,
            mercaderias = uiState.mercaderias,
            products = uiState.products,
            movimientos = uiState.movimientosMercaderia,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .background(Color.Transparent),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = ElQadreGoldDark,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ficha de Costo de Mercadería",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = ElQadreNavy
                            )
                        }
                        Text(
                            text = "${costSheet.product.name} • ${costSheet.product.code} (Catálogo Barra)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate600
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_ficha_costo_mercaderia")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
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
                    // SECCIÓN 1: IDENTIFICACIÓN Y DATOS DE COMPRA
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
                                    "1. IDENTIFICACIÓN Y ADQUISICIÓN",
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
                                    Text("Artículo", fontSize = 10.sp, color = Slate500)
                                    Text(costSheet.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                                    Text("Código: ${costSheet.product.code}", fontSize = 11.sp, color = Slate600)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Categoría / Destino", fontSize = 10.sp, color = Slate500)
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
                    // SECCIÓN 2: GASTOS INDIRECTOS ASIGNADOS
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ElQadreBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "2. GASTOS INDIRECTOS ASIGNADOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    "Total Gastos: $${"%.2f".format(costSheet.totalGastosAsignados)} CUP/día",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGoldDark
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (costSheet.detailedExpenses.isEmpty()) {
                                Text(
                                    "No hay gastos indirectos activos asignados a este producto.",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    costSheet.detailedExpenses.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isSpecific) Amber50 else Slate50,
                                            border = BorderStroke(1.dp, if (item.isSpecific) Amber500.copy(alpha = 0.5f) else Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1.5f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            item.name,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = ElQadreNavy
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = if (item.isSpecific) Amber100 else ElQadreNavy.copy(alpha = 0.1f)
                                                        ) {
                                                            Text(
                                                                text = if (item.isSpecific) "ASIGNACIÓN DIRECTA" else "PRORRATEADO",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (item.isSpecific) Amber800 else ElQadreNavy,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "${item.category} • Original: $${"%.2f".format(item.originalAmount)} (${item.period.lowercase()}) → $${"%.2f".format(item.dailyEquivalent)}/día",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "$${"%.2f".format(item.allocatedDailyAmount)} CUP/día",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ElQadreNavy
                                                    )
                                                    Text(
                                                        "+$${"%.4f".format(item.allocatedUnitAmount)} / u",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = ElQadreGoldDark
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 3: DEPRECIACIÓN DE INVERSIONES
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, ElQadreBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "3. DEPRECIACIÓN DE INVERSIONES (ACTIVOS)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    "Total Deprec.: $${"%.2f".format(costSheet.totalDepreciacionAsignada)} CUP/día",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGoldDark
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (costSheet.detailedInversions.isEmpty()) {
                                Text(
                                    "No hay inversiones activas registradas.",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    costSheet.detailedInversions.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isSpecific) Amber50 else Slate50,
                                            border = BorderStroke(1.dp, if (item.isSpecific) Amber500.copy(alpha = 0.5f) else Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1.5f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            item.name,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = ElQadreNavy
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = if (item.isSpecific) Amber100 else ElQadreNavy.copy(alpha = 0.1f)
                                                        ) {
                                                            Text(
                                                                text = if (item.isSpecific) "ASIGNACIÓN DIRECTA" else "PRORRATEADO",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (item.isSpecific) Amber800 else ElQadreNavy,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "${item.category} • Inversión: $${"%.2f".format(item.originalAmount)} • Vida útil: ${item.usefulLifeText}",
                                                        fontSize = 10.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "$${"%.2f".format(item.allocatedDailyAmount)} CUP/día",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ElQadreNavy
                                                    )
                                                    Text(
                                                        "+$${"%.4f".format(item.allocatedUnitAmount)} / u",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = ElQadreGoldDark
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // SECCIÓN 4 & 5: RESUMEN Y CÁLCULO DEL COSTO REAL
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElQadreGoldSoft.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, ElQadreGold)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "4. FÓRMULA Y CÁLCULO DEL COSTO REAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("COSTO ADQUISICIÓN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    Text("$${"%.2f".format(costSheet.acquisitionCost)}", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ElQadreNavy)
                                }
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate500)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("GASTOS IND. UNIT.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    val unitGastos = if (costSheet.currentStock > 0) costSheet.totalGastosAsignados / costSheet.currentStock else costSheet.totalGastosAsignados
                                    Text("$${"%.2f".format(unitGastos)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate700)
                                }
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate500)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("DEPREC. UNIT.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                    val unitDep = if (costSheet.currentStock > 0) costSheet.totalDepreciacionAsignada / costSheet.currentStock else costSheet.totalDepreciacionAsignada
                                    Text("$${"%.2f".format(unitDep)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate700)
                                }
                                Text("=", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("COSTO REAL UNITARIO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                                    Text(
                                        "$${"%.2f".format(costSheet.costoRealUnitario)} CUP",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = ElQadreGoldDark
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nota: El costo de adquisición original ($${"%.2f".format(costSheet.acquisitionCost)} CUP) se conserva intacto en la base de datos.",
                                fontSize = 10.sp,
                                color = Slate600,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    // ==========================================
                    // SECCIÓN 6: PRECIO DE REFERENCIA Y PRECIO DEFINITIVO
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
                                    Text("+${costSheet.targetMarginPct.toInt()}% sobre Costo Real", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Slate700)
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
                    // SECCIÓN 7: ANÁLISIS DE RENTABILIDAD Y UTILIDAD REAL
                    // ==========================================
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (costSheet.utilidadUnitaria >= 0) Emerald50 else Rose50,
                        border = BorderStroke(1.dp, if (costSheet.utilidadUnitaria >= 0) Emerald500 else Rose500)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "6. ANÁLISIS DE RENTABILIDAD Y UTILIDAD REAL",
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
                                    Text("Precio Def. - Costo Real", fontSize = 9.sp, color = Slate500)
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

                // FOOTER ACTIONS
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            CostSheetPdfExporter.exportSingleMercaderiaCostSheet(context, mercaderia, uiState)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_export_pdf_ficha_mercaderia"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ElQadreNavy),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = Rose700)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descargar PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            CostSheetPdfExporter.exportAllMercaderiasCostSheetsZip(context, uiState)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_export_zip_fichas_mercaderia"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ElQadreGoldDark),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreGoldDark)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp), tint = ElQadreGoldDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Todas (ZIP)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                            .testTag("close_ficha_costo_mercaderia_footer"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cerrar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

