package com.example.ui

import com.example.ui.screens.dueno.CuadreExecutionMode
import com.example.ui.screens.dueno.MercaderiaItemState
import com.example.ui.screens.dueno.ProduccionItemState
import org.junit.Assert.*
import org.junit.Test

class CuadreCajaModeSwitchTest {

    @Test
    fun testModeSwitchingMaintainsSeparateStateData() {
        // 1. Estado para Modo Integrado
        val itemIntegrado = MercaderiaItemState(
            mercaderiaId = 101L,
            productId = 1L,
            productName = "Refresco Cola",
            unit = "U",
            price = 150.0,
            existenciaInicialStr = "20.0",
            entradasStr = "5.0",
            existenciaFinalStr = "10.0",
            isIndependiente = false
        )

        // 2. Estado para Modo Independiente
        val itemIndependiente = MercaderiaItemState(
            mercaderiaId = 101L,
            productId = 1L,
            productName = "Refresco Cola",
            unit = "U",
            price = 180.0, // Precio temporal modificado en cuadre independiente
            existenciaInicialStr = "50.0",
            entradasStr = "0",
            existenciaFinalStr = "30.0",
            isIndependiente = true
        )

        // Modo Integrado activo
        var currentMode = CuadreExecutionMode.INTEGRADO
        var activeItem = if (currentMode == CuadreExecutionMode.INTEGRADO) itemIntegrado else itemIndependiente

        // Verificar cálculo en Modo Integrado
        assertEquals(15.0, activeItem.ventas, 0.001) // 20 + 5 - 10 = 15
        assertEquals(2250.0, activeItem.ingresoEstimado, 0.001) // 15 * 150 = 2250

        // Alternar a Modo Independiente
        currentMode = CuadreExecutionMode.INDEPENDIENTE
        activeItem = if (currentMode == CuadreExecutionMode.INTEGRADO) itemIntegrado else itemIndependiente

        // Verificar cálculo en Modo Independiente con sus propios datos
        assertEquals(20.0, activeItem.ventas, 0.001) // 50 - 30 = 20
        assertEquals(3600.0, activeItem.ingresoEstimado, 0.001) // 20 * 180 = 3600

        // Alternar de regreso a Modo Integrado
        currentMode = CuadreExecutionMode.INTEGRADO
        activeItem = if (currentMode == CuadreExecutionMode.INTEGRADO) itemIntegrado else itemIndependiente

        // Verificar que los datos del Modo Integrado se conservaron sin alteraciones
        assertEquals(15.0, activeItem.ventas, 0.001)
        assertEquals(2250.0, activeItem.ingresoEstimado, 0.001)
        assertEquals(150.0, activeItem.price, 0.001)

        // Alternar nuevamente a Modo Independiente
        currentMode = CuadreExecutionMode.INDEPENDIENTE
        activeItem = if (currentMode == CuadreExecutionMode.INTEGRADO) itemIntegrado else itemIndependiente

        // Verificar que los datos del Modo Independiente se conservaron sin alteraciones
        assertEquals(20.0, activeItem.ventas, 0.001)
        assertEquals(3600.0, activeItem.ingresoEstimado, 0.001)
        assertEquals(180.0, activeItem.price, 0.001)
    }

    @Test
    fun testProduccionModeSwitching() {
        val prodIntegrado = ProduccionItemState(
            productId = 10L,
            productName = "Pan Especial",
            unit = "U",
            price = 50.0,
            tandasCount = 2,
            totalProduced = 100.0,
            defectuosoStr = "5",
            isIndependiente = false
        )

        val prodIndependiente = ProduccionItemState(
            productId = 10L,
            productName = "Pan Especial",
            unit = "U",
            price = 60.0,
            tandasCount = 3,
            totalProduced = 150.0,
            defectuosoStr = "10",
            isIndependiente = true
        )

        assertEquals(95.0, prodIntegrado.vendible, 0.001)
        assertEquals(4750.0, prodIntegrado.ingresoEstimado, 0.001)

        assertEquals(140.0, prodIndependiente.vendible, 0.001)
        assertEquals(8400.0, prodIndependiente.ingresoEstimado, 0.001)
    }
}
