package com.example.util

import com.example.data.local.model.PresentacionEspecial
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import com.example.data.local.model.parsePresentacionesEspeciales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TandasRendimientoPresentacionEspecialTest {

    @Test
    fun testRendimientoConPresentacionEspecial_CasoValidacion() {
        // Validación según requerimiento:
        // Insumo base: 22 lb
        // Producción real obtenida: 78 pizzas personales
        // Presentación especial: 3 pizzas familiares
        // Equivalencia configurada en Ficha: 1 pizza familiar = 5 pizzas personales
        val baseQuantityUsed = 22.0
        val personalQuantity = 78.0
        val familiarQuantity = 3.0
        val familiarEquivalence = 5.0

        val specialUnitsEq = familiarQuantity * familiarEquivalence
        assertEquals(15.0, specialUnitsEq, 0.001)

        val totalYieldUnits = personalQuantity + specialUnitsEq
        assertEquals(93.0, totalYieldUnits, 0.001)

        val rendimientoCalculado = totalYieldUnits / baseQuantityUsed
        val rendimientoFormatted = "%.2f".format(rendimientoCalculado)

        assertEquals("4.23", rendimientoFormatted)
        assertEquals(4.2272727, rendimientoCalculado, 0.001)
    }

    @Test
    fun testRendimientoSinPresentacionEspecial() {
        // Caso sin presentación especial:
        // Insumo base: 22 lb
        // Producción real obtenida: 78 pizzas personales
        val baseQuantityUsed = 22.0
        val personalQuantity = 78.0
        val specialPresentationQty = 0.0
        val specialPresentationEquivalence = 1.0

        val specialUnitsEq = specialPresentationQty * specialPresentationEquivalence
        val totalYieldUnits = personalQuantity + specialUnitsEq

        assertEquals(78.0, totalYieldUnits, 0.001)

        val rendimientoCalculado = totalYieldUnits / baseQuantityUsed
        val rendimientoFormatted = "%.2f".format(rendimientoCalculado)

        assertEquals("3.55", rendimientoFormatted)
    }

    @Test
    fun testParsePresentacionesEspecialesDeFichaDeCosto() {
        val json = """[{"id":"1","name":"Familiar","baseEquivalence":5.0,"price":500.0}]"""
        val presentaciones = parsePresentacionesEspeciales(json)

        assertEquals(1, presentaciones.size)
        assertEquals("Familiar", presentaciones[0].name)
        assertEquals(5.0, presentaciones[0].baseEquivalence, 0.001)
        assertEquals(500.0, presentaciones[0].price, 0.001)
    }

    @Test
    fun testTandaCerradaConPresentacionEspecialConservaCantidadesFisicasSeparadas() {
        val tanda = Tanda(
            id = 1L,
            uuid = "tanda-123",
            productId = 10L,
            productName = "Pizza Napolitana",
            responsibleUser = "admin",
            baseMateriaPrimaId = 1L,
            baseMateriaPrimaName = "Harina",
            baseQuantityUsed = 22.0,
            baseQuantityUnit = "lb",
            productionFactor = 1.0,
            estimatedYield = 90.0,
            productionUnit = "pizza",
            ingredientsConsumedText = "Harina: 22 lb",
            status = "CERRADA",
            actualYield = 78.0, // 78 personales
            specialPresentationName = "Familiar",
            specialPresentationQty = 3.0, // 3 familiares
            specialPresentationEquivalence = 5.0,
            yieldPercentage = (93.0 / 90.0) * 100.0
        )

        // Verificamos que las cantidades físicas sigan separadas:
        assertEquals(78.0, tanda.actualYield, 0.001)
        assertEquals(3.0, tanda.specialPresentationQty, 0.001)
        assertEquals("Familiar", tanda.specialPresentationName)

        // Verificamos el cálculo del rendimiento:
        val eqUnits = tanda.specialPresentationQty * tanda.specialPresentationEquivalence
        val totalRendUnits = tanda.actualYield + eqUnits
        assertEquals(93.0, totalRendUnits, 0.001)

        val rend = totalRendUnits / tanda.baseQuantityUsed
        assertEquals("4.23", "%.2f".format(rend))
    }
}
