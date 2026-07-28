package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.ClientBranch
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientBranchRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.PaymentSessionReader
import com.amaxonia.pos.domain.repository.SalesRepository
import com.amaxonia.pos.domain.repository.TransactionRepository

class PaymentStateRepositories(
    val transaction: TransactionRepository,
    val caja: CajaRepository,
    val cart: CartRepository,
    val clientBranches: ClientBranchRepository,
)

class PaymentRuntimeServices(
    val sales: SalesRepository,
    val session: PaymentSessionReader,
    val connectivity: ConnectivityStatus,
)

class PaymentFlowRepositories(
    val state: PaymentStateRepositories,
    val runtime: PaymentRuntimeServices,
)

class PaymentPreparationOperations(
    val validatePayment: ValidatePaymentUseCase,
    val calculateSaleTotals: CalculateSaleTotalsUseCase,
    val buildSaleItems: BuildSaleItemsUseCase,
    val buildSaleRequest: BuildSaleRequestUseCase,
)

internal sealed interface SalePreparation {
    data class Success(
        val sale: PreparedSale,
    ) : SalePreparation

    data class Failure(
        val message: String,
    ) : SalePreparation
}

internal data class PreparedSale(
    val isOnline: Boolean,
    val client: Client,
    val request: ProcessSaleRequestDto,
    val details: PreparedSaleDetails,
    val financials: PreparedSaleFinancials,
    /**
     * Optional correlation id carried over from a prior attempt of the same
     * operation (e.g. after a crash/reboot). When non-null and still in
     * SENDING state on the local ledger, StartTransactionUseCase will reuse
     * it so the backend dedup (HTTP 409) detects the retry. Defaults to null
     * for brand-new operations, where StartTransactionUseCase mints a fresh
     * UUID. Never re-derived from carrito contents.
     */
    val correlationCarryOver: String? = null,
) {
    /**
     * Returns a copy of this sale with the provided [clientCorrelationId]
     * stamped onto the request idFactura field, used after the local ledger
     * row has been opened.
     */
    fun withCorrelationId(clientCorrelationId: String?): PreparedSale =
        copy(
            request = request.copy(idFactura = clientCorrelationId),
        )
}

internal data class PreparedSaleDetails(
    val items: List<SaleItemDto>,
    val payments: List<SalePaymentDto>,
    val methodsLabel: String,
    val selectedMethods: List<TransactionPaymentMethod>,
)

internal data class PreparedSaleFinancials(
    val total: Double,
    val exchangeRate: Double,
    val isMultiCurrency: Boolean,
)

