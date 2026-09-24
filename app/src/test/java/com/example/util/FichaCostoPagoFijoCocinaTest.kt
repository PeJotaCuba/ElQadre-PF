package com.example.util

import com.example.data.local.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FichaCostoPagoFijoCocinaTest {

    @Test
    fun testPagoFijoCocinaActivadoEjemploUsuario() {
        // Ejemplo del usuario:
        // PAGO FIJO DIARIO = $100
        // PPD = 50 unidades
        // Resultado: $100 ÷ 50 = $2 por unidad
        // Los $2 por unidad se incorporan al costo del producto.
        // El pago fijo real sigue siendo un solo pago diario de $100.
        val prod = Product(
            id = 1L,
            code = "PZ-01",
            name = "Pizza Queso",
            category = "Pizzas",
            price = 60.0,
            cost = 40.0,
            destination = "COCINA",
            isAvailable = true
        )
        val elabFijo = ProductoElaborado(
            id = 1L,
            productId = 1L,
            ppd = 50.0,
            pagoCocinaUnitario = 100.0, // Pago Fijo Diario = $100
            cantidadCocineros = 1,
            isPagoCocinaFijo = true, // ACTIVADO
            pagoDependienteUnitario = 5.0,
            pagoCajeroUnitario = 3.0
        )

        // Verificamos propiedad computada en ProductoElaborado
        assertEquals(2.0, elabFijo.totalPagoCocinaUnitario, 0.001)
        assertEquals(10.0, elabFijo.totalPagoPersonalUnitario, 0.001) // 2 (cocina fijo) + 5 (dep) + 3 (caj)

        // Verificamos cálculo en CostCalculationHelper
        val sheet = CostCalculationHelper.calculateCostSheet(
            product = prod,
            products = listOf(prod),
            productosElaborados = listOf(elabFijo),
            recetaIngredientes = emptyList<RecetaIngrediente>(),
            materiasPrimas = emptyList<MateriaPrima>(),
            gastosGenerales = emptyList<GastoGeneral>(),
            inversiones = emptyList<Inversion>()
        )

        assertTrue(sheet.isPagoCocinaFijo)
        assertEquals(100.0, sheet.pagoCocinaUnitario, 0.001) // Pago fijo diario configurado
        assertEquals(2.0, sheet.totalPagoCocinaUnitario, 0.001) // 100 / 50 = 2.0 por unidad
        assertEquals(10.0, sheet.totalPagoPersonalUnitario, 0.001) // 2.0 + 5.0 + 3.0
        // Costo unitario total debe incluir los $2.0 por unidad de cocina fijo:
        // Costo directo (40.0) + Costos indirectos (0.0) + Pago personal (10.0) = 50.0
        assertEquals(50.0, sheet.costoTotalUnitario, 0.001)
    }

    @Test
    fun testPagoCocinaDesactivadoMantieneLogicaPorUnidad() {
        // CUANDO ESTÁ DESACTIVADO: Mantener la lógica actual del pago de Cocina por unidad.
        val prod = Product(
            id = 2L,
            code = "PZ-02",
            name = "Pizza Jamon",
            category = "Pizzas",
            price = 70.0,
            cost = 45.0,
            destination = "COCINA",
            isAvailable = true
        )
        val elabPorUnidad = ProductoElaborado(
            id = 2L,
            productId = 2L,
            ppd = 50.0,
            pagoCocinaUnitario = 10.0, // Pago por unidad = $10
            cantidadCocineros = 2, // 2 cocineros
            isPagoCocinaFijo = false, // DESACTIVADO
            pagoDependienteUnitario = 5.0,
            pagoCajeroUnitario = 3.0
        )

        // Verificamos propiedad computada en ProductoElaborado: 10 * 2 = 20
        assertEquals(20.0, elabPorUnidad.totalPagoCocinaUnitario, 0.001)
        assertEquals(28.0, elabPorUnidad.totalPagoPersonalUnitario, 0.001) // 20 + 5 + 3

        val sheet = CostCalculationHelper.calculateCostSheet(
            product = prod,
            products = listOf(prod),
            productosElaborados = listOf(elabPorUnidad),
            recetaIngredientes = emptyList<RecetaIngrediente>(),
            materiasPrimas = emptyList<MateriaPrima>(),
            gastosGenerales = emptyList<GastoGeneral>(),
            inversiones = emptyList<Inversion>()
        )

        assertFalse(sheet.isPagoCocinaFijo)
        assertEquals(10.0, sheet.pagoCocinaUnitario, 0.001)
        assertEquals(20.0, sheet.totalPagoCocinaUnitario, 0.001) // 10 * 2 = 20.0
        assertEquals(28.0, sheet.totalPagoPersonalUnitario, 0.001) // 20.0 + 5.0 + 3.0
        assertEquals(73.0, sheet.costoTotalUnitario, 0.001) // 45.0 + 28.0
    }
}
