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
import com.example.data.local.model.Jornada
import com.example.data.local.model.OrderItem
import com.example.data.local.model.TableOrder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SalonReportPdfExporter {

    fun exportSalonReport(
        context: Context,
        activeJornada: Jornada,
        closedOrders: List<TableOrder>,
        orderItems: List<OrderItem>,
        currentDependiente: String,
        montoPorProducto: Double = 0.0
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()
            val linePaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Background
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, 595f, 842f, paint)

            // Header Banner
            paint.color = Color.parseColor("#1E293B") // ElQadreNavy
            canvas.drawRect(30f, 30f, 565f, 100f, paint)

            // Title inside Banner
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("EL QADRE - RESTAURANTE Y BAR", 45f, 62f, paint)

            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("INFORME OPERATIVO DEL DEPENDIENTE DE SALÓN", 45f, 82f, paint)

            // Right side of banner: Date & Jornada ID
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 9f
            canvas.drawText("Fecha: ${dateOnlyFormatter.format(Date())}", 550f, 62f, paint)
            canvas.drawText("Jornada ID: #${activeJornada.id}", 550f, 82f, paint)
            paint.textAlign = Paint.Align.LEFT // Reset alignment

            var currentY = 125f

            // 1. Datos de la Jornada
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("1. DATOS DE LA JORNADA", 30f, currentY, paint)
            currentY += 6f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 18f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f

            paint.color = Color.parseColor("#475569")
            canvas.drawText("Dependiente / Operador:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(currentDependiente, 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Apertura de Turno:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(dateFormatter.format(Date(activeJornada.openedAt)), 430f, currentY, paint)

            currentY += 16f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Estado de Jornada:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val estadoStr = if (activeJornada.isOpen) "ACTIVA (En proceso de Cierre)" else "CERRADA"
            canvas.drawText(estadoStr, 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Total Comandas Cobradas:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${closedOrders.size}", 430f, currentY, paint)

            currentY += 28f

            // 2. Resumen Financiero y Operativo
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("2. RESUMEN FINANCIERO Y OPERATIVO DE SALÓN", 30f, currentY, paint)
            currentY += 6f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 18f

            val totalMontoCobrado = closedOrders.sumOf { it.totalAmount }
            val totalCocina = closedOrders.sumOf { it.totalCocina }
            val totalBarra = closedOrders.sumOf { it.totalBarra }
            val importeRegistradoCocina = totalCocina // Solo Cocina es importe registrado del dependiente
            val totalEfectivo = closedOrders.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.totalAmount }
            val totalTransferencia = closedOrders.filter { it.paymentMethod == "TRANSFERENCIA" || it.paymentMethod == "MIXTO" }.sumOf { it.totalAmount }

            paint.textSize = 9f

            // Metric box 1
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Monto Total Cobrado:", 30f, currentY, paint)
            paint.color = Color.parseColor("#047857") // Emerald
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalMontoCobrado)} CUP", 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Importe Registrado (Cocina):", 310f, currentY, paint)
            paint.color = Color.parseColor("#1E3A8A") // Blue
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(importeRegistradoCocina)} CUP", 450f, currentY, paint)

            currentY += 16f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Subtotal Cocina:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalCocina)} CUP", 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Subtotal Barra (Bebidas):", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalBarra)} CUP", 450f, currentY, paint)

            currentY += 16f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Efectivo Recibido:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalEfectivo)} CUP", 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Transferencia / Otros:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalTransferencia)} CUP", 450f, currentY, paint)

            currentY += 24f

            // Utilidades Potenciales
            val closedOrderIdsPdf = closedOrders.map { it.id }.toSet()
            val kitchenItemsPdf = orderItems.filter { closedOrderIdsPdf.contains(it.orderId) && it.destination == "COCINA" }
            val totalPlatosCocinaVendidosPdf = kitchenItemsPdf.sumOf { it.quantity }
            val finalRatePdf = if (montoPorProducto > 0.0) montoPorProducto else activeJornada.utilidadSalonMontoUnitario
            val utilidadesPotencialesPdf = totalPlatosCocinaVendidosPdf * finalRatePdf

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("UTILIDADES POTENCIALES DE LA JORNADA", 30f, currentY, paint)
            currentY += 6f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 18f

            paint.textSize = 9f
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Platos / Productos Cocina Vendidos:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$totalPlatosCocinaVendidosPdf ud.", 220f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Monto por Producto Asignado:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(finalRatePdf)} CUP", 470f, currentY, paint)

            currentY += 16f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Utilidades Potenciales de Salón:", 30f, currentY, paint)
            paint.color = Color.parseColor("#047857")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(utilidadesPotencialesPdf)} CUP", 220f, currentY, paint)

            currentY += 28f

            // 3. Resumen por Producto y Variante (Agregados)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("3. PRODUCTOS VENDIDOS Y VARIANTES", 30f, currentY, paint)
            currentY += 6f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 18f

            // Group order items by productName + notes
            val closedOrderIds = closedOrders.map { it.id }.toSet()
            val relevantItems = orderItems.filter { closedOrderIds.contains(it.orderId) }

            val itemGroups = relevantItems.groupBy { "${it.productName}${if (it.notes.isNotBlank()) " (${it.notes})" else ""}" }

            paint.textSize = 8.5f
            if (itemGroups.isEmpty()) {
                paint.color = Color.GRAY
                canvas.drawText("No hay productos cobrados en esta jornada.", 30f, currentY, paint)
                currentY += 16f
            } else {
                itemGroups.entries.take(12).forEach { (variantName, items) ->
                    val totalQty = items.sumOf { it.quantity }
                    val totalAmount = items.sumOf { it.unitPrice * it.quantity }

                    paint.color = Color.parseColor("#1E293B")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("• $variantName", 35f, currentY, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    paint.color = Color.parseColor("#0F172A")
                    canvas.drawText("$totalQty ud.", 420f, currentY, paint)
                    canvas.drawText("$${"%.2f".format(totalAmount)} CUP", 550f, currentY, paint)
                    paint.textAlign = Paint.Align.LEFT

                    currentY += 14f
                }
            }

            currentY += 14f

            // 4. Detalle de Comandas Cobradas
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("4. DETALLE DE COMANDAS DE LA JORNADA", 30f, currentY, paint)
            currentY += 6f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 16f

            // Table Header
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRect(30f, currentY - 10f, 565f, currentY + 6f, paint)

            paint.color = Color.parseColor("#334155")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("COM#", 35f, currentY, paint)
            canvas.drawText("MESA", 75f, currentY, paint)
            canvas.drawText("CONFIRMAR", 115f, currentY, paint)
            canvas.drawText("SERVIR", 185f, currentY, paint)
            canvas.drawText("DURACIÓN", 245f, currentY, paint)
            canvas.drawText("COCINA", 320f, currentY, paint)
            canvas.drawText("BARRA", 390f, currentY, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("TOTAL", 555f, currentY, paint)
            paint.textAlign = Paint.Align.LEFT

            currentY += 16f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            closedOrders.take(18).forEach { order ->
                paint.color = Color.parseColor("#0F172A")
                canvas.drawText("#${order.comandaNumber}", 35f, currentY, paint)
                canvas.drawText("Mesa ${order.tableNumber}", 75f, currentY, paint)

                val confStr = if (order.confirmedAt != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(order.confirmedAt)) else "-"
                val servStr = if (order.servedAt != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(order.servedAt)) else "-"
                val durSec = order.serviceDurationSeconds ?: 0L
                val durStr = "${durSec / 60}m ${durSec % 60}s"

                canvas.drawText(confStr, 115f, currentY, paint)
                canvas.drawText(servStr, 185f, currentY, paint)
                canvas.drawText(durStr, 245f, currentY, paint)
                canvas.drawText("$${"%.2f".format(order.totalCocina)}", 320f, currentY, paint)
                canvas.drawText("$${"%.2f".format(order.totalBarra)}", 390f, currentY, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("$${"%.2f".format(order.totalAmount)}", 555f, currentY, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                currentY += 14f
            }

            // Footer
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 8f
            canvas.drawText("Generado por El Qadre POS System • Documento Oficial de Cierre de Salón", 30f, 820f, paint)

            pdfDocument.finishPage(page)

            val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "qdepsalon_report_${activeJornada.id}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareSalonReportToAdmin(context: Context, file: File, adminPhone: String): Boolean {
        val cleanPhone = adminPhone.trim()
        if (cleanPhone.isEmpty()) {
            Toast.makeText(
                context,
                "Error: No hay número de Administrador configurado en el sistema.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Estimado Administrador, se adjunta el Informe de Cierre de Salón de El Qadre."
                )
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Enviar Informe al Administrador ($cleanPhone)...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir con el Administrador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return false
        }
    }
}
