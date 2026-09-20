package com.example.util

import android.content.Context
import android.net.Uri
import com.example.ui.viewmodel.MainUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream

object AdminJsonExportHelper {

    fun buildQProduccionJson(uiState: MainUiState): String {
        val root = JSONObject()
        root.put("fileIdentifier", "Q_PRODUCCION")
        root.put("version", "1.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("businessName", uiState.businessName.ifEmpty { "Negocio" })

        // 1. Insumos (Materias Primas)
        val insumosArr = JSONArray()
        uiState.materiasPrimas.forEach { mp ->
            val mpObj = JSONObject().apply {
                put("id", mp.id)
                put("uniqueKey", "INS-${mp.id}")
                put("name", mp.name)
                put("unit", mp.unit)
                put("unitCost", mp.unitCost)
                put("stock", mp.stock)
                put("initialStock", mp.initialStock)
                put("purchasePrice", mp.purchasePrice)
                put("purchaseUnit", mp.purchaseUnit)
                put("purchaseQuantity", mp.purchaseQuantity)
                put("isActive", mp.isActive)
            }
            insumosArr.put(mpObj)
        }
        root.put("insumos", insumosArr)

        // 2. Kitchen PPD and overheads for production prorating
        val totalKitchenPpd = CostCalculationHelper.calculateTotalKitchenPpd(uiState.products, uiState.productosElaborados)
        val totalDailyExpenses = CostCalculationHelper.calculateTotalDailyOverheads(uiState.gastosGenerales)
        val totalDailyDepreciation = CostCalculationHelper.calculateTotalDailyDepreciation(uiState.inversiones, uiState.gastosGenerales)

        // 3. Productos de Producción
        val prodMap = uiState.productosElaborados.associateBy { it.productId }
        val mpMap = uiState.materiasPrimas.associateBy { it.id }
        val productosArr = JSONArray()

        val kitchenProducts = uiState.products.filter { it.destination == "COCINA" }
        kitchenProducts.forEach { prod ->
            val prodElab = prodMap[prod.id]
            val ppd = prodElab?.effectivePpd ?: 10.0

            // Recipe ingredients
            val recipeList = uiState.recetaIngredientes.filter { it.productoElaboradoId == prod.id }
            val recipeArr = JSONArray()
            var directRecipeCost = 0.0

            recipeList.forEach { ing ->
                val mp = mpMap[ing.materiaPrimaId]
                val convertedQty = if (mp != null) {
                    com.example.ui.viewmodel.UnitConverter.convert(ing.quantity, ing.unit, mp.unit) ?: ing.quantity
                } else {
                    ing.quantity
                }
                val unitCost = mp?.unitCost ?: 0.0
                val ingredientTotal = convertedQty * unitCost
                directRecipeCost += ingredientTotal

                val ingObj = JSONObject().apply {
                    put("id", ing.id)
                    put("uniqueKey", "REC-${ing.id}")
                    put("materiaPrimaId", ing.materiaPrimaId)
                    put("materiaPrimaName", mp?.name ?: "Insumo #${ing.materiaPrimaId}")
                    put("quantity", ing.quantity)
                    put("unit", ing.unit)
                    put("normalizedQuantity", convertedQty)
                    put("normalizedUnit", mp?.unit ?: ing.unit)
                    put("unitCost", unitCost)
                    put("totalCost", ingredientTotal)
                }
                recipeArr.put(ingObj)
            }

            // Prorating according to daily average sales (PPD)
            val ppdShare = if (totalKitchenPpd > 0.0) ppd / totalKitchenPpd else 0.0
            val allocatedDailyExpenses = totalDailyExpenses * ppdShare
            val allocatedDailyDepreciation = totalDailyDepreciation * ppdShare
            val unitIndirectCost = if (ppd > 0.0) (allocatedDailyExpenses + allocatedDailyDepreciation) / ppd else 0.0
            val totalUnitCost = directRecipeCost + unitIndirectCost

            val prodObj = JSONObject().apply {
                put("id", prod.id)
                put("uniqueKey", "PROD-${prod.id}")
                put("code", prod.code)
                put("name", prod.name)
                put("category", prod.category)
                put("salePrice", prod.price)
                put("unitOfMeasure", prod.unitOfMeasure)
                put("destination", prod.destination)
                put("isAvailable", prod.isAvailable)
                put("ppd", ppd)
                put("ppdParticipationRatio", ppdShare)
                put("receta", recipeArr)
                put("costoInsumosReceta", directRecipeCost)
                put("gastosCorrientesDiariosProrrateados", allocatedDailyExpenses)
                put("recuperacionInversionesDiariaProrrateada", allocatedDailyDepreciation)
                put("costoIndirectoUnitario", unitIndirectCost)
                put("costoTotalUnitario", totalUnitCost)
            }
            productosArr.put(prodObj)
        }
        root.put("productos", productosArr)

        root.put("resumenProduccion", JSONObject().apply {
            put("totalKitchenPpd", totalKitchenPpd)
            put("totalProductos", kitchenProducts.size)
            put("totalInsumos", uiState.materiasPrimas.size)
            put("gastosCorrientesDiariosTotales", totalDailyExpenses)
            put("recuperacionInversionesDiariaTotales", totalDailyDepreciation)
        })

        return root.toString(2)
    }

    fun buildQMercanciasJson(uiState: MainUiState): String {
        val root = JSONObject()
        root.put("fileIdentifier", "Q_MERCANCIAS")
        root.put("version", "1.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("businessName", uiState.businessName.ifEmpty { "Negocio" })

        // Calculate total inventory value
        val activeMercs = uiState.mercaderias.filter { it.isActive }
        val movs = uiState.movimientosMercaderia
        val totalInventoryValue = activeMercs.sumOf { m ->
            val stock = CostCalculationHelper.getMercaderiaCurrentStock(m.id, m.initialStock, movs)
            val effStock = if (stock > 0.0) stock else maxOf(m.initialStock, 1.0)
            m.acquisitionCost * effStock
        }.takeIf { it > 0.0 } ?: maxOf(1.0, activeMercs.sumOf { it.acquisitionCost })

        // Overheads for merchandise
        val totalDailyExpenses = uiState.gastosGenerales
            .filter { it.isActive && it.inversionId == null }
            .sumOf { it.dailyCost() }
        val totalDailyDepreciation = uiState.inversiones
            .sumOf { it.dailyDepreciation() }

        root.put("totalInventoryValue", totalInventoryValue)
        root.put("gastosCorrientesDiariosTotales", totalDailyExpenses)
        root.put("recuperacionInversionesDiariaTotales", totalDailyDepreciation)

        val prodMap = uiState.products.associateBy { it.id }
        val mercanciasArr = JSONArray()

        activeMercs.forEach { merc ->
            val product = prodMap[merc.productId]
            val stock = CostCalculationHelper.getMercaderiaCurrentStock(merc.id, merc.initialStock, movs)
            val effStock = if (stock > 0.0) stock else maxOf(merc.initialStock, 1.0)

            // Prorrateo: participación = precio de compra / total inventario (según fórmula exacta solicitada)
            val participacionRatio = merc.acquisitionCost / totalInventoryValue
            val gastosProrrateados = participacionRatio * totalDailyExpenses
            val recuperacionProrrateada = participacionRatio * totalDailyDepreciation
            val directExp = merc.directExpenses
            val totalUnitCost = merc.acquisitionCost + directExp + gastosProrrateados + recuperacionProrrateada

            val mercObj = JSONObject().apply {
                put("id", merc.id)
                put("uniqueKey", "MERC-${merc.id}")
                put("productId", merc.productId)
                put("uniqueProductKey", "PROD-${merc.productId}")
                put("name", product?.name ?: "Mercadería #${merc.id}")
                put("code", product?.code ?: "")
                put("category", product?.category ?: "Bebidas")
                put("unitOfMeasure", merc.unitOfMeasure)
                put("purchasePrice", merc.acquisitionCost)
                put("salePrice", product?.price ?: 0.0)
                put("directExpenses", directExp)
                put("directExpensesDetails", try { JSONArray(merc.directExpensesDetails) } catch (e: Exception) { JSONArray() })
                put("initialStock", merc.initialStock)
                put("currentStock", stock)
                put("participacionInventarioRatio", participacionRatio)
                put("participacionInventarioPorcentaje", participacionRatio * 100.0)
                put("gastosCorrientesProrrateados", gastosProrrateados)
                put("recuperacionInversionesProrrateada", recuperacionProrrateada)
                put("costoTotalMercancia", totalUnitCost)
                put("isActive", merc.isActive)
            }
            mercanciasArr.put(mercObj)
        }
        root.put("mercancias", mercanciasArr)

        // Movimientos
        val movsArr = JSONArray()
        movs.forEach { mov ->
            val movObj = JSONObject().apply {
                put("id", mov.id)
                put("uniqueKey", "MOV-MERC-${mov.id}")
                put("mercaderiaId", mov.mercaderiaId)
                put("type", mov.type)
                put("quantity", mov.quantity)
                put("date", mov.date)
                put("responsibleAdmin", mov.responsibleAdmin)
                put("notes", mov.notes)
            }
            movsArr.put(movObj)
        }
        root.put("movimientos", movsArr)

        return root.toString(2)
    }

    fun buildQInversionesJson(uiState: MainUiState): String {
        val root = JSONObject()
        root.put("fileIdentifier", "Q_INVERSIONES")
        root.put("version", "1.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("businessName", uiState.businessName.ifEmpty { "Negocio" })

        val totalAmount = uiState.inversiones.sumOf { it.amount }
        val totalDailyRecovery = uiState.inversiones.sumOf { it.dailyDepreciation() }

        root.put("totalInvertido", totalAmount)
        root.put("recuperacionDiariaTotal", totalDailyRecovery)

        val invArr = JSONArray()
        uiState.inversiones.forEach { inv ->
            val invObj = JSONObject().apply {
                put("id", inv.id)
                put("uniqueKey", "INV-${inv.id}")
                put("name", inv.name)
                put("category", inv.category)
                put("amount", inv.amount)
                put("usefulLife", inv.usefulLife)
                put("usefulLifeUnit", inv.usefulLifeUnit)
                put("dailyDepreciation", inv.dailyDepreciation())
                put("monthlyDepreciation", inv.monthlyDepreciation())
                put("date", inv.date)
                put("observation", inv.observation)
                put("scope", inv.scope)
                put("currency", inv.currency)
                put("originalAmount", inv.originalAmount)
                put("exchangeRate", inv.exchangeRate)
                put("convertedAmount", inv.convertedAmount)
            }
            invArr.put(invObj)
        }
        root.put("inversiones", invArr)

        return root.toString(2)
    }

    fun writeJsonToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
