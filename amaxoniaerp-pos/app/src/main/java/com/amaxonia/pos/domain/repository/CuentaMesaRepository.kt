package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.CrearCuentaRequest
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaRequest
import com.amaxonia.pos.domain.model.mesas.MarcarCuentaFacturadaResponse

/**
 * Cuenta/división de mesa para el POS.
 *
 * El ciclo de vida típico:
 * 1. `solicitarCuenta`/`cancelarSolicitudCuenta` cambian el estado de la sesión
 *    (ABIERTA ↔ CUENTA_SOLICITADA).
 * 2. `crear` con `incluirTodoPendiente=true` (cuenta completa) o con `items=[{pedido_mesa_id,
 *    cantidad}]` crea una división.
 * 3. El POS envía el detalle como override al flujo normal (`ExecutePaymentFlowUseCase`).
 *    El backend crea factura, marca cantidades y cierra la sesión en una sola transacción.
 * 4. `marcarFacturada` se conserva sólo como contrato legado.
 * 5. `cancelar` descarta una cuenta sin pagar y libera su saldo.
 */
interface CuentaMesaRepository {
    /** Lista todas las cuentas de la sesión (incluye PAGADAS/CANCELADAS para auditoría). */
    suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<List<CuentaMesaResponse>>

    /** Obtiene el detalle de una cuenta concreta. */
    suspend fun obtener(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
    ): Result<CuentaMesaResponse>

    /** Crea una cuenta completa o por división. Devuelve la cuenta creada con sus totales. */
    suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearCuentaRequest,
    ): Result<CuentaMesaResponse>

    /** Cancela una cuenta ACTIVA sin facturar. Libera los saldos asociados. */
    suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
    ): Result<CuentaMesaResponse>

    /**
     * Compatibilidad para clientes anteriores al contexto `cuenta_mesa` de procesar venta.
     * Firma conserva contrato público usado por ambos sabores y adaptadores existentes.
     */
    @Suppress("LongParameterList")
    suspend fun marcarFacturada(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        cuentaId: Int,
        request: MarcarCuentaFacturadaRequest,
    ): Result<MarcarCuentaFacturadaResponse>

    /** Transiciona la sesión a CUENTA_SOLICITADA. */
    suspend fun solicitarCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<Boolean>

    /** Revierte CUENTA_SOLICITADA → ABIERTA. */
    suspend fun cancelarSolicitudCuenta(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<Boolean>
}
