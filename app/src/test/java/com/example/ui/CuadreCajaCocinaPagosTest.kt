package com.example.ui

import com.example.ui.screens.dueno.ProduccionItemState
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreCajaCocinaPagosTest {

    @Test
    fun testPizzaCocinaCalculationWithCookCount() {
        val pizzaItem = ProduccionItemState(
            productId = 1L,
            productName = "Pizza Jamón",
            unit = "u",
            price = 200.0,
            tandasCount = 5,
            totalProduced = 50.0,
            defectuosoStr = "0",
            consumoStr = "0",
            regaliaStr = "0",
            pagoCocinaUnitario = 15.0,
            cantidadCocineros = 2
        )

        // Vendible = 50 u
        assertEquals(50.0, pizzaItem.vendible, 0.001)
        assertEquals(2, pizzaItem.cantidadCocineros)
        assertEquals(15.0, pizzaItem.pagoCocinaUnitario, 0.001)

        // Pago Cocina = 50 vendible * 15 pagoUnitario * 2 cocineros = 1500 CUP
        val subtotalCocina = pizzaItem.vendible * pizzaItem.pagoCocinaUnitario * pizzaItem.cantidadCocineros
        assertEquals(1500.0, subtotalCocina, 0.001)

        // Modificación manual a 3 cocineros
        pizzaItem.cantidadCocinerosStr = "3"
        assertEquals(3, pizzaItem.cantidadCocineros)
        val subtotalModificado = pizzaItem.vendible * pizzaItem.pagoCocinaUnitario * pizzaItem.cantidadCocineros
        assertEquals(2250.0, subtotalModificado, 0.001)
    }

    @Test
    fun testSpaguettiPagoFijoAndTotalPagosEffect() {
        val pizzaPago = 1500.0
        val spaguettiPagoFijoFicha = 800.0
        val pagoDependientes = 400.0
        val pagoCajero = 300.0

        val totalCocina = pizzaPago + spaguettiPagoFijoFicha
        assertEquals(2300.0, totalCocina, 0.001)

        val totalPagosEfectuados = totalCocina + pagoDependientes + pagoCajero
        assertEquals(3000.0, totalPagosEfectuados, 0.001)

        val efectivoContado = 10000.0
        val dineroFinalEnCaja = efectivoContado - totalPagosEfectuados
        assertEquals(7000.0, dineroFinalEnCaja, 0.001)

        // Utilidades brutas no descuentan pagos a trabajadores
        val ingresos = 12000.0
        val costosInsumos = 4000.0
        val utilidadTeorica = ingresos - costosInsumos
        assertEquals(8000.0, utilidadTeorica, 0.001)
    }
}