class PrepareSaleUseCase(
    private val repositories: PaymentFlowRepositories,
    private val operations: PaymentPreparationOperations,
    private val assembleSale: AssemblePreparedSaleUseCase,
) {
    internal suspend operator fun invoke(input: ExecutePaymentFlowInput): SalePreparation {
        val base = loadBaseContext(input)
        return when (base) {
            is PreparationStep.Failure -> SalePreparation.Failure(base.message)
            is PreparationStep.Success -> prepareWithBranch(input, base.value)
        }
    }

    private suspend fun prepareWithBranch(
        input: ExecutePaymentFlowInput,
        base: BaseSaleContext,
    ): SalePreparation =
        when (val branched = resolveClientBranch(input.countryCode, base)) {
            is PreparationStep.Failure -> SalePreparation.Failure(branched.message)
            is PreparationStep.Success -> prepareReadySale(input, branched.value)
        }

    private suspend fun prepareReadySale(
        input: ExecutePaymentFlowInput,
        context: BranchedSaleContext,
    ): SalePreparation =
        when (val ready = resolveSaleReadiness(input, context)) {
            is PreparationStep.Failure -> SalePreparation.Failure(ready.message)
            is PreparationStep.Success -> SalePreparation.Success(assembleSale(input, ready.value))
        }

    private fun loadBaseContext(input: ExecutePaymentFlowInput): PreparationStep<BaseSaleContext> {
        val cartItems = repositories.state.cart.cartItems.value
        val client = repositories.state.cart.selectedClient.value
        val caja = repositories.state.caja.activeCaja.value
        val itemCount = input.saleItemsOverride?.size ?: cartItems.size
        val validation = operations.validatePayment.validateSaleContext(itemCount, client != null, caja != null)
        return when {
            validation != null -> PreparationStep.Failure(validation.message)
            client == null || caja == null -> PreparationStep.Failure("No se pudo preparar la venta")
            else -> PreparationStep.Success(BaseSaleContext(cartItems, client, caja))
        }
    }

    private suspend fun resolveClientBranch(
        countryCode: String,
        base: BaseSaleContext,
    ): PreparationStep<BranchedSaleContext> {
        var selectedBranch = repositories.state.cart.selectedClientSucursal.value
        val branches =
            if (countryCode == PANAMA_CODE) {
                repositories.state.clientBranches.findFor(base.client).also { available ->
                    repositories.state.cart.setClientSucursales(available)
                    selectedBranch = repositories.state.cart.selectedClientSucursal.value
                }
            } else {
                emptyList()
            }
        val validation = operations.validatePayment.validateClientBranch(branches.size, selectedBranch != null)
        return if (validation == null) {
            PreparationStep.Success(BranchedSaleContext(base, selectedBranch))
        } else {
            PreparationStep.Failure(validation.message)
        }
    }

    private suspend fun resolveSaleReadiness(
        input: ExecutePaymentFlowInput,
        context: BranchedSaleContext,
    ): PreparationStep<ReadySaleContext> {
        val isOnline = repositories.runtime.connectivity.isOnline()
        if (!isOnline && input.cuentaMesa != null) {
            return PreparationStep.Failure("Las cuentas de mesa requieren conexión para confirmar saldos en forma atómica")
        }
        val sequenceResult =
            if (isOnline) {
                repositories.state.caja
                    .checkCajaStatus(context.base.caja.idCaja)
                    .map { it.cajaSecuencia }
            } else {
                Result.success(repositories.state.caja.activeCajaSecuencia.value)
            }
        return sequenceResult.fold(
            onFailure = { error ->
                PreparationStep.Failure(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "No se pudo validar el estado de la caja. Intenta nuevamente",
                )
            },
            onSuccess = { sequence -> validateSequence(context, isOnline, sequence) },
        )
    }

    private fun validateSequence(
        context: BranchedSaleContext,
        isOnline: Boolean,
        sequence: CajaSecuencia?,
    ): PreparationStep<ReadySaleContext> {
        val validation =
            operations.validatePayment.validateSaleReadiness(
                isOnline = isOnline,
                hasCajaSequence = !sequence?.idCajaSecuencia.isNullOrBlank(),
                invalidItemCount = context.base.cartItems.count { it.product.id.toIntOrNull() == null },
            )
        return if (validation == null) {
            PreparationStep.Success(ReadySaleContext(context, isOnline, sequence))
        } else {
            PreparationStep.Failure(validation.message)
        }
    }

    private sealed interface PreparationStep<out T> {
        data class Success<T>(
            val value: T,
        ) : PreparationStep<T>

        data class Failure(
            val message: String,
        ) : PreparationStep<Nothing>
    }
}

