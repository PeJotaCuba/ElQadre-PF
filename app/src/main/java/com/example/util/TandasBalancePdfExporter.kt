package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.Jornada
import com.example.data.local.model.Tanda
import com.example.ui.screens.admin.ConsolidatedIngredientItem
import com.example.ui.screens.admin.calculateConsolidatedIngredients
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador de PDF oficial para BALANCE GENERAL DE TANDAS CERRADAS
 * Exporta fielmente:
 * - INFORMACIÓN GENERAL (jornada, período/fecha, cantidad total producida, presentaciones especiales,
 *   unidades equivalentes, rendimiento general, ingrediente base consumido).
 * - CONSUMO GENERAL CONSOLIDADO DE INGREDIENTES (Harina en lb, sin duplicados, acumulando todas las tandas).
 * - GRÁFICO DE PASTEL (participación proporcional de cada ingrediente).
 * - GRÁFICO DE BARRAS (comparativa visual del consumo de insumos).
 * - Descarga, guardado en almacenamiento y compartir directamente por Android / WhatsApp.
 * - Sin alterar cálculos históricos registrados.
 */
object TandasBalancePdfExporter {

    private val CHART_PALETTE = intArrayOf(
        Color.parseColor("#2563EB"), // Azul Real
        Color.parseColor("#059669"), // Esmeralda
        Color.parseColor("#D97706"), // Ámbar / Oro
        Color.parseColor("#7C3AED"), // Violeta
        Color.parseColor("#DC2626"), // Rojo Coral
        Color.parseColor("#0891B2"), // Cian
        Color.parseColor("#0F766E"), // Verde Azulado
        Color.parseColor("#64748B"), // Gris Pizarra
        Color.parseColor("#E11D48"), // Rosa Oscuro
        Color.parseColor("#4F46E5"), // Índigo
        Color.parseColor("#CA8A04"), // Amarillo Mostaza
        Color.parseColor("#475569")  // Pizarra Oscuro
    )

