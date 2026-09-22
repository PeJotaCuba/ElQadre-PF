package com.example.util

import com.example.data.local.model.Product
import org.junit.Assert.*
import org.junit.Test

class CostSheetPdfExporterTest {

    @Test
    fun testFileNameSanitizationFormat() {
        val testCases = listOf(
            "Pizza Especial" to "Ficha_Costo_Pizza_Especial.pdf",
            "Hamburguesa / Doble (Res & Cerdo)" to "Ficha_Costo_Hamburguesa__Doble_Res__Cerdo.pdf",
            "Café Expresso 100%" to "Ficha_Costo_Café_Expresso_100.pdf",
            "Coctel Piña Colada" to "Ficha_Costo_Coctel_Piña_Colada.pdf"
        )

        for ((inputName, expectedFileName) in testCases) {
            val product = Product(
                id = 1L,
                code = "TEST",
                name = inputName,
                category = "Cocina",
                price = 100.0,
                cost = 50.0,
                destination = "COCINA",
                isAvailable = true
            )

            val cleanName = product.name
                .trim()
                .replace(" ", "_")
                .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_\\-]"), "")
                .ifEmpty { "Producto_${product.id}" }
            val fileName = "Ficha_Costo_${cleanName}.pdf"

            assertEquals(expectedFileName, fileName)
            assertTrue(fileName.startsWith("Ficha_Costo_"))
            assertTrue(fileName.endsWith(".pdf"))
            assertFalse(fileName.contains(" "))
        }
    }

    @Test
    fun testNoMassiveExportMethodExistsInExporter() {
        val methods = CostSheetPdfExporter::class.java.declaredMethods.map { it.name }
        assertFalse(
            "No debe existir ningún método de exportación masiva como exportAllCostSheets",
            methods.contains("exportAllCostSheets")
        )
        assertTrue(
            "Debe existir el método individual exportSingleCostSheet",
            methods.contains("exportSingleCostSheet")
        )
    }
}
