package com.example.ui.screens.admin

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sms
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.local.model.*
import com.example.ui.screens.dueno.RegisterTandaDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UnitConverter
import com.example.util.CandidateTandaGroup
import com.example.util.CocinaTandasScanResult
import com.example.util.CostCalculationHelper
import com.example.util.ParsedTandaImport
import com.example.util.SmsTandaInboxMessage
import com.example.util.SmsTandasHelper
import com.example.util.TandasJornadaPdfExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProductTandasSummary(
    val productId: Long,
    val productName: String,
    val tandasCount: Int,
    val expectedYield: Double,
    val productionUnit: String,
    val totalCost: Double,
    val tandas: List<Tanda>
)

@Composable
fun TandasPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    var showAgregarTandaDialog by remember { mutableStateOf(false) }
    var showImportarDialog by remember { mutableStateOf(false) }
    var showJornadaCerradaDialog by remember { mutableStateOf(false) }
    var showingArchivoScreen by remember { mutableStateOf(false) }
    var selectedProductForDetail by remember { mutableStateOf<ProductTandasSummary?>(null) }
    var selectedTandaForDetail by remember { mutableStateOf<Tanda?>(null) }
    var selectedTandaForEdit by remember { mutableStateOf<Tanda?>(null) }
    var selectedTandaForClose by remember { mutableStateOf<Tanda?>(null) }
    var showArchivarPendientesDialog by remember { mutableStateOf(false) }

    // 1. DETALLE DE PRODUCTO: Pantalla completa de Tandas del producto seleccionado
    selectedProductForDetail?.let { summary ->
        ProductTandasDetailScreen(
            summary = summary,
            uiState = uiState,
            viewModel = viewModel,
            onCloseTanda = { tanda ->
                selectedTandaForClose = tanda
            },
            onBack = { selectedProductForDetail = null }
        )

        selectedTandaForClose?.let { tanda ->
            CerrarTandaDialog(
                tanda = tanda,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedTandaForClose = null }
            )
        }
        return
    }

    // 2. ARCHIVO: Pantalla independiente de Tandas (no cuadro superpuesto, no debajo de las tarjetas)
    if (showingArchivoScreen) {
        TandasArchivoScreen(
            uiState = uiState,
            viewModel = viewModel,
            onBack = { showingArchivoScreen = false }
        )
        return
    }

    // Tandas de la jornada actual que estén abiertas (no cerradas)
    val activeJornada = uiState.activeJornada
    val openTandas = remember(uiState.tandas, activeJornada) {
        val activeTandas = uiState.tandas.filter { it.status == "ACTIVA" || it.status == "ACTIVADA" || it.status == "ABIERTA" }
        if (activeJornada != null) {
            val jorTandas = activeTandas.filter {
                it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt) || it.jornadaId == 0L
            }
            if (jorTandas.isNotEmpty()) jorTandas else activeTandas
        } else {
            activeTandas
        }
    }

    // Agrupación por producto: exactamente una tarjeta por producto con tandas abiertas
    val productSummaries = remember(openTandas, uiState.products) {
        val prodMap = uiState.products.associateBy { it.id }
        openTandas.groupBy { it.productId }.map { (prodId, tandasList) ->
            val prod = prodMap[prodId]
            val pName = prod?.name ?: tandasList.firstOrNull()?.productName ?: "Producto #$prodId"
            val count = tandasList.size
            val expYield = tandasList.sumOf { if (it.expectedYield > 0.0) it.expectedYield else it.estimatedYield }
            val unit = tandasList.firstOrNull()?.productionUnit?.ifBlank { null } ?: "u"
            val cost = tandasList.sumOf { it.totalBatchCost }
            ProductTandasSummary(
                productId = prodId,
                productName = pName,
                tandasCount = count,
                expectedYield = expYield,
                productionUnit = unit,
                totalCost = cost,
                tandas = tandasList
            )
        }.sortedBy { it.productName.lowercase() }
    }

    // Tandas cerradas para la lista resumida y los gráficos
    val closedTandas = remember(uiState.tandas, activeJornada) {
        val allClosed = uiState.tandas.filter {
            it.status == "CERRADA" || it.status == "FINALIZADA" || it.status == "PROCESADA" || it.status == "ARCHIVADA"
        }
        val jorClosed = if (activeJornada != null) {
            allClosed.filter {
                it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt)
            }
        } else emptyList()

        val list = if (jorClosed.isNotEmpty()) jorClosed else allClosed
        list.sortedWith(
            compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.tandaNumber }
                .thenBy { it.date }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ENCABEZADO: Flecha ← y texto TANDAS DEL DÍA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onBack?.invoke() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tandas_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = ElQadreNavy,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TANDAS DEL DÍA",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy,
                letterSpacing = 0.5.sp
            )
        }

        // Justo debajo de la flecha/texto: NUEVA | IMPORTAR | ARCHIVO
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NUEVA
            Button(
                onClick = {
                    val isJornadaOpen = uiState.activeJornada != null && uiState.activeJornada.isOpen
                    if (isJornadaOpen) {
                        showAgregarTandaDialog = true
                    } else {
                        showJornadaCerradaDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("nueva_tanda_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NUEVA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }

            // IMPORTAR
            Button(
                onClick = {
                    val isJornadaOpen = uiState.activeJornada != null && uiState.activeJornada.isOpen
                    if (isJornadaOpen) {
                        showImportarDialog = true
                    } else {
                        showJornadaCerradaDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ElQadreNavy
                ),
                border = BorderStroke(1.5.dp, ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("importar_tandas_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = ElQadreNavy
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "IMPORTAR",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    letterSpacing = 0.5.sp
                )
            }

            // ARCHIVO
            Button(
                onClick = { showingArchivoScreen = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ElQadreNavy
                ),
                border = BorderStroke(1.5.dp, ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("tandas_archivo_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = ElQadreNavy
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ARCHIVO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Apartado de Unidades Pendientes de la Jornada Anterior
        TandasUnidadesPendientesSection(
            uiState = uiState,
            viewModel = viewModel,
            onShowJornadaCerradaDialog = { showJornadaCerradaDialog = true }
        )

        // 2. TANDAS ABIERTAS: Una tarjeta por producto con tandas abiertas
        if (productSummaries.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Emerald600,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No hay tandas abiertas en la jornada",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                productSummaries.forEach { summary ->
                    ProductTandasOpenCard(
                        summary = summary,
                        onClick = {
                            selectedProductForDetail = summary
                        }
                    )
                }
            }
        }

        // 3. TANDAS CERRADAS RESUMIDAS
        HorizontalDivider(
            color = Slate200,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        Text(
            text = "TANDAS CERRADAS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = ElQadreNavy,
            letterSpacing = 0.5.sp
        )

        if (closedTandas.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No hay tandas cerradas registradas",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                closedTandas.forEach { tanda ->
                    ClosedTandaCompactCard(tanda = tanda)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. GRÁFICO CIRCULAR — PARTICIPACIÓN DE PRODUCCIÓN
            TandasPieChartSection(closedTandas = closedTandas)

            Spacer(modifier = Modifier.height(8.dp))

            // 5. GRÁFICO DE BARRAS — EFICIENCIA POR TANDA
            TandasEfficiencyBarChartSection(closedTandas = closedTandas)

            Spacer(modifier = Modifier.height(12.dp))

            // 6. BOTÓN PARA MANDAR A ARCHIVO
            Button(
                onClick = {
                    showArchivarPendientesDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("btn_mandar_a_archivo")
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MANDAR A ARCHIVO",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Advertencia cuando se intenta crear una tanda sin jornada abierta
    if (showJornadaCerradaDialog) {
        AlertDialog(
            onDismissRequest = { showJornadaCerradaDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Rose700,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "JORNADA CERRADA",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "No se puede crear una nueva tanda porque no hay una jornada abierta.\n\nPor favor, primero abra la jornada para poder registrar tandas de producción.",
                    fontSize = 15.sp,
                    color = Slate700,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showJornadaCerradaDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_entendido_jornada_cerrada")
                ) {
                    Text(
                        text = "Entendido",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal para registrar nueva tanda (formulario limpio y accesible para adultos mayores)
    if (showAgregarTandaDialog) {
        NuevaTandaDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showAgregarTandaDialog = false }
        )
    }

    // Modal para importar tandas desde texto
    if (showImportarDialog) {
        ImportarTandasDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showImportarDialog = false }
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

    if (showArchivarPendientesDialog) {
        ArchivarTandasPendientesDialog(
            closedTandas = closedTandas,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showArchivarPendientesDialog = false },
            onArchivedConfirmed = {
                showArchivarPendientesDialog = false
            }
        )
    }
}

// =================================================================
// TARJETA DE PRODUCTO CON TANDAS ABIERTAS (MÁXIMA ACCESIBILIDAD)
// =================================================================
@Composable
fun ProductTandasOpenCard(
    summary: ProductTandasSummary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, ElQadreNavy.copy(alpha = 0.15f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tanda_product_card_${summary.productId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Nombre del producto
                Text(
                    text = summary.productName.uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 2. Indicador claro de TANDA ABIERTA
                Surface(
                    color = Color(0xFF0F766E).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF0F766E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF0F766E), shape = RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = if (summary.tandasCount > 1) "${summary.tandasCount} TANDAS ABIERTAS" else "TANDA ABIERTA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F766E),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledTonalIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Slate100,
                    contentColor = ElQadreNavy
                ),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Ver detalle",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =================================================================
// PALETA Y COMPONENTES PARA TANDAS CERRADAS Y GRÁFICOS
// =================================================================
val TANDA_COLOR_PALETTE = listOf(
    Color(0xFF1E3A8A), // Azul marino / Indigo
    Color(0xFF0D9488), // Teal
    Color(0xFFD97706), // Ámbar / Dorado
    Color(0xFF2563EB), // Azul rey
    Color(0xFF059669), // Verde esmeralda
    Color(0xFFDC2626)  // Rojo carmesí
)

@Composable
fun ClosedTandaCompactCard(tanda: Tanda) {
    val baseQtyStr = if (tanda.baseQuantityUsed % 1.0 == 0.0) tanda.baseQuantityUsed.toInt().toString() else "%.1f".format(tanda.baseQuantityUsed)
    val baseUnitStr = tanda.baseQuantityUnit.ifBlank { "lb" }
    val actualYieldStr = if (tanda.actualYield % 1.0 == 0.0) tanda.actualYield.toInt().toString() else "%.1f".format(tanda.actualYield)
    val prodUnitStr = tanda.productionUnit.ifBlank { "unidades" }
    val costStr = "$${"%.2f".format(tanda.totalBatchCost)}"
    val tNumFormatted = tanda.tandaNumber.padStart(2, '0')

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Tanda $tNumFormatted",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy
            )
            Text(
                text = "$baseQtyStr $baseUnitStr → $actualYieldStr $prodUnitStr → $costStr",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Slate700
            )
        }
    }
}

@Composable
fun TandasPieChartSection(closedTandas: List<Tanda>) {
    if (closedTandas.isEmpty()) return

    val totalProduction = closedTandas.sumOf { it.actualYield }
    if (totalProduction <= 0.0) return

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
            Text(
                text = "PARTICIPACIÓN DE PRODUCCIÓN",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy,
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        closedTandas.forEachIndexed { index, tanda ->
                            val color = TANDA_COLOR_PALETTE[index % TANDA_COLOR_PALETTE.size]
                            val sweepAngle = ((tanda.actualYield / totalProduction) * 360f).toFloat()
                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    closedTandas.forEachIndexed { index, tanda ->
                        val color = TANDA_COLOR_PALETTE[index % TANDA_COLOR_PALETTE.size]
                        val yieldFormatted = if (tanda.actualYield % 1.0 == 0.0) tanda.actualYield.toInt().toString() else "%.1f".format(tanda.actualYield)
                        val percentage = (tanda.actualYield / totalProduction) * 100.0
                        val tNumFormatted = tanda.tandaNumber.padStart(2, '0')

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                            Text(
                                text = "Tanda $tNumFormatted: $yieldFormatted ${tanda.productionUnit.ifBlank { "u" }} (${"%.1f".format(percentage)}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class EfficiencyBarData(
    val tandaLabel: String,
    val coefficient: Double,
    val coeffText: String,
    val barColor: Color
)

@Composable
fun TandasEfficiencyBarChartSection(closedTandas: List<Tanda>) {
    if (closedTandas.isEmpty()) return

    val efficiencyItems = closedTandas.mapIndexed { index, tanda ->
        val baseQty = tanda.baseQuantityUsed
        val actualYield = tanda.actualYield
        val coeff = if (baseQty > 0.0) actualYield / baseQty else 0.0
        val color = TANDA_COLOR_PALETTE[index % TANDA_COLOR_PALETTE.size]
        val pUnit = tanda.productionUnit.ifBlank { "unidades" }
        val bUnit = tanda.baseQuantityUnit.ifBlank { "lb" }
        val tNumFormatted = tanda.tandaNumber.padStart(2, '0')
        val coeffFormatted = if (coeff % 1.0 == 0.0) coeff.toInt().toString() else "%.1f".format(coeff)

        EfficiencyBarData(
            tandaLabel = "Tanda $tNumFormatted",
            coefficient = coeff,
            coeffText = "$coeffFormatted $pUnit por $bUnit",
            barColor = color
        )
    }

    val maxCoeff = efficiencyItems.maxOfOrNull { it.coefficient }?.coerceAtLeast(1.0) ?: 1.0

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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "GRÁFICO DE EFICIENCIA POR TANDA",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Eficiencia = Producción real ÷ Cantidad de insumo base",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                efficiencyItems.forEach { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.tandaLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = item.coeffText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = item.barColor
                            )
                        }

                        val fraction = (item.coefficient / maxCoeff).toFloat().coerceIn(0.04f, 1.0f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate100)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(item.barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================================
// DIÁLOGO PARA ARCHIVAR TANDAS Y REGISTRAR UNIDADES PENDIENTES
// =================================================================
@Composable
fun ArchivarTandasPendientesDialog(
    closedTandas: List<Tanda>,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onArchivedConfirmed: () -> Unit
) {
    val productMap = remember(uiState.products) { uiState.products.associateBy { it.id } }

    val groupedByProduct = remember(closedTandas, productMap) {
        closedTandas.groupBy { it.productId }.map { (prodId, tandasList) ->
            val product = productMap[prodId] ?: Product(
                id = prodId,
                code = "P$prodId",
                name = tandasList.firstOrNull()?.productName ?: "Producto #$prodId",
                category = "General",
                price = 0.0,
                unitOfMeasure = tandasList.firstOrNull()?.productionUnit ?: "Unidad"
            )
            val specialPresentations = parsePresentacionesEspeciales(product.presentacionesEspeciales)
            Triple(product, tandasList, specialPresentations)
        }.sortedBy { it.first.name.lowercase() }
    }

    val inputStates = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "UNIDADES PENDIENTES",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
                Text(
                    text = "Indica las unidades producidas que no se vendieron y quedaron pendientes para la siguiente jornada.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (groupedByProduct.isEmpty()) {
                    Text(
                        text = "No hay tandas cerradas para archivar.",
                        fontSize = 14.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                } else {
                    groupedByProduct.forEach { (product, tandasList, specialPresentations) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = product.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )

                                val baseKey = "base_${product.id}"
                                val baseVal = inputStates[baseKey] ?: ""

                                OutlinedTextField(
                                    value = baseVal,
                                    onValueChange = { inputStates[baseKey] = it },
                                    label = { Text("Unidades pendientes (${product.unitOfMeasure.ifBlank { "Unidad" }})") },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_pendientes_base_${product.id}")
                                )

                                if (specialPresentations.isNotEmpty()) {
                                    Text(
                                        text = "Presentación Especial (Ficha de Costo):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate600,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )

                                    specialPresentations.forEach { pres ->
                                        val presKey = "pres_${product.id}_${pres.id.ifBlank { pres.name }}"
                                        val presVal = inputStates[presKey] ?: ""

                                        OutlinedTextField(
                                            value = presVal,
                                            onValueChange = { inputStates[presKey] = it },
                                            label = { Text("Unidades en ${pres.name} (eq: ${pres.baseEquivalence} base)") },
                                            placeholder = { Text("0") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_pendientes_pres_${product.id}_${pres.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedTandas = mutableListOf<Tanda>()
                    val summaryList = mutableListOf<String>()

                    groupedByProduct.forEach { (product, tandasList, specialPresentations) ->
                        val baseKey = "base_${product.id}"
                        val baseQty = inputStates[baseKey]?.toDoubleOrNull() ?: 0.0

                        val presDetails = mutableListOf<String>()
                        specialPresentations.forEach { pres ->
                            val presKey = "pres_${product.id}_${pres.id.ifBlank { pres.name }}"
                            val presQty = inputStates[presKey]?.toDoubleOrNull() ?: 0.0
                            if (presQty > 0.0) {
                                val presQtyStr = if (presQty % 1.0 == 0.0) presQty.toInt().toString() else presQty.toString()
                                presDetails.add("${pres.name}: $presQtyStr u")
                            }
                        }

                        val pendingStr = StringBuilder()
                        if (baseQty > 0.0) {
                            val baseQtyStr = if (baseQty % 1.0 == 0.0) baseQty.toInt().toString() else baseQty.toString()
                            pendingStr.append("$baseQtyStr ${product.unitOfMeasure}")
                        }
                        if (presDetails.isNotEmpty()) {
                            if (pendingStr.isNotEmpty()) pendingStr.append(" | ")
                            pendingStr.append(presDetails.joinToString(", "))
                        }

                        val noteTag = if (pendingStr.isNotEmpty()) {
                            "Pendientes: $pendingStr"
                        } else {
                            "Pendientes: 0"
                        }

                        summaryList.add("${product.name} -> $noteTag")

                        tandasList.forEach { tanda ->
                            val existingObs = tanda.observation
                            val newObs = if (existingObs.isNotBlank()) {
                                if (existingObs.contains("Pendientes:")) existingObs else "$existingObs | $noteTag"
                            } else {
                                noteTag
                            }
                            val updatedTanda = tanda.copy(
                                status = "ARCHIVADA",
                                observation = newObs
                            )
                            updatedTandas.add(updatedTanda)
                        }
                    }

                    val bitacoraSummary = "Se archivaron ${updatedTandas.size} tandas. Registro de unidades pendientes:\n" + summaryList.joinToString("\n")
                    viewModel.archivarTandasConUnidadesPendientes(updatedTandas, bitacoraSummary)
                    onArchivedConfirmed()
                },
                enabled = groupedByProduct.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirmar_archivar_pendientes")
            ) {
                Text(
                    text = "CONFIRMAR Y ARCHIVAR",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// =================================================================
// ITEM DE TANDA ABIERTA (INFORMACIÓN ESENCIAL Y BOTÓN CERRAR TANDA)
// =================================================================
@Composable
fun OpenTandaItemCard(
    tanda: Tanda,
    onCloseTanda: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Identificada como ABIERTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF0F766E),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ABIERTA",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Tanda #${tanda.tandaNumber}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
            }

            // Información esencial:
            // 1. Cantidad de insumo base
            // 2. Cantidad de producto esperada
            // 3. Costo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Insumo base
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = "Insumo base",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500
                    )
                    Text(
                        text = "${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                    if (tanda.baseMateriaPrimaName.isNotBlank()) {
                        Text(
                            text = tanda.baseMateriaPrimaName,
                            fontSize = 11.sp,
                            color = Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Cantidad de producto esperada
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = "Esperado",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500
                    )
                    val yieldText = if (tanda.expectedYield % 1.0 == 0.0) {
                        "${tanda.expectedYield.toInt()} ${tanda.productionUnit}"
                    } else {
                        "${"%.1f".format(tanda.expectedYield)} ${tanda.productionUnit}"
                    }
                    Text(
                        text = yieldText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0284C7)
                    )
                }

                // Costo
                Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Costo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500
                    )
                    Text(
                        text = "$${"%.2f".format(tanda.totalBatchCost)} CUP",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Rose700
                    )
                }
            }

            // Botón grande: CERRAR TANDA
            Button(
                onClick = onCloseTanda,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_cerrar_tanda_${tanda.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CERRAR TANDA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

// =================================================================
// 1. DETALLE DE LAS TANDAS DE UN PRODUCTO (PANTALLA COMPLETA)
// =================================================================
@Composable
fun ProductTandasDetailScreen(
    summary: ProductTandasSummary,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onCloseTanda: (Tanda) -> Unit = {},
    onBack: () -> Unit
) {
    // Obtenemos el producto y su información
    val product = remember(summary.productId, uiState.products) {
        uiState.products.find { it.id == summary.productId }
    }

    // Tandas de este producto ordenadas secuencialmente (Tanda 01, Tanda 02...)
    val productTandas = remember(summary.productId, uiState.tandas, uiState.activeJornada) {
        val allForProduct = uiState.tandas.filter { it.productId == summary.productId }
        val activeJ = uiState.activeJornada
        val forJornada = if (activeJ != null) {
            allForProduct.filter { it.jornadaId == activeJ.id || (activeJ.openedAt > 0 && it.date >= activeJ.openedAt) }
        } else emptyList()

        val list = when {
            forJornada.isNotEmpty() -> forJornada
            summary.tandas.isNotEmpty() -> summary.tandas
            else -> allForProduct
        }
        list.sortedWith(
            compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.tandaNumber }
                .thenBy { it.date }
        )
    }

    Scaffold(
        containerColor = Slate50,
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TANDA ABIERTA",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F766E),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = (product?.name ?: summary.productName).uppercase(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (productTandas.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay tandas registradas para este producto.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                productTandas.forEach { tanda ->
                    val isOpen = tanda.status == "ABIERTA" || tanda.status == "ACTIVA" || tanda.status == "ACTIVADA"
                    if (isOpen) {
                        // TARJETA DE TANDA ABIERTA
                        TandaAbiertaCard(
                            tanda = tanda,
                            fallbackProductionUnit = summary.productionUnit,
                            onCerrarTanda = { onCloseTanda(tanda) }
                        )
                    } else {
                        // TARJETA DE TANDA CERRADA
                        TandaCerradaCard(
                            tanda = tanda,
                            fallbackProductionUnit = summary.productionUnit
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =================================================================
// TARJETA DE TANDA ABIERTA (SOLO INSUMO BASE, ESPERADA Y BOTÓN CERRAR)
// =================================================================
@Composable
fun TandaAbiertaCard(
    tanda: Tanda,
    fallbackProductionUnit: String,
    onCerrarTanda: () -> Unit
) {
    val prodUnit = tanda.productionUnit.ifBlank { fallbackProductionUnit }.ifBlank { "u" }
    val baseUnit = tanda.baseQuantityUnit.ifBlank { "lb" }
    val baseQtyFormatted = if (tanda.baseQuantityUsed % 1.0 == 0.0) {
        tanda.baseQuantityUsed.toInt().toString()
    } else {
        "%.2f".format(tanda.baseQuantityUsed)
    }
    val expYield = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
    val expYieldFormatted = if (expYield % 1.0 == 0.0) {
        expYield.toInt().toString()
    } else {
        "%.1f".format(expYield)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Color(0xFF0F766E).copy(alpha = 0.35f)),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tanda_abierta_card_${tanda.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val isTanda00 = tanda.tandaNumber == "00"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTanda00) "TANDA 00 — UNIDADES PENDIENTES" else "TANDA ${tanda.tandaNumber}",
                    fontSize = if (isTanda00) 18.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isTanda00) Color(0xFF92400E) else ElQadreNavy
                )
                Surface(
                    color = Color(0xFF0F766E).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF0F766E))
                ) {
                    Text(
                        text = "ABIERTA",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F766E),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate200, thickness = 1.dp)

            val isFromPending = isTanda00 ||
                    tanda.observation.contains("UNIDADES PENDIENTES") ||
                    tanda.observation.contains("unidades pendientes") ||
                    tanda.observation.contains("ORIGEN_PENDIENTE_JORNADA")
            if (isFromPending) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isTanda00) "TANDA 00 — PROCEDENTE DE UNIDADES PENDIENTES (SIN CONSUMO DE INSUMOS)" else "PROCEDENTE DE UNIDADES PENDIENTES DE LA JORNADA ANTERIOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Cantidad de insumo base
                    Column {
                        Text(
                            text = if (isTanda00) "Insumo base equivalente (informativo)" else "Cantidad de insumo base",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                        Text(
                            text = "$baseQtyFormatted $baseUnit (${tanda.baseMateriaPrimaName})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        if (isTanda00) {
                            Text(
                                text = "Sin consumo de inventario (insumos registrados en jornada previa)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Emerald700
                            )
                        }
                    }

                    // 2. Producción esperada
                    Column {
                        Text(
                            text = "Producción esperada",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                        Text(
                            text = "$expYieldFormatted $prodUnit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Botón grande a la derecha: CERRAR TANDA
                Button(
                    onClick = onCerrarTanda,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(68.dp)
                        .testTag("btn_cerrar_tanda_${tanda.id}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "CERRAR\nTANDA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// =================================================================
// TARJETA DE TANDA CERRADA (SOLO DATOS ESENCIALES)
// =================================================================
@Composable
fun TandaCerradaCard(
    tanda: Tanda,
    fallbackProductionUnit: String
) {
    val prodUnit = tanda.productionUnit.ifBlank { fallbackProductionUnit }.ifBlank { "u" }
    val baseUnit = tanda.baseQuantityUnit.ifBlank { "lb" }
    val baseQtyFormatted = if (tanda.baseQuantityUsed % 1.0 == 0.0) {
        tanda.baseQuantityUsed.toInt().toString()
    } else {
        "%.2f".format(tanda.baseQuantityUsed)
    }
    val expYield = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
    val expYieldFormatted = if (expYield % 1.0 == 0.0) {
        expYield.toInt().toString()
    } else {
        "%.1f".format(expYield)
    }
    val actYield = tanda.actualYield
    val actYieldFormatted = if (actYield % 1.0 == 0.0) {
        actYield.toInt().toString()
    } else {
        "%.1f".format(actYield)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, Slate200),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tanda_cerrada_card_${tanda.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isTanda00 = tanda.tandaNumber == "00"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTanda00) "TANDA 00 — UNIDADES PENDIENTES" else "TANDA ${tanda.tandaNumber}",
                    fontSize = if (isTanda00) 18.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isTanda00) Color(0xFF92400E) else ElQadreNavy
                )
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text(
                        text = "CERRADA",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate200, thickness = 1.dp)

            val isFromPending = isTanda00 ||
                    tanda.observation.contains("UNIDADES PENDIENTES") ||
                    tanda.observation.contains("unidades pendientes") ||
                    tanda.observation.contains("ORIGEN_PENDIENTE_JORNADA")
            if (isFromPending) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isTanda00) "TANDA 00 — PROCEDENTE DE UNIDADES PENDIENTES (SIN CONSUMO DE INSUMOS)" else "PROCEDENTE DE UNIDADES PENDIENTES DE LA JORNADA ANTERIOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // 1. Cantidad de insumo base
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTanda00) "Insumo base equivalente (informativo)" else "Cantidad de insumo base",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600
                )
                Text(
                    text = "$baseQtyFormatted $baseUnit",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
            }

            // 2. Producción esperada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Producción esperada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600
                )
                Text(
                    text = "$expYieldFormatted $prodUnit",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0284C7)
                )
            }

            // 3. Producción real
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Producción real",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600
                )
                Text(
                    text = "$actYieldFormatted $prodUnit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F766E)
                )
            }

            // 4. Costos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Costos",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total: $${"%.2f".format(tanda.totalBatchCost)} CUP",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Rose700
                    )
                    if (tanda.realUnitCost > 0.0) {
                        Text(
                            text = "Unitario: $${"%.2f".format(tanda.realUnitCost)} CUP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                    }
                }
            }

            if (tanda.observation.isNotBlank()) {
                Surface(
                    color = Slate50,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tanda.observation,
                        fontSize = 12.sp,
                        color = Slate700,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// =================================================================
// 2. APARTADO / PANTALLA INDEPENDIENTE DE ARCHIVO DE TANDAS
// =================================================================
@Composable
fun TandasArchivoScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedJornadaId by remember { mutableStateOf<Long?>(null) }
    var jornadaToDelete by remember { mutableStateOf<Pair<Jornada, List<Tanda>>?>(null) }
    var showLimpiarTodoDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val resolveSalePrice: (Product?, Tanda) -> Double = { prod, tanda ->
        val costSheet = prod?.let { CostCalculationHelper.calculateCostSheet(it, uiState) }
        if (costSheet != null && costSheet.precioDefinitivo > 0.0) {
            costSheet.precioDefinitivo
        } else {
            prod?.price ?: tanda.salePrice
        }
    }

    // 3. ARCHIVO POR JORNADA: Organizar la información jornada por jornada
    val jornadasConTandas = remember(uiState.allJornadas, uiState.tandas) {
        val sortedJornadas = uiState.allJornadas.sortedByDescending { it.openedAt }
        val matchedTandaIds = sortedJornadas.flatMap { j ->
            uiState.tandas.filter { it.jornadaId == j.id || (j.openedAt > 0 && it.date >= j.openedAt && (j.closedAt == null || it.date <= (j.closedAt + 3600000L))) }.map { it.id }
        }.toSet()
        val orphanTandas = uiState.tandas.filter { it.id !in matchedTandaIds }

        val list = mutableListOf<Pair<Jornada, List<Tanda>>>()
        sortedJornadas.forEach { j ->
            val jTandas = uiState.tandas.filter { tanda ->
                tanda.jornadaId == j.id || (j.openedAt > 0 && tanda.date >= j.openedAt && (j.closedAt == null || tanda.date <= (j.closedAt + 3600000L)))
            }.sortedWith(
                compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.tandaNumber }
                    .thenBy { it.date }
            )
            if (jTandas.isNotEmpty()) {
                list.add(j to jTandas)
            }
        }

        // Si existen tandas históricas registradas previamente sin jornada asociada
        if (orphanTandas.isNotEmpty()) {
            val virtualJornada = Jornada(
                id = 0L,
                openedAt = orphanTandas.minOfOrNull { it.date } ?: System.currentTimeMillis(),
                closedAt = orphanTandas.maxOfOrNull { it.date },
                isOpen = false,
                openedBy = "Sistema",
                notes = "Tandas registradas sin jornada asignada"
            )
            list.add(virtualJornada to orphanTandas.sortedWith(
                compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.tandaNumber }
                    .thenBy { it.date }
            ))
        }
        list
    }

    val selectedPair = remember(selectedJornadaId, jornadasConTandas) {
        jornadasConTandas.find { it.first.id == selectedJornadaId }
    }

    if (selectedPair != null) {
        // 4. INFORMACIÓN DE CADA JORNADA: Detalle de tandas organizadas por producto
        val jornada = selectedPair.first
        val tandasDeJornada = selectedPair.second
        val productMap = remember(uiState.products) { uiState.products.associateBy { it.id } }

        val productsWithTandas = remember(tandasDeJornada, productMap) {
            tandasDeJornada.groupBy { it.productId }.map { (prodId, pTandas) ->
                val prod = productMap[prodId]
                val pName = prod?.name ?: pTandas.firstOrNull()?.productName ?: "Producto #$prodId"
                val pUnit = pTandas.firstOrNull()?.productionUnit?.ifBlank { null } ?: "u"
                val sorted = pTandas.sortedWith(
                    compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.tandaNumber }
                        .thenBy { it.date }
                )
                Triple(pName, pUnit, sorted)
            }.sortedBy { it.first.lowercase() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header con flecha de retorno y botón DESCARGAR PDF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { selectedJornadaId = null },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_back_to_jornadas_list")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al listado de jornadas",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (jornada.id == 0L) "JORNADA GENERAL" else "JORNADA #${jornada.id}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Text(
                            text = if (jornada.openedAt > 0) dateOnlyFormatter.format(Date(jornada.openedAt)) else "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                    }
                }

                // 5. PDF DE LA JORNADA: Opción DESCARGAR PDF
                Button(
                    onClick = {
                        TandasJornadaPdfExporter.generateAndShareJornadaPdf(
                            context = context,
                            jornada = jornada,
                            products = uiState.products,
                            tandas = tandasDeJornada,
                            businessName = uiState.businessName,
                            resolveSalePrice = resolveSalePrice
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald600,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("btn_descargar_pdf_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DESCARGAR PDF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Tarjeta de resumen de la Jornada
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RESUMEN DE LA JORNADA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Apertura: ${if (jornada.openedAt > 0) dateFormatter.format(Date(jornada.openedAt)) else "N/A"}",
                            fontSize = 13.sp,
                            color = Slate700
                        )
                        if (jornada.closedAt != null && jornada.closedAt > 0) {
                            Text(
                                text = "Cierre: ${dateFormatter.format(Date(jornada.closedAt))}",
                                fontSize = 13.sp,
                                color = Slate700
                            )
                        } else {
                            Text(
                                text = if (jornada.isOpen) "En curso" else "Cerrada",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (jornada.isOpen) Emerald700 else Slate600
                            )
                        }
                    }
                    HorizontalDivider(color = Slate100, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Productos elaborados: ${productsWithTandas.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Total tandas: ${tandasDeJornada.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }
                }
            }

            // Tarjeta de Unidades Pendientes al Cierre
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "UNIDADES PENDIENTES AL CIERRE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            letterSpacing = 0.5.sp
                        )
                    }

                    HorizontalDivider(color = Slate100, thickness = 1.dp)

                    val pendingMap = remember(productsWithTandas) {
                        productsWithTandas.map { (prodName, _, pTandas) ->
                            val pStr = pTandas.firstNotNullOfOrNull { tanda ->
                                if (tanda.observation.contains("Pendientes:")) {
                                    val part = tanda.observation.substringAfter("Pendientes:").trim()
                                    if (part.isNotBlank() && part != "0") part else null
                                } else null
                            }
                            prodName to pStr
                        }
                    }

                    val hasAnyPendingInUi = pendingMap.any { it.second != null }

                    if (!hasAnyPendingInUi) {
                        Text(
                            text = "Sin unidades pendientes.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                    } else {
                        pendingMap.forEach { (prodName, pStr) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prodName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                Text(
                                    text = pStr ?: "Sin unidades pendientes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (pStr != null) Emerald700 else Slate500
                                )
                            }
                        }
                    }
                }
            }

            // Mostrar las tandas organizadas por producto
            productsWithTandas.forEach { (prodName, prodUnit, tandasDelProducto) ->
                val prod = productMap.values.find { it.name.equals(prodName, ignoreCase = true) }

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
                        // Encabezado del producto
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRODUCTO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = prodName.uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                )
                            }
                            Surface(
                                color = Slate100,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${tandasDelProducto.size} ${if (tandasDelProducto.size == 1) "TANDA" else "TANDAS"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Slate200, thickness = 1.dp)

                        // Tandas correspondientes una debajo de otra
                        tandasDelProducto.forEach { tanda ->
                            TandaCerradaCard(
                                tanda = tanda,
                                fallbackProductionUnit = prodUnit
                            )
                        }
                    }
                }
            }

            // 5. Botón grande para DESCARGAR PDF de la jornada
            Button(
                onClick = {
                    TandasJornadaPdfExporter.generateAndShareJornadaPdf(
                        context = context,
                        jornada = jornada,
                        products = uiState.products,
                        tandas = tandasDeJornada,
                        businessName = uiState.businessName,
                        resolveSalePrice = resolveSalePrice
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald600,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("btn_descargar_pdf_bottom")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DESCARGAR PDF DE LA JORNADA",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    } else {
        // 3. ARCHIVO POR JORNADA: Listado de jornadas archivadas
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header del Archivo con flecha para regresar y opción de LIMPIAR TODO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_back_to_tandas_main")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar a Tandas",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ARCHIVO DE TANDAS",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Historial organizado jornada por jornada",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                    }
                }

                if (jornadasConTandas.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showLimpiarTodoDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose600),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_limpiar_todo_archivo_tandas")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose600)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("LIMPIAR TODO", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rose600)
                    }
                }
            }

            if (jornadasConTandas.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No hay jornadas archivadas con tandas todavía.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                jornadasConTandas.forEach { (jornada, tandasList) ->
                    val distinctProductsCount = tandasList.map { it.productId }.distinct().size
                    val totalTandasCount = tandasList.size
                    val totalCost = tandasList.sumOf { it.totalBatchCost }
                    val totalRev = tandasList.sumOf {
                        val prod = uiState.products.find { p -> p.id == it.productId }
                        val sp = resolveSalePrice(prod, it)
                        val fQty = if (it.actualYield > 0.0) it.actualYield else if (it.expectedYield > 0.0) it.expectedYield else it.estimatedYield
                        fQty * sp
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedJornadaId = jornada.id }
                            .testTag("jornada_card_${jornada.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cabecera de la jornada
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = if (jornada.id == 0L) "JORNADA GENERAL" else "JORNADA #${jornada.id}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                }

                                Surface(
                                    color = if (jornada.isOpen) Color(0xFFDCFCE7) else Slate100,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (jornada.isOpen) "EN CURSO" else "CERRADA",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (jornada.isOpen) Emerald700 else Slate600,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Fechas
                            val apText = if (jornada.openedAt > 0) dateFormatter.format(Date(jornada.openedAt)) else "No disponible"
                            val ciText = if (jornada.closedAt != null && jornada.closedAt > 0) dateFormatter.format(Date(jornada.closedAt)) else null

                            Text(
                                text = "Apertura: $apText${if (ciText != null) " • Cierre: $ciText" else ""}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )

                            HorizontalDivider(color = Slate100, thickness = 1.dp)

                            // Métricas principales
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("PRODUCTOS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$distinctProductsCount", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }
                                Column {
                                    Text("TOTAL TANDAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$totalTandasCount", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("COSTO TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$${"%.2f".format(totalCost)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Rose600)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("ING. POTENCIAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text("$${"%.2f".format(totalRev)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                }
                            }

                            HorizontalDivider(color = Slate100, thickness = 1.dp)

                            // Botones de acción: DESCARGAR PDF, CONSULTAR y ELIMINAR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        TandasJornadaPdfExporter.generateAndShareJornadaPdf(
                                            context = context,
                                            jornada = jornada,
                                            products = uiState.products,
                                            tandas = tandasList,
                                            businessName = uiState.businessName,
                                            resolveSalePrice = resolveSalePrice
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Emerald600,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("btn_pdf_jornada_${jornada.id}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }

                                Button(
                                    onClick = { selectedJornadaId = jornada.id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElQadreNavy,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(46.dp)
                                        .testTag("btn_ver_tandas_jornada_${jornada.id}")
                                ) {
                                    Text("CONSULTAR", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                                Button(
                                    onClick = { jornadaToDelete = jornada to tandasList },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Rose600,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(46.dp)
                                        .testTag("btn_eliminar_jornada_${jornada.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ELIMINAR", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog: Advertencia para ELIMINAR UNA JORNADA
    jornadaToDelete?.let { (jornada, tandas) ->
        AlertDialog(
            onDismissRequest = { jornadaToDelete = null },
            title = {
                Text(
                    text = "ADVERTENCIA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Rose600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Se eliminarán todos los datos de esta jornada y sus tandas.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate800,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Descargue primero el PDF si desea conservar esta información.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pair = jornadaToDelete
                        jornadaToDelete = null
                        if (pair != null) {
                            viewModel.eliminarJornadaArchivadaDeTandas(pair.first.id, pair.second)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_confirmar_eliminar_jornada_archivo")
                ) {
                    Text("ELIMINAR", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { jornadaToDelete = null }) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog: Advertencia para LIMPIAR TODO EL ARCHIVO
    if (showLimpiarTodoDialog) {
        AlertDialog(
            onDismissRequest = { showLimpiarTodoDialog = false },
            title = {
                Text(
                    text = "ADVERTENCIA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Rose600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Se eliminarán todos los datos de todas las jornadas y tandas archivadas.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate800,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Descargue primero los PDF que desee conservar.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLimpiarTodoDialog = false
                        viewModel.limpiarTodoElArchivoDeTandas(jornadasConTandas)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_confirmar_limpiar_todo_archivo")
                ) {
                    Text("ELIMINAR TODO", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimpiarTodoDialog = false }) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
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
                    val isTanda00 = tanda.tandaNumber == "00"
                    Surface(
                        color = if (isTanda00) Color(0xFFFEF3C7) else Amber100,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isTanda00) "Tanda 00 (Pendientes)" else "Tanda ${tanda.tandaNumber.ifEmpty { "01" }}",
                            color = if (isTanda00) Color(0xFF92400E) else Amber700,
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
                    val isTanda00 = tanda.tandaNumber == "00"
                    Surface(
                        color = if (isTanda00) Color(0xFFFEF3C7) else if (isClosed) ElQadreGoldSoft else Amber100,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isTanda00) "Tanda 00 (Pendientes)" else "Tanda ${tanda.tandaNumber.ifEmpty { "01" }}",
                            color = if (isTanda00) Color(0xFF92400E) else ElQadreNavy,
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

            val isFromPending = tanda.observation.contains("UNIDADES PENDIENTES") ||
                    tanda.observation.contains("unidades pendientes") ||
                    tanda.observation.contains("ORIGEN_PENDIENTE_JORNADA")
            if (isFromPending) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Procedente de unidades pendientes de la jornada anterior",
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
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
            uiState = uiState
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
            uiState = uiState
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
                if (tanda.tandaNumber == "00") {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TANDA 00 — UNIDADES PENDIENTES",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Esta tanda no consume insumos del inventario actual. Su edición no altera consumos ni stock.",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }

                Surface(color = Slate50, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Producto: ${tanda.productName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                        Text("Ingrediente Base: ${tanda.baseMateriaPrimaName}", fontSize = 11.sp, color = Slate700)
                    }
                }

                if (tanda.tandaNumber != "00") {
                    OutlinedTextField(
                        value = baseQuantityInputText,
                        onValueChange = { baseQuantityInputText = it },
                        label = { Text("Nueva Cantidad Base (${tanda.baseQuantityUnit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy),
                        modifier = Modifier.fillMaxWidth().testTag("edit_base_qty_input")
                    )
                }

                OutlinedTextField(
                    value = observationText,
                    onValueChange = { observationText = it },
                    label = { Text("Observaciones / Corrección") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (tanda.tandaNumber != "00") {
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
            }
        },
        confirmButton = {
            if (tanda.tandaNumber == "00") {
                Button(
                    onClick = {
                        val updatedTanda = tanda.copy(observation = observationText.trim())
                        viewModel.editarTandaActiva(tanda, updatedTanda, emptyList(), emptyList())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.testTag("confirm_edit_tanda_00")
                ) {
                    Text("Guardar Cambios (Sin Ajuste de Stock)", fontWeight = FontWeight.Bold)
                }
            } else {
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
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Slate500) }
        }
    )
}

// =================================================================
// 6. MODAL CERRAR TANDA (GRANDE, SENCILLO, CENTRADO Y ACCESIBLE)
// =================================================================
@Composable
fun CerrarTandaDialog(
    tanda: Tanda,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = remember(tanda.productId, uiState.products) {
        uiState.products.find { it.id == tanda.productId }
    }
    val presentaciones: List<PresentacionEspecial> = remember(product) {
        product?.let { parsePresentacionesEspeciales(it.presentacionesEspeciales) } ?: emptyList()
    }

    var selectedPresentacion by remember { mutableStateOf<PresentacionEspecial?>(null) }
    var cantidadRealProducidaText by remember {
        mutableStateOf(
            if (tanda.actualYield > 0.0) {
                if (tanda.actualYield % 1.0 == 0.0) tanda.actualYield.toInt().toString() else "%.1f".format(tanda.actualYield)
            } else if (tanda.expectedYield > 0.0) {
                if (tanda.expectedYield % 1.0 == 0.0) tanda.expectedYield.toInt().toString() else "%.1f".format(tanda.expectedYield)
            } else ""
        )
    }
    var cantidadPresentacionText by remember { mutableStateOf("") }

    val finalQty = cantidadRealProducidaText.toDoubleOrNull() ?: 0.0
    val rendimientoCalculado = if (tanda.baseQuantityUsed > 0.0) finalQty / tanda.baseQuantityUsed else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "CERRAR TANDA",
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    fontSize = 22.sp
                )
                Text(
                    text = "${tanda.productName.uppercase()} — TANDA ${tanda.tandaNumber}",
                    fontWeight = FontWeight.Bold,
                    color = Slate600,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Insumo base y producción esperada de referencia
                Surface(
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Insumo base:",
                                fontSize = 13.sp,
                                color = Slate600,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Esperada:",
                                fontSize = 13.sp,
                                color = Slate600,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${tanda.expectedYield.toInt()} ${tanda.productionUnit}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                }

                // 1. CANTIDAD REAL PRODUCIDA
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "CANTIDAD REAL PRODUCIDA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                    OutlinedTextField(
                        value = cantidadRealProducidaText,
                        onValueChange = { cantidadRealProducidaText = it },
                        placeholder = { Text("Cantidad obtenida (${tanda.productionUnit})", fontSize = 16.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald600,
                            focusedLabelColor = Emerald600
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("input_cantidad_real_producida")
                    )
                }

                // 2. PRESENTACIÓN ESPECIAL (SI ESTÁ CONFIGURADA EN LA RECETA/FICHA DE COSTO)
                if (presentaciones.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "PRESENTACIÓN ESPECIAL (OPCIONAL)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Opción Estándar
                            FilterChip(
                                selected = selectedPresentacion == null,
                                onClick = {
                                    selectedPresentacion = null
                                    cantidadPresentacionText = ""
                                },
                                label = {
                                    Text(
                                        "Estándar (${tanda.productionUnit})",
                                        fontWeight = if (selectedPresentacion == null) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )

                            // Opciones de presentaciones configuradas
                            presentaciones.forEach { pres ->
                                FilterChip(
                                    selected = selectedPresentacion == pres,
                                    onClick = {
                                        selectedPresentacion = pres
                                        if (pres.baseEquivalence > 0.0 && finalQty > 0.0) {
                                            val presQty = finalQty / pres.baseEquivalence
                                            cantidadPresentacionText = if (presQty % 1.0 == 0.0) presQty.toInt().toString() else "%.1f".format(presQty)
                                        }
                                    },
                                    label = {
                                        Text(
                                            "${pres.name} (${pres.baseEquivalence}x)",
                                            fontWeight = if (selectedPresentacion == pres) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }

                        // Si seleccionó una presentación especial
                        selectedPresentacion?.let { pres ->
                            Surface(
                                color = Emerald50,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Emerald600.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Presentación seleccionada: ${pres.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Emerald700
                                    )
                                    OutlinedTextField(
                                        value = cantidadPresentacionText,
                                        onValueChange = {
                                            cantidadPresentacionText = it
                                            val pQty = it.toDoubleOrNull() ?: 0.0
                                            if (pQty > 0.0 && pres.baseEquivalence > 0.0) {
                                                val eqBase = pQty * pres.baseEquivalence
                                                cantidadRealProducidaText = if (eqBase % 1.0 == 0.0) eqBase.toInt().toString() else "%.1f".format(eqBase)
                                            }
                                        },
                                        label = { Text("Cantidad en ${pres.name}") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_presentacion_especial_qty")
                                    )
                                }
                            }
                        }
                    }
                }

                // Cálculo automático del rendimiento
                if (finalQty > 0.0) {
                    Surface(
                        color = Emerald50,
                        border = BorderStroke(1.dp, Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Rendimiento calculado:",
                                fontSize = 13.sp,
                                color = Emerald700,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${"%.2f".format(rendimientoCalculado)} ${tanda.productionUnit}/${tanda.baseQuantityUnit}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald700
                            )
                            Text(
                                text = "(${if (finalQty % 1.0 == 0.0) finalQty.toInt().toString() else "%.1f".format(finalQty)} ${tanda.productionUnit} ÷ ${tanda.baseQuantityUsed} ${tanda.baseQuantityUnit})",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (finalQty <= 0.0) return@Button

                    val catalogPrice = product?.price ?: tanda.salePrice
                    val realRevenue = finalQty * catalogPrice
                    val realProfit = realRevenue - tanda.totalBatchCost
                    val realProfitMargin = if (realRevenue > 0.0) (realProfit / realRevenue) * 100.0 else 0.0
                    val realUnitCost = if (finalQty > 0.0) tanda.totalBatchCost / finalQty else 0.0

                    val obsText = buildString {
                        if (selectedPresentacion != null) {
                            append("Presentación: ${selectedPresentacion?.name}")
                            if (cantidadPresentacionText.isNotBlank()) {
                                append(" ($cantidadPresentacionText u)")
                            }
                            append(" | ")
                        }
                        append("Rendimiento: ${"%.2f".format(rendimientoCalculado)} ${tanda.productionUnit}/${tanda.baseQuantityUnit}")
                    }

                    val updatedTanda = tanda.copy(
                        actualYield = finalQty,
                        yieldPercentage = rendimientoCalculado,
                        status = "CERRADA",
                        observation = obsText,
                        expectedRevenue = realRevenue,
                        estimatedProfit = realProfit,
                        profitMargin = realProfitMargin,
                        realUnitCost = realUnitCost,
                        inventoryDeducted = true
                    )

                    viewModel.cerrarTanda(updatedTanda)
                    onDismiss()
                },
                enabled = finalQty > 0.0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald600,
                    disabledContainerColor = Slate300
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_cerrar_tanda")
            ) {
                Text(
                    text = "ACEPTAR",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancelar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

// =================================================================
// FORMULARIO DE NUEVA TANDA (MÁXIMA ACCESIBILIDAD ADULTO MAYOR)
// =================================================================
@Composable
fun NuevaTandaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    // 2. SELECCIÓN DEL PRODUCTO
    // Los productos disponibles deben provenir de los productos de Producción y sus recetas existentes.
    // No crear productos ni recetas nuevas.
    val availableProducts = remember(uiState.productosElaborados, uiState.products) {
        uiState.productosElaborados.filter { pe ->
            pe.isActive && uiState.products.any { it.id == pe.productId }
        }.mapNotNull { pe ->
            uiState.products.find { it.id == pe.productId }?.let { p -> Pair(pe, p) }
        }.sortedBy { it.second.name.lowercase() }
    }

    var selectedPair by remember { mutableStateOf<Pair<ProductoElaborado, Product>?>(null) }
    var productDropdownExpanded by remember { mutableStateOf(false) }

    val pe = selectedPair?.first
    val prod = selectedPair?.second

    // Base raw material
    val baseMp = remember(pe, uiState.materiasPrimas) {
        pe?.let { pElab -> uiState.materiasPrimas.find { it.id == pElab.baseMateriaPrimaId } }
    }

    // Recipe ingredients
    val recipeIngredients = remember(pe, uiState.recetaIngredientes) {
        pe?.let { pElab ->
            uiState.recetaIngredientes.filter {
                it.productoElaboradoId == pElab.productId || it.productoElaboradoId == pElab.id
            }
        } ?: emptyList()
    }

    // Determine default unit: LIBRAS (lb) for solid products, or liquid unit for liquids
    val isLiquid = remember(baseMp) {
        val u = baseMp?.unit?.lowercase()?.trim() ?: ""
        u in setOf("l", "lt", "lts", "litro", "litros", "ml", "mililitros")
    }

    var selectedUnit by remember(selectedPair) {
        val initialUnit = if (isLiquid) {
            val u = baseMp?.unit?.lowercase()?.trim() ?: "l"
            if (u.startsWith("ml")) "ml" else "l"
        } else {
            // UNIDAD DE MEDIDA: La unidad predeterminada para introducir la cantidad del insumo base debe ser: LIBRAS (lb)
            "lb"
        }
        mutableStateOf(initialUnit)
    }

    val compatibleUnits = remember(isLiquid, baseMp) {
        if (isLiquid) {
            listOf("l", "ml")
        } else {
            val u = baseMp?.unit?.lowercase()?.trim() ?: "g"
            if (u in setOf("u", "un", "unidad", "unidades", "docena")) {
                listOf("u", "docena")
            } else {
                listOf("lb", "kg", "g", "oz")
            }
        }
    }

    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var baseQuantityInput by remember(selectedPair) { mutableStateOf("") }

    // Proporción y cálculos de ingredientes
    val baseQtyEntered = baseQuantityInput.toDoubleOrNull() ?: 0.0

    // Recipe base quantity & unit
    val recipeBaseIng = remember(recipeIngredients, pe) {
        recipeIngredients.find { it.materiaPrimaId == pe?.baseMateriaPrimaId }
    }
    val recipeBaseQty = remember(recipeBaseIng, pe) {
        if (recipeBaseIng != null && recipeBaseIng.quantity > 0.0) {
            recipeBaseIng.quantity
        } else if (pe != null && pe.baseQuantity > 0.0) {
            pe.baseQuantity
        } else {
            1.0
        }
    }
    val recipeBaseUnit = remember(recipeBaseIng, baseMp, selectedUnit) {
        recipeBaseIng?.unit ?: baseMp?.unit ?: selectedUnit
    }

    // Convert entered base quantity to recipe unit
    val baseQtyInRecipeUnit = remember(baseQtyEntered, selectedUnit, recipeBaseUnit) {
        if (baseQtyEntered > 0.0) {
            UnitConverter.convert(baseQtyEntered, selectedUnit, recipeBaseUnit) ?: baseQtyEntered
        } else 0.0
    }

    // Proporción definida en la receta
    val proportion = remember(recipeBaseQty, baseQtyInRecipeUnit) {
        if (recipeBaseQty > 0.0 && baseQtyInRecipeUnit > 0.0) {
            baseQtyInRecipeUnit / recipeBaseQty
        } else 0.0
    }

    // Otros ingredientes de la receta (excluyendo el insumo base si ya está listado)
    val otherIngredients = remember(recipeIngredients, pe) {
        recipeIngredients.filter { it.materiaPrimaId != pe?.baseMateriaPrimaId }
    }

    // Cálculo de cantidad esperada y costos
    val expectedYield = remember(pe, proportion) {
        if (pe != null && proportion > 0.0) {
            (if (pe.baseYield > 0.0) pe.baseYield else 1.0) * proportion
        } else 0.0
    }

    val totalDirectCost = remember(recipeIngredients, proportion, pe, baseMp, baseQtyEntered, selectedUnit, uiState.materiasPrimas) {
        var cost = 0.0
        val mpMap = uiState.materiasPrimas.associateBy { it.id }

        // Insumo base
        if (baseMp != null && baseQtyEntered > 0.0) {
            val convertedBase = UnitConverter.convert(baseQtyEntered, selectedUnit, baseMp.unit) ?: baseQtyEntered
            cost += convertedBase * baseMp.unitCost
        }

        // Otros ingredientes
        otherIngredients.forEach { ing ->
            val raw = mpMap[ing.materiaPrimaId]
            if (raw != null) {
                val reqQty = ing.quantity * proportion
                val converted = UnitConverter.convert(reqQty, ing.unit, raw.unit) ?: reqQty
                cost += converted * raw.unitCost
            }
        }
        cost
    }

    val indirectCost = remember(prod, expectedYield, uiState) {
        if (prod != null && expectedYield > 0.0) {
            val costSheet = CostCalculationHelper.calculateCostSheet(product = prod, uiState = uiState)
            expectedYield * costSheet.gastoIndirectoUnitario
        } else 0.0
    }

    val totalBatchCost = totalDirectCost + indirectCost
    val canAccept = selectedPair != null && baseQtyEntered > 0.0 && proportion > 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Slate50
        ) {
            Scaffold(
                containerColor = Slate50,
                topBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NUEVA TANDA",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Slate600,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 56.dp)
                        ) {
                            Button(
                                onClick = {
                                    val isJornadaOpen = uiState.activeJornada != null && uiState.activeJornada.isOpen
                                    if (!isJornadaOpen) {
                                        onDismiss()
                                        return@Button
                                    }

                                    val pair = selectedPair ?: return@Button
                                    val (productElab, product) = pair
                                    if (baseQtyEntered <= 0.0 || proportion <= 0.0) return@Button

                                    val now = System.currentTimeMillis()
                                    val activeJorId = uiState.activeJornada?.id ?: 0L
                                    val currentJornadaNormalTandas = if (activeJorId != 0L) {
                                        uiState.tandas.filter { it.jornadaId == activeJorId && it.tandaNumber != "00" }
                                    } else {
                                        uiState.tandas.filter { it.tandaNumber != "00" }
                                    }
                                    val maxNum = currentJornadaNormalTandas.mapNotNull { it.tandaNumber.toIntOrNull() }.maxOrNull() ?: 0
                                    val tandaNumberStr = "%02d".format(maxNum + 1)
                                    val batchUuid = "TANDA-$tandaNumberStr-$now"
                                    val realUnitCost = if (expectedYield > 0.0) totalBatchCost / expectedYield else 0.0
                                    val expectedRevenue = expectedYield * product.price
                                    val estimatedProfit = expectedRevenue - totalBatchCost

                                    val consumosList = mutableListOf<Triple<Long, Double, String>>()
                                    // Consumo insumo base
                                    if (baseMp != null) {
                                        val convertedBase = UnitConverter.convert(baseQtyEntered, selectedUnit, baseMp.unit) ?: baseQtyEntered
                                        consumosList.add(Triple(baseMp.id, convertedBase, "${"%.2f".format(baseQtyEntered)} $selectedUnit"))
                                    }
                                    // Consumo otros ingredientes
                                    otherIngredients.forEach { ing ->
                                        val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                        val reqQty = ing.quantity * proportion
                                        val converted = if (raw != null) {
                                            UnitConverter.convert(reqQty, ing.unit, raw.unit) ?: reqQty
                                        } else reqQty
                                        consumosList.add(Triple(ing.materiaPrimaId, converted, "${"%.2f".format(reqQty)} ${ing.unit}"))
                                    }

                                    val newTanda = Tanda(
                                        uuid = batchUuid,
                                        tandaNumber = tandaNumberStr,
                                        productId = product.id,
                                        productName = product.name,
                                        date = now,
                                        responsibleUser = uiState.currentUser?.username ?: "Dueño",
                                        baseMateriaPrimaId = productElab.baseMateriaPrimaId,
                                        baseMateriaPrimaName = baseMp?.name ?: "Insumo base",
                                        baseQuantityUsed = baseQtyEntered,
                                        baseQuantityUnit = selectedUnit,
                                        productionFactor = proportion,
                                        estimatedYield = expectedYield,
                                        expectedYield = expectedYield,
                                        actualYield = 0.0,
                                        yieldPercentage = 100.0,
                                        productionUnit = productElab.productionUnit,
                                        ingredientsConsumedText = recipeIngredients.joinToString(", ") { ing ->
                                            val rawName = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }?.name ?: ""
                                            "$rawName: ${"%.2f".format(ing.quantity * proportion)} ${ing.unit}"
                                        },
                                        status = "ABIERTA",
                                        jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Abierta",
                                        jornadaId = uiState.activeJornada?.id ?: 0L,
                                        observation = "",
                                        laborCostType = "NINGUNO",
                                        laborCostValue = 0.0,
                                        totalLaborCost = 0.0,
                                        ownerPayType = "NINGUNO",
                                        ownerPayValue = 0.0,
                                        totalOwnerPay = 0.0,
                                        totalDirectIngredientsCost = totalDirectCost,
                                        totalIndirectCostAllocated = indirectCost,
                                        totalBatchCost = totalBatchCost,
                                        realUnitCost = realUnitCost,
                                        expectedRevenue = expectedRevenue,
                                        estimatedProfit = estimatedProfit,
                                        profitMargin = if (expectedRevenue > 0.0) (estimatedProfit / expectedRevenue) * 100.0 else 0.0,
                                        inventoryDeducted = true,
                                        quantitySold = 0.0,
                                        salePrice = product.price,
                                        realRevenue = 0.0,
                                        deviceId = "DISPOSITIVO-LOCAL"
                                    )

                                    viewModel.registrarTanda(newTanda, consumosList)
                                    onDismiss()
                                },
                                enabled = canAccept,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElQadreNavy,
                                    disabledContainerColor = Slate300
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .testTag("aceptar_nueva_tanda_btn")
                            ) {
                                Text(
                                    text = "ACEPTAR",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // 2. SELECCIÓN DEL PRODUCTO
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PRODUCTO",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Slate700
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { productDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (selectedPair != null) ElQadreNavy else Slate300),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedPair != null) Slate50 else Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_select_product_tanda")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prod?.name?.uppercase() ?: "SELECCIONAR PRODUCTO",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPair != null) ElQadreNavy else Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = productDropdownExpanded,
                            onDismissRequest = { productDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            if (availableProducts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No hay productos de producción activos", fontSize = 15.sp) },
                                    onClick = { productDropdownExpanded = false }
                                )
                            } else {
                                availableProducts.forEach { pair ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = pair.second.name.uppercase(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy
                                            )
                                        },
                                        onClick = {
                                            selectedPair = pair
                                            productDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. CANTIDAD DEL INSUMO BASE
                if (selectedPair != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CANTIDAD DEL INSUMO BASE",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Slate700
                        )

                        baseMp?.name?.let { mpName ->
                            Text(
                                text = "Insumo: ${mpName.uppercase()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F766E)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = baseQuantityInput,
                                onValueChange = { baseQuantityInput = it },
                                placeholder = { Text("Ej. 20", fontSize = 18.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElQadreNavy,
                                    focusedLabelColor = ElQadreNavy
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(64.dp)
                                    .testTag("input_base_quantity")
                            )

                            // Selector de Unidad (default "lb" para sólidos)
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { if (compatibleUnits.size > 1) unitDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, ElQadreNavy),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("select_unit_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedUnit,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            color = ElQadreNavy
                                        )
                                        if (compatibleUnits.size > 1) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = ElQadreNavy
                                            )
                                        }
                                    }
                                }

                                if (compatibleUnits.size > 1) {
                                    DropdownMenu(
                                        expanded = unitDropdownExpanded,
                                        onDismissRequest = { unitDropdownExpanded = false }
                                    ) {
                                        compatibleUnits.forEach { u ->
                                            DropdownMenuItem(
                                                text = { Text(u, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
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
                    }

                    // 4. INGREDIENTES DE LA RECETA CALCULADOS AUTOMÁTICAMENTE
                    if (baseQtyEntered > 0.0 && otherIngredients.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "INGREDIENTES CALCULADOS (RECETA)",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Slate700
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    otherIngredients.forEach { ing ->
                                        val ingMp = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                        val ingName = ingMp?.name ?: "Insumo"
                                        val calculatedQty = ing.quantity * proportion

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ingName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${"%.2f".format(calculatedQty)} ${ing.unit}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF0284C7)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Resumen de producción esperada y costo
                    if (baseQtyEntered > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Emerald50,
                            border = BorderStroke(1.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Producción esperada", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Medium)
                                    val yieldText = if (expectedYield % 1.0 == 0.0) {
                                        "${expectedYield.toInt()} ${pe?.productionUnit ?: "u"}"
                                    } else {
                                        "${"%.1f".format(expectedYield)} ${pe?.productionUnit ?: "u"}"
                                    }
                                    Text(yieldText, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Costo estimado", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Medium)
                                    Text("$${"%.2f".format(totalBatchCost)} CUP", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Rose700)
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

// =================================================================
// IMPORTAR TANDAS DESDE TEXTO / MENSAJES SMS (CUALQUIER CHAT)
// =================================================================
@Composable
fun ImportarTandasDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(true) }
    var scanFinished by remember { mutableStateOf(false) }
    var candidateGroups by remember { mutableStateOf<List<CandidateTandaGroup>>(emptyList()) }
    var mismatchAccepted by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val elaboratedProductsMap = remember(uiState.productosElaborados, uiState.products) {
        uiState.productosElaborados.mapNotNull { pe ->
            uiState.products.find { it.id == pe.productId }?.let { p ->
                p.name.lowercase().trim() to Pair(pe, p)
            }
        }.toMap()
    }

    val performAutoScan: () -> Unit = {
        isScanning = true
        scanErrorMessage = null
        try {
            val found = SmsTandasHelper.scanInboxForTandasLast48Hours(
                context = context,
                elaboratedProductsMap = elaboratedProductsMap,
                jornadaOpenedAt = uiState.activeJornada?.openedAt ?: 0L
            )
            candidateGroups = found
            scanFinished = true
        } catch (e: Exception) {
            scanErrorMessage = "Error al leer mensajes: ${e.localizedMessage}"
            scanFinished = true
        } finally {
            isScanning = false
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            performAutoScan()
        } else {
            isScanning = false
            scanFinished = true
            scanErrorMessage = "Permiso para leer mensajes SMS no otorgado en el dispositivo."
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            kotlinx.coroutines.delay(600)
            performAutoScan()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 580.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, ElQadreNavy.copy(alpha = 0.2f)),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElQadreNavy.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Sms,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "IMPORTAR POR SMS",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Búsqueda en últimas 48 horas",
                                fontSize = 12.sp,
                                color = Slate600,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate500
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Slate200)

                // BODY STATES
                if (isScanning) {
                    // ESTADO: LEYENDO MENSAJES...
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = ElQadreNavy,
                            strokeWidth = 5.dp
                        )
                        Text(
                            text = "LEYENDO MENSAJES...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Buscando mensajes de tandas de las últimas 48 horas en su aplicación de mensajes...",
                            fontSize = 14.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate300),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("CANCELAR", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                } else if (scanFinished && candidateGroups.isEmpty()) {
                    // ESTADO: NO SE ENCONTRARON MENSAJES
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Slate100,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = Slate500,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Text(
                            text = "NO SE ENCONTRARON MENSAJES",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = scanErrorMessage ?: "No se encontraron mensajes de tandas en las últimas 48 horas dentro de la bandeja de entrada.",
                            fontSize = 14.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { performAutoScan() },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, ElQadreNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("REINTENTAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("CERRAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    // ESTADO: MENSAJES ENCONTRADOS
                    val totalCandidateTandas = candidateGroups.sumOf { it.tandas.size }
                    val hasAnyDateMismatch = candidateGroups.any { !it.isDateMatch }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Emerald50,
                            border = BorderStroke(1.dp, Emerald600.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald700,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Se encontraron ${candidateGroups.size} mensaje(s) con $totalCandidateTandas tanda(s) válida(s).",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald800
                                )
                            }
                        }

                        // Lista de grupos de mensajes candidatos
                        candidateGroups.forEach { group ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, if (!group.isDateMatch) Color(0xFFF59E0B) else Slate300),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Info del mensaje
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "De: ${group.senderPhone} • ${group.formattedDate} ${group.formattedTime}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ElQadreNavy
                                        )
                                        Badge(
                                            containerColor = if (group.isDateMatch) Emerald700 else Color(0xFFD97706)
                                        ) {
                                            Text(
                                                text = "${group.tandas.size} tanda(s)",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // REGLA CRÍTICA DE FECHA: Advertencia y confirmación si no coincide con la jornada
                                    if (!group.isDateMatch) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFFFBEB),
                                            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Warning,
                                                        contentDescription = null,
                                                        tint = Color(0xFFB45309),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "DESFASE DE FECHAS DETECTADO",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFFB45309)
                                                    )
                                                }
                                                Text(
                                                    text = "Jornada: ${group.jornadaDateStr}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = ElQadreNavy
                                                )
                                                Text(
                                                    text = "Mensaje: ${group.formattedDate}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFFB45309)
                                                )
                                                Text(
                                                    text = "Las fechas no coinciden. ¿Desea procesar este mensaje?",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Slate800
                                                )
                                            }
                                        }
                                    }

                                    // Tandas dentro del mensaje
                                    group.tandas.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Tanda ${item.tandaNumber} — ${item.product.name}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = ElQadreNavy
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Slate100
                                                    ) {
                                                        Text(
                                                            text = "${item.baseQtyUsed} ${item.pe.productionUnit} base",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Slate700,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Prod. Esperada: ${item.expectedYield.toInt()} ${item.pe.productionUnit}",
                                                        fontSize = 12.sp,
                                                        color = Slate600
                                                    )
                                                    if (item.actualYield > 0.0 && item.actualYield != item.expectedYield) {
                                                        Text(
                                                            text = "Real: ${item.actualYield.toInt()} ${item.pe.productionUnit}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Emerald700
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Checkbox si hay desfase de fechas
                        if (hasAnyDateMismatch) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { mismatchAccepted = !mismatchAccepted }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = mismatchAccepted,
                                        onCheckedChange = { mismatchAccepted = it },
                                        colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                    )
                                    Text(
                                        text = "Confirmar y aceptar importación a pesar del desfase de fechas",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    val canConfirm = !hasAnyDateMismatch || mismatchAccepted

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Slate300),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Text(
                                text = "CANCELAR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                        }

                        Button(
                            onClick = {
                                val activeJornada = uiState.activeJornada
                                if (activeJornada == null || !activeJornada.isOpen) {
                                    viewModel.setErrorMessage("No se puede importar sin una jornada abierta.")
                                    onDismiss()
                                    return@Button
                                }

                                val existingUuids = uiState.tandas.map { it.uuid }.toSet()
                                val existingKeys = uiState.tandas.map { "${it.tandaNumber}_${it.productId}_${activeJornada.id}" }.toSet()

                                var importedCount = 0

                                candidateGroups.forEach { group ->
                                    group.tandas.forEachIndexed { index, item ->
                                        val p = item.product
                                        val pe = item.pe
                                        val baseMp = uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId }
                                        val batchUuid = "TANDA-${item.tandaNumber}-${group.date}-$index"
                                        val deduplicationKey = "${item.tandaNumber}_${p.id}_${activeJornada.id}"

                                        // EVITAR DUPLICADOS
                                        if (existingUuids.contains(batchUuid) || existingKeys.contains(deduplicationKey)) {
                                            return@forEachIndexed
                                        }

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
                                            uiState = uiState
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

                                        val tanda = Tanda(
                                            uuid = batchUuid,
                                            tandaNumber = item.tandaNumber,
                                            productId = p.id,
                                            productName = p.name,
                                            date = group.date,
                                            responsibleUser = "SMS (${group.senderPhone})",
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
                                            status = if (item.actualYield > 0.0 && item.actualYield != item.expectedYield) "CERRADA" else "ABIERTA",
                                            jornada = "Jornada #${activeJornada.id}",
                                            jornadaId = activeJornada.id,
                                            observation = "Importada desde SMS (${group.senderPhone})",
                                            laborCostType = "NINGUNO",
                                            laborCostValue = 0.0,
                                            totalLaborCost = 0.0,
                                            ownerPayType = "NINGUNO",
                                            ownerPayValue = 0.0,
                                            totalOwnerPay = 0.0,
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
                                        importedCount++
                                    }
                                }

                                if (importedCount > 0) {
                                    viewModel.setSuccessMessage("Se importaron $importedCount tanda(s) correctamente.")
                                } else {
                                    viewModel.setSuccessMessage("Todas las tandas encontradas ya estaban registradas.")
                                }
                                onDismiss()
                            },
                            enabled = canConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                disabledContainerColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(54.dp)
                                .testTag("confirm_importar_tandas")
                        ) {
                            Text(
                                text = "IMPORTAR ($totalCandidateTandas)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
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
                uiState = uiState
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

fun Double.ifZeroUse(fallback: Double): Double = if (this > 0.0) this else fallback