    fun generateAndShareBalancePdf(
        context: Context,
        closedTandas: List<Tanda>,
        jornada: Jornada?,
        businessName: String = "EL QADRE"
    ): File? {
        try {
            if (closedTandas.isEmpty()) {
                Toast.makeText(context, "No hay tandas cerradas para generar el Balance.", Toast.LENGTH_SHORT).show()
                return null
            }

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val paint = Paint().apply { isAntiAlias = true }
            val linePaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val nowStr = dateFormatter.format(Date())

            val totalClosedCount = closedTandas.size
            val totalActualYield = closedTandas.sumOf { it.actualYield }
            val totalExpectedYield = closedTandas.sumOf { if (it.expectedYield > 0.0) it.expectedYield else it.estimatedYield }
            val totalSpecialQty = closedTandas.sumOf { it.specialPresentationQty }
            val totalSpecialUnitsEq = closedTandas.sumOf { tanda ->
                if (tanda.specialPresentationQty > 0.0) {
                    val presEquiv = if (tanda.specialPresentationEquivalence > 0.0) tanda.specialPresentationEquivalence else 1.0
                    tanda.specialPresentationQty * presEquiv
                } else 0.0
            }
            val totalYieldEquivalent = totalActualYield + totalSpecialUnitsEq
            val hasSpecialPres = totalSpecialQty > 0.0

            val prodUnit = closedTandas.firstOrNull()?.productionUnit?.ifBlank { "unidades" } ?: "unidades"

            // Insumo base
            val baseNames = closedTandas.map { it.baseMateriaPrimaName.ifBlank { "Harina" } }.distinct()
            val baseNameDisplay = if (baseNames.isEmpty()) "Insumo base" else baseNames.joinToString(", ")
            val totalBaseQuantity = closedTandas.sumOf { it.baseQuantityUsed }
            val baseUnit = closedTandas.firstOrNull()?.baseQuantityUnit?.ifBlank { "lb" } ?: "lb"

            // Rendimiento general
            val rendGeneralVal = if (totalBaseQuantity > 0.0) totalYieldEquivalent / totalBaseQuantity else 0.0
            val rendGeneralFormatted = if (rendGeneralVal % 1.0 == 0.0) rendGeneralVal.toInt().toString() else "%.2f".format(rendGeneralVal)
            val displayEfficiencyPct = if (totalExpectedYield > 0.0) (totalYieldEquivalent / totalExpectedYield) * 100.0 else 100.0

            // Consumo consolidado (Harina en libras garantizada)
            val consolidatedIngredients: List<ConsolidatedIngredientItem> = calculateConsolidatedIngredients(closedTandas)

            // Formateos exactos
            val totalActualYieldStr = if (totalActualYield % 1.0 == 0.0) totalActualYield.toInt().toString() else "%.1f".format(totalActualYield)
            val totalSpecialQtyStr = if (totalSpecialQty % 1.0 == 0.0) totalSpecialQty.toInt().toString() else "%.1f".format(totalSpecialQty)
            val totalSpecialUnitsEqStr = if (totalSpecialUnitsEq % 1.0 == 0.0) totalSpecialUnitsEq.toInt().toString() else "%.1f".format(totalSpecialUnitsEq)
            val totalYieldEquivalentStr = if (totalYieldEquivalent % 1.0 == 0.0) totalYieldEquivalent.toInt().toString() else "%.1f".format(totalYieldEquivalent)
            val totalBaseQtyStr = if (totalBaseQuantity % 1.0 == 0.0) totalBaseQuantity.toInt().toString() else "%.2f".format(totalBaseQuantity).trimEnd('0').trimEnd('.')

            val jornadaIdStr = jornada?.let { "#${it.id}" } ?: closedTandas.firstOrNull()?.jornada ?: "Actual"
            val jornadaFechaStr = jornada?.let { dateOnlyFormatter.format(Date(it.openedAt)) } ?: dateOnlyFormatter.format(Date())

            fun drawHeader(titleSuffix: String = "") {
                // Barra superior oscura de marca
                paint.color = Color.parseColor("#0F172A") // ElQadreNavy
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 56f, paint)

                paint.color = Color.parseColor("#F59E0B") // ElQadreGold
                paint.textSize = 15f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(businessName.uppercase(), 30f, 26f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val subtitle = if (titleSuffix.isBlank()) "BALANCE GENERAL DE TANDAS CERRADAS" else "BALANCE DE TANDAS — $titleSuffix"
                canvas.drawText(subtitle, 30f, 44f, paint)

                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 8.5f
                canvas.drawText("Jornada: $jornadaIdStr ($jornadaFechaStr)", (pageWidth - 210).toFloat(), 26f, paint)
                canvas.drawText("Emisión: $nowStr", (pageWidth - 210).toFloat(), 44f, paint)

                // Franja decorativa dorada
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawRect(0f, 56f, pageWidth.toFloat(), 59f, paint)
            }

            fun drawFooter(curPage: Int) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("El Qadre • Sistema de Gestión y Balance Operativo", 30f, pageHeight - 20f, paint)
                val pageStr = "Página $curPage"
                val pWidth = paint.measureText(pageStr)
                canvas.drawText(pageStr, pageWidth - 30f - pWidth, pageHeight - 20f, paint)
                canvas.drawLine(30f, pageHeight - 28f, pageWidth - 30f, pageHeight - 28f, linePaint)
            }

            fun drawTableHeader(topY: Float) {
                val colX0 = 30f
                val colX1 = 60f
                val colX2 = 250f
                val colX3 = 385f
                val colX4 = 485f
                val tableRight = (pageWidth - 30).toFloat()

                paint.color = Color.parseColor("#1E293B")
                canvas.drawRect(colX0, topY, tableRight, topY + 22f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("N°", colX0 + 8f, topY + 15f, paint)
                canvas.drawText("INGREDIENTE", colX1 + 8f, topY + 15f, paint)
                canvas.drawText("CANTIDAD CONSUMIDA", colX2 + 8f, topY + 15f, paint)
                canvas.drawText("UNIDAD", colX3 + 8f, topY + 15f, paint)
                canvas.drawText("PARTICIPACIÓN", colX4 + 5f, topY + 15f, paint)
            }

            fun checkNewPage(neededHeight: Float, currentY: Float, isInsideTable: Boolean = false): Float {
                if (currentY + neededHeight > pageHeight - 45f) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader()
                    val startY = 78f
                    if (isInsideTable) {
                        drawTableHeader(startY)
                        return startY + 22f
                    }
                    return startY
                }
                return currentY
            }

            // Iniciar primera página
            drawHeader()
            var y = 74f

            // ==========================================
            // SECCIÓN 1: RENDIMIENTO GENERAL Y PRODUCCIÓN
            // ==========================================
            val section1Height = if (hasSpecialPres) 154f else 118f
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + section1Height, 8f, 8f, paint)

            paint.color = Color.parseColor("#CBD5E1")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + section1Height, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // Título de la sección 1
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("1. RESUMEN GENERAL DE RENDIMIENTO Y PRODUCCIÓN", 42f, y + 20f, paint)

