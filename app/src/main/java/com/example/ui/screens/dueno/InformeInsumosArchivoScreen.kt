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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Jornada
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.MovimientoMateriaPrima
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.InformeInsumosArchiveManager
import com.example.util.InformeInsumosArchiveManager.InsumoArchiveItem
import com.example.util.InformeInsumosArchiveManager.JornadaInsumosArchive
import com.example.util.InformeInsumosPdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de ARCHIVO HISTÓRICO de INFORME INSUMOS
 * Permite explorar jornadas cerradas y acceder a sus informes congelados con:
 * - INICIO | ENTRADAS | FINAL | IMPORTE
 * - Desglose de precios y costos
 * - Gráficos congelados de la jornada
 * - Descarga de PDF oficial de la jornada archivada
 */
@Composable
fun InformeInsumosArchivoScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedArchive by remember { mutableStateOf<JornadaInsumosArchive?>(null) }
    var selectedInsumoDetail by remember { mutableStateOf<InsumoArchiveItem?>(null) }
    var showHistoricalGraficos by remember { mutableStateOf(false) }
    var showPdfSuccessDialog by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Obtener todas las jornadas cerradas ordenadas de más reciente a más antigua
    val closedJornadas = remember(uiState.allJornadas) {
        uiState.allJornadas.filter { !it.isOpen }.sortedByDescending { it.id }
    }

    if (selectedArchive != null) {
        val archive = selectedArchive!!
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fechaApertura = dateFormat.format(Date(archive.openedAt))
        val fechaCierre = dateFormat.format(Date(archive.closedAt))

        val filteredItems = remember(archive.items, searchQuery) {
            if (searchQuery.isBlank()) {
                archive.items
            } else {
                val q = searchQuery.trim().lowercase()
                archive.items.filter { it.name.lowercase().contains(q) }
            }
        }

        // ==========================================
        // VISTA DE DETALLE DE LA JORNADA ARCHIVADA
        // ==========================================
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
                                    onClick = { selectedArchive = null },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Slate100, RoundedCornerShape(12.dp))
                                        .testTag("btn_volver_lista_archivo")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver al Archivo",
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "JORNADA #${archive.jornadaId}",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy,
                                            letterSpacing = 0.5.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate200
                                        ) {
                                            Text(
                                                text = "HISTÓRICO CONGELADO",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Slate700,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Cierre: $fechaCierre • Por: ${archive.closedBy}",
                                        fontSize = 12.sp,
                                        color = Slate600,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Barra de búsqueda dentro de la jornada archivada
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Buscar insumo en esta jornada...",
                                    fontSize = 14.sp,
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
                                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Slate500)
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
                                .testTag("input_buscar_insumo_archivo")
                        )

                        // Botones de acción del informe histórico: PDF y GRÁFICOS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. DESCARGAR PDF HISTÓRICO
                            Surface(
                                onClick = {
                                    val simulatedMaterias = archive.items.map { itm ->
                                        MateriaPrima(
                                            id = itm.materiaPrimaId,
                                            name = itm.name,
                                            unit = itm.unit,
                                            stock = itm.finalStock,
                                            initialStock = itm.initialStock,
                                            unitCost = itm.unitCost,
                                            isActive = true
                                        )
                                    }
                                    val simulatedMovimientos = archive.items.flatMap { itm ->
                                        val mList = mutableListOf<MovimientoMateriaPrima>()
                                        itm.entradasDetalles.forEach { ed ->
                                            mList.add(
                                                MovimientoMateriaPrima(
                                                    materiaPrimaId = itm.materiaPrimaId,
                                                    materiaPrimaName = itm.name,
                                                    type = ed.type,
                                                    quantity = ed.quantity,
                                                    unit = itm.unit,
                                                    date = ed.date,
                                                    responsibleUser = archive.closedBy,
                                                    notes = ed.notes
                                                )
                                            )
                                        }
                                        itm.consumosDetalles.forEach { cd ->
                                            mList.add(
                                                MovimientoMateriaPrima(
                                                    materiaPrimaId = itm.materiaPrimaId,
                                                    materiaPrimaName = itm.name,
                                                    type = cd.type,
                                                    quantity = cd.quantity,
                                                    unit = itm.unit,
                                                    date = cd.date,
                                                    responsibleUser = archive.closedBy,
                                                    notes = cd.notes
                                                )
                                            )
                                        }
                                        mList
                                    }
                                    val fakeJornada = Jornada(
                                        id = archive.jornadaId,
                                        openedAt = archive.openedAt,
                                        closedAt = archive.closedAt,
                                        isOpen = false,
                                        closedBy = archive.closedBy
                                    )

                                    val file = InformeInsumosPdfExporter.generatePdf(
                                        context = context,
                                        uiStateMaterias = simulatedMaterias,
                                        movimientosMateria = simulatedMovimientos,
                                        activeJornada = fakeJornada,
                                        businessName = archive.businessName,
                                        duenoName = archive.closedBy
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
                                    .testTag("btn_descargar_pdf_archivo")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PDF HISTÓRICO",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }

                            // 2. VER GRÁFICOS HISTÓRICOS
                            Surface(
                                onClick = { showHistoricalGraficos = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_graficos_archivo")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PieChart,
                                        contentDescription = null,
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "VER GRÁFICOS",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF92400E)
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
                // Tarjeta de Resumen General de la Jornada Archivada
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = "VALORACIÓN AL CIERRE DE JORNADA",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGold,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "JORNADA #${archive.jornadaId}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "$${"%.2f".format(archive.totalImporte)} CUP",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("TOTAL INSUMOS", fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                    Text("${archive.items.size}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                                Column {
                                    Text("ENTRADAS REGISTRADAS", fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                    Text("${archive.totalEntradasCount}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF86EFAC))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("APERTURA", fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(archive.openedAt)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate300)
                                }
                            }
                        }
                    }
                }

                // Lista de Insumos de la Jornada Archivada
                items(filteredItems, key = { it.materiaPrimaId }) { item ->
                    ArchivedInsumoCard(
                        item = item,
                        onClick = { selectedInsumoDetail = item }
                    )
                }
            }
        }

        // Diálogo de detalle de insumo congelado
        selectedInsumoDetail?.let { itm ->
            ArchivedInsumoDetailDialog(
                item = itm,
                jornadaId = archive.jornadaId,
                onDismiss = { selectedInsumoDetail = null }
            )
        }

        // Gráficos Históricos de la Jornada Archivada
        if (showHistoricalGraficos) {
            val simulatedMaterias = remember(archive.items) {
                archive.items.map { itm ->
                    MateriaPrima(
                        id = itm.materiaPrimaId,
                        name = itm.name,
                        unit = itm.unit,
                        stock = itm.finalStock,
                        initialStock = itm.initialStock,
                        unitCost = itm.unitCost,
                        isActive = true
                    )
                }
            }
            val simulatedMovimientos = remember(archive.items) {
                archive.items.flatMap { itm ->
                    val list = mutableListOf<MovimientoMateriaPrima>()
                    itm.entradasDetalles.forEach { ed ->
                        list.add(
                            MovimientoMateriaPrima(
                                materiaPrimaId = itm.materiaPrimaId,
                                materiaPrimaName = itm.name,
                                type = ed.type,
                                quantity = ed.quantity,
                                unit = itm.unit,
                                date = ed.date,
                                responsibleUser = archive.closedBy,
                                notes = ed.notes
                            )
                        )
                    }
                    itm.consumosDetalles.forEach { cd ->
                        list.add(
                            MovimientoMateriaPrima(
                                materiaPrimaId = itm.materiaPrimaId,
                                materiaPrimaName = itm.name,
                                type = cd.type,
                                quantity = cd.quantity,
                                unit = itm.unit,
                                date = cd.date,
                                responsibleUser = archive.closedBy,
                                notes = cd.notes
                            )
                        )
                    }
                    list
                }
            }
            val fakeJornada = remember(archive) {
                Jornada(
                    id = archive.jornadaId,
                    openedAt = archive.openedAt,
                    closedAt = archive.closedAt,
                    isOpen = false,
                    closedBy = archive.closedBy
                )
            }

            InformeInsumosGraficosDialog(
                materiasPrimas = simulatedMaterias,
                movimientosMateria = simulatedMovimientos,
                activeJornada = fakeJornada,
                isJornadaOpen = false,
                onDismiss = { showHistoricalGraficos = false }
            )
        }

    } else {
        // ==========================================
        // VISTA PRINCIPAL DEL ARCHIVO: LISTA DE JORNADAS
        // ==========================================
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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .testTag("btn_volver_archivo_a_informe")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver a Informe Insumos",
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "ARCHIVO HISTÓRICO",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Repositorio Inmutable de Jornadas Cerradas",
                                        fontSize = 12.sp,
                                        color = Slate600,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate100,
                                border = BorderStroke(1.dp, Slate300)
                            ) {
                                Text(
                                    text = "${closedJornadas.size} CERRADAS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF1F5F9),
            modifier = modifier
        ) { innerPadding ->
            if (closedJornadas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Slate100,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderSpecial,
                                        contentDescription = null,
                                        tint = Slate600,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Sin Jornadas Archivadas",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Al cerrar una jornada activa, el sistema congelará y guardará automáticamente su informe completo de insumos y gráficos en este repositorio.",
                                fontSize = 13.5.sp,
                                color = Slate600,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Seleccione una jornada para consultar su informe con datos e importes exactamente congelados.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1E40AF),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    items(closedJornadas, key = { it.id }) { j ->
                        val archiveData = remember(j.id) {
                            InformeInsumosArchiveManager.getOrBuildArchive(
                                context = context,
                                jornada = j,
                                allMaterias = uiState.materiasPrimas,
                                allMovimientos = uiState.movimientosMateriaPrima,
                                businessName = uiState.businessName
                            )
                        }

                        JornadaArchiveCard(
                            jornada = j,
                            archive = archiveData,
                            onClick = { selectedArchive = archiveData }
                        )
                    }
                }
            }
        }
    }

    // Modal de Confirmación y Descarga de PDF
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
                            text = "PDF HISTÓRICO GENERADO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Informe Oficial Congelado de la Jornada #${selectedArchive?.jornadaId ?: 0}",
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Botones de acción principales
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
                                .testTag("btn_compartir_pdf_archivo_modal")
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
                                .testTag("btn_guardar_pdf_archivo_modal")
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
 * Tarjeta de cada jornada cerrada en la lista de ARCHIVO
 */
