package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.Jornada
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import com.example.ui.screens.admin.ConsolidatedIngredientItem
import com.example.ui.screens.admin.calculateConsolidatedIngredients
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TandaPdfItem(
    val tandaNumber: String,
    val isTanda00: Boolean,
    val dateStr: String,
    val baseMateriaPrimaName: String,
    val baseQuantityFormatted: String,
    val finalQuantityFormatted: String,
    val specialPresentationDetail: String,
    val rendimientoFormatted: String,
    val costFormatted: String,
    val unitCostFormatted: String,
    val potentialRevenueFormatted: String,
    val rawCost: Double,
    val rawPotentialRevenue: Double,
    val rawBaseQty: Double,
    val rawFinalQty: Double,
    val rawYieldUnits: Double,
    val rawUnit: String,
    val rawBaseUnit: String,
    val observation: String = ""
)

data class ProductTandasPdfGroup(
    val productName: String,
    val productionUnit: String,
    val tandas: List<TandaPdfItem>
)

/**
 * Generador de PDF oficial para INFORME DE JORNADA DE TANDAS
 * Exporta fielmente:
 * - DATOS HISTÓRICOS REGISTRADOS: No recalcula con recetas o precios posteriores.
 * - IDENTIFICACIÓN DE JORNADA: Número, fechas, responsables, totales.
 * - DETALLE DE TANDAS: Números, insumos, cantidades, presentaciones especiales, unidades equivalentes, rendimiento, costos e ingresos.
 * - CONSUMO CONSOLIDADO DE INGREDIENTES de la jornada (Harina en lb, sin duplicados).
 * - UNIDADES PENDIENTES AL CIERRE.
 * - TOTALES Y GRÁFICOS ANALÍTICOS (Pastel de producción y Rendimiento por tanda).
 * - Manejo robusto de múltiples páginas sin cortes.
 */
object TandasJornadaPdfExporter {

    private val CHART_PALETTE = intArrayOf(
        Color.parseColor("#2563EB"), // Blue
        Color.parseColor("#059669"), // Emerald
        Color.parseColor("#D97706"), // Amber
        Color.parseColor("#7C3AED"), // Purple
        Color.parseColor("#DC2626"), // Red
        Color.parseColor("#0891B2"), // Cyan
        Color.parseColor("#0F766E"), // Teal
        Color.parseColor("#64748B"), // Slate
        Color.parseColor("#E11D48"), // Rose
        Color.parseColor("#4F46E5")  // Indigo
    )

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
            val hourOnlyFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Agrupar tandas por producto conservando datos históricos
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
                    val isTanda00 = tanda.tandaNumber == "00"
                    val baseUnit = tanda.baseQuantityUnit.ifBlank { "lb" }
                    val bQtyFormatted = if (tanda.baseQuantityUsed % 1.0 == 0.0) {
                        tanda.baseQuantityUsed.toInt().toString()
                    } else {
                        "%.2f".format(tanda.baseQuantityUsed).trimEnd('0').trimEnd('.')
                    }

                    val finalQty = if (tanda.actualYield > 0.0) tanda.actualYield else if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                    val fQtyFormatted = if (finalQty % 1.0 == 0.0) finalQty.toInt().toString() else "%.1f".format(finalQty)

                    // Preservar equivalencia histórica almacenada en la tanda
                    val presEquiv = if (tanda.specialPresentationEquivalence > 0.0) tanda.specialPresentationEquivalence else 1.0
                    val specialUnitsEq = if (tanda.specialPresentationQty > 0.0) tanda.specialPresentationQty * presEquiv else 0.0
                    val totalYieldUnits = finalQty + specialUnitsEq

                    val specialPresDetail = if (tanda.specialPresentationQty > 0.0) {
                        val sQtyStr = if (tanda.specialPresentationQty % 1.0 == 0.0) tanda.specialPresentationQty.toInt().toString() else "%.1f".format(tanda.specialPresentationQty)
                        val sEqStr = if (specialUnitsEq % 1.0 == 0.0) specialUnitsEq.toInt().toString() else "%.1f".format(specialUnitsEq)
                        "+ $sQtyStr ${tanda.specialPresentationName.ifBlank { "Esp." }} ($sEqStr eq.)"
                    } else ""

