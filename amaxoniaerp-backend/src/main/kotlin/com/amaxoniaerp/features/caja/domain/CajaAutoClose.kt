package com.amaxoniaerp.features.caja.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cálculo del cierre automático de una secuencia de caja a partir de los
 * datos leídos: agrega ventas/devoluciones por forma de pago, clasifica
 * efectivo vs otros y construye el [CajaCierreSaveRequest] con diferencias
 * en cero. Puro (sin Ktor/Exposed) para poder testearlo de forma aislada.
 */
fun isCashSigla(siglas: String?): Boolean {
    val value = siglas.orEmpty().trim().uppercase()
    return value == "CASH" || value == "EF" || value == "EFE" || value == "EFECTIVO"
}

private fun Double.toMoney(): BigDecimal = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)

fun buildAutoCloseFormaPagoTotals(data: CajaSecuenciaData): Map<Int, FormaPagoCloseTotal> {
    val totals = linkedMapOf<Int, FormaPagoCloseTotal>()

    data.formaPago
        .asSequence()
        .filter { it.id > 0 && it.monto != 0.0 }
        .forEach { line ->
            totals.merge(
                line.id,
                FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
            ) { current, incoming ->
                val sum =
                    (current.monto.toMoney() + incoming.monto.toMoney())
                        .setScale(2, RoundingMode.HALF_UP)
                        .toDouble()
                current.copy(monto = sum)
            }
        }

    data.formaPagoDevolucion
        .asSequence()
        .filter { it.idFormaPago > 0 && it.monto != 0.0 }
        .forEach { line ->
            totals.merge(
                line.idFormaPago,
                FormaPagoCloseTotal(sigla = line.siglas, monto = line.monto),
            ) { current, incoming ->
                val sum =
                    (current.monto.toMoney() + incoming.monto.toMoney())
                        .setScale(2, RoundingMode.HALF_UP)
                        .toDouble()
                current.copy(monto = sum)
            }
        }

    return totals
        .filterValues { it.monto > 0.0 }
        .toMap()
}

fun buildAutoCloseRequest(data: CajaSecuenciaData): CajaCierreSaveRequest {
    val formaPagoTotals = buildAutoCloseFormaPagoTotals(data)
    val montoEfectivoVentas =
        formaPagoTotals
            .filter { (_, item) -> isCashSigla(item.sigla) }
            .values
            .map { it.monto.toMoney() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)
    val montoOtrosTotal =
        formaPagoTotals
            .filterNot { (_, item) -> isCashSigla(item.sigla) }
            .values
            .map { it.monto.toMoney() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)
    val montoEfectivoApertura = data.montoEfectivoApertura.toMoney()
    val montoEfectivoEntrada = data.montoEfectivoEntrada.toMoney()
    val montoEfectivoSalida = data.montoEfectivoSalida.toMoney()
    val montoEfectivoTotal =
        (montoEfectivoApertura + montoEfectivoVentas + montoEfectivoEntrada - montoEfectivoSalida)
            .setScale(2, RoundingMode.HALF_UP)
    val montoTotal = (montoEfectivoTotal + montoOtrosTotal).setScale(2, RoundingMode.HALF_UP)

    return CajaCierreSaveRequest(
        id = data.id,
        montoEfectivoVentas = montoEfectivoVentas.toDouble(),
        montoEfectivoEntrada = montoEfectivoEntrada.toDouble(),
        montoEfectivoSalida = montoEfectivoSalida.toDouble(),
        montoEfectivoTotal = montoEfectivoTotal.toDouble(),
        montoEfectivoCierre = montoEfectivoTotal.toDouble(),
        montoEfectivoDiferencia = 0.0,
        montoOtrosTotal = montoOtrosTotal.toDouble(),
        montoOtrosCierre = montoOtrosTotal.toDouble(),
        montoOtrosDiferencia = 0.0,
        montoTotal = montoTotal.toDouble(),
        montoCierre = montoTotal.toDouble(),
        montoDiferencia = 0.0,
        detalle = emptyList(),
        detalleFormaPago =
            formaPagoTotals
                .map { (idFormaPago, item) ->
                    CajaCierreFormaPagoRequest(
                        idFormaPago = idFormaPago,
                        monto = item.monto,
                        montoCierre = item.monto,
                        montoDiferencia = 0.0,
                    )
                },
        observacionCierre = "Cierre automático por nueva apertura",
        numeroCierreFiscal = "",
    )
}
