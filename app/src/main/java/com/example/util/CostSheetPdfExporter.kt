package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import com.example.data.local.model.Product
import com.example.ui.viewmodel.MainUiState
import java.io.File
import java.io.FileOutputStream

object CostSheetPdfExporter {

    fun exportSingleCostSheet(context: Context, product: Product, uiState: MainUiState) {
        try {
            val costSheet = CostCalculationHelper.calculateCostSheet(
                product = product,
                uiState = uiState
            )

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            drawCostSheetContent(canvas, paint, costSheet, product)

            pdfDocument.finishPage(page)

            val fileName = "Ficha_Costo_${product.name.replace(" ", "_")}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(context, "PDF guardado en: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportAllCostSheets(context: Context, uiState: MainUiState) {
        try {
            val cocinaProducts = uiState.products.filter { it.destination == "COCINA" }
            if (cocinaProducts.isEmpty()) {
                Toast.makeText(context, "No hay productos de cocina para exportar", Toast.LENGTH_SHORT).show()
                return
            }

            val pdfDocument = PdfDocument()
            var pageIndex = 1

            for (product in cocinaProducts) {
                val costSheet = CostCalculationHelper.calculateCostSheet(
                    product = product,
                    uiState = uiState
                )

                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint()

                drawCostSheetContent(canvas, paint, costSheet, product)

                pdfDocument.finishPage(page)
                pageIndex++
            }

            val fileName = "Fichas_Costo_Todas_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(context, "Todas las fichas exportadas (${cocinaProducts.size}): ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar PDF conjunto: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawCostSheetContent(canvas: Canvas, paint: Paint, costSheet: ProductCostSheet, product: Product) {
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Header Banner
        paint.color = Color.parseColor("#1E293B") // ElQadreNavy
        canvas.drawRect(40f, 40f, 555f, 110f, paint)

        paint.color = Color.parseColor("#F59E0B") // ElQadreGold
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("EL QADRE — FICHA DE COSTO DE PRODUCCIÓN", 55f, 75f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Producto: ${product.name} | Categoría: ${product.category}", 55f, 95f, paint)

        var y = 140f
        paint.color = Color.parseColor("#334155")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("1. IDENTIFICACIÓN Y DATOS BÁSICOS", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Unidad de Medida: ${costSheet.productionUnit}", 50f, y, paint)
        y += 18f
        canvas.drawText("Rendimiento Base: ${costSheet.baseYield} ${costSheet.productionUnit}", 50f, y, paint)
        y += 18f
        canvas.drawText("Precio Definitivo Configurado: ${if (costSheet.hasPrecioDefinitivo) "SÍ ($${String.format("%.2f", costSheet.precioDefinitivo)} CUP)" else "NO"}", 50f, y, paint)

        y += 30f
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("2. MATERIAS PRIMAS Y COSTO DIRECTO", 40f, y, paint)

        y += 20f
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Ingrediente", 50f, y, paint)
        canvas.drawText("Cant.", 300f, y, paint)
        canvas.drawText("C. Unit.", 380f, y, paint)
        canvas.drawText("Total", 470f, y, paint)

        y += 5f
        paint.color = Color.parseColor("#CBD5E1")
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 16f
        paint.color = Color.parseColor("#1E293B")
        paint.isFakeBoldText = false

        if (costSheet.ingredientDetails.isEmpty()) {
            canvas.drawText("No hay ingredientes registrados en la receta.", 50f, y, paint)
            y += 20f
        } else {
            for (ing in costSheet.ingredientDetails) {
                if (y > 720f) break
                val ingName = ing.materiaPrima?.name ?: "Ingrediente"
                canvas.drawText(ingName, 50f, y, paint)
                canvas.drawText("${ing.quantity} ${ing.unit}", 300f, y, paint)
                canvas.drawText("$${String.format("%.2f", ing.unitCost)}", 380f, y, paint)
                canvas.drawText("$${String.format("%.2f", ing.totalCost)}", 470f, y, paint)
                y += 18f
            }
        }

        y += 10f
        paint.color = Color.parseColor("#CBD5E1")
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 25f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("3. COSTOS ESTRUCTURALES Y FINALES", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Costo Directo Unitario (CDU): $${String.format("%.2f", costSheet.costoDirectoUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Gasto General Prorrateado por Unidad: $${String.format("%.4f", costSheet.gastoGeneralUnitarioProrrateo)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Depreciación de Inversiones por Unidad: $${String.format("%.4f", costSheet.depreciacionInversionesUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Pago a Personal por Unidad: $${String.format("%.2f", costSheet.totalPagoPersonalUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#047857")
        canvas.drawText("COSTO UNITARIO FINAL: $${String.format("%.2f", costSheet.costoTotalUnitario)} CUP", 50f, y, paint)

        y += 25f
        paint.color = Color.parseColor("#1E293B")
        paint.isFakeBoldText = true
        canvas.drawText("4. FIJACIÓN DE PRECIOS", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Precio de Referencia Sugerido (+30%): $${String.format("%.2f", costSheet.precioReferencia)} CUP", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#D97706")
        canvas.drawText("Precio Definitivo en Catálogo: $${String.format("%.2f", costSheet.precioDefinitivo)} CUP", 50f, y, paint)
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

            val safeName = costSheet.product.name.replace(" ", "_").replace("/", "_")
            val fileName = "Ficha_Mercaderia_${safeName}.pdf"
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

                val safeName = costSheet.product.name.replace(" ", "_").replace("/", "_")
                val pdfFile = File(tempDir, "Ficha_${safeName}_${merc.id}.pdf")
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
        canvas.drawText("1. IDENTIFICACIÓN Y ADQUISICIÓN", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Unidad de Medida / Presentación: ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Existencia Actual: ${costSheet.currentStock} ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Costo de Adquisición Original: $${String.format("%.2f", costSheet.acquisitionCost)} CUP / ${mercaderia.unitOfMeasure}", 50f, y, paint)
        y += 18f
        canvas.drawText("Valor Total en Existencia: $${String.format("%.2f", costSheet.totalAcquisitionValue)} CUP (Participación: ${String.format("%.2f", costSheet.porcentajeParticipacion)}%)", 50f, y, paint)

        y += 30f
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("2. GASTOS INDIRECTOS Y DEPRECIACIÓN ASIGNADOS", 40f, y, paint)

        y += 20f
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Concepto", 50f, y, paint)
        canvas.drawText("Tipo", 220f, y, paint)
        canvas.drawText("Período (Importe Orig.)", 290f, y, paint)
        canvas.drawText("Unitario", 480f, y, paint)

        y += 5f
        paint.color = Color.parseColor("#CBD5E1")
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 16f
        paint.color = Color.parseColor("#1E293B")
        paint.isFakeBoldText = false

                // Breakdown as requested for Mercaderías (Traceability)
        for (gasto in costSheet.detailedExpenses) {
            val typeStr = if (gasto.isSpecific) "PUNTUAL" else "GENERAL"
            canvas.drawText(gasto.name.take(28), 50f, y, paint)
            canvas.drawText(typeStr, 220f, y, paint)
            val periodStr = if (gasto.period.uppercase() in listOf("ÚNICO", "UNICO")) "Único ($${String.format("%.2f", gasto.originalAmount)})" else "${gasto.period} ($${String.format("%.2f", gasto.originalAmount)})"
            canvas.drawText(periodStr, 290f, y, paint)
            canvas.drawText("$${String.format("%.4f", gasto.allocatedUnitAmount)}", 480f, y, paint)
            y += 18f
            
            if (y > 780f) {
                canvas.drawText("... más gastos", 50f, y, paint)
                y += 18f
                break
            }
        }
        for (inv in costSheet.detailedInversions) {
            val typeStr = if (inv.isSpecific) "INV. PUNTUAL" else "INV. GENERAL"
            canvas.drawText(inv.name.take(28), 50f, y, paint)
            canvas.drawText(typeStr, 220f, y, paint)
            canvas.drawText("Vida: ${inv.usefulLifeText} ($${String.format("%.2f", inv.originalAmount)})", 290f, y, paint)
            canvas.drawText("$${String.format("%.4f", inv.allocatedUnitAmount)}", 480f, y, paint)
            y += 18f
            
            if (y > 780f) {
                canvas.drawText("... más inversiones", 50f, y, paint)
                y += 18f
                break
            }
        }

        if (costSheet.detailedExpenses.isEmpty() && costSheet.detailedInversions.isEmpty()) {
            canvas.drawText("No hay gastos ni inversiones asignadas a este producto.", 50f, y, paint)
            y += 20f
        }

        y += 10f
        paint.color = Color.parseColor("#CBD5E1")
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 25f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("3. ESTRUCTURA DE COSTO REAL Y RENTABILIDAD", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Costo Adquisición Base: $${String.format("%.2f", costSheet.acquisitionCost)} CUP", 50f, y, paint)
        y += 18f
        canvas.drawText("Gastos Directos: $${String.format("%.2f", costSheet.directExpenses)} CUP", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        canvas.drawText("Costo Directo Unitario: $${String.format("%.2f", costSheet.costoDirectoUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Gasto General Prorrateado por Unidad: $${String.format("%.4f", costSheet.gastoGeneralUnitarioProrrateo)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Depreciación de Inversiones por Unidad: $${String.format("%.4f", costSheet.depreciacionInversionesUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Pago a Personal por Unidad: $${String.format("%.2f", costSheet.totalPagoPersonalUnitario)} CUP", 50f, y, paint)
        
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#047857")
        canvas.drawText("COSTO UNITARIO FINAL: $${String.format("%.2f", costSheet.costoTotalUnitario)} CUP", 50f, y, paint)

        y += 25f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("4. PRECIOS Y UTILIDAD PROYECTADA", 40f, y, paint)

        y += 20f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText("Precio de Referencia Sugerido: $${String.format("%.2f", costSheet.precioReferencia)} CUP", 50f, y, paint)
        y += 18f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#D97706")
        canvas.drawText("Precio Definitivo en Catálogo: $${String.format("%.2f", costSheet.precioDefinitivo)} CUP", 50f, y, paint)
        y += 18f
        paint.color = Color.parseColor("#047857")
        canvas.drawText("Utilidad Unitaria Real: $${String.format("%.2f", costSheet.utilidadUnitaria)} CUP (${String.format("%.1f", costSheet.margenPorcentual)}% margen)", 50f, y, paint)
        y += 18f
        canvas.drawText("Utilidad Total Proyectada (Stock ${costSheet.currentStock} u): $${String.format("%.2f", costSheet.totalUtilidadProyectada)} CUP", 50f, y, paint)
    }
}
