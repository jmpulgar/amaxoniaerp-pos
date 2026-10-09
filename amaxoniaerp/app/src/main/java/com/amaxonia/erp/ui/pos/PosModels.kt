package com.amaxonia.erp.ui.pos

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.ItemCarrito
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.Promocion
import com.amaxonia.erp.domain.model.SellerSummary
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor

data class CartItem(
    val product: Product,
    val quantity: Double = 1.0,
    val unitPriceWithTax: Double = product.mainPrice,
    val discountPercent: Double = 0.0,
    val isManualPrice: Boolean = false,
    val selectedPriceLabel: String = "A",
    val promocionId: String? = null,
    val promocionCodigo: String = "",
    val promocionNombre: String = "",
    val promocionTipo: String = "",
    val promocionGrupo: String = "",
    val promocionDetalleId: String = "",
    val promocionVeces: Int = 1,
) {
    val isPromotionLine: Boolean get() = !promocionId.isNullOrBlank()

    val taxRate: Double
        get() = if (product.isExempt) 0.0 else product.taxRate

    val unitPriceWithoutTax: Double
        get() {
            if (taxRate <= 0.0) return unitPriceWithTax
            val divisor = 1.0 + (taxRate / 100.0)
            return unitPriceWithTax / divisor
        }

    val subtotalWithoutTax: Double
        get() = BigDecimal.valueOf(unitPriceWithoutTax * quantity)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    val discountAmountWithoutTax: Double
        get() = BigDecimal.valueOf(subtotalWithoutTax * (discountPercent.coerceIn(0.0, 100.0) / 100.0))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    val totalWithoutTax: Double
        get() = (subtotalWithoutTax - discountAmountWithoutTax).coerceAtLeast(0.0)

    val taxAmount: Double
        get() {
            if (taxRate <= 0.0) return 0.0
            return BigDecimal.valueOf(totalWithoutTax * (taxRate / 100.0))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        }

    val totalWithTax: Double
        get() = BigDecimal.valueOf(totalWithoutTax + taxAmount)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    val total: Double
        get() = totalWithTax
}

data class CompletedSaleInfo(
    val facturaId: String,
    val numeroFactura: String,
    val clientName: String,
    val total: Double,
    val receivedAmount: Double,
    val changeAmount: Double,
    val paymentMethodName: String,
)

data class PosCartSummary(
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val itemCount: Int = 0,
    val grossSubtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val itemDiscounts: Double = 0.0,
    val globalDiscountAmount: Double = 0.0,
)

