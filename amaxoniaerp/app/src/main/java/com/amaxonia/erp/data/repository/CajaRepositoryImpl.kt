package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.checkCajaStatus
import com.amaxonia.erp.data.remote.closeCaja
import com.amaxonia.erp.data.remote.createCaja
import com.amaxonia.erp.data.remote.getCajaSecuencia
import com.amaxonia.erp.data.remote.getCajas
import com.amaxonia.erp.data.remote.getNextSecuenciaCodigo
import com.amaxonia.erp.data.remote.openCaja
import com.amaxonia.erp.data.remote.updateCaja
import com.amaxonia.erp.data.local.db.CajaSesionDao
import com.amaxonia.erp.data.local.db.CajaSesionEntity
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaPaymentLine
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.repository.CajaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import java.util.UUID

class CajaRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val cajaSesionDao: CajaSesionDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
) : CajaRepository {

    private val _activeCaja = MutableStateFlow<Caja?>(null)
    override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()

    private val _activeCajaName = MutableStateFlow("Caja no seleccionada")
    override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()

    private val _activeCajaSecuencia = MutableStateFlow<CajaSecuencia?>(null)
    override val activeCajaSecuencia: StateFlow<CajaSecuencia?> = _activeCajaSecuencia.asStateFlow()

    private val _sessionStatus = MutableStateFlow(CajaSessionStatus.SIN_CAJA)
    override val sessionStatus: StateFlow<CajaSessionStatus> = _sessionStatus.asStateFlow()

    private suspend fun tenantId(): String {
        val session = localStore.readCompanySession()
        return session?.company?.id?.toString() ?: "default"
    }

    private suspend fun persistSesionAbierta(
        cajaId: String,
        cajaSecuencia: CajaSecuencia?,
    ) {
        val dao = cajaSesionDao ?: return
        val tId = tenantId()
        val abierta = dao.getAbiertaPorCaja(tId, cajaId)
        val secJson = cajaSecuencia?.let {
            runCatching { AppJson.encodeToString(it) }.getOrNull()
        }.orEmpty()
        dao.upsert(
            CajaSesionEntity(
                localId = abierta?.localId ?: UUID.randomUUID().toString(),
                cajaId = cajaId,
                serverSecuenciaId = cajaSecuencia?.idCajaSecuencia ?: abierta?.serverSecuenciaId,
                estado = "ABIERTA",
                openedAt = abierta?.openedAt ?: System.currentTimeMillis(),
                userId = abierta?.userId.orEmpty(),
                tenantId = tId,
                secuenciaJson = if (secJson.isNotBlank()) secJson else (abierta?.secuenciaJson.orEmpty()),
            )
        )
    }

    private suspend fun markSesionCerrada(cajaId: String? = null) {
        val dao = cajaSesionDao ?: return
        val tId = tenantId()
        val now = System.currentTimeMillis()
        if (cajaId != null) {
            dao.markCerradaPorCaja(tId, cajaId, now)
        } else {
            dao.getAbierta(tId)?.let { sesion ->
                dao.upsert(sesion.copy(estado = "CERRADA", closedAt = now))
            }
        }
    }

    private suspend fun restorePersistedSesion(cajaId: String? = null): CajaSecuencia? {
        val dao = cajaSesionDao ?: return null
        val tId = tenantId()
        val sesion = if (cajaId != null) {
            dao.getAbiertaPorCaja(tId, cajaId)
        } else {
            dao.getAbierta(tId)
        } ?: return null

        return runCatching {
            AppJson.decodeFromString<CajaSecuencia>(sesion.secuenciaJson)
        }.getOrNull()
    }

    private suspend fun getContext(): Pair<String, String> {
        val session = localStore.readCompanySession()
            ?: error("No hay sesión de empresa activa")
        val adminDb = session.company.adminDb.ifBlank { "default" }
        return Pair(session.token, adminDb)
    }

    override suspend fun getCajas(): Result<List<Caja>> {
        val isOnline = networkMonitor?.isOnline() ?: true
        val cached = localStore.readCajas()

        if (!isOnline) {
            return if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                val active = localStore.readActiveCajaSnapshot()
                if (active != null) Result.success(listOf(active)) else Result.success(emptyList())
            }
        }

        return runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getCajas(token, adminDb, all = true)
            if (response.isNotEmpty()) {
                localStore.saveCajas(response)
            }
            response
        }.recoverCatching { error ->
            if (cached.isNotEmpty()) {
                cached
            } else {
                val active = localStore.readActiveCajaSnapshot()
                if (active != null) listOf(active) else throw error
            }
        }
    }

    override suspend fun createCaja(request: SaveCajaRequest): Result<Caja> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.createCaja(token, adminDb, request)
        }

    override suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.updateCaja(token, adminDb, id, request)
        }

    override suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getNextSecuenciaCodigo(token, adminDb, idCaja)
            response.codigo
        }

    override suspend fun restoreActiveCajaIfValid() {
        val caja = localStore.readActiveCajaSnapshot()
            ?: localStore.readActiveCaja()?.let { (id, name) -> Caja(idCaja = id, caja = name, descripcion = name) }
        if (caja != null && caja.idCaja.isNotBlank()) {
            _activeCajaName.update { caja.displayName }
            _activeCaja.update { caja }
            val persistedSecuencia = restorePersistedSesion(caja.idCaja)
            if (persistedSecuencia != null) {
                _activeCajaSecuencia.update { persistedSecuencia }
                _sessionStatus.update { CajaSessionStatus.ABIERTA }
            } else {
                _sessionStatus.update { CajaSessionStatus.PENDIENTE_APERTURA }
            }

            val isOnline = networkMonitor?.isOnline() ?: true
            if (isOnline) {
                checkCajaStatus(caja.idCaja)
            }
        } else {
            _activeCajaName.update { "Caja no seleccionada" }
            _activeCaja.update { null }
            _activeCajaSecuencia.update { null }
            _sessionStatus.update { CajaSessionStatus.SIN_CAJA }
        }
    }

    override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> =
        runCatching {
            val isOnline = networkMonitor?.isOnline() ?: true
            if (!isOnline) {
                val persisted = restorePersistedSesion(cajaId)
                if (persisted != null) {
                    _activeCajaSecuencia.update { persisted }
                    _sessionStatus.update { CajaSessionStatus.ABIERTA }
                    return@runCatching CajaStatusResponse(isOpen = true, cajaSecuencia = persisted)
                } else {
                    return@runCatching CajaStatusResponse(isOpen = false, cajaSecuencia = null)
                }
            }

            val (token, adminDb) = getContext()
            val response = apiService.checkCajaStatus(token, adminDb, cajaId)
            val trimmedCajaId = cajaId.trim()
            val activeId = _activeCaja.value?.idCaja?.trim()
            val sec = response.cajaSecuencia
            val matchesSecCaja = sec != null && (sec.idCaja.isBlank() || sec.idCaja.trim() == trimmedCajaId)
            if (activeId == null || activeId == trimmedCajaId) {
                if (response.isOpen && matchesSecCaja) {
                    _activeCajaSecuencia.update { sec }
                    _sessionStatus.update { CajaSessionStatus.ABIERTA }
                    persistSesionAbierta(trimmedCajaId, sec)
                } else if (_activeCaja.value != null && activeId == trimmedCajaId) {
                    _activeCajaSecuencia.update { null }
                    _sessionStatus.update { CajaSessionStatus.PENDIENTE_APERTURA }
                    markSesionCerrada(trimmedCajaId)
                }
            }
            response
        }.recoverCatching { error ->
            val persisted = restorePersistedSesion(cajaId)
            if (persisted != null) {
                _activeCajaSecuencia.update { persisted }
                _sessionStatus.update { CajaSessionStatus.ABIERTA }
                CajaStatusResponse(isOpen = true, cajaSecuencia = persisted)
            } else if (cajaSesionDao != null) {
                CajaStatusResponse(isOpen = false, cajaSecuencia = null)
            } else {
                throw error
            }
        }

    override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.openCaja(token, adminDb, request)
            if (response.isOpen && response.cajaSecuencia != null) {
                _activeCajaSecuencia.update { response.cajaSecuencia }
                _sessionStatus.update { CajaSessionStatus.ABIERTA }
                persistSesionAbierta(request.idCaja, response.cajaSecuencia)
            }
            response
        }

    override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> =
        runCatching {
            val (token, adminDb) = getContext()
            val activeCajaId = _activeCaja.value?.idCaja
            val response = apiService.closeCaja(token, adminDb, request)
            if (response.success) {
                _activeCajaSecuencia.update { null }
                _sessionStatus.update {
                    if (_activeCaja.value != null) CajaSessionStatus.PENDIENTE_APERTURA else CajaSessionStatus.SIN_CAJA
                }
                markSesionCerrada(activeCajaId)
            }
            response
        }

    override suspend fun getCierreSummary(): Result<CierreCajaSummary> {
        val caja = _activeCaja.value
        val sequenceId = _activeCajaSecuencia.value?.idCajaSecuencia
            ?: (caja?.let { checkCajaStatus(it.idCaja).getOrNull()?.cajaSecuencia?.idCajaSecuencia })

        return when {
            caja == null -> Result.failure(IllegalStateException("No hay caja activa"))
            sequenceId.isNullOrBlank() -> Result.failure(IllegalStateException("No hay una secuencia de caja abierta"))
            else -> loadCierreSummary(caja, sequenceId, verifyPendingInvoices = true)
        }
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
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getCajaSecuencia(
                token = token,
                companyDb = adminDb,
                idSecuencia = sequenceId,
                verifyFacturasTemporales = verifyPendingInvoices,
            )
            val dto = response.data?.takeIf { response.success }
                ?: error(response.error ?: "No se pudo cargar el cierre de caja")

            val paymentLines = dto.forma_pago
                .filter { it.monto > 0.0 && it.id > 0 }
                .map {
                    CierreCajaPaymentLine(
                        idFormaPago = it.id,
                        label = it.forma_pago ?: it.siglas ?: "Forma de pago",
                        siglas = it.siglas.orEmpty(),
                        amount = it.monto,
                    )
                }

            val totalCash = paymentLines
                .filter {
                    it.siglas.equals("CASH", ignoreCase = true) ||
                        it.siglas.equals("EF", ignoreCase = true) ||
                        it.siglas.equals("EFE", ignoreCase = true) ||
                        it.siglas.equals("EFECTIVO", ignoreCase = true)
                }.sumOf { it.amount }

            val totalCard = paymentLines
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
                paymentLines = paymentLines,
            )
        }

    override suspend fun setActiveCaja(caja: Caja) {
        val currentCaja = _activeCaja.value
        if (currentCaja?.idCaja != caja.idCaja) {
            val restored = restorePersistedSesion(caja.idCaja)
            _activeCajaSecuencia.update { restored }
        }
        _activeCaja.update { caja }
        _activeCajaName.update { caja.displayName }
        _sessionStatus.update {
            if (_activeCajaSecuencia.value != null) CajaSessionStatus.ABIERTA else CajaSessionStatus.PENDIENTE_APERTURA
        }
        localStore.saveActiveCaja(caja)
    }

    override suspend fun setActiveCaja(id: String, name: String) {
        val currentCaja = _activeCaja.value
        val newCaja = Caja(idCaja = id, caja = name, descripcion = name)
        if (currentCaja?.idCaja != id) {
            val restored = restorePersistedSesion(id)
            _activeCajaSecuencia.update { restored }
            _activeCaja.update { newCaja }
        }
        _activeCajaName.update { name }
        _sessionStatus.update {
            if (_activeCajaSecuencia.value != null) CajaSessionStatus.ABIERTA else CajaSessionStatus.PENDIENTE_APERTURA
        }
        localStore.saveActiveCaja(newCaja)
    }

    override suspend fun clearActiveCaja() {
        _activeCaja.update { null }
        _activeCajaName.update { "Caja no seleccionada" }
        _activeCajaSecuencia.update { null }
        _sessionStatus.update { CajaSessionStatus.SIN_CAJA }
        localStore.clearActiveCaja()
    }

    override suspend fun markSequenceClosed() {
        val activeCajaId = _activeCaja.value?.idCaja
        _activeCajaSecuencia.update { null }
        _sessionStatus.update {
            if (_activeCaja.value != null) CajaSessionStatus.PENDIENTE_APERTURA else CajaSessionStatus.SIN_CAJA
        }
        markSesionCerrada(activeCajaId)
    }

    override suspend fun getActiveCaja(): Pair<String, String>? =
        localStore.readActiveCaja()
}
