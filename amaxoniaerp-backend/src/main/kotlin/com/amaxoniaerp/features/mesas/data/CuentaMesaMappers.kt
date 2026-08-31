package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.mesas.domain.CuentaDetalleResponse
import com.amaxoniaerp.features.mesas.domain.CuentaMesaResponse
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val CUENTA_SCALE = 2
internal const val CUENTA_MAX_ERROR_LEN = 500
internal val CUENTA_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

internal const val UNIT_CALCULATION_SCALE = 6
internal const val QUANTITY_SCALE = 3

internal fun ResultRow.toCuentaMesaResponse(detalles: List<CuentaDetalleResponse> = emptyList()): CuentaMesaResponse =
    CuentaMesaResponse(
        id = this[CuentaMesaTable.id],
        sesionMesaId = this[CuentaMesaTable.sesionMesaId],
        numeroCuenta = this[CuentaMesaTable.numeroCuenta],
        estado = this[CuentaMesaTable.estado],
        subtotal = this[CuentaMesaTable.subtotal].toDouble(),
        descuento = this[CuentaMesaTable.descuento].toDouble(),
        impuesto = this[CuentaMesaTable.impuesto].toDouble(),
        total = this[CuentaMesaTable.total].toDouble(),
        saldoRestante = this[CuentaMesaTable.saldoRestante].toDouble(),
        idFactura = this[CuentaMesaTable.idFactura],
        codFactura = this[CuentaMesaTable.codFactura],
        fechaFactura = this[CuentaMesaTable.fechaFactura]?.formatCuentaIso(),
        fechaCreacion = this[CuentaMesaTable.fechaCreacion].formatCuentaIso(),
        detalle = detalles,
    )

internal fun ResultRow.toCuentaDetalleResponse(): CuentaDetalleResponse =
    CuentaDetalleResponse(
        id = this[CuentaMesaDetalleTable.id],
        cuentaMesaId = this[CuentaMesaDetalleTable.cuentaMesaId],
        pedidoMesaId = this[CuentaMesaDetalleTable.pedidoMesaId],
        productoId = this[CuentaMesaDetalleTable.productoId],
        itemAlmacen = this[CuentaMesaDetalleTable.itemAlmacen],
        itemCodigo = this[CuentaMesaDetalleTable.itemCodigo],
        itemDescripcion = this[CuentaMesaDetalleTable.itemDescripcion],
        cantidad = this[CuentaMesaDetalleTable.cantidad].toDouble(),
        itemPrecioSinIva = this[CuentaMesaDetalleTable.itemPrecioSinIva].toDouble(),
        itemDescuento = this[CuentaMesaDetalleTable.itemDescuento].toDouble(),
        itemMontoDescuento = this[CuentaMesaDetalleTable.itemMontoDescuento].toDouble(),
        itemPIva = this[CuentaMesaDetalleTable.itemPIva].toDouble(),
        itemTotalSinIva = this[CuentaMesaDetalleTable.itemTotalSinIva].toDouble(),
        itemTotalConIva = this[CuentaMesaDetalleTable.itemTotalConIva].toDouble(),
        facturado = this[CuentaMesaDetalleTable.facturado],
        fechaCreacion = this[CuentaMesaDetalleTable.fechaCreacion].formatCuentaIso(),
    )

internal fun LocalDateTime.formatCuentaIso(): String = CUENTA_ISO_FORMATTER.format(this)

internal fun dinero(value: Double): BigDecimal =
    BigDecimal.valueOf(value).setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN)

internal fun cantidad(value: Double): BigDecimal =
    BigDecimal.valueOf(value).setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN)

internal fun cantidadMesaScale(value: BigDecimal): BigDecimal = value.setScale(CUENTA_SCALE, RoundingMode.HALF_EVEN)
