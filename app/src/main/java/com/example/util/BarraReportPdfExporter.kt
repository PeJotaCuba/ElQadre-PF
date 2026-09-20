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
import com.example.ui.screens.barra.BarraProductItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BarraReportPdfExporter {

    fun exportBarraReport(
        context: Context,
        activeJornada: Jornada,
        closedOrders: List<TableOrder>,
        orderItems: List<OrderItem>,
        currentDependiente: String,
        montoPorBebida: Double = 0.0,
        barraProductItems: List<BarraProductItem> = emptyList(),
        physicalCounts: Map<Long, Int> = emptyMap()
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
            canvas.drawText("INFORME OPERATIVO Y CUADRE DE BARRA", 45f, 82f, paint)

            // Right side of banner: Date & Jornada ID
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 9f
            canvas.drawText("Fecha: ${dateOnlyFormatter.format(Date())}", 550f, 62f, paint)
            canvas.drawText("Jornada ID: #${activeJornada.id}", 550f, 82f, paint)
            paint.textAlign = Paint.Align.LEFT // Reset alignment

            var currentY = 122f

            // 1. Datos de la Jornada
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("1. DATOS DE LA JORNADA DE BARRA", 30f, currentY, paint)
            currentY += 5f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 16f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9f

            paint.color = Color.parseColor("#475569")
            canvas.drawText("Dependiente de Barra:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(currentDependiente, 155f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Apertura de Turno:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(dateFormatter.format(Date(activeJornada.openedAt)), 425f, currentY, paint)

            currentY += 15f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Estado de Jornada:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val estadoStr = if (activeJornada.isOpen) "ACTIVA" else "CERRADA (${if (activeJornada.closedAt != null) dateFormatter.format(Date(activeJornada.closedAt)) else "-"})"
            canvas.drawText(estadoStr, 155f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Comandas Cobradas:", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${closedOrders.size}", 425f, currentY, paint)

            currentY += 24f

            // 2. Resumen Financiero y Categorías
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("2. RESUMEN DE VENTAS Y CATEGORÍAS", 30f, currentY, paint)
            currentY += 5f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 16f

            val closedOrderIds = closedOrders.map { it.id }.toSet()
            val barraItems = orderItems.filter { closedOrderIds.contains(it.orderId) && it.destination == "BARRA" }

            val totalMontoCobrado = closedOrders.sumOf { it.totalAmount }
            val totalBebidasVendidas = barraProductItems.filter { it.subcategory == "BEBIDAS" }.sumOf { it.ventasJornada }
            val totalConfiteriasVendidas = barraProductItems.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.ventasJornada }

            val totalImporteBebidas = barraProductItems.filter { it.subcategory == "BEBIDAS" }.sumOf { it.ventasJornada * it.product.price }
            val totalImporteConfiterias = barraProductItems.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.ventasJornada * it.product.price }
            val totalTeoricoVentas = totalImporteBebidas + totalImporteConfiterias

            val utilidadesPotenciales = totalBebidasVendidas * montoPorBebida

            paint.textSize = 9f
            paint.color = Color.parseColor("#475569")
            canvas.drawText("Ventas Bebidas ($totalBebidasVendidas ud.):", 30f, currentY, paint)
            paint.color = Color.parseColor("#1E3A8A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalImporteBebidas)} CUP", 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Total Cobrado Registrado:", 310f, currentY, paint)
            paint.color = Color.parseColor("#047857")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalMontoCobrado)} CUP", 440f, currentY, paint)

            currentY += 15f

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Ventas Confiterías ($totalConfiteriasVendidas ud.):", 30f, currentY, paint)
            paint.color = Color.parseColor("#B45309")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalImporteConfiterias)} CUP", 160f, currentY, paint)

            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Total Teórico (Ventas × Precio):", 310f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$${"%.2f".format(totalTeoricoVentas)} CUP", 440f, currentY, paint)

            currentY += 15f

            // Utilidades Potenciales
            paint.color = Color.parseColor("#047857")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Utilidades Potenciales (Bebidas):", 30f, currentY, paint)
            canvas.drawText("$${"%.2f".format(utilidadesPotenciales)} CUP ($totalBebidasVendidas ud. × $${"%.2f".format(montoPorBebida)} CUP)", 185f, currentY, paint)

            currentY += 24f

            // 3. Control de Inventario Físico vs. Teórico
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("3. CONTROL Y CUADRE DE INVENTARIO DE BARRA", 30f, currentY, paint)
            currentY += 5f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 14f

            // Table Header
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRect(30f, currentY - 10f, 565f, currentY + 6f, paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("PRODUCTO", 35f, currentY, paint)
            canvas.drawText("CAT", 170f, currentY, paint)
            canvas.drawText("INICIO", 215f, currentY, paint)
            canvas.drawText("ENTRADA", 260f, currentY, paint)
            canvas.drawText("VENTAS", 315f, currentY, paint)
            canvas.drawText("TEÓRICO", 370f, currentY, paint)
            canvas.drawText("FÍSICO", 430f, currentY, paint)
            canvas.drawText("DIF.", 495f, currentY, paint)

            currentY += 14f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.5f

            val itemsToPrint = barraProductItems.take(16)
            for (item in itemsToPrint) {
                val physical = physicalCounts[item.product.id] ?: item.existenciaActual
                val debeTerminar = item.existenciaActual
                val dif = physical - debeTerminar

                paint.color = Color.parseColor("#0F172A")
                val shortName = if (item.product.name.length > 22) item.product.name.take(20) + ".." else item.product.name
                canvas.drawText(shortName, 35f, currentY, paint)

                paint.color = if (item.subcategory == "BEBIDAS") Color.parseColor("#1E3A8A") else Color.parseColor("#B45309")
                canvas.drawText(if (item.subcategory == "BEBIDAS") "BEB" else "CONF", 170f, currentY, paint)

                paint.color = Color.parseColor("#475569")
                canvas.drawText("${item.inventarioInicial}", 220f, currentY, paint)
                canvas.drawText("+${item.entradasJornada}", 270f, currentY, paint)
                canvas.drawText("${item.ventasJornada}", 325f, currentY, paint)
                canvas.drawText("$debeTerminar", 380f, currentY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#0F172A")
                canvas.drawText("$physical", 440f, currentY, paint)

                if (dif == 0) {
                    paint.color = Color.parseColor("#047857")
                    canvas.drawText("0 OK", 495f, currentY, paint)
                } else if (dif < 0) {
                    paint.color = Color.parseColor("#E11D48")
                    canvas.drawText("$dif", 495f, currentY, paint)
                } else {
                    paint.color = Color.parseColor("#2563EB")
                    canvas.drawText("+$dif", 495f, currentY, paint)
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                currentY += 12f
            }

            if (barraProductItems.size > 16) {
                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 7f
                canvas.drawText("... y ${barraProductItems.size - 16} productos adicionales registrados.", 35f, currentY, paint)
                currentY += 14f
            } else {
                currentY += 6f
            }

            // 4. Detalle de Comandas
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("4. REGISTRO DE COMANDAS COBRADAS (${closedOrders.size})", 30f, currentY, paint)
            currentY += 5f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 14f

            // Table Header Comandas
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRect(30f, currentY - 10f, 565f, currentY + 6f, paint)

            paint.color = Color.parseColor("#1E293B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("COMANDA", 35f, currentY, paint)
            canvas.drawText("MESA / ORIGEN", 110f, currentY, paint)
            canvas.drawText("HORA", 230f, currentY, paint)
            canvas.drawText("PAGO", 320f, currentY, paint)
            canvas.drawText("TOTAL COBRADO", 470f, currentY, paint)

            currentY += 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 7.5f

            val ordersToPrint = closedOrders.take(12)
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            for (order in ordersToPrint) {
                paint.color = Color.parseColor("#0F172A")
                canvas.drawText("#${order.comandaNumber}", 35f, currentY, paint)

                val origenStr = if (order.customerName.contains("Barra", ignoreCase = true) || order.tableNumber == null || order.tableNumber == 0) {
                    if (order.customerName.contains("Llevar", ignoreCase = true)) "Para Llevar" else "Barra Directa"
                } else {
                    "Mesa ${order.tableNumber}"
                }
                canvas.drawText(origenStr, 110f, currentY, paint)

                val horaStr = timeFormatter.format(Date(order.closedAt ?: order.createdAt))
                canvas.drawText(horaStr, 230f, currentY, paint)

                canvas.drawText(order.paymentMethod, 320f, currentY, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#047857")
                canvas.drawText("$${"%.2f".format(order.totalAmount)} CUP", 550f, currentY, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                currentY += 12f
            }

            // Footer
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 8f
            canvas.drawText("Generado por El Qadre POS System • Documento Oficial de Cierre y Cuadre de Barra", 30f, 820f, paint)

            pdfDocument.finishPage(page)

            val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "qdepenbarra_report_${activeJornada.id}.pdf")
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

    fun shareBarraReportToAdmin(context: Context, file: File, adminPhone: String): Boolean {
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
                    "Estimado Administrador, se adjunta el Informe de Cierre y Cuadre de Barra de El Qadre."
                )
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Enviar Informe de Barra al Administrador ($cleanPhone)...").apply {
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
