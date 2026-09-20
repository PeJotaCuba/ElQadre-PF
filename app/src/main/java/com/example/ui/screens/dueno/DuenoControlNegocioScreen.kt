package com.example.ui.screens.dueno

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Jornada
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostCalculationHelper
import com.example.util.InformeFinancieroPdfExporter
import com.example.util.QJornadaExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ControlNegocioTab(val title: String, val icon: ImageVector) {
    GENERALES("GENERALES", Icons.Outlined.Dashboard),
    PRODUCCION("PRODUCCIÓN", Icons.Outlined.Restaurant),
    AGREGADOS("AGREGADOS", Icons.Outlined.Extension),
    MERCADERIAS("MERCADERÍAS", Icons.Outlined.Storefront),
    LIQUIDEZ("LIQUIDEZ", Icons.Outlined.AccountBalanceWallet)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuenoControlNegocioScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Active tab
    var selectedTab by rememberSaveable { mutableStateOf(ControlNegocioTab.GENERALES) }

    // Target jornada: Default to active jornada, or the most recent one if no active jornada
    val allJornadasList = uiState.allJornadas.sortedByDescending { it.id }
    val defaultJornada = uiState.activeJornada ?: allJornadasList.firstOrNull()

    var selectedJornadaId by remember(uiState.activeJornada?.id, allJornadasList.firstOrNull()?.id) {
        mutableStateOf(defaultJornada?.id)
    }

    val currentJornada = remember(selectedJornadaId, uiState.activeJornada, uiState.allJornadas) {
        if (selectedJornadaId != null) {
            uiState.allJornadas.find { it.id == selectedJornadaId } ?: uiState.activeJornada
        } else {
            uiState.activeJornada ?: allJornadasList.firstOrNull()
        }
    }

    // Consolidated Economics calculation for the selected/current jornada
    val calc = remember(
        currentJornada,
        uiState.tandas,
        uiState.movimientosMercaderia,
        uiState.products,
        uiState.mercaderias,
        uiState.gastosGenerales,
        uiState.inversiones,
        uiState.materiasPrimas
    ) {
        if (currentJornada != null) {
            QJornadaExporter.calculateJornadaEconomics(
                jornada = currentJornada,
                allTandas = uiState.tandas,
                allMovimientos = uiState.movimientosMercaderia,
                allProducts = uiState.products,
                allMercaderias = uiState.mercaderias,
                allGastos = uiState.gastosGenerales,
                allInversiones = uiState.inversiones,
                currentUser = uiState.currentUser,
                allMateriasPrimas = uiState.materiasPrimas,
                finalCashInput = currentJornada.finalCash,
                closedByInput = currentJornada.closedBy,
                notesInput = currentJornada.notes
            )
        } else null
    }

    // Extracciones state
    var extraccionesText by remember(currentJornada?.id, currentJornada?.extracciones) {
        mutableStateOf(if ((currentJornada?.extracciones ?: 0.0) > 0.0) currentJornada?.extracciones.toString() else "")
    }
    val extraccionesVal = extraccionesText.toDoubleOrNull() ?: (currentJornada?.extracciones ?: 0.0)

    // Base Economic Values from Cuadre de Caja and calculations
    val ventasProduccion = if ((currentJornada?.realSalesProduccion ?: 0.0) > 0.0) {
        currentJornada?.realSalesProduccion ?: 0.0
    } else {
        calc?.produccionIngresosReales ?: 0.0
    }

    val costosProduccion = calc?.produccionCostosReales ?: (currentJornada?.realCostProduccion ?: 0.0)
    val resultadoProduccion = ventasProduccion - costosProduccion

    val ventasMercaderia = if ((currentJornada?.realSalesMercaderias ?: 0.0) > 0.0) {
        currentJornada?.realSalesMercaderias ?: 0.0
    } else {
        calc?.mercaderiasIngresosReales ?: 0.0
    }

    val costosMercaderia = calc?.mercaderiasCostosReales ?: (currentJornada?.realCostMercaderias ?: 0.0)
    val resultadoMercaderia = ventasMercaderia - costosMercaderia

    val ventasTotales = ventasProduccion + ventasMercaderia
    val costosTotales = costosProduccion + costosMercaderia
    val gastosGenerales = calc?.totalGastos ?: (currentJornada?.totalGastos ?: 0.0)
    val inversionesGenerales = calc?.totalInversiones ?: (currentJornada?.totalInversiones ?: 0.0)

    val resultadoOperativo = ventasTotales - costosTotales
    val utilidadCalculada = resultadoOperativo - gastosGenerales - inversionesGenerales
    val liquidezCalculada = utilidadCalculada - extraccionesVal

    // Filter tandas for Producción section
    val jornadaTandas = remember(uiState.tandas, currentJornada) {
        if (currentJornada != null) {
            uiState.tandas.filter { tanda ->
                tanda.jornadaId == currentJornada.id || (currentJornada.openedAt > 0 && tanda.date >= currentJornada.openedAt && (currentJornada.closedAt == null || tanda.date <= currentJornada.closedAt))
            }
        } else emptyList()
    }

    // Filter transferencias for Liquidez section
    val transferenciasForJornada = remember(uiState.allTransferencias, currentJornada) {
        if (currentJornada != null && currentJornada.openedAt > 0) {
            uiState.allTransferencias.filter { it.receivedAt >= currentJornada.openedAt && (currentJornada.closedAt == null || it.receivedAt <= currentJornada.closedAt) }
        } else emptyList()
    }
    val transferenciasTotalMonto = remember(transferenciasForJornada) {
        transferenciasForJornada.sumOf { it.amount }
    }

    var showArchivarDialog by remember { mutableStateOf(false) }
    var isJornadaDropdownOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElQadreBackground)
    ) {
        // TOP HEADER
        Surface(
            color = ElQadreNavy,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_back_control_negocio")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Regresar",
                                tint = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreGold.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                Icons.Outlined.Analytics,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "CONTROL DEL NEGOCIO",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Producción + Mercaderías + Cuadre de Caja",
                                fontSize = 11.sp,
                                color = Slate300
                            )
                        }
                    }

                    if (currentJornada != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentJornada.isOpen) Color(0xFF047857) else Slate600
                        ) {
                            Text(
                                text = if (currentJornada.isOpen) "ACTIVA" else "ARCHIVADA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 4 TOP SECTIONS SELECTOR
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ControlNegocioTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElQadreGold else Color.Transparent)
                                    .clickable { selectedTab = tab }
                                    .testTag("tab_control_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    color = if (isSelected) ElQadreNavy else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // SCROLLABLE BODY
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Jornada selector card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JORNADA EN CONSULTA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate500
                        )
                        if (currentJornada != null) {
                            Text(
                                text = "ID #${currentJornada.id}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }

                    if (allJornadasList.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { isJornadaDropdownOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_select_jornada"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Slate300),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val jorText = if (currentJornada != null) {
                                        "Jornada #${currentJornada.id} - ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(currentJornada.openedAt))} (${if (currentJornada.isOpen) "Abierta" else "Archivada"})"
                                    } else "Seleccionar Jornada"
                                    Text(jorText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = isJornadaDropdownOpen,
                                onDismissRequest = { isJornadaDropdownOpen = false }
                            ) {
                                allJornadasList.forEach { j ->
                                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(j.openedAt))
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Jornada #${j.id} - $dateStr ${if (j.isOpen) " [ACTIVA]" else " [ARCHIVADA]"}",
                                                fontWeight = if (j.id == selectedJornadaId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedJornadaId = j.id
                                            isJornadaDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (currentJornada == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate100),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No hay jornadas registradas para mostrar.", color = Slate500)
                    }
                }
            } else {
                // CONDITIONAL CONTENT BASED ON SELECTED TAB
                when (selectedTab) {
                    ControlNegocioTab.GENERALES -> {
                        ControlGeneralesContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            currentJornada = currentJornada,
                            calc = calc,
                            ventasProduccion = ventasProduccion,
                            costosProduccion = costosProduccion,
                            resultadoProduccion = resultadoProduccion,
                            ventasMercaderia = ventasMercaderia,
                            costosMercaderia = costosMercaderia,
                            resultadoMercaderia = resultadoMercaderia,
                            ventasTotales = ventasTotales,
                            costosTotales = costosTotales,
                            gastosGenerales = gastosGenerales,
                            inversionesGenerales = inversionesGenerales,
                            resultadoOperativo = resultadoOperativo,
                            utilidadCalculada = utilidadCalculada,
                            extraccionesVal = extraccionesVal,
                            liquidezCalculada = liquidezCalculada,
                            onGenerarPdf = {
                                val configNegocio = uiState.businessConfig
                                val nombreNegocio = configNegocio?.nombreNegocio ?: uiState.businessName
                                val nombreDueno = uiState.currentUser?.fullName ?: "Dueño"
                                val dvcCode = uiState.deviceId
                                val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(currentJornada.openedAt))

                                val pdfData = InformeFinancieroPdfExporter.InformeFinancieroData(
                                    negocio = nombreNegocio,
                                    dueno = nombreDueno,
                                    dvc = dvcCode,
                                    jornada = "#${currentJornada.id}",
                                    fecha = dateFormatted,
                                    ventasProduccion = ventasProduccion,
                                    costosProduccion = costosProduccion,
                                    resultadoProduccion = resultadoProduccion,
                                    ventasMercaderia = ventasMercaderia,
                                    costosMercaderia = costosMercaderia,
                                    resultadoMercaderia = resultadoMercaderia,
                                    ventasTotales = ventasTotales,
                                    costosTotales = costosTotales,
                                    utilidad = utilidadCalculada,
                                    gastos = gastosGenerales,
                                    inversiones = inversionesGenerales,
                                    extracciones = extraccionesVal,
                                    liquidezFinal = liquidezCalculada
                                )
                                InformeFinancieroPdfExporter.exportAndShareInformeFinancieroPdf(context, pdfData)
                            },
                            onArchivarJornada = {
                                if (!currentJornada.isOpen) {
                                    Toast.makeText(context, "Esta jornada ya fue archivada como registro histórico inmutable.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showArchivarDialog = true
                                }
                            }
                        )
                    }

                    ControlNegocioTab.PRODUCCION -> {
                        ControlProduccionContent(
                            uiState = uiState,
                            currentJornada = currentJornada,
                            jornadaTandas = jornadaTandas,
                            totalVentasProduccion = ventasProduccion,
                            totalCostosProduccion = costosProduccion,
                            totalUtilidadProduccion = resultadoProduccion
                        )
                    }

                    ControlNegocioTab.AGREGADOS -> {
                        ControlAgregadosContent(
                            uiState = uiState
                        )
                    }

                    ControlNegocioTab.MERCADERIAS -> {
                        ControlMercaderiasContent(
                            uiState = uiState,
                            currentJornada = currentJornada,
                            totalVentasMercaderia = ventasMercaderia,
                            totalCostosMercaderia = costosMercaderia,
                            totalUtilidadMercaderia = resultadoMercaderia
                        )
                    }

                    ControlNegocioTab.LIQUIDEZ -> {
                        ControlLiquidezContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            currentJornada = currentJornada,
                            ventasTotales = ventasTotales,
                            transferenciasTotal = transferenciasTotalMonto,
                            transferenciasCount = transferenciasForJornada.size,
                            extraccionesText = extraccionesText,
                            onExtraccionesTextChange = { newVal ->
                                extraccionesText = newVal
                                val newExt = newVal.toDoubleOrNull() ?: 0.0
                                val newLiq = utilidadCalculada - newExt
                                viewModel.updateJornada(
                                    currentJornada.copy(
                                        extracciones = newExt,
                                        liquidezFinal = newLiq
                                    )
                                )
                            },
                            extraccionesVal = extraccionesVal,
                            utilidadCalculada = utilidadCalculada,
                            liquidezCalculada = liquidezCalculada
                        )
                    }
                }
            }
        }
    }

    // Modal de confirmación para archivar jornada
    if (showArchivarDialog && currentJornada != null && calc != null) {
        AlertDialog(
            onDismissRequest = { showArchivarDialog = false },
            title = {
                Text(
                    text = "Archivar Jornada #${currentJornada.id}",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Al archivar la jornada se consolidará el registro económico como histórico inmutable. No podrá ser modificada posteriormente.",
                        fontSize = 13.sp,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Utilidad final: $${"%.2f".format(utilidadCalculada)} CUP",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Text(
                        text = "• Extracciones: $${"%.2f".format(extraccionesVal)} CUP",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Text(
                        text = "• Liquidez final: $${"%.2f".format(liquidezCalculada)} CUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archivarJornada(
                            jornada = currentJornada,
                            calcResult = calc,
                            extracciones = extraccionesVal,
                            liquidezFinal = liquidezCalculada
                        ) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            showArchivarDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))
                ) {
                    Text("Confirmar Archivo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchivarDialog = false }) {
                    Text("Cancelar", color = Slate600)
                }
            }
        )
    }
}

