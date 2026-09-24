package com.example.ui.screens.dueno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.util.Locale
import kotlin.math.min

// Paleta de colores distinguibles de alto contraste para los gráficos
private val ChartPalette = listOf(
    Color(0xFF2563EB), // Azul Real
    Color(0xFF059669), // Esmeralda
    Color(0xFFD97706), // Ámbar / Oro
    Color(0xFF7C3AED), // Violeta
    Color(0xFFDC2626), // Rojo Coral
    Color(0xFF0891B2), // Cian Oscuro
    Color(0xFF64748B)  // Gris Pizarra (OTROS)
)

data class PieChartSlice(
    val name: String,
    val amount: Double,
    val unit: String,
    val quantity: Double,
    val percentage: Float,
    val color: Color
)

data class UsoInsumoItem(
    val insumo: MateriaPrima,
    val cantidadUsada: Double,
    val unit: String,
    val costoTotalUsado: Double,
    val percentageOfMax: Float
)

data class MovimientoInsumoItem(
    val insumo: MateriaPrima,
    val totalMovimiento: Double,
    val entradasQty: Double,
    val usosQty: Double,
    val unit: String,
    val percentageOfMax: Float
)

/**
 * Pantalla / Diálogo de Gráficos Analíticos de INFORME INSUMOS
 * Diseñado con tipografía grande, alto contraste y lectura clara para personas mayores.
 * Incluye:
 * 1. PARTICIPACIÓN POR IMPORTE (Gráfico circular con top 5 + OTROS)
 * 2. MAYOR USO (Gráfico de barras de consumo de la jornada)
 * 3. MAYOR MOVIMIENTO (Gráfico de barras de actividad total: entradas + usos)
 */
