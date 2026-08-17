package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import java.math.BigDecimal
import java.math.RoundingMode

internal fun buildClienteVE(ctx: InvoiceVEContext): VenezuelaHkaCliente =
    ctx.comprador.let { c ->
        VenezuelaHkaCliente(
            nombreRazonSocial = c.nombreRazonSocial.ifBlank { "CONSUMIDOR FINAL" },
            numeroRif = c.rif.ifBlank { "V000000000" },
            direccion = c.direccion?.takeIf { it.isNotBlank() },
            telefono = c.telefono?.takeIf { it.isNotBlank() },
            correoElectronico = c.email?.takeIf { it.isNotBlank() },
        )
    }

internal fun buildItemVE(det: VEDetalleData): VenezuelaHkaItem {
    val valorIva = det.totalConIva.subtract(det.totalSinIva).max(BigDecimal.ZERO)
    return VenezuelaHkaItem(
        descripcion = det.descripcion,
        codigo = det.codigo,
        referencia = det.referencia?.takeIf { it.isNotBlank() },
        unidadMedida = det.unidadEmpaque?.takeIf { it.isNotBlank() } ?: "UND",
        cantidad = det.cantidad.setScale(QTY_SCALE, RoundingMode.HALF_UP).toPlainString(),
        precioUnitario = det.precioSinIva.format(),
        precioUnitarioDescuento =
            if (det.cantidad > BigDecimal.ZERO && det.montoDescuento > BigDecimal.ZERO) {
                det.montoDescuento.divide(det.cantidad, MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()
            } else {
                null
            },
        montoDescuento = det.montoDescuento.takeIf { it > BigDecimal.ZERO }?.format(),
        precioItem = det.totalSinIva.format(),
        valorTotal = det.totalConIva.format(),
        alicuotaIva = alicuotaCodigo(det.piva),
        valorIva = valorIva.format(),
        valorAcarreo = det.importeAcarreo?.takeIf { it > BigDecimal.ZERO }?.format(),
        valorSeguro = det.importeSeguro?.takeIf { it > BigDecimal.ZERO }?.format(),
        valorIsc = det.importeIsc?.takeIf { it > BigDecimal.ZERO }?.format(),
        porcentajeIsc = det.porcentajeIsc?.takeIf { it > BigDecimal.ZERO }?.format(),
    )
}
