package com.example.util

import com.example.data.local.model.Product

/**
 * Utilidad para la clasificación y manejo de categorías en el módulo de Mercaderías.
 * Asegura la correcta diferenciación entre Bebidas y Confituras según las reglas de negocio.
 */
object MercaderiaCategoryHelper {
    const val CATEGORY_BEBIDAS = "Bebidas"
    const val CATEGORY_CONFITURAS = "Confituras"
    const val CATEGORY_SNACKS = "Snacks"
    const val CATEGORY_BARRA = "Barra"
    const val CATEGORY_CIGARROS = "Cigarros"
    const val CATEGORY_CAFETERIA = "Cafetería"

    val DEFAULT_CATEGORIES = listOf(
        CATEGORY_BEBIDAS,
        CATEGORY_CONFITURAS,
        CATEGORY_SNACKS,
        CATEGORY_BARRA,
        CATEGORY_CIGARROS,
        CATEGORY_CAFETERIA
    )

    /**
     * Determina si un producto corresponde a la clasificación de Bebidas.
     */
    fun isBebida(product: Product?): Boolean {
        if (product == null) return false
        val cat = product.category.trim()
        return cat.equals("Bebidas", ignoreCase = true) ||
               cat.equals("Bebida", ignoreCase = true) ||
               cat.contains("Bebida", ignoreCase = true) ||
               cat.contains("Refresco", ignoreCase = true) ||
               cat.contains("Cerveza", ignoreCase = true) ||
               cat.contains("Jugo", ignoreCase = true) ||
               cat.contains("Agua", ignoreCase = true) ||
               cat.contains("Licor", ignoreCase = true) ||
               cat.contains("Ron", ignoreCase = true)
    }

    /**
     * Determina si un producto corresponde a la clasificación de Confituras.
     * Las confituras NO tienen pagos de personal asociados.
     */
    fun isConfitura(product: Product?): Boolean {
        if (product == null) return false
        val cat = product.category.trim()
        return cat.equals("Confituras", ignoreCase = true) ||
               cat.equals("Confitura", ignoreCase = true) ||
               cat.contains("Confitur", ignoreCase = true)
    }
}
