package com.example.ui.screens.dueno

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Jornada
import com.example.data.local.model.Mercaderia
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import com.example.ui.screens.cajero.TransferenciasPane
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CuadreCajaPdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CuadreTab(val label: String) {
    PRODUCCION("PRODUCCIÓN"),
    MERCADERIA("MERCADERÍA"),
    TRANSFERENCIA("TRANSFERENCIA"),
    GENERALES("GENERALES"),
    PAGOS("PAGOS")
}

data class ProduccionItemState(
    val productId: Long,
    val productName: String,
    val unit: String,
    val price: Double,
    val tandasCount: Int,
    val totalProduced: Double,
    val qtyPerTanda: Double,
    var defectuosoStr: String = "0",
    var consumoStr: String = "0",
    var regaliaStr: String = "0"
) {
    val defectuoso: Double get() = defectuosoStr.toDoubleOrNull() ?: 0.0
    val consumo: Double get() = consumoStr.toDoubleOrNull() ?: 0.0
    val regalia: Double get() = regaliaStr.toDoubleOrNull() ?: 0.0
    val mermaTotal: Double get() = defectuoso + consumo + regalia
    val vendible: Double get() = (totalProduced - mermaTotal).coerceAtLeast(0.0)
    val ingresoEstimado: Double get() = vendible * price
    val mermaValor: Double get() = mermaTotal * price
}

data class MercaderiaItemState(
    val mercaderiaId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val price: Double,
    var existenciaInicialStr: String = "0",
    var entradasStr: String = "0",
    var existenciaFinalStr: String = "0",
    var defectuosoStr: String = "0",
    var consumoStr: String = "0",
    var regaliaStr: String = "0"
) {
    val existenciaInicial: Double get() = existenciaInicialStr.toDoubleOrNull() ?: 0.0
    val entradas: Double get() = entradasStr.toDoubleOrNull() ?: 0.0
    val existenciaFinal: Double get() = existenciaFinalStr.toDoubleOrNull() ?: 0.0
    val defectuoso: Double get() = defectuosoStr.toDoubleOrNull() ?: 0.0
    val consumo: Double get() = consumoStr.toDoubleOrNull() ?: 0.0
    val regalia: Double get() = regaliaStr.toDoubleOrNull() ?: 0.0
    val mermaTotal: Double get() = defectuoso + consumo + regalia
    // Ventas = Existencia inicial + Entradas - Existencia final - Mermas
    val ventas: Double get() = ((existenciaInicial + entradas - existenciaFinal) - mermaTotal).coerceAtLeast(0.0)
    val ingresoEstimado: Double get() = ventas * price
    val mermaValor: Double get() = mermaTotal * price
}

data class AgregadoCuadreItemState(
    val materiaPrimaId: Long,
    val name: String,
    val unit: String,
    val rationQuantity: Double,
    val rationUnit: String,
    val precioVenta: Double,
    val costoPorRacion: Double,
    var racionesEnviadasStr: String = "0",
    var racionesVendidasStr: String = "0",
    var racionesRegaliaStr: String = "0"
) {
    val racionesEnviadas: Double get() = racionesEnviadasStr.toDoubleOrNull() ?: 0.0
    val racionesVendidas: Double get() = racionesVendidasStr.toDoubleOrNull() ?: 0.0
    val racionesRegalia: Double get() = racionesRegaliaStr.toDoubleOrNull() ?: 0.0
    val racionesSobrantes: Double get() = (racionesEnviadas - racionesVendidas - racionesRegalia).coerceAtLeast(0.0)
    val ingresoEstimado: Double get() = racionesVendidas * precioVenta
    val costoVendido: Double get() = racionesVendidas * costoPorRacion
    val sobranteFisico: Double get() = racionesSobrantes * rationQuantity
}

