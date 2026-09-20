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

    data class CuadreCajaReportData(
        val businessName: String,
        val duenoName: String,
        val jornadaId: Long,
        val openedAt: Long,
        val closedAt: Long = System.currentTimeMillis(),
        val initialCash: Double,
        val ingresosProduccion: Double,
        val ingresosMercaderias: Double,
        val mermasTotalValor: Double,
        val transferenciasMonto: Double,
        val transferenciasCount: Int,
        val extracciones: Double,
        val extraccionesNotas: String = "",
        val efectivoEsperado: Double,
        val efectivoReal: Double,
        val diferencia: Double,
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
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            fun drawHeader() {
                // Background
                paint.color = Color.WHITE
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

                // Header Banner
                paint.color = Color.parseColor("#1E293B") // ElQadreNavy
                canvas.drawRect(30f, 30f, (pageWidth - 30).toFloat(), 95f, paint)

                // Title inside Banner
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(data.businessName.ifBlank { "EL QADRE" }.uppercase(), 45f, 58f, paint)

                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("CUADRE DE CAJA DEL DUEÑO", 45f, 78f, paint)

                // Right side of banner
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 9f
                val fechaJornadaStr = if (data.openedAt > 0) dateOnlyFormatter.format(Date(data.openedAt)) else dateOnlyFormatter.format(Date())
                canvas.drawText("Jornada: #${data.jornadaId} | Fecha: $fechaJornadaStr", (pageWidth - 45).toFloat(), 58f, paint)
                canvas.drawText("Emisión: ${dateFormatter.format(Date())}", (pageWidth - 45).toFloat(), 78f, paint)
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
                    return 115f
                }
                return curY
            }

            drawHeader()
            var y = 115f

            // Section 1: RESUMEN DE LA JORNADA
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("RESUMEN DE CAJA Y JORNADA", 35f, y, paint)
            y += 6f
            canvas.drawLine(35f, y, (pageWidth - 35).toFloat(), y, linePaint)
            y += 16f

            val rowH = 15f
            fun drawSummaryRow(label: String, value: String, isBold: Boolean = false, colorHex: String = "#0F172A") {
                paint.textSize = 9.5f
                paint.color = Color.parseColor(if (isBold) colorHex else "#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(label, 40f, y, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.color = Color.parseColor(colorHex)
                canvas.drawText(value, (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += rowH
            }

            val fechaAperturaStr = if (data.openedAt > 0) dateFormatter.format(Date(data.openedAt)) else "No disponible"
            val fechaCierreStr = dateFormatter.format(Date(data.closedAt))
            drawSummaryRow("Apertura de Jornada:", fechaAperturaStr)
            drawSummaryRow("Cierre de Jornada:", fechaCierreStr)
            drawSummaryRow("Responsable (DUEÑO):", data.duenoName.ifBlank { "DUEÑO" })
            drawSummaryRow("Fondo Inicial de Caja:", "$${"%.2f".format(data.initialCash)} CUP")
            drawSummaryRow("(+) Ingresos Esperados de Producción:", "$${"%.2f".format(data.ingresosProduccion)} CUP", isBold = true, colorHex = "#15803D")
            drawSummaryRow("(+) Ingresos Esperados de Mercaderías:", "$${"%.2f".format(data.ingresosMercaderias)} CUP", isBold = true, colorHex = "#15803D")
            drawSummaryRow("(-) Mermas Totales (Valor):", "$${"%.2f".format(data.mermasTotalValor)} CUP", isBold = false, colorHex = "#B91C1C")
            drawSummaryRow("(-) Transferencias Recibidas (${data.transferenciasCount}):", "$${"%.2f".format(data.transferenciasMonto)} CUP", isBold = false, colorHex = "#0284C7")
            val extrTxt = if (data.extraccionesNotas.isNotBlank()) "(-) Extracciones (${data.extraccionesNotas}):" else "(-) Extracciones:"
            drawSummaryRow(extrTxt, "$${"%.2f".format(data.extracciones)} CUP", isBold = false, colorHex = "#D97706")

            y += 4f
            canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
            y += 14f

            drawSummaryRow("(=) EFECTIVO ESPERADO EN CAJA:", "$${"%.2f".format(data.efectivoEsperado)} CUP", isBold = true, colorHex = "#1E293B")
            drawSummaryRow("EFECTIVO REAL EN CAJA:", "$${"%.2f".format(data.efectivoReal)} CUP", isBold = true, colorHex = "#1E3A8A")

            val difColor = when {
                data.diferencia > 0.01 -> "#15803D"
                data.diferencia < -0.01 -> "#DC2626"
                else -> "#0F172A"
            }
            val difLabel = when {
                data.diferencia > 0.01 -> "DIFERENCIA (SOBRANTE):"
                data.diferencia < -0.01 -> "DIFERENCIA (FALTANTE):"
                else -> "DIFERENCIA (CUADRE EXACTO):"
            }
            drawSummaryRow(difLabel, "$${"%.2f".format(data.diferencia)} CUP", isBold = true, colorHex = difColor)

            if (data.notas.isNotBlank()) {
                y += 4f
                paint.textSize = 9f
                paint.color = Color.parseColor("#475569")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("Notas: ${data.notas}", 40f, y, paint)
                y += 14f
            }

            y += 10f

            // Section 2: DESGLOSE DE PRODUCCIÓN
            y = checkNewPage(120f, y)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("PRODUCCIÓN (Tandas e Ingresos Estimados)", 35f, y, paint)
            y += 6f
            canvas.drawLine(35f, y, (pageWidth - 35).toFloat(), y, linePaint)
            y += 14f

            if (data.produccionRows.isEmpty()) {
                paint.textSize = 9f
                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No se registraron tandas de producción en esta jornada.", 40f, y, paint)
                y += 18f
            } else {
                // Table header
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(35f, y - 10f, (pageWidth - 35).toFloat(), y + 6f, paint)

                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PRODUCTO", 40f, y, paint)
                canvas.drawText("TANDAS", 170f, y, paint)
                canvas.drawText("PROD.", 220f, y, paint)
                canvas.drawText("MERMA", 280f, y, paint)
                canvas.drawText("VENDIBLE", 360f, y, paint)
                canvas.drawText("PRECIO", 430f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TOTAL", (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += 14f

                for (row in data.produccionRows) {
                    y = checkNewPage(20f, y)
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val prodName = if (row.productName.length > 22) row.productName.take(20) + ".." else row.productName
                    canvas.drawText(prodName, 40f, y, paint)
                    canvas.drawText("${row.tandasCount}", 170f, y, paint)
                    canvas.drawText("${"%.1f".format(row.totalProduced)} ${row.unit}", 220f, y, paint)
                    val mermaTxt = "${"%.1f".format(row.defectuoso + row.consumo + row.regalia)} (D:${row.defectuoso.toInt()} C:${row.consumo.toInt()} R:${row.regalia.toInt()})"
                    canvas.drawText(mermaTxt, 280f, y, paint)
                    canvas.drawText("${"%.1f".format(row.vendible)}", 360f, y, paint)
                    canvas.drawText("$${"%.2f".format(row.price)}", 430f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(row.ingresoEstimado)}", (pageWidth - 40).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 14f
                }
                y += 6f
            }

            // Section 3: DESGLOSE DE MERCADERÍAS (LOCAL DE VENTAS)
            y = checkNewPage(120f, y)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("MERCADERÍAS (Local de Ventas)", 35f, y, paint)
            y += 6f
            canvas.drawLine(35f, y, (pageWidth - 35).toFloat(), y, linePaint)
            y += 14f

            if (data.mercaderiaRows.isEmpty()) {
                paint.textSize = 9f
                paint.color = Color.parseColor("#64748B")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No hay mercaderías configuradas o vendidas en esta jornada.", 40f, y, paint)
                y += 18f
            } else {
                // Table header
                paint.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(35f, y - 10f, (pageWidth - 35).toFloat(), y + 6f, paint)

                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("PRODUCTO", 40f, y, paint)
                canvas.drawText("INICIAL", 160f, y, paint)
                canvas.drawText("FINAL", 215f, y, paint)
                canvas.drawText("MERMA", 270f, y, paint)
                canvas.drawText("VENTAS", 355f, y, paint)
                canvas.drawText("PRECIO", 425f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TOTAL", (pageWidth - 40).toFloat(), y, paint)
                paint.textAlign = Paint.Align.LEFT
                y += 14f

                for (row in data.mercaderiaRows) {
                    y = checkNewPage(20f, y)
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val prodName = if (row.productName.length > 20) row.productName.take(18) + ".." else row.productName
                    canvas.drawText(prodName, 40f, y, paint)
                    canvas.drawText("${"%.1f".format(row.existenciaInicial)}", 160f, y, paint)
                    canvas.drawText("${"%.1f".format(row.existenciaFinal)}", 215f, y, paint)
                    val mermaTxt = "${"%.1f".format(row.defectuoso + row.consumo + row.regalia)} (D:${row.defectuoso.toInt()} C:${row.consumo.toInt()} R:${row.regalia.toInt()})"
                    canvas.drawText(mermaTxt, 270f, y, paint)
                    canvas.drawText("${"%.1f".format(row.vendidas)}", 355f, y, paint)
                    canvas.drawText("$${"%.2f".format(row.price)}", 425f, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(row.ingresoEstimado)}", (pageWidth - 40).toFloat(), y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    y += 14f
                }
                y += 6f
            }

            // Signatures
            y = checkNewPage(90f, y)
            y += 20f
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
