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
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.MovimientoMateriaPrima
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador de PDF oficial para INFORME INSUMOS
 * Exporta:
 * PÁGINA 1: Tablas detalladas de existencias, compras, movimientos y valoración de la jornada.
 * PÁGINA 2: Gráficos analíticos reales (Participación por importe, Mayor uso, Mayor movimiento).
 */
object InformeInsumosPdfExporter {

    private val CHART_PALETTE = intArrayOf(
        Color.parseColor("#2563EB"), // Azul Real
        Color.parseColor("#059669"), // Esmeralda
        Color.parseColor("#D97706"), // Ámbar / Oro
        Color.parseColor("#7C3AED"), // Violeta
        Color.parseColor("#DC2626"), // Rojo Coral
        Color.parseColor("#0891B2"), // Cian
        Color.parseColor("#64748B")  // Gris Pizarra (OTROS)
    )

    data class InsumoPdfRow(
        val id: Long,
        val name: String,
        val unit: String,
        val initialStock: Double,
        val entradasQty: Double,
        val finalStock: Double,
        val unitCost: Double,
        val importe: Double,
        val priceBreakdown: List<String> = emptyList(),
        val entradasDetalles: List<String> = emptyList()
    )

    data class InformeInsumosPdfData(
        val businessName: String,
        val duenoName: String,
        val jornadaId: Long,
        val isJornadaOpen: Boolean,
        val fechaStr: String,
        val rows: List<InsumoPdfRow>,
        val totalImporte: Double,
        val totalEntradasCount: Int,
        val activeMaterias: List<MateriaPrima>,
        val movimientosJornada: List<MovimientoMateriaPrima>
    )

    fun generatePdf(
        context: Context,
        uiStateMaterias: List<MateriaPrima>,
        movimientosMateria: List<MovimientoMateriaPrima>,
        activeJornada: Jornada?,
        businessName: String = "ElQadre",
        duenoName: String = "Administrador"
    ): File? {
        try {
            val isJornadaOpen = activeJornada != null && activeJornada.isOpen
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val fechaStr = if (activeJornada != null) {
                dateFormat.format(Date(activeJornada.openedAt))
            } else {
                dateFormat.format(Date())
            }

            val movimientosJornada = if (activeJornada != null) {
                val end = activeJornada.closedAt ?: Long.MAX_VALUE
                movimientosMateria.filter { it.date >= activeJornada.openedAt && it.date <= end }
            } else {
                movimientosMateria
            }

            val activeMaterias = uiStateMaterias.filter { it.isActive }

            val rows = activeMaterias.map { mp ->
                val entradas = movimientosJornada.filter { it.materiaPrimaId == mp.id && it.type == "ENTRADA" }
                val entradasQty = entradas.sumOf { it.quantity }
                val inicioQty = mp.initialStock
                val finalQty = mp.stock
                val importe = finalQty * mp.unitCost

                val priceBreakdown = mutableListOf<String>()
                if (entradas.isNotEmpty()) {
                    entradas.forEachIndexed { i, e ->
                        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.date))
                        var notePrice = ""
                        val match = """importe\s*(?:de)?\s*\$?([0-9]+(?:\.[0-9]+)?)""".toRegex(RegexOption.IGNORE_CASE).find(e.notes)
                        if (match != null) {
                            val imp = match.groupValues[1].toDoubleOrNull() ?: 0.0
                            if (e.quantity > 0.0 && imp > 0.0) {
                                val unitP = imp / e.quantity
                                notePrice = " ($${"%.2f".format(unitP)}/u = $${"%.2f".format(imp)})"
                            }
                        }
                        priceBreakdown.add("Entrada #${i + 1} ($time): +${"%.2f".format(e.quantity)} ${mp.unit}$notePrice")
                    }
                }

                InsumoPdfRow(
                    id = mp.id,
                    name = mp.name,
                    unit = mp.unit,
                    initialStock = inicioQty,
                    entradasQty = entradasQty,
                    finalStock = finalQty,
                    unitCost = mp.unitCost,
                    importe = importe,
                    priceBreakdown = priceBreakdown
                )
            }

