package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.model.OrderItem
import com.example.data.local.model.Product

object SalonTransferConfig {
    private const val PREFS_NAME = "SalonTransferPrefs"
    private const val KEY_PREFIX_PROD = "limit_prod_"
    private const val KEY_PREFIX_CODE = "limit_code_"
    private const val KEY_PREFIX_NAME = "limit_name_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Identifies if a product is a kitchen / elaborated / production item.
     */
    fun isProductionProduct(product: Product, isElaborated: Boolean = false): Boolean {
        if (isElaborated) return true
        if (product.destination.equals("COCINA", ignoreCase = true)) return true
        val cat = product.category.lowercase()
        return cat.contains("cocina") || cat.contains("postre") || cat.contains("elaborad") || cat.contains("pizz") || cat.contains("comida")
    }

    /**
     * Gets the configured transfer limit (units) for a product.
     * Returns null if no custom limit is set (i.e. unlimited).
     * Returns an Int >= 0 if a limit is configured.
     */
    fun getProductTransferLimit(context: Context, product: Product): Int? {
        val prefs = getPrefs(context)
        val keyProd = "$KEY_PREFIX_PROD${product.id}"
        if (prefs.contains(keyProd)) {
            val v = prefs.getInt(keyProd, -1)
            if (v >= 0) return v
        }

        if (product.code.isNotBlank()) {
            val keyCode = "$KEY_PREFIX_CODE${product.code.trim().uppercase()}"
            if (prefs.contains(keyCode)) {
                val v = prefs.getInt(keyCode, -1)
                if (v >= 0) return v
            }
        }

        if (product.name.isNotBlank()) {
            val keyName = "$KEY_PREFIX_NAME${product.name.trim().lowercase()}"
            if (prefs.contains(keyName)) {
                val v = prefs.getInt(keyName, -1)
                if (v >= 0) return v
            }
        }

        return null
    }

    /**
     * Saves the configured transfer units limit for a product.
     * Pass limit = null to remove the restriction (unlimited).
     * Pass limit >= 0 to set maximum transfer units.
     */
    fun setProductTransferLimit(context: Context, product: Product, limit: Int?) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        val keyProd = "$KEY_PREFIX_PROD${product.id}"
        val keyCode = if (product.code.isNotBlank()) "$KEY_PREFIX_CODE${product.code.trim().uppercase()}" else null
        val keyName = if (product.name.isNotBlank()) "$KEY_PREFIX_NAME${product.name.trim().lowercase()}" else null

        if (limit == null || limit < 0) {
            editor.remove(keyProd)
            keyCode?.let { editor.remove(it) }
            keyName?.let { editor.remove(it) }
        } else {
            editor.putInt(keyProd, limit)
            keyCode?.let { editor.putInt(it, limit) }
            keyName?.let { editor.putInt(it, limit) }
        }
        editor.apply()
    }

    data class ItemBreakdown(
        val productName: String,
        val totalQuantity: Int,
        val transferQuantity: Int,
        val cashQuantity: Int,
        val unitPrice: Double,
        val transferSubtotal: Double,
        val cashSubtotal: Double,
        val configuredLimit: Int?
    )

    data class TransferCalculationResult(
        val totalAmount: Double,
        val transferAmount: Double,
        val cashAmount: Double,
        val isLimited: Boolean,
        val itemBreakdowns: List<ItemBreakdown>
    )

    /**
     * Calculates the automatic breakdown between Transferencia and Efectivo
     * respecting configured per-product limits.
     */
    fun calculatePaymentBreakdown(
        context: Context,
        items: List<OrderItem>,
        products: List<Product>,
        fallbackTotal: Double = 0.0,
        isElaboratedProductId: (Long) -> Boolean = { false }
    ): TransferCalculationResult {
        if (items.isEmpty()) {
            return TransferCalculationResult(
                totalAmount = fallbackTotal,
                transferAmount = fallbackTotal,
                cashAmount = 0.0,
                isLimited = false,
                itemBreakdowns = emptyList()
            )
        }

        var totalAmount = 0.0
        var totalTransferAmount = 0.0
        var totalCashAmount = 0.0
        var hasLimitedItem = false
        val breakdowns = mutableListOf<ItemBreakdown>()

        for (item in items) {
            val itemTotal = item.unitPrice * item.quantity
            totalAmount += itemTotal

            val matchedProduct = products.find { it.id == item.productId || it.name.equals(item.productName, ignoreCase = true) }
            val isProd = if (matchedProduct != null) {
                isProductionProduct(matchedProduct, isElaboratedProductId(matchedProduct.id))
            } else {
                item.destination.equals("COCINA", ignoreCase = true)
            }

            val limit = if (matchedProduct != null) {
                getProductTransferLimit(context, matchedProduct)
            } else {
                val prefs = getPrefs(context)
                val keyName = "$KEY_PREFIX_NAME${item.productName.trim().lowercase()}"
                if (prefs.contains(keyName)) prefs.getInt(keyName, -1).takeIf { it >= 0 } else null
            }

            if (isProd && limit != null) {
                val transferQty = minOf(item.quantity, limit)
                val cashQty = item.quantity - transferQty
                val transferSub = transferQty * item.unitPrice
                val cashSub = cashQty * item.unitPrice

                if (cashQty > 0) {
                    hasLimitedItem = true
                }

                totalTransferAmount += transferSub
                totalCashAmount += cashSub

                breakdowns.add(
                    ItemBreakdown(
                        productName = item.productName,
                        totalQuantity = item.quantity,
                        transferQuantity = transferQty,
                        cashQuantity = cashQty,
                        unitPrice = item.unitPrice,
                        transferSubtotal = transferSub,
                        cashSubtotal = cashSub,
                        configuredLimit = limit
                    )
                )
            } else {
                totalTransferAmount += itemTotal
                breakdowns.add(
                    ItemBreakdown(
                        productName = item.productName,
                        totalQuantity = item.quantity,
                        transferQuantity = item.quantity,
                        cashQuantity = 0,
                        unitPrice = item.unitPrice,
                        transferSubtotal = itemTotal,
                        cashSubtotal = 0.0,
                        configuredLimit = null
                    )
                )
            }
        }

        return TransferCalculationResult(
            totalAmount = totalAmount,
            transferAmount = totalTransferAmount,
            cashAmount = totalCashAmount,
            isLimited = hasLimitedItem || totalCashAmount > 0.0,
            itemBreakdowns = breakdowns
        )
    }
}
