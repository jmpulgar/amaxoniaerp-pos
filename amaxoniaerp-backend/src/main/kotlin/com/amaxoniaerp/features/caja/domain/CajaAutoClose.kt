package com.amaxoniaerp.features.caja.domain

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
                current.copy(monto = current.monto + incoming.monto)
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
                current.copy(monto = current.monto + incoming.monto)
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
            .sumOf { it.monto }
    val montoOtrosTotal =
        formaPagoTotals
            .filterNot { (_, item) -> isCashSigla(item.sigla) }
            .values
            .sumOf { it.monto }
    val montoEfectivoTotal =
        data.montoEfectivoApertura +
            montoEfectivoVentas +
            data.montoEfectivoEntrada -
            data.montoEfectivoSalida
    val montoTotal = montoEfectivoTotal + montoOtrosTotal

    return CajaCierreSaveRequest(
        id = data.id,
        montoEfectivoVentas = montoEfectivoVentas,
        montoEfectivoEntrada = data.montoEfectivoEntrada,
        montoEfectivoSalida = data.montoEfectivoSalida,
        montoEfectivoTotal = montoEfectivoTotal,
        montoEfectivoCierre = montoEfectivoTotal,
        montoEfectivoDiferencia = 0.0,
        montoOtrosTotal = montoOtrosTotal,
        montoOtrosCierre = montoOtrosTotal,
        montoOtrosDiferencia = 0.0,
        montoTotal = montoTotal,
        montoCierre = montoTotal,
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