            val totalImporte = rows.sumOf { it.importe }
            val totalEntradasCount = movimientosJornada.count { it.type == "ENTRADA" }

            val reportData = InformeInsumosPdfData(
                businessName = businessName,
                duenoName = duenoName,
                jornadaId = activeJornada?.id ?: 0L,
                isJornadaOpen = isJornadaOpen,
                fechaStr = fechaStr,
                rows = rows,
                totalImporte = totalImporte,
                totalEntradasCount = totalEntradasCount,
                activeMaterias = activeMaterias,
                movimientosJornada = movimientosJornada
            )

            return renderPdfDocument(context, reportData)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun renderPdfDocument(context: Context, data: InformeInsumosPdfData): File? {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard width (pt)
        val pageHeight = 842 // A4 standard height (pt)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        fun drawHeader(c: Canvas, pageTitle: String = "INFORME OFICIAL DE INSUMOS Y EXISTENCIAS") {
            // Header Top Bar
            val topBarPaint = Paint().apply { color = Color.parseColor("#0F172A") }
            c.drawRect(0f, 0f, pageWidth.toFloat(), 64f, topBarPaint)

            paint.color = Color.parseColor("#D4AF37") // Gold
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(data.businessName.uppercase(Locale.getDefault()), 36f, 30f, paint)

            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(pageTitle, 36f, 50f, paint)

            paint.textSize = 9.5f
            paint.color = Color.parseColor("#94A3B8")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.RIGHT
            val jornadaLabel = if (data.isJornadaOpen) "JORNADA #${data.jornadaId}" else "RESUMEN DE INVENTARIO"
            c.drawText(jornadaLabel, (pageWidth - 36).toFloat(), 30f, paint)
            c.drawText("Fecha: ${data.fechaStr} | Pág. $pageNumber", (pageWidth - 36).toFloat(), 48f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        fun drawFooter(c: Canvas) {
            val footerLinePaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
            }
            c.drawLine(36f, (pageHeight - 32).toFloat(), (pageWidth - 36).toFloat(), (pageHeight - 32).toFloat(), footerLinePaint)

            paint.textSize = 8f
            paint.color = Color.parseColor("#64748B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            c.drawText("ElQadre • Documento oficial de inventario y valoración de existencias", 36f, (pageHeight - 18).toFloat(), paint)

            paint.textAlign = Paint.Align.RIGHT
            c.drawText("Página $pageNumber", (pageWidth - 36).toFloat(), (pageHeight - 18).toFloat(), paint)
            paint.textAlign = Paint.Align.LEFT
        }

        // ==========================================
        // PÁGINA 1: TABLAS Y EXISTENCIAS
        // ==========================================
        drawHeader(canvas, "INFORME OFICIAL DE INSUMOS Y EXISTENCIAS")
        drawFooter(canvas)

        var y = 84f

        fun checkNewPage(neededSpace: Float): Canvas {
            if (y + neededSpace > pageHeight - 48f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                val newCanvas = page.canvas
                drawHeader(newCanvas, "INFORME OFICIAL DE INSUMOS Y EXISTENCIAS")
                drawFooter(newCanvas)
                y = 84f
                return newCanvas
            }
            return canvas
        }

        // Summary Card Box on Page 1
        val summaryBoxHeight = 56f
        val summaryBgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val summaryBorderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(36f, y, (pageWidth - 36).toFloat(), y + summaryBoxHeight, 8f, 8f, summaryBgPaint)
        canvas.drawRoundRect(36f, y, (pageWidth - 36).toFloat(), y + summaryBoxHeight, 8f, 8f, summaryBorderPaint)

        // Column 1: Total Insumos
        paint.color = Color.parseColor("#475569")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL INSUMOS", 50f, y + 20f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 15f
        canvas.drawText("${data.rows.size}", 50f, y + 42f, paint)

        // Column 2: Entradas Registradas
        paint.color = Color.parseColor("#475569")
        paint.textSize = 8.5f
        canvas.drawText("ENTRADAS JORNADA", 190f, y + 20f, paint)
        paint.color = Color.parseColor("#15803D")
        paint.textSize = 15f
        canvas.drawText("${data.totalEntradasCount}", 190f, y + 42f, paint)

        // Column 3: VALORACIÓN TOTAL EXISTENCIAS (IMPORTE TOTAL)
        paint.color = Color.parseColor("#B45309")
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("VALORACIÓN TOTAL DE EXISTENCIAS", (pageWidth - 50).toFloat(), y + 20f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 16f
        canvas.drawText("$${"%.2f".format(data.totalImporte)} CUP", (pageWidth - 50).toFloat(), y + 42f, paint)
        paint.textAlign = Paint.Align.LEFT

        y += summaryBoxHeight + 18f

        // Table Header
        fun drawTableHeader(c: Canvas) {
            val thBg = Paint().apply { color = Color.parseColor("#1E293B") }
            c.drawRect(36f, y, (pageWidth - 36).toFloat(), y + 24f, thBg)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            c.drawText("INSUMO / UNIDAD", 44f, y + 16f, paint)
            c.drawText("INICIO", 200f, y + 16f, paint)
            c.drawText("ENTRADAS", 265f, y + 16f, paint)
            c.drawText("FINAL", 345f, y + 16f, paint)
            c.drawText("COSTO UNIT.", 415f, y + 16f, paint)

            paint.textAlign = Paint.Align.RIGHT
            c.drawText("IMPORTE (CUP)", (pageWidth - 44).toFloat(), y + 16f, paint)
            paint.textAlign = Paint.Align.LEFT

            y += 24f
        }

        drawTableHeader(canvas)

        // Table Rows
        data.rows.forEachIndexed { index, row ->
            val hasBreakdown = row.priceBreakdown.isNotEmpty()
            val extraHeight = if (hasBreakdown) (row.priceBreakdown.size * 12f) + 6f else 0f
            val rowHeight = 26f + extraHeight

            canvas = checkNewPage(rowHeight)

            // Row Background (Alternating)
            val rowBg = Paint().apply {
                color = if (index % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            }
            canvas.drawRect(36f, y, (pageWidth - 36).toFloat(), y + rowHeight, rowBg)
            canvas.drawLine(36f, y + rowHeight, (pageWidth - 36).toFloat(), y + rowHeight, linePaint)

            // Insumo Name and Unit
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(row.name.uppercase(Locale.getDefault()), 44f, y + 16f, paint)

            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("(${row.unit})", 44f, y + 26f, paint)

            // INICIO
            paint.color = Color.parseColor("#334155")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("${"%.2f".format(row.initialStock)}", 200f, y + 16f, paint)

            // ENTRADAS
            if (row.entradasQty > 0) {
                paint.color = Color.parseColor("#15803D")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("+${"%.2f".format(row.entradasQty)}", 265f, y + 16f, paint)
            } else {
                paint.color = Color.parseColor("#94A3B8")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("0.00", 265f, y + 16f, paint)
            }

            // FINAL
            paint.color = if (row.finalStock <= 0.0) Color.parseColor("#DC2626") else Color.parseColor("#0F766E")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            canvas.drawText("${"%.2f".format(row.finalStock)}", 345f, y + 16f, paint)

            // COSTO UNIT
            paint.color = Color.parseColor("#475569")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("$${"%.2f".format(row.unitCost)}", 415f, y + 16f, paint)

            // IMPORTE
            paint.color = Color.parseColor("#B45309")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$${"%.2f".format(row.importe)}", (pageWidth - 44).toFloat(), y + 16f, paint)
            paint.textAlign = Paint.Align.LEFT

            // Desglose de entradas / compras con diferentes precios si existen
            if (hasBreakdown) {
                var breakdownY = y + 26f
                paint.textSize = 7.5f
                paint.color = Color.parseColor("#059669")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                row.priceBreakdown.forEach { itemText ->
                    canvas.drawText("• $itemText", 180f, breakdownY, paint)
                    breakdownY += 12f
                }
            }

            y += rowHeight
        }

        // Total Row at the bottom
        canvas = checkNewPage(36f)
        val totalBg = Paint().apply { color = Color.parseColor("#0F172A") }
        canvas.drawRect(36f, y, (pageWidth - 36).toFloat(), y + 28f, totalBg)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL GENERAL VALORACIÓN DE EXISTENCIAS:", 44f, y + 18f, paint)

        paint.color = Color.parseColor("#D4AF37")
        paint.textSize = 12f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$${"%.2f".format(data.totalImporte)} CUP", (pageWidth - 44).toFloat(), y + 18f, paint)
        paint.textAlign = Paint.Align.LEFT

        y += 36f

        // Notes and Disclaimer
        canvas = checkNewPage(50f)
        paint.textSize = 8f
        paint.color = Color.parseColor("#64748B")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("* Nota: La existencia FINAL constituye la base de INICIO de la jornada siguiente.", 36f, y, paint)
        y += 12f
        canvas.drawText("* Los importes reflejan la composición real de inventario y su costo ponderado histórico.", 36f, y, paint)

        // Signatures
        y += 24f
        canvas = checkNewPage(45f)
        val sigLineWidth = 160f
        canvas.drawLine(50f, y, 50f + sigLineWidth, y, linePaint)
        canvas.drawLine((pageWidth - 50 - sigLineWidth).toFloat(), y, (pageWidth - 50).toFloat(), y, linePaint)
        y += 12f

        paint.textSize = 8.5f
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Firma del DUEÑO / Responsable", 60f, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Verificación de Almacén", (pageWidth - 60).toFloat(), y, paint)
        paint.textAlign = Paint.Align.LEFT

        pdfDocument.finishPage(page)

        // ==========================================
        // PÁGINA 2: GRÁFICOS REALES DE INSUMOS
        // ==========================================
        pageNumber++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas

        drawHeader(canvas, "GRÁFICOS ANALÍTICOS • INFORME DE INSUMOS")
        drawFooter(canvas)

        var chartY = 78f
        val slicePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

        // ----------------------------------------------------
        // GRÁFICO 1: PARTICIPACIÓN POR IMPORTE (Pastel Top 5 + OTROS)
        // ----------------------------------------------------
        val pieCardHeight = 190f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Header
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. PARTICIPACIÓN POR IMPORTE (VALOR MONETARIO FINAL)", 48f, chartY + 18f, paint)

        val listConImporte = data.rows.map { row ->
            row to row.importe
        }.sortedByDescending { it.second }

        val totalImpGeneral = listConImporte.sumOf { it.second }

        if (totalImpGeneral <= 0.0) {
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Sin existencias con importe disponible para graficar.", 48f, chartY + 50f, paint)
        } else {
            val top5 = listConImporte.take(5)
            val others = listConImporte.drop(5)

            val pieCenterX = 115f
            val pieCenterY = chartY + 105f
            val pieRadius = 55f
            val pieOval = RectF(pieCenterX - pieRadius, pieCenterY - pieRadius, pieCenterX + pieRadius, pieCenterY + pieRadius)

            var startAngle = -90f

            top5.forEachIndexed { index, (row, imp) ->
                val colorInt = CHART_PALETTE.getOrElse(index) { Color.GRAY }
                val sweepAngle = ((imp / totalImpGeneral) * 360f).toFloat()
                if (sweepAngle > 0f) {
                    slicePaint.color = colorInt
                    canvas.drawArc(pieOval, startAngle, sweepAngle, true, slicePaint)
                    startAngle += sweepAngle
                }
            }

            val othersSum = others.sumOf { it.second }
            if (othersSum > 0.0) {
                val colorInt = CHART_PALETTE.last()
                val sweepAngle = ((othersSum / totalImpGeneral) * 360f).toFloat()
                if (sweepAngle > 0f) {
                    slicePaint.color = colorInt
                    canvas.drawArc(pieOval, startAngle, sweepAngle, true, slicePaint)
                    startAngle += sweepAngle
                }
            }

            // Donut Center Hole
            paint.color = Color.WHITE
            canvas.drawCircle(pieCenterX, pieCenterY, 24f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TOTAL", pieCenterX, pieCenterY - 2f, paint)
            paint.textSize = 7.5f
            canvas.drawText("$${"%.0f".format(totalImpGeneral)}", pieCenterX, pieCenterY + 8f, paint)
            paint.textAlign = Paint.Align.LEFT

            // Leyenda
            var legendY = chartY + 36f
            val legendX = 195f

            top5.forEachIndexed { index, (row, imp) ->
                val colorInt = CHART_PALETTE.getOrElse(index) { Color.GRAY }
                val pct = ((imp / totalImpGeneral) * 100.0)

                slicePaint.color = colorInt
                canvas.drawRoundRect(legendX, legendY, legendX + 9f, legendY + 9f, 2f, 2f, slicePaint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(row.name.uppercase(Locale.getDefault()), legendX + 15f, legendY + 8f, paint)

                paint.color = Color.parseColor("#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("$${"%.2f".format(imp)} CUP (${"%.1f".format(pct)}%) • ${"%.1f".format(row.finalStock)} ${row.unit}", legendX + 150f, legendY + 8f, paint)

                legendY += 15f
            }

            if (othersSum > 0.0) {
                val colorInt = CHART_PALETTE.last()
                val pct = ((othersSum / totalImpGeneral) * 100.0)

                slicePaint.color = colorInt
                canvas.drawRoundRect(legendX, legendY, legendX + 9f, legendY + 9f, 2f, 2f, slicePaint)

                paint.color = Color.parseColor("#64748B")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("OTROS (${others.size} insumos)", legendX + 15f, legendY + 8f, paint)

                paint.color = Color.parseColor("#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("$${"%.2f".format(othersSum)} CUP (${"%.1f".format(pct)}%)", legendX + 150f, legendY + 8f, paint)
            }
        }

        chartY += pieCardHeight + 14f

        // ----------------------------------------------------
        // GRÁFICO 2: MAYOR USO DURANTE LA JORNADA (Barras)
        // ----------------------------------------------------
        val consumos = data.movimientosJornada.filter {
            it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA")
        }
        val consumosPorInsumo = consumos.groupBy { it.materiaPrimaId }

        val mayorUsoList = data.activeMaterias.mapNotNull { insumo ->
            val movs = consumosPorInsumo[insumo.id] ?: emptyList()
            val totalUsado = movs.sumOf { it.quantity }
            if (totalUsado > 0.0) {
                val costoTotalUsado = totalUsado * insumo.unitCost
                Triple(insumo, totalUsado, costoTotalUsado)
            } else null
        }.sortedByDescending { it.second }.take(5)

        val usoCardHeight = 175f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + usoCardHeight, 8f, 8f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + usoCardHeight, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Header
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("2. MAYOR CONSUMO / USO DURANTE LA JORNADA", 48f, chartY + 18f, paint)

        if (mayorUsoList.isEmpty()) {
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Sin consumos o salidas registradas durante esta jornada.", 48f, chartY + 45f, paint)
        } else {
            val maxUsado = mayorUsoList.maxOf { it.second }.coerceAtLeast(1.0)
            val barStartX = 150f
            val maxBarWidth = 230f
            var barY = chartY + 34f

            mayorUsoList.forEachIndexed { index, (insumo, totalUsado, costoUsado) ->
                val colorInt = CHART_PALETTE.getOrElse(index) { Color.RED }

                // Label
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(insumo.name.uppercase(Locale.getDefault()), 48f, barY + 8f, paint)

                // Track
                paint.color = Color.parseColor("#E2E8F0")
                canvas.drawRoundRect(barStartX, barY, barStartX + maxBarWidth, barY + 10f, 3f, 3f, paint)

                // Fill
                val barW = ((totalUsado / maxUsado) * maxBarWidth).toFloat().coerceAtLeast(4f)
                slicePaint.color = colorInt
                canvas.drawRoundRect(barStartX, barY, barStartX + barW, barY + 10f, 3f, 3f, slicePaint)

                // Info text
                paint.color = Color.parseColor("#BE123C")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("-${"%.2f".format(totalUsado)} ${insumo.unit} ($${"%.2f".format(costoUsado)})", barStartX + maxBarWidth + 10f, barY + 8f, paint)

                barY += 24f
            }
        }

        chartY += usoCardHeight + 14f

        // ----------------------------------------------------
        // GRÁFICO 3: MAYOR MOVIMIENTO TOTAL EN LA JORNADA (Barras)
        // ----------------------------------------------------
        val movsPorInsumo = data.movimientosJornada.groupBy { it.materiaPrimaId }
        val mayorMovList = data.activeMaterias.mapNotNull { insumo ->
            val movs = movsPorInsumo[insumo.id] ?: emptyList()
            val entradas = movs.filter { it.type == "ENTRADA" }.sumOf { it.quantity }
            val usos = movs.filter { it.type in listOf("TANDA_CONSUMO", "SALIDA", "SALIDA_VENTA", "MERMA") }.sumOf { it.quantity }
            val total = entradas + usos
            if (total > 0.0) {
                Triple(insumo, total, Pair(entradas, usos))
            } else null
        }.sortedByDescending { it.second }.take(5)

        val movCardHeight = 175f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + movCardHeight, 8f, 8f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(36f, chartY, (pageWidth - 36).toFloat(), chartY + movCardHeight, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Header
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("3. MAYOR MOVIMIENTO TOTAL (ENTRADAS + CONSUMOS)", 48f, chartY + 18f, paint)

        if (mayorMovList.isEmpty()) {
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Sin movimientos registrados durante esta jornada.", 48f, chartY + 45f, paint)
        } else {
            val maxMov = mayorMovList.maxOf { it.second }.coerceAtLeast(1.0)
            val barStartX = 150f
            val maxBarWidth = 230f
            var barY = chartY + 34f

            mayorMovList.forEachIndexed { index, (insumo, totalMov, entradasUsos) ->
                val colorInt = CHART_PALETTE.getOrElse(index) { Color.BLUE }
                val (eQty, uQty) = entradasUsos

                // Label
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(insumo.name.uppercase(Locale.getDefault()), 48f, barY + 8f, paint)

                // Track
                paint.color = Color.parseColor("#E2E8F0")
                canvas.drawRoundRect(barStartX, barY, barStartX + maxBarWidth, barY + 10f, 3f, 3f, paint)

                // Fill
                val barW = ((totalMov / maxMov) * maxBarWidth).toFloat().coerceAtLeast(4f)
                slicePaint.color = colorInt
                canvas.drawRoundRect(barStartX, barY, barStartX + barW, barY + 10f, 3f, 3f, slicePaint)

                // Info text
                paint.color = Color.parseColor("#0284C7")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("${"%.2f".format(totalMov)} ${insumo.unit} (+${"%.1f".format(eQty)} / -${"%.1f".format(uQty)})", barStartX + maxBarWidth + 10f, barY + 8f, paint)

                barY += 24f
            }
        }

        pdfDocument.finishPage(page)

        // Save PDF file
        val outputDir = File(context.cacheDir, "informes_insumos").apply { if (!exists()) mkdirs() }
        val fileName = "Informe_Insumos_Jornada_${data.jornadaId}_${System.currentTimeMillis()}.pdf"
        val file = File(outputDir, fileName)
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()

        return file
    }

    /**
     * Compartir el PDF mediante el mecanismo estándar de Android
     */
    fun sharePdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe Insumos - Existencias y Valoración")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el Informe Oficial de Insumos y Existencias.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir Informe de Insumos").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No se pudo compartir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Guardar copia en la carpeta de Documentos / Descargas públicas y abrirlo
     */
    fun saveToDeviceOrOpen(context: Context, sourceFile: File): File? {
        try {
            val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appFolder = File(targetDir, "ElQadre_Informes").apply { if (!exists()) mkdirs() }
            val targetFile = File(appFolder, sourceFile.name)

            sourceFile.copyTo(targetFile, overwrite = true)
            Toast.makeText(context, "Guardado en: Documentos/ElQadre_Informes", Toast.LENGTH_LONG).show()

            // Abrir el archivo
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openIntent)
            return targetFile
        } catch (e: Exception) {
            // Fallback: abrir desde cache
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    sourceFile
                )
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(openIntent)
                return sourceFile
            } catch (ex: Exception) {
                Toast.makeText(context, "Guardado en aplicación: ${sourceFile.name}", Toast.LENGTH_LONG).show()
                return sourceFile
            }
        }
    }
}
