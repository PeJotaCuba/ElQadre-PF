package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.model.Category
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.ConfiguracionNegocio
import com.example.data.local.model.Product
import com.example.licensing.BusinessCodeHelper
import com.example.licensing.CommercialLicenseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CatalogoDataManager {

    sealed class ImportResult {
        data class Success(
            val categoriesCount: Int,
            val productsCount: Int,
            val businessNumber: String,
            val businessName: String
        ) : ImportResult()

        data class Error(val message: String) : ImportResult()
    }

    /**
     * Identifica si un objeto JSON corresponde a un archivo de Catálogo de ElQadre.
     */
    fun isCatalogoJson(root: JSONObject): Boolean {
        val tipo = root.optString("tipo", "").trim().uppercase()
        val idArchivo = root.optString("identificador_archivo", "").trim().uppercase()
        if (tipo == "CATALOGO_ELQADRE" || idArchivo == "Q_CATALOGO") {
            return true
        }
        // Si no tiene el tipo explícito, revisar si contiene las colecciones de catálogo y no es de usuario
        val hasProducts = root.has("products")
        val hasCategories = root.has("categories")
        val isUserJson = root.has("persona") || root.has("usuario") || tipo == "USUARIO_ELQADRE"
        return (hasProducts || hasCategories) && !isUserJson
    }

    /**
     * Genera el archivo JSON consolidado del catálogo del negocio actual (Q_{numeroNegocio}catalogo.json).
     */
    fun generateCatalogoJson(
        context: Context,
        categories: List<Category>,
        products: List<Product>,
        configNegocio: ConfiguracionNegocio?,
        configGeneral: ConfiguracionGeneral?
    ): File {
        val licenseMgr = CommercialLicenseManager.getInstance(context)
        val licenseInfo = licenseMgr.licenseInfo.value
        val rawBizCode = BusinessCodeHelper.resolveBusinessCode(
            context = context,
            configNegocio = configNegocio,
            configGeneral = configGeneral,
            licenseInfo = licenseInfo
        )
        val businessNumber = BusinessCodeHelper.formatCode(rawBizCode)
        val businessCode = configNegocio?.codigoNegocio?.ifBlank { "NEG-$businessNumber" } ?: "NEG-$businessNumber"
        val businessName = configNegocio?.nombreNegocio?.ifBlank { "ElQadre" } ?: "ElQadre"

        val root = JSONObject().apply {
            put("tipo", "CATALOGO_ELQADRE")
            put("version", System.currentTimeMillis().toString())
            put("numeroNegocio", businessNumber)
            put("codigoNegocio", businessCode)
            put("nombreNegocio", businessName)

            put("negocio", JSONObject().apply {
                put("codigo", businessNumber)
                put("nombre", businessName)
            })

            put("tasaUsd", configGeneral?.tasaUsd ?: 0.0)
            put("tasaEur", configGeneral?.tasaEur ?: 0.0)

            val categoriesArray = JSONArray()
            for (cat in categories) {
                val catObj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("description", cat.description)
                    put("isActive", cat.isActive)
                }
                categoriesArray.put(catObj)
            }
            put("categories", categoriesArray)

            val productsArray = JSONArray()
            for (p in products) {
                val prodObj = JSONObject().apply {
                    put("id", p.id)
                    put("code", p.code)
                    put("name", p.name)
                    put("category", p.category)
                    put("price", p.price)
                    put("cost", p.cost)
                    put("stock", p.stock)
                    put("initialStock", p.stock)
                    put("cantidadInicial", p.stock)
                    put("existenciaInicial", p.stock)
                    put("stock_inicial", p.stock)
                    put("minStock", p.minStock)
                    put("destination", p.destination)
                    put("isAvailable", p.isAvailable)
                    put("description", p.description)
                    put("unitOfMeasure", p.unitOfMeasure)
                    put("admitsAgregados", p.admitsAgregados)
                    try {
                        put("agregadosList", JSONArray(p.agregadosList))
                    } catch (e: Exception) {
                        put("agregadosList", JSONArray())
                    }
                    if (p.imagePath != null) {
                        put("imagePath", p.imagePath)
                    }
                }
                productsArray.put(prodObj)
            }
            put("products", productsArray)

            put("timestamp", System.currentTimeMillis())
        }

        val jsonString = root.toString(4)
        val fileName = "Q_${businessNumber}catalogo.json"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        var targetFile = File(downloadsDir, fileName)

        try {
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            FileOutputStream(targetFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            targetFile = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
            FileOutputStream(targetFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
        }

        return targetFile
    }

    /**
     * Utiliza el mecanismo nativo de compartir de Android para enviar el archivo de catálogo.
     */
    fun shareCatalogoViaWhatsApp(
        context: Context,
        jsonFile: File,
        businessNumber: String,
        businessName: String
    ) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jsonFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Catálogo ElQadre - $businessName ($businessNumber)")
            putExtra(
                Intent.EXTRA_TEXT,
                "Hola, adjunto el catálogo oficial de $businessName ($businessNumber).\n" +
                "Guarda el archivo '${jsonFile.name}' y utiliza la opción 'CARGAR DATOS' en tu aplicación ElQadre para sincronizar los productos."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Compartir catálogo ($businessName)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Importa y valida localmente el archivo JSON de catálogo recibido en el dispositivo.
     */
    suspend fun importCatalogoJson(
        context: Context,
        uri: Uri,
        db: AppDatabase
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("No se pudo abrir el archivo seleccionado.")

            val jsonString = inputStream.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            if (jsonString.isBlank()) {
                return@withContext ImportResult.Error("El archivo de catálogo seleccionado está vacío.")
            }

            val root = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                return@withContext ImportResult.Error("El archivo no contiene un formato JSON válido.")
            }

            // 1. Validar que es catálogo
            if (!isCatalogoJson(root)) {
                return@withContext ImportResult.Error("El archivo no corresponde a un catálogo válido de ElQadre.")
            }

            // 2. Extraer y validar número de negocio
            val fileBizNumber = root.optString("numeroNegocio").trim().ifBlank {
                root.optJSONObject("negocio")?.optString("codigo")?.trim() ?: ""
            }.ifBlank {
                root.optString("codigoNegocio").trim()
            }

            val configNegocio = db.configuracionNegocioDao().getConfigSync()
            val configGeneral = db.configuracionGeneralDao().getConfigSync()
            val licenseMgr = CommercialLicenseManager.getInstance(context)
            val licenseInfo = licenseMgr.licenseInfo.value
            val deviceRawBiz = BusinessCodeHelper.resolveBusinessCode(
                context = context,
                configNegocio = configNegocio,
                configGeneral = configGeneral,
                licenseInfo = licenseInfo
            )
            val deviceBizNumber = BusinessCodeHelper.formatCode(deviceRawBiz)

            // Si el dispositivo receptor tiene un negocio asignado, verificar correspondencia
            if (deviceBizNumber.isNotBlank() && deviceBizNumber != "000") {
                val normalizedFileBiz = BusinessCodeHelper.formatCode(fileBizNumber)
                if (normalizedFileBiz.isNotBlank() && normalizedFileBiz != deviceBizNumber) {
                    return@withContext ImportResult.Error(
                        "El catálogo corresponde al negocio $normalizedFileBiz, pero este dispositivo está autorizado para el negocio $deviceBizNumber. Importación rechazada."
                    )
                }
            }

            val businessName = root.optString("nombreNegocio").ifBlank {
                root.optJSONObject("negocio")?.optString("nombre") ?: "ElQadre"
            }

            // 3. Parsear categorías
            val categoriesArray = root.optJSONArray("categories")
            val newCategories = mutableListOf<Category>()
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val catObj = categoriesArray.optJSONObject(i) ?: continue
                    newCategories.add(
                        Category(
                            id = catObj.optLong("id", 0),
                            name = catObj.optString("name", "Categoría"),
                            description = catObj.optString("description", ""),
                            isActive = catObj.optBoolean("isActive", true)
                        )
                    )
                }
            }

            // 4. Parsear productos
            val productsArray = root.optJSONArray("products")
            val newProducts = mutableListOf<Product>()
            if (productsArray != null) {
                for (i in 0 until productsArray.length()) {
                    val prodObj = productsArray.optJSONObject(i) ?: continue
                    val initialStockVal = when {
                        prodObj.has("initialStock") -> prodObj.optInt("initialStock", prodObj.optInt("stock", 0))
                        prodObj.has("cantidadInicial") -> prodObj.optInt("cantidadInicial", prodObj.optInt("stock", 0))
                        prodObj.has("existenciaInicial") -> prodObj.optInt("existenciaInicial", prodObj.optInt("stock", 0))
                        prodObj.has("stock_inicial") -> prodObj.optInt("stock_inicial", prodObj.optInt("stock", 0))
                        prodObj.has("stock") -> prodObj.optInt("stock", 0)
                        else -> 0
                    }

                    val agregadosListStr = if (prodObj.has("agregadosList")) {
                        val arr = prodObj.optJSONArray("agregadosList")
                        arr?.toString() ?: "[]"
                    } else {
                        "[]"
                    }

                    newProducts.add(
                        Product(
                            id = prodObj.optLong("id", 0),
                            code = prodObj.optString("code", "P${i + 1}"),
                            name = prodObj.optString("name", "Producto"),
                            category = prodObj.optString("category", "General"),
                            price = prodObj.optDouble("price", 0.0),
                            cost = prodObj.optDouble("cost", 0.0),
                            stock = initialStockVal,
                            minStock = prodObj.optInt("minStock", 3),
                            destination = prodObj.optString("destination", "COCINA"),
                            isAvailable = prodObj.optBoolean("isAvailable", true),
                            description = prodObj.optString("description", ""),
                            unitOfMeasure = prodObj.optString("unitOfMeasure", "Unidad"),
                            imagePath = if (prodObj.has("imagePath") && !prodObj.isNull("imagePath")) prodObj.optString("imagePath") else null,
                            admitsAgregados = prodObj.optBoolean("admitsAgregados", false),
                            agregadosList = agregadosListStr
                        )
                    )
                }
            }

            if (newCategories.isEmpty() && newProducts.isEmpty()) {
                return@withContext ImportResult.Error("El catálogo importado no contiene categorías ni productos.")
            }

            val remoteVersion = root.optString("version", System.currentTimeMillis().toString())

            // 5. Aplicar de forma atómica en la base de datos sin afectar usuarios ni otros módulos
            db.withTransaction {
                // Eliminar catálogo previo para evitar duplicados
                db.categoryDao().deleteAllCategories()
                db.productDao().deleteAllProducts()

                // Insertar nuevas categorías y productos
                newCategories.forEach { db.categoryDao().insertCategory(it) }
                newProducts.forEach { db.productDao().insertProduct(it) }

                // Actualizar estado en ConfiguracionGeneral
                if (configGeneral != null) {
                    val remoteTasaUsd = if (root.has("tasaUsd")) root.optDouble("tasaUsd", configGeneral.tasaUsd) else configGeneral.tasaUsd
                    val remoteTasaEur = if (root.has("tasaEur")) root.optDouble("tasaEur", configGeneral.tasaEur) else configGeneral.tasaEur

                    db.configuracionGeneralDao().updateConfig(
                        configGeneral.copy(
                            lastCatalogoUpdateDate = System.currentTimeMillis(),
                            lastCatalogoUpdateStatus = "SUCCESS",
                            lastCatalogoUpdateVersion = remoteVersion,
                            tasaUsd = remoteTasaUsd,
                            tasaEur = remoteTasaEur
                        )
                    )
                }
            }

            ImportResult.Success(
                categoriesCount = newCategories.size,
                productsCount = newProducts.size,
                businessNumber = fileBizNumber.ifBlank { deviceBizNumber },
                businessName = businessName
            )
        } catch (e: Exception) {
            ImportResult.Error("Error al importar catálogo: ${e.localizedMessage ?: e.message}")
        }
    }
}
