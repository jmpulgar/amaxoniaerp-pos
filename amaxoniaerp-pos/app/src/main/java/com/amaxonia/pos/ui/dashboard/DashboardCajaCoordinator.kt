package com.amaxonia.pos.ui.dashboard

import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSessionStatus
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.setSellerContext
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintOutcome
import com.amaxonia.pos.domain.usecase.caja.CashClosePrintingService
import com.amaxonia.pos.domain.usecase.caja.CashCloseTicketPayloadBuilder
import com.amaxonia.pos.domain.util.CajaDateParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardCajaCoordinator(
    private val cajaRepository: CajaRepository,
    private val cartRepository: CartRepository,
    private val cashClosePrinting: CashClosePrintingService,
    private val ticketPayloadBuilder: CashCloseTicketPayloadBuilder,
    private val connectivity: ConnectivityStatus,
) : DashboardSaleGate {
    /** True mientras se consulta el estado de la caja contra el backend. */
    private val verifying = MutableStateFlow(false)

    /** Registra la última secuencia por la que ya se mostró el aviso de día anterior. */
    private var lastPromptedSecuenciaId: String? = null

    /** Trabajo en curso de carga de cajas para evitar condiciones de carrera entre cargas en segundo plano. */
    private var loadCajasJob: kotlinx.coroutines.Job? = null

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
            combine(
                cajaRepository.activeCaja,
                cajaRepository.activeCajaSecuencia,
                verifying,
            ) { caja, secuencia, isVerifying ->
                val validSecuencia = secuencia?.takeIf { it.idCaja == caja?.idCaja }
                Triple(caja, validSecuencia, isVerifying)
            }.collect { (caja, secuencia, isVerifying) ->
                val branchName = caja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal"
                val warehouseName =
                    caja?.almacenNombre?.takeIf(String::isNotBlank)
                        ?: caja?.defaultWarehouseId?.let { "Almacén $it" }
                        ?: caja?.codAlmacen?.takeIf { it > 0 }?.let { "Almacén $it" }
                        ?: "Almacén principal"
                val rawFecha = secuencia?.fechaApertura
                val formattedFecha = rawFecha?.let(CajaDateParser::formatDisplayDate)
                val isDiaAnterior = secuencia != null && CajaDateParser.isFromPreviousDay(rawFecha)
                val seqId = secuencia?.idCajaSecuencia
                val shouldPrompt = !isVerifying && isDiaAnterior && !seqId.isNullOrBlank() && seqId != lastPromptedSecuenciaId
                if (shouldPrompt) {
                    lastPromptedSecuenciaId = seqId
                } else if (seqId == null || !isDiaAnterior) {
                    lastPromptedSecuenciaId = null
                }
                state.update {
                    it.copy(
                        activeCajaId = caja?.idCaja,
                        sucursalNombre = branchName,
                        almacenNombre = warehouseName,
                        hasActiveCaja = caja != null,
                        cajaSession = resolveSession(caja, secuencia, isVerifying),
                        cajaFechaApertura = formattedFecha,
                        isCajaDiaAnterior = isDiaAnterior,
                        showAvisoCajaAnterior = if (shouldPrompt) true else (if (!isVerifying && isDiaAnterior) it.showAvisoCajaAnterior else false),
                    )
                }
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
            verifying.value = true
            cajaRepository.restoreActiveCajaIfValid()
            verifyActiveSession()
            if (connectivity.isOnline() || cajaRepository.activeCaja.value == null) {
                loadCajasJob?.cancel()
                loadCajasJob = scope.launch { loadCajas(state) }
            }
            verifying.value = false
        }
    }

    private fun resolveSession(
        caja: Caja?,
        secuencia: com.amaxonia.pos.domain.model.caja.CajaSecuencia?,
        isVerifying: Boolean,
    ): CajaSessionStatus =
        when {
            isVerifying -> CajaSessionStatus.VERIFICANDO
            caja == null -> CajaSessionStatus.SIN_CAJA
            secuencia != null && !secuencia.idCajaSecuencia.isNullOrBlank() && secuencia.idCaja == caja.idCaja -> CajaSessionStatus.ABIERTA
            else -> CajaSessionStatus.PENDIENTE_APERTURA
        }

    /**
     * Sincroniza [CajaRepository.activeCajaSecuencia] con el backend cuando hay
     * conectividad. Offline se conserva la última secuencia conocida, igual que
     * en el flujo de pago.
     */
    private suspend fun verifyActiveSession() {
        val caja = cajaRepository.activeCaja.value ?: return
        if (connectivity.isOnline()) {
            cajaRepository.checkCajaStatus(caja.idCaja)
        }
    }

    fun fetch(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        forceShowSelector: Boolean = false,
    ) {
        loadCajasJob?.cancel()
        loadCajasJob = scope.launch { loadCajas(state, forceShowSelector) }
    }

    private suspend fun loadCajas(
        state: MutableStateFlow<DashboardState>,
        forceShowSelector: Boolean = false,
    ) {
        val keepSelectorVisible = forceShowSelector || state.value.showCajaSelector
        state.update { it.copy(isLoadingCajas = true, showCajaSelector = keepSelectorVisible) }
        cajaRepository.getCajas().fold(
            onSuccess = { cajas ->
                val currentActive = cajaRepository.activeCaja.value
                val isCurrentActiveValid = currentActive != null && cajas.any { it.idCaja == currentActive.idCaja }
                if (currentActive != null && !isCurrentActiveValid) {
                    // La caja activa en memoria/caché no pertenece a las cajas asignadas al usuario
                    cajaRepository.clearActiveCaja()
                    state.update { it.copy(activeCajaId = null) }
                }

                val activeCaja = cajaRepository.activeCaja.value
                val onlyAvailableCaja = cajas.singleOrNull()
                if (!forceShowSelector && !state.value.showCajaSelector && activeCaja == null && onlyAvailableCaja != null) {
                    // Auto-seleccionamos la única caja solo si el usuario no tiene abierto el selector
                    cajaRepository.setActiveCaja(onlyAvailableCaja)
                    verifyActiveSession()
                }
                // Si el selector ya estaba visible o se forzó, jamás debe cerrarse por una carga en segundo plano.
                val shouldShowSelector =
                    keepSelectorVisible || state.value.showCajaSelector || (cajaRepository.activeCaja.value == null && onlyAvailableCaja == null)
                state.update {
                    it.copy(
                        availableCajas = cajas,
                        isLoadingCajas = false,
                        showCajaSelector = shouldShowSelector,
                    )
                }
            },
            onFailure = { error ->
                val activeCaja = cajaRepository.activeCaja.value
                val isOffline = !connectivity.isOnline()
                val shouldShowSelector = keepSelectorVisible || state.value.showCajaSelector || activeCaja == null
                state.update {
                    it.copy(
                        isLoadingCajas = false,
                        showCajaSelector = shouldShowSelector,
                        error = if (isOffline) null else "Error al cargar cajas: ${error.message}",
                    )
                }
            },
        )
    }

    /** Muestra el diálogo de confirmación de apertura para [caja]. */
    private fun requestApertura(
        state: MutableStateFlow<DashboardState>,
        caja: Caja,
    ) {
        state.update {
            it.copy(
                showCajaSelector = false,
                showAvisoCajaAnterior = false,
                showAperturaPrompt = true,
                aperturaCandidate = caja,
            )
        }
    }

    private fun dismissApertura(state: MutableStateFlow<DashboardState>) {
        state.update { it.copy(showAperturaPrompt = false, aperturaCandidate = null) }
    }

    private fun confirmApertura(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        caja: Caja,
        openingAmount: Double,
    ) {
        state.update { it.copy(showAperturaPrompt = false, aperturaCandidate = null) }
        selectAndOpen(scope, state, caja, openingAmount)
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
                    val ticketOffer =
                        closedSequenceId?.let { sequenceId ->
                            buildAutomaticCloseTicketOffer(caja, sequenceId)
                        }
                    state.update { current ->
                        current.copy(
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

    private fun renovarCajaDiaAnterior(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ) {
        val caja =
            cajaRepository.activeCaja.value
                ?: state.value.availableCajas.firstOrNull { it.caja == state.value.cajaPrincipalNombre }
                ?: state.value.availableCajas.singleOrNull()
                ?: return

        scope.launch {
            state.update {
                it.copy(
                    showAvisoCajaAnterior = false,
                    isRenovandoCaja = true,
                )
            }
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
                            isRenovandoCaja = false,
                            error = "Error al obtener correlativo de caja: ${error.message}",
                        )
                    }
                    return@launch
                }
            val request =
                AperturaRequest(
                    idCaja = caja.idCaja,
                    montoApertura = 0.0,
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
                    val ticketOffer =
                        closedSequenceId?.let { sequenceId ->
                            buildAutomaticCloseTicketOffer(caja, sequenceId)
                        }
                    state.update { current ->
                        current.copy(
                            isRenovandoCaja = false,
                            automaticCloseTicketOffer = ticketOffer,
                        )
                    }
                },
                onFailure = { error ->
                    state.update {
                        it.copy(
                            isRenovandoCaja = false,
                            error = "Error al renovar caja: ${error.message}",
                        )
                    }
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
            is DashboardCajaUiAction.SelectCaja -> selectCaja(scope, state, action.caja)
            is DashboardCajaUiAction.RequestApertura -> requestApertura(state, action.caja)
            DashboardCajaUiAction.RequestAperturaActive -> {
                val activeCaja = cajaRepository.activeCaja.value ?: state.value.availableCajas.singleOrNull()
                if (activeCaja != null) {
                    scope.launch { cajaRepository.setActiveCaja(activeCaja) }
                    requestApertura(state, activeCaja)
                } else {
                    setSelectorVisible(state, true)
                }
            }
            is DashboardCajaUiAction.ConfirmApertura -> confirmApertura(scope, state, action.caja, action.openingAmount)
            DashboardCajaUiAction.DismissApertura -> dismissApertura(state)
            DashboardCajaUiAction.DismissAutoCloseMessage -> state.update { it.copy(autoCloseMessage = null) }
            DashboardCajaUiAction.PrintAutomaticCloseTicket -> printAutomaticCloseTicket(scope, state)
            DashboardCajaUiAction.DismissAutomaticCloseTicket ->
                state.update { it.copy(automaticCloseTicketOffer = null) }
            DashboardCajaUiAction.DismissAvisoCajaAnterior ->
                state.update { it.copy(showAvisoCajaAnterior = false) }
            DashboardCajaUiAction.RenovarCajaDiaAnterior ->
                renovarCajaDiaAnterior(scope, state)
        }
    }

    private fun selectCaja(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
        caja: Caja,
    ) {
        state.update {
            it.copy(
                activeCajaId = caja.idCaja,
                cajaPrincipalNombre = caja.caja ?: caja.descripcion ?: "Caja Principal",
                cajaSession = CajaSessionStatus.VERIFICANDO,
                showCajaSelector = false,
                showAvisoCajaAnterior = false,
                isCajaDiaAnterior = false,
                cajaFechaApertura = null,
            )
        }
        lastPromptedSecuenciaId = null
        scope.launch {
            verifying.value = true
            cajaRepository.setActiveCaja(caja)
            if (connectivity.isOnline()) {
                val status = cajaRepository.checkCajaStatus(caja.idCaja).getOrNull()
                verifying.value = false
                if (status != null && !status.isOpen) {
                    requestApertura(state, caja)
                }
            } else {
                verifying.value = false
            }
        }
    }

    /**
     * Guard de venta: solo se puede proceder con la caja [CajaSessionStatus.ABIERTA].
     * En el resto de estados guía al usuario (verificando / apertura / selección)
     * en lugar de dejar avanzar hacia un cobro que fallaría.
     */
    override fun canProceed(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ): Boolean =
        when (state.value.cajaSession) {
            CajaSessionStatus.ABIERTA -> true
            CajaSessionStatus.VERIFICANDO -> {
                state.update { it.copy(promotionMessage = "Verificando el estado de la caja…") }
                false
            }
            CajaSessionStatus.PENDIENTE_APERTURA -> {
                val caja = cajaRepository.activeCaja.value ?: state.value.availableCajas.singleOrNull()
                if (caja != null) {
                    scope.launch { cajaRepository.setActiveCaja(caja) }
                    requestApertura(state, caja)
                } else {
                    setSelectorVisible(state, true)
                }
                false
            }
            CajaSessionStatus.SIN_CAJA -> {
                val available = state.value.availableCajas
                val singleCaja = available.singleOrNull()
                if (singleCaja != null) {
                    scope.launch { cajaRepository.setActiveCaja(singleCaja) }
                    requestApertura(state, singleCaja)
                } else {
                    state.update {
                        it.copy(
                            showCajaSelector = true,
                            promotionMessage = "No tienes una caja abierta. Selecciona una caja para aperturar.",
                        )
                    }
                    if (available.isEmpty() && !state.value.isLoadingCajas) {
                        fetch(scope, state, forceShowSelector = true)
                    }
                }
                false
            }
        }
}

fun interface DashboardSaleGate {
    fun canProceed(
        scope: CoroutineScope,
        state: MutableStateFlow<DashboardState>,
    ): Boolean
}
