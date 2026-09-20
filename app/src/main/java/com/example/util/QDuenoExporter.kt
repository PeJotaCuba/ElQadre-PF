package com.example.util

import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QDuenoExporter {

    suspend fun generateQDuenoJsonString(
        db: AppDatabase,
        customFechaPublicacion: String? = null,
        customBusinessCode: String? = null
    ): String = withContext(Dispatchers.IO) {
        val jsonObject = JSONObject()

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

        val fechaPub = customFechaPublicacion?.ifBlank { null } ?: dateFormat.format(Date(now))
        val fechaHoraGen = dateTimeFormat.format(Date(now))

        val configNegocio = db.configuracionNegocioDao().getConfigSync()
        val formattedCode = if (!customBusinessCode.isNullOrBlank()) {
            com.example.licensing.BusinessCodeHelper.formatCode(customBusinessCode)
        } else {
            val raw = configNegocio?.codigoNegocio ?: "001"
            com.example.licensing.BusinessCodeHelper.formatCode(raw)
        }
        val nombreNegocio = configNegocio?.nombreNegocio ?: "El Qadre POS"
        val codigoNegocio = "NEG-$formattedCode"

        // 1. Identification & Version Metadata (Section 4)
        val fileName = "Q_${formattedCode}dueño.json"
        jsonObject.put("archivo", fileName)
        jsonObject.put("identificador_archivo", "Q_DUENO")
        jsonObject.put("codigoNegocio", formattedCode)
        jsonObject.put("urlDueño", com.example.licensing.SuperAdminBusinessManager.buildUrlDueño(formattedCode))
        jsonObject.put("timestamp_ms", now)
        jsonObject.put("version", now.toString())
        jsonObject.put("fecha_publicacion", fechaPub)
        jsonObject.put("fecha_generacion", fechaHoraGen)
        jsonObject.put("fecha_hora_generacion", fechaHoraGen)

        val negocioObj = JSONObject()
        negocioObj.put("nombre", nombreNegocio)
        negocioObj.put("codigo", formattedCode)
        negocioObj.put("codigoNegocio", codigoNegocio)
        jsonObject.put("negocio", negocioObj)
        jsonObject.put("identificacion_negocio", negocioObj)

        val datosObj = JSONObject()

        val allProducts = db.productDao().getAllProductsSync().associateBy { it.id }

        // 2. Producción Section
        val produccionObj = JSONObject()

        // Materias Primas
        val materiasPrimas = db.materiaPrimaDao().getAllSync()
        val mpArray = JSONArray()
        for (mp in materiasPrimas) {
            val mpObj = JSONObject()
            mpObj.put("id", mp.id)
            mpObj.put("name", mp.name)
            mpObj.put("unit", mp.unit)
            mpObj.put("unitCost", mp.unitCost)
            mpObj.put("stock", mp.stock)
            mpObj.put("initialStock", mp.initialStock)
            mpObj.put("purchasePrice", mp.purchasePrice)
            mpObj.put("purchaseUnit", mp.purchaseUnit)
            mpObj.put("purchaseQuantity", mp.purchaseQuantity)
            mpObj.put("isActive", mp.isActive)
            mpArray.put(mpObj)
        }
        produccionObj.put("materias_primas", mpArray)

        // Productos Elaborados & Recetas
        val productosElaborados = db.productoElaboradoDao().getAllSync()
        val allIngredients = db.recetaIngredienteDao().getAllIngredientsSync().groupBy { it.productoElaboradoId }
        val prodElabArray = JSONArray()

        for (pe in productosElaborados) {
            val peObj = JSONObject()
            peObj.put("id", pe.id)
            peObj.put("productId", pe.productId)
            val associatedProduct = allProducts[pe.productId]
            peObj.put("name", associatedProduct?.name ?: "")
            peObj.put("code", associatedProduct?.code ?: "")
            peObj.put("category", associatedProduct?.category ?: "")
            peObj.put("unitOfMeasure", pe.productionUnit)
            peObj.put("recipeName", pe.recipeName)
            peObj.put("baseYield", pe.baseYield)
            peObj.put("baseQuantity", pe.baseQuantity)
            peObj.put("ppd", pe.effectivePpd)
            peObj.put("precioDefinitivo", pe.precioDefinitivo)
            peObj.put("hasPrecioDefinitivo", pe.hasPrecioDefinitivo)
            peObj.put("targetMarginPct", pe.targetMarginPct)
            peObj.put("salePrice", associatedProduct?.price ?: 0.0)
            peObj.put("isActive", pe.isActive)

            // Receta
            val ingredientes = allIngredients[pe.id] ?: emptyList()
            val recetaArray = JSONArray()
            for (ing in ingredientes) {
                val ingObj = JSONObject()
                ingObj.put("id", ing.id)
                ingObj.put("materiaPrimaId", ing.materiaPrimaId)
                ingObj.put("quantity", ing.quantity)
                ingObj.put("unit", ing.unit)
                recetaArray.put(ingObj)
            }
            peObj.put("receta", recetaArray)

            prodElabArray.put(peObj)
        }
        produccionObj.put("productos_elaborados", prodElabArray)
        jsonObject.put("produccion", produccionObj)
        datosObj.put("produccion", produccionObj)

        // 3. Mercaderías Section
        val mercaderias = db.mercaderiaDao().getAllSync()
        val mercArray = JSONArray()
        for (m in mercaderias) {
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("productId", m.productId)
            val associatedProduct = allProducts[m.productId]
            mObj.put("name", associatedProduct?.name ?: "")
            mObj.put("code", associatedProduct?.code ?: "")
            mObj.put("category", associatedProduct?.category ?: "")
            mObj.put("unitOfMeasure", m.unitOfMeasure)
            mObj.put("acquisitionCost", m.acquisitionCost)
            mObj.put("purchasePrice", m.purchasePrice)
            mObj.put("purchaseMode", m.purchaseMode)
            mObj.put("unitsPerLot", m.unitsPerLot)
            mObj.put("salePrice", associatedProduct?.price ?: 0.0)
            mObj.put("initialStock", m.initialStock)
            mObj.put("currentStock", associatedProduct?.stock?.toDouble() ?: m.initialStock)
            mObj.put("directExpenses", m.directExpenses)
            mObj.put("directExpensesDetails", m.directExpensesDetails)
            mObj.put("isActive", m.isActive)
            mercArray.put(mObj)
        }
        jsonObject.put("mercaderias", mercArray)
        datosObj.put("mercaderias", mercArray)

        // 4. Inversiones Section
        val inversiones = db.inversionDao().getAllSync()
        val invArray = JSONArray()
        for (inv in inversiones) {
            val invObj = JSONObject()
            invObj.put("id", inv.id)
            invObj.put("name", inv.name)
            invObj.put("category", inv.category)
            invObj.put("amount", inv.amount)
            invObj.put("date", inv.date)
            invObj.put("usefulLife", inv.usefulLife)
            invObj.put("usefulLifeUnit", inv.usefulLifeUnit)
            invObj.put("observation", inv.observation)
            invObj.put("scope", inv.scope)
            invObj.put("currency", inv.currency)
            invObj.put("originalAmount", inv.originalAmount)
            invObj.put("exchangeRate", inv.exchangeRate)
            invObj.put("convertedAmount", inv.convertedAmount)
            invObj.put("dailyDepreciation", inv.dailyDepreciation())
            invObj.put("monthlyDepreciation", inv.monthlyDepreciation())
            invArray.put(invObj)
        }
        jsonObject.put("inversiones", invArray)
        datosObj.put("inversiones", invArray)

        // 5. Gastos Corrientes Section (Section 6)
        val gastosGenerales = db.gastoGeneralDao().getAllSync()
        val gastosArray = JSONArray()
        for (g in gastosGenerales) {
            val gObj = JSONObject()
            gObj.put("id", g.id)
            gObj.put("nombre", g.name)
            gObj.put("name", g.name)
            gObj.put("description", g.description)
            gObj.put("importe", g.amount)
            gObj.put("amount", g.amount)
            gObj.put("periodo", g.period)
            gObj.put("period", g.period)
            gObj.put("periodDays", g.periodDays)
            gObj.put("costo_diario_normalizado", g.dailyCost())
            gObj.put("dailyCost", g.dailyCost())
            gObj.put("categoria", g.category)
            gObj.put("category", g.category)
            gObj.put("isActive", g.isActive)
            gObj.put("createdAt", g.createdAt)
            gObj.put("scope", g.scope)
            if (g.targetProductIds != null) gObj.put("targetProductIds", g.targetProductIds)
            if (g.startDate != null) gObj.put("startDate", g.startDate)
            if (g.endDate != null) gObj.put("endDate", g.endDate)
            gastosArray.put(gObj)
        }
        jsonObject.put("gastos_corrientes", gastosArray)
        datosObj.put("gastos_corrientes", gastosArray)

        jsonObject.put("datos", datosObj)

        jsonObject.toString(4)
    }
}