private data class IndividualProductSummaryItem(
    val name: String,
    val category: String,
    val qtySold: Double,
    val unit: String,
    val ingresos: Double,
    val costos: Double,
    val utilidad: Double
)

// ==========================================
// 1. GENERALES SUB-CONTENT
// ==========================================
@Composable
private fun ControlGeneralesContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    currentJornada: Jornada,
    calc: QJornadaExporter.JornadaCalculationResult? = null,
    ventasProduccion: Double,
    costosProduccion: Double,
    resultadoProduccion: Double,
    ventasMercaderia: Double,
    costosMercaderia: Double,
    resultadoMercaderia: Double,
    ventasTotales: Double,
    costosTotales: Double,
    gastosGenerales: Double,
    inversionesGenerales: Double,
    resultadoOperativo: Double,
    utilidadCalculada: Double,
    extraccionesVal: Double,
    liquidezCalculada: Double,
    onGenerarPdf: () -> Unit,
    onArchivarJornada: () -> Unit
) {
    val individualProducts = remember(calc, uiState.materiasPrimas) {
        val list = mutableListOf<IndividualProductSummaryItem>()

        calc?.produccionTandas?.forEach { t ->
            list.add(
                IndividualProductSummaryItem(
                    name = t.productName,
                    category = "PRODUCCIÓN",
                    qtySold = t.soldQty,
                    unit = "ud",
                    ingresos = t.realRevenue,
                    costos = t.soldCost,
                    utilidad = t.netResult
                )
            )
        }

        uiState.materiasPrimas.filter { it.isAgregado && it.isActive }.forEach { mp ->
            val racionesVendidas = mp.racionesEnVenta.coerceAtLeast(0.0)
            val ingresos = racionesVendidas * mp.precioEfectivoVenta
            val costos = racionesVendidas * mp.costoPorRacion
            list.add(
                IndividualProductSummaryItem(
                    name = mp.name,
                    category = "AGREGADO",
                    qtySold = racionesVendidas,
                    unit = "raciones",
                    ingresos = ingresos,
                    costos = costos,
                    utilidad = ingresos - costos
                )
            )
        }

        calc?.mercaderiasMovimientos?.forEach { m ->
            list.add(
                IndividualProductSummaryItem(
                    name = m.productName,
                    category = "MERCADERÍA",
                    qtySold = m.soldQty,
                    unit = "ud",
                    ingresos = m.realRevenue,
                    costos = m.soldCost,
                    utilidad = m.netResult
                )
            )
        }

        list
    }

    val margen = if (ventasTotales > 0) (utilidadCalculada / ventasTotales) * 100.0 else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Banner informativo
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFEFF6FF),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Desglose por producto individual (Producción, Agregados y Mercaderías). Los ingresos proceden del Cuadre de Caja y los costos de las fichas reales.",
                    fontSize = 12.sp,
                    color = Color(0xFF1E40AF),
                    lineHeight = 16.sp
                )
            }
        }

        // ==========================================
        // 4 INDICADORES PRINCIPALES (HERO CARDS)
        // ==========================================
        Text(
            text = "INDICADORES PRINCIPALES DEL NEGOCIO",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // FILA 1: VENTAS & COSTOS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // INDICADOR 1: VENTAS
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFF15803D).copy(alpha = 0.3f)),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_card_ventas")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VENTAS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D)
                        )
                        Icon(
                            imageVector = Icons.Outlined.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "$${"%.2f".format(ventasTotales)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF15803D)
                    )

                    HorizontalDivider(color = Slate100)

                    Text(
                        text = "Prod: $${"%.2f".format(ventasProduccion)}\nMerc: $${"%.2f".format(ventasMercaderia)}",
                        fontSize = 10.sp,
                        color = Slate600,
                        lineHeight = 14.sp
                    )
                }
            }

            // INDICADOR 2: COSTOS
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_card_costos")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COSTOS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate700
                        )
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "$${"%.2f".format(costosTotales)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate800
                    )

                    HorizontalDivider(color = Slate100)

                    Text(
                        text = "Prod: $${"%.2f".format(costosProduccion)}\nMerc: $${"%.2f".format(costosMercaderia)}",
                        fontSize = 10.sp,
                        color = Slate600,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // INDICADOR 3: UTILIDAD (TARJETA DESTACADA)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_generales_utilidad"),
            colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            color = ElQadreGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.TrendingUp,
                                    contentDescription = null,
                                    tint = ElQadreGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "UTILIDAD DE LA JORNADA",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreGold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (margen >= 0) Color(0xFF047857).copy(alpha = 0.4f) else Color(0xFFDC2626).copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "Margen ${"%.1f".format(margen)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (margen >= 0) Color(0xFF34D399) else Color(0xFFFCA5A5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "$${"%.2f".format(utilidadCalculada)} CUP",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "Ventas ($${"%.2f".format(ventasTotales)}) − Costos ($${"%.2f".format(costosTotales)}) − Gastos ($${"%.2f".format(gastosGenerales)}) − Inversiones ($${"%.2f".format(inversionesGenerales)})",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 15.sp
                )
            }
        }

        // INDICADOR 4: LIQUIDEZ (TARJETA DESTACADA)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, ElQadreGold),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kpi_card_liquidez")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            color = ElQadreGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "LIQUIDEZ DISPONIBLE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = "DISPONIBLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "$${"%.2f".format(liquidezCalculada)} CUP",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )

                Text(
                    text = "Utilidad ($${"%.2f".format(utilidadCalculada)}) − Extracciones ($${"%.2f".format(extraccionesVal)})",
                    fontSize = 11.sp,
                    color = Slate600
                )
            }
        }

        // ==========================================
        // 1. DESGLOSE INDIVIDUAL POR PRODUCTO (Requirement 5)
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_generales_productos_individuales"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DESGLOSE INDIVIDUAL POR PRODUCTO Y AGREGADO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )

                HorizontalDivider(color = Slate100)

                if (individualProducts.isEmpty()) {
                    Text(
                        text = "No se registraron productos o agregados en esta jornada.",
                        fontSize = 12.sp,
                        color = Slate500,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    individualProducts.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = Slate100)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    val (badgeBg, badgeFg) = when (item.category) {
                                        "AGREGADO" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                                        "MERCADERÍA" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                        else -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                                        Text(
                                            text = item.category,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                }

                                Text(
                                    text = "$${"%.2f".format(item.ingresos)} CUP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Vendidos: ${"%.1f".format(item.qtySold)} ${item.unit} | Costos: $${"%.2f".format(item.costos)}",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = "Utilidad: $${"%.2f".format(item.utilidad)} CUP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.utilidad >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. TOTALES Y COSTOS REALES
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_generales_totales"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "TOTALES CONSOLIDADOS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )

                HorizontalDivider(color = Slate100)

                DuenoMetricRow(label = "Ingresos Totales (Cuadre de Caja)", value = ventasTotales, isBold = true)
                DuenoMetricRow(label = "Costos Reales Totales", value = costosTotales, isBold = false)
                DuenoMetricRow(label = "Gastos Corrientes", value = gastosGenerales, isBold = false)
                DuenoMetricRow(label = "Inversiones (Depreciación)", value = inversionesGenerales, isBold = false)

                HorizontalDivider(color = Slate200)

                DuenoMetricRow(
                    label = "Resultado Operativo",
                    value = resultadoOperativo,
                    isBold = true,
                    colorOverride = if (resultadoOperativo >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                )
            }
        }

        // ACCIONES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onGenerarPdf,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_generar_pdf_control"),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold, contentColor = ElQadreNavy),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = ElQadreNavy)
                Spacer(modifier = Modifier.width(6.dp))
                Text("GENERAR PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
            }

            Button(
                onClick = onArchivarJornada,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_archivar_jornada"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentJornada.isOpen) Color(0xFFB45309) else Slate500
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Archive, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentJornada.isOpen) "ARCHIVAR" else "ARCHIVADA",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================
// 2. PRODUCCIÓN SUB-CONTENT
// ==========================================
@Composable
private fun ControlProduccionContent(
    uiState: MainUiState,
    currentJornada: Jornada,
    jornadaTandas: List<com.example.data.local.model.Tanda>,
    totalVentasProduccion: Double,
    totalCostosProduccion: Double,
    totalUtilidadProduccion: Double
) {
    // Group tandas by productId
    val groupedByProduct = remember(jornadaTandas, uiState.products, uiState.productosElaborados) {
        val grouped = jornadaTandas.groupBy { it.productId }
        grouped.map { (prodId, tandas) ->
            val product = uiState.products.find { it.id == prodId }
            val prodName = product?.name ?: tandas.firstOrNull()?.productName ?: "Producto #$prodId"
            val price = product?.price ?: tandas.firstOrNull()?.salePrice ?: 0.0
            val unit = product?.unitOfMeasure ?: tandas.firstOrNull()?.productionUnit ?: "U"

            val totalProduced = tandas.sumOf { if (it.actualYield > 0) it.actualYield else (if (it.estimatedYield > 0) it.estimatedYield else 1.0) }
            val tandasCount = tandas.size

            // Ficha de costo real existente
            val costSheet = if (product != null) {
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

            val realUnitCost = if (costSheet != null && costSheet.costoRealUnitario > 0.0) {
                costSheet.costoRealUnitario
            } else if (totalProduced > 0) {
                tandas.sumOf { it.totalBatchCost } / totalProduced
            } else {
                product?.cost ?: 0.0
            }

            // Mermas from tandas (difference between expected/estimated and actual or mermas registered)
            val mermasTotal = tandas.sumOf { (it.estimatedYield - it.actualYield).coerceAtLeast(0.0) }
            val vendible = (totalProduced - mermasTotal).coerceAtLeast(0.0)
            val restanteTotal = tandas.sumOf { it.cantidadRestante }
            val ingresos = vendible * price
            val costoRealTotal = vendible * realUnitCost
            val utilidad = ingresos - costoRealTotal

            ProduccionProductSummary(
                productId = prodId,
                productName = prodName,
                unit = unit,
                tandasCount = tandasCount,
                totalProduced = totalProduced,
                mermas = mermasTotal,
                vendible = vendible,
                restanteSiguienteJornada = restanteTotal,
                price = price,
                unitCost = realUnitCost,
                ingresos = ingresos,
                costoRealTotal = costoRealTotal,
                utilidad = utilidad
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Resumen General de Producción
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_resumen_produccion"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ElQadreNavy.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "RESULTADOS DE PRODUCCIÓN",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                val sumProducido = groupedByProduct.sumOf { it.totalProduced }
                val sumVendible = groupedByProduct.sumOf { it.vendible }
                val sumMermas = groupedByProduct.sumOf { it.mermas }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Producido:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(sumProducido)} uds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Vendible / Vendido:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(sumVendible)} uds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Mermas:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(sumMermas)} uds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sumMermas > 0) Color(0xFFDC2626) else Slate700)
                }

                HorizontalDivider(color = Slate100)

                DuenoMetricRow(label = "Ingresos de Producción", value = totalVentasProduccion, isBold = false)
                DuenoMetricRow(label = "Costos Reales (según Ficha)", value = totalCostosProduccion, isBold = false)
                DuenoMetricRow(
                    label = "Utilidad de Producción",
                    value = totalUtilidadProduccion,
                    isBold = true,
                    colorOverride = if (totalUtilidadProduccion >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                )
            }
        }

        // Listado por Producto
        Text(
            text = "PRODUCTOS PRODUCIDOS EN LA JORNADA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (groupedByProduct.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No se registraron tandas de producción en esta jornada.", color = Slate500, fontSize = 13.sp)
                }
            }
        } else {
            groupedByProduct.forEach { item ->
                ProduccionProductCard(item = item)
            }
        }
    }
}