                    val rendVal = if (tanda.baseQuantityUsed > 0.0) totalYieldUnits / tanda.baseQuantityUsed else 0.0
                    val rendFormatted = if (rendVal % 1.0 == 0.0) rendVal.toInt().toString() else "%.2f".format(rendVal)
                    val rendFullText = if (isTanda00) {
                        "Tanda 00 (Pendiente)"
                    } else if (specialUnitsEq > 0.0) {
                        val totEqFormatted = if (totalYieldUnits % 1.0 == 0.0) totalYieldUnits.toInt().toString() else "%.1f".format(totalYieldUnits)
                        "$totEqFormatted eq. ÷ $bQtyFormatted $baseUnit = $rendFormatted"
                    } else {
                        "$fQtyFormatted $pUnit ÷ $bQtyFormatted $baseUnit = $rendFormatted"
                    }

                    // Precios e ingresos históricos registrados
                    val effectiveSalePrice = if (tanda.salePrice > 0.0) {
                        tanda.salePrice
                    } else {
                        prod?.price ?: resolveSalePrice(prod, tanda)
                    }
                    val potRev = if (tanda.expectedRevenue > 0.0 && tanda.actualYield == (if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield)) {
                        tanda.expectedRevenue
                    } else {
                        finalQty * effectiveSalePrice
                    }

                    val dateStr = if (tanda.date > 0) hourOnlyFormatter.format(Date(tanda.date)) else ""

                    TandaPdfItem(
                        tandaNumber = tanda.tandaNumber,
                        isTanda00 = isTanda00,
                        dateStr = dateStr,
                        baseMateriaPrimaName = tanda.baseMateriaPrimaName.ifBlank { "Harina" },
                        baseQuantityFormatted = if (isTanda00) "0 $baseUnit (Pend.)" else "$bQtyFormatted $baseUnit",
                        finalQuantityFormatted = "$fQtyFormatted $pUnit",
                        specialPresentationDetail = specialPresDetail,
                        rendimientoFormatted = rendFullText,
                        costFormatted = "$${"%.2f".format(tanda.totalBatchCost)}",
                        unitCostFormatted = if (tanda.realUnitCost > 0.0) "$${"%.2f".format(tanda.realUnitCost)}/u" else "",
                        potentialRevenueFormatted = "$${"%.2f".format(potRev)}",
                        rawCost = tanda.totalBatchCost,
                        rawPotentialRevenue = potRev,
                        rawBaseQty = tanda.baseQuantityUsed,
                        rawFinalQty = finalQty,
                        rawYieldUnits = totalYieldUnits,
                        rawUnit = pUnit,
                        rawBaseUnit = baseUnit,
                        observation = tanda.observation
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
            val consolidatedJornadaIngredients = calculateConsolidatedIngredients(tandas)

            fun drawHeader(titleSuffix: String = "INFORME OFICIAL DE TANDAS POR JORNADA") {
                // Header Banner Navy
                paint.color = Color.parseColor("#0F172A")
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 56f, paint)

                // Business Name & Title
                paint.color = Color.parseColor("#F59E0B")
                paint.textSize = 15f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(businessName.ifBlank { "EL QADRE" }.uppercase(), 30f, 26f, paint)

                paint.color = Color.WHITE
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(titleSuffix, 30f, 44f, paint)

                // Right side
                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 8.5f
                val fechaJornadaStr = if (jornada.openedAt > 0) dateOnlyFormatter.format(Date(jornada.openedAt)) else dateOnlyFormatter.format(Date())
                canvas.drawText("Jornada: #${jornada.id} ($fechaJornadaStr)", (pageWidth - 210).toFloat(), 26f, paint)
                canvas.drawText("Página $pageNumber", (pageWidth - 210).toFloat(), 44f, paint)

                // Franja decorativa dorada
                paint.color = Color.parseColor("#F59E0B")
                canvas.drawRect(0f, 56f, pageWidth.toFloat(), 59f, paint)
            }