data class PosUiState(
    val isLoading: Boolean = true,
    val products: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val catalogCurrentPage: Int = 1,
    val catalogTotalPages: Int = 1,
    val isCatalogLoading: Boolean = false,
    val departments: List<DepartmentDto> = emptyList(),
    val selectedDepartmentId: Int? = null,
    val searchQuery: String = "",
    val cart: List<CartItem> = emptyList(),
    val selectedClient: Client? = null,
    val clientBranches: List<ClientBranch> = emptyList(),
    val selectedClientBranch: ClientBranch? = null,
    val isLoadingBranches: Boolean = false,
    val branchSelectionRequiredError: Boolean = false,
    val selectedSeller: SellerSummary? = null,
    val availableSellers: List<SellerSummary> = emptyList(),
    val showSellerSheet: Boolean = false,
    val activeCajaName: String? = null,
    val activeCajaId: String? = null,
    val isCajaOpen: Boolean = false,
    val isCajaDiaAnterior: Boolean = false,
    val showAvisoCajaAnterior: Boolean = false,
    val cajaFechaApertura: String? = null,
    val isRenovandoCaja: Boolean = false,
    val sucursalNombre: String? = null,
    val almacenNombre: String? = null,
    val usuarioApertura: String? = null,
    val paymentMethods: List<FormaPagoDto> = emptyList(),
    val selectedPaymentMethod: FormaPagoDto? = null,
    val receivedAmountText: String = "",
    val isProcessingSale: Boolean = false,
    val completedSaleInvoice: String? = null,
    val completedSaleInfo: CompletedSaleInfo? = null,
    val isPrintingReceipt: Boolean = false,
    val printFeedbackMessage: String? = null,
    val isPrintSuccess: Boolean = true,
    val errorMessage: String? = null,
    val showPaymentDialog: Boolean = false,
    val showClientDialog: Boolean = false,
    val showCajaWarningDialog: Boolean = false,
    val allPromotions: List<Promocion> = emptyList(),
    val pendingPromotionProduct: Product? = null,
    val promotionOptions: List<Promocion> = emptyList(),
    val showPromotionChoice: Boolean = false,
    val quantityPickerProduct: Product? = null,
    val allowEditPrices: Boolean = true,
    val allowDiscounts: Boolean = true,
    val isCatalogDrawerOpen: Boolean = true,
    val observationText: String = "",
    val activeDocumentType: String = "Factura",
    val globalDiscountPercent: Double = 0.0,
    val activeCajaSecuenciaId: String? = null,
    val customClientName: String = "",
    val showCustomClientNameDialog: Boolean = false,
    val showGlobalDiscountDialog: Boolean = false,
    val showPrintOptionsDialog: Boolean = false,
    val paymentsMap: Map<Int, Double> = emptyMap(),
    val paymentInputTexts: Map<Int, String> = emptyMap(),
    val activePaymentInputMethodId: Int? = null,
    val expandedCashDenominations: Boolean = false,
    val showProductDialog: Boolean = false,
    val productDialogQuery: String = "",
) {

    val summary: PosCartSummary
        get() {
            var grossSub = 0.0
            var discounts = 0.0
            var sub = 0.0
            var tx = 0.0
            var cnt = 0
            cart.forEach { item ->
                grossSub += item.subtotalWithoutTax
                discounts += item.discountAmountWithoutTax
                sub += item.totalWithoutTax
                tx += item.taxAmount
                cnt += item.quantity.toInt().coerceAtLeast(1)
            }
            val globalDiscountAmount = if (globalDiscountPercent > 0.0) {
                BigDecimal.valueOf(sub * (globalDiscountPercent / 100.0))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toDouble()
            } else 0.0
            val subAfterGlobalDiscount = (sub - globalDiscountAmount).coerceAtLeast(0.0)
            val txFactor = if (sub > 0.0) subAfterGlobalDiscount / sub else 1.0
            val adjustedTx = BigDecimal.valueOf(tx * txFactor).setScale(2, RoundingMode.HALF_UP).toDouble()
            val tot = BigDecimal.valueOf(subAfterGlobalDiscount + adjustedTx).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalSub = BigDecimal.valueOf(subAfterGlobalDiscount).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalGrossSub = BigDecimal.valueOf(grossSub).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalDiscounts = BigDecimal.valueOf(discounts + globalDiscountAmount).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalTx = adjustedTx
            return PosCartSummary(
                subtotal = finalSub,
                tax = finalTx,
                total = tot,
                itemCount = cnt,
                grossSubtotal = finalGrossSub,
                discountTotal = finalDiscounts,
                itemDiscounts = discounts,
                globalDiscountAmount = globalDiscountAmount,
            )
        }

    val displayItems: List<ItemCarrito>
        get() {
            val grouped = mutableListOf<ItemCarrito>()
            val processedPromoIds = mutableSetOf<String>()
            cart.forEach { item ->
                val promoId = item.promocionId
                if (promoId.isNullOrBlank()) {
                    grouped.add(ItemCarrito.ProductoIndividual(item))
                } else if (processedPromoIds.add(promoId)) {
                    val promoItems = cart.filter { it.promocionId == promoId }
                    val first = promoItems.first()
                    grouped.add(
                        ItemCarrito.PromocionAgrupada(
                            promocionId = promoId,
                            promocionCodigo = first.promocionCodigo,
                            promocionNombre = first.promocionNombre,
                            promocionTipo = first.promocionTipo,
                            promocionGrupo = first.promocionGrupo,
                            items = promoItems,
                        ),
                    )
                }
            }
            return grouped
        }

    val changeAmount: Double
        get() {
            val received = receivedAmountText.toDoubleOrNull() ?: 0.0
            return (received - summary.total).coerceAtLeast(0.0)
        }
}

fun isCashPaymentMethod(method: FormaPagoDto?): Boolean {
    if (method == null) return false
    val desc = method.descripcion.orEmpty()
    val cod = method.codigo.orEmpty()
    val sig = method.siglas.orEmpty()
    return desc.contains("Efectivo", ignoreCase = true) ||
        cod.contains("EFECTIVO", ignoreCase = true) ||
        sig.equals("EF", ignoreCase = true) ||
        sig.equals("EFEC", ignoreCase = true) ||
        sig.equals("CASH", ignoreCase = true)
}

internal fun calculateSuggestedBills(total: Double): List<Double> {
    if (total <= 0.0) return emptyList()
    val suggestions = sortedSetOf<Double>()

    val ceilVal = ceil(total)
    if (ceilVal > total) {
        suggestions.add(ceilVal)
    }

    val standardDenominations = listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0)
    for (bill in standardDenominations) {
        if (bill > total) {
            suggestions.add(bill)
        }
    }

    if (total > 10.0) {
        val next5 = (floor(total / 5.0) + 1) * 5.0
        if (next5 > total) suggestions.add(next5)
        val next10 = (floor(total / 10.0) + 1) * 10.0
        if (next10 > total) suggestions.add(next10)
    }

    return suggestions.toList().take(5)
}

