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
)

data class PosUiState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
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
            val tot = BigDecimal.valueOf(sub + tx).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalSub = BigDecimal.valueOf(sub).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalGrossSub = BigDecimal.valueOf(grossSub).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalDiscounts = BigDecimal.valueOf(discounts).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalTx = BigDecimal.valueOf(tx).setScale(2, RoundingMode.HALF_UP).toDouble()
            return PosCartSummary(
                subtotal = finalSub,
                tax = finalTx,
                total = tot,
                itemCount = cnt,
                grossSubtotal = finalGrossSub,
                discountTotal = finalDiscounts,
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

