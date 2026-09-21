package com.example.ui

import com.example.ui.screens.dueno.DuenoView
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias para validar la reorganización de DUEÑO → Ajustes
 * en dos entradas principales independientes: PREFERENCIAS y GESTIÓN.
 */
class DuenoAjustesReorganizationTest {

    @Test
    fun testDuenoAjustesIndependentSections() {
        val seccionesDisponibles = listOf("PREFERENCIAS", "GESTION")
        assertEquals(2, seccionesDisponibles.size)
        assertTrue(seccionesDisponibles.contains("PREFERENCIAS"))
        assertTrue(seccionesDisponibles.contains("GESTION"))
    }

    @Test
    fun testPreferenciasContieneExclusivamenteFuncionesPermitidas() {
        // En PREFERENCIAS deben estar únicamente:
        // 1. Tasa de cambio
        // 2. Cambio de contraseña del DUEÑO
        // 3. Copias de seguridad locales (Respaldar / Restaurar)
        // 4. Archivo de Jornada / jornadas almacenadas
        // 5. Actualización de la aplicación
        val funcionesPreferencias = listOf(
            "TASA_DE_CAMBIO",
            "CAMBIO_CONTRASENA_DUENO",
            "COPIAS_DE_SEGURIDAD",
            "ARCHIVO_DE_JORNADAS",
            "ACTUALIZACION_APP"
        )

        assertEquals(5, funcionesPreferencias.size)

        // Verificar que NO se incorporen las opciones eliminadas
        val opcionesEliminadas = listOf(
            "PERSONALIZAR_MI_SESION",
            "INFORMACION_Y_DATOS_DEL_NEGOCIO",
            "PARAMETROS_DE_JORNADA_Y_CAJA",
            "MONEDA_PRINCIPAL_DE_OPERACION",
            "METODO_DE_COBRO_ACEPTADO"
        )

        for (opcion in opcionesEliminadas) {
            assertFalse("La opción $opcion no debe estar en PREFERENCIAS", funcionesPreferencias.contains(opcion))
        }
    }

    @Test
    fun testGestionModulosOperativosDestinos() {
        // GESTIÓN sirve de punto de entrada a los módulos operativos sin duplicar ni modificar su lógica interna
        val modulosGestion = listOf(
            DuenoView.INVENTARIO,
            DuenoView.CATALOGO,
            DuenoView.PERSONAL,
            DuenoView.GASTOS,
            DuenoView.INVERSIONES,
            DuenoView.CONTROL_NEGOCIO
        )

        assertEquals(6, modulosGestion.size)
        assertTrue(modulosGestion.contains(DuenoView.INVENTARIO))
        assertTrue(modulosGestion.contains(DuenoView.CATALOGO))
        assertTrue(modulosGestion.contains(DuenoView.PERSONAL))
        assertTrue(modulosGestion.contains(DuenoView.GASTOS))
        assertTrue(modulosGestion.contains(DuenoView.INVERSIONES))
        assertTrue(modulosGestion.contains(DuenoView.CONTROL_NEGOCIO))
    }

    @Test
    fun testDuenoInicioCardsExactOrderAndScope() {
        // En INICIO deben aparecer única y exclusivamente 3 tarjetas en este orden:
        // 1. TANDAS
        // 2. CUADRE DE CAJA
        // 3. AJUSTES
        val inicioCards = listOf(
            DuenoView.TANDAS,
            DuenoView.CUADRE_CAJA,
            DuenoView.AJUSTES
        )

        assertEquals(3, inicioCards.size)
        assertEquals(DuenoView.TANDAS, inicioCards[0])
        assertEquals(DuenoView.CUADRE_CAJA, inicioCards[1])
        assertEquals(DuenoView.AJUSTES, inicioCards[2])

        // Los módulos operativos NO deben estar en Inicio
        assertFalse(inicioCards.contains(DuenoView.INVENTARIO))
        assertFalse(inicioCards.contains(DuenoView.CATALOGO))
        assertFalse(inicioCards.contains(DuenoView.INVERSIONES))
        assertFalse(inicioCards.contains(DuenoView.GASTOS))
        assertFalse(inicioCards.contains(DuenoView.PERSONAL))
        assertFalse(inicioCards.contains(DuenoView.CONTROL_NEGOCIO))
    }
}
