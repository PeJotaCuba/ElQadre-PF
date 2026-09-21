package com.example.ui.screens.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.*
import com.example.ui.screens.dueno.RegisterTandaDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UnitConverter
import com.example.util.CocinaTandasScanResult
import com.example.util.CostCalculationHelper
import com.example.util.SmsTandasHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TandasPane(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var showAgregarTandaDialog by remember { mutableStateOf(false) }
    var selectedTandaForDetail by remember { mutableStateOf<Tanda?>(null) }
    var selectedTandaForEdit by remember { mutableStateOf<Tanda?>(null) }
    var selectedTandaForClose by remember { mutableStateOf<Tanda?>(null) }
    var importFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var importFeedbackTitle by remember { mutableStateOf("Importar Tandas") }

    val handleImportarTandasClick: () -> Unit = {
        val cocinaPhones = mutableListOf<String>()
        uiState.users.filter { it.role == UserRole.COCINA && it.telefono.isNotBlank() }.forEach { cocinaPhones.add(it.telefono) }
        uiState.personalContratado.filter { it.role.equals("COCINA", ignoreCase = true) && it.movil.isNotBlank() }.forEach { cocinaPhones.add(it.movil) }
        val distinctCocinaPhones = cocinaPhones.distinct()
        val currentNeg = uiState.businessConfig?.codigoNegocio ?: "NEG-000001"
        val existingUuids = uiState.tandas.map { it.uuid }.toSet()

        val scanResult = SmsTandasHelper.scanInboxForCocinaTandas(
            context = context,
            cocinaPhoneNumbers = distinctCocinaPhones,
            currentCodigoNegocio = currentNeg,
            existingTandaUuids = existingUuids
        )

        when (scanResult) {
            is CocinaTandasScanResult.Success -> {
                viewModel.importarTandasDesdeCocina(
                    tandas = scanResult.newTandas,
                    onSuccess = { count ->
                        importFeedbackTitle = "Importación Exitosa"
                        importFeedbackMessage = "Se importaron exitosamente $count tanda(s) desde Cocina (${scanResult.senderPhone})."
                    },
                    onError = { err ->
                        importFeedbackTitle = "Error de Importación"
                        importFeedbackMessage = err
                    }
                )
            }
            is CocinaTandasScanResult.AllAlreadyImported -> {
                importFeedbackTitle = "Tandas Ya Registradas"
                importFeedbackMessage = "Las ${scanResult.totalInSms} tanda(s) del SMS de Cocina (${scanResult.senderPhone}) ya se encontraban previamente importadas."
            }
            is CocinaTandasScanResult.BusinessMismatch -> {
                importFeedbackTitle = "Negocio No Coincide"
                importFeedbackMessage = "El mensaje SMS de Cocina pertenece a otro negocio (${scanResult.actual}) y no al negocio activo ($currentNeg). No se importaron datos."
            }
            is CocinaTandasScanResult.InvalidFormat -> {
                importFeedbackTitle = "Formato de SMS Inválido"
                importFeedbackMessage = "El mensaje recibido desde Cocina está corrupto o incompleto: ${scanResult.reason}"
            }
            is CocinaTandasScanResult.NoSmsFound -> {
                // Flujo alternativo: si no hay SMS válido, abrir directamente el formulario ACTIVAR NUEVA TANDA
                showAgregarTandaDialog = true
            }
        }
    }

    // Categorized Tandas
    val activeTandas = remember(uiState.tandas) {
        uiState.tandas.filter { it.status == "ACTIVA" || it.status == "ACTIVADA" || it.status == "ABIERTA" }
    }
    val closedTandas = remember(uiState.tandas) {
        uiState.tandas.filter { it.status == "CERRADA" }
    }
    val tandasJornada = remember(uiState.tandas) { uiState.tandas }

    // Consolidated Summary Calculations
    val totalTandasCount = tandasJornada.size
    val totalProducidoReal = remember(tandasJornada) { tandasJornada.sumOf { it.actualYield.ifZeroUse(it.estimatedYield) } }
    val totalCostoProduccion = remember(tandasJornada) { tandasJornada.sumOf { it.totalBatchCost } }
    val totalIngresoEsperado = remember(tandasJornada, uiState.products) {
        val prodMap = uiState.products.associateBy { it.id }
        tandasJornada.sumOf { tanda ->
            val catalogPrice = prodMap[tanda.productId]?.price ?: 0.0
            if (tanda.expectedRevenue > 0.0) tanda.expectedRevenue else catalogPrice * (tanda.actualYield.ifZeroUse(tanda.estimatedYield))
        }
    }
    val totalGanancia = totalIngresoEsperado - totalCostoProduccion

    // Cost Breakdown for Pie Chart
    val totalMP = remember(tandasJornada) { tandasJornada.sumOf { it.totalDirectIngredientsCost } }
    val totalMO = remember(tandasJornada) { tandasJornada.sumOf { it.totalLaborCost } }
    val totalGI = remember(tandasJornada) { tandasJornada.sumOf { it.totalIndirectCostAllocated } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action Bar & Header
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ElQadreBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Factory, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                            Text(
                                text = "GESTIÓN DE TANDAS Y PRODUCCIÓN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )
                        }
                        Text(
                            text = "Flujo de activación por cantidad base, descuento inmediato y cierre por producción real",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = Slate600
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = ElQadreNavy,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$totalTandasCount Tandas Registradas",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = handleImportarTandasClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGoldDark),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .testTag("importar_tandas_button")
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORTAR TANDAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showAgregarTandaDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .testTag("agregar_tanda_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AGREGAR TANDA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ==========================================
        // 4. SECCIÓN TANDAS ACTIVAS (Altura reducida a ~1/3)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Amber600),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = Amber700, modifier = Modifier.size(16.dp))
                        Text(
                            text = "TANDAS ACTIVAS",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )
                    }

                    Surface(
                        color = Amber50,
                        border = BorderStroke(1.dp, Amber600),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${activeTandas.size} En Proceso",
                            color = Amber700,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (activeTandas.isEmpty()) {
                    Surface(
                        color = Amber50.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                            Text(
                                text = "No hay tandas activas actualmente",
                                fontWeight = FontWeight.Bold,
                                color = Slate800,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeTandas.forEach { tanda ->
                            ActiveTandaCard(
                                tanda = tanda,
                                products = uiState.products,
                                onEdit = { selectedTandaForEdit = tanda },
                                onCloseTanda = { selectedTandaForClose = tanda },
                                onClickDetail = { selectedTandaForDetail = tanda }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7. REGISTRO CONSOLIDADO Y HISTORIAL DE TANDAS
        // ==========================================
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ElQadreBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REGISTRO CONSOLIDADO DE TANDAS (JORNADA)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = ElQadreNavy
                )

                if (tandasJornada.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate50),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = Slate400, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No hay tandas registradas en la jornada", fontWeight = FontWeight.Bold, color = Slate600, fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tandasJornada.forEach { tanda ->
                            TandaTableRowCard(
                                tanda = tanda,
                                products = uiState.products,
                                onClickDetail = { selectedTandaForDetail = tanda },
                                onCloseTanda = if (tanda.status != "CERRADA") { { selectedTandaForClose = tanda } } else null,
                                onEditTanda = if (tanda.status != "CERRADA") { { selectedTandaForEdit = tanda } } else null
                            )
                        }
                    }
                }
            }
        }

        // Resumen General y Análisis Gráfico
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Slate50,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RESUMEN GENERAL DE LA JORNADA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                    Text(
                        "$totalTandasCount Tandas Totales",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                }

                HorizontalDivider(color = Slate200)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Producción", fontSize = 10.sp, color = Slate500)
                        Text("${totalProducidoReal.toInt()} Unidades", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                    }
                    Column {
                        Text("Costo Total", fontSize = 10.sp, color = Slate500)
                        Text("$${"%.2f".format(totalCostoProduccion)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Rose700)
                    }
                    Column {
                        Text("Ingreso Esperado", fontSize = 10.sp, color = Slate500)
                        Text("$${"%.2f".format(totalIngresoEsperado)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Emerald700)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ganancia", fontSize = 10.sp, color = Slate500)
                        Text("$${"%.2f".format(totalGanancia)} CUP", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = ElQadreGoldDark)
                    }
                }

                // Pie Chart of Cost Composition
                if (totalCostoProduccion > 0) {
                    HorizontalDivider(color = Slate200)
                    Text("COMPOSICIÓN DEL COSTO DE PRODUCCIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val total = totalCostoProduccion.toFloat()
                                if (total > 0f) {
                                    val sweepMP = (totalMP.toFloat() / total) * 360f
                                    val sweepMO = (totalMO.toFloat() / total) * 360f
                                    val sweepGI = (totalGI.toFloat() / total) * 360f

                                    var startAngle = -90f

                                    drawArc(
                                        color = Color(0xFF0284C7),
                                        startAngle = startAngle,
                                        sweepAngle = sweepMP,
                                        useCenter = false,
                                        style = Stroke(width = 20.dp.toPx())
                                    )
                                    startAngle += sweepMP

                                    drawArc(
                                        color = Color(0xFFD97706),
                                        startAngle = startAngle,
                                        sweepAngle = sweepMO,
                                        useCenter = false,
                                        style = Stroke(width = 20.dp.toPx())
                                    )
                                    startAngle += sweepMO

                                    drawArc(
                                        color = Color(0xFF4B5563),
                                        startAngle = startAngle,
                                        sweepAngle = sweepGI,
                                        useCenter = false,
                                        style = Stroke(width = 20.dp.toPx())
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("COSTO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                Text("$${"%.0f".format(totalCostoProduccion)}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val pctMP = if (totalCostoProduccion > 0) (totalMP / totalCostoProduccion) * 100 else 0.0
                            val pctMO = if (totalCostoProduccion > 0) (totalMO / totalCostoProduccion) * 100 else 0.0
                            val pctGI = if (totalCostoProduccion > 0) (totalGI / totalCostoProduccion) * 100 else 0.0

                            LegendBadge(color = Color(0xFF0284C7), label = "Insumos", amount = totalMP, percentage = pctMP)
                            LegendBadge(color = Color(0xFFD97706), label = "Mano de Obra", amount = totalMO, percentage = pctMO)
                            LegendBadge(color = Color(0xFF4B5563), label = "Costos Indirectos", amount = totalGI, percentage = pctGI)
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs
    if (showAgregarTandaDialog) {
        RegisterTandaDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showAgregarTandaDialog = false }
        )
    }

    importFeedbackMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { importFeedbackMessage = null },
            title = {
                Text(
                    text = importFeedbackTitle,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = { importFeedbackMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Aceptar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    selectedTandaForEdit?.let { tanda ->
        EditarTandaDialog(
            tanda = tanda,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedTandaForEdit = null }
        )
    }

    selectedTandaForClose?.let { tanda ->
        CerrarTandaDialog(
            tanda = tanda,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedTandaForClose = null }
        )
    }

    selectedTandaForDetail?.let { tanda ->
        TandaDetailDialog(
            tanda = tanda,
            uiState = uiState,
            onDismiss = { selectedTandaForDetail = null }
        )
    }
}

// =================================================================
// COMPONENT: CARD DE TANDA ACTIVA (CON RESULTADO PROVISIONAL Y BARRAS)
// =================================================================
@Composable
fun ActiveTandaCard(
    tanda: Tanda,
    products: List<Product>,
    onEdit: () -> Unit,
    onCloseTanda: () -> Unit,
    onClickDetail: () -> Unit
) {
    val catalogPrice = remember(tanda.productId, products) {
        products.find { it.id == tanda.productId }?.price ?: 0.0
    }
    val expectedRevenueVal = if (tanda.expectedRevenue > 0.0) tanda.expectedRevenue else (tanda.estimatedYield * catalogPrice)
    val expectedProfitVal = if (tanda.estimatedProfit != 0.0) tanda.estimatedProfit else (expectedRevenueVal - tanda.totalBatchCost)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDetail() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Amber600),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Tanda #, Product, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        color = Amber100,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Tanda ${tanda.tandaNumber.ifEmpty { "01" }}",
                            color = Amber700,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = tanda.productName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = ElQadreNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                        border = BorderStroke(1.dp, ElQadreNavy),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("editar_tanda_button_${tanda.uuid}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("EDITAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onCloseTanda,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("cerrar_tanda_button_${tanda.uuid}")
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("CERRAR TANDA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Grid of Provisional Values
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Amber50.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Base Utilizada", fontSize = 9.sp, color = Slate500)
                    Text(
                        "${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Slate800
                    )
                }

                Column {
                    Text("Prod. Esperada", fontSize = 9.sp, color = Slate500)
                    Text(
                        "${tanda.estimatedYield.toInt()} ${tanda.productionUnit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ElQadreNavy
                    )
                }

                Column {
                    Text("Costo Prov.", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(tanda.totalBatchCost)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Rose700
                    )
                }

                Column {
                    Text("Ingreso Esperado", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(expectedRevenueVal)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Emerald700
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Utilidad Esperada", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(expectedProfitVal)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = ElQadreGoldDark
                    )
                }
            }
        }
    }
}

// =================================================================
// TABLE ROW CARD FOR CONSOLIDATED REGISTER
// =================================================================
@Composable
fun TandaTableRowCard(
    tanda: Tanda,
    products: List<Product>,
    onClickDetail: () -> Unit,
    onCloseTanda: (() -> Unit)? = null,
    onEditTanda: (() -> Unit)? = null
) {
    val catalogPrice = remember(tanda.productId, products) {
        products.find { it.id == tanda.productId }?.price ?: 0.0
    }

    val isClosed = tanda.status == "CERRADA"
    val expectedYieldVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
    val actualYieldVal = if (isClosed) tanda.actualYield else expectedYieldVal

    val rendimientoPct = if (expectedYieldVal > 0.0) (actualYieldVal / expectedYieldVal) * 100.0 else 100.0
    val ingresoEsperadoVal = if (tanda.expectedRevenue > 0.0) tanda.expectedRevenue else (actualYieldVal * catalogPrice)
    val gananciaVal = if (tanda.estimatedProfit != 0.0) tanda.estimatedProfit else (ingresoEsperadoVal - tanda.totalBatchCost)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickDetail() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isClosed) ElQadreBorderLight else Amber600),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (isClosed) ElQadreGoldSoft else Amber100,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Tanda ${tanda.tandaNumber.ifEmpty { "01" }}",
                            color = ElQadreNavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = tanda.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = if (isClosed) Emerald50 else Amber50,
                    border = BorderStroke(1.dp, if (isClosed) Emerald600 else Amber600),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isClosed) "CERRADA / DEFINITIVA" else "ACTIVA / PROVISIONAL",
                        color = if (isClosed) Emerald600 else Amber700,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate50, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(if (isClosed) "Esperado → Real" else "Esperado", fontSize = 9.sp, color = Slate500)
                    Text(
                        if (isClosed) "${expectedYieldVal.toInt()} → ${actualYieldVal.toInt()} ${tanda.productionUnit}"
                        else "${expectedYieldVal.toInt()} ${tanda.productionUnit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Slate800
                    )
                }

                Column {
                    Text("Rendimiento", fontSize = 9.sp, color = Slate500)
                    Text(
                        if (isClosed) "${"%.1f".format(rendimientoPct)}%" else "Provisional",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = if (!isClosed) Amber700 else if (rendimientoPct >= 95.0) Emerald600 else if (rendimientoPct >= 85.0) Amber700 else Rose600
                    )
                }

                Column {
                    Text(if (isClosed) "Costo Real" else "Costo Prov.", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(tanda.totalBatchCost)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Rose700
                    )
                }

                Column {
                    Text("Ingreso", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(ingresoEsperadoVal)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Emerald700
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Ganancia", fontSize = 9.sp, color = Slate500)
                    Text(
                        "$${"%.2f".format(gananciaVal)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = ElQadreGoldDark
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Base: ${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit} (${tanda.baseMateriaPrimaName})",
                    fontSize = 10.sp,
                    color = Slate500
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isClosed && onEditTanda != null) {
                        TextButton(onClick = onEditTanda, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("EDITAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                    }
                    if (!isClosed && onCloseTanda != null) {
                        TextButton(onClick = onCloseTanda, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("CERRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald600)
                        }
                    }

                    TextButton(
                        onClick = onClickDetail,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("DETALLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElQadreNavy)
                    }
                }
            }
        }
    }
}

// =================================================================
// 1 & 2. MODAL PARA CREAR / ACTIVAR TANDA (SOLO PRODUCTO Y CANTIDAD BASE)
// =================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarTandaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val elaboratedProducts = remember(uiState.productosElaborados, uiState.products) {
        uiState.productosElaborados.mapNotNull { pe ->
            uiState.products.find { it.id == pe.productId }?.let { p -> Pair(pe, p) }
        }
    }

    var selectedProductPair by remember { mutableStateOf<Pair<ProductoElaborado, Product>?>(null) }
    var tandaNumberText by remember { mutableStateOf("01") }
    var baseQuantityInputText by remember { mutableStateOf("") }
    var observationText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var laborCostType by remember { mutableStateOf("NINGUNO") }
    var laborCostValueText by remember { mutableStateOf("") }
    var ownerPayType by remember { mutableStateOf("NINGUNO") }
    var ownerPayValueText by remember { mutableStateOf("") }

    val activeRecipeIngredients = remember(selectedProductPair, uiState.recetaIngredientes) {
        selectedProductPair?.let { (pe, _) ->
            uiState.recetaIngredientes.filter { it.productoElaboradoId == pe.productId }
        } ?: emptyList()
    }

    var selectedBaseUnitState by remember(selectedProductPair) { mutableStateOf<String?>(null) }
    val currentBaseMp = selectedProductPair?.let { (pe, _) -> uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId } }
    val baseUnitCategory = remember(currentBaseMp) { getBaseUnit(currentBaseMp?.unit ?: "g") }
    val compatibleUnits = remember(baseUnitCategory) { getCompatibleUnits(baseUnitCategory) }
    val activeBaseUnit = selectedBaseUnitState ?: currentBaseMp?.unit ?: "g"

    // Automatic calculations: ONLY requires baseQuantityInputText
    val calculationResults = remember(
        selectedProductPair,
        baseQuantityInputText,
        activeBaseUnit,
        activeRecipeIngredients,
        uiState.materiasPrimas,
        uiState.gastosGenerales,
        uiState.inversiones,
        laborCostType,
        laborCostValueText,
        ownerPayType,
        ownerPayValueText
    ) {
        val (pe, product) = selectedProductPair ?: return@remember null
        val actualBaseQty = baseQuantityInputText.toDoubleOrNull() ?: return@remember null
        if (actualBaseQty <= 0.0 || pe.baseQuantity <= 0.0) return@remember null

        val recipeBaseUnit = currentBaseMp?.unit ?: activeBaseUnit
        val baseQtyInRecipeUnit = UnitConverter.convert(actualBaseQty, activeBaseUnit, recipeBaseUnit) ?: actualBaseQty
        val factor = baseQtyInRecipeUnit / pe.baseQuantity
        val calculatedExpectedYield = pe.baseYield * factor

        var hasErrors = false
        var totalDirectIngredientsCost = 0.0

        val ingredientConsumptions = activeRecipeIngredients.map { ing ->
            val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
            val baseQtyInRecipe = ing.quantity
            val recalculatedQty = baseQtyInRecipe * factor

            val convertedQtyToDeduct = if (raw != null) {
                UnitConverter.convert(recalculatedQty, ing.unit, raw.unit) ?: recalculatedQty
            } else recalculatedQty

            val unitCost = raw?.unitCost ?: 0.0
            val ingredientTotalCost = convertedQtyToDeduct * unitCost
            totalDirectIngredientsCost += ingredientTotalCost

            val isCompatible = if (raw != null) UnitConverter.areCompatible(ing.unit, raw.unit) else true
            val stockAvailable = raw?.stock ?: 0.0
            val isSufficient = if (raw != null) stockAvailable >= convertedQtyToDeduct else true
            if (!isSufficient || !isCompatible) {
                hasErrors = true
            }

            IngredientBatchResult(
                materiaPrimaId = ing.materiaPrimaId,
                name = raw?.name ?: "Materia prima desconocida",
                recipeUnit = ing.unit,
                inventoryUnit = raw?.unit ?: ing.unit,
                recalculatedRecipeQty = recalculatedQty,
                convertedInventoryQty = convertedQtyToDeduct,
                unitCost = unitCost,
                totalCost = ingredientTotalCost,
                stockAvailable = stockAvailable,
                isSufficient = isSufficient,
                isCompatible = isCompatible
            )
        }

        val costSheet = CostCalculationHelper.calculateCostSheet(
            product = product,
            products = uiState.products,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones
        )
        val indirectUnitCost = costSheet.gastoIndirectoUnitario
        val totalIndirectCostAllocated = calculatedExpectedYield * indirectUnitCost

        val catalogPrice = product.price
        val expectedRevenue = calculatedExpectedYield * catalogPrice

        val laborVal = laborCostValueText.toDoubleOrNull() ?: 0.0
        val totalLaborCost = when (laborCostType) {
            "PORCENTAJE" -> (laborVal / 100.0) * expectedRevenue
            "FIJO_UNITARIO" -> laborVal * calculatedExpectedYield
            else -> 0.0
        }

        val ownerVal = ownerPayValueText.toDoubleOrNull() ?: 0.0
        val totalOwnerPay = when (ownerPayType) {
            "PORCENTAJE" -> (ownerVal / 100.0) * expectedRevenue
            "FIJO_UNITARIO" -> ownerVal * calculatedExpectedYield
            else -> 0.0
        }

        val totalBatchCost = totalDirectIngredientsCost + totalIndirectCostAllocated + totalLaborCost
        val realUnitCost = if (calculatedExpectedYield > 0.0) totalBatchCost / calculatedExpectedYield else 0.0

        val estimatedProfit = expectedRevenue - totalBatchCost - totalOwnerPay
        val profitMargin = if (expectedRevenue > 0.0) (estimatedProfit / expectedRevenue) * 100.0 else 0.0

        BatchCalculationResult(
            factor = factor,
            yield = calculatedExpectedYield,
            ingredients = ingredientConsumptions,
            totalDirectIngredientsCost = totalDirectIngredientsCost,
            totalIndirectCostAllocated = totalIndirectCostAllocated,
            totalLaborCost = totalLaborCost,
            totalOwnerPay = totalOwnerPay,
            totalBatchCost = totalBatchCost,
            realUnitCost = realUnitCost,
            hasErrors = hasErrors,
            userExpectedYield = calculatedExpectedYield,
            yieldPercentage = 100.0,
            expectedRevenue = expectedRevenue,
            estimatedProfit = estimatedProfit,
            profitMargin = profitMargin
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Factory, contentDescription = null, tint = ElQadreNavy)
                Text("ACTIVAR NUEVA TANDA", fontWeight = FontWeight.ExtraBold, color = ElQadreNavy, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = tandaNumberText,
                    onValueChange = { tandaNumberText = it },
                    label = { Text("Número de Tanda") },
                    placeholder = { Text("Ej. 01") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().testTag("tanda_number_input")
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedProductPair?.second?.name ?: "Seleccione Producto...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Producto Elaborado") },
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        elaboratedProducts.forEach { pair ->
                            val (pe, p) = pair
                            DropdownMenuItem(
                                text = { Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    selectedProductPair = pair
                                    dropdownExpanded = false
                                    baseQuantityInputText = pe.baseQuantity.toString()
                                }
                            )
                        }
                    }
                }

                selectedProductPair?.let { (pe, p) ->
                    val baseMp = uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = baseQuantityInputText,
                            onValueChange = { baseQuantityInputText = it },
                            label = { Text("Cantidad (${baseMp?.name ?: "Base"})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("base_qty_used_input")
                        )

                        var expandedUnitDropdown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedUnitDropdown = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ElQadreNavy)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeBaseUnit, color = ElQadreNavy, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ElQadreNavy)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedUnitDropdown,
                                onDismissRequest = { expandedUnitDropdown = false }
                            ) {
                                compatibleUnits.forEach { unitItem ->
                                    DropdownMenuItem(
                                        text = { Text(unitItem, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            selectedBaseUnitState = unitItem
                                            expandedUnitDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = observationText,
                        onValueChange = { observationText = it },
                        label = { Text("Observaciones / Notas") },
                        placeholder = { Text("Ej. Turno de la mañana, horno 2...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2.5 CONFIGURACIÓN DE PAGOS (MANO DE OBRA Y PROPIETARIO)
                    if (selectedProductPair != null) {
                        val res = calculationResults
                        TandaPaymentConfigSection(
                            laborCostType = laborCostType,
                            onLaborCostTypeChange = { laborCostType = it },
                            laborCostValueText = laborCostValueText,
                            onLaborCostValueTextChange = { laborCostValueText = it },
                            ownerPayType = ownerPayType,
                            onOwnerPayTypeChange = { ownerPayType = it },
                            ownerPayValueText = ownerPayValueText,
                            onOwnerPayValueTextChange = { ownerPayValueText = it },
                            totalLaborCost = res?.totalLaborCost ?: 0.0,
                            totalOwnerPay = res?.totalOwnerPay ?: 0.0
                        )
                    }

                    // 3. RESULTADO PROVISIONAL DISPLAY
                    calculationResults?.let { res ->
                        Surface(
                            color = Amber50,
                            border = BorderStroke(1.dp, Amber600),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("RESULTADO PROVISIONAL (CALCULADO SEGÚN RECETA)", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Amber700)
                                    Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = Amber700, modifier = Modifier.size(14.dp))
                                }

                                HorizontalDivider(color = Amber600.copy(alpha = 0.3f))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Producción Esperada:", fontSize = 11.sp, color = Slate700)
                                    Text("${res.userExpectedYield.toInt()} ${pe.productionUnit}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Costo Insumos Directos:", fontSize = 11.sp, color = Slate700)
                                    Text("$${"%.2f".format(res.totalDirectIngredientsCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                if (res.totalLaborCost > 0.0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Costo Mano de Obra:", fontSize = 11.sp, color = Slate700)
                                        Text("$${"%.2f".format(res.totalLaborCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                                    }
                                }
                                if (res.totalOwnerPay > 0.0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Pago Propietario/Dueño:", fontSize = 11.sp, color = Slate700)
                                        Text("$${"%.2f".format(res.totalOwnerPay)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Costo Provisional Tanda:", fontSize = 11.sp, color = Slate700)
                                    Text("$${"%.2f".format(res.totalBatchCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Precio Definitivo Catálogo:", fontSize = 11.sp, color = Slate700)
                                    Text("$${"%.2f".format(p.price)} CUP / ${pe.productionUnit}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ingreso Esperado:", fontSize = 11.sp, color = Slate700)
                                    Text("$${"%.2f".format(res.expectedRevenue)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Utilidad Esperada:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    Text("$${"%.2f".format(res.estimatedProfit)} CUP (${"%.1f".format(res.profitMargin)}%)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                                }

                                if (res.hasErrors) {
                                    Surface(color = Rose50, border = BorderStroke(1.dp, Rose500), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                                        Text("⚠️ Stock insuficiente o unidades incompatibles en algunos insumos.", color = Rose700, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val res = calculationResults
            val canConfirm = res != null && !res.hasErrors

            Button(
                onClick = {
                    val pair = selectedProductPair ?: return@Button
                    val result = res ?: return@Button
                    val (pe, p) = pair
                    val baseMp = uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId } ?: return@Button

                    val now = System.currentTimeMillis()
                    val batchUuid = "TANDA-${tandaNumberText.ifEmpty { "01" }}-${System.currentTimeMillis() % 1000}"

                    val consumSummary = result.ingredients.joinToString(", ") { ing ->
                        "${ing.name}: ${"%.2f".format(ing.recalculatedRecipeQty)} ${ing.recipeUnit}"
                    }

                    val tanda = Tanda(
                        uuid = batchUuid,
                        tandaNumber = tandaNumberText.trim().ifEmpty { "01" },
                        productId = p.id,
                        productName = p.name,
                        date = now,
                        responsibleUser = uiState.currentUser?.username ?: "Admin",
                        baseMateriaPrimaId = pe.baseMateriaPrimaId,
                        baseMateriaPrimaName = baseMp.name,
                        baseQuantityUsed = baseQuantityInputText.toDoubleOrNull() ?: pe.baseQuantity,
                        baseQuantityUnit = activeBaseUnit,
                        productionFactor = result.factor,
                        estimatedYield = result.userExpectedYield,
                        expectedYield = result.userExpectedYield,
                        actualYield = 0.0, // Unconfirmed real yield until closing
                        yieldPercentage = 100.0,
                        productionUnit = pe.productionUnit,
                        ingredientsConsumedText = consumSummary,
                        status = "ACTIVA",
                        jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Única",
                        jornadaId = uiState.activeJornada?.id ?: 0L,
                        observation = observationText.trim(),
                        laborCostType = laborCostType,
                        laborCostValue = laborCostValueText.toDoubleOrNull() ?: 0.0,
                        totalLaborCost = result.totalLaborCost,
                        ownerPayType = ownerPayType,
                        ownerPayValue = ownerPayValueText.toDoubleOrNull() ?: 0.0,
                        totalOwnerPay = result.totalOwnerPay,
                        totalDirectIngredientsCost = result.totalDirectIngredientsCost,
                        totalIndirectCostAllocated = result.totalIndirectCostAllocated,
                        totalBatchCost = result.totalBatchCost,
                        realUnitCost = result.realUnitCost,
                        expectedRevenue = result.expectedRevenue,
                        estimatedProfit = result.estimatedProfit,
                        profitMargin = result.profitMargin,
                        inventoryDeducted = true
                    )

                    val consumos = result.ingredients.map { ing ->
                        Triple(ing.materiaPrimaId, ing.convertedInventoryQty, "${"%.2f".format(ing.recalculatedRecipeQty)} ${ing.recipeUnit}")
                    }

                    viewModel.registrarTanda(tanda, consumos)
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.testTag("confirm_agregar_tanda")
            ) {
                Text("Activar Tanda y Descontar Inventario", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate500)
            }
        }
    )
}

// =================================================================
// 5. MODAL EDITAR TANDA ACTIVA (RECALCULA Y REAJUSTA INVENTARIO)
// =================================================================
@Composable
fun EditarTandaDialog(
    tanda: Tanda,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val pe = remember(tanda.productId, uiState.productosElaborados) {
        uiState.productosElaborados.find { it.productId == tanda.productId }
    }
    val product = remember(tanda.productId, uiState.products) {
        uiState.products.find { it.id == tanda.productId }
    }

    var baseQuantityInputText by remember { mutableStateOf(tanda.baseQuantityUsed.toString()) }
    var observationText by remember { mutableStateOf(tanda.observation) }

    val activeRecipeIngredients = remember(pe, uiState.recetaIngredientes) {
        if (pe != null) uiState.recetaIngredientes.filter { it.productoElaboradoId == pe.productId } else emptyList()
    }

    val calculationResults = remember(
        pe,
        product,
        baseQuantityInputText,
        activeRecipeIngredients,
        uiState.materiasPrimas,
        uiState.gastosGenerales,
        uiState.inversiones
    ) {
        if (pe == null || product == null) return@remember null
        val newBaseQty = baseQuantityInputText.toDoubleOrNull() ?: return@remember null
        if (newBaseQty <= 0.0 || pe.baseQuantity <= 0.0) return@remember null

        val factor = newBaseQty / pe.baseQuantity
        val calculatedExpectedYield = pe.baseYield * factor

        var hasErrors = false
        var totalDirectIngredientsCost = 0.0

        val ingredientConsumptions = activeRecipeIngredients.map { ing ->
            val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
            val recalculatedQty = ing.quantity * factor
            val convertedQtyToDeduct = if (raw != null) UnitConverter.convert(recalculatedQty, ing.unit, raw.unit) ?: recalculatedQty else recalculatedQty
            val unitCost = raw?.unitCost ?: 0.0
            val ingredientTotalCost = convertedQtyToDeduct * unitCost
            totalDirectIngredientsCost += ingredientTotalCost

            val isCompatible = if (raw != null) UnitConverter.areCompatible(ing.unit, raw.unit) else true
            val stockAvailable = raw?.stock ?: 0.0
            val isSufficient = if (raw != null) (stockAvailable + (tanda.baseQuantityUsed * 0.1)) >= convertedQtyToDeduct else true
            if (!isSufficient || !isCompatible) hasErrors = true

            IngredientBatchResult(
                materiaPrimaId = ing.materiaPrimaId,
                name = raw?.name ?: "Materia prima",
                recipeUnit = ing.unit,
                inventoryUnit = raw?.unit ?: ing.unit,
                recalculatedRecipeQty = recalculatedQty,
                convertedInventoryQty = convertedQtyToDeduct,
                unitCost = unitCost,
                totalCost = ingredientTotalCost,
                stockAvailable = stockAvailable,
                isSufficient = isSufficient,
                isCompatible = isCompatible
            )
        }

        val costSheet = CostCalculationHelper.calculateCostSheet(
            product = product,
            products = uiState.products,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones
        )
        val indirectUnitCost = costSheet.gastoIndirectoUnitario
        val totalIndirectCostAllocated = calculatedExpectedYield * indirectUnitCost
        val totalBatchCost = totalDirectIngredientsCost + totalIndirectCostAllocated
        val realUnitCost = if (calculatedExpectedYield > 0.0) totalBatchCost / calculatedExpectedYield else 0.0

        val expectedRevenue = calculatedExpectedYield * product.price
        val estimatedProfit = expectedRevenue - totalBatchCost
        val profitMargin = if (expectedRevenue > 0.0) (estimatedProfit / expectedRevenue) * 100.0 else 0.0

        BatchCalculationResult(
            factor = factor,
            yield = calculatedExpectedYield,
            ingredients = ingredientConsumptions,
            totalDirectIngredientsCost = totalDirectIngredientsCost,
            totalIndirectCostAllocated = totalIndirectCostAllocated,
            totalLaborCost = 0.0,
            totalBatchCost = totalBatchCost,
            realUnitCost = realUnitCost,
            hasErrors = hasErrors,
            userExpectedYield = calculatedExpectedYield,
            yieldPercentage = 100.0,
            expectedRevenue = expectedRevenue,
            estimatedProfit = estimatedProfit,
            profitMargin = profitMargin
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = ElQadreNavy)
                Text("EDITAR TANDA #${tanda.tandaNumber}", fontWeight = FontWeight.ExtraBold, color = ElQadreNavy, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = Slate50, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Producto: ${tanda.productName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                        Text("Ingrediente Base: ${tanda.baseMateriaPrimaName}", fontSize = 11.sp, color = Slate700)
                    }
                }

                OutlinedTextField(
                    value = baseQuantityInputText,
                    onValueChange = { baseQuantityInputText = it },
                    label = { Text("Nueva Cantidad Base (${tanda.baseQuantityUnit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().testTag("edit_base_qty_input")
                )

                OutlinedTextField(
                    value = observationText,
                    onValueChange = { observationText = it },
                    label = { Text("Observaciones / Corrección") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                calculationResults?.let { res ->
                    Surface(color = Amber50, border = BorderStroke(1.dp, Amber600), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("RECALCULO PROVISIONAL ACTUALIZADO", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Amber700)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Prod. Esperada:", fontSize = 11.sp, color = Slate700)
                                Text("${res.userExpectedYield.toInt()} ${tanda.productionUnit}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Nuevo Costo Tanda:", fontSize = 11.sp, color = Slate700)
                                Text("$${"%.2f".format(res.totalBatchCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Nuevo Ingreso Esperado:", fontSize = 11.sp, color = Slate700)
                                Text("$${"%.2f".format(res.expectedRevenue)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val res = calculationResults
            val canConfirm = res != null && !res.hasErrors

            Button(
                onClick = {
                    val result = res ?: return@Button
                    val peObj = pe ?: return@Button

                    val oldFactor = tanda.productionFactor
                    val oldConsumos = activeRecipeIngredients.map { ing ->
                        val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                        val oldQty = ing.quantity * oldFactor
                        val oldConverted = if (raw != null) UnitConverter.convert(oldQty, ing.unit, raw.unit) ?: oldQty else oldQty
                        Triple(ing.materiaPrimaId, oldConverted, "${"%.2f".format(oldQty)} ${ing.unit}")
                    }

                    val newConsumos = result.ingredients.map { ing ->
                        Triple(ing.materiaPrimaId, ing.convertedInventoryQty, "${"%.2f".format(ing.recalculatedRecipeQty)} ${ing.recipeUnit}")
                    }

                    val newConsumSummary = result.ingredients.joinToString(", ") { ing ->
                        "${ing.name}: ${"%.2f".format(ing.recalculatedRecipeQty)} ${ing.recipeUnit}"
                    }

                    val updatedTanda = tanda.copy(
                        baseQuantityUsed = baseQuantityInputText.toDoubleOrNull() ?: tanda.baseQuantityUsed,
                        productionFactor = result.factor,
                        estimatedYield = result.userExpectedYield,
                        expectedYield = result.userExpectedYield,
                        ingredientsConsumedText = newConsumSummary,
                        observation = observationText.trim(),
                        totalDirectIngredientsCost = result.totalDirectIngredientsCost,
                        totalIndirectCostAllocated = result.totalIndirectCostAllocated,
                        totalBatchCost = result.totalBatchCost,
                        realUnitCost = result.realUnitCost,
                        expectedRevenue = result.expectedRevenue,
                        estimatedProfit = result.estimatedProfit,
                        profitMargin = result.profitMargin
                    )

                    viewModel.editarTandaActiva(tanda, updatedTanda, oldConsumos, newConsumos)
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.testTag("confirm_edit_tanda")
            ) {
                Text("Guardar Cambios y Ajustar Inventario", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Slate500) }
        }
    )
}

// =================================================================
// 6. MODAL CERRAR TANDA (INTRODUCIR SOLO PRODUCCIÓN REAL)
// =================================================================
@Composable
fun CerrarTandaDialog(
    tanda: Tanda,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val catalogPrice = remember(tanda.productId, uiState.products) {
        uiState.products.find { it.id == tanda.productId }?.price ?: 0.0
    }

    var actualYieldInputText by remember { mutableStateOf(tanda.estimatedYield.toInt().toString()) }
    var observationText by remember { mutableStateOf(tanda.observation) }

    val actualYieldVal = actualYieldInputText.toDoubleOrNull() ?: 0.0
    val expectedYieldVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield

    val yieldPct = if (expectedYieldVal > 0.0) (actualYieldVal / expectedYieldVal) * 100.0 else 100.0
    val realRevenue = actualYieldVal * catalogPrice
    val realProfit = realRevenue - tanda.totalBatchCost
    val realProfitMargin = if (realRevenue > 0.0) (realProfit / realRevenue) * 100.0 else 0.0
    val realUnitCost = if (actualYieldVal > 0.0) tanda.totalBatchCost / actualYieldVal else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Emerald600)
                Text("CERRAR TANDA #${tanda.tandaNumber}", fontWeight = FontWeight.ExtraBold, color = ElQadreNavy, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Read-only parameters
                Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PARÁMETROS DE LA TANDA", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Producto:", fontSize = 11.sp, color = Slate600)
                            Text(tanda.productName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cantidad Base Utilizada:", fontSize = 11.sp, color = Slate600)
                            Text("${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit} (${tanda.baseMateriaPrimaName})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Producción Esperada (Calculada):", fontSize = 11.sp, color = Slate600)
                            Text("${expectedYieldVal.toInt()} ${tanda.productionUnit}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Emerald700)
                        }
                    }
                }

                // 6. PRODUCCIÓN REAL (ÚNICO DATO MANUAL REQUERIDO)
                OutlinedTextField(
                    value = actualYieldInputText,
                    onValueChange = { actualYieldInputText = it },
                    label = { Text("PRODUCCIÓN REAL OBTENIDA (${tanda.productionUnit}) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Emerald600, focusedLabelColor = Emerald600),
                    modifier = Modifier.fillMaxWidth().testTag("actual_yield_input_cerrar")
                )

                OutlinedTextField(
                    value = observationText,
                    onValueChange = { observationText = it },
                    label = { Text("Observaciones / Notas de Cierre") },
                    placeholder = { Text("Ej. Merma por quemado leve, masa extra...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 7. RESULTADO FINAL CONSOLIDADO PREVIEW
                Surface(
                    color = Emerald50,
                    border = BorderStroke(1.dp, Emerald600),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("RESULTADO FINAL Y CONSOLIDACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Emerald700)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rendimiento Real:", fontSize = 11.sp, color = Slate700)
                            Text("${"%.1f".format(yieldPct)}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (yieldPct >= 95.0) Emerald600 else Rose600)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costo Consolidado:", fontSize = 11.sp, color = Slate700)
                            Text("$${"%.2f".format(tanda.totalBatchCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingreso Real Consolidado:", fontSize = 11.sp, color = Slate700)
                            Text("$${"%.2f".format(realRevenue)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Utilidad Real Consolidada:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text("$${"%.2f".format(realProfit)} CUP (${"%.1f".format(realProfitMargin)}%)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreGoldDark)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canConfirm = actualYieldVal > 0.0

            Button(
                onClick = {
                    val updatedTanda = tanda.copy(
                        actualYield = actualYieldVal,
                        yieldPercentage = yieldPct,
                        status = "CERRADA",
                        observation = observationText.trim(),
                        expectedRevenue = realRevenue,
                        estimatedProfit = realProfit,
                        profitMargin = realProfitMargin,
                        realUnitCost = realUnitCost,
                        inventoryDeducted = true // Inventory was deducted at activation, not re-deducted at close
                    )

                    viewModel.cerrarTanda(updatedTanda)
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                modifier = Modifier.testTag("confirm_cerrar_tanda")
            ) {
                Text("Confirmar Cierre de Tanda", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Slate500) }
        }
    )
}

// =================================================================
// IMPORTAR TANDAS DESDE TEXTO (CONSERVADO)
// =================================================================
@Composable
fun ImportarTandasDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var parsedPreview by remember { mutableStateOf<List<ParsedTandaImport>?>(null) }
    var parseErrorMessage by remember { mutableStateOf<String?>(null) }

    val elaboratedProductsMap = remember(uiState.productosElaborados, uiState.products) {
        uiState.productosElaborados.mapNotNull { pe ->
            uiState.products.find { it.id == pe.productId }?.let { p ->
                p.name.lowercase().trim() to Pair(pe, p)
            }
        }.toMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = ElQadreGoldDark)
                Text("IMPORTAR TANDAS DESDE TEXTO", fontWeight = FontWeight.ExtraBold, color = ElQadreNavy, fontSize = 16.sp)
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
                    text = "Pegue el texto recibido de Cocina o del Dueño. Puede incluir una o varias tandas en formato estructurado.",
                    fontSize = 11.sp,
                    color = Slate600
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        parsedPreview = null
                        parseErrorMessage = null
                    },
                    label = { Text("Texto Estructurado de Cocina") },
                    placeholder = {
                        Text("Ejemplo:\nTanda: 01\nProducto: Pizza Napolitana\nBase: 20 lb harina\nProducción esperada: 100\nProducción real: 94")
                    },
                    minLines = 6,
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().testTag("import_text_input")
                )

                if (parsedPreview == null && rawText.isNotBlank()) {
                    Button(
                        onClick = {
                            val parseRes = parseImportedTandasText(rawText, elaboratedProductsMap)
                            if (parseRes.errorMessage != null) {
                                parseErrorMessage = parseRes.errorMessage
                                parsedPreview = null
                            } else {
                                parseErrorMessage = null
                                parsedPreview = parseRes.items
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Procesar y Validar Texto")
                    }
                }

                parseErrorMessage?.let { err ->
                    Surface(
                        color = Rose50,
                        border = BorderStroke(1.dp, Rose500),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(16.dp))
                            Text(err, color = Rose700, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                parsedPreview?.let { items ->
                    Surface(
                        color = Emerald50,
                        border = BorderStroke(1.dp, Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                                Text("Vista Previa: ${items.size} Tandas Reconocidas Correctamente", color = Emerald700, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            items.forEach { item ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Tanda ${item.tandaNumber} — ${item.product.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                                        Text("Base: ${item.baseQtyUsed} ${item.pe.productionUnit} | Esperada: ${item.expectedYield.toInt()} | Real: ${item.actualYield.toInt()}", fontSize = 11.sp, color = Slate700)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val previewItems = parsedPreview
            val canConfirm = previewItems != null && previewItems.isNotEmpty()

            Button(
                onClick = {
                    val items = previewItems ?: return@Button
                    val now = System.currentTimeMillis()

                    items.forEachIndexed { index, item ->
                        val pe = item.pe
                        val p = item.product
                        val baseMp = uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId }

                        val factor = if (pe.baseQuantity > 0.0) item.baseQtyUsed / pe.baseQuantity else 1.0
                        val recipeIngredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == pe.productId }

                        val activeConsumos = recipeIngredients.map { ing ->
                            val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                            val recalculatedQty = ing.quantity * factor
                            val convertedToDeduct = if (raw != null) UnitConverter.convert(recalculatedQty, ing.unit, raw.unit) ?: recalculatedQty else recalculatedQty
                            Triple(ing.materiaPrimaId, convertedToDeduct, "${"%.2f".format(recalculatedQty)} ${ing.unit}")
                        }

                        val costSheet = CostCalculationHelper.calculateCostSheet(
                            product = p,
                            products = uiState.products,
                            productosElaborados = uiState.productosElaborados,
                            recetaIngredientes = uiState.recetaIngredientes,
                            materiasPrimas = uiState.materiasPrimas,
                            gastosGenerales = uiState.gastosGenerales,
                            inversiones = uiState.inversiones
                        )

                        val directCost = activeConsumos.sumOf { (mpId, qty, _) ->
                            val raw = uiState.materiasPrimas.find { it.id == mpId }
                            qty * (raw?.unitCost ?: 0.0)
                        }
                        val indirectCost = item.expectedYield * costSheet.gastoIndirectoUnitario
                        val totalBatchCost = directCost + indirectCost
                        val realUnitCost = if (item.actualYield > 0.0) totalBatchCost / item.actualYield else (if (item.expectedYield > 0.0) totalBatchCost / item.expectedYield else 0.0)

                        val expectedRev = item.actualYield * p.price
                        val estProfit = expectedRev - totalBatchCost
                        val profitMargin = if (expectedRev > 0.0) (estProfit / expectedRev) * 100.0 else 0.0
                        val yieldPct = if (item.expectedYield > 0.0) (item.actualYield / item.expectedYield) * 100.0 else 100.0

                        val batchUuid = "TANDA-${item.tandaNumber}-$now-$index"

                        val tanda = Tanda(
                            uuid = batchUuid,
                            tandaNumber = item.tandaNumber,
                            productId = p.id,
                            productName = p.name,
                            date = now,
                            responsibleUser = uiState.currentUser?.username ?: "Admin",
                            baseMateriaPrimaId = pe.baseMateriaPrimaId,
                            baseMateriaPrimaName = baseMp?.name ?: "Base",
                            baseQuantityUsed = item.baseQtyUsed,
                            baseQuantityUnit = baseMp?.unit ?: "",
                            productionFactor = factor,
                            estimatedYield = item.expectedYield,
                            expectedYield = item.expectedYield,
                            actualYield = item.actualYield,
                            yieldPercentage = yieldPct,
                            productionUnit = pe.productionUnit,
                            ingredientsConsumedText = activeConsumos.joinToString(", ") { "${it.third}" },
                            status = if (item.actualYield > 0.0 && item.actualYield != item.expectedYield) "CERRADA" else "ACTIVA",
                            jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Única",
                            jornadaId = uiState.activeJornada?.id ?: 0L,
                            observation = "Importada desde texto de Cocina",
                            totalLaborCost = 0.0,
                            totalDirectIngredientsCost = directCost,
                            totalIndirectCostAllocated = indirectCost,
                            totalBatchCost = totalBatchCost,
                            realUnitCost = realUnitCost,
                            expectedRevenue = expectedRev,
                            estimatedProfit = estProfit,
                            profitMargin = profitMargin,
                            inventoryDeducted = true
                        )

                        viewModel.registrarTanda(tanda, activeConsumos)
                    }

                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.testTag("confirm_importar_tandas")
            ) {
                Text("Confirmar e Importar ${parsedPreview?.size ?: 0} Tandas", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate500)
            }
        }
    )
}

// =================================================================
// DIALOG DE DETALLE Y AUDITORÍA DE TANDA
// =================================================================
@Composable
fun TandaDetailDialog(
    tanda: Tanda,
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(tanda.date) { dateFormat.format(Date(tanda.date)) }

    val product = remember(tanda.productId, uiState.products) {
        uiState.products.find { it.id == tanda.productId }
    }

    val costSheet = remember(product, uiState) {
        if (product != null) {
            CostCalculationHelper.calculateCostSheet(
                product = product,
                products = uiState.products,
                productosElaborados = uiState.productosElaborados,
                recetaIngredientes = uiState.recetaIngredientes,
                materiasPrimas = uiState.materiasPrimas,
                gastosGenerales = uiState.gastosGenerales,
                inversiones = uiState.inversiones
            )
        } else null
    }

    val isClosed = tanda.status == "CERRADA"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = ElQadreNavy)
                Column {
                    Text("DETALLE DE TANDA ${tanda.tandaNumber}", fontWeight = FontWeight.ExtraBold, color = ElQadreNavy, fontSize = 16.sp)
                    Text(tanda.uuid, fontSize = 11.sp, color = Slate500)
                }
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
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Producto: ${tanda.productName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            Text(dateStr, fontSize = 10.sp, color = Slate500)
                        }
                        Text("Estado: ${tanda.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isClosed) Emerald600 else Amber700)
                        Text("Responsable: ${tanda.responsibleUser}", fontSize = 11.sp, color = Slate700)
                        if (tanda.observation.isNotEmpty()) {
                            Text("Observación: ${tanda.observation}", fontSize = 11.sp, color = Slate600)
                        }
                    }
                }

                Surface(
                    color = if (isClosed) ElQadreBgSecondary else Amber50,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isClosed) "RENDIMIENTO Y RENTABILIDAD REAL" else "RESULTADO PROVISIONAL", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = ElQadreNavy)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Producción Esperada:", fontSize = 11.sp, color = Slate600)
                            Text("${tanda.estimatedYield.toInt()} ${tanda.productionUnit}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Producción Real:", fontSize = 11.sp, color = Slate600)
                            Text(if (isClosed) "${tanda.actualYield.toInt()} ${tanda.productionUnit}" else "Pendiente de cierre", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                        }
                        if (isClosed) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rendimiento del Lote:", fontSize = 11.sp, color = Slate600)
                                Text("${"%.1f".format(tanda.yieldPercentage)}%", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = if (tanda.yieldPercentage >= 95.0) Emerald600 else Rose600)
                            }
                        }
                        HorizontalDivider(color = Slate200)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingreso Esperado / Real:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(tanda.expectedRevenue)} CUP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Emerald700)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costo Total de Tanda:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(tanda.totalBatchCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Rose700)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ganancia (Margen):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text("$${"%.2f".format(tanda.estimatedProfit)} CUP (${"%.1f".format(tanda.profitMargin)}%)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = ElQadreGoldDark)
                        }
                    }
                }

                Text("Ingredientes Consumidos y Descontados de Inventario:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                Surface(
                    color = Color.White,
                    border = BorderStroke(1.dp, ElQadreBorderLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tanda.ingredientsConsumedText,
                        fontSize = 11.sp,
                        color = Slate800,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                costSheet?.let { cs ->
                    Surface(
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("COMPARACIÓN CON FICHA DE COSTO", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Unitario Ficha (Estimado):", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(cs.costoRealUnitario)} CUP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Unitario Tanda (Real/Prov.):", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(tanda.realUnitCost)} CUP", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = ElQadreGoldDark)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun LegendBadge(color: Color, label: String, amount: Double, percentage: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = "$label: $${"%.0f".format(amount)} (${"%.1f".format(percentage)}%)",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate700
        )
    }
}

@Composable
fun TandaPaymentConfigSection(
    laborCostType: String,
    onLaborCostTypeChange: (String) -> Unit,
    laborCostValueText: String,
    onLaborCostValueTextChange: (String) -> Unit,
    ownerPayType: String,
    onOwnerPayTypeChange: (String) -> Unit,
    ownerPayValueText: String,
    onOwnerPayValueTextChange: (String) -> Unit,
    totalLaborCost: Double,
    totalOwnerPay: Double
) {
    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate300),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CONFIGURACIÓN DE PAGOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)

            // 1. TRABAJADORES (Mano de obra)
            Text("Trabajadores (Mano de Obra):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("NINGUNO" to "Ninguno", "PORCENTAJE" to "Porcentaje (%)", "FIJO_UNITARIO" to "Fijo ($/u)").forEach { (code, label) ->
                    FilterChip(
                        selected = laborCostType == code,
                        onClick = { onLaborCostTypeChange(code) },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (laborCostType != "NINGUNO") {
                OutlinedTextField(
                    value = laborCostValueText,
                    onValueChange = onLaborCostValueTextChange,
                    label = { Text(if (laborCostType == "PORCENTAJE") "Porcentaje (%)" else "Valor Fijo por Unidad ($/u)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("labor_cost_value_input")
                )
                Text("Total mano de obra: $${"%.2f".format(totalLaborCost)} CUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald700)
            }

            HorizontalDivider(color = Slate200)

            // 2. DUEÑOS / ADMINISTRACIÓN
            Text("Dueños / Pago Propietario:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("NINGUNO" to "Ninguno", "PORCENTAJE" to "Porcentaje (%)", "FIJO_UNITARIO" to "Fijo ($/u)").forEach { (code, label) ->
                    FilterChip(
                        selected = ownerPayType == code,
                        onClick = { onOwnerPayTypeChange(code) },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (ownerPayType != "NINGUNO") {
                OutlinedTextField(
                    value = ownerPayValueText,
                    onValueChange = onOwnerPayValueTextChange,
                    label = { Text(if (ownerPayType == "PORCENTAJE") "Porcentaje (%)" else "Valor Fijo por Unidad ($/u)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("owner_pay_value_input")
                )
                Text("Total pago dueño: $${"%.2f".format(totalOwnerPay)} CUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElQadreGoldDark)
            }
        }
    }
}

data class IngredientBatchResult(
    val materiaPrimaId: Long,
    val name: String,
    val recipeUnit: String,
    val inventoryUnit: String,
    val recalculatedRecipeQty: Double,
    val convertedInventoryQty: Double,
    val unitCost: Double,
    val totalCost: Double,
    val stockAvailable: Double,
    val isSufficient: Boolean,
    val isCompatible: Boolean
)

data class BatchCalculationResult(
    val factor: Double,
    val yield: Double,
    val ingredients: List<IngredientBatchResult>,
    val totalDirectIngredientsCost: Double,
    val totalIndirectCostAllocated: Double,
    val totalLaborCost: Double,
    val totalOwnerPay: Double = 0.0,
    val totalBatchCost: Double,
    val realUnitCost: Double,
    val hasErrors: Boolean,
    val userExpectedYield: Double = 0.0,
    val yieldPercentage: Double = 100.0,
    val expectedRevenue: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val profitMargin: Double = 0.0
)

data class ParsedTandaImport(
    val tandaNumber: String,
    val pe: ProductoElaborado,
    val product: Product,
    val baseQtyUsed: Double,
    val expectedYield: Double,
    val actualYield: Double
)

data class ParseImportResult(
    val items: List<ParsedTandaImport>,
    val errorMessage: String?
)

fun parseImportedTandasText(
    text: String,
    elaboratedProductsMap: Map<String, Pair<ProductoElaborado, Product>>
): ParseImportResult {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) {
        return ParseImportResult(emptyList(), "El texto proporcionado está vacío.")
    }

    val blocks = mutableListOf<MutableList<String>>()
    var currentBlock = mutableListOf<String>()

    lines.forEach { line ->
        if (line.startsWith("Tanda", ignoreCase = true) && currentBlock.isNotEmpty()) {
            blocks.add(currentBlock)
            currentBlock = mutableListOf()
        }
        currentBlock.add(line)
    }
    if (currentBlock.isNotEmpty()) {
        blocks.add(currentBlock)
    }

    val parsedList = mutableListOf<ParsedTandaImport>()

    blocks.forEachIndexed { index, block ->
        var tandaNum = "${index + 1}"
        var productName = ""
        var baseQty = 0.0
        var expYield = 0.0
        var actYield = 0.0

        block.forEach { l ->
            val parts = l.split(":", "=")
            if (parts.size >= 2) {
                val key = parts[0].lowercase().trim()
                val valStr = parts.subList(1, parts.size).joinToString(":").trim()

                when {
                    key.contains("tanda") -> tandaNum = valStr.replace("N°", "").replace("#", "").trim()
                    key.contains("producto") -> productName = valStr.lowercase().trim()
                    key.contains("base") || key.contains("cantidad") -> {
                        val numeric = valStr.replace(Regex("[^0-9.]"), " ").trim().split("\\s+".toRegex()).firstOrNull()
                        baseQty = numeric?.toDoubleOrNull() ?: 0.0
                    }
                    key.contains("esperada") || key.contains("esperado") -> {
                        val numeric = valStr.replace(Regex("[^0-9.]"), " ").trim().split("\\s+".toRegex()).firstOrNull()
                        expYield = numeric?.toDoubleOrNull() ?: 0.0
                    }
                    key.contains("real") || key.contains("obtenida") -> {
                        val numeric = valStr.replace(Regex("[^0-9.]"), " ").trim().split("\\s+".toRegex()).firstOrNull()
                        actYield = numeric?.toDoubleOrNull() ?: 0.0
                    }
                }
            }
        }

        if (productName.isEmpty()) {
            return ParseImportResult(emptyList(), "Error en bloque ${index + 1}: No se encontró el nombre del producto.")
        }

        val pair = elaboratedProductsMap.entries.find { (pName, _) ->
            productName.contains(pName) || pName.contains(productName)
        }?.value

        if (pair == null) {
            return ParseImportResult(emptyList(), "Producto '$productName' no coincide con ningún producto elaborado en el sistema.")
        }

        val (pe, p) = pair
        if (baseQty <= 0.0) baseQty = pe.baseQuantity
        if (expYield <= 0.0) expYield = pe.baseYield
        if (actYield <= 0.0) actYield = expYield

        parsedList.add(
            ParsedTandaImport(
                tandaNumber = tandaNum,
                pe = pe,
                product = p,
                baseQtyUsed = baseQty,
                expectedYield = expYield,
                actualYield = actYield
            )
        )
    }

    return ParseImportResult(parsedList, null)
}

fun Double.ifZeroUse(fallback: Double): Double = if (this > 0.0) this else fallback
