package com.example.util

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.model.Category
import com.example.data.local.model.Product
import com.example.data.local.model.Mercaderia
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.ProductoElaborado
import com.example.data.local.model.RecetaIngrediente
import com.example.data.local.model.Inversion
import com.example.data.local.model.BitacoraEntry
import com.example.data.local.model.ConfiguracionGeneral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object DataUpdateManager {

    enum class UpdateResult {
        Success, NoConnection, DownloadError, InvalidFile, NoNewUpdate
    }

    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_ERROR = "ERROR"

    private fun cleanJsonString(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim()
        }
        if (s.startsWith("```")) {
            s = s.replaceFirst(Regex("^```(?:json)?\\s*"), "")
            if (s.endsWith("```")) {
                s = s.substring(0, s.length - 3).trim()
            }
        }
        return s.trim()
    }

    private fun fetchUrlWithRedirects(urlString: String, maxRedirects: Int = 5): Pair<Int, String?> {
        var currentUrl = urlString.trim()
        var redirects = 0
        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")

            try {
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(url, location).toString()
                        }
                        redirects++
                        continue
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line).append("\n")
                    }
                    reader.close()
                    return Pair(responseCode, response.toString())
                } else {
                    return Pair(responseCode, null)
                }
            } finally {
                connection.disconnect()
            }
        }
        return Pair(-1, null)
    }

    suspend fun updateCatalogo(db: AppDatabase, urlString: String): UpdateResult = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext UpdateResult.DownloadError
        
        try {
            val (code, jsonBody) = fetchUrlWithRedirects(urlString)
            if (code != HttpURLConnection.HTTP_OK || jsonBody == null) {
                return@withContext UpdateResult.DownloadError
            }
            
            val jsonString = cleanJsonString(jsonBody)
            val jsonObject = JSONObject(jsonString)
            
            if (!jsonObject.has("version") || !jsonObject.has("categories") || !jsonObject.has("products")) {
                return@withContext UpdateResult.InvalidFile
            }
            
            val remoteVersion = jsonObject.getString("version")
            val currentConfig = db.configuracionGeneralDao().getConfigSync()
            
            if (currentConfig != null && 
                currentConfig.lastCatalogoUpdateVersion == remoteVersion && 
                currentConfig.lastCatalogoUpdateStatus == STATUS_SUCCESS) {
                return@withContext UpdateResult.NoNewUpdate
            }
            
            val categoriesArray = jsonObject.getJSONArray("categories")
            val newCategories = mutableListOf<Category>()
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(i)
                newCategories.add(Category(
                    id = catObj.optLong("id", 0),
                    name = catObj.getString("name"),
                    description = catObj.optString("description", ""),
                    isActive = catObj.optBoolean("isActive", true)
                ))
            }
            
            val productsArray = jsonObject.getJSONArray("products")
            val newProducts = mutableListOf<Product>()
            for (i in 0 until productsArray.length()) {
                val prodObj = productsArray.getJSONObject(i)
                val initialStockVal = when {
                    prodObj.has("initialStock") -> prodObj.optInt("initialStock", prodObj.optInt("stock", 0))
                    prodObj.has("cantidadInicial") -> prodObj.optInt("cantidadInicial", prodObj.optInt("stock", 0))
                    prodObj.has("existenciaInicial") -> prodObj.optInt("existenciaInicial", prodObj.optInt("stock", 0))
                    prodObj.has("stock_inicial") -> prodObj.optInt("stock_inicial", prodObj.optInt("stock", 0))
                    prodObj.has("stock") -> prodObj.optInt("stock", 0)
                    else -> 0
                }
                newProducts.add(Product(
                    id = prodObj.optLong("id", 0),
                    code = prodObj.getString("code"),
                    name = prodObj.getString("name"),
                    category = prodObj.getString("category"),
                    price = prodObj.getDouble("price"),
                    cost = prodObj.optDouble("cost", 0.0),
                    stock = initialStockVal,
                    minStock = prodObj.optInt("minStock", 3),
                    destination = prodObj.optString("destination", "COCINA"),
                    isAvailable = prodObj.optBoolean("isAvailable", true),
                    description = prodObj.optString("description", ""),
                    unitOfMeasure = prodObj.optString("unitOfMeasure", "Unidad"),
                    imagePath = if (prodObj.has("imagePath") && !prodObj.isNull("imagePath")) prodObj.getString("imagePath") else null,
                    admitsAgregados = prodObj.optBoolean("admitsAgregados", false),
                    agregadosList = if (prodObj.has("agregadosList")) prodObj.getJSONArray("agregadosList").toString() else "[]"
                ))
            }
            
            // Apply atomically
            db.withTransaction {
                // Clear existing
                db.categoryDao().deleteAllCategories()
                db.productDao().deleteAllProducts()
                
                // Insert new
                newCategories.forEach { db.categoryDao().insertCategory(it) }
                newProducts.forEach { db.productDao().insertProduct(it) }
                
                if (currentConfig != null) {
                    val remoteTasaUsd = if (jsonObject.has("tasaUsd")) jsonObject.getDouble("tasaUsd") else currentConfig.tasaUsd
                    val remoteTasaEur = if (jsonObject.has("tasaEur")) jsonObject.getDouble("tasaEur") else currentConfig.tasaEur
                    db.configuracionGeneralDao().updateConfig(
                        currentConfig.copy(
                            lastCatalogoUpdateDate = System.currentTimeMillis(),
                            lastCatalogoUpdateStatus = STATUS_SUCCESS,
                            lastCatalogoUpdateVersion = remoteVersion,
                            tasaUsd = remoteTasaUsd,
                            tasaEur = remoteTasaEur
                        )
                    )
                }
            }
            
            return@withContext UpdateResult.Success
            
        } catch (e: UnknownHostException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: SocketTimeoutException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateResult.InvalidFile
        }
    }

    suspend fun updateMercainv(db: AppDatabase, urlString: String): UpdateResult = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext UpdateResult.DownloadError
        
        try {
            val (code, jsonBody) = fetchUrlWithRedirects(urlString)
            if (code != HttpURLConnection.HTTP_OK || jsonBody == null) {
                return@withContext UpdateResult.DownloadError
            }
            
            val jsonString = cleanJsonString(jsonBody)
            val jsonObject = JSONObject(jsonString)
            
            if (!jsonObject.has("version") || !jsonObject.has("mercaderias")) {
                return@withContext UpdateResult.InvalidFile
            }
            
            val remoteVersion = jsonObject.getString("version")
            val currentConfig = db.configuracionGeneralDao().getConfigSync()
            
            if (currentConfig != null && 
                currentConfig.lastMercainvUpdateVersion == remoteVersion && 
                currentConfig.lastMercainvUpdateStatus == STATUS_SUCCESS) {
                return@withContext UpdateResult.NoNewUpdate
            }
            
            val mercaderiasArray = jsonObject.getJSONArray("mercaderias")
            val newMercaderias = mutableListOf<Mercaderia>()
            for (i in 0 until mercaderiasArray.length()) {
                val mercObj = mercaderiasArray.getJSONObject(i)
                newMercaderias.add(Mercaderia(
                    id = mercObj.getLong("id"),
                    productId = mercObj.getLong("productId"),
                    acquisitionCost = mercObj.getDouble("acquisitionCost"),
                    unitOfMeasure = mercObj.getString("unitOfMeasure"),
                    initialStock = mercObj.getDouble("initialStock"),
                    isActive = mercObj.optBoolean("isActive", true)
                ))
            }
            
            // Apply atomically
            db.withTransaction {
                // Clear existing
                db.mercaderiaDao().deleteAllMercaderias()
                
                newMercaderias.forEach { db.mercaderiaDao().insert(it) }
                
                if (currentConfig != null) {
                    db.configuracionGeneralDao().updateConfig(
                        currentConfig.copy(
                            lastMercainvUpdateDate = System.currentTimeMillis(),
                            lastMercainvUpdateStatus = STATUS_SUCCESS,
                            lastMercainvUpdateVersion = remoteVersion
                        )
                    )
                }
            }
            
            return@withContext UpdateResult.Success
            
        } catch (e: UnknownHostException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: SocketTimeoutException) {
            return@withContext UpdateResult.NoConnection
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateResult.InvalidFile
        }
    }

    suspend fun importQProduccion(db: AppDatabase, jsonBody: String, username: String = "Dueño"): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = cleanJsonString(jsonBody)
            val jsonObject = JSONObject(jsonString)

            val insumosArr = jsonObject.optJSONArray("insumos")
            val productosArr = jsonObject.optJSONArray("productos")

            if (insumosArr == null && productosArr == null) {
                return@withContext false
            }

            db.withTransaction {
                // 1. Process Insumos (Materias Primas)
                if (insumosArr != null) {
                    val existingMps = db.materiaPrimaDao().getAllSync().associateBy { it.id }
                    for (i in 0 until insumosArr.length()) {
                        val mpObj = insumosArr.getJSONObject(i)
                        val id = mpObj.optLong("id", 0L)
                        val name = mpObj.optString("name", "Insumo")
                        val unit = mpObj.optString("unit", "g")
                        val unitCost = mpObj.optDouble("unitCost", 0.0)
                        val initialStock = mpObj.optDouble("initialStock", mpObj.optDouble("stock", 0.0))
                        val stock = mpObj.optDouble("stock", initialStock)
                        val purchasePrice = mpObj.optDouble("purchasePrice", 0.0)
                        val purchaseUnit = mpObj.optString("purchaseUnit", unit)
                        val purchaseQuantity = mpObj.optDouble("purchaseQuantity", 1.0)
                        val isActive = mpObj.optBoolean("isActive", true)

                        val existing = if (id > 0) existingMps[id] else null
                        val mpToSave = MateriaPrima(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            unit = unit,
                            unitCost = unitCost,
                            isActive = isActive,
                            stock = existing?.stock ?: stock,
                            initialStock = if (existing != null) existing.initialStock else initialStock,
                            purchasePrice = purchasePrice,
                            purchaseUnit = purchaseUnit,
                            purchaseQuantity = purchaseQuantity
                        )
                        db.materiaPrimaDao().insert(mpToSave)
                    }
                }

                // 2. Process Productos Elaborados & Recetas
                if (productosArr != null) {
                    val existingProducts = db.productDao().getAllProductsSync().associateBy { it.id }
                    val existingElaborados = db.productoElaboradoDao().getAllSync().associateBy { it.productId }

                    for (i in 0 until productosArr.length()) {
                        val prodObj = productosArr.getJSONObject(i)
                        val id = prodObj.optLong("id", 0L)
                        val name = prodObj.optString("name", "Producto")
                        val code = prodObj.optString("code", "")
                        val category = prodObj.optString("category", "Cocina")
                        val salePrice = prodObj.optDouble("salePrice", 0.0)
                        val unitOfMeasure = prodObj.optString("unitOfMeasure", "unidad")
                        val destination = prodObj.optString("destination", "COCINA")
                        val isAvailable = prodObj.optBoolean("isAvailable", true)
                        val ppd = prodObj.optDouble("ppd", 10.0)

                        val existingProd = if (id > 0) existingProducts[id] else null
                        val productToSave = Product(
                            id = if (existingProd != null) existingProd.id else (if (id > 0) id else 0L),
                            code = code,
                            name = name,
                            category = category,
                            price = salePrice,
                            cost = prodObj.optDouble("costoTotalUnitario", 0.0),
                            stock = existingProd?.stock ?: 0,
                            minStock = existingProd?.minStock ?: 3,
                            destination = destination,
                            isAvailable = isAvailable,
                            description = existingProd?.description ?: "",
                            unitOfMeasure = unitOfMeasure,
                            imagePath = existingProd?.imagePath,
                            admitsAgregados = existingProd?.admitsAgregados ?: false,
                            agregadosList = existingProd?.agregadosList ?: "[]"
                        )
                        val savedProdId = db.productDao().insertProduct(productToSave)
                        val targetProdId = if (savedProdId > 0) savedProdId else productToSave.id

                        val existingElab = existingElaborados[targetProdId]
                        val elabToSave = ProductoElaborado(
                            id = existingElab?.id ?: 0L,
                            productId = targetProdId,
                            isActive = isAvailable,
                            recipeName = "Receta $name",
                            productionUnit = unitOfMeasure,
                            baseYield = 1.0,
                            baseMateriaPrimaId = existingElab?.baseMateriaPrimaId ?: 0L,
                            baseQuantity = existingElab?.baseQuantity ?: 0.0,
                            ppd = ppd,
                            precioDefinitivo = salePrice,
                            hasPrecioDefinitivo = salePrice > 0.0
                        )
                        val savedElabId = db.productoElaboradoDao().insert(elabToSave)
                        val targetElabId = if (savedElabId > 0) savedElabId else elabToSave.id

                        // Process Receta
                        val recetaArr = prodObj.optJSONArray("receta")
                        if (recetaArr != null) {
                            db.recetaIngredienteDao().deleteIngredientsForProduct(targetElabId)
                            db.recetaIngredienteDao().deleteIngredientsForProduct(targetProdId)
                            for (j in 0 until recetaArr.length()) {
                                val ingObj = recetaArr.getJSONObject(j)
                                val mpId = ingObj.optLong("materiaPrimaId", 0L)
                                val qty = ingObj.optDouble("quantity", 0.0)
                                val unit = ingObj.optString("unit", "g")
                                if (mpId > 0 && qty > 0) {
                                    db.recetaIngredienteDao().insert(
                                        RecetaIngrediente(
                                            productoElaboradoId = targetElabId,
                                            materiaPrimaId = mpId,
                                            quantity = qty,
                                            unit = unit
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Register Bitacora
                db.bitacoraDao().insertEntry(
                    BitacoraEntry(
                        title = "Importación de Q_produccion.json",
                        content = "Datos de producción actualizados exitosamente por $username.",
                        category = "PRODUCCIÓN",
                        authorUsername = username,
                        priority = "NORMAL"
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importQMercancias(db: AppDatabase, jsonBody: String, username: String = "Dueño"): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = cleanJsonString(jsonBody)
            val jsonObject = JSONObject(jsonString)

            val mercanciasArr = jsonObject.optJSONArray("mercancias") ?: jsonObject.optJSONArray("mercaderias")
            if (mercanciasArr == null) {
                return@withContext false
            }

            db.withTransaction {
                val existingMercs = db.mercaderiaDao().getAllSync().associateBy { it.id }
                val existingProds = db.productDao().getAllProductsSync().associateBy { it.id }

                for (i in 0 until mercanciasArr.length()) {
                    val mercObj = mercanciasArr.getJSONObject(i)
                    val id = mercObj.optLong("id", 0L)
                    val productId = mercObj.optLong("productId", 0L)
                    val name = mercObj.optString("name", "Mercadería")
                    val code = mercObj.optString("code", "")
                    val category = mercObj.optString("category", "Bebidas")
                    val unitOfMeasure = mercObj.optString("unitOfMeasure", "u")
                    val purchasePrice = mercObj.optDouble("purchasePrice", mercObj.optDouble("acquisitionCost", 0.0))
                    val salePrice = mercObj.optDouble("salePrice", 0.0)
                    val directExpenses = mercObj.optDouble("directExpenses", 0.0)
                    val initialStock = mercObj.optDouble("initialStock", 0.0)
                    val currentStock = mercObj.optDouble("currentStock", initialStock)
                    val isActive = mercObj.optBoolean("isActive", true)
                    val directExpensesDetails = mercObj.optJSONArray("directExpensesDetails")?.toString() ?: "[]"

                    // Ensure associated Product exists or update price
                    val existingProd = if (productId > 0) existingProds[productId] else null
                    val prodToSave = Product(
                        id = if (existingProd != null) existingProd.id else (if (productId > 0) productId else 0L),
                        code = code,
                        name = name,
                        category = category,
                        price = if (salePrice > 0.0) salePrice else (existingProd?.price ?: 0.0),
                        cost = purchasePrice + directExpenses,
                        stock = existingProd?.stock ?: currentStock.toInt(),
                        minStock = existingProd?.minStock ?: 5,
                        destination = "BARRA",
                        isAvailable = isActive,
                        description = existingProd?.description ?: "",
                        unitOfMeasure = unitOfMeasure,
                        imagePath = existingProd?.imagePath,
                        admitsAgregados = existingProd?.admitsAgregados ?: false,
                        agregadosList = existingProd?.agregadosList ?: "[]"
                    )
                    val savedProdId = db.productDao().insertProduct(prodToSave)
                    val targetProdId = if (savedProdId > 0) savedProdId else prodToSave.id

                    val existingMerc = if (id > 0) existingMercs[id] else null
                    val mercToSave = Mercaderia(
                        id = if (existingMerc != null) existingMerc.id else (if (id > 0) id else 0L),
                        productId = targetProdId,
                        acquisitionCost = purchasePrice,
                        unitOfMeasure = unitOfMeasure,
                        initialStock = if (existingMerc != null) existingMerc.initialStock else initialStock,
                        isActive = isActive,
                        directExpenses = directExpenses,
                        directExpensesDetails = directExpensesDetails
                    )
                    db.mercaderiaDao().insert(mercToSave)
                }

                // Register Bitacora
                db.bitacoraDao().insertEntry(
                    BitacoraEntry(
                        title = "Importación de Q_mercaderias.json",
                        content = "Datos de mercaderías actualizados exitosamente por $username.",
                        category = "MERCADERÍAS",
                        authorUsername = username,
                        priority = "NORMAL"
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importQInversiones(db: AppDatabase, jsonBody: String, username: String = "Dueño"): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = cleanJsonString(jsonBody)
            val jsonObject = JSONObject(jsonString)

            val invArr = jsonObject.optJSONArray("inversiones")
            if (invArr == null) {
                return@withContext false
            }

            db.withTransaction {
                val existingInvs = db.inversionDao().getAllSync().associateBy { it.id }

                for (i in 0 until invArr.length()) {
                    val invObj = invArr.getJSONObject(i)
                    val id = invObj.optLong("id", 0L)
                    val name = invObj.optString("name", "Inversión")
                    val category = invObj.optString("category", "Equipos")
                    val amount = invObj.optDouble("amount", 0.0)
                    val usefulLife = invObj.optDouble("usefulLife", 12.0)
                    val usefulLifeUnit = invObj.optString("usefulLifeUnit", "MESES")
                    val date = invObj.optLong("date", System.currentTimeMillis())
                    val observation = invObj.optString("observation", "")
                    val scope = invObj.optString("scope", "PRODUCCION")
                    val currency = invObj.optString("currency", "CUP")
                    val originalAmount = invObj.optDouble("originalAmount", amount)
                    val exchangeRate = invObj.optDouble("exchangeRate", 1.0)
                    val convertedAmount = invObj.optDouble("convertedAmount", amount)

                    val existing = if (id > 0) existingInvs[id] else null
                    val invToSave = Inversion(
                        id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                        name = name,
                        category = category,
                        amount = amount,
                        date = date,
                        usefulLife = usefulLife,
                        usefulLifeUnit = usefulLifeUnit,
                        observation = observation,
                        scope = scope,
                        currency = currency,
                        originalAmount = originalAmount,
                        exchangeRate = exchangeRate,
                        convertedAmount = convertedAmount
                    )
                    db.inversionDao().insert(invToSave)
                }

                // Register Bitacora
                db.bitacoraDao().insertEntry(
                    BitacoraEntry(
                        title = "Importación de Q_inversiones.json",
                        content = "Datos de inversiones actualizados exitosamente por $username.",
                        category = "INVERSIONES",
                        authorUsername = username,
                        priority = "NORMAL"
                    )
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun extractTimestampMillis(jsonObject: JSONObject): Long {
        val tsMs = jsonObject.optLong("timestamp_ms", 0L)
        if (tsMs > 0L) return tsMs

        val verLong = jsonObject.optString("version", "").toLongOrNull()
        if (verLong != null && verLong > 1000000000000L) return verLong

        val dateCandidates = listOf(
            jsonObject.optString("fecha_hora_generacion"),
            jsonObject.optString("fechaHoraCierre"),
            jsonObject.optString("fecha_generacion"),
            jsonObject.optString("fecha_publicacion")
        ).filter { it.isNotBlank() }

        val formats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        )

        for (str in dateCandidates) {
            for (fmt in formats) {
                try {
                    val parsed = fmt.parse(str)
                    if (parsed != null && parsed.time > 0L) {
                        return parsed.time
                    }
                } catch (_: Exception) {}
            }
        }

        return 0L
    }

    fun formatTimestampToDisplay(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Desconocida"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestampMs))
    }

    suspend fun updateQDueno(
        db: AppDatabase,
        urlString: String,
        username: String = "Dueño",
        context: android.content.Context? = null
    ): UpdateResult = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext UpdateResult.DownloadError

        try {
            val (code, jsonBody) = fetchUrlWithRedirects(urlString)
            if (code !in 200..299 || jsonBody.isNullOrBlank()) {
                return@withContext UpdateResult.DownloadError
            }

            when (val res = importQDueno(db, jsonBody, username, context)) {
                is QDuenoUpdateResult.Success -> UpdateResult.Success
                is QDuenoUpdateResult.VersionNotNewer -> UpdateResult.NoNewUpdate
                is QDuenoUpdateResult.InvalidFile -> UpdateResult.InvalidFile
                is QDuenoUpdateResult.Error -> UpdateResult.DownloadError
            }
        } catch (e: UnknownHostException) {
            UpdateResult.NoConnection
        } catch (e: SocketTimeoutException) {
            UpdateResult.NoConnection
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateResult.DownloadError
        }
    }

    sealed class QDuenoUpdateResult {
        data class Success(val version: String, val fechaPublicacion: String) : QDuenoUpdateResult()
        data class VersionNotNewer(val message: String) : QDuenoUpdateResult()
        data class InvalidFile(val reason: String) : QDuenoUpdateResult()
        data class Error(val message: String) : QDuenoUpdateResult()
    }

    suspend fun importQDueno(
        db: AppDatabase,
        jsonBody: String,
        username: String = "Dueño",
        context: android.content.Context? = null
    ): QDuenoUpdateResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = cleanJsonString(jsonBody)
            if (jsonString.isBlank() || (!jsonString.startsWith("{") && !jsonString.startsWith("["))) {
                return@withContext QDuenoUpdateResult.InvalidFile("El archivo proporcionado no es un JSON válido o está vacío.")
            }

            val jsonObject = JSONObject(jsonString)

            val idArchivo = jsonObject.optString("archivo", jsonObject.optString("identificador_archivo", ""))
            val idTag = jsonObject.optString("identificador_archivo", "")
            if (!idArchivo.contains("dueño", ignoreCase = true) &&
                !idArchivo.contains("dueno", ignoreCase = true) &&
                !idTag.contains("Q_DUENO", ignoreCase = true) &&
                !idTag.contains("Q_DUEÑO", ignoreCase = true)
            ) {
                return@withContext QDuenoUpdateResult.InvalidFile("El archivo no contiene un formato válido de Q_dueño.json.")
            }

            // Business Code Validation (Rules 3 & 11)
            val remoteBizCodeRaw = jsonObject.optString("codigoNegocio",
                jsonObject.optJSONObject("negocio")?.optString("codigo") ?:
                jsonObject.optJSONObject("negocio")?.optString("codigoNegocio") ?: ""
            )
            val remoteBizCode = if (remoteBizCodeRaw.isNotBlank()) com.example.licensing.BusinessCodeHelper.formatCode(remoteBizCodeRaw) else ""

            val configNegocio = db.configuracionNegocioDao().getConfigSync()
            val configGeneral = db.configuracionGeneralDao().getConfigSync()
            val localBizCode = com.example.licensing.BusinessCodeHelper.resolveBusinessCode(context, configNegocio, configGeneral)

            if (remoteBizCode.isNotBlank() && remoteBizCode != localBizCode) {
                return@withContext QDuenoUpdateResult.InvalidFile(
                    "El archivo pertenece a un negocio diferente (Negocio $remoteBizCode) y no corresponde al negocio actual (Negocio $localBizCode)."
                )
            }

            val version = jsonObject.optString("version", "")
            val fechaPublicacion = jsonObject.optString("fecha_publicacion", jsonObject.optString("fecha_generacion", ""))

            val remoteTimestamp = extractTimestampMillis(jsonObject)
            val lastProcessedTimestamp = configGeneral?.lastQDuenoUpdateDate ?: 0L

            if (remoteTimestamp > 0L && lastProcessedTimestamp > 0L && remoteTimestamp <= lastProcessedTimestamp) {
                val remoteStr = formatTimestampToDisplay(remoteTimestamp)
                val localStr = formatTimestampToDisplay(lastProcessedTimestamp)
                return@withContext QDuenoUpdateResult.VersionNotNewer(
                    "No existen datos nuevos. La versión del archivo remoto ($remoteStr) es igual o anterior a la última versión procesada localmente ($localStr)."
                )
            }

            if (isVersionOrDateOlderOrEqual(version, fechaPublicacion, configGeneral?.lastQDuenoUpdateVersion ?: "", configGeneral?.lastQDuenoUpdateFechaPublicacion ?: "")) {
                return@withContext QDuenoUpdateResult.VersionNotNewer(
                    "La información disponible ya corresponde a una versión igual o más reciente."
                )
            }

            val dataContainer = jsonObject.optJSONObject("datos") ?: jsonObject

            db.withTransaction {
                // 1. Producción Section
                val produccionObj = dataContainer.optJSONObject("produccion")
                if (produccionObj != null) {
                    val insumosArr = produccionObj.optJSONArray("materias_primas") ?: produccionObj.optJSONArray("insumos")
                    val productosArr = produccionObj.optJSONArray("productos_elaborados") ?: produccionObj.optJSONArray("productos")

                    if (insumosArr != null) {
                        val existingMps = db.materiaPrimaDao().getAllSync().associateBy { it.id }
                        for (i in 0 until insumosArr.length()) {
                            val mpObj = insumosArr.getJSONObject(i)
                            val id = mpObj.optLong("id", 0L)
                            val name = mpObj.optString("name", mpObj.optString("nombre", "Insumo"))
                            val unit = mpObj.optString("unit", mpObj.optString("unidad", "g"))
                            val unitCost = mpObj.optDouble("unitCost", mpObj.optDouble("costo_unitario", 0.0))
                            val initialStock = mpObj.optDouble("initialStock", mpObj.optDouble("stock_inicial", mpObj.optDouble("stock", 0.0)))
                            val stock = mpObj.optDouble("stock", mpObj.optDouble("stock_actual", initialStock))
                            val purchasePrice = mpObj.optDouble("purchasePrice", mpObj.optDouble("precio_compra", 0.0))
                            val purchaseUnit = mpObj.optString("purchaseUnit", mpObj.optString("unidad_compra", unit))
                            val purchaseQuantity = mpObj.optDouble("purchaseQuantity", mpObj.optDouble("cantidad_compra", 1.0))
                            val isActive = mpObj.optBoolean("isActive", mpObj.optBoolean("activo", true))

                            val existing = if (id > 0) existingMps[id] else null
                            val mpToSave = MateriaPrima(
                                id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                                name = name,
                                unit = unit,
                                unitCost = unitCost,
                                isActive = isActive,
                                stock = existing?.stock ?: stock,
                                initialStock = if (existing != null) existing.initialStock else initialStock,
                                purchasePrice = purchasePrice,
                                purchaseUnit = purchaseUnit,
                                purchaseQuantity = purchaseQuantity
                            )
                            db.materiaPrimaDao().insert(mpToSave)
                        }
                    }

                    if (productosArr != null) {
                        val existingProducts = db.productDao().getAllProductsSync().associateBy { it.id }
                        val existingElaborados = db.productoElaboradoDao().getAllSync().associateBy { it.productId }

                        for (i in 0 until productosArr.length()) {
                            val prodObj = productosArr.getJSONObject(i)
                            val id = prodObj.optLong("id", 0L)
                            val name = prodObj.optString("name", prodObj.optString("nombre", "Producto"))
                            val code = prodObj.optString("code", prodObj.optString("codigo", ""))
                            val category = prodObj.optString("category", prodObj.optString("categoria", "Cocina"))
                            val salePrice = prodObj.optDouble("salePrice", prodObj.optDouble("precio_venta", 0.0))
                            val unitOfMeasure = prodObj.optString("unitOfMeasure", prodObj.optString("unidad_medida", "unidad"))
                            val destination = prodObj.optString("destination", prodObj.optString("destino", "COCINA"))
                            val isAvailable = prodObj.optBoolean("isAvailable", prodObj.optBoolean("disponible", true))
                            val ppd = prodObj.optDouble("ppd", 10.0)

                            val existingProd = if (id > 0) existingProducts[id] else null
                            val productToSave = Product(
                                id = if (existingProd != null) existingProd.id else (if (id > 0) id else 0L),
                                code = code,
                                name = name,
                                category = category,
                                price = salePrice,
                                cost = prodObj.optDouble("costoTotalUnitario", prodObj.optDouble("costo_unitario", 0.0)),
                                stock = existingProd?.stock ?: 0,
                                minStock = existingProd?.minStock ?: 3,
                                destination = destination,
                                isAvailable = isAvailable,
                                description = existingProd?.description ?: "",
                                unitOfMeasure = unitOfMeasure,
                                imagePath = existingProd?.imagePath,
                                admitsAgregados = existingProd?.admitsAgregados ?: false,
                                agregadosList = existingProd?.agregadosList ?: "[]"
                            )
                            val savedProdId = db.productDao().insertProduct(productToSave)
                            val targetProdId = if (savedProdId > 0) savedProdId else productToSave.id

                            val existingElab = existingElaborados[targetProdId]
                            val elabToSave = ProductoElaborado(
                                id = existingElab?.id ?: 0L,
                                productId = targetProdId,
                                isActive = isAvailable,
                                recipeName = "Receta $name",
                                productionUnit = unitOfMeasure,
                                baseYield = prodObj.optDouble("expectedBatchYield", prodObj.optDouble("rendimiento_esperado", 1.0)),
                                targetMarginPct = prodObj.optDouble("targetProfitMarginPercentage", prodObj.optDouble("margen_objetivo_porcentaje", 30.0)),
                                ppd = ppd,
                                precioDefinitivo = prodObj.optDouble("precioDefinitivo", prodObj.optDouble("precio_definitivo", 0.0)),
                                hasPrecioDefinitivo = prodObj.optBoolean("hasPrecioDefinitivo", prodObj.optBoolean("tiene_precio_definitivo", false))
                            )
                            val elabId = db.productoElaboradoDao().insert(elabToSave)
                            val targetElabId = if (elabId > 0) elabId else elabToSave.id

                            val recetaArr = prodObj.optJSONArray("receta")
                            if (recetaArr != null) {
                                db.recetaIngredienteDao().deleteIngredientsForProduct(targetElabId)
                                for (j in 0 until recetaArr.length()) {
                                    val rObj = recetaArr.getJSONObject(j)
                                    val mpId = rObj.optLong("materiaPrimaId", rObj.optLong("materia_prima_id", 0L))
                                    val qty = rObj.optDouble("quantityNeeded", rObj.optDouble("cantidad_necesaria", 0.0))
                                    val unit = rObj.optString("unit", rObj.optString("unidad", "g"))
                                    if (mpId > 0) {
                                        db.recetaIngredienteDao().insert(
                                            RecetaIngrediente(
                                                productoElaboradoId = targetElabId,
                                                materiaPrimaId = mpId,
                                                quantity = qty,
                                                unit = unit
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Mercaderías Section
                val mercaderiasArr = dataContainer.optJSONArray("mercaderias") ?: dataContainer.optJSONArray("mercancias") ?: jsonObject.optJSONArray("mercaderias")
                if (mercaderiasArr != null) {
                    val existingProducts = db.productDao().getAllProductsSync().associateBy { it.id }
                    val existingMercs = db.mercaderiaDao().getAllSync().associateBy { it.productId }

                    for (i in 0 until mercaderiasArr.length()) {
                        val mObj = mercaderiasArr.getJSONObject(i)
                        val id = mObj.optLong("id", 0L)
                        val prodId = mObj.optLong("productId", mObj.optLong("product_id", id))
                        val name = mObj.optString("name", mObj.optString("nombre", "Mercadería"))
                        val code = mObj.optString("code", mObj.optString("codigo", ""))
                        val category = mObj.optString("category", mObj.optString("categoria", "Bebidas"))
                        val acquisitionCost = mObj.optDouble("acquisitionCost", mObj.optDouble("costo_adquisicion", 0.0))
                        val salePrice = mObj.optDouble("salePrice", mObj.optDouble("precio_venta", 0.0))
                        val unitOfMeasure = mObj.optString("unitOfMeasure", mObj.optString("unidad_medida", "unidad"))
                        val initialStock = mObj.optDouble("initialStock", mObj.optDouble("stock_inicial", 0.0))
                        val currentStock = mObj.optDouble("currentStock", mObj.optDouble("stock_actual", initialStock))
                        val isActive = mObj.optBoolean("isActive", mObj.optBoolean("activo", true))
                        val directExpenses = mObj.optDouble("gastosDirectos", mObj.optDouble("gastos_directos", 0.0))

                        val existingProd = if (prodId > 0) existingProducts[prodId] else null
                        val productToSave = Product(
                            id = if (existingProd != null) existingProd.id else (if (prodId > 0) prodId else 0L),
                            code = code,
                            name = name,
                            category = category,
                            price = salePrice,
                            cost = acquisitionCost,
                            stock = existingProd?.stock ?: currentStock.toInt(),
                            minStock = existingProd?.minStock ?: 3,
                            destination = "BARRA",
                            isAvailable = isActive,
                            description = existingProd?.description ?: "",
                            unitOfMeasure = unitOfMeasure
                        )
                        val savedProdId = db.productDao().insertProduct(productToSave)
                        val targetProdId = if (savedProdId > 0) savedProdId else productToSave.id

                        val existingMerc = existingMercs[targetProdId]
                        val mercToSave = Mercaderia(
                            id = existingMerc?.id ?: 0L,
                            productId = targetProdId,
                            acquisitionCost = acquisitionCost,
                            unitOfMeasure = unitOfMeasure,
                            initialStock = if (existingMerc != null) existingMerc.initialStock else initialStock,
                            isActive = isActive,
                            directExpenses = directExpenses
                        )
                        db.mercaderiaDao().insert(mercToSave)
                    }
                }

                // 3. Inversiones Section
                val inversionesArr = dataContainer.optJSONArray("inversiones") ?: jsonObject.optJSONArray("inversiones")
                if (inversionesArr != null) {
                    val existingInvs = db.inversionDao().getAllSync().associateBy { it.id }
                    for (i in 0 until inversionesArr.length()) {
                        val invObj = inversionesArr.getJSONObject(i)
                        val id = invObj.optLong("id", 0L)
                        val name = invObj.optString("name", invObj.optString("nombre", "Inversión"))
                        val category = invObj.optString("category", invObj.optString("categoria", "Equipos"))
                        val amount = invObj.optDouble("amount", invObj.optDouble("monto", 0.0))
                        val usefulLife = invObj.optDouble("usefulLife", invObj.optDouble("vida_util", 12.0))
                        val usefulLifeUnit = invObj.optString("usefulLifeUnit", invObj.optString("unidad_vida_util", "MESES"))
                        val date = invObj.optLong("date", invObj.optLong("fecha", System.currentTimeMillis()))
                        val observation = invObj.optString("observation", invObj.optString("observacion", ""))
                        val scope = invObj.optString("scope", invObj.optString("ambito", "PRODUCCION"))
                        val currency = invObj.optString("currency", invObj.optString("moneda", "CUP"))
                        val originalAmount = invObj.optDouble("originalAmount", amount)
                        val exchangeRate = invObj.optDouble("exchangeRate", 1.0)
                        val convertedAmount = invObj.optDouble("convertedAmount", amount)

                        val existing = if (id > 0) existingInvs[id] else null
                        val invToSave = Inversion(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            category = category,
                            amount = amount,
                            date = date,
                            usefulLife = usefulLife,
                            usefulLifeUnit = usefulLifeUnit,
                            observation = observation,
                            scope = scope,
                            currency = currency,
                            originalAmount = originalAmount,
                            exchangeRate = exchangeRate,
                            convertedAmount = convertedAmount
                        )
                        db.inversionDao().insert(invToSave)
                    }
                }

                // 4. Gastos Corrientes Section
                val gastosArr = dataContainer.optJSONArray("gastos_corrientes") ?: dataContainer.optJSONArray("gastos_generales") ?: dataContainer.optJSONArray("gastos") ?: jsonObject.optJSONArray("gastos_corrientes")
                if (gastosArr != null) {
                    val existingGastos = db.gastoGeneralDao().getAllSync().associateBy { it.id }
                    for (i in 0 until gastosArr.length()) {
                        val gObj = gastosArr.getJSONObject(i)
                        val id = gObj.optLong("id", 0L)
                        val name = gObj.optString("nombre", gObj.optString("name", "Gasto General"))
                        val description = gObj.optString("description", gObj.optString("descripcion", ""))
                        val amount = gObj.optDouble("importe", gObj.optDouble("amount", 0.0))
                        val period = gObj.optString("periodo", gObj.optString("period", "MENSUAL"))
                        val periodDays = gObj.optInt("periodDays", gObj.optInt("dias_periodo", 30))
                        val category = gObj.optString("categoria", gObj.optString("category", "Otros"))
                        val isActive = gObj.optBoolean("isActive", gObj.optBoolean("activo", true))
                        val scope = gObj.optString("scope", gObj.optString("ambito", "PRODUCCION"))
                        val targetProductIds = if (gObj.has("targetProductIds")) gObj.optString("targetProductIds") else null
                        val startDate = if (gObj.has("startDate")) gObj.optLong("startDate") else null
                        val endDate = if (gObj.has("endDate")) gObj.optLong("endDate") else null

                        val existing = if (id > 0) existingGastos[id] else null
                        val gastoToSave = com.example.data.local.model.GastoGeneral(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            description = description,
                            amount = amount,
                            period = period,
                            periodDays = periodDays,
                            category = category,
                            isActive = isActive,
                            scope = scope,
                            targetProductIds = targetProductIds,
                            startDate = startDate,
                            endDate = endDate
                        )
                        db.gastoGeneralDao().insert(gastoToSave)
                    }
                }

                // 5. Update Metadata in ConfiguracionGeneral
                val currentConfig = db.configuracionGeneralDao().getConfigSync()
                val now = System.currentTimeMillis()
                val updatedConfig = if (currentConfig != null) {
                    currentConfig.copy(
                        lastQDuenoUpdateDate = now,
                        lastQDuenoUpdateStatus = STATUS_SUCCESS,
                        lastQDuenoUpdateVersion = version,
                        lastQDuenoUpdateFechaPublicacion = fechaPublicacion
                    )
                } else {
                    ConfiguracionGeneral(
                        id = 1,
                        lastQDuenoUpdateDate = now,
                        lastQDuenoUpdateStatus = STATUS_SUCCESS,
                        lastQDuenoUpdateVersion = version,
                        lastQDuenoUpdateFechaPublicacion = fechaPublicacion
                    )
                }
                if (currentConfig != null) {
                    db.configuracionGeneralDao().updateConfig(updatedConfig)
                } else {
                    db.configuracionGeneralDao().insertConfig(updatedConfig)
                }

                // Register Bitacora
                db.bitacoraDao().insertEntry(
                    BitacoraEntry(
                        title = "Actualización de Q_dueño.json",
                        content = "Se actualizó exitosamente Q_dueño.json (Versión: $version, Fecha Pub: $fechaPublicacion) por $username.",
                        category = "DUEÑO",
                        authorUsername = username,
                        priority = "NORMAL"
                    )
                )
            }

            QDuenoUpdateResult.Success(version = version, fechaPublicacion = fechaPublicacion)
        } catch (e: Exception) {
            e.printStackTrace()
            QDuenoUpdateResult.Error(e.localizedMessage ?: "Error al procesar Q_dueño.json")
        }
    }

    private fun isVersionOrDateOlderOrEqual(
        newVersion: String,
        newFechaPub: String,
        lastVersion: String,
        lastFechaPub: String
    ): Boolean {
        if (lastVersion.isBlank() && lastFechaPub.isBlank()) return false

        if (newFechaPub.isNotBlank() && lastFechaPub.isNotBlank()) {
            val dateCmp = newFechaPub.compareTo(lastFechaPub)
            if (dateCmp < 0) return true
            if (dateCmp > 0) return false
        }

        val newVerLong = newVersion.toLongOrNull()
        val lastVerLong = lastVersion.toLongOrNull()
        if (newVerLong != null && lastVerLong != null) {
            return newVerLong <= lastVerLong
        }

        if (newVersion.isNotBlank() && lastVersion.isNotBlank()) {
            return newVersion.compareTo(lastVersion) <= 0
        }

        return false
    }

    suspend fun importQAdmin(
        db: AppDatabase,
        jsonBody: String,
        username: String = "Admin",
        context: android.content.Context? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = cleanJsonString(jsonBody)
            if (jsonString.isBlank() || (!jsonString.startsWith("{") && !jsonString.startsWith("["))) {
                return@withContext Pair(false, "El archivo proporcionado no es un JSON válido o está vacío.")
            }

            val jsonObject = JSONObject(jsonString)

            val idArchivo = jsonObject.optString("archivo", jsonObject.optString("identificador_archivo", ""))
            val idTag = jsonObject.optString("identificador_archivo", "")
            if (!idArchivo.contains("admin", ignoreCase = true) &&
                !idTag.contains("Q_ADMIN", ignoreCase = true)
            ) {
                return@withContext Pair(false, "El archivo no corresponde a un formato válido de Q_admin.json.")
            }

            // Business Code Validation (Rules 3 & 11)
            val remoteBizCodeRaw = jsonObject.optString("codigoNegocio",
                jsonObject.optJSONObject("negocio")?.optString("codigo") ?:
                jsonObject.optJSONObject("negocio")?.optString("codigoNegocio") ?: ""
            )
            val remoteBizCode = if (remoteBizCodeRaw.isNotBlank()) com.example.licensing.BusinessCodeHelper.formatCode(remoteBizCodeRaw) else ""

            val configNegocio = db.configuracionNegocioDao().getConfigSync()
            val configGeneral = db.configuracionGeneralDao().getConfigSync()
            val localBizCode = com.example.licensing.BusinessCodeHelper.resolveBusinessCode(context, configNegocio, configGeneral)

            if (remoteBizCode.isNotBlank() && remoteBizCode != localBizCode) {
                return@withContext Pair(false, "El archivo pertenece a un negocio diferente (Negocio $remoteBizCode) y no corresponde al negocio actual (Negocio $localBizCode).")
            }

            // Timestamp Validation (Rules 4, 5, 8, 9)
            val remoteTimestamp = extractTimestampMillis(jsonObject)
            val prefs = context?.getSharedPreferences("elqadre_json_sync", android.content.Context.MODE_PRIVATE)
            val lastProcessedTimestamp = prefs?.getLong("last_q_admin_timestamp_$localBizCode", 0L) ?: 0L

            if (remoteTimestamp > 0L && lastProcessedTimestamp > 0L && remoteTimestamp <= lastProcessedTimestamp) {
                val remoteStr = formatTimestampToDisplay(remoteTimestamp)
                val localStr = formatTimestampToDisplay(lastProcessedTimestamp)
                return@withContext Pair(false, "No existen datos nuevos. La versión del archivo remoto ($remoteStr) es igual o anterior a la última versión procesada localmente ($localStr).")
            }

            val dataContainer = jsonObject.optJSONObject("datos") ?: jsonObject

            var updatedCount = 0

            db.withTransaction {
                // 1. Producción / Catálogo (Productos Elaborados & Productos)
                val produccionObj = dataContainer.optJSONObject("produccion")
                val productosArr = produccionObj?.optJSONArray("productos_elaborados")
                    ?: produccionObj?.optJSONArray("productos")
                    ?: dataContainer.optJSONArray("productos")
                    ?: dataContainer.optJSONArray("products")
                    ?: jsonObject.optJSONArray("products")

                if (productosArr != null) {
                    val existingProducts = db.productDao().getAllProductsSync().associateBy { it.id }
                    val existingElaborados = db.productoElaboradoDao().getAllSync().associateBy { it.productId }

                    for (i in 0 until productosArr.length()) {
                        val prodObj = productosArr.getJSONObject(i)
                        val id = prodObj.optLong("id", 0L)
                        val name = prodObj.optString("name", prodObj.optString("nombre", "Producto"))
                        val code = prodObj.optString("code", prodObj.optString("codigo", ""))
                        val category = prodObj.optString("category", prodObj.optString("categoria", "Cocina"))
                        val salePrice = prodObj.optDouble("salePrice", prodObj.optDouble("precio_venta", prodObj.optDouble("price", 0.0)))
                        val unitOfMeasure = prodObj.optString("unitOfMeasure", prodObj.optString("unidad_medida", "unidad"))
                        val destination = prodObj.optString("destination", prodObj.optString("destino", "COCINA"))
                        val isAvailable = prodObj.optBoolean("isAvailable", prodObj.optBoolean("disponible", true))
                        val ppd = prodObj.optDouble("ppd", 10.0)

                        val existingProd = if (id > 0) existingProducts[id] else null
                        val currentPrice = if (salePrice > 0.0) salePrice else (existingProd?.price ?: 0.0)

                        val productToSave = Product(
                            id = if (existingProd != null) existingProd.id else (if (id > 0) id else 0L),
                            code = code.ifBlank { existingProd?.code ?: "" },
                            name = name.ifBlank { existingProd?.name ?: "Producto" },
                            category = category.ifBlank { existingProd?.category ?: "Cocina" },
                            price = currentPrice,
                            cost = prodObj.optDouble("costoTotalUnitario", prodObj.optDouble("costo_unitario", prodObj.optDouble("cost", existingProd?.cost ?: 0.0))),
                            stock = existingProd?.stock ?: 0,
                            minStock = existingProd?.minStock ?: 3,
                            destination = destination,
                            isAvailable = isAvailable,
                            description = existingProd?.description ?: "",
                            unitOfMeasure = unitOfMeasure,
                            imagePath = existingProd?.imagePath,
                            admitsAgregados = existingProd?.admitsAgregados ?: false,
                            agregadosList = existingProd?.agregadosList ?: "[]"
                        )
                        val savedProdId = db.productDao().insertProduct(productToSave)
                        val targetProdId = if (savedProdId > 0) savedProdId else productToSave.id

                        val existingElab = existingElaborados[targetProdId]
                        val precioDef = prodObj.optDouble("precioDefinitivo", prodObj.optDouble("precio_definitivo", currentPrice))
                        val hasPrecioDef = prodObj.optBoolean("hasPrecioDefinitivo", prodObj.optBoolean("tiene_precio_definitivo", precioDef > 0.0))

                        val elabToSave = ProductoElaborado(
                            id = existingElab?.id ?: 0L,
                            productId = targetProdId,
                            isActive = isAvailable,
                            recipeName = "Receta $name",
                            productionUnit = unitOfMeasure,
                            baseYield = prodObj.optDouble("expectedBatchYield", prodObj.optDouble("rendimiento_esperado", existingElab?.baseYield ?: 1.0)),
                            targetMarginPct = prodObj.optDouble("targetProfitMarginPercentage", prodObj.optDouble("margen_objetivo_porcentaje", existingElab?.targetMarginPct ?: 30.0)),
                            ppd = ppd,
                            precioDefinitivo = precioDef,
                            hasPrecioDefinitivo = hasPrecioDef
                        )
                        val elabId = db.productoElaboradoDao().insert(elabToSave)
                        val targetElabId = if (elabId > 0) elabId else elabToSave.id

                        val recetaArr = prodObj.optJSONArray("receta")
                        if (recetaArr != null) {
                            db.recetaIngredienteDao().deleteIngredientsForProduct(targetElabId)
                            for (j in 0 until recetaArr.length()) {
                                val rObj = recetaArr.getJSONObject(j)
                                val mpId = rObj.optLong("materiaPrimaId", rObj.optLong("materia_prima_id", 0L))
                                val qty = rObj.optDouble("quantityNeeded", rObj.optDouble("cantidad_necesaria", rObj.optDouble("quantity", 0.0)))
                                val unit = rObj.optString("unit", rObj.optString("unidad", "g"))
                                if (mpId > 0) {
                                    db.recetaIngredienteDao().insert(
                                        RecetaIngrediente(
                                            productoElaboradoId = targetElabId,
                                            materiaPrimaId = mpId,
                                            quantity = qty,
                                            unit = unit
                                        )
                                    )
                                }
                            }
                        }
                        updatedCount++
                    }
                }

                // 2. Materias Primas / Insumos
                val insumosArr = produccionObj?.optJSONArray("materias_primas")
                    ?: produccionObj?.optJSONArray("insumos")
                    ?: dataContainer.optJSONArray("materias_primas")
                    ?: dataContainer.optJSONArray("insumos")

                if (insumosArr != null) {
                    val existingMps = db.materiaPrimaDao().getAllSync().associateBy { it.id }
                    for (i in 0 until insumosArr.length()) {
                        val mpObj = insumosArr.getJSONObject(i)
                        val id = mpObj.optLong("id", 0L)
                        val name = mpObj.optString("name", mpObj.optString("nombre", "Insumo"))
                        val unit = mpObj.optString("unit", mpObj.optString("unidad", "g"))
                        val unitCost = mpObj.optDouble("unitCost", mpObj.optDouble("costo_unitario", 0.0))
                        val initialStock = mpObj.optDouble("initialStock", mpObj.optDouble("stock_inicial", mpObj.optDouble("stock", 0.0)))
                        val stock = mpObj.optDouble("stock", mpObj.optDouble("stock_actual", initialStock))
                        val purchasePrice = mpObj.optDouble("purchasePrice", mpObj.optDouble("precio_compra", 0.0))
                        val purchaseUnit = mpObj.optString("purchaseUnit", mpObj.optString("unidad_compra", unit))
                        val purchaseQuantity = mpObj.optDouble("purchaseQuantity", mpObj.optDouble("cantidad_compra", 1.0))
                        val isActive = mpObj.optBoolean("isActive", mpObj.optBoolean("activo", true))

                        val existing = if (id > 0) existingMps[id] else null
                        val mpToSave = MateriaPrima(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            unit = unit,
                            unitCost = unitCost,
                            isActive = isActive,
                            stock = existing?.stock ?: stock,
                            initialStock = if (existing != null) existing.initialStock else initialStock,
                            purchasePrice = purchasePrice,
                            purchaseUnit = purchaseUnit,
                            purchaseQuantity = purchaseQuantity
                        )
                        db.materiaPrimaDao().insert(mpToSave)
                        updatedCount++
                    }
                }

                // 3. Mercaderías
                val mercaderiasArr = dataContainer.optJSONArray("mercaderias")
                    ?: dataContainer.optJSONArray("mercancias")
                    ?: jsonObject.optJSONArray("mercaderias")

                if (mercaderiasArr != null) {
                    val existingProducts = db.productDao().getAllProductsSync().associateBy { it.id }
                    val existingMercs = db.mercaderiaDao().getAllSync().associateBy { it.productId }

                    for (i in 0 until mercaderiasArr.length()) {
                        val mObj = mercaderiasArr.getJSONObject(i)
                        val id = mObj.optLong("id", 0L)
                        val prodId = mObj.optLong("productId", mObj.optLong("product_id", id))
                        val name = mObj.optString("name", mObj.optString("nombre", "Mercadería"))
                        val code = mObj.optString("code", mObj.optString("codigo", ""))
                        val category = mObj.optString("category", mObj.optString("categoria", "Bebidas"))
                        val acquisitionCost = mObj.optDouble("acquisitionCost", mObj.optDouble("costo_adquisicion", mObj.optDouble("purchasePrice", 0.0)))
                        val salePrice = mObj.optDouble("salePrice", mObj.optDouble("precio_venta", 0.0))
                        val unitOfMeasure = mObj.optString("unitOfMeasure", mObj.optString("unidad_medida", "unidad"))
                        val initialStock = mObj.optDouble("initialStock", mObj.optDouble("stock_inicial", 0.0))
                        val currentStock = mObj.optDouble("currentStock", mObj.optDouble("stock_actual", initialStock))
                        val isActive = mObj.optBoolean("isActive", mObj.optBoolean("activo", true))
                        val directExpenses = mObj.optDouble("gastosDirectos", mObj.optDouble("gastos_directos", mObj.optDouble("directExpenses", 0.0)))

                        val existingProd = if (prodId > 0) existingProducts[prodId] else null
                        val productToSave = Product(
                            id = if (existingProd != null) existingProd.id else (if (prodId > 0) prodId else 0L),
                            code = code.ifBlank { existingProd?.code ?: "" },
                            name = name.ifBlank { existingProd?.name ?: "Mercadería" },
                            category = category.ifBlank { existingProd?.category ?: "Bebidas" },
                            price = if (salePrice > 0.0) salePrice else (existingProd?.price ?: 0.0),
                            cost = acquisitionCost,
                            stock = existingProd?.stock ?: currentStock.toInt(),
                            minStock = existingProd?.minStock ?: 3,
                            destination = "BARRA",
                            isAvailable = isActive,
                            description = existingProd?.description ?: "",
                            unitOfMeasure = unitOfMeasure
                        )
                        val savedProdId = db.productDao().insertProduct(productToSave)
                        val targetProdId = if (savedProdId > 0) savedProdId else productToSave.id

                        val existingMerc = existingMercs[targetProdId]
                        val mercToSave = Mercaderia(
                            id = existingMerc?.id ?: 0L,
                            productId = targetProdId,
                            acquisitionCost = acquisitionCost,
                            unitOfMeasure = unitOfMeasure,
                            initialStock = if (existingMerc != null) existingMerc.initialStock else initialStock,
                            isActive = isActive,
                            directExpenses = directExpenses
                        )
                        db.mercaderiaDao().insert(mercToSave)
                        updatedCount++
                    }
                }

                // 4. Inversiones
                val inversionesArr = dataContainer.optJSONArray("inversiones") ?: jsonObject.optJSONArray("inversiones")
                if (inversionesArr != null) {
                    val existingInvs = db.inversionDao().getAllSync().associateBy { it.id }
                    for (i in 0 until inversionesArr.length()) {
                        val invObj = inversionesArr.getJSONObject(i)
                        val id = invObj.optLong("id", 0L)
                        val name = invObj.optString("name", invObj.optString("nombre", "Inversión"))
                        val category = invObj.optString("category", invObj.optString("categoria", "Equipos"))
                        val amount = invObj.optDouble("amount", invObj.optDouble("monto", 0.0))
                        val usefulLife = invObj.optDouble("usefulLife", invObj.optDouble("vida_util", 12.0))
                        val usefulLifeUnit = invObj.optString("usefulLifeUnit", invObj.optString("unidad_vida_util", "MESES"))
                        val date = invObj.optLong("date", invObj.optLong("fecha", System.currentTimeMillis()))
                        val observation = invObj.optString("observation", invObj.optString("observacion", ""))
                        val scope = invObj.optString("scope", invObj.optString("ambito", "PRODUCCION"))
                        val currency = invObj.optString("currency", invObj.optString("moneda", "CUP"))
                        val originalAmount = invObj.optDouble("originalAmount", amount)
                        val exchangeRate = invObj.optDouble("exchangeRate", 1.0)
                        val convertedAmount = invObj.optDouble("convertedAmount", amount)

                        val existing = if (id > 0) existingInvs[id] else null
                        val invToSave = Inversion(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            category = category,
                            amount = amount,
                            date = date,
                            usefulLife = usefulLife,
                            usefulLifeUnit = usefulLifeUnit,
                            observation = observation,
                            scope = scope,
                            currency = currency,
                            originalAmount = originalAmount,
                            exchangeRate = exchangeRate,
                            convertedAmount = convertedAmount
                        )
                        db.inversionDao().insert(invToSave)
                        updatedCount++
                    }
                }

                // 5. Gastos Corrientes
                val gastosArr = dataContainer.optJSONArray("gastos_corrientes")
                    ?: dataContainer.optJSONArray("gastos_generales")
                    ?: dataContainer.optJSONArray("gastos")
                    ?: jsonObject.optJSONArray("gastos_corrientes")
                if (gastosArr != null) {
                    val existingGastos = db.gastoGeneralDao().getAllSync().associateBy { it.id }
                    for (i in 0 until gastosArr.length()) {
                        val gObj = gastosArr.getJSONObject(i)
                        val id = gObj.optLong("id", 0L)
                        val name = gObj.optString("nombre", gObj.optString("name", "Gasto General"))
                        val description = gObj.optString("description", gObj.optString("descripcion", ""))
                        val amount = gObj.optDouble("importe", gObj.optDouble("amount", 0.0))
                        val period = gObj.optString("periodo", gObj.optString("period", "MENSUAL"))
                        val periodDays = gObj.optInt("periodDays", gObj.optInt("dias_periodo", 30))
                        val category = gObj.optString("categoria", gObj.optString("category", "Otros"))
                        val isActive = gObj.optBoolean("isActive", gObj.optBoolean("activo", true))
                        val scope = gObj.optString("scope", gObj.optString("ambito", "PRODUCCION"))
                        val targetProductIds = if (gObj.has("targetProductIds")) gObj.optString("targetProductIds") else null
                        val startDate = if (gObj.has("startDate")) gObj.optLong("startDate") else null
                        val endDate = if (gObj.has("endDate")) gObj.optLong("endDate") else null

                        val existing = if (id > 0) existingGastos[id] else null
                        val gastoToSave = com.example.data.local.model.GastoGeneral(
                            id = if (existing != null) existing.id else (if (id > 0) id else 0L),
                            name = name,
                            description = description,
                            amount = amount,
                            period = period,
                            periodDays = periodDays,
                            category = category,
                            isActive = isActive,
                            scope = scope,
                            targetProductIds = targetProductIds,
                            startDate = startDate,
                            endDate = endDate
                        )
                        db.gastoGeneralDao().insert(gastoToSave)
                        updatedCount++
                    }
                }

                // 6. Currency Exchange Rates
                val currentConfig = db.configuracionGeneralDao().getConfigSync()
                if (currentConfig != null && (jsonObject.has("tasaUsd") || jsonObject.has("tasaEur"))) {
                    val tasaUsd = if (jsonObject.has("tasaUsd")) jsonObject.getDouble("tasaUsd") else currentConfig.tasaUsd
                    val tasaEur = if (jsonObject.has("tasaEur")) jsonObject.getDouble("tasaEur") else currentConfig.tasaEur
                    db.configuracionGeneralDao().updateConfig(
                        currentConfig.copy(
                            tasaUsd = tasaUsd,
                            tasaEur = tasaEur
                        )
                    )
                }

                // Save metadata in SharedPreferences
                val tsToSave = if (remoteTimestamp > 0L) remoteTimestamp else System.currentTimeMillis()
                prefs?.edit()?.putLong("last_q_admin_timestamp_$localBizCode", tsToSave)?.apply()
                prefs?.edit()?.putString("last_q_admin_file_$localBizCode", "Q_${localBizCode}admin.json")?.apply()

                // Bitacora Entry
                db.bitacoraDao().insertEntry(
                    BitacoraEntry(
                        title = "Importación de Q_admin.json",
                        content = "Precios de venta, precios definitivos y datos actualizados desde archivo de Dueño por $username ($updatedCount elementos procesados). Versión: ${formatTimestampToDisplay(tsToSave)}.",
                        category = "ADMIN",
                        authorUsername = username,
                        priority = "ALTA"
                    )
                )
            }

            Pair(true, "Datos y precios actualizados exitosamente ($updatedCount registros procesados). Versión: ${formatTimestampToDisplay(if (remoteTimestamp > 0L) remoteTimestamp else System.currentTimeMillis())}.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Error al procesar Q_admin.json: ${e.localizedMessage}")
        }
    }
}
