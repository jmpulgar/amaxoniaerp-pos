package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.remote.api.CuentaMesaApi
import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaRequest
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaResponse
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.CuentaMesaRepository
import com.amaxonia.pos.domain.repository.SessionConfigurationException

/**
 * Puerta de enlace al [CuentaMesaApi]. Como los saldos de cuenta mutan con cada pago, no
 * cachea: cada llamada consulta al backend para reflejar el estado autoritativo.
 */
class CuentaMesaRepositoryImpl(
    private val api: CuentaMesaApi,
    private val session: CompanyTokenReader,
) : CuentaMesaRepository {
    override suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<List<CuentaMesaResponse>> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.listar(cajaId, areaId, mesaId, sesionId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudieron consultar las cuentas")
                response.data
            }
        }

    override suspend fun obtener(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
    ): Result<CuentaMesaResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.obtener(cajaId, areaId, mesaId, sesionId, cuentaId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo consultar la cuenta")
                response.data
            }
        }

    override suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearCuentaRequest,
    ): Result<CuentaMesaResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.crear(cajaId, areaId, mesaId, sesionId, request, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo crear la cuenta")
                response.data
            }
        }

    override suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
    ): Result<CuentaMesaResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.cancelar(cajaId, areaId, mesaId, sesionId, cuentaId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo cancelar la cuenta")
                response.data
            }
        }

    override suspend fun marcarFacturada(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        request: MarcarCuentaFacturadaRequest,
    ): Result<MarcarCuentaFacturadaResponse> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.marcarFacturada(cajaId, areaId, mesaId, sesionId, cuentaId, request, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo confirmar la facturación")
                response
            }
        }

    override suspend fun solicitarCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<Boolean> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.solicitarCuenta(cajaId, areaId, mesaId, sesionId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo solicitar la cuenta")
                true
            }
        }

    override suspend fun cancelarSolicitudCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<Boolean> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.cancelarSolicitudCuenta(cajaId, areaId, mesaId, sesionId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo cancelar la solicitud de cuenta")
                true
            }
        }

    private suspend fun getAuthHeader(): String {
        val token =
            session.companyToken()
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
