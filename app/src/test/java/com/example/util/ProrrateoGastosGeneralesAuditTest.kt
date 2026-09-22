package com.example.util

import com.example.data.local.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Auditoría y Validación definitiva del Algoritmo de Prorrateo de Gastos Generales.
 * Verifica matemáticamente el ejemplo exacto del requerimiento y todos los casos borde.
 */
class ProrrateoGastosGeneralesAuditTest {

    @Test
    fun testPeriodicidadesConversionDiaria() {
        assertEquals(100.0, GastoGeneral(id = 1, name = "Gasto Dia", amount = 100.0, period = "Día").dailyCost(), 0.0001)
        assertEquals(700.0 / 7.0, GastoGeneral(id = 2, name = "Gasto Semana", amount = 700.0, period = "Semana").dailyCost(), 0.0001)
        assertEquals(600.0 / 6.0, GastoGeneral(id = 3, name = "Gasto Semana Lab", amount = 600.0, period = "Semana laborable").dailyCost(), 0.0001)
        assertEquals(6000.0 / 30.0, GastoGeneral(id = 4, name = "Gasto Mes", amount = 6000.0, period = "Mes").dailyCost(), 0.0001)
        assertEquals(2600.0 / 26.0, GastoGeneral(id = 5, name = "Gasto Mes Lab", amount = 2600.0, period = "Mes laborable").dailyCost(), 0.0001)
        assertEquals(36000.0 / 360.0, GastoGeneral(id = 6, name = "Gasto Año", amount = 36000.0, period = "Año").dailyCost(), 0.0001)
        assertEquals(31200.0 / 312.0, GastoGeneral(id = 7, name = "Gasto Año Lab", amount = 31200.0, period = "Año laborable").dailyCost(), 0.0001)
    }

