package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CuadreCajaPdfExporter {

    data class ProduccionPdfRow(
        val productName: String,
        val tandasCount: Int,
        val totalProduced: Double,
        val unit: String,
        val defectuoso: Double,
        val consumo: Double,
        val regalia: Double,
        val vendible: Double,
        val price: Double,
        val ingresoEstimado: Double
    )

    data class MercaderiaPdfRow(
        val productName: String,
        val unit: String,
        val existenciaInicial: Double,
        val existenciaFinal: Double,
        val defectuoso: Double,
        val consumo: Double,
        val regalia: Double,
        val vendidas: Double,
        val price: Double,
        val ingresoEstimado: Double
    )

    data class AgregadoPdfRow(
        val name: String,
        val enviadas: Double,
        val vendidas: Double,
        val regalia: Double,
        val sobrantes: Double,
        val price: Double,
        val ingresoEstimado: Double
    )

    data class CocinaPagoPdfRow(
        val productName: String,
        val vendible: Double,
        val unit: String,
        val pagoUnitario: Double,
        val cantidadCocineros: Int,
        val totalPago: Double
    )

    data class CajeroPagoPdfInfo(
        val pagoProduccion: Double,
        val pagoMercaderias: Double,
        val totalPago: Double
    )

    data class CuadreCajaReportData(
        val businessName: String,
        val duenoName: String,
        val jornadaId: Long,
        val openedAt: Long,
        val closedAt: Long = System.currentTimeMillis(),
        val initialCash: Double,
        val ingresosProduccion: Double,
        val ingresosMercaderias: Double,
        val ingresosAgregados: Double = 0.0,
        val totalIngresos: Double = 0.0,
        val mermasTotalValor: Double,
        val transferenciasMonto: Double,
        val transferenciasCount: Int,
        val extracciones: Double,
        val extraccionesNotas: String = "",
        val efectivoEsperado: Double,
        val efectivoReal: Double,
        val diferencia: Double,
        val costoProduccion: Double = 0.0,
        val costoMercaderias: Double = 0.0,
        val costoAgregados: Double = 0.0,
        val costoTotal: Double = 0.0,
        val utilidadTeorica: Double = 0.0,
        val pagosConfirmados: Boolean = false,
        val totalPagosPersonal: Double = 0.0,
        val totalPagoCocina: Double = 0.0,
        val totalPagoCajero: Double = 0.0,
        val totalPagoDependientes: Double = 0.0,
        val cocinaPagoRows: List<CocinaPagoPdfRow> = emptyList(),
        val cajeroPagoInfo: CajeroPagoPdfInfo? = null,
        val dependientesRows: List<DependientePagoDistribucion> = emptyList(),
        val dineroFinalEnCaja: Double = 0.0,
        val notas: String,
        val produccionRows: List<ProduccionPdfRow>,
        val mercaderiaRows: List<MercaderiaPdfRow>,
        val agregadoRows: List<AgregadoPdfRow> = emptyList()
    )

    fun exportCuadreCajaReport(context: Context, data: CuadreCajaReportData): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val paint = Paint()
            val linePaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            fun drawHeader() {
                paint.color = Color.WHITE
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

                // Header Banner
                paint.color = Color.parseColor("#1E293B") // ElQadreNavy
                canvas.drawRect(30f, 25f, (pageWidth - 30).toFloat(), 92f, paint)

                // Title inside Banner
                paint.color = Color.WHITE
                paint.textSize = 15f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(data.businessName.ifBlank { "EL QADRE" }.uppercase(), 45f, 52f, paint)

                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("INFORME OFICIAL DE CUADRE DE CAJA Y LIQUIDACIÓN", 45f, 72f, paint)

                // Right side of banner
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 9f
                val fechaJornadaStr = if (data.openedAt > 0) dateOnlyFormatter.format(Date(data.openedAt)) else dateOnlyFormatter.format(Date())
                canvas.drawText("Jornada: #${data.jornadaId} | Fecha: $fechaJornadaStr", (pageWidth - 45).toFloat(), 52f, paint)
                canvas.drawText("Emisión: ${dateFormatter.format(Date())}", (pageWidth - 45).toFloat(), 72f, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            fun checkNewPage(neededHeight: Float, curY: Float): Float {
                if (curY + neededHeight > pageHeight - 50f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader()
                    return 110f
                }
                return curY
            }

            fun drawSectionHeader(title: String, curY: Float): Float {
                var y = checkNewPage(35f, curY)
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(title, 35f, y, paint)
                y += 5f
                canvas.drawLine(35f, y, (pageWidth - 35).toFloat(), y, linePaint)
                y += 14f
                return y
            }

            fun drawRow(label: String, value: String, curY: Float, isBold: Boolean = false, colorHex: String = "#0F172A", leftPad: Float = 40f): Float {
                var y = checkNewPage(18f, curY)
                paint.textSize = 9.5f
                paint.color = Color.parseColor(if (isBold) colorHex else "#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(label, leftPad, y, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.color = Color.parseColor(colorHex)
                canvas.drawText(value, (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                return y + 14f
            }

            drawHeader()
            var y = 110f

            // 1. IDENTIFICACIÓN Y FECHAS
            val fechaAperturaStr = if (data.openedAt > 0) dateFormatter.format(Date(data.openedAt)) else "No disponible"
            val fechaCierreStr = dateFormatter.format(Date(data.closedAt))

            // 2. RESUMEN DE VENTAS / INGRESOS
            y = drawSectionHeader("1. RESUMEN DE VENTAS E INGRESOS REALES", y)
            val totIng = if (data.totalIngresos > 0.0) data.totalIngresos else (data.ingresosProduccion + data.ingresosMercaderias)
            y = drawRow("Apertura de Jornada:", fechaAperturaStr, y)
            y = drawRow("Cierre de Jornada:", fechaCierreStr, y)
            y = drawRow("Responsable (DUEÑO):", data.duenoName.ifBlank { "DUEÑO" }, y)
            y = drawRow("Ventas de Producción Elaborada:", "$${"%.2f".format(data.ingresosProduccion - data.ingresosAgregados)} CUP", y, isBold = false, colorHex = "#15803D")
            if (data.ingresosAgregados > 0.0) {
                y = drawRow("Ventas de Agregados / Adicionales:", "$${"%.2f".format(data.ingresosAgregados)} CUP", y, isBold = false, colorHex = "#15803D")
            }
            y = drawRow("Ventas de Mercaderías (Local de Ventas):", "$${"%.2f".format(data.ingresosMercaderias)} CUP", y, isBold = false, colorHex = "#15803D")
            y = drawRow("TOTAL GENERAL DE INGRESOS REALES:", "$${"%.2f".format(totIng)} CUP", y, isBold = true, colorHex = "#15803D")
            y += 6f

            // 3. MOVIMIENTOS DE EFECTIVO
            y = drawSectionHeader("2. MOVIMIENTOS DE EFECTIVO Y ARQUEO", y)
            y = drawRow("Fondo Inicial de Caja:", "$${"%.2f".format(data.initialCash)} CUP", y)
            y = drawRow("(+) Total Ingresos por Ventas:", "$${"%.2f".format(totIng)} CUP", y, isBold = true, colorHex = "#15803D")
            y = drawRow("(-) Transferencias Recibidas (${data.transferenciasCount} op.):", "-$${"%.2f".format(data.transferenciasMonto)} CUP", y, colorHex = "#0284C7")
            val extrTxt = if (data.extraccionesNotas.isNotBlank()) "(-) Extracciones (${data.extraccionesNotas}):" else "(-) Extracciones de Caja:"
            y = drawRow(extrTxt, "-$${"%.2f".format(data.extracciones)} CUP", y, colorHex = "#D97706")
            y = drawRow("(=) EFECTIVO ESPERADO EN CAJA:", "$${"%.2f".format(data.efectivoEsperado)} CUP", y, isBold = true, colorHex = "#1E293B")
            y = drawRow("EFECTIVO REAL EN CAJA (CONTADO):", "$${"%.2f".format(data.efectivoReal)} CUP", y, isBold = true, colorHex = "#1E3A8A")

            val difColor = when {
                data.diferencia > 0.01 -> "#15803D"
                data.diferencia < -0.01 -> "#DC2626"
                else -> "#0F172A"
            }
            val difLabel = when {
                data.diferencia > 0.01 -> "DIFERENCIA DEL CUADRE (SOBRANTE):"
                data.diferencia < -0.01 -> "DIFERENCIA DEL CUADRE (FALTANTE):"
                else -> "DIFERENCIA DEL CUADRE (EXACTO):"
            }
            y = drawRow(difLabel, "$${"%.2f".format(data.diferencia)} CUP", y, isBold = true, colorHex = difColor)
            y += 6f

            // 4. COSTOS TEÓRICOS Y UTILIDAD TEÓRICA
            y = drawSectionHeader("3. COSTOS TEÓRICOS Y RESULTADO TEÓRICO", y)
            y = drawRow("Costo Teórico de Producción:", "$${"%.2f".format(data.costoProduccion)} CUP", y)
            if (data.costoAgregados > 0.0) {
                y = drawRow("Costo Teórico de Agregados:", "$${"%.2f".format(data.costoAgregados)} CUP", y)
            }
            y = drawRow("Costo Teórico de Mercaderías:", "$${"%.2f".format(data.costoMercaderias)} CUP", y)
            y = drawRow("TOTAL COSTOS TEÓRICOS:", "$${"%.2f".format(data.costoTotal)} CUP", y, isBold = true, colorHex = "#B91C1C")
            
            val utilColor = if (data.utilidadTeorica >= 0) "#15803D" else "#DC2626"
            y = drawRow("UTILIDAD TEÓRICA (Ingresos − Costos):", "$${"%.2f".format(data.utilidadTeorica)} CUP", y, isBold = true, colorHex = utilColor)

            // Nota Ficha de Costo
            y = checkNewPage(24f, y)
            paint.textSize = 8f
            paint.color = Color.parseColor("#64748B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("* Nota: Los pagos de personal calculados por Ficha de Costo forman parte del costo teórico unitario.", 40f, y, paint)
            y += 11f
            canvas.drawText("  No se suman como costo adicional para evitar duplicación contable.", 40f, y, paint)
            y += 16f

            // 5. PAGOS DE PERSONAL EJECUTADOS Y DINERO FINAL EN CAJA
            y = drawSectionHeader("4. PAGOS DE PERSONAL EJECUTADOS Y DINERO FINAL EN CAJA", y)
            val estadoPagosStr = if (data.pagosConfirmados) "PAGOS CONFIRMADOS Y DEDUCIDOS" else "CÁLCULO TEÓRICO / PENDIENTE"
            y = drawRow("Estado de Pagos:", estadoPagosStr, y, isBold = true, colorHex = if (data.pagosConfirmados) "#15803D" else "#D97706")
            y = drawRow("Total Pagado a COCINA:", "$${"%.2f".format(data.totalPagoCocina)} CUP", y)
            y = drawRow("Total Pagado a CAJERO:", "$${"%.2f".format(data.totalPagoCajero)} CUP", y)
            y = drawRow("Total Pagado a DEPENDIENTES:", "$${"%.2f".format(data.totalPagoDependientes)} CUP", y)
            y = drawRow("TOTAL GENERAL PAGOS REALES EJECUTADOS:", "-$${"%.2f".format(data.totalPagosPersonal)} CUP", y, isBold = true, colorHex = "#DC2626")
            
            // FÓRMULA DINERO FINAL EN CAJA
            y = checkNewPage(30f, y)
            paint.color = Color.parseColor("#F0FDF4")
            canvas.drawRect(35f, y - 10f, (pageWidth - 35).toFloat(), y + 16f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#86EFAC")
            canvas.drawRect(35f, y - 10f, (pageWidth - 35).toFloat(), y + 16f, paint)
            paint.style = Paint.Style.FILL

            paint.textSize = 10f
            paint.color = Color.parseColor("#14532D")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DINERO FINAL EN CAJA (Efectivo Contado − Pagos Reales):", 42f, y + 4f, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 12f
            canvas.drawText("$${"%.2f".format(data.dineroFinalEnCaja)} CUP", (pageWidth - 42).toFloat(), y + 4f, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 26f

            // 6. DETALLE DE PAGOS A COCINA, CAJERO Y DEPENDIENTES
            if (data.cocinaPagoRows.isNotEmpty() || data.cajeroPagoInfo != null || data.dependientesRows.isNotEmpty()) {
                y = drawSectionHeader("5. DESGLOSE Y DISTRIBUCIÓN INDIVIDUAL DE PAGOS", y)

                // Desglose Cocina
                if (data.cocinaPagoRows.isNotEmpty()) {
                    y = checkNewPage(30f, y)
                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("• Cocina (Producción por Ficha de Costo):", 40f, y, paint)
                    y += 12f

                    // Header table
                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(40f, y - 9f, (pageWidth - 40).toFloat(), y + 5f, paint)
                    paint.color = Color.parseColor("#334155")
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("PRODUCTO", 45f, y, paint)
                    canvas.drawText("VENDIBLE", 200f, y, paint)
                    canvas.drawText("TARIFA/U", 280f, y, paint)
                    canvas.drawText("COCINEROS", 370f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("TOTAL", (pageWidth - 45).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 12f

                    for (cRow in data.cocinaPagoRows) {
                        y = checkNewPage(16f, y)
                        paint.color = Color.parseColor("#0F172A")
                        paint.textSize = 8f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        val pName = if (cRow.productName.length > 22) cRow.productName.take(20) + ".." else cRow.productName
                        canvas.drawText(pName, 45f, y, paint)
                        canvas.drawText("${"%.1f".format(cRow.vendible)} ${cRow.unit}", 200f, y, paint)
                        canvas.drawText("$${"%.2f".format(cRow.pagoUnitario)}", 280f, y, paint)
                        canvas.drawText("${cRow.cantidadCocineros}", 370f, y, paint)
                        paint.textAlign = Paint.Align.RIGHT
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("$${"%.2f".format(cRow.totalPago)}", (pageWidth - 45).toFloat(), y, paint)
                        paint.textAlign = Paint.Align.LEFT
                        y += 12f
                    }
                    y += 4f
                }

                // Desglose Cajero
                if (data.cajeroPagoInfo != null) {
                    y = checkNewPage(24f, y)
                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("• Cajero:", 40f, y, paint)
                    y += 12f
                    y = drawRow("  - Pago por Producción vendida:", "$${"%.2f".format(data.cajeroPagoInfo.pagoProduccion)} CUP", y, leftPad = 48f)
                    y = drawRow("  - Pago por Bebidas / Mercaderías:", "$${"%.2f".format(data.cajeroPagoInfo.pagoMercaderias)} CUP", y, leftPad = 48f)
                    y = drawRow("  = Total Pagado al Cajero:", "$${"%.2f".format(data.cajeroPagoInfo.totalPago)} CUP", y, isBold = true, leftPad = 48f)
                    y += 4f
                }

                // Desglose Dependientes
                if (data.dependientesRows.isNotEmpty()) {
                    y = checkNewPage(30f, y)
                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("• Dependientes (Distribución Individual Confirmada):", 40f, y, paint)
                    y += 12f

                    // Header table
                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(40f, y - 9f, (pageWidth - 40).toFloat(), y + 5f, paint)
                    paint.color = Color.parseColor("#334155")
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("DEPENDIENTE", 45f, y, paint)
                    canvas.drawText("VTAS. PROD.", 180f, y, paint)
                    canvas.drawText("VTAS. BEB.", 270f, y, paint)
                    canvas.drawText("VTAS. TOT.", 360f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("MONTO PAGO", (pageWidth - 45).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 12f

                    for (dep in data.dependientesRows) {
                        y = checkNewPage(16f, y)
                        paint.color = Color.parseColor("#0F172A")
                        paint.textSize = 8f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        canvas.drawText(dep.name.ifBlank { "Dependiente ${dep.id}" }, 45f, y, paint)
                        canvas.drawText("${"%.1f".format(dep.ventasProduccion)} u", 180f, y, paint)
                        canvas.drawText("${"%.1f".format(dep.ventasBebidas)} u", 270f, y, paint)
                        val totV = if (dep.ventasTotales > 0.0) dep.ventasTotales else (dep.ventasProduccion + dep.ventasBebidas)
                        canvas.drawText("${"%.1f".format(totV)} u", 360f, y, paint)
                        paint.textAlign = Paint.Align.RIGHT
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("$${"%.2f".format(dep.montoPago)} CUP", (pageWidth - 45).toFloat(), y, paint)
                        paint.textAlign = Paint.Align.LEFT
                        y += 12f
                    }
                    y += 6f
                }
            }

            // 7. TABLA DETALLADA DE PRODUCCIÓN
            y = drawSectionHeader("6. DESGLOSE DETALLADO DE PRODUCCIÓN (TANDAS)", y)
            if (data.produccionRows.isEmpty()) {
                paint.textSize = 9f
                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No se registraron tandas de producción en esta jornada.", 40f, y, paint)
                y += 16f
            } else {
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(35f, y - 9f, (pageWidth - 35).toFloat(), y + 5f, paint)

                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PRODUCTO", 40f, y, paint)
                canvas.drawText("TANDAS", 160f, y, paint)
                canvas.drawText("PROD.", 210f, y, paint)
                canvas.drawText("MERMAS", 270f, y, paint)
                canvas.drawText("VENDIBLE", 355f, y, paint)
                canvas.drawText("PRECIO", 430f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TOTAL", (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += 12f

                for (row in data.produccionRows) {
                    y = checkNewPage(16f, y)
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val prodName = if (row.productName.length > 22) row.productName.take(20) + ".." else row.productName
                    canvas.drawText(prodName, 40f, y, paint)
                    canvas.drawText("${row.tandasCount}", 160f, y, paint)
                    canvas.drawText("${"%.1f".format(row.totalProduced)} ${row.unit}", 210f, y, paint)
                    val mermaTxt = "${"%.1f".format(row.defectuoso + row.consumo + row.regalia)} (D:${row.defectuoso.toInt()} C:${row.consumo.toInt()} R:${row.regalia.toInt()})"
                    canvas.drawText(mermaTxt, 270f, y, paint)
                    canvas.drawText("${"%.1f".format(row.vendible)}", 355f, y, paint)
                    canvas.drawText("$${"%.2f".format(row.price)}", 430f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(row.ingresoEstimado)}", (pageWidth - 40).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 12f
                }
                y += 6f
            }

            // 8. TABLA DETALLADA DE MERCADERÍAS
            y = drawSectionHeader("7. DESGLOSE DETALLADO DE MERCADERÍAS", y)
            if (data.mercaderiaRows.isEmpty()) {
                paint.textSize = 9f
                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No hay mercaderías configuradas o vendidas en esta jornada.", 40f, y, paint)
                y += 16f
            } else {
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(35f, y - 9f, (pageWidth - 35).toFloat(), y + 5f, paint)

                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PRODUCTO", 40f, y, paint)
                canvas.drawText("INICIAL", 155f, y, paint)
                canvas.drawText("FINAL", 210f, y, paint)
                canvas.drawText("MERMAS", 265f, y, paint)
                canvas.drawText("VENTAS", 350f, y, paint)
                canvas.drawText("PRECIO", 425f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TOTAL", (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += 12f

                for (row in data.mercaderiaRows) {
                    y = checkNewPage(16f, y)
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val prodName = if (row.productName.length > 20) row.productName.take(18) + ".." else row.productName
                    canvas.drawText(prodName, 40f, y, paint)
                    canvas.drawText("${"%.1f".format(row.existenciaInicial)}", 155f, y, paint)
                    canvas.drawText("${"%.1f".format(row.existenciaFinal)}", 210f, y, paint)
                    val mermaTxt = "${"%.1f".format(row.defectuoso + row.consumo + row.regalia)} (D:${row.defectuoso.toInt()} C:${row.consumo.toInt()} R:${row.regalia.toInt()})"
                    canvas.drawText(mermaTxt, 265f, y, paint)
                    canvas.drawText("${"%.1f".format(row.vendidas)}", 350f, y, paint)
                    canvas.drawText("$${"%.2f".format(row.price)}", 425f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(row.ingresoEstimado)}", (pageWidth - 40).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 12f
                }
                y += 6f
            }

            // 9. TABLA DE AGREGADOS (si existen)
            if (data.agregadoRows.isNotEmpty()) {
                y = drawSectionHeader("8. DESGLOSE DE AGREGADOS Y ADICIONALES", y)
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(35f, y - 9f, (pageWidth - 35).toFloat(), y + 5f, paint)

                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("AGREGADO", 40f, y, paint)
                canvas.drawText("ENVIADAS", 160f, y, paint)
                canvas.drawText("VENDIDAS", 230f, y, paint)
                canvas.drawText("REGALÍAS", 300f, y, paint)
                canvas.drawText("SOBRANTES", 370f, y, paint)
                canvas.drawText("PRECIO", 440f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TOTAL", (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += 12f

                for (ag in data.agregadoRows) {
                    y = checkNewPage(16f, y)
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val agName = if (ag.name.length > 20) ag.name.take(18) + ".." else ag.name
                    canvas.drawText(agName, 40f, y, paint)
                    canvas.drawText("${"%.1f".format(ag.enviadas)}", 160f, y, paint)
                    canvas.drawText("${"%.1f".format(ag.vendidas)}", 230f, y, paint)
                    canvas.drawText("${"%.1f".format(ag.regalia)}", 300f, y, paint)
                    canvas.drawText("${"%.1f".format(ag.sobrantes)}", 370f, y, paint)
                    canvas.drawText("$${"%.2f".format(ag.price)}", 440f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(ag.ingresoEstimado)}", (pageWidth - 40).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 12f
                }
                y += 6f
            }

            if (data.notas.isNotBlank()) {
                y = checkNewPage(30f, y)
                paint.textSize = 9f
                paint.color = Color.parseColor("#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("Observaciones del Cuadre: ${data.notas}", 40f, y, paint)
                y += 16f
            }

            // Signatures
            y = checkNewPage(80f, y)
            y += 24f
            val sigLineWidth = 180f
            canvas.drawLine(50f, y, 50f + sigLineWidth, y, linePaint)
            canvas.drawLine((pageWidth - 50 - sigLineWidth).toFloat(), y, (pageWidth - 50).toFloat(), y, linePaint)
            y += 12f

            paint.textSize = 8.5f
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Firma del DUEÑO", 90f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Firma de Conformidad / Cajero", (pageWidth - 80).toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            // Save PDF file
            val outputDir = File(context.cacheDir, "cuadre_reports").apply { if (!exists()) mkdirs() }
            val fileName = "Cuadre_Caja_Jornada_${data.jornadaId}_${System.currentTimeMillis()}.pdf"
            val file = File(outputDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    fun shareCuadreCajaReport(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe de Cuadre de Caja")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el comprobante oficial de Cuadre de Caja de la Jornada.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir / Descargar PDF de Cuadre"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No se pudo compartir el archivo PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