class AssemblePreparedSaleUseCase(
    private val repositories: PaymentFlowRepositories,
    private val operations: PaymentPreparationOperations,
) {
    internal suspend operator fun invoke(
        input: ExecutePaymentFlowInput,
        ready: ReadySaleContext,
    ): PreparedSale {
        val base = ready.branched.base
        val configuration = resolveConfiguration(base.caja)
        val items = input.saleItemsOverride ?: buildItems(base, configuration)
        val totals = operations.calculateSaleTotals(items)
        val payments = buildPayments(input)
        val request =
            operations.buildSaleRequest(
                BuildSaleRequestInput(
                    invoice = buildInvoice(input, ready, configuration, totals),
                    items = items,
                    taxes = totals.taxLines,
                    paymentSummary = buildPaymentSummary(input, totals, payments),
                    payments = payments,
                    currency = buildCurrency(base.caja, configuration, totals.total),
                ),
            )
        return PreparedSale(
            isOnline = ready.isOnline,
            client = base.client,
            request = request.copy(cuentaMesa = input.cuentaMesa),
            details =
                PreparedSaleDetails(
                    items = items,
                    payments = payments,
                    methodsLabel = paymentMethodsLabel(payments, input.availableMethods),
                    selectedMethods = input.paymentDetails.transactionMethods,
                ),
            financials = PreparedSaleFinancials(totals.total, configuration.rate, configuration.isMultiCurrency),
            // Propagate the durable carry-over id (auditoría ítem 1) so a
            // retry after a timeout/crash reuses the same idFactura and the
            // backend dedup kicks in. null for brand-new operations.
            correlationCarryOver = input.correlationCarryOver,
        )
    }

    private fun resolveConfiguration(caja: Caja): SaleConfiguration {
        val sellerId =
            repositories.state.cart.currentSeller.value
                ?.id
                ?.takeIf { it > 0 }
                ?: repositories.state.cart.availableSellers.value
                    .firstOrNull()
                    ?.id
                ?: DEFAULT_SELLER_ID
        val warehouseId = caja.defaultWarehouseId ?: caja.codAlmacen?.takeIf { it > 0 } ?: DEFAULT_WAREHOUSE_ID
        val isMultiCurrency = caja.currency?.multiMoneda.equals("SI", ignoreCase = true)
        val rate = if (isMultiCurrency) caja.currency?.tasa?.takeIf { it > 0.0 } ?: DEFAULT_RATE else DEFAULT_RATE
        val rateId = if (isMultiCurrency) caja.currency?.idTasa ?: 0 else 0
        return SaleConfiguration(sellerId, warehouseId, isMultiCurrency, rate, rateId)
    }

    private fun buildItems(
        base: BaseSaleContext,
        configuration: SaleConfiguration,
    ): List<SaleItemDto> =
        operations.buildSaleItems(
            BuildSaleItemsInput(
                cartItems = base.cartItems,
                warehouseId = configuration.warehouseId,
                sellerId = configuration.sellerId,
                defaultTaxRate = base.caja.defaultTaxRate?.takeIf { it > 0.0 } ?: 0.0,
            ),
        )

    private suspend fun buildInvoice(
        input: ExecutePaymentFlowInput,
        ready: ReadySaleContext,
        configuration: SaleConfiguration,
        totals: SaleTotals,
    ): SaleInvoiceDto {
        val base = ready.branched.base
        val client = base.client
        val caja = base.caja
        val branch = ready.branched.clientBranch
        return SaleInvoiceDto(
            idCliente = client.id,
            codCliente = client.code.ifBlank { client.id },
            codVendedor = configuration.sellerId,
            idShop = caja.idSucursal ?: DEFAULT_SHOP_ID,
            idSucursal = caja.idSucursal ?: DEFAULT_SHOP_ID,
            idCaja = caja.idCaja,
            codigoCaja = caja.codCaja.orEmpty(),
            idCajaSecuencia = ready.sequence?.idCajaSecuencia ?: "OFFLINE-${caja.idCaja}",
            serieSucursal = ready.sequence?.serieSucursal ?: caja.serieSucursal ?: caja.serieCaja,
            formaPago = "contado",
            codEstatus = PROCESSED_STATUS,
            subtotal = totals.subtotalGross,
            descuentosItemFactura = totals.itemDiscounts,
            ivaTotalFactura = totals.tax,
            totalTotalFactura = totals.total,
            montoItemsFactura = totals.subtotalNet,
            totalizarSubTotal = totals.subtotalGross,
            totalizarDescuentoParcial = totals.itemDiscounts,
            totalizarTotalOperacion = totals.subtotalNet,
            totalizarPDescuentoGlobal = 0.0,
            totalizarDescuentoGlobal = 0.0,
            totalizarBaseImponible = totals.subtotalNet,
            totalizarMontoIva = totals.tax,
            totalizarTotalGeneral = totals.total,
            usuarioCreacion = repositories.runtime.session.currentUsername(),
            facturarA = client.paymentDisplayName(),
            facturarARuc = client.ruc.ifBlank { client.cedula.ifBlank { "CF" } },
            facturarADireccion = branch?.direccion?.takeIf(String::isNotBlank) ?: client.addressDetail,
            facturarATelefono = branch?.telefonoContacto?.takeIf(String::isNotBlank) ?: client.phone,
            clienteSucursalId = branch?.sucursalId,
            nroz = if (input.countryCode == VENEZUELA_CODE) "0000" else "",
            impresoraSerial = "",
        )
    }

    private fun buildPaymentSummary(
        input: ExecutePaymentFlowInput,
        totals: SaleTotals,
        payments: List<SalePaymentDto>,
    ): SalePaymentSummaryDto =
        SalePaymentSummaryDto(
            totalizarMontoCancelar = totals.total,
            totalizarMontoEfectivo = payments.filter { it.tipoMovimiento == CASH_SIGLA }.sumOf { it.montoRecibido },
            totalizarCambio = input.changeDue,
            totalizarSaldoPendiente = 0.0,
            montosPorTipo = payments.groupBy { it.tipoMovimiento }.mapValues { (_, values) -> values.sumOf { it.monto } },
        )

    private fun buildCurrency(
        caja: Caja,
        configuration: SaleConfiguration,
        total: Double,
    ): SaleCurrencyDto =
        SaleCurrencyDto(
            multiMoneda = if (configuration.isMultiCurrency) "SI" else "NO",
            tasa = configuration.rate,
            idTasa = configuration.rateId,
            monedaBase = caja.currency?.monedaBase ?: DEFAULT_CURRENCY_ID,
            abrMonedaBase = caja.currency?.abrMonedaBase ?: DEFAULT_CURRENCY,
            monedaSecundaria = caja.currency?.monedaSecundaria ?: DEFAULT_CURRENCY_ID,
            abrMonedaSecundaria = caja.currency?.abrMonedaSecundaria ?: DEFAULT_CURRENCY,
            totalRef = total,
        )

    private fun buildPayments(input: ExecutePaymentFlowInput): List<SalePaymentDto> =
        input.paymentDetails.payload.detalle.map { detail ->
            val type = detail.sigla.uppercase().ifBlank { "OT" }
            val isCash = type == CASH_SIGLA
            SalePaymentDto(
                idFormaPago = detail.idFormaPago,
                tipoMovimiento = type,
                monto = detail.monto,
                montoRecibido = if (isCash) input.tenderedAmount.toDouble() else detail.monto,
                efectivoCambio = if (isCash) input.changeDue else 0.0,
            )
        }

    private fun paymentMethodsLabel(
        payments: List<SalePaymentDto>,
        methods: List<com.amaxonia.pos.domain.model.payment.FormaPago>,
    ): String =
        payments
            .map { payment ->
                methods.firstOrNull { it.idFormaPago == payment.idFormaPago }?.descripcion?.takeIf(String::isNotBlank)
                    ?: payment.tipoMovimiento
            }.distinct()
            .joinToString(" + ")
}

internal data class BaseSaleContext(
    val cartItems: List<CartItem>,
    val client: Client,
    val caja: Caja,
)

internal data class BranchedSaleContext(
    val base: BaseSaleContext,
    val clientBranch: ClientBranch?,
)

internal data class ReadySaleContext(
    val branched: BranchedSaleContext,
    val isOnline: Boolean,
    val sequence: CajaSecuencia?,
)

private data class SaleConfiguration(
    val sellerId: Int,
    val warehouseId: Int,
    val isMultiCurrency: Boolean,
    val rate: Double,
    val rateId: Int,
)

private fun Client.paymentDisplayName(): String = "$firstName $lastName".trim().ifBlank { "CONSUMIDOR FINAL" }

private const val PANAMA_CODE = "PA"
private const val VENEZUELA_CODE = "VE"
private const val CASH_SIGLA = "CASH"
private const val DEFAULT_CURRENCY = "USD"
private const val DEFAULT_CURRENCY_ID = 1
private const val DEFAULT_RATE = 1.0
private const val DEFAULT_SELLER_ID = 1
private const val DEFAULT_SHOP_ID = 1
private const val DEFAULT_WAREHOUSE_ID = 0
private const val PROCESSED_STATUS = 2
