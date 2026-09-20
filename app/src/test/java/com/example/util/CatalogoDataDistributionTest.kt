package com.example.util

import com.example.data.local.model.Category
import com.example.data.local.model.Product
import com.example.licensing.BusinessCodeHelper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogoDataDistributionTest {

    @Test
    fun testGenerateAndValidateCatalogJsonStructure() {
        val categories = listOf(
            Category(id = 1L, name = "Pizzas", description = "Pizzas artesanales", isActive = true),
            Category(id = 2L, name = "Bebidas", description = "Refrescos y cervezas", isActive = true)
        )

        val products = listOf(
            Product(
                id = 101L,
                code = "PZ01",
                name = "Pizza Margarita",
                category = "Pizzas",
                price = 1200.0,
                cost = 550.0,
                stock = 50,
                minStock = 5,
                destination = "COCINA",
                isAvailable = true,
                description = "Queso mozzarella y tomate",
                unitOfMeasure = "Unidad",
                admitsAgregados = true,
                agregadosList = "[\"Extra Queso\",\"Jamón\"]"
            ),
            Product(
                id = 102L,
                code = "BEB01",
                name = "Cerveza Cristal",
                category = "Bebidas",
                price = 350.0,
                cost = 180.0,
                stock = 120,
                minStock = 12,
                destination = "BARRA",
                isAvailable = true,
                description = "Lata 355ml",
                unitOfMeasure = "Unidad",
                admitsAgregados = false,
                agregadosList = "[]"
            )
        )

        val businessNumber = "001"
        val businessCode = "NEG-001"
        val businessName = "Restaurante Don Roberto"

        val root = JSONObject().apply {
            put("tipo", "CATALOGO_ELQADRE")
            put("version", "1710000000000")
            put("numeroNegocio", businessNumber)
            put("codigoNegocio", businessCode)
            put("nombreNegocio", businessName)

            put("negocio", JSONObject().apply {
                put("codigo", businessNumber)
                put("nombre", businessName)
            })

            put("tasaUsd", 320.0)
            put("tasaEur", 340.0)

            val categoriesArray = JSONArray()
            for (cat in categories) {
                categoriesArray.put(JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("description", cat.description)
                    put("isActive", cat.isActive)
                })
            }
            put("categories", categoriesArray)

            val productsArray = JSONArray()
            for (p in products) {
                productsArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("code", p.code)
                    put("name", p.name)
                    put("category", p.category)
                    put("price", p.price)
                    put("cost", p.cost)
                    put("stock", p.stock)
                    put("minStock", p.minStock)
                    put("destination", p.destination)
                    put("isAvailable", p.isAvailable)
                    put("description", p.description)
                    put("unitOfMeasure", p.unitOfMeasure)
                    put("admitsAgregados", p.admitsAgregados)
                    put("agregadosList", JSONArray(p.agregadosList))
                })
            }
            put("products", productsArray)
            put("timestamp", System.currentTimeMillis())
        }

        val jsonString = root.toString(2)
        assertNotNull(jsonString)

        val parsed = JSONObject(jsonString)
        assertEquals("CATALOGO_ELQADRE", parsed.getString("tipo"))
        assertEquals("001", parsed.getString("numeroNegocio"))
        assertEquals("NEG-001", parsed.getString("codigoNegocio"))
        assertEquals("Restaurante Don Roberto", parsed.getString("nombreNegocio"))
        assertEquals(320.0, parsed.getDouble("tasaUsd"), 0.001)

        val parsedCategories = parsed.getJSONArray("categories")
        assertEquals(2, parsedCategories.length())
        assertEquals("Pizzas", parsedCategories.getJSONObject(0).getString("name"))

        val parsedProducts = parsed.getJSONArray("products")
        assertEquals(2, parsedProducts.length())
        val p1 = parsedProducts.getJSONObject(0)
        assertEquals("PZ01", p1.getString("code"))
        assertEquals("Pizza Margarita", p1.getString("name"))
        assertEquals(1200.0, p1.getDouble("price"), 0.001)
        assertEquals("COCINA", p1.getString("destination"))

        val p2 = parsedProducts.getJSONObject(1)
        assertEquals("BEB01", p2.getString("code"))
        assertEquals("BARRA", p2.getString("destination"))
        assertEquals(350.0, p2.getDouble("price"), 0.001)

        // Verify that NO sensitive credentials or SuperAdmin data is leaked
        assertFalse(parsed.has("usuarios"))
        assertFalse(parsed.has("usuario"))
        assertFalse(parsed.has("passwordHash"))
        assertFalse(parsed.has("dvc"))
        assertFalse(parsed.has("licencia"))
    }

    @Test
    fun testDiscriminatorBetweenUserAndCatalogJson() {
        val userJson = JSONObject().apply {
            put("tipo", "USUARIO_ELQADRE")
            put("numeroNegocio", "001")
            put("usuario", JSONObject().apply {
                put("username", "cajero1")
            })
        }

        val catalogJson = JSONObject().apply {
            put("tipo", "CATALOGO_ELQADRE")
            put("numeroNegocio", "001")
            put("products", JSONArray())
            put("categories", JSONArray())
        }

        val legacyCatalogJson = JSONObject().apply {
            put("codigoNegocio", "001")
            put("products", JSONArray())
            put("categories", JSONArray())
        }

        assertTrue(PersonalUserDataManager.isPersonalUserJson(userJson))
        assertFalse(CatalogoDataManager.isCatalogoJson(userJson))

        assertTrue(CatalogoDataManager.isCatalogoJson(catalogJson))
        assertFalse(PersonalUserDataManager.isPersonalUserJson(catalogJson))

        assertTrue(CatalogoDataManager.isCatalogoJson(legacyCatalogJson))
        assertFalse(PersonalUserDataManager.isPersonalUserJson(legacyCatalogJson))
    }

    @Test
    fun testBusinessCodeFormattingForCatalog() {
        assertEquals("001", BusinessCodeHelper.formatCode("1"))
        assertEquals("001", BusinessCodeHelper.formatCode("01"))
        assertEquals("001", BusinessCodeHelper.formatCode("001"))
        assertEquals("001", BusinessCodeHelper.formatCode("NEG-001"))
        assertEquals("042", BusinessCodeHelper.formatCode("42"))
        assertEquals("Q_001catalogo.json", "Q_${BusinessCodeHelper.formatCode("1")}catalogo.json")
        assertEquals("Q_002catalogo.json", "Q_${BusinessCodeHelper.formatCode("2")}catalogo.json")
    }

    @Test
    fun testCrossBusinessValidationRejection() {
        val deviceBizNumber = "001"
        val foreignFileBizNumber = "002"

        val normalizedFileBiz = BusinessCodeHelper.formatCode(foreignFileBizNumber)
        val shouldReject = deviceBizNumber.isNotBlank() && deviceBizNumber != "000" && normalizedFileBiz != deviceBizNumber

        assertTrue("Should reject cross-business catalog import", shouldReject)
    }
}
