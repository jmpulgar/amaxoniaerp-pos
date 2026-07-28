package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CrearCuentaResponse
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.CuentasMesaListResponse
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaRequest
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaResponse

/**
 * Cuenta/división de mesa y solicitud de cuenta sobre una sesión. Sigue el mismo contrato
 * de autenticación y manejo de errores que [PedidosMesaApi].
 *
 * Endpoints:
 * - `GET    .../sesiones/{sesionId}/cuenta` listado de cuentas (con detalle y saldo).
 * - `GET    .../sesiones/{sesionId}/cuenta/{cuentaId}` detalle de una cuenta.
 * - `POST   .../sesiones/{sesionId}/cuenta` crear (completa o por división).
 * - `POST   .../sesiones/{sesionId}/cuenta/{cuentaId}/cancelar` cancelar cuenta sin pagar.
 * - `POST   .../sesiones/{sesionId}/cuenta/{cuentaId}/marcar-facturada` confirmar facturación.
 * - `POST   .../sesiones/{sesionId}/solicitar-cuenta` sesión -> CUENTA_SOLICITADA.
 * - `POST   .../sesiones/{sesionId}/cancelar-solicitud-cuenta` revierte a ABIERTA.
 */
interface CuentaMesaApi {
    suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<CuentasMesaListResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun obtener(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        authHeader: String,
    ): Result<CrearCuentaResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearCuentaRequest,
        authHeader: String,
    ): Result<CrearCuentaResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        authHeader: String,
    ): Result<CrearCuentaResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun marcarFacturada(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        request: MarcarCuentaFacturadaRequest,
        authHeader: String,
    ): Result<MarcarCuentaFacturadaResponse>

    suspend fun solicitarCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SolicitudCuentaResponse>

    suspend fun cancelarSolicitudCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SolicitudCuentaResponse>
}

/** Resultado de un cambio simple de estado de sesión (solicitar / cancelar-solicitud). */
@kotlinx.serialization.Serializable
data class SolicitudCuentaResponse(
    val success: Boolean = true,
    val mensaje: String? = null,
    val error: String? = null,
)

/** Wrapper tipado para listados returned by [CuentaMesaApi.listar] y Repository.listar. */
typealias CuentasMesaList = List<CuentaMesaResponse>
