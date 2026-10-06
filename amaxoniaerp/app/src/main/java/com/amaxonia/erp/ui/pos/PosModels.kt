package com.amaxonia.erp.ui.pos

import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.SellerSummary
import java.math.BigDecimal
import java.math.RoundingMode

data class CartItem(
    val product: Product,
    val quantity: Double = 1.0,
    val discountPercent: Double = 0.0,
) {
    val unitPriceWithTax: Double get() = product.mainPrice

    val unitPriceWithoutTax: Double
        get() {
            if (product.isExempt || product.taxRate <= 0.0) return unitPriceWithTax
            val divisor = 1.0 + (product.taxRate / 100.0)
            return unitPriceWithTax / divisor
        }

    val subtotalWithoutTax: Double
        get() = BigDecimal.valueOf(unitPriceWithoutTax * quantity)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    val taxAmount: Double
        get() {
            if (product.isExempt || product.taxRate <= 0.0) return 0.0
            return BigDecimal.valueOf(subtotalWithoutTax * (product.taxRate / 100.0))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        }

    val totalWithTax: Double
        get() = BigDecimal.valueOf(subtotalWithoutTax + taxAmount)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
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
) {

    val summary: PosCartSummary
        get() {
            var sub = 0.0
            var tx = 0.0
            var cnt = 0
            cart.forEach { item ->
                sub += item.subtotalWithoutTax
                tx += item.taxAmount
                cnt += item.quantity.toInt().coerceAtLeast(1)
            }
            val tot = BigDecimal.valueOf(sub + tx).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalSub = BigDecimal.valueOf(sub).setScale(2, RoundingMode.HALF_UP).toDouble()
            val finalTx = BigDecimal.valueOf(tx).setScale(2, RoundingMode.HALF_UP).toDouble()
            return PosCartSummary(
                subtotal = finalSub,
                tax = finalTx,
                total = tot,
                itemCount = cnt,
            )
        }

    val changeAmount: Double
        get() {
            val received = receivedAmountText.toDoubleOrNull() ?: 0.0
            return (received - summary.total).coerceAtLeast(0.0)
        }
}
