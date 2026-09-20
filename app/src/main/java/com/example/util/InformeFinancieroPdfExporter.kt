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

object InformeFinancieroPdfExporter {

    data class InformeFinancieroData(
        val negocio: String,
        val dueno: String,
        val dvc: String,
        val jornada: String,
        val fecha: String,
        val ventasProduccion: Double,
        val costosProduccion: Double,
        val resultadoProduccion: Double,
        val ventasMercaderia: Double,
        val costosMercaderia: Double,
        val resultadoMercaderia: Double,
        val ventasTotales: Double,
        val costosTotales: Double,
        val utilidad: Double,
        val gastos: Double,
        val inversiones: Double,
        val extracciones: Double,
        val liquidezFinal: Double
    )

    fun exportAndShareInformeFinancieroPdf(
        context: Context,
        data: InformeFinancieroData
    ): File? {
        try {
            val pageWidth = 595
            val pageHeight = 842
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            val navyColor = Color.parseColor("#1B2A4A")
            val goldColor = Color.parseColor("#C89D3C")
            val darkSlate = Color.parseColor("#334155")
            val lightBg = Color.parseColor("#F8FAFC")
            val greenColor = Color.parseColor("#047857")
            val redColor = Color.parseColor("#DC2626")
            val borderColor = Color.parseColor("#CBD5E1")

            var yPos = 40f

            // Header Banner
            paint.color = navyColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 60f, paint)

            paint.color = goldColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, yPos + 56f, (pageWidth - 30).toFloat(), yPos + 60f, paint)

            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("EL QADRE - INFORME FINANCIERO DE JORNADA", 45f, yPos + 35f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val datePrint = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generado el: $datePrint", (pageWidth - 200).toFloat(), yPos + 35f, paint)

            yPos += 75f

            // Encabezado General (Negocio, Dueño, DVC, Jornada, Fecha)
            paint.color = lightBg
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 70f, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 70f, paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 11f

            // Line 1: Negocio & Dueño
            paint.color = darkSlate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("NEGOCIO:", 45f, yPos + 22f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.BLACK
            canvas.drawText(data.negocio, 115f, yPos + 22f, paint)

            paint.color = darkSlate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DUEÑO:", 320f, yPos + 22f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.BLACK
            canvas.drawText(data.dueno, 380f, yPos + 22f, paint)

            // Line 2: DVC, Jornada, Fecha
            paint.color = darkSlate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DVC:", 45f, yPos + 48f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.BLACK
            canvas.drawText(data.dvc, 115f, yPos + 48f, paint)

            paint.color = darkSlate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("JORNADA:", 220f, yPos + 48f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.BLACK
            canvas.drawText(data.jornada, 290f, yPos + 48f, paint)

            paint.color = darkSlate
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("FECHA:", 390f, yPos + 48f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.BLACK
            canvas.drawText(data.fecha, 445f, yPos + 48f, paint)

            yPos += 85f

            // Helper function to draw sections
            fun drawSectionHeader(title: String) {
                paint.color = navyColor
                paint.style = Paint.Style.FILL
                canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 24f, paint)

                paint.color = Color.WHITE
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(title, 45f, yPos + 16f, paint)
                yPos += 24f
            }

            fun drawDataRow(concept: String, amount: Double, isBold: Boolean = false, colorOverride: Int? = null, prefix: String = "$") {
                paint.color = if (isBold) lightBg else Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 22f, paint)

                paint.color = borderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 22f, paint)

                paint.style = Paint.Style.FILL
                paint.color = if (isBold) navyColor else darkSlate
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(concept, 45f, yPos + 15f, paint)

                val valStr = "$prefix${"%.2f".format(amount)} CUP"
                paint.color = colorOverride ?: (if (isBold) navyColor else Color.BLACK)
                paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                val textWidth = paint.measureText(valStr)
                canvas.drawText(valStr, (pageWidth - 45 - textWidth), yPos + 15f, paint)

                yPos += 22f
            }

            // 1. COSTOS Y VENTAS DE PRODUCCIÓN
            drawSectionHeader("1. COSTOS Y VENTAS DE PRODUCCIÓN")
            drawDataRow("Ventas de Producción", data.ventasProduccion)
            drawDataRow("Costos de Producción", data.costosProduccion)
            val resProdColor = if (data.resultadoProduccion >= 0) greenColor else redColor
            drawDataRow("Resultado de Producción", data.resultadoProduccion, isBold = true, colorOverride = resProdColor)

            yPos += 12f

            // 2. COSTOS Y VENTAS DE MERCADERÍA
            drawSectionHeader("2. COSTOS Y VENTAS DE MERCADERÍA")
            drawDataRow("Ventas de Mercadería", data.ventasMercaderia)
            drawDataRow("Costos de Mercadería", data.costosMercaderia)
            val resMercColor = if (data.resultadoMercaderia >= 0) greenColor else redColor
            drawDataRow("Resultado de Mercadería", data.resultadoMercaderia, isBold = true, colorOverride = resMercColor)

            yPos += 12f

            // 3. TOTALES CONSOLIDADOS
            drawSectionHeader("3. TOTALES")
            drawDataRow("Ventas totales", data.ventasTotales, isBold = true)
            drawDataRow("Costos totales", data.costosTotales, isBold = true)
            val resTotal = data.ventasTotales - data.costosTotales
            val resTotColor = if (resTotal >= 0) greenColor else redColor
            drawDataRow("Resultado total (Margen Operativo Bruto)", resTotal, isBold = true, colorOverride = resTotColor)

            yPos += 12f

            // 4. GASTOS E INVERSIONES Y UTILIDAD
            drawSectionHeader("4. GASTOS, INVERSIONES Y UTILIDAD")
            drawDataRow("Gastos corrientes del día", data.gastos)
            drawDataRow("Inversiones (Depreciación del día)", data.inversiones)
            val utilColor = if (data.utilidad >= 0) greenColor else redColor
            drawDataRow("Utilidad de la Jornada", data.utilidad, isBold = true, colorOverride = utilColor)

            yPos += 12f

            // 5. EXTRACCIONES Y LIQUIDEZ FINAL
            drawSectionHeader("5. EXTRACCIONES Y LIQUIDEZ")
            drawDataRow("Extracciones realizadas de la jornada", data.extracciones, isBold = false, colorOverride = if (data.extracciones > 0) redColor else darkSlate)
            val liqColor = if (data.liquidezFinal >= 0) greenColor else redColor
            drawDataRow("LIQUIDEZ FINAL DISPONIBLE", data.liquidezFinal, isBold = true, colorOverride = liqColor)

            yPos += 30f

            // Signature / Verification Block
            paint.color = lightBg
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 55f, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(30f, yPos, (pageWidth - 30).toFloat(), yPos + 55f, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkSlate
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Informe Financiero emitido oficialmente por el sistema ElQadre.", 45f, yPos + 22f, paint)
            canvas.drawText("Certifica la correspondencia económica inmutable de la jornada con los registros del Dueño.", 45f, yPos + 38f, paint)

            pdfDocument.finishPage(page)

            // Save PDF
            val sanitizedDate = data.fecha.replace("/", "-").replace(":", "-").replace(" ", "_")
            val fileName = "Informe_Financiero_Jornada_${data.jornada.replace("#", "").replace(" ", "_")}_$sanitizedDate.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            sharePdfFile(context, file)
            return file
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar informe financiero PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun sharePdfFile(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe Financiero de Jornada - El Qadre")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el Informe Financiero de la jornada generado en ElQadre.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Informe Financiero (PDF)"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
