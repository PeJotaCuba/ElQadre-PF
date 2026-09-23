package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.Jornada
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TandaPdfItem(
    val tandaNumber: String,
    val baseQuantityFormatted: String,
    val finalQuantityFormatted: String,
    val rendimientoFormatted: String,
    val costFormatted: String,
    val potentialRevenueFormatted: String,
    val rawCost: Double,
    val rawPotentialRevenue: Double
)

data class ProductTandasPdfGroup(
    val productName: String,
    val productionUnit: String,
    val tandas: List<TandaPdfItem>
)

object TandasJornadaPdfExporter {

    fun generateAndShareJornadaPdf(
        context: Context,
        jornada: Jornada,
        products: List<Product>,
        tandas: List<Tanda>,
        businessName: String = "EL QADRE",
        resolveSalePrice: (Product?, Tanda) -> Double
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

            // Agrupar tandas por producto ordenadamente
            val productMap = products.associateBy { it.id }
            val groups = tandas.groupBy { it.productId }.map { (prodId, prodTandas) ->
                val prod = productMap[prodId]
                val pName = prod?.name ?: prodTandas.firstOrNull()?.productName ?: "Producto #$prodId"
                val pUnit = prodTandas.firstOrNull()?.productionUnit?.ifBlank { null } ?: "u"

                val sortedTandas = prodTandas.sortedWith(
                    compareBy<Tanda> { it.tandaNumber.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.tandaNumber }
                        .thenBy { it.date }
                ).map { tanda ->
                    val baseUnit = tanda.baseQuantityUnit.ifBlank { "lb" }
                    val bQtyFormatted = if (tanda.baseQuantityUsed % 1.0 == 0.0) {
                        tanda.baseQuantityUsed.toInt().toString()
                    } else {
                        "%.2f".format(tanda.baseQuantityUsed)
                    }

                    val finalQty = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                    val fQtyFormatted = if (finalQty % 1.0 == 0.0) finalQty.toInt().toString() else "%.1f".format(finalQty)

                    val rendVal = if (tanda.baseQuantityUsed > 0.0) finalQty / tanda.baseQuantityUsed else 0.0
                    val rendFormatted = if (rendVal % 1.0 == 0.0) rendVal.toInt().toString() else "%.2f".format(rendVal)
                    val rendFullText = "$fQtyFormatted $pUnit ÷ $bQtyFormatted $baseUnit = $rendFormatted"

                    val salePrice = resolveSalePrice(prod, tanda)
                    val potRev = finalQty * salePrice

                    TandaPdfItem(
                        tandaNumber = tanda.tandaNumber,
                        baseQuantityFormatted = "$bQtyFormatted $baseUnit",
                        finalQuantityFormatted = "$fQtyFormatted $pUnit",
                        rendimientoFormatted = rendFullText,
                        costFormatted = "$${"%.2f".format(tanda.totalBatchCost)}",
                        potentialRevenueFormatted = "$${"%.2f".format(potRev)}",
                        rawCost = tanda.totalBatchCost,
                        rawPotentialRevenue = potRev
                    )
                }

                ProductTandasPdfGroup(
                    productName = pName,
                    productionUnit = pUnit,
                    tandas = sortedTandas
                )
            }.sortedBy { it.productName.lowercase() }

            val totalTandasCount = groups.sumOf { it.tandas.size }
            val totalCostAll = groups.sumOf { g -> g.tandas.sumOf { it.rawCost } }
            val totalRevAll = groups.sumOf { g -> g.tandas.sumOf { it.rawPotentialRevenue } }

            fun drawHeader() {
                paint.color = Color.WHITE
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

                // Header Banner Navy
                paint.color = Color.parseColor("#1E293B")
                canvas.drawRect(30f, 25f, (pageWidth - 30).toFloat(), 88f, paint)

                // Business Name & Title
                paint.color = Color.WHITE
                paint.textSize = 14f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(businessName.ifBlank { "EL QADRE" }.uppercase(), 45f, 50f, paint)

                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("INFORME OFICIAL DE TANDAS POR JORNADA", 45f, 70f, paint)

                // Right side
                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 9f
                val fechaJornadaStr = if (jornada.openedAt > 0) dateOnlyFormatter.format(Date(jornada.openedAt)) else dateOnlyFormatter.format(Date())
                canvas.drawText("Jornada: #${jornada.id} | Fecha: $fechaJornadaStr", (pageWidth - 45).toFloat(), 50f, paint)
                canvas.drawText("Página $pageNumber", (pageWidth - 45).toFloat(), 70f, paint)
                paint.textAlign = Paint.Align.LEFT
            }

            fun checkNewPage(neededHeight: Float, curY: Float): Float {
                if (curY + neededHeight > pageHeight - 45f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader()
                    return 105f
                }
                return curY
            }

            // Iniciar primera página
            drawHeader()
            var y = 105f

            // 1. IDENTIFICACIÓN DE LA JORNADA
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 68f, 6f, 6f, paint)
            paint.color = Color.parseColor("#CBD5E1")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 68f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val apStr = if (jornada.openedAt > 0) dateFormatter.format(Date(jornada.openedAt)) else "N/A"
            val ciStr = if (jornada.closedAt != null && jornada.closedAt > 0) dateFormatter.format(Date(jornada.closedAt)) else "En curso / Sin cerrar"
            canvas.drawText("JORNADA #${jornada.id}  •  Apertura: $apStr  •  Cierre: $ciStr", 42f, y + 18f, paint)

