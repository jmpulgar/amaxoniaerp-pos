package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.CajaApi
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaFormaPagoItem
import com.amaxonia.pos.domain.model.caja.CierreCajaPaymentLine
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.repository.CajaRepository
import com.amaxonia.pos.domain.repository.SessionConfigurationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CajaRepositoryImpl(
    private val cajaApi: CajaApi,
    private val localStore: LocalStore,
) : CajaRepository {
    private val _activeCajaName = MutableStateFlow("Caja no seleccionada")
    override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()
    private val _activeCaja = MutableStateFlow<Caja?>(null)
    override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()
    private val _activeCajaSecuencia = MutableStateFlow<com.amaxonia.pos.domain.model.caja.CajaSecuencia?>(null)
    override val activeCajaSecuencia: StateFlow<com.amaxonia.pos.domain.model.caja.CajaSecuencia?> = _activeCajaSecuencia.asStateFlow()

    /** Stores the active session so we can build close-register summaries. */
    private var activeSecuencia: com.amaxonia.pos.domain.model.caja.CajaSecuencia? = null

    override suspend fun getCajas(): Result<List<Caja>> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            cajaApi.getCajas(authHeader, companyDb)
        }

    override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            cajaApi
                .getNextSecuenciaCodigo(idCaja, authHeader, companyDb)
                .map { it.codigo }
        }

    override suspend fun restoreActiveCajaIfValid() {
        val caja = localStore.readActiveCajaForToday()
        if (caja != null) {
            _activeCajaName.update { caja.caja ?: caja.descripcion ?: "Caja Principal" }
            _activeCaja.update { caja }
        } else {
            _activeCajaName.update { "Caja no seleccionada" }
            _activeCaja.update { null }
            activeSecuencia = null
            _activeCajaSecuencia.update { null }
        }
    }

    override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.checkCajaStatus(cajaId, authHeader, companyDb)
            result.onSuccess { response ->
                if (response.isOpen) {
                    activeSecuencia = response.cajaSecuencia
                    _activeCajaSecuencia.update { response.cajaSecuencia }
                }
            }
            result
        }

    override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.openCaja(request, authHeader, companyDb)
            result.onSuccess { response ->
                activeSecuencia = response.cajaSecuencia
                _activeCajaSecuencia.update { response.cajaSecuencia }
            }
            result
        }

    override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.closeCaja(request, authHeader, companyDb)
            result.onSuccess {
                activeSecuencia = null
                _activeCajaSecuencia.update { null }
            }
            result
        }

    override suspend fun getCierreSummary(): Result<CierreCajaSummary> {
        val caja =
            _activeCaja.value
                ?: return Result.failure(IllegalStateException("No hay caja activa"))

        val sequenceId =
            checkCajaStatus(caja.idCaja).getOrNull()?.cajaSecuencia?.idCajaSecuencia
                ?: return Result.failure(IllegalStateException("No hay una secuencia de caja abierta"))
        return loadCierreSummary(caja, sequenceId, verifyPendingInvoices = true)
    }

    override suspend fun getCierreSummaryForSequence(
        caja: Caja,
        sequenceId: String,
    ): Result<CierreCajaSummary> = loadCierreSummary(caja, sequenceId, verifyPendingInvoices = false)

    private suspend fun loadCierreSummary(
        caja: Caja,
        sequenceId: String,
        verifyPendingInvoices: Boolean,
    ): Result<CierreCajaSummary> =
        catchingResult {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            cajaApi
                .getCajaSecuencia(
                    idSecuencia = sequenceId,
                    verifyFacturasTemporales = verifyPendingInvoices,
                    authHeader = authHeader,
                    companyDb = companyDb,
                ).mapCatching { response ->
                    val dto =
                        response.data
                            ?.takeIf { response.success }
                            ?: error(response.error ?: "No se pudo cargar el cierre de caja")
                    val paymentLines =
                        dto.forma_pago
                            .filter { it.monto > 0.0 && it.id > 0 }
                            .map {
                                CierreCajaPaymentLine(
                                    idFormaPago = it.id,
                                    label = it.forma_pago ?: it.siglas ?: "Forma de pago",
                                    siglas = it.siglas ?: "",
                                    amount = it.monto,
                                )
                            }
                    val totalCash =
                        paymentLines
                            .filter {
                                it.siglas.equals("CASH", ignoreCase = true) ||
                                    it.siglas.equals("EF", ignoreCase = true) ||
                                    it.siglas.equals("EFE", ignoreCase = true)
                            }.sumOf { it.amount }
                    val totalCard =
                        paymentLines
                            .filter {
                                it.siglas.equals("TDC", ignoreCase = true) ||
                                    it.siglas.equals("TARJETA", ignoreCase = true) ||
                                    it.siglas.equals("PV", ignoreCase = true) ||
                                    it.siglas.equals("POS", ignoreCase = true) ||
                                    it.siglas.equals("DEBITO", ignoreCase = true) ||
                                    it.siglas.equals("DB", ignoreCase = true) ||
                                    it.siglas.equals("CR", ignoreCase = true) ||
                                    it.siglas.equals("CREDITO", ignoreCase = true)
                            }.sumOf { it.amount }
                    val totalOther = (paymentLines.sumOf { it.amount } - totalCash - totalCard).coerceAtLeast(0.0)

                    CierreCajaSummary(
                        idCajaSecuencia = dto.id,
                        idCaja = dto.id_caja,
                        cajaName = dto.caja?.ifBlank { null } ?: caja.caja ?: caja.descripcion ?: "Caja",
                        vendedorName = dto.vendedor.orEmpty(),
                        openedAt = dto.ffecha_apertura,
                        openAmount = dto.monto_efectivo_apertura,
                        totalSales = dto.total_ventas,
                        totalCash = totalCash,
                        totalCard = totalCard,
                        totalOther = totalOther,
                        transactionCount = dto.cantidad_transacciones,
                        expectedClose = dto.monto_cierre,
                        montoEfectivoVentas = dto.monto_efectivo_ventas,
                        montoEfectivoEntrada = dto.monto_efectivo_entrada,
                        montoEfectivoSalida = dto.monto_efectivo_salida,
                        montoEfectivoTotal = dto.monto_efectivo_total,
                        montoEfectivoCierre = dto.monto_efectivo_cierre,
                        montoEfectivoDiferencia = dto.monto_efectivo_diferencia,
                        montoOtrosTotal = dto.monto_otros_total,
                        montoOtrosCierre = dto.monto_otros_cierre,
                        montoOtrosDiferencia = dto.monto_otros_diferencia,
                        montoTotal = dto.monto_total,
                        montoCierre = dto.monto_cierre,
                        montoDiferencia = dto.monto_diferencia,
                        detalleFormaPago =
                            paymentLines.map { line ->
                                CierreCajaFormaPagoItem(
                                    id_forma_pago = line.idFormaPago,
                                    monto = line.amount,
                                    monto_cierre = line.amount,
                                    monto_diferencia = 0.0,
                                )
                            },
                        paymentLines = paymentLines,
                        inventoryLines =
                            dto.inventario.map { line ->
                                com.amaxonia.pos.domain.model.caja.CashCloseInventoryLine(
                                    productCode = line.codigo,
                                    productName = line.descripcion,
                                    initialQuantity = line.existencia_inicial,
                                    soldQuantity = line.cantidad_vendida,
                                    realQuantity = line.existencia_disponible,
                                )
                            },
                    )
                }
        }

    override suspend fun setActiveCaja(caja: Caja) {
        _activeCajaName.update { caja.caja ?: caja.descripcion ?: "Caja Principal" }
        _activeCaja.update { caja }
        localStore.saveActiveCaja(caja)
    }

    override suspend fun clearActiveCaja() {
        _activeCajaName.update { "Caja no seleccionada" }
        _activeCaja.update { null }
        activeSecuencia = null
        _activeCajaSecuencia.update { null }
        localStore.clearActiveCaja()
    }

    private suspend fun getAuthHeader(): String {
        val token =
            localStore.readCompanySession()?.token
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }

    private suspend fun getCompanyDb(): String =
        localStore.readCompanySession()?.company?.adminDb
            ?: throw SessionConfigurationException("Base de datos de empresa no configurada")
}