    @Test
    fun testEjemploNumericoCompletoRequerimiento() {
        // Gasto general: $6,000 mensual -> $200 diario
        val gasto = GastoGeneral(
            id = 1,
            name = "Electricidad Local",
            amount = 6000.0,
            period = "Mes",
            isActive = true
        )
        val gastosList = listOf(gasto)
        assertEquals(200.0, gasto.dailyCost(), 0.0001)

        // PRODUCCIÓN:
        // Pizza: 300 unidades esperadas, Costo directo $300 -> Base $90,000
        val pizzaProduct = Product(id = 10, code = "P-PIZZA", name = "Pizza", category = "Cocina", price = 500.0, cost = 300.0, destination = "COCINA", isAvailable = true)
        val pizzaElab = ProductoElaborado(id = 1, productId = 10, ppd = 300.0, baseYield = 1.0)

        // Espagueti: 100 unidades esperadas, Costo directo $500 -> Base $50,000
        val espaguetiProduct = Product(id = 11, code = "P-ESP", name = "Espagueti", category = "Cocina", price = 800.0, cost = 500.0, destination = "COCINA", isAvailable = true)
        val espaguetiElab = ProductoElaborado(id = 2, productId = 11, ppd = 100.0, baseYield = 1.0)

        // MERCADERÍAS:
        // Refresco: 100 unidades en inventario, Costo directo $400 -> Base $40,000
        val refrescoProduct = Product(id = 20, code = "M-REF", name = "Refresco", category = "Bebidas", price = 600.0, cost = 400.0, destination = "BARRA", isAvailable = true)
        val refrescoMerc = Mercaderia(id = 100, productId = 20, acquisitionCost = 400.0, unitOfMeasure = "U", initialStock = 100.0, isActive = true)

        // Confitura: 50 unidades en inventario, Costo directo $200 -> Base $10,000
        val confituraProduct = Product(id = 21, code = "M-CONF", name = "Confitura", category = "Confituras", price = 350.0, cost = 200.0, destination = "BARRA", isAvailable = true)
        val confituraMerc = Mercaderia(id = 101, productId = 21, acquisitionCost = 200.0, unitOfMeasure = "U", initialStock = 50.0, isActive = true)

        val products = listOf(pizzaProduct, espaguetiProduct, refrescoProduct, confituraProduct)
        val prodElaborados = listOf(pizzaElab, espaguetiElab)
        val mercaderias = listOf(refrescoMerc, confituraMerc)

        val result = CostCalculationHelper.calcularBaseProrrateoGastosGenerales(
            products = products,
            productosElaborados = prodElaborados,
            mercaderias = mercaderias,
            movimientosMercaderia = emptyList(),
            gastosGenerales = gastosList
        )

        // 1. Verificación Gasto General Diario Total
        assertEquals(200.0, result.gastoGeneralDiarioTotal, 0.001)

        // 2. Verificación Bases individuales
        assertEquals(140000.0, result.baseProduccionTotal, 0.001) // 300 * 300 (Pizza) + 100 * 500 (Espagueti) = 90k + 50k = 140k
        assertEquals(50000.0, result.baseMercaderiasTotal, 0.001) // 100 * 400 (Refresco) + 50 * 200 (Confitura) = 40k + 10k = 50k
        
        // 3. Verificación Base Económica Total = 90,000 + 50,000 + 40,000 + 10,000 = 190,000
        assertEquals(190000.0, result.baseTotal, 0.001)

        val pizzaItem = result.itemsProrrateo.find { it.productId == 10L }!!
        val espaguetiItem = result.itemsProrrateo.find { it.productId == 11L }!!
        val refrescoItem = result.itemsProrrateo.find { it.productId == 20L }!!
        val confituraItem = result.itemsProrrateo.find { it.productId == 21L }!!

        // 4. Verificación Porcentajes de Participación
        // Pizza: 90,000 / 190,000 = 47.3684%
        assertEquals(47.3684, pizzaItem.porcentajeParticipacion, 0.01)
        // Espagueti: 50,000 / 190,000 = 26.3158%
        assertEquals(26.3158, espaguetiItem.porcentajeParticipacion, 0.01)
        // Refresco: 40,000 / 190,000 = 21.0526%
        assertEquals(21.0526, refrescoItem.porcentajeParticipacion, 0.01)
        // Confitura: 10,000 / 190,000 = 5.2632%
        assertEquals(5.2632, confituraItem.porcentajeParticipacion, 0.01)

        val sumaPorcentajes = result.itemsProrrateo.sumOf { it.porcentajeParticipacion }
        assertEquals(100.0, sumaPorcentajes, 0.01)

        // 5. Verificación Gasto General Asignado
        // Pizza: $200 * 47.3684% = $94.74
        assertEquals(94.74, pizzaItem.gastoGeneralAsignado, 0.02)
        // Espagueti: $200 * 26.3158% = $52.63
        assertEquals(52.63, espaguetiItem.gastoGeneralAsignado, 0.02)
        // Refresco: $200 * 21.0526% = $42.11
        assertEquals(42.11, refrescoItem.gastoGeneralAsignado, 0.02)
        // Confitura: $200 * 5.2632% = $10.53
        assertEquals(10.53, confituraItem.gastoGeneralAsignado, 0.02)

        val sumaGastosAsignados = result.itemsProrrateo.sumOf { it.gastoGeneralAsignado }
        assertEquals(200.0, sumaGastosAsignados, 0.01)

        // 6. Verificación Gasto General Unitario
        // Pizza: $94.74 / 300 = $0.3158
        assertEquals(0.3158, pizzaItem.gastoGeneralUnitario, 0.001)
        // Espagueti: $52.63 / 100 = $0.5263
        assertEquals(0.5263, espaguetiItem.gastoGeneralUnitario, 0.001)
        // Refresco: $42.11 / 100 = $0.4211
        assertEquals(0.4211, refrescoItem.gastoGeneralUnitario, 0.001)
        // Confitura: $10.53 / 50 = $0.2105
        assertEquals(0.2105, confituraItem.gastoGeneralUnitario, 0.001)

        // 7. Verificación Costo Total Unitario en Fichas de Costo
        val pizzaSheet = CostCalculationHelper.calculateCostSheet(
            product = pizzaProduct,
            products = products,
            productosElaborados = prodElaborados,
            recetaIngredientes = emptyList(),
            materiasPrimas = emptyList(),
            gastosGenerales = gastosList,
            inversiones = emptyList(),
            mercaderias = mercaderias,
            movimientosMercaderia = emptyList()
        )
        // Pizza: $300 + $0.3158 = $300.3158
        assertEquals(300.0, pizzaSheet.costoDirectoUnitario, 0.001)
        assertEquals(0.3158, pizzaSheet.gastoGeneralUnitarioProrrateo, 0.001)
        assertEquals(300.3158, pizzaSheet.costoTotalUnitario, 0.005)

        val refrescoSheet = CostCalculationHelper.calculateMercaderiaCostSheet(
            mercaderia = refrescoMerc,
            mercaderias = mercaderias,
            products = products,
            movimientos = emptyList(),
            gastosGenerales = gastosList,
            inversiones = emptyList(),
            productosElaborados = prodElaborados
        )
        // Refresco: $400 + $0.4211 = $400.4211
        assertEquals(400.0, refrescoSheet.costoDirectoUnitario, 0.001)
        assertEquals(0.4211, refrescoSheet.gastoGeneralUnitarioProrrateo, 0.001)
        assertEquals(400.4211, refrescoSheet.costoTotalUnitario, 0.005)
    }

