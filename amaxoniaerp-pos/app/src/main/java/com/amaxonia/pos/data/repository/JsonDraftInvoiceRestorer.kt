package com.amaxonia.pos.data.repository

import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.DraftInvoiceRestorer
import org.json.JSONArray

/** Compatibility adapter for the existing persisted draft JSON contract. */
class JsonDraftInvoiceRestorer(
    private val cartRepository: CartRepository,
) : DraftInvoiceRestorer {
    override fun restore(draft: DraftInvoice): Result<Unit> =
        runCatching {
            cartRepository.clearCart()
            val jsonArray = JSONArray(draft.itemsJson)
            for (index in 0 until jsonArray.length()) {
                restoreItem(jsonArray.getJSONObject(index))
            }
        }

    private fun restoreItem(item: org.json.JSONObject) {
        val product =
            Product(
                id = item.getString("productId"),
                description = item.getString("description"),
                code = item.optString("code", ""),
                barcode1 = item.optString("barcode1", ""),
                taxRate = item.optDouble("taxRate", 0.0),
                isExempt = item.optBoolean("isExempt", false),
                unitPackage = item.optString("unitPackage", ""),
                bulkQuantity = item.optDouble("bulkQuantity", 1.0).takeIf { it > 0.0 } ?: 1.0,
                portionUnit = item.optString("portionUnit", "").takeIf { it.isNotBlank() },
                prices =
                    listOf(
                        PriceLevel(
                            label = "A",
                            pricePlusTax = item.getDouble("unitPriceWithTax"),
                        ),
                    ),
            )
        val quantity = item.getInt("quantity")
        val discount = item.optDouble("discountPercent", 0.0)
        val unit = item.optString("itemUnitPackage", "UNIDAD")

        cartRepository.addToCart(product)
        cartRepository.updateItemUnit(product.id, unit)
        repeat(quantity - 1) { cartRepository.increaseQuantity(product.id) }
        if (discount > 0.0) {
            cartRepository.updateItemDiscount(product.id, discount)
        }
        val unitPrice = item.getDouble("unitPriceWithTax")
        if (unitPrice > 0.0) {
            cartRepository.updateItemPrice(product.id, unitPrice)
        }
    }
}
