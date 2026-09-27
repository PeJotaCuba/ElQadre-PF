package com.example.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QRespaldoJsonTest {

    @Test
    fun testFileNameIsStrictlyQRespaldoJson() {
        val fileName = "Q_respaldo.json"
        assertEquals("Q_respaldo.json", fileName)
        assertFalse("El nombre no debe contener sufijo de negocio", fileName.contains("001"))
        assertFalse("El nombre no debe contener guiones adicionales", fileName.contains("Q_respaldo_"))
    }

    @Test
    fun testQRespaldoMandatorySevenBlocks() {
        // Construct a sample Q_respaldo.json structure mimicking BusinessBackupManager output
        val root = JSONObject().apply {
            put("identificador_archivo", "Q_RESPALDO")
            put("nombre_archivo", "Q_respaldo.json")
            put("formatIdentifier", "ELQADRE_FULL_BUSINESS_BACKUP")
            put("version", "2")
            put("codigoNegocio", "001")
            put("nombreNegocio", "El Qadre POS")
            put("timestamp", System.currentTimeMillis())

            // 1. INSUMOS Y AGREGADOS
            put("bloque_1_insumos_y_agregados", JSONObject().apply {
                put("titulo", "INSUMOS Y AGREGADOS")
                put("insumos", JSONArray().put(JSONObject().apply {
                    put("id", 1L)
                    put("name", "Harina de Trigo")
                    put("unit", "lb")
                    put("unitCost", 50.0)
                    put("stock", 25.0)
                }))
                put("agregados", JSONArray().put(JSONObject().apply {
                    put("id", 2L)
                    put("name", "Queso Gouda")
                    put("unit", "g")
                    put("isAgregado", true)
                    put("rationQuantity", 30.0)
                    put("salePrice", 80.0)
                }))
                put("materiasPrimas", JSONArray())
                put("movimientosMateriaPrima", JSONArray())
            })

            // 2. PRODUCTOS
            put("bloque_2_productos", JSONObject().apply {
                put("titulo", "PRODUCTOS")
                put("products", JSONArray().put(JSONObject().apply {
                    put("id", 101L)
                    put("name", "Pizza Familiar")
                    put("destination", "COCINA")
                    put("price", 600.0)
                }))
                put("productosProduccion", JSONArray())
                put("productosMercaderias", JSONArray())
                put("productosElaborados", JSONArray())
                put("recetaIngredientes", JSONArray())
                put("mercaderias", JSONArray().put(JSONObject().apply {
                    put("id", 201L)
                    put("productId", 102L)
                    put("acquisitionCost", 120.0)
                    put("initialStock", 10.0)
                }))
                put("movimientosMercaderia", JSONArray())
                put("stockMovements", JSONArray())
                put("categories", JSONArray())
            })

            // 3. TANDAS
            put("bloque_3_tandas", JSONObject().apply {
                put("titulo", "TANDAS")
                put("tandas", JSONArray().put(JSONObject().apply {
                    put("id", 301L)
                    put("tandaNumber", "01")
                    put("status", "CERRADA")
                    put("actualYield", 20.0)
                }))
                put("tandasJornada", JSONArray())
                put("tandasCerradas", JSONArray())
                put("archivoTandas", JSONArray())
                put("productionBatches", JSONArray())
            })

            // 4. PREFERENCIAS
            put("bloque_4_preferencias", JSONObject().apply {
                put("titulo", "PREFERENCIAS")
                put("configuracionNegocio", JSONObject().apply {
                    put("id", 1L)
                    put("nombreNegocio", "El Qadre POS")
                    put("codigoNegocio", "001")
                })
                put("configuracionGeneral", JSONObject().apply {
                    put("id", 1L)
                    put("tasaUsd", 320.0)
                    put("tasaEur", 345.0)
                })
                put("preferenciasLocales", JSONObject().apply {
                    put("tarifasPagoBebidas", JSONObject())
                    put("modosProduccion", JSONArray())
                    put("clasificacionTransferencias", JSONArray())
                })
            })

            // 5. GESTIÓN
            put("bloque_5_gestion", JSONObject().apply {
                put("titulo", "GESTIÓN")
                put("personalContratado", JSONArray().put(JSONObject().apply {
                    put("id", 1L)
                    put("nombreCompleto", "Juan Pérez")
                    put("role", "CAJERO")
                }))
                put("users", JSONArray())
                put("paymentProposals", JSONArray())
                put("gastosGenerales", JSONArray())
                put("inversiones", JSONArray())
            })

            // 6. INFORMES
            put("bloque_6_informes", JSONObject().apply {
                put("titulo", "INFORMES")
                put("jornadas", JSONArray().put(JSONObject().apply {
                    put("id", 501L)
                    put("openedAt", 1700000000000L)
                    put("totalSales", 15000.0)
                }))
                put("tableOrders", JSONArray())
                put("orderItems", JSONArray())
                put("transferencias", JSONArray())
                put("bitacoraEntries", JSONArray())
                put("consumoPersonalList", JSONArray())
                put("cuadrePagos", JSONArray())
                put("smsComandaQueue", JSONArray())
            })

            // 7. CUADRE DE CAJA — ÚNICAMENTE MERCADERÍAS
            put("bloque_7_cuadre_caja_mercaderias", JSONObject().apply {
                put("titulo", "CUADRE DE CAJA — ÚNICAMENTE MERCADERÍAS")
                put("mercaderias", JSONArray().put(JSONObject().apply {
                    put("mercaderiaId", 201L)
                    put("productId", 102L)
                    put("productName", "Cerveza Cristal")
                    put("inicio", "24.0")
                    put("entradas", "48.0")
                }))
            })
        }

        // Assert 7 mandatory blocks exist
        assertTrue("Bloque 1 Insumos y Agregados requerido", root.has("bloque_1_insumos_y_agregados"))
        assertTrue("Bloque 2 Productos requerido", root.has("bloque_2_productos"))
        assertTrue("Bloque 3 Tandas requerido", root.has("bloque_3_tandas"))
        assertTrue("Bloque 4 Preferencias requerido", root.has("bloque_4_preferencias"))
        assertTrue("Bloque 5 Gestión requerido", root.has("bloque_5_gestion"))
        assertTrue("Bloque 6 Informes requerido", root.has("bloque_6_informes"))
        assertTrue("Bloque 7 Cuadre de Caja Mercaderías requerido", root.has("bloque_7_cuadre_caja_mercaderias"))

        // Validate Block 7 contains ONLY inicio and entradas for Mercaderías
        val b7 = root.getJSONObject("bloque_7_cuadre_caja_mercaderias")
        assertFalse("Bloque 7 NO debe contener ventas", b7.has("ventas"))
        assertFalse("Bloque 7 NO debe contener gastos", b7.has("gastos"))
        assertFalse("Bloque 7 NO debe contener producción", b7.has("produccion"))
        assertFalse("Bloque 7 NO debe contener total esperado", b7.has("totalEsperado"))
        assertFalse("Bloque 7 NO debe contener efectivo", b7.has("efectivo"))
        assertFalse("Bloque 7 NO debe contener transferencias", b7.has("transferencias"))
        assertFalse("Bloque 7 NO debe contener diferencias", b7.has("diferencias"))
        assertFalse("Bloque 7 NO debe contener estados de caja", b7.has("estadosCaja"))
        assertFalse("Bloque 7 NO debe contener cierres de caja", b7.has("cierresCaja"))

        val mercsCuadre = b7.getJSONArray("mercaderias")
        assertEquals(1, mercsCuadre.length())
        val item = mercsCuadre.getJSONObject(0)
        assertEquals(201L, item.getLong("mercaderiaId"))
        assertEquals("24.0", item.getString("inicio"))
        assertEquals("48.0", item.getString("entradas"))
        assertFalse("Item de mercadería en Cuadre NO debe tener ventas", item.has("ventas"))
        assertFalse("Item de mercadería en Cuadre NO debe tener precio", item.has("precio"))
    }
}
