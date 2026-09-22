package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.Product
import com.example.ui.viewmodel.MainUiState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CostSheetPdfExporter {

    fun exportSingleCostSheet(context: Context, product: Product, uiState: MainUiState) {
        try {
            val costSheet = CostCalculationHelper.calculateCostSheet(
                product = product,
                uiState = uiState
            )

            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageIndex = 1

            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
            var canvas: Canvas = page.canvas
            val paint = Paint()

            val primaryColor = Color.parseColor("#1E293B") // ElQadreNavy
            val goldColor = Color.parseColor("#D97706")    // ElQadreGold
            val greenColor = Color.parseColor("#047857")   // Emerald700
            val textDark = Color.parseColor("#0F172A")     // Slate900
            val textMuted = Color.parseColor("#475569")    // Slate600
            val lineGray = Color.parseColor("#CBD5E1")     // Slate300

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val fechaEmision = dateFormat.format(Date())

            fun drawHeader(isSubsequent: Boolean) {
                paint.color = Color.WHITE
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

                if (!isSubsequent) {
                    paint.color = primaryColor
                    canvas.drawRect(35f, 35f, 560f, 100f, paint)

                    paint.color = Color.parseColor("#F59E0B")
                    paint.textSize = 15f
                    paint.isFakeBoldText = true
                    canvas.drawText("EL QADRE — FICHA DE COSTO DE PRODUCCIÓN", 48f, 65f, paint)

                    paint.color = Color.WHITE
                    paint.textSize = 10f
                    paint.isFakeBoldText = false
                    canvas.drawText("Producto: ${product.name} | Cat: ${product.category} | Emisión: $fechaEmision", 48f, 85f, paint)
                } else {
                    paint.color = primaryColor
                    canvas.drawRect(35f, 30f, 560f, 60f, paint)

                    paint.color = Color.parseColor("#F59E0B")
                    paint.textSize = 11f
                    paint.isFakeBoldText = true
                    canvas.drawText("EL QADRE — FICHA DE COSTO: ${product.name} (Cont.)", 45f, 50f, paint)
                }
            }

            fun drawFooter(pageNum: Int) {
                paint.color = lineGray
                canvas.drawLine(35f, 810f, 560f, 810f, paint)
                paint.color = textMuted
                paint.textSize = 9f
                paint.isFakeBoldText = false
                canvas.drawText("El Qadre Sistema de Costos | Página $pageNum", 35f, 824f, paint)
                canvas.drawText("Generado localmente en dispositivo", 390f, 824f, paint)
            }

            drawHeader(isSubsequent = false)
            var y = 125f

            fun checkPageOverflow(neededHeight: Float) {
                if (y + neededHeight > 780f) {
                    drawFooter(pageIndex)
                    pdfDocument.finishPage(page)
                    pageIndex++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
                    canvas = page.canvas
                    drawHeader(isSubsequent = true)
                    y = 80f
                }
            }

            // ==========================================
            // 1. IDENTIFICACIÓN Y DATOS BÁSICOS
            // ==========================================
            checkPageOverflow(110f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("1. IDENTIFICACIÓN Y DATOS BÁSICOS", 35f, y, paint)
            y += 18f

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = textDark
            canvas.drawText("• Unidad de Medida: ${costSheet.productionUnit}", 45f, y, paint)
            canvas.drawText("• Rendimiento Base: ${costSheet.baseYield} ${costSheet.productionUnit}", 310f, y, paint)
            y += 16f
            canvas.drawText("• Producción Promedio Diaria (PPD): ${costSheet.ppd} ${costSheet.productionUnit} / día", 45f, y, paint)
            val codProd = product.code.ifBlank { "N/A" }
            canvas.drawText("• Código de Producto: $codProd", 310f, y, paint)
            y += 16f
            val estadoPrecio = if (costSheet.hasPrecioDefinitivo) "SÍ ($${"%.2f".format(costSheet.precioDefinitivo)} CUP)" else "NO"
            canvas.drawText("• Precio Definitivo Configurado: $estadoPrecio", 45f, y, paint)
            val nombreReceta = costSheet.productoElaborado?.recipeName?.ifBlank { "Estándar" } ?: "Estándar"
            canvas.drawText("• Receta Registrada: $nombreReceta", 310f, y, paint)
            y += 24f

            // ==========================================
            // 2. MATERIAS PRIMAS Y COSTO DIRECTO (RECETA)
            // ==========================================
            checkPageOverflow(60f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("2. MATERIAS PRIMAS Y COSTO DIRECTO (RECETA)", 35f, y, paint)
            y += 18f

            // Encabezado de tabla
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRect(35f, y - 12f, 560f, y + 6f, paint)

            paint.color = primaryColor
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("Ingrediente / Materia Prima", 45f, y, paint)
            canvas.drawText("Cantidad", 290f, y, paint)
            canvas.drawText("Costo Unit.", 380f, y, paint)
            canvas.drawText("Total Costo", 475f, y, paint)
            y += 10f

            paint.color = lineGray
            canvas.drawLine(35f, y, 560f, y, paint)
            y += 14f

            if (costSheet.ingredientDetails.isEmpty()) {
                checkPageOverflow(24f)
                paint.color = textMuted
                paint.textSize = 9.5f
                paint.isFakeBoldText = false
                canvas.drawText("No hay ingredientes registrados en la receta (Costo base producto: $${"%.2f".format(product.cost)} CUP)", 45f, y, paint)
                y += 18f
            } else {
                for (ing in costSheet.ingredientDetails) {
                    checkPageOverflow(20f)
                    val ingName = ing.materiaPrima?.name ?: "Ingrediente"
                    paint.color = textDark
                    paint.textSize = 9.5f
                    paint.isFakeBoldText = false
                    val displayName = if (ingName.length > 38) ingName.take(35) + "..." else ingName
                    canvas.drawText(displayName, 45f, y, paint)
                    canvas.drawText("${"%.2f".format(ing.quantity)} ${ing.unit}", 290f, y, paint)
                    canvas.drawText("$${"%.2f".format(ing.unitCost)}", 380f, y, paint)
                    canvas.drawText("$${"%.2f".format(ing.totalCost)}", 475f, y, paint)
                    y += 16f
                }
            }

            paint.color = lineGray
            canvas.drawLine(35f, y, 560f, y, paint)
            y += 16f

            checkPageOverflow(30f)
            paint.color = textDark
            paint.textSize = 10f
            paint.isFakeBoldText = true
            canvas.drawText("Costo Directo Total de la Receta: $${"%.2f".format(costSheet.totalDirectRecipeCost)} CUP", 45f, y, paint)
            paint.color = greenColor
            canvas.drawText("Costo Directo Unitario (CDU): $${"%.2f".format(costSheet.costoDirectoUnitario)} CUP", 310f, y, paint)
            y += 24f

            // ==========================================
            // 3. EGRESOS POR RATEO ECONÓMICO (COSTOS INDIRECTOS)
            // ==========================================
            checkPageOverflow(110f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("3. EGRESOS POR RATEO ECONÓMICO (COSTOS INDIRECTOS)", 35f, y, paint)
            y += 18f

            paint.color = textDark
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("• Gastos Generales Diarios: $${"%.2f".format(costSheet.gastosGeneralesDiariosTotales)} CUP / día", 45f, y, paint)
            canvas.drawText("• Depreciación Inversiones: $${"%.2f".format(costSheet.depreciacionInversionesDiariaTotales)} CUP / día", 310f, y, paint)
            y += 16f
            paint.isFakeBoldText = true
            canvas.drawText("• Costos Indirectos Totales: $${"%.2f".format(costSheet.costosIndirectosDiariosTotales)} CUP / día", 45f, y, paint)
            paint.isFakeBoldText = false
            canvas.drawText("• Participación en Inventario: ${"%.2f".format(costSheet.porcentajeParticipacionPpd)}%", 310f, y, paint)
            y += 16f
            paint.isFakeBoldText = true
            canvas.drawText("• Asignación Directa: $${"%.2f".format(costSheet.gastoIndirectoAsignado)} CUP / día", 45f, y, paint)
            paint.color = goldColor
            canvas.drawText("• Gasto Indirecto Unitario (GIU): $${"%.2f".format(costSheet.gastoIndirectoUnitario)} CUP / ud", 310f, y, paint)
            y += 24f

            // ==========================================
            // 4. PAGOS DE PERSONAL ASOCIADOS
            // ==========================================
            checkPageOverflow(90f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("4. PAGOS DE PERSONAL ASOCIADOS", 35f, y, paint)
            y += 18f

            paint.color = textDark
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("• Pago Cocina: $${"%.2f".format(costSheet.pagoCocinaUnitario)} CUP (x ${costSheet.cantidadCocineros} coc.) = $${"%.2f".format(costSheet.totalPagoCocinaUnitario)} CUP", 45f, y, paint)
            canvas.drawText("• Pago Dependiente: $${"%.2f".format(costSheet.totalPagoDependienteUnitario)} CUP", 310f, y, paint)
            y += 16f
            canvas.drawText("• Pago Cajero: $${"%.2f".format(costSheet.totalPagoCajeroUnitario)} CUP", 45f, y, paint)
            paint.isFakeBoldText = true
            canvas.drawText("• Total Personal Unitario: $${"%.2f".format(costSheet.totalPagoPersonalUnitario)} CUP", 310f, y, paint)
            y += 24f

            // ==========================================
            // 5. ESTRUCTURA DE COSTO UNITARIO FINAL
            // ==========================================
            checkPageOverflow(115f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("5. ESTRUCTURA DE COSTO UNITARIO FINAL", 35f, y, paint)
            y += 18f

            // Tarjeta de estructura final
            paint.color = primaryColor
            canvas.drawRect(35f, y - 6f, 560f, y + 84f, paint)

            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("• Costo Directo Unitario:", 48f, y + 12f, paint)
            canvas.drawText("$${"%.2f".format(costSheet.costoDirectoUnitario)} CUP", 440f, y + 12f, paint)

            canvas.drawText("• Gasto Indirecto Unitario:", 48f, y + 28f, paint)
            canvas.drawText("$${"%.2f".format(costSheet.gastoIndirectoUnitario)} CUP", 440f, y + 28f, paint)

            canvas.drawText("• Pago a Personal por Unidad:", 48f, y + 44f, paint)
            canvas.drawText("$${"%.2f".format(costSheet.totalPagoPersonalUnitario)} CUP", 440f, y + 44f, paint)

            paint.color = Color.parseColor("#475569")
            canvas.drawLine(48f, y + 52f, 545f, y + 52f, paint)

            paint.color = Color.parseColor("#F59E0B")
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("COSTO UNITARIO FINAL:", 48f, y + 72f, paint)
            canvas.drawText("$${"%.2f".format(costSheet.costoTotalUnitario)} CUP", 420f, y + 72f, paint)
            y += 104f

            // ==========================================
            // 6. PRECIO DE VENTA Y MARGEN DE GANANCIA
            // ==========================================
            checkPageOverflow(85f)
            paint.color = primaryColor
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("6. PRECIO DE VENTA Y MARGEN DE GANANCIA", 35f, y, paint)
            y += 18f

            paint.color = textDark
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("• Precio de Referencia (+30% margen): $${"%.2f".format(costSheet.precioReferencia)} CUP", 45f, y, paint)
            paint.isFakeBoldText = true
            paint.color = greenColor
            canvas.drawText("• Precio en Catálogo: $${"%.2f".format(product.price)} CUP", 310f, y, paint)
            y += 16f

            if (product.price > 0.0 && costSheet.costoTotalUnitario > 0.0) {
                val ganancia = product.price - costSheet.costoTotalUnitario
                val margenPct = (ganancia / costSheet.costoTotalUnitario) * 100.0
                paint.color = if (margenPct >= 0) greenColor else Color.parseColor("#DC2626")
                paint.isFakeBoldText = true
                val sign = if (margenPct >= 0) "+" else ""
                canvas.drawText("• Ganancia Unitaria: $${"%.2f".format(ganancia)} CUP  (${sign}${"%.1f".format(margenPct)}% de margen sobre costo)", 45f, y, paint)
            }

            drawFooter(pageIndex)
            pdfDocument.finishPage(page)

            // Sanitizar nombre de archivo identificable
            val cleanName = product.name
                .trim()
                .replace(" ", "_")
                .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_\\-]"), "")
                .ifEmpty { "Producto_${product.id}" }
            val fileName = "Ficha_Costo_${cleanName}.pdf"

            // Guardar en el almacenamiento del dispositivo
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir
            val file = File(baseDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            // Intentar replicar a Downloads público si el dispositivo lo permite
            try {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDownloads != null && publicDownloads.exists() && publicDownloads.canWrite()) {
                    val publicFile = File(publicDownloads, fileName)
                    file.copyTo(publicFile, overwrite = true)
                }
            } catch (_: Exception) {}

            // Lanzar compartir / ver archivo mediante FileProvider para acceso inmediato
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Ficha de Costo - ${product.name}")
                    putExtra(Intent.EXTRA_TEXT, "Ficha de Costo de Producción: ${product.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir o Abrir Ficha de Costo (PDF)"))
            } catch (_: Exception) {}

            Toast.makeText(context, "Ficha de Costo guardada: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar Ficha de Costo PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportSingleMercaderiaCostSheet(context: Context, mercaderia: com.example.data.local.model.Mercaderia, uiState: MainUiState) {
        try {
            val costSheet = CostCalculationHelper.calculateMercaderiaCostSheet(
                mercaderia = mercaderia,
                mercaderias = uiState.mercaderias,
                products = uiState.products,
                movimientos = uiState.movimientosMercaderia,
                gastosGenerales = uiState.gastosGenerales,
                inversiones = uiState.inversiones,
                productosElaborados = uiState.productosElaborados,
                recetaIngredientes = uiState.recetaIngredientes,
                materiasPrimas = uiState.materiasPrimas,
                tarifasPagoBebidas = uiState.tarifasPagoBebidas
            )

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            drawMercaderiaCostSheetContent(canvas, paint, costSheet, mercaderia)

            pdfDocument.finishPage(page)

            val safeName = costSheet.product.name.trim().replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_-]"), "_")
            val fileName = "Ficha_Costo_${safeName}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(context, "Ficha PDF guardada: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportAllMercaderiasCostSheetsZip(context: Context, uiState: MainUiState) {
        try {
            val activeMercaderias = uiState.mercaderias.filter { it.isActive }
            if (activeMercaderias.isEmpty()) {
                Toast.makeText(context, "No hay mercaderías activas para exportar", Toast.LENGTH_SHORT).show()
                return
            }

            val tempDir = File(context.cacheDir, "fichas_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            val generatedPdfFiles = mutableListOf<File>()

            for (merc in activeMercaderias) {
                val costSheet = CostCalculationHelper.calculateMercaderiaCostSheet(
                    mercaderia = merc,
                    mercaderias = uiState.mercaderias,
                    products = uiState.products,
                    movimientos = uiState.movimientosMercaderia,
                    gastosGenerales = uiState.gastosGenerales,
                    inversiones = uiState.inversiones,
                    productosElaborados = uiState.productosElaborados,
                    recetaIngredientes = uiState.recetaIngredientes,
                    materiasPrimas = uiState.materiasPrimas,
                    tarifasPagoBebidas = uiState.tarifasPagoBebidas
                )

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint()

                drawMercaderiaCostSheetContent(canvas, paint, costSheet, merc)

                pdfDocument.finishPage(page)

                val safeName = costSheet.product.name.trim().replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_-]"), "_")
                val pdfFile = File(tempDir, "Ficha_Costo_${safeName}_${merc.id}.pdf")
                pdfDocument.writeTo(FileOutputStream(pdfFile))
                pdfDocument.close()
                generatedPdfFiles.add(pdfFile)
            }

            // Create ZIP file
            val zipFileName = "Fichas_Costo_Mercaderias_${System.currentTimeMillis()}.zip"
            val zipFile = File(context.getExternalFilesDir(null), zipFileName)
            java.util.zip.ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (file in generatedPdfFiles) {
                    val entry = java.util.zip.ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }

            // Clean temp files
            tempDir.deleteRecursively()

            Toast.makeText(context, "ZIP generado con ${generatedPdfFiles.size} fichas: ${zipFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar ZIP: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawMercaderiaCostSheetContent(
        canvas: Canvas,
        paint: Paint,
        costSheet: MercaderiaCostSheet,
        mercaderia: com.example.data.local.model.Mercaderia
    ) {
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Header Banner
        paint.color = Color.parseColor("#1E293B") // ElQadreNavy
        canvas.drawRect(40f, 40f, 555f, 110f, paint)

        paint.color = Color.parseColor("#F59E0B") // ElQadreGold
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("EL QADRE — FICHA DE COSTO DE MERCADERÍA", 55f, 75f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Producto: ${costSheet.product.name} | Código: ${costSheet.product.code} | Destino: BARRA", 55f, 95f, paint)

        var y = 140f
        paint.color = Color.parseColor("#334155")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("1. INFORMACIÓN DEL PRODUCTO", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Unidad de Medida / Presentación: ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Existencia Actual: ${costSheet.currentStock} ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Costo de Adquisición Original: $${String.format("%.2f", costSheet.acquisitionCost)} CUP / ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Valor Total en Existencia: $${String.format("%.2f", costSheet.totalAcquisitionValue)} CUP", 50f, y, paint)
        y += 18f
        canvas.drawText("Participación en Prorrateo: ${String.format("%.2f", costSheet.porcentajeParticipacion)}%", 50f, y, paint)

        y += 28f
        paint.color = Color.parseColor("#334155")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("2. EGRESOS POR RATEO ECONÓMICO", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#1E293B")
        canvas.drawText("Gastos Generales Indirectos: $${String.format("%.2f", costSheet.gastosGeneralesDiariosTotales)} CUP / día", 50f, y, paint)
        y += 18f
        canvas.drawText("Depreciación por Inversiones: $${String.format("%.2f", costSheet.depreciacionInversionesDiariaTotales)} CUP / día", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        canvas.drawText("Costos Indirectos Generales: $${String.format("%.2f", costSheet.costosIndirectosDiariosTotales)} CUP / día", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Costo Indirecto General por Día: $${String.format("%.2f", costSheet.costosIndirectosDiariosTotales)} CUP / día", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#B45309")
        canvas.drawText("GASTO UNITARIO INDIRECTO: $${String.format("%.4f", costSheet.gastoIndirectoUnitario)} CUP / ud", 50f, y, paint)

        y += 28f
        paint.color = Color.parseColor("#334155")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("3. ESTRUCTURA DEL COSTO UNITARIO", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#1E293B")
        canvas.drawText("Costo Adquisición ($${String.format("%.2f", costSheet.acquisitionCost)}) + Gastos Directos ($${String.format("%.2f", costSheet.directExpenses)}) = Costo Directo: $${String.format("%.2f", costSheet.costoDirectoUnitario)} CUP", 50f, y, paint)
        y += 18f
        canvas.drawText("• Costo Directo Unitario: $${String.format("%.2f", costSheet.costoDirectoUnitario)} CUP", 50f, y, paint)
        y += 18f
        canvas.drawText("• Gasto Unitario Indirecto: $${String.format("%.4f", costSheet.gastoIndirectoUnitario)} CUP", 50f, y, paint)
        y += 18f
        canvas.drawText("• Pago a Personal por Unidad: $${String.format("%.2f", costSheet.totalPagoPersonalUnitario)} CUP", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#047857")
        canvas.drawText("COSTO UNITARIO FINAL: $${String.format("%.2f", costSheet.costoTotalUnitario)} CUP", 50f, y, paint)

        y += 28f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("4. PRECIO DE REFERENCIA Y PRECIO DEFINITIVO", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Precio de Referencia Sugerido (+${costSheet.targetMarginPct.toInt()}%): $${String.format("%.2f", costSheet.precioReferencia)} CUP", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#D97706")
        canvas.drawText("Precio Definitivo en Catálogo: $${String.format("%.2f", costSheet.precioDefinitivo)} CUP", 50f, y, paint)
        y += 18f
        paint.color = Color.parseColor("#047857")
        canvas.drawText("Utilidad Real Unitaria: $${String.format("%.2f", costSheet.utilidadUnitaria)} CUP (${String.format("%.2f", costSheet.margenPorcentual)}% margen)", 50f, y, paint)
        y += 18f
        canvas.drawText("Utilidad Total Proyectada (Stock ${costSheet.currentStock} u): $${String.format("%.2f", costSheet.totalUtilidadProyectada)} CUP", 50f, y, paint)
    }
}
