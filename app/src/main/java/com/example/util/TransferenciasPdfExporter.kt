package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.Transferencia
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransferenciasPdfExporter {

    fun exportTransferenciasReport(
        context: Context,
        transferencias: List<Transferencia>,
        filterLabel: String,
        generatedBy: String
    ): File? {
        try {
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

            fun drawHeaderAndMeta(currentCanvas: Canvas, pNum: Int): Float {
                // Background
                paint.color = Color.WHITE
                currentCanvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

                // Header Banner
                paint.color = Color.parseColor("#1E293B") // ElQadreNavy
                currentCanvas.drawRect(36f, 36f, 559f, 105f, paint)

                // Title inside Banner
                paint.color = Color.parseColor("#F59E0B") // ElQadreGold
                paint.textSize = 18f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                currentCanvas.drawText("EL QADRE", 52f, 68f, paint)

                paint.color = Color.WHITE
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                currentCanvas.drawText("INFORME DE TRANSFERENCIAS RECIBIDAS", 52f, 88f, paint)

                // Right side: Date & Filter
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 9.5f
                currentCanvas.drawText("Emisión: ${dateFormatter.format(Date())}", 545f, 65f, paint)
                currentCanvas.drawText("Cajero: $generatedBy", 545f, 80f, paint)
                currentCanvas.drawText("Filtro: $filterLabel", 545f, 95f, paint)
                paint.textAlign = Paint.Align.LEFT

                var y = 125f

                if (pNum == 1) {
                    // Summary Box
                    val totalAmount = transferencias.sumOf { it.amount }
                    val countTotal = transferencias.size
                    val countSms = transferencias.count { !it.isManual }
                    val countManual = transferencias.count { it.isManual }

                    // Summary Background
                    paint.color = Color.parseColor("#F8FAFC")
                    currentCanvas.drawRoundRect(36f, y, 559f, y + 60f, 8f, 8f, paint)
                    paint.color = Color.parseColor("#E2E8F0")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    currentCanvas.drawRoundRect(36f, y, 559f, y + 60f, 8f, 8f, paint)
                    paint.style = Paint.Style.FILL

                    // Summary Content
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 10.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentCanvas.drawText("RESUMEN DEL PERÍODO ($filterLabel)", 48f, y + 18f, paint)

                    paint.textSize = 9f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.color = Color.parseColor("#475569")

                    // Column 1
                    currentCanvas.drawText("Total Recibido:", 48f, y + 38f, paint)
                    paint.color = Color.parseColor("#0369A1")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentCanvas.drawText("$${"%.2f".format(totalAmount)} CUP ($countTotal transferencias)", 130f, y + 38f, paint)

                    // Column 2
                    paint.color = Color.parseColor("#475569")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    currentCanvas.drawText("Detección SMS / Manual:", 330f, y + 38f, paint)
                    paint.color = Color.parseColor("#0F172A")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    currentCanvas.drawText("$countSms SMS / $countManual Manual", 445f, y + 38f, paint)

                    y += 75f
                }

                // Table Header Bar
                paint.color = Color.parseColor("#334155")
                currentCanvas.drawRect(36f, y, 559f, y + 20f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                currentCanvas.drawText("NRO. TRANSACCIÓN", 42f, y + 13f, paint)
                currentCanvas.drawText("FECHA/HORA", 160f, y + 13f, paint)
                currentCanvas.drawText("ORIGEN", 250f, y + 13f, paint)
                currentCanvas.drawText("TITULAR / TELÉFONO", 310f, y + 13f, paint)
                paint.textAlign = Paint.Align.RIGHT
                currentCanvas.drawText("IMPORTE", 552f, y + 13f, paint)
                paint.textAlign = Paint.Align.LEFT

                y += 26f
                return y
            }

            var currentY = drawHeaderAndMeta(canvas, pageNumber)

            if (transferencias.isEmpty()) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("No existen transferencias para el filtro seleccionado ($filterLabel).", 297.5f, currentY + 30f, paint)
                paint.textAlign = Paint.Align.LEFT
            } else {
                transferencias.forEachIndexed { index, tx ->
                    // Check if new page needed
                    if (currentY > 780f) {
                        // Footer on current page
                        paint.color = Color.parseColor("#94A3B8")
                        paint.textSize = 8f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("Página $pageNumber", 297.5f, 825f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        pdfDocument.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = drawHeaderAndMeta(canvas, pageNumber)
                    }

                    // Row Zebra Background
                    if (index % 2 == 1) {
                        paint.color = Color.parseColor("#F8FAFC")
                        canvas.drawRect(36f, currentY - 8f, 559f, currentY + 22f, paint)
                    }

                    // Transaction Number
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(tx.transactionNumber, 42f, currentY + 2f, paint)

                    // Date
                    val dateFormatted = if (tx.smsDate.isNotBlank()) tx.smsDate else dateOnlyFormatter.format(Date(tx.receivedAt))
                    val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tx.receivedAt))
                    paint.color = Color.parseColor("#475569")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("$dateFormatted $timeFormatted", 160f, currentY + 2f, paint)

                    // Origin
                    val originLabel = if (tx.isManual || tx.source == "MANUAL_EXTERNA") "MANUAL" else "SMS"
                    paint.color = if (tx.isManual) Color.parseColor("#7C3AED") else Color.parseColor("#0284C7")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(originLabel, 250f, currentY + 2f, paint)

                    // Titular / Phone
                    val titularDisplay = when {
                        tx.titularName.isNotBlank() -> tx.titularName + (if (tx.phoneNumber.isNotBlank()) " (${tx.phoneNumber})" else "")
                        tx.phoneNumber.isNotBlank() -> "Tel: ${tx.phoneNumber}"
                        tx.recipientAccount.isNotBlank() -> "Cta: ...${tx.recipientAccount.takeLast(6)}"
                        else -> "N/D"
                    }
                    val safeTitular = if (titularDisplay.length > 30) titularDisplay.take(28) + "..." else titularDisplay
                    paint.color = Color.parseColor("#334155")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(safeTitular, 310f, currentY + 2f, paint)

                    // Amount
                    paint.color = Color.parseColor("#0F172A")
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$${"%.2f".format(tx.amount)} CUP", 552f, currentY + 2f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    // Subtle separator line
                    currentY += 18f
                    canvas.drawLine(36f, currentY, 559f, currentY, linePaint)
                    currentY += 10f
                }
            }

            // Page Number on last page
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 8f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Página $pageNumber - El Qadre Sistema de Gestión", 297.5f, 825f, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            val safeFilter = filterLabel.replace("/", "-").replace(" ", "_")
            val fileName = "Informe_Transferencias_${safeFilter}_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF de transferencias: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    fun shareTransferenciasReport(context: Context, file: File, filterLabel: String, ownerPhone: String) {
        val cleanPhone = ownerPhone.replace("+", "").replace(" ", "").replace("-", "").trim()
        if (cleanPhone.isBlank()) {
            Toast.makeText(
                context,
                "No existe destinatario configurado. El Administrador debe configurar el número del dueño en Ajustes.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val pdfUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Informe de Transferencias - El Qadre ($filterLabel)"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Estimado Dueño, se adjunta el Informe de Transferencias de El Qadre.\nFiltro: $filterLabel"
                )
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Enviar Informe al Dueño ($cleanPhone)...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir informe: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
