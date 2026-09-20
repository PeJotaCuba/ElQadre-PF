package com.example.util

import com.example.data.local.model.Product

object ProductCodeHelper {

    /**
     * Determines whether destination/category/code corresponds to Cocina (Producción) vs Barra (Mercadería).
     * PF-C-XXXX -> Cocina
     * PF-B-XXXX -> Barra
     */
    fun isCocina(destination: String?, category: String? = null, code: String? = null): Boolean {
        if (!code.isNullOrBlank()) {
            val c = code.trim().uppercase()
            if (c.startsWith("PF-C-")) return true
            if (c.startsWith("PF-B-")) return false
        }
        val dest = destination?.trim()?.uppercase() ?: ""
        val cat = category?.trim()?.uppercase() ?: ""
        return dest == "COCINA" ||
                dest.contains("COCINA") ||
                dest.contains("PRODUCCION") ||
                dest.contains("PRODUCCIÓN") ||
                cat.contains("COCINA") ||
                cat.contains("POSTRES") ||
                cat.contains("ELABORADO")
    }

    /**
     * Returns the prefix for the given destination / category.
     * Producción / Cocina -> "PF-C-"
     * Mercadería / Barra -> "PF-B-"
     */
    fun getPrefix(destination: String?, category: String? = null, code: String? = null): String {
        return if (isCocina(destination, category, code)) "PF-C-" else "PF-B-"
    }

    /**
     * Generates the next sequential product code (e.g. PF-C-0001, PF-B-0001)
     * based on existing products in the system.
     * Starts at 0001 and increments consecutively per area.
     */
    fun generateNextProductCode(
        destination: String?,
        category: String? = null,
        existingProducts: List<Product>
    ): String {
        val prefix = getPrefix(destination, category)
        val regex = Regex("""^PF-[CB]-(\d+)""", RegexOption.IGNORE_CASE)

        var maxNumber = 0
        for (product in existingProducts) {
            val code = product.code.trim()
            if (code.startsWith(prefix, ignoreCase = true)) {
                val match = regex.find(code)
                if (match != null) {
                    val num = match.groupValues[1].toIntOrNull() ?: 0
                    if (num > maxNumber) {
                        maxNumber = num
                    }
                }
            }
        }

        val nextNum = maxNumber + 1
        return "$prefix${String.format("%04d", nextNum)}"
    }
}