            paint.color = Color.parseColor("#047857")
            paint.textSize = 8.5f
            val badgeText = "$totalClosedCount TANDAS CERRADAS"
            val badgeW = paint.measureText(badgeText)
            paint.color = Color.parseColor("#DCFCE7")
            canvas.drawRoundRect(pageWidth - 42f - badgeW - 14f, y + 8f, pageWidth - 42f, y + 26f, 4f, 4f, paint)
            paint.color = Color.parseColor("#047857")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(badgeText, pageWidth - 42f - badgeW - 7f, y + 20f, paint)

            canvas.drawLine(42f, y + 30f, (pageWidth - 42).toFloat(), y + 30f, linePaint)

            // Fila 1: Cantidad producida y Rendimiento general
            var infoY = y + 46f
            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Cantidad Total Producida:", 44f, infoY, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$totalActualYieldStr $prodUnit", 200f, infoY, paint)

            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Rendimiento General:", 320f, infoY, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$rendGeneralFormatted $prodUnit / $baseUnit", 440f, infoY, paint)

            // Fila 2: Insumo base utilizado y Total consumido
            infoY += 20f
            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Ingrediente Base Utilizado:", 44f, infoY, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(baseNameDisplay, 200f, infoY, paint)

            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Total Insumo Base Consumido:", 320f, infoY, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$totalBaseQtyStr $baseUnit", 460f, infoY, paint)

            // Fila 3: Eficiencia vs esperado
            infoY += 20f
            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Eficiencia vs Esperado:", 44f, infoY, paint)

            val effColor = if (displayEfficiencyPct >= 95.0) Color.parseColor("#047857") else Color.parseColor("#DC2626")
            paint.color = effColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${"%.1f".format(displayEfficiencyPct)}%", 200f, infoY, paint)

            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Jornada Correspondiente:", 320f, infoY, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(jornadaIdStr, 440f, infoY, paint)

            // Fila opcional: Presentaciones especiales
            if (hasSpecialPres) {
                infoY += 20f
                paint.color = Color.parseColor("#F0FDF4")
                canvas.drawRoundRect(42f, infoY - 12f, (pageWidth - 42).toFloat(), infoY + 22f, 4f, 4f, paint)

                paint.color = Color.parseColor("#15803D")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Presentaciones Especiales: $totalSpecialQtyStr un.", 50f, infoY + 4f, paint)
                canvas.drawText("Unidades Equivalentes: +$totalSpecialUnitsEqStr $prodUnit eq.", 210f, infoY + 4f, paint)
                canvas.drawText("Total Producción Equivalente: $totalYieldEquivalentStr $prodUnit eq.", 380f, infoY + 4f, paint)
            }

            y += section1Height + 14f

            // ==========================================
            // SECCIÓN 2: CONSUMO GENERAL DE INGREDIENTES
            // ==========================================
            y = checkNewPage(90f, y)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("2. CONSUMO GENERAL CONSOLIDADO DE INGREDIENTES", 30f, y + 14f, paint)

            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Cada ingrediente acumula el consumo real de todas las Tandas cerradas correspondientes.", 30f, y + 26f, paint)

            y += 34f

            val colX0 = 30f
            val colX1 = 60f
            val colX2 = 250f
            val colX3 = 385f
            val colX4 = 485f
            val tableRight = (pageWidth - 30).toFloat()

            drawTableHeader(y)
            y += 22f

            val totalIngWeightOrQty = consolidatedIngredients.sumOf { it.totalQuantity }.coerceAtLeast(0.001)

            if (consolidatedIngredients.isEmpty()) {
                y = checkNewPage(24f, y, true)
                paint.color = Color.parseColor("#F8FAFC")
                canvas.drawRect(colX0, y, tableRight, y + 22f, paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("1", colX0 + 12f, y + 15f, paint)
                canvas.drawText(baseNameDisplay, colX1 + 10f, y + 15f, paint)
                canvas.drawText(totalBaseQtyStr, colX2 + 10f, y + 15f, paint)
                canvas.drawText(baseUnit, colX3 + 10f, y + 15f, paint)
                canvas.drawText("100.0%", colX4 + 10f, y + 15f, paint)

                canvas.drawLine(colX0, y + 22f, tableRight, y + 22f, linePaint)
                y += 22f
            } else {
                consolidatedIngredients.forEachIndexed { index, ing ->
                    y = checkNewPage(22f, y, true)
                    val isEven = index % 2 == 0
                    paint.color = if (isEven) Color.parseColor("#FFFFFF") else Color.parseColor("#F8FAFC")
                    canvas.drawRect(colX0, y, tableRight, y + 20f, paint)

                    // Color de acento para el ingrediente
                    val paletteColor = CHART_PALETTE[index % CHART_PALETTE.size]
                    paint.color = paletteColor
                    canvas.drawCircle(colX0 + 14f, y + 10f, 4f, paint)

                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("${index + 1}", colX0 + 22f, y + 14f, paint)

                    val ingNameTrim = if (ing.name.length > 28) ing.name.take(27) + "…" else ing.name
                    canvas.drawText(ingNameTrim, colX1 + 8f, y + 14f, paint)

                    val qtyFormatted = if (ing.totalQuantity % 1.0 == 0.0) ing.totalQuantity.toInt().toString() else "%.2f".format(ing.totalQuantity).trimEnd('0').trimEnd('.')
                    canvas.drawText(qtyFormatted, colX2 + 8f, y + 14f, paint)

                    paint.color = Color.parseColor("#475569")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(ing.unit.ifBlank { "u" }, colX3 + 8f, y + 14f, paint)

                    val pct = (ing.totalQuantity / totalIngWeightOrQty) * 100.0
                    val pctStr = if (pct >= 0.1) "%.1f%%".format(pct) else "<0.1%"
                    paint.color = Color.parseColor("#047857")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(pctStr, colX4 + 8f, y + 14f, paint)

                    canvas.drawLine(colX0, y + 20f, tableRight, y + 20f, linePaint)
                    y += 20f
                }
            }

            // ==========================================
            // PÁGINA DE GRÁFICOS ANALÍTICOS (PASTEL Y BARRAS)
            // ==========================================
            drawFooter(pageNumber)
            pdfDocument.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas

            drawHeader("GRÁFICOS DEL BALANCE")
            var chartY = 74f

            val ingredientsForCharts: List<ConsolidatedIngredientItem> = if (consolidatedIngredients.isNotEmpty()) {
                consolidatedIngredients
            } else {
                listOf(ConsolidatedIngredientItem(baseNameDisplay, totalBaseQuantity, baseUnit, "$totalBaseQtyStr $baseUnit"))
            }

            // ----------------------------------------------------
            // 3. GRÁFICO DE PASTEL: PARTICIPACIÓN DE INGREDIENTES
            // ----------------------------------------------------
            val pieCardHeight = 270f
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)

            paint.color = Color.parseColor("#CBD5E1")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // Título del gráfico de pastel
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("3. GRÁFICO DE PASTEL — PARTICIPACIÓN DE CONSUMO", 44f, chartY + 20f, paint)

            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Distribución proporcional acumulada de todos los ingredientes utilizados.", 44f, chartY + 32f, paint)

            // Dibujar pastel
            val pieCenterX = 130f
            val pieCenterY = chartY + 150f
            val pieRadius = 80f
            val pieRect = RectF(pieCenterX - pieRadius, pieCenterY - pieRadius, pieCenterX + pieRadius, pieCenterY + pieRadius)

            val totalSumForPie = ingredientsForCharts.sumOf { it.totalQuantity }.coerceAtLeast(0.0001)
            var startAngle = -90f
            val slicePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            ingredientsForCharts.forEachIndexed { idx, ing ->
                val sweepAngle = ((ing.totalQuantity / totalSumForPie) * 360.0).toFloat().coerceAtLeast(1.5f)
                slicePaint.color = CHART_PALETTE[idx % CHART_PALETTE.size]
                canvas.drawArc(pieRect, startAngle, sweepAngle, true, slicePaint)
                startAngle += sweepAngle
            }

            // Centro blanco de dona elegante
            paint.color = Color.WHITE
            canvas.drawCircle(pieCenterX, pieCenterY, 36f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val centerText = "${ingredientsForCharts.size}"
            val centerSub = "Insumos"
            val ctw = paint.measureText(centerText)
            canvas.drawText(centerText, pieCenterX - (ctw / 2f), pieCenterY - 2f, paint)
            paint.textSize = 7f
            paint.color = Color.parseColor("#64748B")
            val csw = paint.measureText(centerSub)
            canvas.drawText(centerSub, pieCenterX - (csw / 2f), pieCenterY + 10f, paint)

            // Leyenda del pastel en el lado derecho
            var legendY = chartY + 52f
            val legendX = 240f
            ingredientsForCharts.take(9).forEachIndexed { idx, ing ->
                val pColor = CHART_PALETTE[idx % CHART_PALETTE.size]
                paint.color = pColor
                canvas.drawRoundRect(legendX, legendY - 8f, legendX + 10f, legendY + 2f, 2f, 2f, paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val ingNameTrim = if (ing.name.length > 18) ing.name.take(17) + "…" else ing.name
                canvas.drawText(ingNameTrim, legendX + 16f, legendY, paint)

                val pct = (ing.totalQuantity / totalSumForPie) * 100.0
                val pctStr = if (pct >= 0.1) "%.1f%%".format(pct) else "<0.1%"
                paint.color = Color.parseColor("#047857")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(pctStr, legendX + 140f, legendY, paint)

                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val qtyFormatted = if (ing.totalQuantity % 1.0 == 0.0) ing.totalQuantity.toInt().toString() else "%.2f".format(ing.totalQuantity).trimEnd('0').trimEnd('.')
                val qtyStr = "$qtyFormatted ${ing.unit}"
                canvas.drawText(qtyStr, legendX + 195f, legendY, paint)

                legendY += 20f
            }

            if (ingredientsForCharts.size > 9) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("+ ${ingredientsForCharts.size - 9} insumos adicionales en desglose", legendX + 16f, legendY, paint)
            }

            chartY += pieCardHeight + 16f

            // ----------------------------------------------------
            // 4. GRÁFICO DE BARRAS: CONSUMO COMPARATIVO DE INSUMOS
            // ----------------------------------------------------
            val barCardHeight = (44f + (ingredientsForCharts.size.coerceAtMost(8) * 26f)).coerceAtLeast(160f)
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + barCardHeight, 8f, 8f, paint)

            paint.color = Color.parseColor("#CBD5E1")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + barCardHeight, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // Título del gráfico de barras
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("4. GRÁFICO DE BARRAS — CONSUMO COMPARATIVO DE INGREDIENTES", 44f, chartY + 20f, paint)

            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Nivel comparativo de uso y consumo registrado en las tandas.", 44f, chartY + 32f, paint)

            var barY = chartY + 50f
            val maxQuantity = ingredientsForCharts.maxOfOrNull { it.totalQuantity }?.coerceAtLeast(0.001) ?: 1.0
            val barStartX = 180f
            val maxBarWidth = 220f

            ingredientsForCharts.take(8).forEachIndexed { idx, ing ->
                val pColor = CHART_PALETTE[idx % CHART_PALETTE.size]

                // Nombre del ingrediente a la izquierda
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val ingLabel = if (ing.name.length > 18) ing.name.take(17) + "…" else ing.name
                canvas.drawText(ingLabel, 44f, barY + 9f, paint)

                // Track de fondo de la barra
                paint.color = Color.parseColor("#E2E8F0")
                canvas.drawRoundRect(barStartX, barY, barStartX + maxBarWidth, barY + 12f, 4f, 4f, paint)

                // Barra proporcional rellena
                val barWidth = ((ing.totalQuantity / maxQuantity) * maxBarWidth).toFloat().coerceAtLeast(6f)
                slicePaint.color = pColor
                canvas.drawRoundRect(barStartX, barY, barStartX + barWidth, barY + 12f, 4f, 4f, slicePaint)

                // Valor numérico a la derecha de la barra
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val qtyFormatted = if (ing.totalQuantity % 1.0 == 0.0) ing.totalQuantity.toInt().toString() else "%.2f".format(ing.totalQuantity).trimEnd('0').trimEnd('.')
                val formattedVal = "$qtyFormatted ${ing.unit}"
                canvas.drawText(formattedVal, barStartX + maxBarWidth + 10f, barY + 9f, paint)

                barY += 24f
            }

            drawFooter(pageNumber)
            pdfDocument.finishPage(page)

            // Guardar en almacenamiento de documentos
            val pdfDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "BalancePDF")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val fileName = "Balance_Tandas_Jornada_${jornada?.id ?: "Actual"}_${System.currentTimeMillis()}.pdf"
            val file = File(pdfDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            // Descargar / Compartir PDF
            shareBalancePdf(context, file, jornada?.id ?: 0L)

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF del Balance: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun shareBalancePdf(context: Context, file: File, jornadaId: Long) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val jText = if (jornadaId > 0) "Jornada #$jornadaId" else "Jornada Actual"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Balance General de Tandas - $jText")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el Balance General de Tandas cerradas de la $jText con el resumen de producción, consumo consolidado y gráficos analíticos.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Descargar / Compartir Balance (WhatsApp, etc.)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "PDF guardado: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF guardado exitosamente en Documentos.", Toast.LENGTH_LONG).show()
        }
    }
}
