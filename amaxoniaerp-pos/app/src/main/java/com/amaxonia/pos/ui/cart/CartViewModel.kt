package com.amaxonia.pos.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.db.DraftInvoiceDao
import com.amaxonia.pos.data.local.db.DraftInvoiceEntity
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.seller.Seller
import com.amaxonia.pos.domain.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CartState(
    val items: List<CartItem> = emptyList(),
    val displayItems: List<ItemCarrito> = emptyList(),
    val total: Double = 0.0,
    val selectedClient: Client? = null,
    val currentSeller: Seller? = null,
    val availableSellers: List<Seller> = emptyList(),
    val orderSuccessMessage: String? = null,
    val allowEditPrices: Boolean = false,
    val allowDiscounts: Boolean = false,
    val tasa: Double = 0.0,
    val abrMonedaSecundaria: String = "",
    val isMultiCurrency: Boolean = false,
) {
    val totalBsText: String
        get() = if (isMultiCurrency && tasa > 0.0) String.format("%.2f", total * tasa) else ""
}

class CartViewModel(
    private val cartRepository: CartRepository,
    private val clientRepository: ClientRepository,
    private val localStore: com.amaxonia.pos.data.local.LocalStore,
    private val apiConfigManager: com.amaxonia.pos.data.remote.ApiConfigManager,
    private val cajaRepository: com.amaxonia.pos.domain.repository.CajaRepository = com.amaxonia.pos.ui.common.DependencyContainer.cajaRepository,
    private val draftInvoiceDao: DraftInvoiceDao = com.amaxonia.pos.ui.common.DependencyContainer.draftInvoiceDao
) : ViewModel() {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    @Volatile
    private var adminDb: String = ""

    init {
        viewModelScope.launch {
            adminDb = localStore.readCompanySession()?.company?.adminDb ?: ""
        }
        viewModelScope.launch {
            cartRepository.cartItems.collect { items ->
                updateState(items, cartRepository.selectedClient.value)
            }
        }
        viewModelScope.launch {
            cartRepository.selectedClient.collect { client ->
                updateState(cartRepository.cartItems.value, client)
            }
        }
        viewModelScope.launch {
            cartRepository.currentSeller.collect { seller ->
                _state.update { it.copy(currentSeller = seller) }
            }
        }
        viewModelScope.launch {
            cartRepository.availableSellers.collect { sellers ->
                _state.update { it.copy(availableSellers = sellers) }
            }
        }
        viewModelScope.launch {
            localStore.allowEditPricesFlow().collect { enabled ->
                _state.update { it.copy(allowEditPrices = enabled) }
            }
        }
        viewModelScope.launch {
            localStore.allowDiscountsFlow().collect { enabled ->
                _state.update { it.copy(allowDiscounts = enabled) }
            }
        }
        viewModelScope.launch {
            cajaRepository.activeCaja.collect { caja ->
                val currencyConfig = caja?.currency
                val isMultiCurrency = currencyConfig?.multiMoneda.equals("SI", ignoreCase = true)
                val rate = if (isMultiCurrency) {
                    currencyConfig?.tasa?.takeIf { it > 0.0 } ?: 0.0
                } else {
                    0.0
                }
                _state.update {
                    it.copy(
                        tasa = rate,
                        abrMonedaSecundaria = if (isMultiCurrency) currencyConfig?.abrMonedaSecundaria ?: "" else "",
                        isMultiCurrency = isMultiCurrency
                    )
                }
            }
        }
        ensureDefaultClient()
    }

    private fun updateState(items: List<CartItem>, client: Client?) {
        _state.update {
            it.copy(
                items = items,
                displayItems = cartRepository.getDisplayItems(),
                total = items.sumOf { item -> item.total },
                selectedClient = client
            )
        }
    }

    fun increaseQuantity(productId: String) {
        cartRepository.increaseQuantity(productId)
        refreshLotsIfNeeded(productId)
    }

    fun decreaseQuantity(productId: String) {
        cartRepository.decreaseQuantity(productId)
        refreshLotsIfNeeded(productId)
    }

    fun removeItem(productId: String) = cartRepository.removeItem(productId)

    fun removePromotion(promotionId: String) = cartRepository.removePromotion(promotionId)

    /** Recalcula lotes FEFO cuando cambia la cantidad */
    private fun refreshLotsIfNeeded(productId: String) {
        val item = cartRepository.cartItems.value.firstOrNull { it.product.id == productId } ?: return
        if (!item.hasLotConfig) return

        viewModelScope.launch {
            val session = localStore.readCompanySession() ?: return@launch
            val apiService = com.amaxonia.pos.ui.common.DependencyContainer.apiService
            runCatching {
                val response = apiService.getItemLots(session.token, productId)
                if (response.poseeConfiguracionLote && response.lotes.isNotEmpty()) {
                    val currentItem = cartRepository.cartItems.value.firstOrNull { it.product.id == productId }
                    val totalQty = currentItem?.quantity ?: 0
                    if (totalQty > 0) {
                        val assignments = assignFefo(response.lotes, totalQty)
                        cartRepository.assignLots(productId, assignments)
                    }
                }
            }
        }
    }

    private fun assignFefo(
        lots: List<com.amaxonia.pos.data.remote.dto.ItemLotInfoDto>,
        totalQty: Int
    ): List<com.amaxonia.pos.domain.model.LotAssignment> {
        val assignments = mutableListOf<com.amaxonia.pos.domain.model.LotAssignment>()
        var remaining = totalQty
        for (lot in lots) {
            if (remaining <= 0) break
            val take = minOf(remaining, lot.disponibilidad)
            if (take > 0) {
                assignments.add(
                    com.amaxonia.pos.domain.model.LotAssignment(
                        idLoteItem = lot.idLoteItem.toString(),
                        codigoLote = lot.codigoLoteItem,
                        vencimiento = lot.vencimiento,
                        cantidad = take,
                        almacen = lot.idAlmacen
                    )
                )
                remaining -= take
            }
        }
        return assignments
    }

    fun updateItemPrice(productId: String, unitPriceWithTax: Double) {
        if (!_state.value.allowEditPrices) return
        cartRepository.updateItemPrice(productId, unitPriceWithTax)
    }

    fun updateItemDiscount(productId: String, discountPercent: Double) {
        if (!_state.value.allowDiscounts) return
        cartRepository.updateItemDiscount(productId, discountPercent)
    }

    fun clearCart() {
        cartRepository.clearCart()
    }

    fun removeClient() {
        cartRepository.removeClient()
    }

    fun selectSeller(sellerId: Int) {
        val seller = cartRepository.availableSellers.value.firstOrNull { it.id == sellerId } ?: return
        cartRepository.setCurrentSeller(seller)
    }

    /** Guarda el carrito actual como factura pendiente/borrador local */
    fun saveDraft() {
        val items = _state.value.items
        if (items.isEmpty()) return

        viewModelScope.launch {
            val client = _state.value.selectedClient
            val seller = _state.value.currentSeller

            // Serializar items como JSON simplificado
            val itemsJson = buildDraftItemsJson(items)

            val draft = DraftInvoiceEntity(
                id = UUID.randomUUID().toString(),
                clientId = client?.id,
                clientFirstName = client?.firstName,
                clientLastName = client?.lastName,
                sellerId = seller?.id ?: 0,
                sellerName = seller?.nombre,
                itemsJson = itemsJson,
                total = _state.value.total,
                itemCount = items.sumOf { it.quantity }
            )

            draftInvoiceDao.insert(draft)
            cartRepository.clearCart()
            val clientLabel = if (client != null) "${client.firstName} ${client.lastName}" else "Sin cliente"
            _state.update { it.copy(orderSuccessMessage = "Borrador guardado para $clientLabel (${items.size} productos)") }
        }
    }

    private fun buildDraftItemsJson(items: List<CartItem>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { index, item ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"productId\":\"${item.product.id}\",")
            sb.append("\"description\":\"${item.product.description.replace("\"", "\\\"")}\",")
            sb.append("\"quantity\":${item.quantity},")
            sb.append("\"unitPriceWithTax\":${item.unitPriceWithTax},")
            sb.append("\"discountPercent\":${item.discountPercent},")
            sb.append("\"codVendedor\":${item.codVendedor},")
            sb.append("\"taxRate\":${item.product.taxRate},")
            sb.append("\"isExempt\":${item.product.isExempt},")
            sb.append("\"code\":\"${item.product.code}\",")
            sb.append("\"barcode1\":\"${item.product.barcode1}\"")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    fun clearMessage() {
        _state.update { it.copy(orderSuccessMessage = null) }
    }

    fun getClientPhotoUrl(client: Client): String {
        if (client.id.isBlank() || adminDb.isBlank()) return ""
        val filename = client.photoFilename.takeIf { it.isNotBlank() }
            ?: return ""
        return com.amaxonia.pos.data.remote.ImageUrlHelper.clientPhotoUrl(
            baseUrl = apiConfigManager.baseUrl.value,
            countryCode = apiConfigManager.getCurrentCountryCode(),
            companyDb = adminDb,
            idCliente = client.id,
            photoFilename = filename
        )
    }

    private fun ensureDefaultClient() {
        if (cartRepository.selectedClient.value != null) return
        viewModelScope.launch {
            clientRepository.getDefaultClient()
                .onSuccess { defaultClient -> cartRepository.setClient(defaultClient) }
        }
    }
}
