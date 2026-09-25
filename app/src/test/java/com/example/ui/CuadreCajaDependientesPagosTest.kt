package com.example.ui

import com.example.ui.screens.dueno.ModalidadPagoDependiente
import com.example.ui.screens.dueno.ProduccionItemState
import org.junit.Assert.assertEquals
import org.junit.Test

class CuadreCajaDependientesPagosTest {

    @Test
    fun testPagoIgualModalityCalculation() {
        // 200 pizzas vendidas, 6 CUP por pizza
        val pizzaItem = ProduccionItemState(
            productId = 1L,
            productName = "Pizza Queso",
            unit = "u",
            price = 200.0,
            tandasCount = 4,
            totalProduced = 200.0,
            defectuosoStr = "0",
            consumoStr = "0",
            regaliaStr = "0",
            pagoDependienteUnitario = 6.0
        )

        val vendible = pizzaItem.vendible
        val tarifa = pizzaItem.pagoDependienteUnitario
        assertEquals(200.0, vendible, 0.001)
        assertEquals(6.0, tarifa, 0.001)

        // PAGO IGUAL:
        // CANTIDAD VENDIDA DEL PRODUCTO × TARIFA DE PAGO POR UNIDAD = PAGO PARA CADA DEPENDIENTE
        val pagoCadaDependiente = vendible * tarifa
        assertEquals(1200.0, pagoCadaDependiente, 0.001)

        // Con 1 dependiente: recibe 1.200 CUP, total pagado = 1.200 CUP
        val cantDeps1 = 1
        val totalPagadoProd1 = pagoCadaDependiente * cantDeps1
        assertEquals(1200.0, totalPagadoProd1, 0.001)

        // Con 2 dependientes: CADA UNO recibe 1.200 CUP, total pagado = 2.400 CUP
        val cantDeps2 = 2
        val totalPagadoProd2 = pagoCadaDependiente * cantDeps2
        assertEquals(2400.0, totalPagadoProd2, 0.001)

        // Con 3 dependientes: CADA UNO recibe 1.200 CUP, total pagado = 3.600 CUP
        val cantDeps3 = 3
        val totalPagadoProd3 = pagoCadaDependiente * cantDeps3
        assertEquals(3600.0, totalPagadoProd3, 0.001)
    }

    @Test
    fun testPagoEquitativoModalityCalculation() {
        // 200 pizzas vendidas, 6 CUP por pizza -> Total = 1200 CUP
        val vendible = 200.0
        val tarifa = 6.0
        val totalProd = vendible * tarifa
        assertEquals(1200.0, totalProd, 0.001)

        // Con 2 dependientes: se divide equitativamente -> 600 CUP cada uno
        val cantDeps = 2
        val pagoPorDep = totalProd / cantDeps
        assertEquals(600.0, pagoPorDep, 0.001)
        assertEquals(1200.0, pagoPorDep * cantDeps, 0.001)
    }

    @Test
    fun testPagoPorReparticionModalityCalculation() {
        val tarifa = 6.0
        val cantDep1 = 150.0
        val cantDep2 = 50.0

        val pagoDep1 = cantDep1 * tarifa
        val pagoDep2 = cantDep2 * tarifa

        assertEquals(900.0, pagoDep1, 0.001)
        assertEquals(300.0, pagoDep2, 0.001)
        assertEquals(1200.0, pagoDep1 + pagoDep2, 0.001)
    }
}
