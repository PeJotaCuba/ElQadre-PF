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
import com.example.data.local.model.ConsumoPersonalItem
import com.example.data.local.model.OrderItem
import com.example.data.local.model.TableOrder
import com.example.data.local.model.Jornada
import com.example.data.local.model.Transferencia
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CajaReportPdfExporter {

    fun exportCajaReport(
        context: Context,
        activeJornada: Jornada,
        closedOrders: List<TableOrder>,
        orderItems: List<OrderItem>,
        currentCajero: String,
        consumoPersonalItems: List<ConsumoPersonalItem> = emptyList(),
        transferencias: List<Transferencia> = emptyList()
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            var pageCount = 1
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageCount).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            val paint = Paint()
            val linePaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Background
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, 595f, 842f, paint)

            // Header Banner
            paint.color = Color.parseColor("#1E293B") // ElQadreNavy
            canvas.drawRect(40f, 40f, 555f, 120f, paint)

            // Business Name / Title inside Banner
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("EL QADRE", 60f, 75f, paint)

            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Informe de Cierre de Caja / Jornada", 60f, 95f, paint)

            // Right side of banner: Date
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 10f
            canvas.drawText("Fecha: ${dateOnlyFormatter.format(Date())}", 535f, 75f, paint)
            canvas.drawText("Jornada ID: #${activeJornada.id}", 535f, 95f, paint)
            paint.textAlign = Paint.Align.LEFT // Reset alignment

            // General Info Block
            var currentY = 150f
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DATOS DE LA JORNADA", 40f, currentY, paint)
            currentY += 10f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 20f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 10f
            paint.color = Color.parseColor("#475569")

            // Grid for General Info
            canvas.drawText("Cajero de Apertura:", 40f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(activeJornada.openedBy, 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Fondo Inicial:", 320f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(activeJornada.initialCash)} CUP", 440f, currentY, paint)

            currentY += 20f
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Apertura:", 40f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(dateFormatter.format(Date(activeJornada.openedAt)), 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Cierre:", 320f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val cierreStr = if (activeJornada.closedAt != null) dateFormatter.format(Date(activeJornada.closedAt)) else "Activo (Turno Abierto)"
            canvas.drawText(cierreStr, 440f, currentY, paint)

            currentY += 35f

            // Summary Financials
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("RESUMEN DE OPERACIONES", 40f, currentY, paint)
            currentY += 10f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 20f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 10f
            paint.color = Color.parseColor("#475569")

            val totalSales = closedOrders.sumOf { it.totalAmount }
            val totalCocina = closedOrders.sumOf { it.totalCocina }
            val totalBarra = closedOrders.sumOf { it.totalBarra }
            val totalEfectivo = closedOrders.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.totalAmount }
            val totalTransferencia = closedOrders.filter { it.paymentMethod == "TRANSFERENCIA" }.sumOf { it.totalAmount }
            val totalPropinas = closedOrders.sumOf { it.tip }

            // Financial Metrics Table
            canvas.drawText("Comandas Cobradas:", 40f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("${closedOrders.size}", 180f, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Ventas Cocina:", 320f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("$${"%.2f".format(totalCocina)} CUP", 440f, currentY, paint)

            currentY += 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Cobrado en Efectivo:", 40f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("$${"%.2f".format(totalEfectivo)} CUP", 180f, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Ventas Barra:", 320f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("$${"%.2f".format(totalBarra)} CUP", 440f, currentY, paint)

            currentY += 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Cobrado Transferencia:", 40f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            canvas.drawText("$${"%.2f".format(totalTransferencia)} CUP", 180f, currentY, paint)

            val totalConsumoPersonalPdf = consumoPersonalItems.sumOf { it.totalAmount }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Propinas Totales:", 320f, currentY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#10B981") // Emerald600
            canvas.drawText("$${"%.2f".format(totalPropinas)} CUP", 440f, currentY, paint)

            if (totalConsumoPersonalPdf > 0) {
                currentY += 20f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#B45309") // Amber Text
                canvas.drawText("Consumo Real Personal:", 40f, currentY, paint)
                canvas.drawText("-$${"%.2f".format(totalConsumoPersonalPdf)} CUP", 180f, currentY, paint)
            }

            currentY += 30f
            // Accent bar for Total Sales
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRect(40f, currentY, 555f, currentY + 32f, paint)
            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("TOTAL FACTURADO NETO:", 50f, currentY + 20f, paint)
            paint.textSize = 13f
            paint.color = Color.parseColor("#1E293B")
            canvas.drawText("$${"%.2f".format(totalSales)} CUP", 440f, currentY + 20f, paint)

            currentY += 45f

            if (consumoPersonalItems.isNotEmpty()) {
                paint.color = Color.parseColor("#B45309")
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("CONSUMO DE PERSONAL REGISTRADO", 40f, currentY, paint)
                currentY += 10f
                canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
                currentY += 18f

                consumoPersonalItems.forEach { item ->
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("${item.productName} (x${item.quantity}) - P.Unit: $${"%.2f".format(item.unitPrice)} CUP", 40f, currentY, paint)
                    paint.color = Color.parseColor("#D97706")
                    canvas.drawText("-$${"%.2f".format(item.totalAmount)} CUP", 440f, currentY, paint)
                    currentY += 14f
                }
                currentY += 10f
            }

            // Transferencias Recibidas Section
            if (transferencias.isNotEmpty()) {
                if (currentY > 680f) {
                    pdfDocument.finishPage(page)
                    pageCount++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageCount).create())
                    canvas = page.canvas
                    paint.color = Color.WHITE
                    canvas.drawRect(0f, 0f, 595f, 842f, paint)
                    currentY = 50f
                }

                paint.color = Color.parseColor("#0284C7") // Sky Blue 600
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TRANSFERENCIAS RECIBIDAS (SMS)", 40f, currentY, paint)
                
                val totalTransfersAmount = transferencias.sumOf { it.amount }
                paint.color = Color.parseColor("#0369A1")
                paint.textSize = 10f
                canvas.drawText("Total: $${"%.2f".format(totalTransfersAmount)} CUP (${transferencias.size})", 410f, currentY, paint)
                
                currentY += 10f
                canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
                currentY += 18f

                transferencias.forEach { tx ->
                    if (currentY > 750f) {
                        pdfDocument.finishPage(page)
                        pageCount++
                        page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageCount).create())
                        canvas = page.canvas
                        paint.color = Color.WHITE
                        canvas.drawRect(0f, 0f, 595f, 842f, paint)
                        currentY = 50f
                    }

                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 9.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val titularStr = if (tx.titularName.isNotBlank()) "Titular: ${tx.titularName}" else "Titular: (No especificado)"
                    val ciStr = if (tx.titularCi.isNotBlank()) " | CI: ${tx.titularCi}" else ""
                    val phoneStr = if (tx.phoneNumber.isNotBlank()) " | Tel: ${tx.phoneNumber}" else " | Tel: No informado"
                    canvas.drawText("Trans: ${tx.transactionNumber} - $titularStr$ciStr$phoneStr", 40f, currentY, paint)

                    paint.color = Color.parseColor("#0284C7")
                    canvas.drawText("$${"%.2f".format(tx.amount)} ${tx.currency}", 440f, currentY, paint)

                    currentY += 13f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textSize = 8.5f
                    paint.color = Color.parseColor("#64748B")
                    val dateInfo = if (tx.smsDate.isNotBlank()) "Fecha: ${tx.smsDate} | " else "Fecha: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(tx.receivedAt))} | "
                    val timeInfo = "Hora: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tx.receivedAt))} | "
                    canvas.drawText("  $dateInfo$timeInfo Cajero: @${tx.cajeroUsername}", 40f, currentY, paint)

                    currentY += 16f
                    canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
                    currentY += 12f
                }
                currentY += 10f
            }

            // Detailed orders
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DETALLE DE COMANDAS COBRADAS", 40f, currentY, paint)
            currentY += 10f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 20f

            closedOrders.forEach { order ->
                if (currentY > 750f) {
                    pdfDocument.finishPage(page)
                    pageCount++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageCount).create())
                    canvas = page.canvas
                    // Draw continuous page background
                    paint.color = Color.WHITE
                    canvas.drawRect(0f, 0f, 595f, 842f, paint)
                    currentY = 50f

                    paint.color = Color.parseColor("#475569")
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Detalle de Comandas Cobradas (Cont.)", 40f, currentY, paint)
                    currentY += 15f
                    canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
                    currentY += 20f
                }

                val timeStr = if (order.closedAt != null) timeFormatter.format(Date(order.closedAt)) else "--:--"
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val tableStr = if ((order.tableNumber ?: 0) > 0) "Mesa ${order.tableNumber}" else "PARA LLEVAR"
                canvas.drawText("Comanda #${order.comandaNumber} ($tableStr) - $timeStr", 40f, currentY, paint)

                paint.color = Color.parseColor("#1E293B")
                val amountStr = if (order.currency.isNotEmpty() && order.currency != "CUP" && order.amountInCurrency > 0) {
                    "$${"%.2f".format(order.amountInCurrency)} ${order.currency} ($${"%.2f".format(order.totalAmount)} CUP)"
                } else {
                    "$${"%.2f".format(order.totalAmount)} CUP"
                }
                canvas.drawText(amountStr, 380f, currentY, paint)

                currentY += 14f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 9f
                paint.color = Color.parseColor("#64748B")
                val currInfo = if (order.currency.isNotEmpty() && order.currency != "CUP") " | Moneda: ${order.currency}" else ""
                val metaStr = "Cajero: @${order.waiterUsername} | Método: ${order.paymentMethod}$currInfo"
                canvas.drawText(metaStr, 40f, currentY, paint)

                val breakStr = "Cocina: $${"%.2f".format(order.totalCocina)} | Barra: $${"%.2f".format(order.totalBarra)}"
                canvas.drawText(breakStr, 440f, currentY, paint)

                // Render items inside this order
                val items = orderItems.filter { it.orderId == order.id }
                val itemsStr = items.joinToString { "${it.productName} (x${it.quantity})" }
                if (itemsStr.isNotEmpty()) {
                    currentY += 14f
                    paint.color = Color.parseColor("#94A3B8")
                    paint.textSize = 8.5f
                    // Multi-line safety or wrapping for product names
                    val displayStr = if (itemsStr.length > 105) itemsStr.substring(0, 102) + "..." else itemsStr
                    canvas.drawText("  Artículos: $displayStr", 40f, currentY, paint)
                }

                currentY += 20f
                canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
                currentY += 15f
            }

            pdfDocument.finishPage(page)

            // Save PDF
            val fileName = "Informe_Caja_Jornada_${activeJornada.id}_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF de caja: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    fun shareCajaReportToAdmin(context: Context, file: File, adminPhone: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val pdfUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_TEXT, "Estimado Administrador, se adjunta el Informe de Caja de El Qadre.\nTeléfono: $adminPhone")
                putExtra("jid", "$adminPhone@s.whatsapp.net") // Directly target WhatsApp contact if using WhatsApp
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Target specifically to whatsapp if possible or standard chooser
            val chooserIntent = Intent.createChooser(shareIntent, "Enviar Informe al Administrador ($adminPhone)...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al preparar envío: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