@Composable
fun InformeInsumosGraficosDialog(
    materiasPrimas: List<MateriaPrima>,
    movimientosMateria: List<MovimientoMateriaPrima>,
    activeJornada: Jornada?,
    isJornadaOpen: Boolean,
    onDismiss: () -> Unit
) {
    val activeInsumos = remember(materiasPrimas) { materiasPrimas.filter { it.isActive } }

    val movimientosJornada = remember(movimientosMateria, activeJornada, isJornadaOpen) {
        if (activeJornada != null) {
            val end = activeJornada.closedAt ?: Long.MAX_VALUE
            movimientosMateria.filter { it.date >= activeJornada.openedAt && it.date <= end }
        } else {
            movimientosMateria
        }
    }

    // 1. CÁLCULO DE PARTICIPACIÓN POR IMPORTE (Top 5 + OTROS)
    val (pieSlices, totalImporteGeneral) = remember(activeInsumos) {
        val listConImporte = activeInsumos.map { insumo ->
            val imp = insumo.stock * insumo.unitCost
            insumo to imp
        }.sortedByDescending { it.second }

        val totalImp = listConImporte.sumOf { it.second }
        if (totalImp <= 0.0) {
            emptyList<PieChartSlice>() to 0.0
        } else {
            val top5 = listConImporte.take(5)
            val others = listConImporte.drop(5)

            val slices = mutableListOf<PieChartSlice>()
            top5.forEachIndexed { index, pair ->
                val (insumo, imp) = pair
                val pct = ((imp / totalImp) * 100).toFloat()
                slices.add(
                    PieChartSlice(
                        name = insumo.name,
                        amount = imp,
                        unit = insumo.unit,
                        quantity = insumo.stock,
                        percentage = pct,
                        color = ChartPalette.getOrElse(index) { Color.Gray }
                    )
                )
            }

            val othersSum = others.sumOf { it.second }
            if (othersSum > 0.0) {
                val pct = ((othersSum / totalImp) * 100).toFloat()
                slices.add(
                    PieChartSlice(
                        name = "OTROS (${others.size} insumos)",
                        amount = othersSum,
                        unit = "varias",
                        quantity = 0.0,
                        percentage = pct,
                        color = ChartPalette.last()
                    )
                )
            }

            slices to totalImp
        }
    }

    // 2. CÁLCULO DE MAYOR USO DURANTE LA JORNADA
    val mayorUsoList = remember(activeInsumos, movimientosJornada) {
        val consumos = movimientosJornada.filter {
            it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA")
        }
        val consumosPorInsumo = consumos.groupBy { it.materiaPrimaId }

        val list = activeInsumos.mapNotNull { insumo ->
            val movs = consumosPorInsumo[insumo.id] ?: emptyList()
            val totalUsado = movs.sumOf { it.quantity }
            if (totalUsado > 0.0) {
                val costoTotalUsado = totalUsado * insumo.unitCost
                UsoInsumoItem(
                    insumo = insumo,
                    cantidadUsada = totalUsado,
                    unit = insumo.unit,
                    costoTotalUsado = costoTotalUsado,
                    percentageOfMax = 0f
                )
            } else null
        }.sortedByDescending { it.cantidadUsada }

        val maxUsado = list.maxOfOrNull { it.cantidadUsada } ?: 1.0
        list.map { it.copy(percentageOfMax = (it.cantidadUsada / maxUsado).toFloat()) }
    }

    // 3. CÁLCULO DE MAYOR MOVIMIENTO DURANTE LA JORNADA (Entradas + Usos)
    val mayorMovimientoList = remember(activeInsumos, movimientosJornada) {
        val list = activeInsumos.mapNotNull { insumo ->
            val movs = movimientosJornada.filter { it.materiaPrimaId == insumo.id }
            val entradas = movs.filter { it.type == "ENTRADA" }.sumOf { it.quantity }
            val usos = movs.filter { it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA") }.sumOf { it.quantity }
            val totalMov = entradas + usos

            if (totalMov > 0.0) {
                MovimientoInsumoItem(
                    insumo = insumo,
                    totalMovimiento = totalMov,
                    entradasQty = entradas,
                    usosQty = usos,
                    unit = insumo.unit,
                    percentageOfMax = 0f
                )
            } else null
        }.sortedByDescending { it.totalMovimiento }

        val maxMov = list.maxOfOrNull { it.totalMovimiento } ?: 1.0
        list.map { it.copy(percentageOfMax = (it.totalMovimiento / maxMov).toFloat()) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF8FAFC),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header con botón de cierre
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
                            shape = RoundedCornerShape(12.dp),
                            color = ElQadreNavy,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PieChart,
                                    contentDescription = null,
                                    tint = ElQadreGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "GRÁFICOS DE INSUMOS",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isJornadaOpen && activeJornada != null) "Jornada #${activeJornada.id} en curso" else "Resumen General de Existencias",
                                fontSize = 12.sp,
                                color = Slate600,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Slate100, CircleShape)
                            .testTag("btn_cerrar_graficos_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate700,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contenedor scrolleable para los 3 gráficos
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // ==========================================
                    // GRÁFICO A: PARTICIPACIÓN POR IMPORTE
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "A. PARTICIPACIÓN POR IMPORTE",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy,
                                        letterSpacing = 0.3.sp
                                    )
                                    Text(
                                        text = "Distribución del valor monetario de la existencia final",
                                        fontSize = 11.5.sp,
                                        color = Slate500
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Text(
                                        text = "TOTAL: $${"%.2f".format(totalImporteGeneral)} CUP",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            if (pieSlices.isEmpty() || totalImporteGeneral <= 0.0) {
                                Text(
                                    text = "No hay existencias con importe para graficar.",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            } else {
                                // Dibujo del Gráfico Circular con Canvas
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .size(170.dp)
                                            .padding(8.dp)
                                    ) {
                                        var currentStartAngle = -90f
                                        val strokeWidth = 34.dp.toPx()
                                        val radius = (min(size.width, size.height) - strokeWidth) / 2
                                        val center = Offset(size.width / 2, size.height / 2)
                                        val arcSize = Size(radius * 2, radius * 2)
                                        val topLeft = Offset(center.x - radius, center.y - radius)

                                        pieSlices.forEach { slice ->
                                            val sweepAngle = (slice.percentage / 100f) * 360f
                                            if (sweepAngle > 0f) {
                                                drawArc(
                                                    color = slice.color,
                                                    startAngle = currentStartAngle,
                                                    sweepAngle = sweepAngle,
                                                    useCenter = false,
                                                    topLeft = topLeft,
                                                    size = arcSize,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                                )
                                                currentStartAngle += sweepAngle
                                            }
                                        }
                                    }

                                    // Centro del dona con resumen
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "EXISTENCIA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate400
                                        )
                                        Text(
                                            text = "$${"%.0f".format(totalImporteGeneral)}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "CUP",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreGold
                                        )
                                    }
                                }

                                // Leyenda clara con números grandes y porcentajes
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pieSlices.forEach { slice ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = slice.color,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {}

                                                    Column {
                                                        Text(
                                                            text = slice.name.uppercase(Locale.getDefault()),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = ElQadreNavy
                                                        )
                                                        if (slice.quantity > 0.0) {
                                                            Text(
                                                                text = "${"%.2f".format(slice.quantity)} ${slice.unit}",
                                                                fontSize = 11.sp,
                                                                color = Slate500
                                                            )
                                                        }
                                                    }
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "$${"%.2f".format(slice.amount)} CUP",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFFB45309)
                                                    )
                                                    Text(
                                                        text = "${"%.1f".format(slice.percentage)}% del total",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = slice.color
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
                    // GRÁFICO B: MAYOR USO DURANTE LA JORNADA
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "B. MAYOR USO (CONSUMO EN JORNADA)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.3.sp
                                )
                                Text(
                                    text = "Insumos con mayor cantidad utilizada en producción y salidas hoy",
                                    fontSize = 11.5.sp,
                                    color = Slate500
                                )
                            }

                            HorizontalDivider(color = Slate200)

                            if (mayorUsoList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate50,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Aún no se registran consumos ni tandas en la jornada actual.",
                                        fontSize = 13.sp,
                                        color = Slate600,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    mayorUsoList.take(6).forEachIndexed { idx, item ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFFDC2626),
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "${idx + 1}",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = item.insumo.name.uppercase(Locale.getDefault()),
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy
                                                    )
                                                }

                                                Text(
                                                    text = "${"%.2f".format(item.cantidadUsada)} ${item.unit}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFFDC2626)
                                                )
                                            }

                                            // Barra de progreso visual
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(14.dp)
                                                    .background(Slate100, RoundedCornerShape(7.dp))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(item.percentageOfMax.coerceIn(0.05f, 1f))
                                                        .fillMaxHeight()
                                                        .background(Color(0xFFDC2626), RoundedCornerShape(7.dp))
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Costo del insumo consumido: $${"%.2f".format(item.costoTotalUsado)} CUP",
                                                    fontSize = 11.sp,
                                                    color = Slate600
                                                )
                                                Text(
                                                    text = "${"%.0f".format(item.percentageOfMax * 100)}% del pico",
                                                    fontSize = 11.sp,
                                                    color = Slate500,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (idx < mayorUsoList.take(6).size - 1) {
                                            HorizontalDivider(color = Slate100)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // GRÁFICO C: MAYOR MOVIMIENTO TOTAL (ENTRADAS + USOS)
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "C. MAYOR MOVIMIENTO EN JORNADA",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    letterSpacing = 0.3.sp
                                )
                                Text(
                                    text = "Insumos con más actividad total (Entradas/Compras + Usos/Consumos)",
                                    fontSize = 11.5.sp,
                                    color = Slate500
                                )
                            }

                            HorizontalDivider(color = Slate200)

                            if (mayorMovimientoList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate50,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No se han registrado movimientos de entradas ni consumos en esta jornada.",
                                        fontSize = 13.sp,
                                        color = Slate600,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    mayorMovimientoList.take(6).forEachIndexed { idx, item ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFF2563EB),
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "${idx + 1}",
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = item.insumo.name.uppercase(Locale.getDefault()),
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy
                                                    )
                                                }

                                                Text(
                                                    text = "Mov: ${"%.2f".format(item.totalMovimiento)} ${item.unit}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF2563EB)
                                                )
                                            }

                                            // Barra doble segmentada: Verde (Entradas) + Rojo (Usos)
                                            val entFraction = if (item.totalMovimiento > 0.0) (item.entradasQty / item.totalMovimiento).toFloat() else 0f
                                            val usoFraction = if (item.totalMovimiento > 0.0) (item.usosQty / item.totalMovimiento).toFloat() else 0f

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(14.dp)
                                                    .background(Slate100, RoundedCornerShape(7.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth(item.percentageOfMax.coerceIn(0.05f, 1f))
                                                        .fillMaxHeight()
                                                ) {
                                                    if (entFraction > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(entFraction)
                                                                .fillMaxHeight()
                                                                .background(Color(0xFF16A34A), RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                                                        )
                                                    }
                                                    if (usoFraction > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(usoFraction)
                                                                .fillMaxHeight()
                                                                .background(Color(0xFFDC2626), RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Entradas: +${"%.2f".format(item.entradasQty)} ${item.unit}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                                Text(
                                                    text = "Usos: -${"%.2f".format(item.usosQty)} ${item.unit}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFDC2626)
                                                )
                                            }
                                        }
                                        if (idx < mayorMovimientoList.take(6).size - 1) {
                                            HorizontalDivider(color = Slate100)
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