            paint.color = Color.parseColor("#475569")
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val respOpen = jornada.openedBy.ifBlank { "admin" }
            val respClose = jornada.closedBy?.ifBlank { null } ?: "Pendiente"
            canvas.drawText("Responsable apertura: $respOpen   |   Cerrado por: $respClose", 42f, y + 36f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total Productos: ${groups.size}   |   Total Tandas: $totalTandasCount   |   Costo Total: $${"%.2f".format(totalCostAll)} CUP   |   Ing. Potenciales: $${"%.2f".format(totalRevAll)} CUP", 42f, y + 54f, paint)

            y += 82f

            // 2. PRODUCTOS Y SUS TANDAS
            if (groups.isEmpty()) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 11f
                canvas.drawText("No se registraron tandas de producción durante esta jornada.", 42f, y + 20f, paint)
                y += 40f
            } else {
                for (group in groups) {
                    y = checkNewPage(85f, y)

                    // Header del producto
                    paint.color = Color.parseColor("#0F766E") // Dark Teal
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 22f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("PRODUCTO: ${group.productName.uppercase()}  (${group.tandas.size} ${if (group.tandas.size == 1) "tanda" else "tandas"})", 38f, y + 15f, paint)
                    y += 22f

                    // Encabezados de columnas de la tabla
                    paint.color = Color.parseColor("#E2E8F0")
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 18f, paint)

                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    canvas.drawText("TANDA", 38f, y + 12f, paint)
                    canvas.drawText("INSUMO BASE", 95f, y + 12f, paint)
                    canvas.drawText("CANT. FINAL", 175f, y + 12f, paint)
                    canvas.drawText("RENDIMIENTO (FÓRMULA)", 255f, y + 12f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("COSTO", 475f, y + 12f, paint)
                    canvas.drawText("ING. POTENCIAL", (pageWidth - 38).toFloat(), y + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y += 18f

                    // Filas de cada tanda
                    var isEven = false
                    for (tanda in group.tandas) {
                        y = checkNewPage(20f, y)

                        if (isEven) {
                            paint.color = Color.parseColor("#F8FAFC")
                            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 18f, paint)
                        }
                        isEven = !isEven

                        paint.color = Color.parseColor("#0F172A")
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("Tanda ${tanda.tandaNumber}", 38f, y + 12f, paint)

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.color = Color.parseColor("#334155")
                        canvas.drawText(tanda.baseQuantityFormatted, 95f, y + 12f, paint)

                        paint.color = Color.parseColor("#0284C7")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tanda.finalQuantityFormatted, 175f, y + 12f, paint)

                        paint.color = Color.parseColor("#0F766E")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tanda.rendimientoFormatted, 255f, y + 12f, paint)

                        paint.textAlign = Paint.Align.RIGHT
                        paint.color = Color.parseColor("#BE123C")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tanda.costFormatted, 475f, y + 12f, paint)

                        paint.color = Color.parseColor("#047857")
                        canvas.drawText(tanda.potentialRevenueFormatted, (pageWidth - 38).toFloat(), y + 12f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        // Línea divisoria fina
                        canvas.drawLine(30f, y + 18f, (pageWidth - 30).toFloat(), y + 18f, linePaint)
                        y += 18f
                    }

                    // Subtotal del producto
                    val prodCost = group.tandas.sumOf { it.rawCost }
                    val prodRev = group.tandas.sumOf { it.rawPotentialRevenue }
                    y = checkNewPage(22f, y)

                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 18f, paint)

                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Subtotal ${group.productName}:", 38f, y + 12f, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    paint.color = Color.parseColor("#BE123C")
                    canvas.drawText("$${"%.2f".format(prodCost)} CUP", 475f, y + 12f, paint)

                    paint.color = Color.parseColor("#047857")
                    canvas.drawText("$${"%.2f".format(prodRev)} CUP", (pageWidth - 38).toFloat(), y + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y += 28f
                }
            }

            // 3. RESUMEN FINAL CONSOLIDADO
            y = checkNewPage(65f, y)
            paint.color = Color.parseColor("#1E293B")
            canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 54f, 6f, 6f, paint)

            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("TOTALES CONSOLIDADOS DE PRODUCCIÓN EN LA JORNADA #${jornada.id}", 42f, y + 18f, paint)

            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Total Tandas: $totalTandasCount   |   Costo Total: $${"%.2f".format(totalCostAll)} CUP   |   Ingresos Potenciales: $${"%.2f".format(totalRevAll)} CUP", 42f, y + 36f, paint)

            pdfDocument.finishPage(page)

            // Guardar en almacenamiento de documentos
            val pdfDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "TandasPDF")
            if (!pdfDir.exists()) pdfDir.mkdirs()

            val file = File(pdfDir, "Tandas_Jornada_${jornada.id}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()

            // Compartir / Abrir PDF inmediatamente
            sharePdf(context, file, jornada.id)

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al generar PDF de tandas: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun sharePdf(context: Context, file: File, jornadaId: Long) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe de Tandas - Jornada #$jornadaId")
                putExtra(Intent.EXTRA_TEXT, "Adjunto informe oficial de tandas de producción de la Jornada #$jornadaId.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Descargar / Compartir PDF de Tandas").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF guardado en: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}
