package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.Department

enum class ProductViewMode {
    GRID,
    LIST
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
    val currentSeller: Seller? = null,
    val availableSellers: List<Seller> = emptyList(),
    val cajaPrincipalNombre: String = "Caja no seleccionada",
    val sucursalNombre: String = "Sucursal",
    // -------------------

    // --- CAJA STATE ---
    val availableCajas: List<Caja> = emptyList(),
    val isLoadingCajas: Boolean = false,
    val showCajaSelector: Boolean = false,
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

    // --- AUTO-CLOSE ---
    val autoCloseMessage: String? = null
)
