package com.amaxonia.erp.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.SaleInvoiceDto
import com.amaxonia.erp.data.remote.dto.SaleItemDto
import com.amaxonia.erp.data.remote.dto.SalePaymentDto
import com.amaxonia.erp.data.remote.dto.SalePaymentSummaryDto
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.repository.CajaRepository
import com.amaxonia.erp.domain.repository.ClientRepository
import com.amaxonia.erp.domain.repository.ProductRepository
import com.amaxonia.erp.domain.repository.SalesRepository
import com.amaxonia.erp.domain.util.CajaDateParser
import com.amaxonia.erp.data.printer.DefaultInvoicePrintGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PosTerminalViewModel(
    private val productRepository: ProductRepository,
    private val clientRepository: ClientRepository,
    private val cajaRepository: CajaRepository,
    private val salesRepository: SalesRepository,
    private val localStore: LocalStore,
    private val printGateway: DefaultInvoicePrintGateway? = null,
) : ViewModel() {


    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Load active caja & status
            val activeCaja = cajaRepository.getActiveCaja()
            var isCajaOpen = false
            var isDiaAnterior = false
            var formattedFecha: String? = null
            if (activeCaja != null) {
                val statusResult = cajaRepository.checkCajaStatus(activeCaja.first)
                val status = statusResult.getOrNull()
                isCajaOpen = status?.isOpen == true
                val rawFecha = status?.cajaSecuencia?.fechaApertura
                formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                isDiaAnterior = status?.cajaSecuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
            }

            // 2. Load payment methods
            val paymentMethods = salesRepository.getFormasPago(activeCaja?.first).getOrElse {
                listOf(
                    FormaPagoDto(idFormaPago = 1, siglas = "EF", codigo = "EFECTIVO", descripcion = "Efectivo"),
                    FormaPagoDto(idFormaPago = 2, siglas = "TARJ", codigo = "TARJETA", descripcion = "Tarjeta"),
                    FormaPagoDto(idFormaPago = 3, siglas = "TRANS", codigo = "TRANSFERENCIA", descripcion = "Transferencia"),
                )
            }

            // 3. Load default client
            val defaultClient = Client(id = "0", code = "CF", name = "CONSUMIDOR FINAL", identification = "CF")

            // 4. Load departments
            val departments = productRepository.getDepartments().getOrElse { emptyList() }

            // 5. Load products
            val productsResult = productRepository.getAllProducts(page = 1, pageSize = 50)
            val products = productsResult.getOrElse { emptyList() }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    products = products,
                    filteredProducts = products,
                    departments = departments,
                    activeCajaId = activeCaja?.first,
                    activeCajaName = activeCaja?.second,
                    isCajaOpen = isCajaOpen,
                    isCajaDiaAnterior = isDiaAnterior,
                    showAvisoCajaAnterior = isDiaAnterior,
                    cajaFechaApertura = formattedFecha,
                    paymentMethods = paymentMethods,
                    selectedPaymentMethod = paymentMethods.firstOrNull(),
                    selectedClient = defaultClient,
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val filtered = filterProducts(state.products, query, state.selectedDepartmentId)
            state.copy(searchQuery = query, filteredProducts = filtered)
        }
    }

    fun onDepartmentSelected(deptId: Int?) {
        _uiState.update { state ->
            val newDeptId = if (state.selectedDepartmentId == deptId) null else deptId
            val filtered = filterProducts(state.products, state.searchQuery, newDeptId)
            state.copy(selectedDepartmentId = newDeptId, filteredProducts = filtered)
        }
    }

    private fun filterProducts(products: List<Product>, query: String, deptId: Int?): List<Product> {
        val q = query.trim().lowercase()
        return products.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.description.lowercase().contains(q) ||
                p.code.lowercase().contains(q) ||
                p.barcode1.lowercase().contains(q) ||
                p.reference.lowercase().contains(q)
            matchesQuery
        }
    }

    fun addToCart(product: Product) {
        _uiState.update { state ->
            val existingIndex = state.cart.indexOfFirst { it.product.id == product.id }
            val updatedCart = state.cart.toMutableList()
            if (existingIndex >= 0) {
                val current = updatedCart[existingIndex]
                updatedCart[existingIndex] = current.copy(quantity = current.quantity + 1.0)
            } else {
                updatedCart.add(CartItem(product = product, quantity = 1.0))
            }
            state.copy(cart = updatedCart)
        }
    }

    fun incrementQuantity(productId: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.map { item ->
                if (item.product.id == productId) {
                    item.copy(quantity = item.quantity + 1.0)
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun decrementQuantity(productId: String) {
        _uiState.update { state ->
            val updatedCart = state.cart.mapNotNull { item ->
                if (item.product.id == productId) {
                    val newQty = item.quantity - 1.0
                    if (newQty > 0) item.copy(quantity = newQty) else null
                } else item
            }
            state.copy(cart = updatedCart)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { state ->
            state.copy(cart = state.cart.filterNot { it.product.id == productId })
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun selectClient(client: Client) {
        _uiState.update { it.copy(selectedClient = client, showClientDialog = false) }
    }

    fun openClientDialog() {
        _uiState.update { it.copy(showClientDialog = true) }
    }

    fun dismissClientDialog() {
        _uiState.update { it.copy(showClientDialog = false) }
    }

    fun openPaymentDialog() {
        val state = _uiState.value
        if (state.cart.isEmpty()) return

        if (state.activeCajaId == null || !state.isCajaOpen) {
            _uiState.update { it.copy(showCajaWarningDialog = true) }
            return
        }

        if (state.isCajaDiaAnterior) {
            _uiState.update { it.copy(showAvisoCajaAnterior = true) }
            return
        }

        val total = state.summary.total
        _uiState.update {
            it.copy(
                showPaymentDialog = true,
                receivedAmountText = if (total > 0.0) total.toString() else "0.0",
                errorMessage = null,
            )
        }
    }

    fun dismissPaymentDialog() {
        _uiState.update { it.copy(showPaymentDialog = false, errorMessage = null) }
    }

    fun dismissCajaWarning() {
        _uiState.update { it.copy(showCajaWarningDialog = false) }
    }

    fun dismissAvisoCajaAnterior() {
        _uiState.update { it.copy(showAvisoCajaAnterior = false) }
    }

    fun renovarCajaDiaAnterior() {
        val cajaId = _uiState.value.activeCajaId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRenovandoCaja = true, showAvisoCajaAnterior = false, errorMessage = null) }
            val nextSequence = cajaRepository.getNextSecuenciaCodigo(cajaId).getOrNull()
            val activeCajaObj = cajaRepository.activeCaja.value
            val request = AperturaRequest(
                idCaja = cajaId,
                montoApertura = 0.0,
                secuencia = nextSequence,
                serieSucursal = activeCajaObj?.serieSucursal ?: activeCajaObj?.serieCaja ?: "",
                idSucursal = activeCajaObj?.idSucursal,
            )
            cajaRepository.openCaja(request).fold(
                onSuccess = { res ->
                    val rawFecha = res.cajaSecuencia?.fechaApertura
                    val formatted = rawFecha?.let(CajaDateParser::formatDisplayDate)
                    _uiState.update {
                        it.copy(
                            isRenovandoCaja = false,
                            isCajaOpen = res.isOpen,
                            cajaFechaApertura = formatted,
                            isCajaDiaAnterior = false,
                            showAvisoCajaAnterior = false,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isRenovandoCaja = false,
                            errorMessage = "Error al renovar caja: ${err.message}",
                        )
                    }
                },
            )
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update {
            it.copy(
                completedSaleInvoice = null,
                completedSaleInfo = null,
                printFeedbackMessage = null,
                isPrintingReceipt = false,
            )
        }
    }

    fun printReceipt(facturaId: String? = null) {
        val targetId = facturaId
            ?: _uiState.value.completedSaleInfo?.facturaId
            ?: _uiState.value.completedSaleInvoice
            ?: return

        if (_uiState.value.isPrintingReceipt) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPrintingReceipt = true, printFeedbackMessage = null) }
            val session = localStore.readCompanySession()
            val countryCode = session?.company?.countryCode ?: "PA"

            val feedback = printGateway?.print(countryCode = countryCode, facturaId = targetId)
            _uiState.update {
                it.copy(
                    isPrintingReceipt = false,
                    printFeedbackMessage = feedback?.displayMessage ?: "Impresión finalizada",
                    isPrintSuccess = feedback?.isSuccess ?: true,
                )
            }
        }
    }


    fun onReceivedAmountChange(amount: String) {
        _uiState.update { it.copy(receivedAmountText = amount) }
    }

    fun selectPaymentMethod(method: FormaPagoDto) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
    }

    fun processSale() {
        val state = _uiState.value
        if (state.cart.isEmpty() || state.isProcessingSale) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingSale = true, errorMessage = null) }

            val session = localStore.readCompanySession()
            val sucursal = localStore.readActiveSucursal()
            val sucursalId = sucursal?.first?.toIntOrNull() ?: 1
            val summary = state.summary
            val received = state.receivedAmountText.toDoubleOrNull() ?: summary.total
            val change = (received - summary.total).coerceAtLeast(0.0)

            val invoice = SaleInvoiceDto(
                idCliente = state.selectedClient?.id ?: "0",
                codCliente = state.selectedClient?.code ?: "CF",
                idSucursal = sucursalId,
                idCaja = state.activeCajaId ?: "1",
                codigoCaja = state.activeCajaName ?: "01",
                subtotal = summary.subtotal,
                ivaTotalFactura = summary.tax,
                totalTotalFactura = summary.total,
                montoItemsFactura = summary.subtotal,
                totalizarBaseImponible = summary.subtotal,
                totalizarMontoIva = summary.tax,
                totalizarTotalGeneral = summary.total,
                usuarioCreacion = session?.user?.username ?: "admin",
                facturarA = state.selectedClient?.name ?: "CONSUMIDOR FINAL",
                facturarARuc = state.selectedClient?.identification ?: "CF",
                facturarADireccion = state.selectedClient?.address ?: "",
                facturarATelefono = state.selectedClient?.phone ?: "",
                formaPago = state.selectedPaymentMethod?.codigo ?: "EFECTIVO",
            )

            val saleItems = state.cart.map { item ->
                SaleItemDto(
                    idItem = item.product.id.toIntOrNull() ?: 1,
                    itemAlmacen = 1,
                    itemDescripcion = item.product.description,
                    itemCantidad = item.quantity,
                    itemPrecioSinIva = item.unitPriceWithoutTax,
                    itemPIva = item.product.taxRate,
                    itemTotalSinIva = item.subtotalWithoutTax,
                    itemTotalConIva = item.totalWithTax,
                    itemCantidadTotal = item.quantity,
                    itemCodigo = item.product.code,
                    itemReferencia = item.product.reference,
                    esProductoFisico = true,
                )
            }

            val paymentSummary = SalePaymentSummaryDto(
                totalizarMontoCancelar = summary.total,
                totalizarMontoEfectivo = received,
                totalizarCambio = change,
                montosPorTipo = mapOf(
                    (state.selectedPaymentMethod?.codigo ?: "EFECTIVO") to summary.total,
                ),
            )

            val payments = listOf(
                SalePaymentDto(
                    idFormaPago = state.selectedPaymentMethod?.idFormaPago ?: 1,
                    monto = summary.total,
                    montoRecibido = received,
                    efectivoCambio = change,
                    siglas = state.selectedPaymentMethod?.siglas,
                )
            )

            val request = ProcessSaleRequestDto(
                factura = invoice,
                items = saleItems,
                pagoResumen = paymentSummary,
                pagos = payments,
            )

            val result = salesRepository.processSale(request)

            result.onSuccess { response ->
                val invoiceNumber = response.codFactura.ifBlank { response.idFactura }.ifBlank { "PROCESADA" }
                val targetFacturaId = response.idFactura.ifBlank { response.codFactura }
                val completedInfo = CompletedSaleInfo(
                    facturaId = targetFacturaId,
                    numeroFactura = invoiceNumber,
                    clientName = state.selectedClient?.name ?: "CONSUMIDOR FINAL",
                    total = summary.total,
                    receivedAmount = received,
                    changeAmount = change,
                    paymentMethodName = state.selectedPaymentMethod?.descripcion ?: state.selectedPaymentMethod?.codigo ?: "Efectivo",
                )
                _uiState.update {
                    it.copy(
                        isProcessingSale = false,
                        showPaymentDialog = false,
                        cart = emptyList(),
                        completedSaleInvoice = invoiceNumber,
                        completedSaleInfo = completedInfo,
                        printFeedbackMessage = null,
                        isPrintingReceipt = false,
                    )
                }

                if (localStore.readAutoPrintReceipt()) {
                    printReceipt(targetFacturaId)
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isProcessingSale = false,
                        errorMessage = ex.message ?: "Error al procesar la venta",
                    )
                }
            }
        }
    }
}