    @Test
    fun testCasosBorde() {
        val gasto = GastoGeneral(id = 1, name = "Gasto", amount = 300.0, period = "Mes", isActive = true) // 10/dia
        val gastos = listOf(gasto)

        // 1. Producto con cantidad cero
        val prodZero = Product(id = 1, code = "P0", name = "Zero Prod", category = "Cocina", price = 100.0, cost = 50.0, destination = "COCINA", isAvailable = true)
        val prodZeroElab = ProductoElaborado(id = 1, productId = 1, ppd = 0.0, estimatedDailyQuantity = 0.0)

        val prodNormal = Product(id = 2, code = "P1", name = "Normal Prod", category = "Cocina", price = 100.0, cost = 50.0, destination = "COCINA", isAvailable = true)
        val prodNormalElab = ProductoElaborado(id = 2, productId = 2, ppd = 10.0) // Base = 500

        val resultZero = CostCalculationHelper.calcularBaseProrrateoGastosGenerales(
            products = listOf(prodZero, prodNormal),
            productosElaborados = listOf(prodZeroElab, prodNormalElab),
            mercaderias = emptyList(),
            movimientosMercaderia = emptyList(),
            gastosGenerales = gastos
        )

        // El producto con cantidad cero no recibe gasto ni genera division por cero
        assertNull(resultZero.itemsProrrateo.find { it.productId == 1L })
        val normalItem = resultZero.itemsProrrateo.find { it.productId == 2L }!!
        assertEquals(100.0, normalItem.porcentajeParticipacion, 0.001)
        assertEquals(10.0, normalItem.gastoGeneralAsignado, 0.001)
        assertEquals(1.0, normalItem.gastoGeneralUnitario, 0.001)

        // 2. Base económica total es cero
        val resultBaseZero = CostCalculationHelper.calcularBaseProrrateoGastosGenerales(
            products = listOf(prodZero),
            productosElaborados = listOf(prodZeroElab),
            mercaderias = emptyList(),
            movimientosMercaderia = emptyList(),
            gastosGenerales = gastos
        )
        assertEquals(0.0, resultBaseZero.baseTotal, 0.001)
        assertTrue(resultBaseZero.itemsProrrateo.isEmpty())

        // 3. Gasto general diario es cero
        val gastoZero = GastoGeneral(id = 2, name = "Zero", amount = 0.0, period = "Mes", isActive = true)
        val resultGastoZero = CostCalculationHelper.calcularBaseProrrateoGastosGenerales(
            products = listOf(prodNormal),
            productosElaborados = listOf(prodNormalElab),
            mercaderias = emptyList(),
            movimientosMercaderia = emptyList(),
            gastosGenerales = listOf(gastoZero)
        )
        assertEquals(0.0, resultGastoZero.gastoGeneralDiarioTotal, 0.001)
        val item = resultGastoZero.itemsProrrateo.first()
        assertEquals(0.0, item.gastoGeneralAsignado, 0.001)
        assertEquals(0.0, item.gastoGeneralUnitario, 0.001)
    }
}
