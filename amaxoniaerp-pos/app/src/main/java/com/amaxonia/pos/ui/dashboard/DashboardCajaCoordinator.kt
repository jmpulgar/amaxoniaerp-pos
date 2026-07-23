package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintOutcome
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.CashCloseTicketPayloadBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardCajaCoordinator(
    private val cajaRepository: CajaRepository,
    private val cartRepository: CartRepository,
    private val cashClosePrinting: CashClosePrintingService,
    private val ticketPayloadBuilder: CashCloseTicketPayloadBuilder,
) : DashboardSaleGate {
    fun start(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        scope.launch {
            cajaRepository.activeCajaName.collect { cajaName ->
                state.update { it.copy(cajaPrincipalNombre = cajaName) }
            }
        }
        scope.launch {
            cajaRepository.activeCaja.collect { caja ->
                val branchName = caja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal"
                state.update { it.copy(sucursalNombre = branchName, hasActiveCaja = caja != null) }
                caja?.let {
                    cartRepository.setSellerContext(
                        defaultSellerId = it.defaultSellerId,
                        defaultSellerName = it.defaultSellerName,
                        sellers = it.availableSellers,
                    )
                }
            }
        }
        scope.launch {
            cajaRepository.restoreActiveCajaIfValid()
            fetch(scope, state)
        }
    }

    fun fetch(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        forceShowSelector: Boolean = false,
    ) {
        scope.launch {
            val keepSelectorVisible = forceShowSelector || state.value.showCajaSelector
            state.update { it.copy(isLoadingCajas = true, showCajaSelector = keepSelectorVisible) }
            cajaRepository.getCajas().fold(
                onSuccess = { cajas ->
                    val activeCaja = cajaRepository.activeCaja.value
                    val onlyAvailableCaja = cajas.singleOrNull()
                    if (!forceShowSelector && activeCaja == null && onlyAvailableCaja != null) {
                        cajaRepository.setActiveCaja(onlyAvailableCaja)
                    }
                    val shouldShowSelector = forceShowSelector || (activeCaja == null && onlyAvailableCaja == null)
                    state.update {
                        it.copy(
                            availableCajas = cajas,
                            isLoadingCajas = false,
                            showCajaSelector = shouldShowSelector,
                        )
                    }
                },
                onFailure = { error ->
                    val shouldShowSelector = keepSelectorVisible || cajaRepository.activeCaja.value == null
                    state.update {
                        it.copy(
                            isLoadingCajas = false,
                            showCajaSelector = shouldShowSelector,
                            error = "Error al cargar cajas: ${error.message}",
                        )
                    }
                },
            )
        }
    }

    fun selectAndOpen(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        caja: Caja,
        openingAmount: Double,
    ) {
        scope.launch {
            state.update { it.copy(isLoadingCajas = true) }
            val closedSequenceId =
                cajaRepository
                    .checkCajaStatus(caja.idCaja)
                    .getOrNull()
                    ?.takeIf { it.isOpen }
                    ?.cajaSecuencia
                    ?.idCajaSecuencia
            val sequence =
                cajaRepository.getNextSecuenciaCodigo(caja.idCaja).getOrElse { error ->
                    state.update {
                        it.copy(
                            isLoadingCajas = false,
                            error = "Error al obtener correlativo de caja: ${error.message}",
                        )
                    }
                    return@launch
                }
            val request =
                AperturaRequest(
                    idCaja = caja.idCaja,
                    montoApertura = openingAmount,
                    idVendedor = state.value.currentSeller?.id ?: caja.defaultSellerId,
                    secuencia = sequence,
                    serieSucursal = caja.serieSucursal ?: caja.serieCaja,
                    idSucursal = caja.idSucursal,
                    facturaInicial = 0,
                    notacreditoInicial = 0,
                    devolucionInicial = 0,
                    zInicial = 0,
                )
            cajaRepository.openCaja(request).fold(
                onSuccess = {
                    cajaRepository.setActiveCaja(caja)
                    val ticketOffer = closedSequenceId?.let { buildAutomaticCloseTicketOffer(caja, it) }
                    state.update {
                        it.copy(
                            isLoadingCajas = false,
                            showCajaSelector = false,
                            automaticCloseTicketOffer = ticketOffer,
                        )
                    }
                },
                onFailure = { error ->
                    state.update { it.copy(isLoadingCajas = false, error = "Error al abrir caja: ${error.message}") }
                },
            )
        }
    }

    private suspend fun buildAutomaticCloseTicketOffer(
        caja: Caja,
        sequenceId: String,
    ): AutomaticCloseTicketOffer =
        cajaRepository
            .getCierreSummaryForSequence(caja, sequenceId)
            .fold(
                onSuccess = { summary ->
                    runCatching { ticketPayloadBuilder.build(summary, caja) }
                        .fold(
                            onSuccess = { AutomaticCloseTicketOffer(payload = it) },
                            onFailure = {
                                AutomaticCloseTicketOffer(
                                    payload = null,
                                    unavailableReason = "No se pudo preparar el ticket: ${it.message}",
                                )
                            },
                        )
                },
                onFailure = {
                    AutomaticCloseTicketOffer(
                        payload = null,
                        unavailableReason = "No se pudo cargar el resumen del cierre automático: ${it.message}",
                    )
                },
            )

    private fun printAutomaticCloseTicket(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        val offer = state.value.automaticCloseTicketOffer ?: return
        val payload = offer.payload
        if (payload == null) {
            state.update {
                it.copy(
                    automaticCloseTicketOffer = null,
                    autoCloseMessage = offer.unavailableReason ?: "No se pudo preparar el ticket de cierre",
                )
            }
            return
        }
        scope.launch {
            state.update { it.copy(isPrintingAutomaticCloseTicket = true) }
            val message =
                when (val outcome = cashClosePrinting.printCloseTicket(payload)) {
                    CashClosePrintOutcome.NoPrinter -> "No hay una impresora disponible para el ticket de cierre"
                    is CashClosePrintOutcome.Message -> outcome.value
                }
            state.update {
                it.copy(
                    automaticCloseTicketOffer = null,
                    isPrintingAutomaticCloseTicket = false,
                    autoCloseMessage = message,
                )
            }
        }
    }

    fun setSelectorVisible(
        state: MutableStateFlow<DashboardState>,
        show: Boolean,
    ) {
        val shouldShow = show || cajaRepository.activeCaja.value == null
        state.update { it.copy(showCajaSelector = shouldShow, error = null) }
    }

    fun onAction(
        action: DashboardCajaUiAction,
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        when (action) {
            is DashboardCajaUiAction.Fetch -> fetch(scope, state, action.forceShowSelector)
            is DashboardCajaUiAction.SelectAndOpen -> selectAndOpen(scope, state, action.caja, action.openingAmount)
            is DashboardCajaUiAction.SetSelectorVisible -> setSelectorVisible(state, action.show)
            DashboardCajaUiAction.DismissAutoCloseMessage -> state.update { it.copy(autoCloseMessage = null) }
            DashboardCajaUiAction.PrintAutomaticCloseTicket -> printAutomaticCloseTicket(scope, state)
            DashboardCajaUiAction.DismissAutomaticCloseTicket ->
                state.update { it.copy(automaticCloseTicketOffer = null) }
        }
    }

    override fun canProceed(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ): Boolean {
        if (cajaRepository.activeCaja.value != null) return true
        state.update {
            it.copy(
                showCajaSelector = true,
                promotionMessage = "Selecciona una caja para realizar ventas",
            )
        }
        if (state.value.availableCajas.isEmpty() && !state.value.isLoadingCajas) {
            fetch(scope, state, forceShowSelector = true)
        }
        return false
    }
}

fun interface DashboardSaleGate {
    fun canProceed(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ): Boolean
}
