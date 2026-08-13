package com.amaxonia.pos.domain.usecase.cart

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.DraftInvoice
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.DraftInvoiceRepository
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator

data class SaveDraftInvoiceInput(
    val items: List<CartItem>,
    val client: Client?,
    val seller: Seller?,
    val total: Double,
    val financialSnapshot: SaleFinancialSnapshot? = null,
)

class SaveDraftInvoiceUseCase(
    private val repository: DraftInvoiceRepository,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend operator fun invoke(input: SaveDraftInvoiceInput): DraftInvoice {
        val snapshot = input.financialSnapshot ?: buildFinancialSnapshot(input.items, input.total)
        val draft =
            DraftInvoice(
                id = idGenerator.nextId(),
                clientId = input.client?.id,
                clientFirstName = input.client?.firstName,
                clientLastName = input.client?.lastName,
                sellerId = input.seller?.id ?: 0,
                sellerName = input.seller?.nombre,
                itemsJson = buildDraftItemsJson(input.items),
                total = snapshot.total,
                itemCount = input.items.sumOf { it.quantity },
                createdAt = clock.now().toEpochMilli(),
                subtotalGross = snapshot.subtotalGross,
                itemDiscounts = snapshot.itemDiscounts,
                subtotalNet = snapshot.subtotalNet,
                tax = snapshot.tax,
            )
        repository.save(draft)
        return draft
    }

    private fun buildFinancialSnapshot(
        items: List<CartItem>,
        total: Double,
    ): SaleFinancialSnapshot {
        val subtotalGross = items.fold(Money.ZERO) { sum, item -> sum + Money.fromDouble(item.subtotalWithoutTax) }
        val itemDiscounts = items.fold(Money.ZERO) { sum, item -> sum + Money.fromDouble(item.discountAmountWithoutTax) }
        val subtotalNet = items.fold(Money.ZERO) { sum, item -> sum + Money.fromDouble(item.totalWithoutTax) }
        val totalMoney = Money.fromDouble(total)
        return SaleFinancialSnapshot(
            subtotalGross = subtotalGross.toDouble(),
            itemDiscounts = itemDiscounts.toDouble(),
            subtotalNet = subtotalNet.toDouble(),
            tax = (totalMoney - subtotalNet).toDouble(),
            total = totalMoney.toDouble(),
        )
    }

    private fun buildDraftItemsJson(items: List<CartItem>): String {
        val output = StringBuilder("[")
        items.forEachIndexed { index, item ->
            if (index > 0) output.append(",")
            output.append("{")
            output.append("\"productId\":\"${item.product.id}\",")
            output.append("\"description\":\"${item.product.description.replace("\"", "\\\"")}\",")
            output.append("\"quantity\":${item.quantity},")
            output.append("\"unitPriceWithTax\":${item.unitPriceWithTax},")
            output.append("\"itemUnitPackage\":\"${item.itemUnitPackage}\",")
            output.append("\"unitPackage\":\"${item.product.unitPackage.replace("\"", "\\\"")}\",")
            output.append("\"bulkQuantity\":${item.product.bulkQuantity},")
            output.append("\"portionUnit\":\"${item.product.portionUnit.orEmpty()}\",")
            output.append("\"discountPercent\":${item.discountPercent},")
            output.append("\"codVendedor\":${item.codVendedor},")
            output.append("\"taxRate\":${item.product.taxRate},")
            output.append("\"isExempt\":${item.product.isExempt},")
            output.append("\"code\":\"${item.product.code}\",")
            output.append("\"barcode1\":\"${item.product.barcode1}\"")
            output.append("}")
        }
        output.append("]")
        return output.toString()
    }
}