@Composable
fun CuadreCajaScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(CuadreTab.PRODUCCION) }

    val activeJornada = uiState.activeJornada
    val initialCash = activeJornada?.initialCash ?: 0.0

    // Filter tandas of active jornada / current day
    val jornadaTandas = remember(uiState.tandas, activeJornada) {
        if (activeJornada != null) {
            uiState.tandas.filter { it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt) }
        } else {
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            uiState.tandas.filter { it.date >= startOfToday }
        }
    }

    // Build Produccion item states from registered tandas
    val produccionStates = remember(jornadaTandas, uiState.products) {
        val grouped = jornadaTandas.groupBy { it.productId }
        grouped.map { (prodId, tandas) ->
            val product = uiState.products.find { it.id == prodId }
            val prodName = product?.name ?: tandas.firstOrNull()?.productName ?: "Producto #$prodId"
            val price = product?.price ?: tandas.firstOrNull()?.salePrice ?: 0.0
            val unit = product?.unitOfMeasure ?: tandas.firstOrNull()?.productionUnit ?: "U"
            val totalQty = tandas.sumOf { if (it.actualYield > 0) it.actualYield else it.estimatedYield }
            val count = tandas.size
            val perTanda = if (count > 0) totalQty / count else 0.0

            ProduccionItemState(
                productId = prodId,
                productName = prodName,
                unit = unit,
                price = price,
                tandasCount = count,
                totalProduced = totalQty,
                qtyPerTanda = perTanda
            )
        }.toMutableStateList()
    }

    // Build Mercaderias item states from active mercaderias
    val mercaderiasStates = remember(uiState.mercaderias, uiState.products) {
        uiState.mercaderias.filter { it.isActive }.map { merc ->
            val product = uiState.products.find { it.id == merc.productId }
            val prodName = product?.name ?: "Mercadería #${merc.id}"
            val price = product?.price ?: 0.0
            val unit = merc.unitOfMeasure.ifBlank { product?.unitOfMeasure ?: "U" }

            MercaderiaItemState(
                mercaderiaId = merc.id,
                productId = merc.productId,
                productName = prodName,
                unit = unit,
                price = price,
                existenciaInicialStr = "0",
                existenciaFinalStr = "0"
            )
        }.toMutableStateList()
    }

    // Build Agregados item states from active materias primas configured as agregados
    val agregadosStates = remember(uiState.materiasPrimas) {
        uiState.materiasPrimas.filter { it.isAgregado && it.isActive }.map { mp ->
            val enviadasRaciones = if (mp.racionesEnVenta > 0.0) {
                mp.racionesEnVenta
            } else if (mp.stockEnVenta > 0.0 && mp.rationQuantity > 0.0) {
                mp.stockEnVenta / mp.rationQuantity
            } else {
                0.0
            }
            AgregadoCuadreItemState(
                materiaPrimaId = mp.id,
                name = mp.name,
                unit = mp.unit,
                rationQuantity = mp.rationQuantity,
                rationUnit = mp.rationUnit.ifBlank { mp.unit },
                precioVenta = mp.precioEfectivoVenta,
                costoPorRacion = mp.costoPorRacion,
                racionesEnviadasStr = if (enviadasRaciones > 0.0) "%.1f".format(enviadasRaciones).replace(',', '.') else "0",
                racionesVendidasStr = "0",
                racionesRegaliaStr = "0"
            )
        }.toMutableStateList()
    }

    // Calculate Transferencias
    val transferenciasForJornada = remember(uiState.allTransferencias, activeJornada) {
        if (activeJornada != null && activeJornada.openedAt > 0) {
            uiState.allTransferencias.filter { it.receivedAt >= activeJornada.openedAt }
        } else {
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            uiState.allTransferencias.filter { it.receivedAt >= startOfToday }
        }
    }
    val transferenciasTotalMonto = remember(transferenciasForJornada) {
        transferenciasForJornada.sumOf { it.amount }
    }

    // Extracciones & Cash & Notes State
    var extraccionesStr by rememberSaveable { mutableStateOf(if (activeJornada?.extracciones ?: 0.0 > 0.0) (activeJornada?.extracciones ?: 0.0).toString() else "0") }
    var extraccionesNota by rememberSaveable { mutableStateOf("") }
    var efectivoRealStr by rememberSaveable { mutableStateOf("") }
    var notasCuadre by rememberSaveable { mutableStateOf(activeJornada?.notes ?: "") }

    var isCuadrado by rememberSaveable { mutableStateOf(false) }
    var lastGeneratedPdfFile by remember { mutableStateOf<File?>(null) }

    // Dynamic Calculations
    val ingresosAgregados = agregadosStates.sumOf { it.ingresoEstimado }
    val ingresosProduccion = produccionStates.sumOf { it.ingresoEstimado } + ingresosAgregados
    val ingresosMercaderias = mercaderiasStates.sumOf { it.ingresoEstimado }
    val totalMermasValor = produccionStates.sumOf { it.mermaValor } + mercaderiasStates.sumOf { it.mermaValor }
    val totalMermasUnidades = produccionStates.sumOf { it.mermaTotal } + mercaderiasStates.sumOf { it.mermaTotal }

    val extraccionesVal = extraccionesStr.toDoubleOrNull() ?: 0.0
    val efectivoRealVal = efectivoRealStr.toDoubleOrNull() ?: 0.0

    // Efectivo Esperado en Caja:
    // Fondo Inicial + Ingresos Producción + Ingresos Mercaderías - Transferencias Recibidas - Extracciones
    val efectivoEsperado = (initialCash + ingresosProduccion + ingresosMercaderias - transferenciasTotalMonto - extraccionesVal).coerceAtLeast(0.0)
    val diferencia = efectivoRealVal - efectivoEsperado

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElQadreBackground)
    ) {
        // TOP APP BAR / HEADER
        Surface(
            color = ElQadreNavy,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreGold.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                Icons.Outlined.PointOfSale,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier.padding(6.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CUADRE DE CAJA",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            val jornadaLabel = if (activeJornada != null) "Jornada #${activeJornada.id} - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(activeJornada.openedAt))}" else "Jornada del día"
                            Text(
                                text = jornadaLabel,
                                fontSize = 11.sp,
                                color = Slate300
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("btn_cerrar_cuadre_caja")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TOP TABS: PRODUCCIÓN | MERCADERÍA | TRANSFERENCIA | GENERALES | PAGOS
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CuadreTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val bgModifier = if (isSelected) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElQadreGold)
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTab = tab }
                            }

                            Box(
                                modifier = bgModifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp, horizontal = 2.dp)
                                    .testTag("tab_cuadre_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) ElQadreNavy else Slate300,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // CONTENT OF SELECTED TAB
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                CuadreTab.PRODUCCION -> {
                    CuadreProduccionTab(
                        produccionStates = produccionStates,
                        agregadosStates = agregadosStates
                    )
                }

                CuadreTab.MERCADERIA -> {
                    CuadreMercaderiasTab(
                        mercaderiasStates = mercaderiasStates
                    )
                }

                CuadreTab.TRANSFERENCIA -> {
                    TransferenciasPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }

                CuadreTab.GENERALES -> {
                    CuadreGeneralesTab(
                        uiState = uiState,
                        activeJornada = activeJornada,
                        initialCash = initialCash,
                        ingresosProduccion = ingresosProduccion,
                        ingresosMercaderias = ingresosMercaderias,
                        totalMermasValor = totalMermasValor,
                        totalMermasUnidades = totalMermasUnidades,
                        transferenciasMonto = transferenciasTotalMonto,
                        transferenciasCount = transferenciasForJornada.size,
                        extraccionesStr = extraccionesStr,
                        onExtraccionesChange = { extraccionesStr = it },
                        extraccionesNota = extraccionesNota,
                        onExtraccionesNotaChange = { extraccionesNota = it },
                        efectivoEsperado = efectivoEsperado,
                        efectivoRealStr = efectivoRealStr,
                        onEfectivoRealChange = { efectivoRealStr = it },
                        diferencia = diferencia,
                        notasCuadre = notasCuadre,
                        onNotasCuadreChange = { notasCuadre = it },
                        isCuadrado = isCuadrado,
                        onCuadrarCaja = {
                            isCuadrado = true
                            val jId = activeJornada?.id ?: 1L
                            val mercCuadreItems = mercaderiasStates.map { m ->
                                com.example.ui.viewmodel.MercaderiaCuadreItem(
                                    mercaderiaId = m.mercaderiaId,
                                    productId = m.productId,
                                    ventas = m.ventas,
                                    mermas = m.mermaTotal,
                                    price = m.price
                                )
                            }
                            val agCuadreItems = agregadosStates.map { ag ->
                                com.example.ui.viewmodel.AgregadoCuadreItem(
                                    materiaPrimaId = ag.materiaPrimaId,
                                    name = ag.name,
                                    racionesEnviadas = ag.racionesEnviadas,
                                    racionesVendidas = ag.racionesVendidas,
                                    racionesRegalia = ag.racionesRegalia,
                                    racionesSobrantes = ag.racionesSobrantes,
                                    precioVenta = ag.precioVenta,
                                    costoPorRacion = ag.costoPorRacion,
                                    rationQuantity = ag.rationQuantity,
                                    unit = ag.unit
                                )
                            }
                            viewModel.registrarCuadreDueno(
                                jornadaId = jId,
                                realCash = efectivoRealVal,
                                expectedCash = efectivoEsperado,
                                diferencia = diferencia,
                                ingresosProduccion = ingresosProduccion,
                                ingresosMercaderias = ingresosMercaderias,
                                mermas = totalMermasValor,
                                transferencias = transferenciasTotalMonto,
                                extracciones = extraccionesVal,
                                notes = notasCuadre,
                                mercaderiaItems = mercCuadreItems,
                                agregadoItems = agCuadreItems
                            )
                            Toast.makeText(context, "¡Cuadre de caja registrado con éxito!", Toast.LENGTH_SHORT).show()
                        },
                        onDescargarPdf = {
                            val reportData = CuadreCajaPdfExporter.CuadreCajaReportData(
                                businessName = uiState.businessConfig?.nombreNegocio ?: "EL QADRE",
                                duenoName = uiState.currentUser?.username ?: "DUEÑO",
                                jornadaId = activeJornada?.id ?: 1L,
                                openedAt = activeJornada?.openedAt ?: System.currentTimeMillis(),
                                closedAt = System.currentTimeMillis(),
                                initialCash = initialCash,
                                ingresosProduccion = ingresosProduccion,
                                ingresosMercaderias = ingresosMercaderias,
                                mermasTotalValor = totalMermasValor,
                                transferenciasMonto = transferenciasTotalMonto,
                                transferenciasCount = transferenciasForJornada.size,
                                extracciones = extraccionesVal,
                                extraccionesNotas = extraccionesNota,
                                efectivoEsperado = efectivoEsperado,
                                efectivoReal = efectivoRealVal,
                                diferencia = diferencia,
                                notas = notasCuadre,
                                produccionRows = produccionStates.map { p ->
                                    CuadreCajaPdfExporter.ProduccionPdfRow(
                                        productName = p.productName,
                                        tandasCount = p.tandasCount,
                                        totalProduced = p.totalProduced,
                                        unit = p.unit,
                                        defectuoso = p.defectuoso,
                                        consumo = p.consumo,
                                        regalia = p.regalia,
                                        vendible = p.vendible,
                                        price = p.price,
                                        ingresoEstimado = p.ingresoEstimado
                                    )
                                },
                                mercaderiaRows = mercaderiasStates.map { m ->
                                    CuadreCajaPdfExporter.MercaderiaPdfRow(
                                        productName = m.productName,
                                        unit = m.unit,
                                        existenciaInicial = m.existenciaInicial,
                                        existenciaFinal = m.existenciaFinal,
                                        defectuoso = m.defectuoso,
                                        consumo = m.consumo,
                                        regalia = m.regalia,
                                        vendidas = m.ventas,
                                        price = m.price,
                                        ingresoEstimado = m.ingresoEstimado
                                    )
                                },
                                agregadoRows = agregadosStates.map { ag ->
                                    CuadreCajaPdfExporter.AgregadoPdfRow(
                                        name = ag.name,
                                        enviadas = ag.racionesEnviadas,
                                        vendidas = ag.racionesVendidas,
                                        regalia = ag.racionesRegalia,
                                        sobrantes = ag.racionesSobrantes,
                                        price = ag.precioVenta,
                                        ingresoEstimado = ag.ingresoEstimado
                                    )
                                }
                            )

                            val pdfFile = CuadreCajaPdfExporter.exportCuadreCajaReport(context, reportData)
                            if (pdfFile != null) {
                                lastGeneratedPdfFile = pdfFile
                                CuadreCajaPdfExporter.shareCuadreCajaReport(context, pdfFile)
                            }
                        }
                    )
                }

                CuadreTab.PAGOS -> {
                    CuadrePagosTab(
                        produccionStates = produccionStates,
                        mercaderiasStates = mercaderiasStates
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// -------------------------------------------------------------
// TAB 1: INICIO (Resumen de la Jornada, Cuadrar y PDF)
// -------------------------------------------------------------
@Composable
fun CuadreInicioTab(
    uiState: MainUiState,
    activeJornada: Jornada?,
    initialCash: Double,
    ingresosProduccion: Double,
    ingresosMercaderias: Double,
    totalMermasValor: Double,
    totalMermasUnidades: Double,
    transferenciasMonto: Double,
    transferenciasCount: Int,
    extraccionesStr: String,
    onExtraccionesChange: (String) -> Unit,
    extraccionesNota: String,
    onExtraccionesNotaChange: (String) -> Unit,
    efectivoEsperado: Double,
    efectivoRealStr: String,
    onEfectivoRealChange: (String) -> Unit,
    diferencia: Double,
    notasCuadre: String,
    onNotasCuadreChange: (String) -> Unit,
    isCuadrado: Boolean,
    onCuadrarCaja: () -> Unit,
    onDescargarPdf: () -> Unit
) {
    val scrollState = rememberScrollState()

    val difColor = when {
        diferencia > 0.01 -> Color(0xFF047857)
        diferencia < -0.01 -> Color(0xFFDC2626)
        else -> ElQadreNavy
    }
    val difText = when {
        diferencia > 0.01 -> "+$${"%.2f".format(diferencia)} CUP"
        diferencia < -0.01 -> "-$${"%.2f".format(-diferencia)} CUP"
        else -> "$0.00 CUP"
    }
    val difBadgeLabel = when {
        diferencia > 0.01 -> "SOBRANTE"
        diferencia < -0.01 -> "FALTANTE"
        else -> "CUADRE EXACTO"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. VALORES PRINCIPALES (HERO CARDS)
        // ==========================================
        Text(
            text = "VALORES PRINCIPALES DEL CUADRE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // CARD 1: EFECTIVO CONTADO (ENTRADA DIRECTA)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "EFECTIVO CONTADO",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Dinero físico contado en la caja",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE0F2FE)
                    ) {
                        Text(
                            text = "FÍSICO REAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = efectivoRealStr,
                    onValueChange = { onEfectivoRealChange(it.filter { c -> c.isDigit() || c == '.' }) },
                    label = { Text("Efectivo real en Caja (CUP)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    placeholder = { Text("0.00", fontSize = 15.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0284C7),
                        focusedLabelColor = Color(0xFF0284C7)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_efectivo_real_cuadre")
                )
            }
        }

        // ROW WITH 2 KEY METRICS: EFECTIVO ESPERADO & DIFERENCIA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 2: EFECTIVO ESPERADO
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.weight(1f)
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
                            text = "EFECTIVO ESPERADO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate600
                        )
                        Icon(
                            imageVector = Icons.Outlined.Calculate,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "$${"%.2f".format(efectivoEsperado)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )

                    Text(
                        text = "CUP calculado en sistema",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            }

            // CARD 3: DIFERENCIA
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = difColor.copy(alpha = 0.06f),
                border = BorderStroke(1.5.dp, difColor.copy(alpha = 0.35f)),
                shadowElevation = 2.dp,
                modifier = Modifier.weight(1f)
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
                            text = "DIFERENCIA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = difColor
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = difColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = difBadgeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = difColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = difText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = difColor
                    )

                    Text(
                        text = if (diferencia == 0.0) "Caja perfectamente cuadrada" else "Físico vs Esperado",
                        fontSize = 10.sp,
                        color = difColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // ==========================================
        // 2. DESGLOSE EXISTENTE (BLOQUES SEPARADOS)
        // ==========================================
        Text(
            text = "DESGLOSE Y COMPONENTES DEL CUADRE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Slate600,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // BLOQUE 1: VENTAS E INGRESOS ESPERADOS
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF15803D).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "VENTAS E INGRESOS ESPERADOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "Fondo Inicial en Caja",
                    value = "$${"%.2f".format(initialCash)} CUP",
                    color = Slate700
                )

                CuadreMetricRow(
                    label = "Ingresos esperados de Producción",
                    value = "+$${"%.2f".format(ingresosProduccion)} CUP",
                    color = Color(0xFF15803D),
                    isBold = true
                )

                CuadreMetricRow(
                    label = "Ingresos esperados de Mercaderías",
                    value = "+$${"%.2f".format(ingresosMercaderias)} CUP",
                    color = Color(0xFF15803D),
                    isBold = true
                )
            }
        }

        // BLOQUE 2: MERMAS REGISTRADAS
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "MERMAS DE LA JORNADA",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "Mermas registradas (${"%.1f".format(totalMermasUnidades)} unid.)",
                    value = "$${"%.2f".format(totalMermasValor)} CUP",
                    color = Color(0xFFB91C1C),
                    isBold = true
                )
            }
        }

        // BLOQUE 3: TRANSFERENCIAS Y EXTRACCIONES (SALIDAS DE CAJA)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "TRANSFERENCIAS Y EXTRACCIONES",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "Transferencias ($transferenciasCount operaciones)",
                    value = "-$${"%.2f".format(transferenciasMonto)} CUP",
                    color = Color(0xFF0284C7),
                    isBold = true
                )

                // Subsección Extracciones
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7).copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extracciones de Caja (reducen el efectivo)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = extraccionesStr,
                                onValueChange = { onExtraccionesChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                label = { Text("Monto CUP", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_extracciones_cuadre")
                            )
                            OutlinedTextField(
                                value = extraccionesNota,
                                onValueChange = onExtraccionesNotaChange,
                                label = { Text("Motivo / Destino", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("input_extracciones_motivo_cuadre")
                            )
                        }
                    }
                }
            }
        }

        // BLOQUE 4: NOTAS Y OBSERVACIONES DEL CUADRE
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Notes,
                                contentDescription = null,
                                tint = Slate600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "NOTAS Y OBSERVACIONES",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }

                OutlinedTextField(
                    value = notasCuadre,
                    onValueChange = onNotasCuadreChange,
                    label = { Text("Notas / Observaciones del Cuadre", fontSize = 12.sp) },
                    placeholder = { Text("Detalles de la jornada o incidentes...", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .testTag("input_notas_cuadre"),
                    maxLines = 3
                )
            }
        }

        // ==========================================
        // 3. BOTONES DE ACCIÓN (CUADRAR Y PDF)
        // ==========================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCuadrarCaja,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_cuadrar_caja_action")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = ElQadreGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CUADRAR CAJA",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            if (isCuadrado) {
                Button(
                    onClick = onDescargarPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_descargar_pdf_cuadre")
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DESCARGAR PDF DE CUADRE",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
    }
}