@Composable
private fun JornadaArchiveCard(
    jornada: Jornada,
    archive: JornadaInsumosArchive,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val fechaCierre = dateFormat.format(Date(archive.closedAt))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("card_jornada_archivo_${jornada.id}")
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
                        color = ElQadreNavy,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#${jornada.id}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreGold
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "JORNADA #${jornada.id}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Cierre: $fechaCierre",
                            fontSize = 11.5.sp,
                            color = Slate500
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Text(
                        text = "$${"%.2f".format(archive.totalImporte)} CUP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFB45309),
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Insumos: ${archive.items.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Text(
                        text = "Entradas: +${archive.totalEntradasCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "VER INFORME",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta individual de insumo congelado en una jornada archivada
 */
@Composable
private fun ArchivedInsumoCard(
    item: InsumoArchiveItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, if (item.finalStock <= 0.0) Color(0xFFFCA5A5) else Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("card_insumo_archivo_${item.materiaPrimaId}")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name.uppercase(Locale.getDefault()),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "Costo Unitario: $${"%.2f".format(item.unitCost)} CUP por ${item.unit}",
                        fontSize = 12.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Text(
                        text = "$${"%.2f".format(item.importe)} CUP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate100)

            // 4 Indicadores Fundamentales: INICIO | ENTRADAS | FINAL | IMPORTE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. INICIO
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("INICIO", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            text = "${"%.2f".format(item.initialStock)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate800
                        )
                    }
                }

                // 2. ENTRADAS
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (item.entradasQty > 0) Color(0xFFDCFCE7) else Slate50,
                    border = BorderStroke(1.dp, if (item.entradasQty > 0) Color(0xFF86EFAC) else Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ENTRADAS", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (item.entradasQty > 0) Color(0xFF15803D) else Slate500)
                        Text(
                            text = if (item.entradasQty > 0) "+${"%.2f".format(item.entradasQty)}" else "0.00",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (item.entradasQty > 0) Color(0xFF15803D) else Slate400
                        )
                    }
                }

                // 3. FINAL
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (item.finalStock <= 0.0) Color(0xFFFEE2E2) else Color(0xFFCCFBF1),
                    border = BorderStroke(1.dp, if (item.finalStock <= 0.0) Color(0xFFFCA5A5) else Color(0xFF5EEAD4)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("FINAL", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (item.finalStock <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E))
                        Text(
                            text = "${"%.2f".format(item.finalStock)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (item.finalStock <= 0.0) Color(0xFFDC2626) else Color(0xFF0F766E)
                        )
                    }
                }

                // 4. IMPORTE
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("IMPORTE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        Text(
                            text = "$${"%.1f".format(item.importe)}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            if (item.priceBreakdown.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Entradas con precios registrados en esta jornada:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        item.priceBreakdown.forEach { pTxt ->
                            Text("• $pTxt", fontSize = 11.sp, color = Color(0xFF166534))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Detalle Individual de un Insumo Archivada
 */
@Composable
private fun ArchivedInsumoDetailDialog(
    item: InsumoArchiveItem,
    jornadaId: Long,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF8FAFC),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.name.uppercase(Locale.getDefault()),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Histórico Congelado • Jornada #$jornadaId",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Slate100, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate700)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 4 Indicadores
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("SITUACIÓN CONGELADA DE LA JORNADA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("INICIO", fontSize = 10.sp, color = Slate500)
                                    Text("${"%.2f".format(item.initialStock)} ${item.unit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Slate800)
                                }
                                Column {
                                    Text("ENTRADAS", fontSize = 10.sp, color = Color(0xFF15803D))
                                    Text("+${"%.2f".format(item.entradasQty)} ${item.unit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                }
                                Column {
                                    Text("FINAL", fontSize = 10.sp, color = Color(0xFF0F766E))
                                    Text("${"%.2f".format(item.finalStock)} ${item.unit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("IMPORTE", fontSize = 10.sp, color = Color(0xFFB45309))
                                    Text("$${"%.2f".format(item.importe)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }

                    // Entradas
                    if (item.entradasDetalles.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ENTRADAS REGISTRADAS EN LA JORNADA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                item.entradasDetalles.forEach { e ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = Slate50, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("+${"%.2f".format(e.quantity)} ${item.unit}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                                if (e.notes.isNotBlank()) Text(e.notes, fontSize = 11.sp, color = Slate600)
                                            }
                                            Text(dateFormat.format(Date(e.date)), fontSize = 10.sp, color = Slate400)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Consumos
                    if (item.consumosDetalles.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("USOS Y CONSUMOS EN LA JORNADA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                item.consumosDetalles.forEach { c ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = Slate50, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("-${"%.2f".format(c.quantity)} ${item.unit} (${c.type})", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                                                if (c.notes.isNotBlank()) Text(c.notes, fontSize = 11.sp, color = Slate600)
                                            }
                                            Text(dateFormat.format(Date(c.date)), fontSize = 10.sp, color = Slate400)
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
