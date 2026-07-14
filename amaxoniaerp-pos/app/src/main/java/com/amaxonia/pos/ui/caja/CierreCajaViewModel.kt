package com.amaxonia.pos.ui.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.domain.model.caja.CashCloseInventoryLine
import com.amaxonia.pos.domain.model.caja.CashClosePromotionLine
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.PendingSalesReader
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintOutcome
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.FiscalReportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class CierreCajaViewModel(
    private val cajaRepository: CajaRepository,
    private val cashClosePrinting: CashClosePrintingService,
    private val contextReader: CashCloseContextReader,
    private val productRepository: ProductCatalogReader,
    private val pendingSalesReader: PendingSalesReader,
    private val cashCloseTicketFormatter: CashCloseTicketFormatter,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CierreCajaUiState>(CierreCajaUiState.Loading)
    val uiState: StateFlow<CierreCajaUiState> = _uiState.asStateFlow()

    private val _isPrintingReportX = MutableStateFlow(false)
    val isPrintingReportX: StateFlow<Boolean> = _isPrintingReportX.asStateFlow()

    private val _isPrintingReportZ = MutableStateFlow(false)
    val isPrintingReportZ: StateFlow<Boolean> = _isPrintingReportZ.asStateFlow()

    private val _reportMessage = MutableStateFlow<String?>(null)
    val reportMessage: StateFlow<String?> = _reportMessage.asStateFlow()

    private val _showCloseTicketPrompt = MutableStateFlow(false)
    val showCloseTicketPrompt: StateFlow<Boolean> = _showCloseTicketPrompt.asStateFlow()

    val hasActivePrinter: Boolean
        get() = cashClosePrinting.hasActiveFiscalPrinter()

    init {
        loadSummary()
    }

    fun loadSummary() {
        viewModelScope.launch {
            _uiState.value = CierreCajaUiState.Loading
            cajaRepository.getCierreSummary().fold(
                onSuccess = { summary ->
                    _uiState.value = CierreCajaUiState.Ready(summary)
                },
                onFailure = { error ->
                    _uiState.value =
                        CierreCajaUiState.Error(
                            message = error.message ?: "No se pudo cargar el resumen de caja",
                        )
                },
            )
        }
    }

    fun printReportX() {
        printReport(FiscalReportType.X, _isPrintingReportX)
    }

    fun printReportZ() {
        printReport(FiscalReportType.Z, _isPrintingReportZ)
    }

    private fun printReport(
        type: FiscalReportType,
        printingState: MutableStateFlow<Boolean>,
    ) {
        if (!cashClosePrinting.hasActiveFiscalPrinter()) return
        viewModelScope.launch {
            printingState.value = true
            _reportMessage.value = null
            cashClosePrinting.printReport(type).messageOrNull()?.let { _reportMessage.value = it }
            printingState.value = false
        }
    }

    fun clearReportMessage() {
        _reportMessage.value = null
    }

    fun requestClose() {
        viewModelScope.launch {
            if (shouldAskForPanamaSunmiCloseTicket()) {
                _showCloseTicketPrompt.value = true
            } else {
                confirmClose(printTicket = false)
            }
        }
    }

    fun dismissCloseTicketPrompt() {
        _showCloseTicketPrompt.value = false
    }

    fun confirmClose(printTicket: Boolean) {
        _showCloseTicketPrompt.value = false
        val currentState = _uiState.value
        val summary =
            when (currentState) {
                is CierreCajaUiState.Ready -> currentState.summary
                is CierreCajaUiState.Error -> currentState.summary ?: return
                else -> return
            }

        viewModelScope.launch {
            _uiState.value = CierreCajaUiState.Closing(summary)

            cajaRepository.activeCaja.value ?: run {
                _uiState.value =
                    CierreCajaUiState.Error(
                        message = "No hay caja activa para cerrar",
                        summary = summary,
                    )
                return@launch
            }

            val request =
                CierreCajaRequest(
                    id = summary.idCajaSecuencia,
                    monto_efectivo_ventas = summary.montoEfectivoVentas,
                    monto_efectivo_entrada = summary.montoEfectivoEntrada,
                    monto_efectivo_salida = summary.montoEfectivoSalida,
                    monto_efectivo_total = summary.montoEfectivoTotal,
                    monto_efectivo_cierre = summary.montoEfectivoCierre,
                    monto_efectivo_diferencia = summary.montoEfectivoDiferencia,
                    monto_otros_total = summary.montoOtrosTotal,
                    monto_otros_cierre = summary.montoOtrosCierre,
                    monto_otros_diferencia = summary.montoOtrosDiferencia,
                    monto_total = summary.montoTotal,
                    monto_cierre = summary.montoCierre,
                    monto_diferencia = summary.montoDiferencia,
                    detalle = summary.detalle,
                    detalle_formapago = summary.detalleFormaPago,
                    observacion_cierre = "",
                    numero_cierre_fiscal = "",
                )

            cajaRepository.closeCaja(request).fold(
                onSuccess = { response ->
                    if (printTicket) {
                        printPanamaCloseTicket(summary)
                    }
                    cajaRepository.clearActiveCaja()
                    _uiState.value =
                        CierreCajaUiState.Success(
                            message = response.message,
                        )
                },
                onFailure = { error ->
                    _uiState.value =
                        CierreCajaUiState.Error(
                            message = error.message ?: "Error al cerrar la caja",
                            summary = summary,
                        )
                },
            )
        }
    }

    private suspend fun shouldAskForPanamaSunmiCloseTicket(): Boolean = cashClosePrinting.shouldOfferCloseTicket()

    private suspend fun printPanamaCloseTicket(summary: CierreCajaSummary) {
        val payload = buildPanamaCloseTicketPayload(summary)
        cashClosePrinting.printCloseTicket(payload).messageOrNull()?.let { _reportMessage.value = it }
    }

    private suspend fun buildPanamaCloseTicketPayload(summary: CierreCajaSummary): CashCloseTicketPayload {
        val company = contextReader.currentCompany()
        val caja = cajaRepository.activeCaja.value
        val branchName = caja?.sucursalNombre?.takeIf { it.isNotBlank() } ?: "Sucursal"
        val paymentAmounts = buildPaymentAmounts(summary)
        val todaySales = readTodayLocalSales()
        val inventoryLines = buildInventoryLines()
        val promotionLines = buildPromotionLines(todaySales)
        val qrPayload =
            listOf(
                "tipo=CIERRE_CAJA",
                "ruc=${company?.rif.orEmpty()}",
                "empresa=${company?.name.orEmpty()}",
                "secuencia=${summary.idCajaSecuencia}",
                "caja=${summary.idCaja}",
                "total=${formatQrAmount(summary.montoCierre)}",
                "ventas=${formatQrAmount(summary.totalSales)}",
                "transacciones=${summary.transactionCount}",
                "vendedor=${summary.vendedorName}",
                "sucursal=$branchName",
            ).joinToString("|")

        return CashCloseTicketPayload(
            companyName = company?.name?.takeIf { it.isNotBlank() } ?: "EMPRESA",
            companyRuc = company?.rif.orEmpty(),
            companyAddress = branchName,
            companyPhone = "",
            sellerName = summary.vendedorName,
            cashRegisterName = summary.cajaName,
            branchName = branchName,
            summary = summary,
            paymentAmounts = paymentAmounts,
            generalPayments = 0.0,
            promotionLines = promotionLines,
            inventoryLines = inventoryLines,
            qrPayload = qrPayload,
        )
    }

    private fun buildPaymentAmounts(summary: CierreCajaSummary): Map<String, Double> {
        val amounts = cashCloseTicketFormatter.paymentLabels.associateWith { 0.0 }.toMutableMap()
        summary.paymentLines.forEach { line ->
            val label = normalizePaymentLabel(line.label, line.siglas)
            amounts[label] = amounts.getValue(label) + line.amount
        }
        if (summary.paymentLines.isEmpty()) {
            amounts["EFECTIVO"] = summary.totalCash
            amounts["TARJETA DE DEBITO"] = summary.totalCard
        }
        return amounts
    }

    private fun normalizePaymentLabel(
        label: String,
        siglas: String,
    ): String {
        val value =
            "$siglas $label"
                .uppercase(Locale.ROOT)
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("Á", "A")
        return when {
            value.contains("YAPPY") -> "YAPPY"
            value.contains("MASTER") -> "MASTERCARD"
            value.contains("VISA") -> "VISA"
            value.contains("ACH") || value.contains("IBAN") -> "ACH / IBAN"
            value.contains("REFER") -> "REFERENCIA"
            value.contains("DEPOS") -> "DEPOSITO"
            value.contains("CUENTA") || value.contains("COBRAR") || value.contains("CXC") -> "CUENTAS POR COBRAR"
            value.contains("NOTA") || value.contains("DEVOL") -> "NOTAS DE CREDITO / DEVOLUCIONES"
            value.contains("CERTIFIC") || value.contains("REGALO") -> "CERTIFICADO DE REGALO"
            value.contains("PUNTO") -> "PUNTOS"
            value.contains("DEBIT") || value.contains("TDD") -> "TARJETA DE DEBITO"
            value.contains("ABONO") -> "ABONOS APLICADOS"
            value.contains("CASH") || value.contains("EFECT") || value == "EF" || value == "EFE" -> "EFECTIVO"
            else -> "REFERENCIA"
        }
    }

    private suspend fun buildInventoryLines(): List<CashCloseInventoryLine> {
        val soldByProduct =
            readTodayLocalSales()
                .flatMap { it.items }
                .groupBy { it.idItem.toString() }
                .mapValues { (_, items) -> items.sumOf { it.itemCantidadTotal } }

        return soldByProduct
            .mapNotNull { (productId, soldQuantity) ->
                val product = productRepository.getProductById(productId).getOrNull()
                val realQuantity =
                    productRepository.getProductStock(productId).getOrNull()?.stockTotalDisponible
                        ?: return@mapNotNull null
                val initialQuantity = realQuantity + soldQuantity
                CashCloseInventoryLine(
                    productCode = product?.code?.takeIf { it.isNotBlank() } ?: productId,
                    productName = product?.description?.takeIf { it.isNotBlank() } ?: "Producto $productId",
                    initialQuantity = initialQuantity,
                    soldQuantity = soldQuantity,
                    realQuantity = initialQuantity - soldQuantity,
                )
            }.sortedBy { it.productName }
    }

    private fun buildPromotionLines(sales: List<ProcessSaleRequestDto>): List<CashClosePromotionLine> =
        sales
            .flatMap { it.items }
            .filter { it.promocionId.isNotBlank() }
            .groupBy { it.promocionId }
            .map { (promotionId, items) ->
                val first = items.first()
                val soldTimes =
                    items
                        .groupBy { it.promocionDetalleId.ifBlank { it.idItem.toString() } }
                        .values
                        .firstOrNull()
                        ?.sumOf { item ->
                            item.promocionCantidad.takeIf { it > 0.0 } ?: 1.0
                        }
                        ?: 0.0
                CashClosePromotionLine(
                    promotionCode = first.promocionCodigo.ifBlank { promotionId },
                    promotionName = first.promocionNombre.ifBlank { first.promocionCodigo.ifBlank { "Promocion $promotionId" } },
                    soldTimes = soldTimes,
                )
            }.filter { it.soldTimes > 0.0 }
            .sortedBy { it.promotionName }

    private suspend fun readTodayLocalSales(): List<ProcessSaleRequestDto> {
        val zone = ZoneId.systemDefault()
        val start =
            LocalDate
                .now(zone)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val end =
            LocalDate
                .now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli() - 1
        return pendingSalesReader.createdBetween(start, end)
    }

    private fun formatQrAmount(value: Double): String = String.format(Locale.US, "%.2f", value)
}

private fun CashClosePrintOutcome.messageOrNull(): String? =
    when (this) {
        CashClosePrintOutcome.NoPrinter -> null
        is CashClosePrintOutcome.Message -> value
    }
