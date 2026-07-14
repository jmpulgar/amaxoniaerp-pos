package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.Department

enum class ProductViewMode {
    GRID,
    LIST,
}

data class DashboardProduct(
    val id: String,
    val name: String,
    val price: Double,
    val taxRate: Double = 0.0,
    val isExempt: Boolean = false,
    val imageUrl: String? = null,
    val category: String = "General",
    val code: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val sourceProduct: Product? = null,
)

data class DashboardState(
    val products: List<DashboardProduct> = emptyList(),
    val bestSellers: List<DashboardProduct> = emptyList(),
    // --- CAMBIOS PARA EL CARRITO ---
    val cartItems: List<CartItem> = emptyList(),
    val cartTotal: Double = 0.0,
    val cartItemCount: Int = 0,
    // -------------------------------
    // --- NUEVO CAMPO ---
    val selectedClient: Client? = null,
    val clientSucursales: List<ClientBranch> = emptyList(),
    val selectedClientSucursal: ClientBranch? = null,
    val currentSeller: Seller? = null,
    val availableSellers: List<Seller> = emptyList(),
    val cajaPrincipalNombre: String = "Caja no seleccionada",
    val sucursalNombre: String = "Sucursal",
    // -------------------
    // --- CAJA STATE ---
    val availableCajas: List<Caja> = emptyList(),
    val isLoadingCajas: Boolean = false,
    val showCajaSelector: Boolean = false,
    val hasActiveCaja: Boolean = false,
    // -------------------
    val selectedCategory: String = "Todos los productos",
    val departments: List<Department> = emptyList(),
    val selectedDepartmentId: Int? = null,
    val showDepartmentPicker: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val endOfListReached: Boolean = false,
    val isLoadingBestSellers: Boolean = false,
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val viewMode: ProductViewMode = ProductViewMode.GRID,
    val bottomSelected: Int = 0,
    val error: String? = null,
    val manualEntryValue: String = "",
    val promotionOptions: List<Promocion> = emptyList(),
    val pendingPromotionProduct: DashboardProduct? = null,
    val showPromotionChoice: Boolean = false,
    val promotionMessage: String? = null,
    val quantityPickerProduct: DashboardProduct? = null,
    // --- AUTO-CLOSE ---
    val autoCloseMessage: String? = null,
) {
    val isInitialProductLoading: Boolean
        get() = isLoading && products.isEmpty()

    val isInitialBestSellersLoading: Boolean
        get() = isLoadingBestSellers && bottomSelected == 1 && bestSellers.isEmpty()
}

sealed interface DashboardUiAction

sealed interface DashboardCajaUiAction : DashboardUiAction {
    data class Fetch(
        val forceShowSelector: Boolean = false,
    ) : DashboardCajaUiAction

    data class SelectAndOpen(
        val caja: Caja,
        val openingAmount: Double,
    ) : DashboardCajaUiAction

    data class SetSelectorVisible(
        val show: Boolean,
    ) : DashboardCajaUiAction

    data object DismissAutoCloseMessage : DashboardCajaUiAction
}

sealed interface DashboardSaleUiAction : DashboardUiAction {
    sealed interface Product : DashboardSaleUiAction

    sealed interface Manual : DashboardSaleUiAction

    sealed interface Session : DashboardSaleUiAction

    data class AddProduct(
        val product: DashboardProduct,
        val quantity: Int = 1,
    ) : Product

    data class ShowQuantityPicker(
        val product: DashboardProduct,
    ) : Product

    data object DismissQuantityPicker : Product

    data class ConfirmProductQuantity(
        val product: DashboardProduct,
        val quantity: Int,
    ) : Product

    data class AddProductIndividualFromPromotionChoice(
        val quantity: Int = 1,
    ) : Product

    data class AddPromotionFromChoice(
        val promotion: Promocion,
        val times: Int = 1,
    ) : Product

    data object DismissPromotionChoice : Product

    data object ClearPromotionMessage : Product

    data class ManualKey(
        val key: String,
    ) : Manual

    data object ManualClear : Manual

    data object ManualBackspace : Manual

    data object ManualSubmit : Manual

    data object StartNewOrder : Session

    data class SelectSeller(
        val sellerId: Int,
    ) : Session

    data object Checkout : DashboardSaleUiAction
}

sealed interface DashboardUiEffect {
    data object NavigateToCart : DashboardUiEffect
}

sealed interface DashboardCatalogUiAction : DashboardUiAction {
    sealed interface Paging : DashboardCatalogUiAction

    sealed interface Search : DashboardCatalogUiAction

    sealed interface View : DashboardCatalogUiAction

    sealed interface Department : DashboardCatalogUiAction

    data object LoadMoreProducts : Paging

    data object ToggleSearch : Search

    data class SetSearchQuery(
        val value: String,
    ) : Search

    data object ToggleViewMode : View

    data class SetBottomSelected(
        val index: Int,
    ) : View

    data object Retry : Paging

    data class SetDepartmentPicker(
        val show: Boolean,
    ) : Department

    data class SelectDepartment(
        val departmentId: Int?,
    ) : Department
}
