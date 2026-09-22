package com.example.util

import com.example.data.local.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class FichaCostoProduccionCorreccionTest {

    @Test
    fun testCasoNumericoFichaCostoProduccion() {
        // 1. Configuración de Gastos Generales e Inversiones
        // Gastos generales diarios = 800 CUP/día
        // Depreciación de inversiones = 200 CUP/día
        // Costos indirectos totales = 800 + 200 = 1,000 CUP/día
        val gasto1 = GastoGeneral(id = 1L, name = "Electricidad", amount = 800.0, period = "Día", isActive = true)
        val inv1 = Inversion(
            id = 1L,
            name = "Horno Industrial",
            category = "Equipos",
            amount = 60000.0,
            usefulLife = 10.0,
            usefulLifeUnit = "MESES",
            scope = "PRODUCCION"
        )

        // Producto A (Objetivo de la prueba):
        // PPD = 30 unidades
        // CDU (Costo directo unitario) = 80 CUP
        // Pago personal unitario: Cocina 10 + Dependiente 3 + Cajero 2 = 15 CUP
        // Base de Producto A = 30 * 80 = 2,400 CUP
        val prodA = Product(
            id = 10L,
            code = "PROD-A",
            name = "Pizza Especial",
            category = "Cocina",
            price = 140.0,
            cost = 80.0,
            destination = "COCINA",
            isAvailable = true
        )
        val elabA = ProductoElaborado(
            id = 10L,
            productId = 10L,
            ppd = 30.0,
            pagoCocinaUnitario = 10.0,
            cantidadCocineros = 1,
            pagoDependienteUnitario = 3.0,
            pagoCajeroUnitario = 2.0,
            targetMarginPct = 30.0
        )

        // Producto B (Para que Producto A tenga exactamente el 15% de participación en inventario económico):
        // Base Total necesaria para que 2,400 sea el 15% = 2,400 / 0.15 = 16,000 CUP
        // Base Producto B = 16,000 - 2,400 = 13,600 CUP
        // PPD = 136, CDU = 100 -> 136 * 100 = 13,600 CUP
        val prodB = Product(
            id = 20L,
            code = "PROD-B",
            name = "Hamburguesa",
            category = "Cocina",
            price = 150.0,
            cost = 100.0,
            destination = "COCINA",
            isAvailable = true
        )
        val elabB = ProductoElaborado(
            id = 20L,
            productId = 20L,
            ppd = 136.0
        )

        val products = listOf(prodA, prodB)
        val productosElaborados = listOf(elabA, elabB)
        val gastosGenerales = listOf(gasto1)
        val inversiones = listOf(inv1)

        // 2. Ejecutar cálculo de la ficha de costo
        val costSheet = CostCalculationHelper.calculateCostSheet(
            product = prodA,
            products = products,
            productosElaborados = productosElaborados,
            recetaIngredientes = emptyList(),
            materiasPrimas = emptyList(),
            gastosGenerales = gastosGenerales,
            inversiones = inversiones
        )

        // 3. Verificaciones de la cadena de cálculo

        // APARTADO 5:
        // Costos indirectos totales = Gastos generales diarios (800) + Depreciación (200) = 1,000 CUP/día
        assertEquals(800.0, costSheet.gastosGeneralesDiariosTotales, 0.001)
        assertEquals(200.0, costSheet.depreciacionInversionesDiariaTotales, 0.001)
        assertEquals(1000.0, costSheet.costosIndirectosDiariosTotales, 0.001)

        // Porcentaje de participación en inventario económico = 15 %
        assertEquals(15.0, costSheet.porcentajeParticipacionPpd, 0.001)

        // Asignación directa = Costos indirectos totales × Porcentaje de participación en inventario económico
        // Asignación directa = 1,000 × 0.15 = 150 CUP/día
        assertEquals(150.0, costSheet.gastoIndirectoAsignado, 0.001)

        // Gasto indirecto unitario = Asignación directa ÷ Producción promedio diaria
        // Gasto indirecto unitario = 150 ÷ 30 = 5 CUP/unidad
        assertEquals(5.0, costSheet.gastoIndirectoUnitario, 0.001)

        // APARTADO 8:
        // Costo directo unitario = 80 CUP
        assertEquals(80.0, costSheet.costoDirectoUnitario, 0.001)

        // Pago a personal por unidad = 10 + 3 + 2 = 15 CUP
        assertEquals(15.0, costSheet.totalPagoPersonalUnitario, 0.001)

        // Costo unitario final = Costo directo unitario + Gasto indirecto unitario + Pago a personal por unidad
        // Costo unitario final = 80 + 5 + 15 = 100 CUP
        // Sin duplicación ni de gastos generales ni de depreciación
        assertEquals(100.0, costSheet.costoTotalUnitario, 0.001)
        assertEquals(100.0, costSheet.costoRealUnitario, 0.001)

        // APARTADO 9:
        // Precio de referencia con +30% de margen = 100 × 1.30 = 130 CUP
        assertEquals(130.0, costSheet.precioReferencia, 0.001)

        // Cálculo de % de ganancia real para un precio de venta seleccionado diferente al de referencia:
        // Ejemplo del usuario: Precio seleccionado = 140 CUP
        val precioSeleccionado = 140.0
        val ganancia = precioSeleccionado - costSheet.costoTotalUnitario
        val porcentajeGanancia = (ganancia / costSheet.costoTotalUnitario) * 100.0

        // Ganancia = 140 - 100 = 40 CUP
        assertEquals(40.0, ganancia, 0.001)
        // Porcentaje de ganancia = (40 ÷ 100) × 100 = 40%
        assertEquals(40.0, porcentajeGanancia, 0.001)
    }
}