@Composable
private fun CuadreMetricRow(
    label: String,
    value: String,
    color: Color = Slate700,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Slate700,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.SemiBold,
            color = color
        )
    }
}

// -------------------------------------------------------------
// TAB 2: PRODUCCIÓN (Tandas e Ingresos con Mermas y Agregados)
// -------------------------------------------------------------
@Composable
fun CuadreProduccionTab(
    produccionStates: List<ProduccionItemState>,
    agregadosStates: List<AgregadoCuadreItemState> = emptyList()
) {
    if (produccionStates.isEmpty() && agregadosStates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.SoupKitchen,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No hay tandas de producción ni agregados en esta jornada.",
                    textAlign = TextAlign.Center,
                    color = Slate600,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Procese mermas de productos principales y raciones de agregados enviadas, vendidas, regalía y sobrantes.",
                        fontSize = 12.sp,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }
        }

        if (produccionStates.isNotEmpty()) {
            item {
                Text(
                    text = "PRODUCTOS PRINCIPALES (TANDAS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate600,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(produccionStates) { item ->
                ProduccionCuadreCard(item = item)
            }
        }

        if (agregadosStates.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AGREGADOS VINCULADOS A PRODUCTOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(agregadosStates) { item ->
                AgregadoCuadreCard(item = item)
            }
        }

        item {
            Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun AgregadoCuadreCard(
    item: AgregadoCuadreItemState
) {
    var enviadasText by remember { mutableStateOf(item.racionesEnviadasStr) }
    var vendidasText by remember { mutableStateOf(item.racionesVendidasStr) }
    var regaliaText by remember { mutableStateOf(item.racionesRegaliaStr) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                        Text(
                            text = "AGREGADO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ElQadreNavy
                    )
                }
                Text(
                    text = "$${"%.2f".format(item.precioVenta)} CUP/rac.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600
                )
            }

            HorizontalDivider(color = Slate100)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CONTROL DE RACIONES (1 ración = ${item.rationQuantity} ${item.rationUnit}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = enviadasText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            enviadasText = clean
                            item.racionesEnviadasStr = clean
                        },
                        label = { Text("Enviadas", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = vendidasText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            vendidasText = clean
                            item.racionesVendidasStr = clean
                        },
                        label = { Text("Vendidas", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = regaliaText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            regaliaText = clean
                            item.racionesRegaliaStr = clean
                        },
                        label = { Text("Regalía", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // RESULTS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sobrantes: ${"%.1f".format(item.racionesSobrantes)} raciones (${"%.2f".format(item.sobranteFisico)} ${item.unit})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                    Text(
                        text = "Regresan al inventario al cerrar jornada",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }

                Text(
                    text = "Venta: $${"%.2f".format(item.ingresoEstimado)} CUP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}

@Composable
fun ProduccionCuadreCard(
    item: ProduccionItemState
) {
    var defectuosoText by remember { mutableStateOf(item.defectuosoStr) }
    var consumoText by remember { mutableStateOf(item.consumoStr) }
    var regaliaText by remember { mutableStateOf(item.regaliaStr) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ElQadreNavy
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "Precio: $${"%.2f".format(item.price)} CUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElQadreNavy,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate200)

            // Production details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tandas:", fontSize = 11.sp, color = Slate500)
                    Text("${item.tandasCount}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                }
                Column {
                    Text("Por tanda:", fontSize = 11.sp, color = Slate500)
                    Text("${"%.1f".format(item.qtyPerTanda)} ${item.unit}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                }
                Column {
                    Text("Total Producido:", fontSize = 11.sp, color = Slate500)
                    Text("${"%.1f".format(item.totalProduced)} ${item.unit}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                }
            }

            // MERMAS SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFBEB), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MERMA (Reduce cantidad vendible):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = defectuosoText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            defectuosoText = clean
                            item.defectuosoStr = clean
                        },
                        label = { Text("Defectuoso", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = consumoText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            consumoText = clean
                            item.consumoStr = clean
                        },
                        label = { Text("Consumo", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = regaliaText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            regaliaText = clean
                            item.regaliaStr = clean
                        },
                        label = { Text("Regalía", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // RESULTS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vendible: ${"%.1f".format(item.vendible)} ${item.unit}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )
                Text(
                    text = "Ingreso Estimado: $${"%.2f".format(item.ingresoEstimado)} CUP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: MERCADERÍAS (Local de Ventas)
// -------------------------------------------------------------
@Composable
fun CuadreMercaderiasTab(
    mercaderiasStates: List<MercaderiaItemState>
) {
    if (mercaderiasStates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No hay productos de Mercaderías configurados en el sistema.",
                    textAlign = TextAlign.Center,
                    color = Slate600,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF3C7).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Control del LOCAL DE VENTAS (independiente de Almacén). Ventas = Existencia inicial − Existencia final − Mermas.",
                        fontSize = 12.sp,
                        color = Color(0xFF78350F)
                    )
                }
            }
        }

        items(mercaderiasStates) { item ->
            MercaderiaCuadreCard(item = item)
        }

        item {
            Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun MercaderiaCuadreCard(
    item: MercaderiaItemState
) {
    var inicialText by remember { mutableStateOf(item.existenciaInicialStr) }
    var finalText by remember { mutableStateOf(item.existenciaFinalStr) }
    var defectuosoText by remember { mutableStateOf(item.defectuosoStr) }
    var consumoText by remember { mutableStateOf(item.consumoStr) }
    var regaliaText by remember { mutableStateOf(item.regaliaStr) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ElQadreNavy
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "Precio: $${"%.2f".format(item.price)} CUP",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElQadreNavy,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate200)

            // EXISTENCIAS LOCAL DE VENTAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inicialText,
                    onValueChange = {
                        val clean = it.filter { c -> c.isDigit() || c == '.' }
                        inicialText = clean
                        item.existenciaInicialStr = clean
                    },
                    label = { Text("Existencia inicial", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = finalText,
                    onValueChange = {
                        val clean = it.filter { c -> c.isDigit() || c == '.' }
                        finalText = clean
                        item.existenciaFinalStr = clean
                    },
                    label = { Text("Existencia final", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // MERMAS SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFBEB), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MERMA (Defectuoso, Consumo, Regalía):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = defectuosoText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            defectuosoText = clean
                            item.defectuosoStr = clean
                        },
                        label = { Text("Defectuoso", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = consumoText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            consumoText = clean
                            item.consumoStr = clean
                        },
                        label = { Text("Consumo", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = regaliaText,
                        onValueChange = {
                            val clean = it.filter { c -> c.isDigit() || c == '.' }
                            regaliaText = clean
                            item.regaliaStr = clean
                        },
                        label = { Text("Regalía", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // RESULTS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ventas: ${"%.1f".format(item.ventas)} ${item.unit}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )
                Text(
                    text = "Ingreso Estimado: $${"%.2f".format(item.ingresoEstimado)} CUP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}