private data class ProduccionProductSummary(
    val productId: Long,
    val productName: String,
    val unit: String,
    val tandasCount: Int,
    val totalProduced: Double,
    val mermas: Double,
    val vendible: Double,
    val restanteSiguienteJornada: Double,
    val price: Double,
    val unitCost: Double,
    val ingresos: Double,
    val costoRealTotal: Double,
    val utilidad: Double
)

@Composable
private fun ProduccionProductCard(item: ProduccionProductSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_prod_${item.productId}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate100
                ) {
                    Text(
                        text = "${item.tandasCount} tanda(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Producido: ${"%.1f".format(item.totalProduced)} ${item.unit}", fontSize = 12.sp, color = Slate700)
                Text("Vendible/Vendido: ${"%.1f".format(item.vendible)} ${item.unit}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF047857))
            }

            if (item.restanteSiguienteJornada > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Restante para Siguiente Jornada:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                    Text("${"%.1f".format(item.restanteSiguienteJornada)} ${item.unit}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E40AF))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mermas: ${"%.1f".format(item.mermas)} ${item.unit}", fontSize = 12.sp, color = if (item.mermas > 0) Color(0xFFDC2626) else Slate600)
                Text("Precio Venta: $${"%.2f".format(item.price)}", fontSize = 12.sp, color = Slate700)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Costo Unitario Real: $${"%.2f".format(item.unitCost)}", fontSize = 12.sp, color = Slate600)
                Text("Costo Total: $${"%.2f".format(item.costoRealTotal)}", fontSize = 12.sp, color = Slate700)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ingresos: $${"%.2f".format(item.ingresos)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }
                    Text(
                        text = "Utilidad: $${"%.2f".format(item.utilidad)} CUP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (item.utilidad >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2.5. AGREGADOS SUB-CONTENT
// ==========================================
@Composable
private fun ControlAgregadosContent(
    uiState: MainUiState
) {
    val agregadosList = remember(uiState.materiasPrimas) {
        uiState.materiasPrimas.filter { it.isAgregado && it.isActive }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_resumen_agregados"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF16A34A).copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Extension, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "CONTROL Y RESULTADOS DE AGREGADOS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                val totalAgregadosCount = agregadosList.size
                val totalRacionesEnVenta = agregadosList.sumOf { it.racionesEnVenta }
                val totalValorEstimadoVenta = agregadosList.sumOf { it.racionesEnVenta * it.precioEfectivoVenta }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Agregados Configurados:", fontSize = 13.sp, color = Slate600)
                    Text("$totalAgregadosCount insumo(s)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Raciones Enviadas a Venta:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(totalRacionesEnVenta)} raciones", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Valor Estimado de Raciones en Venta:", fontSize = 13.sp, color = Slate600)
                    Text("$${"%.2f".format(totalValorEstimadoVenta)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                }
            }
        }

        Text(
            text = "LISTADO DE AGREGADOS Y RACIONES",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (agregadosList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No hay insumos configurados como Agregado.", color = Slate500, fontSize = 13.sp)
                }
            }
        } else {
            agregadosList.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("card_agregado_${item.id}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF0FDF4)) {
                                Text("${item.rationQuantity} ${item.rationUnit} / ración", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        HorizontalDivider(color = Slate100)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("En Almacén:", fontSize = 12.sp, color = Slate600)
                            Text("${"%.1f".format(item.stock)} ${item.unit} (${"%.1f".format(item.racionesDisponibles)} raciones)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Enviado a Venta:", fontSize = 12.sp, color = Slate600)
                            Text("${"%.1f".format(item.stockEnVenta)} ${item.unit} (${"%.1f".format(item.racionesEnVenta)} raciones)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costo x Ración:", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(item.costoPorRacion)} CUP", fontSize = 12.sp, color = Slate800)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Precio Sugerido (+30%):", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(item.precioSugeridoCalculado)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Precio Venta Efectivo:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            Text("$${"%.2f".format(item.precioEfectivoVenta)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. MERCADERÍAS SUB-CONTENT
// ==========================================
@Composable
private fun ControlMercaderiasContent(
    uiState: MainUiState,
    currentJornada: Jornada,
    totalVentasMercaderia: Double,
    totalCostosMercaderia: Double,
    totalUtilidadMercaderia: Double
) {
    // Calculate mercaderías items based on Cuadre de Caja rules
    val mercaderiaItems = remember(uiState.mercaderias, uiState.products, currentJornada) {
        uiState.mercaderias.filter { it.isActive }.map { merc ->
            val product = uiState.products.find { it.id == merc.productId }
            val prodName = product?.name ?: "Mercadería #${merc.id}"
            val price = product?.price ?: 0.0
            val unit = merc.unitOfMeasure.ifBlank { product?.unitOfMeasure ?: "ud" }
            val acqCost = merc.acquisitionCost

            // Ventas procedentes de Cuadre de Caja (Inicial Local - Final Local)
            val movs = uiState.movimientosMercaderia.filter {
                it.mercaderiaId == merc.id && (it.jornadaId == currentJornada.id || (currentJornada.openedAt > 0 && it.date >= currentJornada.openedAt && (currentJornada.closedAt == null || it.date <= currentJornada.closedAt)))
            }
            val mermas = movs.filter { it.type.uppercase() == "MERMA" }.sumOf { it.quantity }
            val ventas = movs.filter { it.type.uppercase() == "VENTA" }.sumOf { if (it.quantitySold > 0) it.quantitySold else it.quantity }

            val ingresos = ventas * price
            val costoReal = ventas * acqCost
            val utilidad = ingresos - costoReal

            MercaderiaSummaryItem(
                mercaderiaId = merc.id,
                productId = merc.productId,
                productName = prodName,
                unit = unit,
                ventas = ventas,
                mermas = mermas,
                price = price,
                acquisitionCost = acqCost,
                ingresos = ingresos,
                costoReal = costoReal,
                utilidad = utilidad
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Banner aclaratorio de regla de Cuadre de Caja para Mercaderías
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Regla de Mercaderías: Las ventas proceden exclusivamente del Cuadre de Caja del Local (Existencia inicial − Existencia final). 'Para venta' de inventario es un traslado a Local y no cuenta como venta.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E),
                    lineHeight = 15.sp
                )
            }
        }

        // Resumen General de Mercaderías
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_resumen_mercaderias"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF2563EB).copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "RESULTADOS DE MERCADERÍAS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                val sumVentas = mercaderiaItems.sumOf { it.ventas }
                val sumMermas = mercaderiaItems.sumOf { it.mermas }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Unidades Vendidas:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(sumVentas)} uds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Mermas de Mercadería:", fontSize = 13.sp, color = Slate600)
                    Text("${"%.1f".format(sumMermas)} uds", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sumMermas > 0) Color(0xFFDC2626) else Slate700)
                }

                HorizontalDivider(color = Slate100)

                DuenoMetricRow(label = "Ingresos de Mercaderías", value = totalVentasMercaderia, isBold = false)
                DuenoMetricRow(label = "Costos Reales (Adquisición)", value = totalCostosMercaderia, isBold = false)
                DuenoMetricRow(
                    label = "Utilidad de Mercaderías",
                    value = totalUtilidadMercaderia,
                    isBold = true,
                    colorOverride = if (totalUtilidadMercaderia >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                )
            }
        }

        // Listado por Producto de Mercadería
        Text(
            text = "PRODUCTOS DE MERCADERÍA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (mercaderiaItems.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No existen productos de mercadería configurados.", color = Slate500, fontSize = 13.sp)
                }
            }
        } else {
            mercaderiaItems.forEach { item ->
                MercaderiaProductCard(item = item)
            }
        }
    }
}

