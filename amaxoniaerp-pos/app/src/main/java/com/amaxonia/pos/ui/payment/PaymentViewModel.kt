package com.amaxonia.pos.ui.payment

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.printer.PrinterFactory
import com.amaxonia.pos.data.printer.RapidPayBridge
import com.amaxonia.pos.data.printer.TheFactoryRapidPayClient
import com.amaxonia.pos.data.repository.CartRepository
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormaPagoDetalle
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class PaymentViewModel(
    private val transactionRepository: TransactionRepository,
    private val formaPagoRepository: FormaPagoRepository,
    private val cajaRepository: CajaRepository,
    private val cartRepository: CartRepository,
    private val salesRepository: SalesRepository,
    private val localStore: LocalStore,
    private val printerFactory: PrinterFactory,
    private val rapidPayClient: TheFactoryRapidPayClient
) : ViewModel() {

    companion object {
        private const val TAG = "PaymentVM"
    }

    private var configuredGatewayKey: String = ""
    private var configuredCommerceRif: String = ""

    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()

    /**
     * One-shot event: emits an Intent that the UI (PaymentScreen) must launch
     * via startActivity() to open the HKA POS gateway app.
     */
    private val _gatewayIntentEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val gatewayIntentEvent: SharedFlow<Intent> = _gatewayIntentEvent.asSharedFlow()

    init {
        loadFormasPago()
        loadGatewayConfiguration()
    }

    fun setTotalAmount(amount: Double) {
        val normalized = Money.toDouble(Money.fromDouble(amount))
        _state.update { it.copy(totalAmount = normalized) }
    }

    fun onKeyPadInput(key: String) {
        _state.update { s ->
            when (key) {
                "C" -> s.copy(tenderedAmountInput = "0", showInsufficientReminder = false)
                "BACK" -> {
                    val current = s.tenderedAmountInput
                    val updated = if (current.length > 1) current.dropLast(1) else "0"
                    s.copy(
                        tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                        showInsufficientReminder = false
                    )
                }
                "00" -> {
                    val updated = if (s.tenderedAmountInput == "0") "0" else s.tenderedAmountInput + "00"
                    s.copy(
                        tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                        showInsufficientReminder = false
                    )
                }
                else -> {
                    val updated = if (s.tenderedAmountInput == "0") key else s.tenderedAmountInput + key
                    s.copy(
                        tenderedAmountInput = Money.normalizeInput(updated).ifBlank { "0" },
                        showInsufficientReminder = false
                    )
                }
            }
        }
    }

    fun setExactAmount() {
        _state.update { it.copy(tenderedAmountInput = it.totalAmountText, showInsufficientReminder = false) }
    }

    fun toggleMethod(method: PaymentMethod) {
        _state.update { it.copy(selectedMethod = method, showInsufficientReminder = false) }
    }

    fun setExactAmountForNonCash(idFormaPago: Int) {
        _state.update { current ->
            current.copy(
                nonCashAmountsInput = mapOf(idFormaPago to current.totalAmountText),
                showInsufficientReminder = false
            )
        }
    }

    fun setNonCashAmount(idFormaPago: Int, amount: String) {
        val normalized = Money.normalizeInput(amount)

        _state.update { current ->
            current.copy(
                nonCashAmountsInput = current.nonCashAmountsInput.toMutableMap().apply {
                    if (normalized.isBlank()) remove(idFormaPago) else put(idFormaPago, normalized)
                },
                showInsufficientReminder = false
            )
        }
    }

    private fun loadFormasPago() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingFormasPago = true, formasPagoError = null, paymentError = null) }
            val activeCajaId = cajaRepository.activeCaja.first()?.idCaja

            formaPagoRepository.getFormasPago(activeCajaId).fold(
                onSuccess = { formas ->
                    val ordered = formas.sortedWith(compareBy<FormaPago> { it.orden }.thenBy { it.codigo ?: "" })
                    _state.update {
                        it.copy(
                            formasPago = ordered,
                            isLoadingFormasPago = false,
                            formasPagoError = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            formasPago = emptyList(),
                            isLoadingFormasPago = false,
                            formasPagoError = error.message ?: "No se pudieron cargar las formas de pago"
                        )
                    }
                }
            )
        }
    }

    private fun loadGatewayConfiguration() {
        viewModelScope.launch {
            val settings = localStore.readTheFactorySettings()
            configuredGatewayKey = settings.gatewayKey.trim()
            configuredCommerceRif = localStore.readCompanySession()?.company?.rif.orEmpty().trim()
        }
    }

    fun processPayment(onSuccess: (PaymentSuccessPayload) -> Unit) {
        val currentState = _state.value
        if (!currentState.isPaymentEnough) {
            _state.update { it.copy(showInsufficientReminder = true) }
            viewModelScope.launch {
                delay(1400)
                _state.update { state ->
                    if (state.isPaymentEnough) state else state.copy(showInsufficientReminder = false)
                }
            }
            return
        }

        val formapagoDetalle = buildFormapagoDetalle(currentState)
        val selectedPaymentMethods = buildSelectedPaymentMethods(currentState, formapagoDetalle.detalle)
        val requiresGatewayConfig = formapagoDetalle.detalle.any { detail ->
            val forma = currentState.formasPago.firstOrNull { it.idFormaPago == detail.idFormaPago }
            forma != null && requiresRapidPayForForma(forma)
        }
        if (formapagoDetalle.detalle.isEmpty()) {
            _state.update { it.copy(paymentError = "Debes indicar al menos una forma de pago valida") }
            return
        }
        _state.update {
            it.copy(
                lastFormapagoDetalle = formapagoDetalle,
                isProcessingPayment = true,
                paymentError = null,
                gatewayStatusMessage = "Validando cobro..."
            )
        }

        viewModelScope.launch {
            val cartItems = cartRepository.cartItems.value
            val selectedClient = cartRepository.selectedClient.value
            val activeCaja = cajaRepository.activeCaja.value
            if (requiresGatewayConfig) {
                if (configuredGatewayKey.isBlank()) {
                    configuredGatewayKey = localStore.readTheFactorySettings().gatewayKey.trim()
                }
                if (configuredCommerceRif.isBlank()) {
                    configuredCommerceRif = localStore.readCompanySession()?.company?.rif.orEmpty().trim()
                }
                if (configuredGatewayKey.isBlank()) {
                    _state.update {
                        it.copy(
                            isProcessingPayment = false,
                            paymentError = "Configura una pasarela HKA en configuración de impresora"
                        )
                    }
                    return@launch
                }
                if (configuredCommerceRif.isBlank()) {
                    _state.update {
                        it.copy(
                            isProcessingPayment = false,
                            paymentError = "No se encontró RIF comercio desde parametros_generales.rif"
                        )
                    }
                    return@launch
                }
            }

            if (cartItems.isEmpty()) {
                _state.update { it.copy(isProcessingPayment = false, paymentError = "No hay items en el carrito") }
                return@launch
            }

            if (selectedClient == null) {
                _state.update { it.copy(isProcessingPayment = false, paymentError = "Debes seleccionar un cliente") }
                return@launch
            }

            if (activeCaja == null) {
                _state.update { it.copy(isProcessingPayment = false, paymentError = "Debes seleccionar una caja") }
                return@launch
            }

            val cajaStatus = cajaRepository.checkCajaStatus(activeCaja.idCaja).getOrElse { error ->
                val backendMessage = error.message?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        paymentError = backendMessage
                            ?: "No se pudo validar el estado de la caja. Intenta nuevamente"
                    )
                }
                return@launch
            }

            val cajaSecuencia = cajaStatus.cajaSecuencia
            val idCajaSecuencia = cajaSecuencia?.idCajaSecuencia
            if (idCajaSecuencia.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        paymentError = "La caja no esta abierta o no tiene secuencia activa"
                    )
                }
                return@launch
            }

            val itemWarehouse = activeCaja.defaultWarehouseId
                ?: activeCaja.codAlmacen?.takeIf { it > 0 }
                ?: 0
            val invalidItems = cartItems.filter { it.product.id.toIntOrNull() == null }
            if (invalidItems.isNotEmpty()) {
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        paymentError = "Hay items manuales/no sincronizados que no se pueden facturar aun"
                    )
                }
                return@launch
            }

            val authUser = localStore.readAuthSnapshot()?.user
            val usuario = authUser?.username ?: "POS"
            val currentSellerId = cartRepository.currentSeller.value?.id?.takeIf { it > 0 }
                ?: cartRepository.availableSellers.value.firstOrNull()?.id
                ?: 1
            val currencyConfig = activeCaja.currency
            val isMultiCurrency = currencyConfig?.multiMoneda.equals("SI", ignoreCase = true)
            val currentRate = if (isMultiCurrency) {
                currencyConfig?.tasa?.takeIf { it > 0.0 } ?: 1.0
            } else {
                1.0
            }
            val currentIdTasa = if (isMultiCurrency) {
                currencyConfig?.idTasa ?: 0
            } else {
                0
            }
            val defaultTaxRate = activeCaja.defaultTaxRate?.takeIf { it > 0.0 } ?: 0.0

            val mappedItems = cartItems.map { cartItem ->
                val itemId = cartItem.product.id.toInt()
                val qty = cartItem.quantity.toDouble()
                val unitConIva = cartItem.unitPriceWithTax
                val taxRate = if (cartItem.product.isExempt) {
                    0.0
                } else {
                    cartItem.product.taxRate.takeIf { it > 0.0 } ?: defaultTaxRate
                }
                val divisor = 1.0 + (taxRate / 100.0)
                val unitSinIva = if (taxRate <= 0.0) unitConIva else unitConIva / divisor
                val subtotalSinIva = unitSinIva * qty
                val discountPct = cartItem.discountPercent.coerceIn(0.0, 100.0)
                val discountAmountSinIva = subtotalSinIva * (discountPct / 100.0)
                val totalSinIva = (subtotalSinIva - discountAmountSinIva).coerceAtLeast(0.0)
                val totalConIva = if (taxRate <= 0.0) totalSinIva else totalSinIva * divisor
                val lineSellerId = cartItem.codVendedor.takeIf { it > 0 } ?: currentSellerId

                SaleItemDto(
                    idItem = itemId,
                    codVendedor = lineSellerId,
                    itemAlmacen = itemWarehouse,
                    itemDescripcion = cartItem.product.description,
                    itemCantidad = qty,
                    itemPrecioSinIva = unitSinIva,
                    itemDescuento = discountPct,
                    itemMontoDescuento = discountAmountSinIva,
                    itemPIva = taxRate,
                    itemTotalSinIva = totalSinIva,
                    itemTotalConIva = totalConIva,
                    itemCantidadTotal = qty,
                    esProductoFisico = true,
                    itemCodigo = cartItem.product.code,
                    itemReferencia = cartItem.product.reference,
                    poseeConfiguracionLote = if (cartItem.hasLotConfig) "si" else "no",
                    codigosLote = cartItem.lotAssignments.map { lot ->
                        com.amaxonia.pos.domain.model.sales.SaleLotDto(
                            idLoteItem = lot.idLoteItem.toIntOrNull() ?: 0,
                            codigoLoteItem = lot.codigoLote,
                            cantidad = lot.cantidad,
                            idAlmacen = lot.almacen
                        )
                    }
                )
            }

            val subtotalBruto = mappedItems.sumOf { it.itemPrecioSinIva * it.itemCantidadTotal }
            val totalDescuentoItems = mappedItems.sumOf { it.itemMontoDescuento }
            val subtotalNeto = mappedItems.sumOf { it.itemTotalSinIva }
            val totalGeneral = mappedItems.sumOf { it.itemTotalConIva }
            val totalIva = totalGeneral - subtotalNeto

            val taxLines = mappedItems
                .groupBy { it.itemPIva }
                .filterKeys { it > 0.0 }
                .map { (_, lines) ->
                    val base = lines.sumOf { it.itemTotalSinIva }
                    val iva = lines.sumOf { it.itemTotalConIva - it.itemTotalSinIva }
                    SaleTaxDto(
                        totalizarBaseRetencion = base,
                        codImpuestoIva = 1,
                        totalizarMontoIva2 = iva
                    )
                }

            val payments = formapagoDetalle.detalle.map { det ->
                val tipo = det.sigla.uppercase().ifBlank { "OT" }
                val isCash = tipo == "CASH"
                SalePaymentDto(
                    idFormaPago = det.idFormaPago,
                    tipoMovimiento = tipo,
                    monto = det.monto,
                    montoRecibido = if (isCash) Money.toDouble(currentState.tenderedAmountMoney) else det.monto,
                    efectivoCambio = if (isCash) currentState.changeDue else 0.0
                )
            }

            val gatewayResult = processGatewayPaymentsIfNeeded(
                paymentMethods = selectedPaymentMethods,
                selectedClient = selectedClient
            )
            if (gatewayResult.isFailure) {
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        gatewayStatusMessage = null,
                        paymentError = gatewayResult.exceptionOrNull()?.message
                            ?: "No se pudo completar el cobro en The Factory"
                    )
                }
                return@launch
            }

            _state.update { it.copy(gatewayStatusMessage = "Generando factura...") }

            val montosPorTipo = payments.groupBy { it.tipoMovimiento }
                .mapValues { (_, list) -> list.sumOf { it.monto } }

            val saleRequest = ProcessSaleRequestDto(
                procesar = 1,
                esCobroCreditoPrevio = false,
                factura = SaleInvoiceDto(
                    idCliente = selectedClient.id,
                    codCliente = selectedClient.code.ifBlank { selectedClient.id },
                    codVendedor = currentSellerId,
                    idShop = activeCaja.idSucursal ?: 1,
                    idSucursal = activeCaja.idSucursal ?: 1,
                    idCaja = activeCaja.idCaja,
                    codigoCaja = activeCaja.codCaja.orEmpty(),
                    idCajaSecuencia = idCajaSecuencia,
                    serieSucursal = cajaSecuencia.serieSucursal,
                    formaPago = "contado",
                    codEstatus = 2,
                    subtotal = subtotalBruto,
                    descuentosItemFactura = totalDescuentoItems,
                    ivaTotalFactura = totalIva,
                    totalTotalFactura = totalGeneral,
                    montoItemsFactura = subtotalNeto,
                    totalizarSubTotal = subtotalBruto,
                    totalizarDescuentoParcial = totalDescuentoItems,
                    totalizarTotalOperacion = subtotalNeto,
                    totalizarPDescuentoGlobal = 0.0,
                    totalizarDescuentoGlobal = 0.0,
                    totalizarBaseImponible = subtotalNeto,
                    totalizarMontoIva = totalIva,
                    totalizarTotalGeneral = totalGeneral,
                    usuarioCreacion = usuario,
                    facturarA = "${selectedClient.firstName} ${selectedClient.lastName}".trim().ifBlank { "CONSUMIDOR FINAL" },
                    facturarARuc = selectedClient.ruc.ifBlank { selectedClient.cedula.ifBlank { "CF" } },
                    facturarADireccion = selectedClient.addressDetail,
                    facturarATelefono = selectedClient.phone,
                    impresoraSerial = ""
                ),
                items = mappedItems,
                impuestos = taxLines,
                pagoResumen = SalePaymentSummaryDto(
                    totalizarMontoCancelar = totalGeneral,
                    totalizarMontoEfectivo = payments.filter { it.tipoMovimiento == "CASH" }.sumOf { it.montoRecibido },
                    totalizarCambio = currentState.changeDue,
                    totalizarSaldoPendiente = 0.0,
                    montosPorTipo = montosPorTipo
                ),
                pagos = payments,
                moneda = SaleCurrencyDto(
                    multiMoneda = if (isMultiCurrency) "SI" else "NO",
                    tasa = currentRate,
                    idTasa = currentIdTasa,
                    monedaBase = currencyConfig?.monedaBase ?: 1,
                    abrMonedaBase = currencyConfig?.abrMonedaBase ?: "USD",
                    monedaSecundaria = currencyConfig?.monedaSecundaria ?: 1,
                    abrMonedaSecundaria = currencyConfig?.abrMonedaSecundaria ?: "USD",
                    totalRef = totalGeneral,
                )
            )

            val saleResult = salesRepository.processSale(saleRequest)
            if (saleResult.isFailure) {
                val error = saleResult.exceptionOrNull()
                val backendMessage = error?.message?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        gatewayStatusMessage = null,
                        paymentError = backendMessage
                            ?: "No se pudo procesar la venta. Intenta nuevamente"
                    )
                }
                return@launch
            }

            val response = saleResult.getOrThrow()
            val formatter = DateTimeFormatter.ofPattern("hh:mm a")
            val dateHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
            val now = LocalDateTime.now()

            val methods = payments
                .mapNotNull { payment ->
                    currentState.formasPago.firstOrNull { it.idFormaPago == payment.idFormaPago }
                        ?.descripcion
                        ?.takeIf { it.isNotBlank() }
                        ?: payment.tipoMovimiento
                }
                .distinct()
                .joinToString(" + ")

            val newTransaction = Transaction(
                id = UUID.randomUUID().toString(),
                invoiceNumber = response.codFactura,
                time = now.format(formatter),
                amount = Money.toDouble(currentState.totalAmountMoney),
                currency = "USD",
                status = TransactionStatus.PAID,
                dateHeader = now.format(dateHeaderFormatter),
                clienteNombre = "${selectedClient.firstName} ${selectedClient.lastName}".trim(),
                clienteIdentificacion = selectedClient.ruc.ifBlank { selectedClient.cedula },
                formaPago = methods,
                paymentMethods = selectedPaymentMethods
            )

            transactionRepository.saveTransaction(newTransaction).onFailure { saveError ->
                _state.update {
                    it.copy(
                        isProcessingPayment = false,
                        paymentError = saveError.message
                            ?: "La venta se proceso, pero no se pudo guardar la transaccion local"
                    )
                }
                return@launch
            }

            _state.update { it.copy(gatewayStatusMessage = "Imprimiendo factura...") }
            val printFeedback = printReceiptIfConfigured(newTransaction)

            _state.update {
                it.copy(
                    isSuccess = true,
                    isProcessingPayment = false,
                    paymentError = null,
                    gatewayStatusMessage = null,
                    receiptPrintMessage = printFeedback
                )
            }

            onSuccess(
                PaymentSuccessPayload(
                    changeDue = currentState.changeDue,
                    paymentMethodsLabel = methods,
                    codFactura = response.codFactura,
                    transactionId = response.idFactura,
                    receiptPrintMessage = printFeedback
                )
            )
        }
    }

    fun clearPaymentError() {
        _state.update { it.copy(paymentError = null) }
    }

    fun clearReceiptPrintMessage() {
        _state.update { it.copy(receiptPrintMessage = null) }
    }

    private suspend fun printReceiptIfConfigured(transaction: Transaction): String? {
        val activePrinter = printerFactory.getActivePrinter() ?: return null
        return activePrinter.printReceipt(transaction).fold(
            onSuccess = { isStarted ->
                if (isStarted) {
                    "Imprimiendo recibo..."
                } else {
                    "No se pudo iniciar la impresion del recibo"
                }
            },
            onFailure = { throwable ->
                throwable.message ?: "No se pudo imprimir el recibo"
            }
        )
    }

    private fun buildFormapagoDetalle(state: PaymentState): FormapagoDetallePayload {
        val detalle = when (state.selectedMethod) {
            PaymentMethod.CASH -> {
                val efectivoForma = state.formasPagoEfectivo.firstOrNull()
                if (efectivoForma == null) {
                    emptyList()
                } else {
                    listOf(
                        FormaPagoDetalle(
                            idFormaPago = efectivoForma.idFormaPago,
                            sigla = efectivoForma.siglas ?: "",
                            monto = Money.toDouble(state.totalAmountMoney),
                            idCajaTpConcepto = efectivoForma.idCajaTpConcepto,
                            idBancoCuenta = efectivoForma.idBancoCuenta,
                            idBancoOperacion = efectivoForma.idBancoOperacion
                        )
                    )
                }
            }

            PaymentMethod.NON_CASH -> {
                state.formasPagoTarjetaOtro.mapNotNull { forma ->
                    val monto = Money.parse(state.nonCashAmountsInput[forma.idFormaPago])
                    if (monto <= BigDecimal.ZERO) {
                        null
                    } else {
                        FormaPagoDetalle(
                            idFormaPago = forma.idFormaPago,
                            sigla = forma.siglas ?: "",
                            monto = Money.toDouble(monto),
                            idCajaTpConcepto = forma.idCajaTpConcepto,
                            idBancoCuenta = forma.idBancoCuenta,
                            idBancoOperacion = forma.idBancoOperacion
                        )
                    }
                }
            }
        }

        val totalEfectivo = detalle
            .filter { it.sigla.equals("CASH", ignoreCase = true) }
            .fold(BigDecimal.ZERO) { acc, item -> acc + Money.fromDouble(item.monto) }

        val totalCredito = detalle
            .filter {
                it.sigla.equals("CRED", ignoreCase = true) ||
                    it.sigla.equals("CXC", ignoreCase = true)
            }
            .fold(BigDecimal.ZERO) { acc, item -> acc + Money.fromDouble(item.monto) }

        val totalDetalle = detalle.fold(BigDecimal.ZERO) { acc, item -> acc + Money.fromDouble(item.monto) }
        val totalOtros = (totalDetalle - totalEfectivo - totalCredito).coerceAtLeast(BigDecimal.ZERO)

        return FormapagoDetallePayload(
            totalizarMontoEfectivo = Money.toDouble(totalEfectivo),
            totalizarMontoCredito = Money.toDouble(totalCredito),
            totalizarMontoOtros = Money.toDouble(totalOtros),
            detalle = detalle
        )
    }

    private fun buildSelectedPaymentMethods(
        state: PaymentState,
        detalle: List<FormaPagoDetalle>
    ): List<TransactionPaymentMethod> {
        return detalle.mapNotNull { item ->
            val forma = state.formasPago.firstOrNull { it.idFormaPago == item.idFormaPago } ?: return@mapNotNull null
            TransactionPaymentMethod(
                description = forma.descripcion.orEmpty(),
                sigla = forma.siglas.orEmpty(),
                amount = item.monto,
                fiscalCode = resolveFiscalPaymentCode(forma),
                gatewayCommandPrefix = resolveGatewayPaymentPrefix(forma)
            )
        }
    }

    /**
     * Processes gateway (Rapid Pay) payments via Android Intent to the HKA POS app.
     *
     * For each payment method that requires Rapid Pay:
     * 1. Builds the encrypted Intent via TheFactoryRapidPayClient
     * 2. Emits the Intent via gatewayIntentEvent (UI launches it with startActivity)
     * 3. Suspends via RapidPayBridge.awaitResult() until onNewIntent delivers the result
     * 4. If rejected, returns failure immediately
     */
    private suspend fun processGatewayPaymentsIfNeeded(
        paymentMethods: List<TransactionPaymentMethod>,
        selectedClient: com.amaxonia.pos.domain.model.Client
    ): Result<Unit> {
        if (!isHka20FlowEnabled()) {
            Log.d(TAG, "processGatewayPaymentsIfNeeded() → flujo no HKA20, omitiendo gateway")
            return Result.success(Unit)
        }

        val gatewayMethods = paymentMethods.filter(::requiresRapidPay)
        if (gatewayMethods.isEmpty()) {
            Log.d(TAG, "processGatewayPaymentsIfNeeded() → no hay metodos que requieran gateway")
            return Result.success(Unit)
        }

        for (method in gatewayMethods) {
            Log.d(TAG, "processGatewayPaymentsIfNeeded() → procesando ${method.description} | monto=${method.amount} | prefix=${method.gatewayCommandPrefix}")

            // Step 1: Build the Intent
            val intentResult = rapidPayClient.buildGatewayIntent(
                amount = method.amount,
                commandPrefix = method.gatewayCommandPrefix,
                customerIdentifier = selectedClient.ruc.ifBlank { selectedClient.cedula.ifBlank { selectedClient.id } },
                commerceRif = configuredCommerceRif
            )

            if (intentResult.isFailure) {
                val error = intentResult.exceptionOrNull()?.message ?: "Error al preparar la pasarela de pago"
                Log.e(TAG, "processGatewayPaymentsIfNeeded() → error construyendo intent: $error")
                return Result.failure(IllegalStateException(error))
            }

            val intent = intentResult.getOrThrow()

            // Step 2: Update UI state to show gateway status
            _state.update { it.copy(gatewayStatusMessage = "Esperando respuesta de pasarela de pago...") }

            // Step 3: Emit the Intent for the UI to launch
            Log.d(TAG, "processGatewayPaymentsIfNeeded() → emitiendo intent para UI")
            _gatewayIntentEvent.emit(intent)

            // Step 4: Suspend and wait for the result from onNewIntent via RapidPayBridge
            val result = RapidPayBridge.awaitResult()
            Log.d(TAG, "processGatewayPaymentsIfNeeded() → resultado: approved=${result.approved} | message=${result.message}")

            // Step 5: Clear gateway status
            _state.update { it.copy(gatewayStatusMessage = null) }

            if (!result.approved) {
                return Result.failure(IllegalStateException(result.message))
            }
        }

        return Result.success(Unit)
    }

    private fun requiresRapidPay(method: TransactionPaymentMethod): Boolean {
        return method.gatewayCommandPrefix.isNotBlank()
    }

    private fun resolveGatewayPaymentPrefix(forma: FormaPago): String {
        val gatewayKey = configuredGatewayKey.takeIf { it.isNotBlank() } ?: return ""
        if (!requiresRapidPayForForma(forma)) return ""
        val normalized = listOf(
            forma.descripcion.orEmpty(),
            forma.siglas.orEmpty(),
            forma.codigo.orEmpty()
        ).joinToString(" ").lowercase()

        return when {
            normalized.contains("punto de venta") -> "K${gatewayKey}V"
            normalized.contains("debito") || normalized == "pv" || normalized.contains(" tdc") || normalized.startsWith("tdc") -> "K${gatewayKey}V"
            normalized.contains("credito") -> "K${gatewayKey}V"
            else -> ""
        }
    }

    private fun requiresRapidPayForForma(forma: FormaPago): Boolean {
        val normalized = listOf(
            forma.descripcion.orEmpty(),
            forma.siglas.orEmpty(),
            forma.codigo.orEmpty()
        ).joinToString(" ").lowercase()

        return normalized.contains("punto de venta") ||
            normalized.contains("debito") ||
            normalized == "pv" ||
            normalized.contains(" tdc") ||
            normalized.startsWith("tdc") ||
            normalized.contains("credito")
    }

    private suspend fun isHka20FlowEnabled(): Boolean {
        val selectedPrinterType = localStore.readSelectedPrinterType()
        if (selectedPrinterType != PrinterType.THE_FACTORY_HKA) return false
        val mode = localStore.readTheFactorySettings().openMode.trim()
        return mode.isBlank() || mode.equals("HKA20", ignoreCase = true)
    }

    private fun resolveFiscalPaymentCode(forma: FormaPago): String {
        forma.formaPagoFact
            ?.trim()
            ?.takeIf { it in setOf("101", "102", "103", "104", "199") }
            ?.let { return it }

        val normalized = listOf(
            forma.descripcion.orEmpty(),
            forma.siglas.orEmpty(),
            forma.codigo.orEmpty()
        ).joinToString(" ").lowercase()

        return when {
            normalized.contains("punto de venta") -> "102"
            normalized.contains("debito") || normalized == "pv" || normalized.contains(" tdc") || normalized.startsWith("tdc") -> "102"
            normalized.contains("credito") -> "103"
            normalized.contains("efectivo") || normalized.contains("cash") || normalized.contains("divisa") -> "101"
            normalized.contains("transfer") || normalized.contains("deposit") || normalized.contains("cheque") ||
                normalized.contains("zelle") || normalized.contains("pago movil") || normalized.contains("yappy") ||
                normalized.contains("nequi") || normalized.contains("solutech") || normalized.contains("sunmi") ||
                normalized.contains("retencion") || normalized.contains("puntos") || normalized.contains("anticipo") -> "104"
            else -> "199"
        }
    }
}
