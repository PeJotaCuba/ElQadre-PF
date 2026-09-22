package com.example.util

import com.example.data.local.model.Mercaderia
import com.example.data.local.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class MercaderiaDirectExpensesTest {

    @Test
    fun testMercaderiaDirectExpensesCalculationForBatchPurchase() {
        // Example from prompt:
        // 4 cajas de refrescos, 24 unidades por caja = 96 unidades total.
        // Gasto de transporte total: $500.
        // Gasto directo unitario de transporte: $500 / 96 = $5.208333... por refresco (~$5.21).

        val numCajas = 4.0
        val unidadesPorCaja = 24.0
        val totalUnidadesAdquiridas = numCajas * unidadesPorCaja // 96.0
        assertEquals(96.0, totalUnidadesAdquiridas, 0.001)

        val gastoTransporteTotal = 500.0
        val gastoDirectoUnitario = gastoTransporteTotal / totalUnidadesAdquiridas // 5.208333333333334

        assertEquals(5.20833, gastoDirectoUnitario, 0.0001)
        assertEquals(5.21, gastoDirectoUnitario, 0.01)

        val costoAdquisicionUnitario = 400.0
        val costoDirectoUnitarioCalculado = costoAdquisicionUnitario + gastoDirectoUnitario

        // Costo directo unitario = COSTO DE ADQUISICIÓN UNITARIO ($400) + GASTOS DIRECTOS UNITARIOS ($5.21) = $405.21
        assertEquals(405.20833, costoDirectoUnitarioCalculado, 0.0001)
        assertEquals(405.21, costoDirectoUnitarioCalculado, 0.01)

        // Verify CostCalculationHelper
        val refrescoProduct = Product(
            id = 10,
            code = "M-REF",
            name = "Refresco Ciego Montero",
            category = "Bebidas",
            price = 600.0,
            cost = costoAdquisicionUnitario,
            destination = "BARRA",
            isAvailable = true
        )

        val refrescoMercaderia = Mercaderia(
            id = 1,
            productId = 10,
            acquisitionCost = costoAdquisicionUnitario,
            unitOfMeasure = "Unidad",
            initialStock = totalUnidadesAdquiridas,
            isActive = true,
            directExpenses = gastoDirectoUnitario,
            purchaseMode = "POR LOTE",
            purchasePrice = 400.0 * 24.0,
            unitsPerLot = 24.0
        )

        val costSheet = CostCalculationHelper.calculateMercaderiaCostSheet(
            mercaderia = refrescoMercaderia,
            mercaderias = listOf(refrescoMercaderia),
            products = listOf(refrescoProduct),
            movimientos = emptyList(),
            gastosGenerales = emptyList(),
            inversiones = emptyList()
        )

        assertEquals(costoAdquisicionUnitario, costSheet.acquisitionCost, 0.001)
        assertEquals(gastoDirectoUnitario, costSheet.directExpenses, 0.001)
        assertEquals(costoDirectoUnitarioCalculado, costSheet.costoDirectoUnitario, 0.001)
        assertEquals(5.21, costSheet.directExpenses, 0.01)
        assertEquals(405.21, costSheet.costoDirectoUnitario, 0.01)
    }
}