private data class MercaderiaSummaryItem(
    val mercaderiaId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val ventas: Double,
    val mermas: Double,
    val price: Double,
    val acquisitionCost: Double,
    val ingresos: Double,
    val costoReal: Double,
    val utilidad: Double
)

@Composable
private fun MercaderiaProductCard(item: MercaderiaSummaryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_merc_${item.mercaderiaId}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = "Ventas: ${"%.1f".format(item.ventas)} ${item.unit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Precio Venta: $${"%.2f".format(item.price)}", fontSize = 12.sp, color = Slate700)
                Text("Costo Adquisición: $${"%.2f".format(item.acquisitionCost)}", fontSize = 12.sp, color = Slate600)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mermas: ${"%.1f".format(item.mermas)} ${item.unit}", fontSize = 12.sp, color = if (item.mermas > 0) Color(0xFFDC2626) else Slate600)
                Text("Costos Reales: $${"%.2f".format(item.costoReal)}", fontSize = 12.sp, color = Slate700)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingresos: $${"%.2f".format(item.ingresos)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    Text(
                        text = "Utilidad: $${"%.2f".format(item.utilidad)} CUP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (item.utilidad >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. LIQUIDEZ SUB-CONTENT
// ==========================================
@Composable
private fun ControlLiquidezContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    currentJornada: Jornada,
    ventasTotales: Double,
    transferenciasTotal: Double,
    transferenciasCount: Int,
    extraccionesText: String,
    onExtraccionesTextChange: (String) -> Unit,
    extraccionesVal: Double,
    utilidadCalculada: Double,
    liquidezCalculada: Double
) {
    val initialCash = currentJornada.initialCash
    val efectivoReal = currentJornada.finalCash

    // Efectivo Esperado procedente de Cuadre de Caja:
    // Fondo Inicial + Ventas Totales - Transferencias - Extracciones
    val efectivoEsperado = (initialCash + ventasTotales - transferenciasTotal - extraccionesVal).coerceAtLeast(0.0)
    val diferencia = efectivoReal - efectivoEsperado

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Tarjeta resumen de situación de caja procedente de Cuadre de Caja
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_situacion_efectivo"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(ElQadreGold.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Paid, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "SITUACIÓN DE EFECTIVO (CUADRE DE CAJA)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                DuenoMetricRow(label = "(+) Fondo Inicial de Caja", value = initialCash, isBold = false)
                DuenoMetricRow(label = "(+) Ventas Totales en Caja", value = ventasTotales, isBold = false)
                DuenoMetricRow(label = "(−) Transferencias Recibidas ($transferenciasCount)", value = transferenciasTotal, isBold = false)
                DuenoMetricRow(label = "(−) Extracciones Realizadas", value = extraccionesVal, isBold = false)

                HorizontalDivider(color = Slate200)

                DuenoMetricRow(
                    label = "(=) Efectivo Esperado en Caja",
                    value = efectivoEsperado,
                    isBold = true,
                    colorOverride = ElQadreNavy
                )
                DuenoMetricRow(
                    label = "Efectivo Real Contado",
                    value = efectivoReal,
                    isBold = true,
                    colorOverride = Color(0xFF2563EB)
                )

                // Badge de Diferencia
                val (diffColor, diffBg, diffLabel) = when {
                    kotlin.math.abs(diferencia) < 0.01 -> Triple(Color(0xFF047857), Color(0xFFECFDF5), "CUADRA EXACTO")
                    diferencia > 0 -> Triple(Color(0xFF2563EB), Color(0xFFEFF6FF), "SOBRANTE: +$${"%.2f".format(diferencia)} CUP")
                    else -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), "FALTANTE: −$${"%.2f".format(kotlin.math.abs(diferencia))} CUP")
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = diffBg,
                    border = BorderStroke(1.dp, diffColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resultado del Cuadre:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                        Text(
                            text = diffLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = diffColor
                        )
                    }
                }
            }
        }

        // Extracciones input
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_input_extracciones"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "EXTRACCIONES DEL DUEÑO",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
                Text(
                    text = "Registre o actualice el total de extracciones retiradas durante la jornada:",
                    fontSize = 12.sp,
                    color = Slate600
                )

                OutlinedTextField(
                    value = extraccionesText,
                    onValueChange = onExtraccionesTextChange,
                    label = { Text("Monto de Extracciones (CUP)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_extracciones_control"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy
                    )
                )
            }
        }

        // LIQUIDEZ FINAL RESULTANTE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_liquidez_final"),
            colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESULTADO DE LIQUIDEZ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreGold
                    )
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = ElQadreGold,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "Liquidez = Utilidad del Día ($${"%.2f".format(utilidadCalculada)}) − Extracciones ($${"%.2f".format(extraccionesVal)})",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Text(
                    text = "$${"%.2f".format(liquidezCalculada)} CUP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun DuenoMetricRow(
    label: String,
    value: Double,
    isBold: Boolean = false,
    colorOverride: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (isBold) ElQadreNavy else Slate700,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = "$${"%.2f".format(value)} CUP",
            fontSize = if (isBold) 14.sp else 13.sp,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.SemiBold,
            color = colorOverride ?: (if (isBold) ElQadreNavy else Slate800)
        )
    }
}
