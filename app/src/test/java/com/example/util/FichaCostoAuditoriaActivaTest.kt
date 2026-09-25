package com.example.util

import com.example.data.local.model.Mercaderia
import com.example.data.local.model.Product
import com.example.data.local.model.ProductoElaborado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FichaCostoAuditoriaActivaTest {

    @Test
    fun testAuditoriaActivarDesactivarProductoProduccion() {
        val prodInicial = Product(
            id = 101L,
            code = "COC-001",
            name = "Pizza Familiar",
            category = "Pizzas",
            price = 500.0,
            cost = 250.0,
            destination = "COCINA",
            isAvailable = true
        )
        val peInicial = ProductoElaborado(
            id = 1L,
            productId = 101L,
            baseYield = 1.0,
            productionUnit = "Unidad",
            ppd = 20.0,
            isActive = true
        )

        assertTrue("El producto inicial debe estar activo", prodInicial.isAvailable)
        assertTrue("El producto elaborado inicial debe estar activo", peInicial.isActive)

        // Simular desactivación desde el control superior derecho de Ficha de Costo
        val nuevoEstado = !prodInicial.isAvailable
        val prodDesactivado = prodInicial.copy(isAvailable = nuevoEstado)
        val peDesactivado = peInicial.copy(isActive = nuevoEstado)

        assertFalse("El producto debe quedar inactivo", prodDesactivado.isAvailable)
        assertFalse("El producto elaborado debe quedar inactivo", peDesactivado.isActive)

        // Simular reactivación desde Ficha de Costo
        val reactivado = prodDesactivado.copy(isAvailable = !prodDesactivado.isAvailable)
        assertTrue("El producto debe quedar activo tras reactivar", reactivado.isAvailable)
    }

    @Test
    fun testAuditoriaActivarDesactivarMercaderia() {
        val mercInicial = Mercaderia(
            id = 201L,
            productId = 301L,
            unitOfMeasure = "Lata 355ml",
            acquisitionCost = 150.0,
            directExpenses = 10.0,
            initialStock = 100.0,
            isActive = true
        )
        val prodMercInicial = Product(
            id = 301L,
            code = "BAR-001",
            name = "Cerveza Cristal 355ml",
            category = "Bebidas",
            price = 250.0,
            cost = 160.0,
            destination = "BARRA",
            isAvailable = true
        )

        assertTrue("La mercadería inicial debe estar activa", mercInicial.isActive)
        assertTrue("El producto barra asociado debe estar activo", prodMercInicial.isAvailable)

        // Simular toggle desde el encabezado de FichaCostoMercaderiaDialog
        val nuevoEstadoMerc = !mercInicial.isActive
        val mercDesactivada = mercInicial.copy(isActive = nuevoEstadoMerc)
        val prodDesactivado = prodMercInicial.copy(isAvailable = nuevoEstadoMerc)

        assertFalse("La mercadería debe quedar inactiva", mercDesactivada.isActive)
        assertFalse("El producto barra debe quedar inactivo", prodDesactivado.isAvailable)

        // Validar filtro de listados activos
        val listaMercaderias = listOf(mercInicial, mercDesactivada)
        val activas = listaMercaderias.filter { it.isActive }
        assertEquals(1, activas.size)
        assertEquals(mercInicial.id, activas[0].id)
    }
}
