package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.local.readFormasPago
import com.amaxonia.pos.data.local.saveFormasPago
import com.amaxonia.pos.data.local.db.CajaPaymentMethodEntity
import com.amaxonia.pos.data.local.db.PaymentMethodDao
import com.amaxonia.pos.data.local.db.PaymentMethodEntity
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.api.FormaPagoApi
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.SessionConfigurationException

/**
 * Formas de pago con TRES fuentes, en orden de frescura:
 *  1. API online (y calienta Room + DataStore).
 *  2. Room (`payment_methods`, poblada por el sync incremental ADR-008).
 *  3. Snapshot DataStore por caja (legado).
 * Garantía offline: la pantalla de pago nunca se queda sin métodos si hubo
 * al menos un sync exitoso.
 */
class FormaPagoRepositoryImpl(
    private val formaPagoApi: FormaPagoApi,
    private val localStore: LocalStore,
    private val paymentMethodDao: PaymentMethodDao,
    private val networkMonitor: NetworkMonitor,
) : FormaPagoRepository {
    override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>> {
        val cached = localStore.readFormasPago(cajaId)
        if (!networkMonitor.isOnline()) {
            val fromRoom = readFromRoom(cajaId)
            return when {
                fromRoom.isNotEmpty() -> Result.success(fromRoom)
                cached.isNotEmpty() -> Result.success(cached)
                else -> Result.failure(IllegalStateException("No hay formas de pago sincronizadas para trabajar offline"))
            }
        }

        return catchingResult {
            val authHeader = getAuthHeader()
            formaPagoApi
                .getFormasPago(cajaId = cajaId, authHeader = authHeader)
                .map { response -> response.data }
                .onSuccess { formasPago ->
                    localStore.saveFormasPago(cajaId, formasPago)
                    upsertToRoom(formasPago, cajaId)
                }
                .recoverCatching { error ->
                    val fromRoom = readFromRoom(cajaId)
                    when {
                        fromRoom.isNotEmpty() -> fromRoom
                        cached.isNotEmpty() -> cached
                        else -> throw error
                    }
                }
        }.recoverCatching { error ->
            val fromRoom = readFromRoom(cajaId)
            when {
                fromRoom.isNotEmpty() -> fromRoom
                cached.isNotEmpty() -> cached
                else -> throw error
            }
        }
    }

    /** Room es la fuente offline principal del sync incremental (ADR-008). */
    private suspend fun readFromRoom(cajaId: String?): List<FormaPago> =
        if (cajaId.isNullOrBlank()) {
            paymentMethodDao.getAll()
        } else {
            paymentMethodDao.getByCaja(cajaId)
        }.map { it.toFormaPago(cajaId) }

    private suspend fun upsertToRoom(
        formasPago: List<FormaPago>,
        cajaId: String?,
    ) {
        val entities =
            formasPago.map { formaPago ->
                PaymentMethodEntity(
                    idFormaPago = formaPago.idFormaPago,
                    siglas = formaPago.siglas,
                    codigo = formaPago.codigo?.toIntOrNull(),
                    descripcion = formaPago.descripcion,
                    idCajaTpConcepto = formaPago.idCajaTpConcepto,
                    cuentaContable = formaPago.cuentaContable,
                    idCajaTpRegistro = formaPago.idCajaTpRegistro,
                    formaPagoFact = formaPago.formaPagoFact,
                    activo = formaPago.activo,
                    pos = formaPago.pos,
                    imagen = formaPago.imagen ?: "",
                    grupo = formaPago.grupo,
                    orden = formaPago.orden,
                    idBancoCuenta = formaPago.idBancoCuenta ?: 1,
                    idBancoOperacion = formaPago.idBancoOperacion ?: 1,
                    tipoMoneda = formaPago.tipoMoneda,
                )
            }
        paymentMethodDao.upsertAll(entities)
        if (!cajaId.isNullOrBlank()) {
            paymentMethodDao.upsertCajaMappings(
                formasPago.map { formaPago ->
                    CajaPaymentMethodEntity(
                        idCaja = cajaId,
                        idFormaPago = formaPago.idFormaPago,
                        activo = formaPago.activo,
                    )
                },
            )
        }
    }

    private fun PaymentMethodEntity.toFormaPago(idCaja: String?): FormaPago =
        FormaPago(
            idFormaPago = idFormaPago,
            siglas = siglas,
            codigo = codigo?.toString(),
            descripcion = descripcion,
            idCajaTpConcepto = idCajaTpConcepto,
            idCajaTpRegistro = idCajaTpRegistro,
            cuentaContable = cuentaContable,
            formaPagoFact = formaPagoFact,
            activo = activo,
            pos = pos,
            imagen = imagen,
            grupo = grupo,
            orden = orden,
            idBancoCuenta = idBancoCuenta,
            idBancoOperacion = idBancoOperacion,
            tipoMoneda = tipoMoneda ?: "",
            idCaja = idCaja,
        )

    private suspend fun getAuthHeader(): String {
        val token =
            localStore.readCompanySession()?.token
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