            fun drawFooter(curPage: Int) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("El Qadre • Informe Histórico de Producción y Tandas", 30f, pageHeight - 20f, paint)
                val pageStr = "Página $curPage"
                val pWidth = paint.measureText(pageStr)
                canvas.drawText(pageStr, pageWidth - 30f - pWidth, pageHeight - 20f, paint)
                canvas.drawLine(30f, pageHeight - 28f, pageWidth - 30f, pageHeight - 28f, linePaint)
            }

            fun checkNewPage(neededHeight: Float, curY: Float): Float {
                if (curY + neededHeight > pageHeight - 45f) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader()
                    return 78f
                }
                return curY
            }

            // ==========================================
            // PÁGINA 1: IDENTIFICACIÓN Y TABLAS DE TANDAS
            // ==========================================
            drawHeader()
            var y = 74f

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
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val respOpen = jornada.openedBy.ifBlank { "admin" }
            val respClose = jornada.closedBy?.ifBlank { null } ?: "Pendiente"
            canvas.drawText("Responsable apertura: $respOpen   |   Cerrado por: $respClose", 42f, y + 36f, paint)

            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total Productos: ${groups.size}   |   Total Tandas: $totalTandasCount   |   Costo Total: $${"%.2f".format(totalCostAll)} CUP   |   Ing. Potenciales: $${"%.2f".format(totalRevAll)} CUP", 42f, y + 54f, paint)

            y += 80f

            // 2. PRODUCTOS Y SUS TANDAS
            if (groups.isEmpty()) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 10.5f
                canvas.drawText("No se registraron tandas de producción durante esta jornada.", 42f, y + 20f, paint)
                y += 36f
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
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    canvas.drawText("TANDA", 38f, y + 12f, paint)
                    canvas.drawText("INSUMO BASE", 95f, y + 12f, paint)
                    canvas.drawText("PRODUCCIÓN", 175f, y + 12f, paint)
                    canvas.drawText("RENDIMIENTO (FÓRMULA)", 265f, y + 12f, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("COSTO", 475f, y + 12f, paint)
                    canvas.drawText("ING. POTENCIAL", (pageWidth - 38).toFloat(), y + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y += 18f

                    // Filas de cada tanda
                    var isEven = false
                    for (tItem in group.tandas) {
                        val rowHeight = if (tItem.specialPresentationDetail.isNotBlank() || tItem.observation.isNotBlank()) 28f else 18f
                        y = checkNewPage(rowHeight, y)

                        if (isEven) {
                            paint.color = Color.parseColor("#F8FAFC")
                            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + rowHeight, paint)
                        }
                        isEven = !isEven

                        paint.color = if (tItem.isTanda00) Color.parseColor("#92400E") else Color.parseColor("#0F172A")
                        paint.textSize = 8.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        val tNumLabel = if (tItem.isTanda00) "Tanda 00" else "Tanda ${tItem.tandaNumber}"
                        canvas.drawText(tNumLabel, 38f, y + 12f, paint)

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.color = Color.parseColor("#334155")
                        canvas.drawText(tItem.baseQuantityFormatted, 95f, y + 12f, paint)

                        paint.color = Color.parseColor("#0284C7")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tItem.finalQuantityFormatted, 175f, y + 12f, paint)

                        if (tItem.specialPresentationDetail.isNotBlank()) {
                            paint.color = Color.parseColor("#15803D")
                            paint.textSize = 7f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            canvas.drawText(tItem.specialPresentationDetail, 175f, y + 22f, paint)
                        }

                        paint.color = Color.parseColor("#0F766E")
                        paint.textSize = 8f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tItem.rendimientoFormatted, 265f, y + 12f, paint)

                        paint.textAlign = Paint.Align.RIGHT
                        paint.color = Color.parseColor("#BE123C")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText(tItem.costFormatted, 475f, y + 12f, paint)

                        paint.color = Color.parseColor("#047857")
                        canvas.drawText(tItem.potentialRevenueFormatted, (pageWidth - 38).toFloat(), y + 12f, paint)
                        paint.textAlign = Paint.Align.LEFT

                        // Línea divisoria fina
                        canvas.drawLine(30f, y + rowHeight, (pageWidth - 30).toFloat(), y + rowHeight, linePaint)
                        y += rowHeight
                    }

                    // Subtotal del producto
                    val prodCost = group.tandas.sumOf { it.rawCost }
                    val prodRev = group.tandas.sumOf { it.rawPotentialRevenue }
                    val prodUnitsTot = group.tandas.sumOf { it.rawFinalQty }
                    y = checkNewPage(22f, y)

                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 18f, paint)

                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val prodTotStr = if (prodUnitsTot % 1.0 == 0.0) prodUnitsTot.toInt().toString() else "%.1f".format(prodUnitsTot)
                    canvas.drawText("Subtotal ${group.productName} ($prodTotStr ${group.productionUnit}):", 38f, y + 12f, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    paint.color = Color.parseColor("#BE123C")
                    canvas.drawText("$${"%.2f".format(prodCost)} CUP", 475f, y + 12f, paint)

                    paint.color = Color.parseColor("#047857")
                    canvas.drawText("$${"%.2f".format(prodRev)} CUP", (pageWidth - 38).toFloat(), y + 12f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y += 26f
                }
            }

            // 3. CONSUMO CONSOLIDADO DE INGREDIENTES EN LA JORNADA
            if (consolidatedJornadaIngredients.isNotEmpty()) {
                y = checkNewPage(80f, y)

                paint.color = Color.parseColor("#0F172A")
                canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 20f, paint)
                paint.color = Color.WHITE
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("CONSUMO CONSOLIDADO DE INGREDIENTES EN LA JORNADA", 38f, y + 14f, paint)
                y += 20f

                paint.color = Color.parseColor("#E2E8F0")
                canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 16f, paint)
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("N°", 38f, y + 11f, paint)
                canvas.drawText("INGREDIENTE", 75f, y + 11f, paint)
                canvas.drawText("CANTIDAD TOTAL CONSUMIDA", 250f, y + 11f, paint)
                canvas.drawText("UNIDAD", 440f, y + 11f, paint)
                y += 16f

                consolidatedJornadaIngredients.forEachIndexed { idx, ing ->
                    y = checkNewPage(18f, y)
                    val isEven = idx % 2 == 0
                    paint.color = if (isEven) Color.parseColor("#FFFFFF") else Color.parseColor("#F8FAFC")
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 16f, paint)

                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("${idx + 1}", 38f, y + 11f, paint)
                    canvas.drawText(ing.name, 75f, y + 11f, paint)

                    val qtyFormatted = if (ing.totalQuantity % 1.0 == 0.0) ing.totalQuantity.toInt().toString() else "%.2f".format(ing.totalQuantity).trimEnd('0').trimEnd('.')
                    canvas.drawText(qtyFormatted, 250f, y + 11f, paint)

                    paint.color = Color.parseColor("#475569")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(ing.unit.ifBlank { "u" }, 440f, y + 11f, paint)

                    canvas.drawLine(30f, y + 16f, (pageWidth - 30).toFloat(), y + 16f, linePaint)
                    y += 16f
                }
                y += 10f
            }

            // 4. UNIDADES PENDIENTES AL CIERRE
            y = checkNewPage(60f, y)

            paint.color = Color.parseColor("#334155")
            canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("UNIDADES PENDIENTES AL CIERRE DE LA JORNADA", 38f, y + 14f, paint)

            y += 20f

            val pendingProducts = groups.map { group ->
                val pendingStr = group.tandas.firstNotNullOfOrNull { tanda ->
                    if (tanda.observation.contains("Pendientes:")) {
                        val part = tanda.observation.substringAfter("Pendientes:").trim()
                        if (part.isNotBlank() && part != "0") part else null
                    } else null
                }
                group.productName to pendingStr
            }

            val hasAnyPending = pendingProducts.any { it.second != null }

            if (!hasAnyPending) {
                y = checkNewPage(24f, y)
                paint.color = Color.parseColor("#F8FAFC")
                canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 20f, paint)

                paint.color = Color.parseColor("#64748B")
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Sin unidades pendientes registradas.", 38f, y + 14f, paint)
                y += 26f
            } else {
                for ((prodName, pStr) in pendingProducts) {
                    y = checkNewPage(22f, y)
                    paint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(30f, y, (pageWidth - 30).toFloat(), y + 18f, paint)

                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(prodName.uppercase(), 38f, y + 12f, paint)

                    paint.color = if (pStr != null) Color.parseColor("#0284C7") else Color.parseColor("#64748B")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, if (pStr != null) Typeface.BOLD else Typeface.NORMAL)
                    canvas.drawText(pStr ?: "Sin unidades pendientes", 220f, y + 12f, paint)

                    canvas.drawLine(30f, y + 18f, (pageWidth - 30).toFloat(), y + 18f, linePaint)
                    y += 18f
                }
                y += 8f
            }

            // 5. RESUMEN FINAL CONSOLIDADO
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

            // Terminar página(s) de tablas
            drawFooter(pageNumber)
            pdfDocument.finishPage(page)

            // ==========================================
            // PÁGINA 2: GRÁFICOS ANALÍTICOS DE TANDAS
            // ==========================================
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas

            drawHeader("GRÁFICOS ANALÍTICOS DE PRODUCCIÓN")

            var chartY = 74f
            val allTandasFlattened = groups.flatMap { it.tandas }
            val totalProductionUnits = allTandasFlattened.sumOf { it.rawFinalQty }

            if (allTandasFlattened.isEmpty() || totalProductionUnits <= 0.0) {
                paint.color = Color.parseColor("#64748B")
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("No hay datos de producción registrados para generar gráficos en esta jornada.", 42f, chartY + 30f, paint)
            } else {
                // 1. GRÁFICO DE PASTEL: PARTICIPACIÓN DE PRODUCCIÓN
                val pieCardHeight = 240f
                paint.color = Color.parseColor("#F8FAFC")
                canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)
                paint.color = Color.parseColor("#CBD5E1")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + pieCardHeight, 8f, 8f, paint)
                paint.style = Paint.Style.FILL

                // Título del gráfico de pastel
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("1. PARTICIPACIÓN DE PRODUCCIÓN (VOLUMEN FINAL)", 44f, chartY + 20f, paint)

                // Renderizado del pastel
                val pieCenterX = 120f
                val pieCenterY = chartY + 130f
                val pieRadius = 65f
                val pieOval = RectF(pieCenterX - pieRadius, pieCenterY - pieRadius, pieCenterX + pieRadius, pieCenterY + pieRadius)

                var startAngle = -90f
                val slicePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

                allTandasFlattened.forEachIndexed { index, tItem ->
                    val colorInt = CHART_PALETTE[index % CHART_PALETTE.size]
                    val sweepAngle = ((tItem.rawFinalQty / totalProductionUnits) * 360f).toFloat()
                    if (sweepAngle > 0f) {
                        slicePaint.color = colorInt
                        canvas.drawArc(pieOval, startAngle, sweepAngle, true, slicePaint)
                        startAngle += sweepAngle
                    }
                }

                // Donut hole interior
                paint.color = Color.WHITE
                canvas.drawCircle(pieCenterX, pieCenterY, 30f, paint)

                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("TOTAL", pieCenterX, pieCenterY - 2f, paint)
                val totalQtyStr = if (totalProductionUnits % 1.0 == 0.0) totalProductionUnits.toInt().toString() else "%.1f".format(totalProductionUnits)
                canvas.drawText(totalQtyStr, pieCenterX, pieCenterY + 10f, paint)
                paint.textAlign = Paint.Align.LEFT

                // Leyenda del pastel
                var legendY = chartY + 44f
                val legendX = 210f
                allTandasFlattened.take(8).forEachIndexed { index, tItem ->
                    val colorInt = CHART_PALETTE[index % CHART_PALETTE.size]
                    val pct = (tItem.rawFinalQty / totalProductionUnits) * 100.0
                    val pctFormatted = "%.1f".format(pct)
                    val tLabel = if (tItem.isTanda00) "Tanda 00" else "Tanda ${tItem.tandaNumber}"

                    // Box de color
                    slicePaint.color = colorInt
                    canvas.drawRoundRect(legendX, legendY, legendX + 10f, legendY + 10f, 2f, 2f, slicePaint)

                    // Texto de la leyenda
                    paint.color = Color.parseColor("#1E293B")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$tLabel:", legendX + 16f, legendY + 8f, paint)

                    paint.color = Color.parseColor("#475569")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText("${tItem.finalQuantityFormatted} ($pctFormatted%)", legendX + 70f, legendY + 8f, paint)

                    legendY += 18f
                }

                if (allTandasFlattened.size > 8) {
                    paint.color = Color.parseColor("#64748B")
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    canvas.drawText("+ ${allTandasFlattened.size - 8} tandas adicionales resumidas en total.", legendX + 16f, legendY + 8f, paint)
                }

                chartY += pieCardHeight + 16f

                // 2. GRÁFICO DE BARRAS: EFICIENCIA Y RENDIMIENTO POR TANDA
                val barCardHeight = (allTandasFlattened.take(8).size * 26f + 50f).coerceAtLeast(160f)
                paint.color = Color.parseColor("#F8FAFC")
                canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + barCardHeight, 8f, 8f, paint)
                paint.color = Color.parseColor("#CBD5E1")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(30f, chartY, (pageWidth - 30).toFloat(), chartY + barCardHeight, 8f, 8f, paint)
                paint.style = Paint.Style.FILL

                // Título del gráfico de barras
                paint.color = Color.parseColor("#0F172A")
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("2. RENDIMIENTO Y EFICIENCIA (COEFICIENTE DE PRODUCCIÓN)", 44f, chartY + 20f, paint)

                // Barras horizontales
                var barY = chartY + 44f
                val maxCoeff = allTandasFlattened.maxOfOrNull {
                    val unitsForYield = if (it.rawYieldUnits > 0.0) it.rawYieldUnits else it.rawFinalQty
                    if (it.rawBaseQty > 0.0) unitsForYield / it.rawBaseQty else 0.0
                }?.coerceAtLeast(1.0) ?: 1.0

                val maxBarWidth = 230f
                val barStartX = 150f

                allTandasFlattened.take(8).forEachIndexed { index, tItem ->
                    val colorInt = CHART_PALETTE[index % CHART_PALETTE.size]
                    val unitsForYield = if (tItem.rawYieldUnits > 0.0) tItem.rawYieldUnits else tItem.rawFinalQty
                    val coeff = if (tItem.rawBaseQty > 0.0) unitsForYield / tItem.rawBaseQty else 0.0
                    val coeffFormatted = if (coeff % 1.0 == 0.0) coeff.toInt().toString() else "%.2f".format(coeff)
                    val tLabel = if (tItem.isTanda00) "Tanda 00" else "Tanda ${tItem.tandaNumber}"

                    // Label tanda
                    paint.color = Color.parseColor("#0F172A")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(tLabel, 44f, barY + 9f, paint)

                    // Track de fondo
                    paint.color = Color.parseColor("#E2E8F0")
                    canvas.drawRoundRect(barStartX, barY, barStartX + maxBarWidth, barY + 12f, 4f, 4f, paint)

                    // Barra rellena proporcional
                    val barWidth = ((coeff / maxCoeff) * maxBarWidth).toFloat().coerceAtLeast(4f)
                    slicePaint.color = colorInt
                    canvas.drawRoundRect(barStartX, barY, barStartX + barWidth, barY + 12f, 4f, 4f, slicePaint)

                    // Valor de rendimiento
                    paint.color = Color.parseColor("#0F766E")
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("$coeffFormatted ${tItem.rawUnit} / ${tItem.rawBaseUnit}", barStartX + maxBarWidth + 10f, barY + 9f, paint)

                    barY += 24f
                }
            }

            drawFooter(pageNumber)
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
                putExtra(Intent.EXTRA_TEXT, "Adjunto informe oficial de tandas de producción de la Jornada #$jornadaId con el desglose de productos, consumo consolidado y gráficos analíticos.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Descargar / Compartir PDF de Tandas (WhatsApp, etc.)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "PDF guardado: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF guardado en: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}
