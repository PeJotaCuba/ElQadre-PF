package com.example.ui.screens.dueno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.local.model.Jornada
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.CuadreCajaArchiveManager
import com.example.util.CuadreCajaArchiveManager.JornadaCuadreCajaArchive
import com.example.util.CuadreCajaPdfExporter
import com.example.util.DependientePagoDistribucion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de ARCHIVO HISTÓRICO de CUADRO DE CAJA
 * Permite explorar jornadas archivadas y consultar su Cuadro de Caja completo congelado.
 */
private fun generatePdfForArchive(
    context: android.content.Context,
    archive: JornadaCuadreCajaArchive
): File? {
    val pdfRowsProd = archive.produccionItems.map { p ->
        CuadreCajaPdfExporter.ProduccionPdfRow(
            productName = p.productName,
            tandasCount = p.tandasCount,
            totalProduced = p.totalProduced,
            unit = p.unit,
            defectuoso = p.defectuoso,
            consumo = p.consumo,
            regalia = p.regalia,
            pendientes = p.pendientes,
            vendible = p.vendible,
            price = p.price,
            ingresoEstimado = p.ingresoEstimado
        )
    }
    val pdfRowsMerc = archive.mercaderiaItems.map { m ->
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
    }
    val pdfRowsAg = archive.agregadoItems.map { ag ->
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
    val cocinaRows = archive.cocinaPagoRows.map { c ->
        CuadreCajaPdfExporter.CocinaPagoPdfRow(
            productName = c.productName,
            vendible = c.vendible,
            unit = c.unit,
            pagoUnitario = c.pagoUnitario,
            cantidadCocineros = c.cantidadCocineros,
            totalPago = c.totalPago
        )
    }
    val cajeroInfo = archive.cajeroPagoInfo?.let {
        CuadreCajaPdfExporter.CajeroPagoPdfInfo(
            pagoProduccion = it.pagoProduccion,
            pagoMercaderias = it.pagoMercaderias,
            totalPago = it.totalPago
        )
    }
    val depRows = archive.dependientesRows.map { d ->
        DependientePagoDistribucion(
            id = d.id,
            name = d.name,
            ventasProduccion = d.ventasProduccion,
            ventasBebidas = d.ventasBebidas,
            ventasTotales = d.ventasTotales,
            montoPago = d.montoPago
        )
    }

    val reportData = CuadreCajaPdfExporter.CuadreCajaReportData(
        businessName = archive.businessName,
        duenoName = archive.closedBy,
        jornadaId = archive.jornadaId,
        openedAt = archive.openedAt,
        closedAt = archive.closedAt,
        initialCash = archive.initialCash,
        ingresosProduccion = archive.ingresosProduccion,
        ingresosMercaderias = archive.ingresosMercaderias,
        ingresosAgregados = archive.ingresosAgregados,
        totalIngresos = archive.totalIngresos,
        mermasTotalValor = archive.mermasTotalValor,
        transferenciasMonto = archive.transferenciasMonto,
        transferenciasCount = archive.transferenciasCount,
        extracciones = archive.extracciones,
        extraccionesNotas = archive.extraccionesNotas,
        efectivoEsperado = archive.efectivoEsperado,
        efectivoReal = archive.efectivoReal,
        diferencia = archive.diferencia,
        costoProduccion = archive.costoProduccion,
        costoMercaderias = archive.costoMercaderias,
        costoAgregados = archive.costoAgregados,
        costoTotal = archive.costoTotal,
        utilidadTeorica = archive.utilidadTeorica,
        pagosConfirmados = archive.pagosConfirmados,
        totalPagosPersonal = archive.totalPagosPersonal,
        totalPagoCocina = archive.totalPagoCocina,
        totalPagoCajero = archive.totalPagoCajero,
        totalPagoDependientes = archive.totalPagoDependientes,
        cocinaPagoRows = cocinaRows,
        cajeroPagoInfo = cajeroInfo,
        dependientesRows = depRows,
        dineroFinalEnCaja = archive.dineroFinalEnCaja,
        notas = archive.notas,
        produccionRows = pdfRowsProd,
        mercaderiaRows = pdfRowsMerc,
        agregadoRows = pdfRowsAg
    )
    return CuadreCajaPdfExporter.exportCuadreCajaReport(context, reportData)
}

@Composable
fun CuadreCajaArchivoScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedArchive by remember { mutableStateOf<JornadaCuadreCajaArchive?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var lastGeneratedPdfFile by remember { mutableStateOf<File?>(null) }

    // Obtener todas las jornadas cerradas ordenadas de más reciente a más antigua
    val closedJornadas = remember(uiState.allJornadas) {
        uiState.allJornadas.filter { !it.isOpen }.sortedByDescending { it.id }
    }

    if (selectedArchive != null) {
        val archive = selectedArchive!!
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fechaApertura = dateFormat.format(Date(archive.openedAt))
        val fechaCierre = dateFormat.format(Date(archive.closedAt))

        // ==========================================
        // VISTA DE DETALLE: CUADRO DE CAJA CONGELADO
        // ==========================================
        Scaffold(
            topBar = {
                Surface(
                    color = ElQadreNavy,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                IconButton(
                                    onClick = { selectedArchive = null },
                                    modifier = Modifier.size(36.dp).testTag("btn_volver_lista_archivo_cuadre")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver",
                                        tint = Color.White
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "JORNADA #${archive.jornadaId}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 17.sp,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1E293B)
                                        ) {
                                            Text(
                                                text = "CONGELADO",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ElQadreGold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$fechaApertura — $fechaCierre",
                                        fontSize = 11.sp,
                                        color = Slate300
                                    )
                                }
                            }

                            // Botones PDF Rápidos en Header
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val file = generatePdfForArchive(context, archive)
                                        if (file != null) {
                                            lastGeneratedPdfFile = file
                                            CuadreCajaPdfExporter.viewPdfReport(context, file)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("btn_pdf_generar_header")
                                ) {
                                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontWeight = FontWeight.Black, fontSize = 11.5.sp, color = ElQadreNavy)
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(ElQadreBackground)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 0. TARJETA ACCIONES DE PDF DE LA JORNADA
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    tint = ElQadreGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "INFORME COMPLETO DE CUADRO DE CAJA (PDF)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.5.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Informe congelado: Productos, Ventas, Ingresos, Costos, Extracciones, Mermas, Finales, Pagos y Utilidades.",
                                        fontSize = 11.sp,
                                        color = Slate300
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. GENERAR / VER PDF
                                Button(
                                    onClick = {
                                        val file = generatePdfForArchive(context, archive)
                                        if (file != null) {
                                            lastGeneratedPdfFile = file
                                            CuadreCajaPdfExporter.viewPdfReport(context, file)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("btn_generar_pdf_archivo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "GENERAR",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = ElQadreNavy
                                    )
                                }

                                // 2. DESCARGAR / GUARDAR PDF
                                Button(
                                    onClick = {
                                        val sourceFile = lastGeneratedPdfFile ?: generatePdfForArchive(context, archive)
                                        if (sourceFile != null) {
                                            lastGeneratedPdfFile = sourceFile
                                            CuadreCajaPdfExporter.savePdfToDownloads(context, sourceFile)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("btn_guardar_pdf_archivo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "GUARDAR",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }

                                // 3. ENVIAR / COMPARTIR PDF
                                Button(
                                    onClick = {
                                        val file = lastGeneratedPdfFile ?: generatePdfForArchive(context, archive)
                                        if (file != null) {
                                            lastGeneratedPdfFile = file
                                            CuadreCajaPdfExporter.shareCuadreCajaReport(context, file)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("btn_compartir_pdf_archivo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "COMPARTIR",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                // 1. TARJETA RESUMEN DE CUADRE
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESUMEN DEL CUADRE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = ElQadreNavy
                                )
                                val diffState = when {
                                    archive.diferencia == 0.0 -> "CUADRA EXACTO" to Color(0xFF15803D)
                                    archive.diferencia > 0.0 -> "SOBRANTE" to Color(0xFF1D4ED8)
                                    else -> "FALTANTE" to Color(0xFFDC2626)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = diffState.second.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, diffState.second.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${diffState.first} (${if (archive.diferencia > 0) "+" else ""}${"%.2f".format(archive.diferencia)} CUP)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = diffState.second,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Slate100)

                            // Matriz de 4 indicadores
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ResumenMiniCard(
                                    titulo = "EFECTIVO INICIAL",
                                    valor = "$${"%.2f".format(archive.initialCash)}",
                                    color = Slate700,
                                    modifier = Modifier.weight(1f)
                                )
                                ResumenMiniCard(
                                    titulo = "TOTAL INGRESOS",
                                    valor = "$${"%.2f".format(archive.totalIngresos)}",
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ResumenMiniCard(
                                    titulo = "EFECTIVO ESPERADO",
                                    valor = "$${"%.2f".format(archive.efectivoEsperado)}",
                                    color = ElQadreNavy,
                                    modifier = Modifier.weight(1f)
                                )
                                ResumenMiniCard(
                                    titulo = "EFECTIVO REAL",
                                    valor = "$${"%.2f".format(archive.efectivoReal)}",
                                    color = ElQadreNavy,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (archive.transferenciasMonto > 0.0 || archive.extracciones > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (archive.transferenciasMonto > 0.0) {
                                        ResumenMiniCard(
                                            titulo = "TRANSFERENCIAS",
                                            valor = "$${"%.2f".format(archive.transferenciasMonto)}",
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (archive.extracciones > 0.0) {
                                        ResumenMiniCard(
                                            titulo = "EXTRACCIONES",
                                            valor = "-$${"%.2f".format(archive.extracciones)}",
                                            color = Color(0xFFDC2626),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            if (archive.pagosConfirmados) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Pagos de Personal:", fontSize = 12.sp, color = Slate600)
                                            Text("-$${"%.2f".format(archive.totalPagosPersonal)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Dinero Final en Caja:", fontSize = 12.sp, color = Slate600)
                                            Text("$${"%.2f".format(archive.dineroFinalEnCaja)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                        }
                                    }
                                }
                            }

                            if (archive.notas.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Notas del Cierre:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                        Text(archive.notas, fontSize = 12.sp, color = Color(0xFF78350F))
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. SECCIÓN: PRODUCCIÓN CONGELADA
                item {
                    SectionHeader(
                        title = "PRODUCCIÓN (${archive.produccionItems.size} PRODUCTOS)",
                        subtotal = "$${"%.2f".format(archive.ingresosProduccion)} CUP",
                        icon = Icons.Outlined.SoupKitchen
                    )
                }

                if (archive.produccionItems.isEmpty()) {
                    item {
                        EmptyCardMessage(msg = "No se registraron productos de producción en esta jornada.")
                    }
                } else {
                    items(archive.produccionItems) { prod ->
                        ArchivedProduccionCard(prod)
                    }
                }

                // 3. SECCIÓN: MERCADERÍAS CONGELADAS
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeader(
                        title = "MERCADERÍAS (${archive.mercaderiaItems.size} PRODUCTOS)",
                        subtotal = "$${"%.2f".format(archive.ingresosMercaderias)} CUP",
                        icon = Icons.Outlined.Inventory2
                    )
                }

                if (archive.mercaderiaItems.isEmpty()) {
                    item {
                        EmptyCardMessage(msg = "No se registraron mercaderías en esta jornada.")
                    }
                } else {
                    items(archive.mercaderiaItems) { merc ->
                        ArchivedMercaderiaCard(merc)
                    }
                }

                // 4. SECCIÓN: AGREGADOS CONGELADOS (si existen)
                if (archive.agregadoItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        SectionHeader(
                            title = "AGREGADOS (${archive.agregadoItems.size})",
                            subtotal = "$${"%.2f".format(archive.ingresosAgregados)} CUP",
                            icon = Icons.Outlined.Fastfood
                        )
                    }
                    items(archive.agregadoItems) { ag ->
                        ArchivedAgregadoCard(ag)
                    }
                }

                // 5. SECCIÓN: PAGOS DE PERSONAL CONGELADOS (si hubo)
                if (archive.pagosConfirmados || archive.cocinaPagoRows.isNotEmpty() || archive.dependientesRows.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        SectionHeader(
                            title = "PAGOS DE PERSONAL",
                            subtotal = "$${"%.2f".format(archive.totalPagosPersonal)} CUP",
                            icon = Icons.Outlined.Payments
                        )
                    }
                    item {
                        ArchivedPagosCard(archive)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
        return
    }

    // ====================================================
    // VISTA PRINCIPAL: LISTA DE JORNADAS DE CUADRO DE CAJA
    // ====================================================
    Scaffold(
        topBar = {
            Surface(
                color = ElQadreNavy,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
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
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp).testTag("btn_volver_cuadre_desde_archivo")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "ARCHIVO DE CUADRE DE CAJA",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Histórico de jornadas cerradas y congeladas",
                                    fontSize = 11.sp,
                                    color = Slate300
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${closedJornadas.size} JORNADAS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buscador de jornadas
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por número de jornada...", fontSize = 13.sp, color = Slate400) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Slate400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = Slate300
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("input_buscar_archivo_cuadre")
                    )
                }
            }
        }
    ) { paddingValues ->
        val filteredJornadas = remember(closedJornadas, searchQuery) {
            if (searchQuery.isBlank()) closedJornadas
            else {
                val q = searchQuery.trim().lowercase()
                closedJornadas.filter {
                    it.id.toString().contains(q) || (it.notes.lowercase().contains(q))
                }
            }
        }

        if (filteredJornadas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(ElQadreBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No hay jornadas archivadas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Slate700
                    )
                    Text(
                        text = "Al cerrar una jornada, su Cuadro de Caja completo quedará archivado y congelado aquí automáticamente.",
                        fontSize = 13.sp,
                        color = Slate500,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(ElQadreBackground)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredJornadas) { jornada ->
                    ArchivedJornadaSummaryCard(
                        jornada = jornada,
                        uiState = uiState,
                        onClick = {
                            val arch = CuadreCajaArchiveManager.getOrBuildArchive(
                                context = context,
                                jornada = jornada,
                                allTandas = uiState.tandas,
                                allMovimientosMercaderia = uiState.movimientosMercaderia,
                                allProducts = uiState.products,
                                allMercaderias = uiState.mercaderias,
                                allTransferencias = uiState.allTransferencias,
                                businessName = uiState.businessConfig?.nombreNegocio ?: "EL QADRE"
                            )
                            selectedArchive = arch
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ArchivedJornadaSummaryCard(
    jornada: Jornada,
    uiState: MainUiState,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val archive = remember(jornada.id) {
        CuadreCajaArchiveManager.getArchive(context, jornada.id) ?: CuadreCajaArchiveManager.buildArchiveFromHistoricalData(
            jornada = jornada,
            allTandas = uiState.tandas,
            allMovimientosMercaderia = uiState.movimientosMercaderia,
            allProducts = uiState.products,
            allMercaderias = uiState.mercaderias,
            allTransferencias = uiState.allTransferencias,
            businessName = uiState.businessConfig?.nombreNegocio ?: "EL QADRE"
        )
    }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val fechaApertura = dateFormat.format(Date(archive.openedAt))
    val fechaCierre = dateFormat.format(Date(archive.closedAt))

    val diffState = when {
        archive.diferencia == 0.0 -> "CUADRA EXACTO" to Color(0xFF15803D)
        archive.diferencia > 0.0 -> "SOBRANTE (+${"%.2f".format(archive.diferencia)})" to Color(0xFF1D4ED8)
        else -> "FALTANTE (${"%.2f".format(archive.diferencia)})" to Color(0xFFDC2626)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("card_archivo_jornada_${jornada.id}")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreNavy
                    ) {
                        Text(
                            text = "JORNADA #${jornada.id}",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = fechaApertura,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = diffState.second.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, diffState.second.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = diffState.first,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = diffState.second,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate100)

            // Fila de Datos Clave
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOTAL INGRESOS", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("$${"%.2f".format(archive.totalIngresos)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EFECTIVO REAL", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("$${"%.2f".format(archive.efectivoReal)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("PRODUCTOS", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${archive.produccionItems.size + archive.mercaderiaItems.size} ítems", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
            }

            // Pie con llamado a consultar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cerrado por: ${archive.closedBy.ifBlank { "DUEÑO" }}",
                    fontSize = 11.sp,
                    color = Slate400
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ver Cuadro Completo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumenMiniCard(
    titulo: String,
    valor: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Slate200),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = titulo,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                letterSpacing = 0.5.sp
            )
            Text(
                text = valor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtotal: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
            Icon(icon, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy,
                letterSpacing = 0.5.sp
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFF0FDF4),
            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
        ) {
            Text(
                text = subtotal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF15803D),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun EmptyCardMessage(msg: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = msg,
            fontSize = 12.5.sp,
            color = Slate500,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ArchivedProduccionCard(prod: CuadreCajaArchiveManager.ArchivedProduccionItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = prod.productName,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = ElQadreNavy
                )
                Text(
                    text = "$${"%.2f".format(prod.ingresoEstimado)} CUP",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color(0xFF15803D)
                )
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CANTIDAD", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(prod.totalProduced)} ${prod.unit}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                }
                Column {
                    Text("PRECIO", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("$${"%.2f".format(prod.price)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VENDIDAS", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(prod.vendible)} ${prod.unit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
            }

            if (prod.pendientes > 0.0 || prod.defectuoso > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (prod.pendientes > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFF7ED),
                            border = BorderStroke(1.dp, Color(0xFFFED7AA))
                        ) {
                            Text(
                                text = "Pendientes: ${"%.1f".format(prod.pendientes)} ${prod.unit}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (prod.defectuoso > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Text(
                                text = "Merma: ${"%.1f".format(prod.defectuoso)} ${prod.unit}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedMercaderiaCard(merc: CuadreCajaArchiveManager.ArchivedMercaderiaItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = merc.productName,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = ElQadreNavy
                )
                Text(
                    text = "$${"%.2f".format(merc.ingresoEstimado)} CUP",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color(0xFF15803D)
                )
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("INICIO", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(merc.existenciaInicial)} ${merc.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
                if (merc.entradas > 0.0) {
                    Column {
                        Text("ENTRADAS", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                        Text("+${"%.1f".format(merc.entradas)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                    }
                }
                Column {
                    Text("FINAL", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(merc.existenciaFinal)} ${merc.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VENDIDAS", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(merc.ventas)} ${merc.unit}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                }
            }
        }
    }
}

@Composable
private fun ArchivedAgregadoCard(ag: CuadreCajaArchiveManager.ArchivedAgregadoItem) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ag.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = ElQadreNavy)
                Text("$${"%.2f".format(ag.ingresoEstimado)} CUP", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF15803D))
            }
            HorizontalDivider(color = Slate100)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enviadas: ${"%.0f".format(ag.racionesEnviadas)}", fontSize = 12.sp, color = Slate600)
                Text("Vendidas: ${"%.0f".format(ag.racionesVendidas)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Text("Sobrantes: ${"%.0f".format(ag.racionesSobrantes)}", fontSize = 12.sp, color = Slate600)
            }
        }
    }
}

@Composable
private fun ArchivedPagosCard(archive: JornadaCuadreCajaArchive) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text("TOTAL PAGOS PERSONAL", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreNavy)
                Text("-$${"%.2f".format(archive.totalPagosPersonal)} CUP", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFDC2626))
            }

            HorizontalDivider(color = Slate100)

            if (archive.totalPagoCocina > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("• Cocina / Producción:", fontSize = 13.sp, color = Slate700)
                    Text("$${"%.2f".format(archive.totalPagoCocina)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
            }

            if (archive.totalPagoCajero > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("• Cajero:", fontSize = 13.sp, color = Slate700)
                    Text("$${"%.2f".format(archive.totalPagoCajero)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
            }

            if (archive.totalPagoDependientes > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("• Dependientes (${archive.dependientesRows.size}):", fontSize = 13.sp, color = Slate700)
                    Text("$${"%.2f".format(archive.totalPagoDependientes)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
                archive.dependientesRows.forEach { dep ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("- ${dep.name}:", fontSize = 12.sp, color = Slate600)
                        Text("$${"%.2f".format(dep.montoPago)} CUP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dinero Final en Caja:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    Text("$${"%.2f".format(archive.dineroFinalEnCaja)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                }
            }
        }
    }
}
