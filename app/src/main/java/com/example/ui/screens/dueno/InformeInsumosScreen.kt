package com.example.ui.screens.dueno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.MovimientoMateriaPrima
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.InformeInsumosPdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * INFORME INSUMOS — Repositorio de consulta rápida para el DUEÑO
 * Permite consultar directamente desde Inicio la situación de los insumos:
 * INICIO | ENTRADAS | FINAL | IMPORTE
 * 
 * Permite pulsar cualquier insumo para ver su detalle individual completo y la
 * composición exacta de sus existencias a diferentes precios.
 * 
 * Utiliza datos reales del inventario existente sin duplicar ni crear datos ficticios.
 * Diseñado con tipografía grande, alto contraste y lectura cómoda para personas mayores.
 */
@Composable
fun InformeInsumosScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedInsumoForDetail by remember { mutableStateOf<MateriaPrima?>(null) }
    var showGraficosDialog by remember { mutableStateOf(false) }
    var showPdfSuccessDialog by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var showArchivoScreen by remember { mutableStateOf(false) }

    if (showArchivoScreen) {
        InformeInsumosArchivoScreen(
            uiState = uiState,
            onBack = { showArchivoScreen = false },
            modifier = modifier
        )
        return
    }

    val activeJornada = uiState.activeJornada
    val isJornadaOpen = activeJornada != null && activeJornada.isOpen

    // Filtrar insumos activos o existentes
    val materiasPrimas = remember(uiState.materiasPrimas) {
        uiState.materiasPrimas.filter { it.isActive }
    }

    // Movimientos de insumos de la jornada activa (para calcular ENTRADAS reales)
    val movimientosJornada = remember(uiState.movimientosMateriaPrima, activeJornada) {
        if (activeJornada != null && activeJornada.isOpen) {
            uiState.movimientosMateriaPrima.filter { it.date >= activeJornada.openedAt }
        } else {
            emptyList()
        }
    }

    // Filtrado por búsqueda
    val filteredInsumos = remember(materiasPrimas, searchQuery) {
        if (searchQuery.isBlank()) {
            materiasPrimas
        } else {
            val query = searchQuery.trim().lowercase()
            materiasPrimas.filter { it.name.lowercase().contains(query) }
        }
    }

    // Cálculo de totales generales del informe
    val totalInsumosCount = materiasPrimas.size
    val totalImporteGeneral = remember(materiasPrimas) {
        materiasPrimas.sumOf { it.stock * it.unitCost }
    }
    val totalEntradasJornadaCount = remember(movimientosJornada) {
        movimientosJornada.count { it.type == "ENTRADA" }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Slate100, RoundedCornerShape(12.dp))
                                    .testTag("btn_volver_informe_insumos")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver a Inicio",
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "INFORME INSUMOS",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Consulta Rápida de Existencias e Importes",
                                    fontSize = 12.sp,
                                    color = Slate600,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Badge de estado de jornada
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isJornadaOpen) Color(0xFFDCFCE7) else Slate100,
                            border = BorderStroke(1.dp, if (isJornadaOpen) Color(0xFF86EFAC) else Slate300)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isJornadaOpen) Color(0xFF16A34A) else Slate500,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Text(
                                    text = if (isJornadaOpen) "JORNADA #${activeJornada.id}" else "SIN JORNADA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isJornadaOpen) Color(0xFF15803D) else Slate700
                                )
                            }
                        }
                    }

                    // Barra de búsqueda con tipografía grande y cómoda
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Buscar insumo por nombre...",
                                fontSize = 15.sp,
                                color = Slate400
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = Slate500
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Slate50,
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_buscar_informe_insumos")
                    )

                    // Barra Superior de 3 Acciones Principales
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. DESCARGAR PDF
                        Surface(
                            onClick = {
                                val file = InformeInsumosPdfExporter.generatePdf(
                                    context = context,
                                    uiStateMaterias = uiState.materiasPrimas,
                                    movimientosMateria = uiState.movimientosMateriaPrima,
                                    activeJornada = activeJornada,
                                    businessName = uiState.businessName,
                                    duenoName = uiState.currentUser?.fullName ?: "Administrador"
                                )
                                if (file != null) {
                                    generatedPdfFile = file
                                    showPdfSuccessDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_descargar_pdf_insumos")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "DESCARGAR PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1E40AF),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 2. GENERAR GRÁFICOS
                        Surface(
                            onClick = { showGraficosDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generar_graficos_insumos")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PieChart,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "GENERAR GRÁFICOS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF92400E),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 3. ARCHIVO (Repositorio Histórico de Jornadas Cerradas)
                        Surface(
                            onClick = { showArchivoScreen = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate300),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_archivo_insumos")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderSpecial,
                                    contentDescription = null,
                                    tint = Slate600,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "ARCHIVO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Slate700,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF1F5F9),
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de Resumen General Superior
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_resumen_informe_insumos")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "VALORACIÓN TOTAL DE EXISTENCIAS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "$totalInsumosCount Insumos Registrados",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Text(
                            text = "$${"%.2f".format(totalImporteGeneral)} CUP",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

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
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = ElQadreGoldSoft,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isJornadaOpen) "Entradas hoy: $totalEntradasJornadaCount" else "Jornada no iniciada",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Text(
                                text = "Toca un insumo para ver su detalle",
                                fontSize = 11.5.sp,
                                color = ElQadreGoldSoft,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (filteredInsumos.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isBlank()) "No hay insumos registrados" else "No se encontraron insumos",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "Los insumos registrados en el sistema aparecerán aquí automáticamente para su consulta rápida."
                                } else {
                                    "No hay insumos que coincidan con \"$searchQuery\"."
                                },
                                fontSize = 13.sp,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Listado de Tarjetas de Insumos (INICIO | ENTRADAS | FINAL | IMPORTE)
                items(filteredInsumos, key = { it.id }) { insumo ->
                    // Calcular ENTRADAS durante la jornada activa para este insumo
                    val entradasInsumo = if (isJornadaOpen) {
                        movimientosJornada.filter { it.materiaPrimaId == insumo.id && it.type == "ENTRADA" }
                    } else {
                        emptyList()
                    }
                    val totalEntradasQty = entradasInsumo.sumOf { it.quantity }

                    // INICIO: Cantidad existente al comenzar la jornada (initialStock)
                    // Si no hay jornada activa, el stock inicial representativo es initialStock o stock
                    val inicioQty = if (isJornadaOpen) insumo.initialStock else insumo.stock

                    // FINAL: Cantidad existente actualmente en almacén
                    val finalQty = insumo.stock

                    // IMPORTE: Valor monetario de la existencia real disponible
                    val importe = insumo.stock * insumo.unitCost

                    InsumoInformeCard(
                        insumo = insumo,
                        inicioQty = inicioQty,
                        entradasQty = totalEntradasQty,
                        finalQty = finalQty,
                        importe = importe,
                        entradasMovimientos = entradasInsumo,
                        isJornadaOpen = isJornadaOpen,
                        onClick = { selectedInsumoForDetail = insumo }
                    )
                }
            }
        }
    }

    // Modal de Consulta Detallada Individual del Insumo y Composición de Precios
    selectedInsumoForDetail?.let { insumo ->
        val movimientosInsumo = remember(uiState.movimientosMateriaPrima, insumo.id) {
            uiState.movimientosMateriaPrima.filter { it.materiaPrimaId == insumo.id }
        }
        val entradasDeJornada = remember(movimientosInsumo, activeJornada) {
            if (activeJornada != null && activeJornada.isOpen) {
                movimientosInsumo.filter { it.type == "ENTRADA" && it.date >= activeJornada.openedAt }
            } else {
                emptyList()
            }
        }
        val consumosDeJornada = remember(movimientosInsumo, activeJornada) {
            if (activeJornada != null && activeJornada.isOpen) {
                movimientosInsumo.filter {
                    it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA") && it.date >= activeJornada.openedAt
                }
            } else {
                emptyList()
            }
        }

        DetalleInsumoIndividualDialog(
            insumo = insumo,
            activeJornada = activeJornada,
            isJornadaOpen = isJornadaOpen,
            entradasJornada = entradasDeJornada,
            consumosJornada = consumosDeJornada,
            allMovimientos = movimientosInsumo,
            onDismiss = { selectedInsumoForDetail = null }
        )
    }

    // Modal de Gráficos Analíticos de Insumos (Participación por importe, Mayor uso, Mayor movimiento)
    if (showGraficosDialog) {
        InformeInsumosGraficosDialog(
            materiasPrimas = uiState.materiasPrimas,
            movimientosMateria = uiState.movimientosMateriaPrima,
            activeJornada = activeJornada,
            isJornadaOpen = isJornadaOpen,
            onDismiss = { showGraficosDialog = false }
        )
    }

    // Modal de Descarga y Acciones de PDF Oficial (Compartir / Guardar en dispositivo)
    if (showPdfSuccessDialog && generatedPdfFile != null) {
        val pdfFile = generatedPdfFile!!
        val fileSizeKb = (pdfFile.length() / 1024).coerceAtLeast(1)

        Dialog(
            onDismissRequest = { showPdfSuccessDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PDF GENERADO EXITOSAMENTE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Informe Oficial de Insumos, Existencias e Importes",
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Tarjeta de información del archivo generado
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
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
                                Text("Archivo:", fontSize = 12.sp, color = Slate500)
                                Text(pdfFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800, maxLines = 1)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tamaño:", fontSize = 12.sp, color = Slate500)
                                Text("$fileSizeKb KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Jornada:", fontSize = 12.sp, color = Slate500)
                                Text(if (isJornadaOpen && activeJornada != null) "Jornada #${activeJornada.id}" else "Inventario General", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            }
                        }
                    }

                    // Botones de acción principales (Grandes y Cómodos)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. COMPARTIR
                        Button(
                            onClick = {
                                InformeInsumosPdfExporter.sharePdf(context, pdfFile)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_compartir_pdf_modal")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COMPARTIR PDF", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        // 2. GUARDAR EN EL DISPOSITIVO / ABRIR
                        Button(
                            onClick = {
                                InformeInsumosPdfExporter.saveToDeviceOrOpen(context, pdfFile)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_guardar_pdf_modal")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, tint = ElQadreGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GUARDAR EN DISPOSITIVO / ABRIR", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        // 3. CERRAR
                        OutlinedButton(
                            onClick = { showPdfSuccessDialog = false },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("CERRAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta individual de cada insumo en INFORME INSUMOS
 * Muestra con claridad y números grandes:
 * INICIO | ENTRADAS | FINAL | IMPORTE
 */
@Composable
private fun InsumoInformeCard(
    insumo: MateriaPrima,
    inicioQty: Double,
    entradasQty: Double,
    finalQty: Double,
    importe: Double,
    entradasMovimientos: List<MovimientoMateriaPrima>,
    isJornadaOpen: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, if (finalQty <= 0.0) Color(0xFFFCA5A5) else Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("card_informe_insumo_${insumo.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado del Insumo (Nombre, Unidad y Costo Ponderado)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insumo.name.uppercase(Locale.getDefault()),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Unidad: ${insumo.unit} | Costo unitario real: $${"%.2f".format(insumo.unitCost)} CUP/${insumo.unit}",
                        fontSize = 12.sp,
                        color = Slate600,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (insumo.isAgregado) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = "AGREGADO",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Slate100,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Ver detalle",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Slate200)

            // Cuadrícula 2x2 con los 4 indicadores fundamentales (Letras y Números Grandes)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // FILA 1: INICIO y ENTRADAS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // INICIO
                    MetricBox(
                        title = "INICIO",
                        subtitle = "Al iniciar jornada",
                        value = "${"%.2f".format(inicioQty)} ${insumo.unit}",
                        colorValue = Slate800,
                        backgroundColor = Color(0xFFF8FAFC),
                        borderColor = Slate300,
                        icon = Icons.Outlined.PlayCircleOutline,
                        iconTint = Slate600,
                        modifier = Modifier.weight(1f)
                    )

                    // ENTRADAS
                    MetricBox(
                        title = "ENTRADAS",
                        subtitle = if (entradasQty > 0) "${entradasMovimientos.size} entrada(s) hoy" else "Sin ingresos hoy",
                        value = if (entradasQty > 0) "+${"%.2f".format(entradasQty)} ${insumo.unit}" else "0.00 ${insumo.unit}",
                        colorValue = if (entradasQty > 0) Color(0xFF15803D) else Slate600,
                        backgroundColor = if (entradasQty > 0) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                        borderColor = if (entradasQty > 0) Color(0xFF86EFAC) else Slate300,
                        icon = Icons.Outlined.AddCircleOutline,
                        iconTint = if (entradasQty > 0) Color(0xFF16A34A) else Slate500,
                        modifier = Modifier.weight(1f)
                    )
                }

                // FILA 2: FINAL e IMPORTE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // FINAL
                    MetricBox(
                        title = "FINAL",
                        subtitle = "Existencia disponible",
                        value = "${"%.2f".format(finalQty)} ${insumo.unit}",
                        colorValue = if (finalQty <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E),
                        backgroundColor = if (finalQty <= 0.0) Color(0xFFFEF2F2) else Color(0xFFF0FDFA),
                        borderColor = if (finalQty <= 0.0) Color(0xFFFECACA) else Color(0xFF99F6E4),
                        icon = Icons.Outlined.Inventory2,
                        iconTint = if (finalQty <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E),
                        modifier = Modifier.weight(1f)
                    )

                    // IMPORTE
                    MetricBox(
                        title = "IMPORTE",
                        subtitle = "Valoración monetaria",
                        value = "$${"%.2f".format(importe)} CUP",
                        colorValue = Color(0xFFB45309),
                        backgroundColor = Color(0xFFFFFBEB),
                        borderColor = Color(0xFFFDE68A),
                        icon = Icons.Outlined.MonetizationOn,
                        iconTint = Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Pie informativo accesible para tocar y ver desglose
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate50, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Toca para ver composición de precios y movimientos",
                        fontSize = 12.sp,
                        color = Slate700,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Detalle →",
                    fontSize = 12.sp,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Cuadro de métrica individual con alto contraste y diseño accesible
 */
@Composable
private fun MetricBox(
    title: String,
    subtitle: String,
    value: String,
    colorValue: Color,
    backgroundColor: Color,
    borderColor: Color,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = colorValue,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = colorValue
            )

            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = Slate500,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Modelo interno para representar una capa/lote de precio del insumo
 */
private data class LotePrecioInsumo(
    val concepto: String,
    val cantidad: Double,
    val unidad: String,
    val precioUnitario: Double,
    val importe: Double,
    val fechaHora: String? = null,
    val observacion: String = ""
)

/**
 * DIALOGO DE DETALLE INDIVIDUAL Y VALORACIÓN DE INSUMO
 * Muestra detalladamente la composición de las existencias y cómo se calcula el IMPORTE
 * respetando las adquisiciones realizadas a diferentes precios.
 */
@Composable
private fun DetalleInsumoIndividualDialog(
    insumo: MateriaPrima,
    activeJornada: com.example.data.local.model.Jornada?,
    isJornadaOpen: Boolean,
    entradasJornada: List<MovimientoMateriaPrima>,
    consumosJornada: List<MovimientoMateriaPrima>,
    allMovimientos: List<MovimientoMateriaPrima>,
    onDismiss: () -> Unit
) {
    val inicioQty = if (isJornadaOpen) insumo.initialStock else insumo.stock
    val totalEntradasQty = entradasJornada.sumOf { it.quantity }
    val totalConsumoQty = consumosJornada.sumOf { it.quantity }
    val finalQty = insumo.stock
    val importeFinal = insumo.stock * insumo.unitCost

    // Cálculo de desglose de precios y composición real de las existencias
    val lotesPrecios = remember(insumo, entradasJornada, inicioQty, isJornadaOpen) {
        val list = mutableListOf<LotePrecioInsumo>()

        // Procesar entradas de la jornada para extraer precios individuales registrados
        val lotesEntradas = entradasJornada.map { mov ->
            // Intentar extraer importe y costo unitario de la nota si fue registrada
            var unitPrice = insumo.unitCost
            val regexImporte = """importe\s*(?:de)?\s*\$?([0-9]+(?:\.[0-9]+)?)""".toRegex(RegexOption.IGNORE_CASE)
            val match = regexImporte.find(mov.notes)
            if (match != null) {
                val imp = match.groupValues[1].toDoubleOrNull() ?: 0.0
                if (mov.quantity > 0.0 && imp > 0.0) {
                    unitPrice = imp / mov.quantity
                }
            } else if (insumo.purchasePrice > 0.0) {
                unitPrice = insumo.purchasePrice
            }

            val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(mov.date))
            LotePrecioInsumo(
                concepto = "Entrada en Jornada",
                cantidad = mov.quantity,
                unidad = insumo.unit,
                precioUnitario = unitPrice,
                importe = mov.quantity * unitPrice,
                fechaHora = timeStr,
                observacion = mov.notes
            )
        }

        // Si hay stock inicial registrado
        if (inicioQty > 0.0) {
            // Costo estimado del stock inicial
            // Si hubo entradas que recalcularon el unitCost, el costo inicial original corresponde al remanente
            val totalValorEntradas = lotesEntradas.sumOf { it.importe }
            val totalStockHistorico = inicioQty + totalEntradasQty
            val initialUnitCost = if (totalStockHistorico > 0.0 && lotesEntradas.isNotEmpty()) {
                val computed = ((insumo.stock * insumo.unitCost) + (totalConsumoQty * insumo.unitCost) - totalValorEntradas) / inicioQty
                if (computed > 0.0) computed else insumo.unitCost
            } else {
                insumo.unitCost
            }

            list.add(
                LotePrecioInsumo(
                    concepto = "Existencia al Iniciar Jornada",
                    cantidad = inicioQty,
                    unidad = insumo.unit,
                    precioUnitario = initialUnitCost,
                    importe = inicioQty * initialUnitCost,
                    fechaHora = if (activeJornada != null) SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(activeJornada.openedAt)) else null,
                    observacion = "Stock proveniente del cierre de la jornada anterior"
                )
            )
        }

        // Añadir las entradas registradas
        list.addAll(lotesEntradas)

        // Si la lista está vacía pero hay stock disponible
        if (list.isEmpty() && finalQty > 0.0) {
            list.add(
                LotePrecioInsumo(
                    concepto = "Existencia en Almacén",
                    cantidad = finalQty,
                    unidad = insumo.unit,
                    precioUnitario = insumo.unitCost,
                    importe = finalQty * insumo.unitCost,
                    observacion = "Inventario disponible a costo unitario registrado"
                )
            )
        }

        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header del Dialog
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
                                text = "DETALLE DE INSUMO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp
                            )
                            if (isJornadaOpen && activeJornada != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDCFCE7),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                ) {
                                    Text(
                                        text = "JORNADA #${activeJornada.id}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = insumo.name.uppercase(Locale.getDefault()),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Slate100, CircleShape)
                            .testTag("btn_cerrar_detalle_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate700,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contenido Scrolleable con estructura clara
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. LOS 4 INDICADORES FUNDAMENTALES (Grandes y de Alto Contraste)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ESTADO DE EXISTENCIAS DE LA JORNADA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )

                            // Fila 1: INICIO y ENTRADAS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricBox(
                                    title = "INICIO",
                                    subtitle = "Al abrir jornada",
                                    value = "${"%.2f".format(inicioQty)} ${insumo.unit}",
                                    colorValue = Slate800,
                                    backgroundColor = Color.White,
                                    borderColor = Slate300,
                                    icon = Icons.Outlined.PlayCircleOutline,
                                    iconTint = Slate600,
                                    modifier = Modifier.weight(1f)
                                )

                                MetricBox(
                                    title = "ENTRADAS",
                                    subtitle = if (totalEntradasQty > 0) "${entradasJornada.size} entrada(s)" else "Sin ingresos",
                                    value = if (totalEntradasQty > 0) "+${"%.2f".format(totalEntradasQty)} ${insumo.unit}" else "0.00 ${insumo.unit}",
                                    colorValue = if (totalEntradasQty > 0) Color(0xFF15803D) else Slate600,
                                    backgroundColor = if (totalEntradasQty > 0) Color(0xFFF0FDF4) else Color.White,
                                    borderColor = if (totalEntradasQty > 0) Color(0xFF86EFAC) else Slate300,
                                    icon = Icons.Outlined.AddCircleOutline,
                                    iconTint = if (totalEntradasQty > 0) Color(0xFF16A34A) else Slate500,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Fila 2: FINAL e IMPORTE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricBox(
                                    title = "FINAL",
                                    subtitle = "Existencia actual",
                                    value = "${"%.2f".format(finalQty)} ${insumo.unit}",
                                    colorValue = if (finalQty <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E),
                                    backgroundColor = if (finalQty <= 0.0) Color(0xFFFEF2F2) else Color(0xFFF0FDFA),
                                    borderColor = if (finalQty <= 0.0) Color(0xFFFECACA) else Color(0xFF99F6E4),
                                    icon = Icons.Outlined.Inventory2,
                                    iconTint = if (finalQty <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E),
                                    modifier = Modifier.weight(1f)
                                )

                                MetricBox(
                                    title = "IMPORTE",
                                    subtitle = "Valor existencia final",
                                    value = "$${"%.2f".format(importeFinal)} CUP",
                                    colorValue = Color(0xFFB45309),
                                    backgroundColor = Color(0xFFFFFBEB),
                                    borderColor = Color(0xFFFDE68A),
                                    icon = Icons.Outlined.MonetizationOn,
                                    iconTint = Color(0xFFD97706),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (totalConsumoQty > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Consumo en Producción / Tandas de hoy:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF991B1B)
                                        )
                                        Text(
                                            text = "-${"%.2f".format(totalConsumoQty)} ${insumo.unit}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. COMPOSICIÓN DE EXISTENCIAS Y PRECIOS DIFERENCIADOS
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFD97706)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        color = Color(0xFFFEF3C7),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = Color(0xFFB45309),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "COMPOSICIÓN DE EXISTENCIAS Y PRECIOS",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "Desglose por costos reales de adquisición",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            // Listado de Lotes y Precios
                            if (lotesPrecios.isEmpty()) {
                                Text(
                                    text = "Sin existencias registradas actualmente.",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    lotesPrecios.forEachIndexed { idx, lote ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF8FAFC),
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
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = lote.concepto,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Slate800
                                                        )
                                                        if (lote.fechaHora != null) {
                                                            Text(
                                                                text = "(${lote.fechaHora})",
                                                                fontSize = 11.sp,
                                                                color = Slate500
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    // Fórmula clara: Ej. 20 kg × 100 CUP = 2,000 CUP
                                                    Text(
                                                        text = "${"%.2f".format(lote.cantidad)} ${lote.unidad} × $${"%.2f".format(lote.precioUnitario)} CUP = $${"%.2f".format(lote.importe)} CUP",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF0F766E)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            // Resumen de la Composición y Costo Promedio Ponderado
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "EXISTENCIA FINAL:",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Slate800
                                        )
                                        Text(
                                            text = "${"%.2f".format(finalQty)} ${insumo.unit}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "IMPORTE TOTAL:",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                        Text(
                                            text = "$${"%.2f".format(importeFinal)} CUP",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                    }

                                    Text(
                                        text = "Costo Unitario Promedio Ponderado: $${"%.2f".format(insumo.unitCost)} CUP / ${insumo.unit}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate600
                                    )
                                }
                            }
                        }
                    }

                    // 3. HISTORIAL DE MOVIMIENTOS REGISTRADOS
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "MOVIMIENTOS DE ESTE INSUMO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )

                            if (allMovimientos.isEmpty()) {
                                Text(
                                    text = "No hay registros de movimientos para este insumo.",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            } else {
                                allMovimientos.take(10).forEachIndexed { idx, mov ->
                                    val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(mov.date))
                                    val isEntrada = mov.type == "ENTRADA" || mov.type == "INVENTARIO_INICIAL" || mov.type == "AJUSTE_POSITIVO"

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Slate50,
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isEntrada) Color(0xFFDCFCE7) else Color(0xFFFEF2F2)
                                                    ) {
                                                        Text(
                                                            text = mov.type,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isEntrada) Color(0xFF15803D) else Color(0xFFB91C1C),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = timeStr,
                                                        fontSize = 11.sp,
                                                        color = Slate500
                                                    )
                                                }

                                                if (mov.notes.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = mov.notes,
                                                        fontSize = 11.5.sp,
                                                        color = Slate600
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${if (isEntrada) "+" else "-"}${"%.2f".format(mov.quantity)} ${mov.unit}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isEntrada) Color(0xFF15803D) else Color(0xFFB91C1C)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón de Cerrar Grande y Cómodo
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_cerrar_detalle_insumo_modal")
                ) {
                    Text(
                        text = "CERRAR CONSULTA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}
