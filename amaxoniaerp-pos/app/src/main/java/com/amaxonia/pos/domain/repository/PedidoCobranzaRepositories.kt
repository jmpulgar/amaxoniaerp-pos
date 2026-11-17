package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.cobranza.CobranzaPendientesResponseDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaRequestDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaResponseDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoRequestDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoResponseDto
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailDto
import com.amaxonia.pos.domain.model.pedidos.PedidoSummaryDto

/** Pedidos de venta en el ERP (requieren conexión). */
interface PedidoRepository {
    suspend fun create(request: CreatePedidoRequestDto): Result<CreatePedidoResponseDto>

    suspend fun pendientes(search: String? = null): Result<List<PedidoSummaryDto>>

    suspend fun detail(idPedido: String): Result<PedidoDetailDto>
}

/** Cobranza de facturas pendientes (CxC) de clientes (requiere conexión). */
interface CobranzaRepository {
    suspend fun pendientes(idCliente: String): Result<CobranzaPendientesResponseDto>

    suspend fun cobrar(request: CreateCobranzaRequestDto): Result<CreateCobranzaResponseDto>
}

/** Caja activa y su secuencia abierta, tal como las ve el POS en este momento. */
interface ActiveCajaContext {
    fun activeCaja(): Caja?

    fun activeSecuencia(): CajaSecuencia?
}
