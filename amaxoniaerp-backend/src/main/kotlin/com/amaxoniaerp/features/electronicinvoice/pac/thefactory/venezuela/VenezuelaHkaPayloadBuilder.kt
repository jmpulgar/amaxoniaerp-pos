package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
import com.amaxoniaerp.features.electronicinvoice.domain.VEFormaPagoData
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Builder Pattern: transforma el [InvoiceVEContext] (datos crudos de la DB VE)
 * en un [VenezuelaHkaDocumentoWrapper] listo para enviar al endpoint Emision VE.
 *
 * Reglas (FASE 1, tipoDocumento "01"):
 *
 * 1. BigDecimal obligatorio: NUNCA Double/Float en cálculos monetarios.
 *    Escala 2 + HALF_UP explícito en cada paso.
 * 2. IVA por alícuota:
 *      - General    = 16 % (estándar VE vigente)
 *      - Reducido   = 8 %
 *      - Exento     = 0 %
 *    Se suman los `piva` de cada línea para clasificar y se totaliza a 3 grupos.
 * 3. IGTF: para cada forma de pago en divisa (esDivisa=true), se toma el monto
 *    en VES equivalente (montoEnDivisa * tasa) y se aplica el porcentaje
 *    `parametros_generales.igtf` (NO 3 % por defecto). Si no hay divisa, NO se
 *    emite el nodo IGTF.
 * 4. Total en letras: se calcula una vez conocido el `montoTotalFactura`.
 * 5. `transaccionId`: SHA-256(idFactura + numeroFormateado) truncado. Determinista.
 * 6. Moneda secundaria: si `multi_moneda == "SI"` y `tasa > 1`, se calcula el
 *    total en VES (base del documento) y se agrega `montoTotalMonedaSecundaria`
 *    (la divisa) más `tasaCambio`.
 * 7. NO se incluyen campos inventados: solo los del Swagger VE.
 *
 * Los helpers puros (cliente, items, alícuotas, fechas, monto en letras) viven
 * en VenezuelaHkaPayloadSupport.kt y VenezuelaHkaMontoEnLetras.kt.
 */
open class VenezuelaHkaPayloadBuilder {
    /**
     * Construye el payload completo de emisión.
     *
     * @param serie Serie fiscal consultada al PAC (generalmente "L001P001").
     * @param numeroDocumentoFiscalFinal Número efectivo a enviar (max(local, remoto+1)).
     */
    fun build(
        context: InvoiceVEContext,
        serie: String,
        numeroDocumentoFiscalFinal: String,
    ): VenezuelaHkaDocumentoWrapper {
        val porIva = agruparIvaPorAlicuota(context.detalles)
        val igtfCalculado = calcularIgtf(context)
        val items = context.detalles.map { buildItemVE(it) }
        val transaccionId =
            transaccionIdDeterminista(
                idFactura = context.factura.idFactura,
                numeroFormateado = numeroDocumentoFiscalFinal,
            )
        val moneda = resolverMonedaSecundaria(context)

        return VenezuelaHkaDocumentoWrapper(
            documento =
                VenezuelaHkaDocumento(
                    codigoSucursalEmisor = context.caja.codigoSucursalEmisor,
                    datosTransaccion =
                        buildDatosTransaccion(context, serie, numeroDocumentoFiscalFinal, transaccionId),
                    listaItems = items,
                    totalesSubTotales = buildTotales(context, porIva, igtfCalculado, moneda, transaccionId),
                ),
        )
    }

    /**
     * Helper histórico: número efectivo a enviar considerando el remoto del PAC.
     *
     * @deprecated FASE 1.1 — Brief item 3. La responsabilidad de aplicar
     * `max(contadorLocal, remoto+1)` se movió a [VenezuelaElectronicInvoiceRepository.reserveAtLeast],
     * por lo que la Strategy YA pasa el número definitivo a `build(...)`. Este
     * método se conserva provisionalmente para retrocompatibilidad de tests;
     * será eliminado al limpiar la suite.
     */
    @Deprecated(
        "La reserva via reserveAtLeast calcula el número final; no uses este max() aquí.",
        level = DeprecationLevel.WARNING,
    )
    open fun numeroPacFormateado(
        reservado: VECorrelativoReservado,
        remoto: Int?,
    ): String {
        val candidato = maxOf(reservado.numero, (remoto ?: 0) + 1)
        return candidato.toString().padStart(reservado.formato.coerceAtLeast(1), '0')
    }

    // ─── Datos de transacción ────────────────────────────────────────────────

    private fun buildDatosTransaccion(
        ctx: InvoiceVEContext,
        serie: String,
        numeroDocumentoFiscalFinal: String,
        transaccionId: String,
    ): VenezuelaHkaDatosTransaccion =
        VenezuelaHkaDatosTransaccion(
            tipoEmision = ctx.config.tipoEmision,
            tipoDocumento = ctx.factura.tipoDocumento,
            numeroDocumentoFiscal = numeroDocumentoFiscalFinal,
            puntoFacturacionFiscal = ctx.caja.puntoFacturacionFiscal,
            fechaEmision = formatFechaEmision(ctx.factura.fechaFactura),
            procesoGeneracion = ctx.config.procesoGeneracion,
            transaccionId = transaccionId,
            cliente = buildClienteVE(ctx),
            serie = serie,
            sucursal = ctx.caja.serieSucursal,
        )

    // ─── IGTF ──────────────────────────────────────────────────────────────────

    private data class IgtfResult(
        val baseImponible: BigDecimal,
        val porcentaje: BigDecimal,
        val monto: BigDecimal,
    )

    /**
     * IGTF: base imponible = monto en VES de las formas de pago en divisa
     * (montoEnDivisa * tasa). El porcentaje sale de [InvoiceVEContext.config.igtf].
     *
     * NO se aplica automáticamente 3 %: respeta el dato del tenant. Si la base
     * sale en cero (no hubo pago en divisa) el nodo IGTF no se emite.
     */
    private fun calcularIgtf(ctx: InvoiceVEContext): IgtfResult? =
        run {
            val pct = ctx.config.igtf.takeIf { it > BigDecimal.ZERO } ?: return null
            val tasa = ctx.factura.tasa.takeIf { it > BigDecimal.ONE } ?: BigDecimal.ONE
            val baseEnVes =
                ctx.formasPago
                    .filter { it.esDivisa }
                    .fold(BigDecimal.ZERO) { acc, fp -> acc.add(fp.monto.multiply(tasa)) }
                    .bigDecimalMoney()
            if (baseEnVes <= BigDecimal.ZERO) return null

            val monto =
                baseEnVes
                    .multiply(pct.divide(BigDecimal("100"), PERCENT_CALCULATION_SCALE, RoundingMode.HALF_UP))
                    .bigDecimalMoney()
            return IgtfResult(
                baseImponible = baseEnVes,
                porcentaje = pct.bigDecimalMoney(),
                monto = monto,
            )
        }

    // ─── Moneda secundaria ────────────────────────────────────────────────────

    private data class MonedaSecundaria(
        val multimoneda: Boolean,
        val tasa: BigDecimal,
        val montoTotalMonedaSecundaria: BigDecimal?,
    )

    /**
     * El documento SIEMPRE se emite en VES (moneda base del PAC VE).
     * Multimoneda: si aplica, se reporta el total en la divisa secundaria.
     */
    private fun resolverMonedaSecundaria(ctx: InvoiceVEContext): MonedaSecundaria {
        val multimoneda = ctx.factura.multiMoneda.equals("SI", ignoreCase = true)
        val tasa = ctx.factura.tasa.takeIf { it > BigDecimal.ONE } ?: BigDecimal.ONE
        val montoTotalFacturaVES = ctx.factura.totalizarTotalGeneral.bigDecimalMoney()
        val montoTotalMonedaSecundaria =
            if (multimoneda && tasa > BigDecimal.ONE) {
                montoTotalFacturaVES.divide(tasa, MONEY_SCALE, RoundingMode.HALF_UP)
            } else {
                null
            }
        return MonedaSecundaria(multimoneda, tasa, montoTotalMonedaSecundaria)
    }

    // ─── Totales ──────────────────────────────────────────────────────────────

    private fun buildTotales(
        ctx: InvoiceVEContext,
        porIva: IvaPorAlicuota,
        igtfCalculado: IgtfResult?,
        moneda: MonedaSecundaria,
        transaccionId: String,
    ): VenezuelaHkaTotalesSubTotales {
        val totalPrecioNeto = sumPrecioNeto(ctx)
        val totalIva = ctx.factura.ivaTotalFactura.bigDecimalMoney()
        val totalDescuento = ctx.factura.descuentosItemFactura.bigDecimalMoney()
        val montoTotalFacturaVES = ctx.factura.totalizarTotalGeneral.bigDecimalMoney()

        // Total Monto Gravado = base imponible gravada (general + reducido).
        val totalMontoGravado =
            porIva.totalGeneral
                .add(porIva.totalReducido)
                .bigDecimalMoney()

        return VenezuelaHkaTotalesSubTotales(
            totalPrecioNeto = totalPrecioNeto.format(),
            totalIva = totalIva.format(),
            totalDescuento = totalDescuento.format(),
            totalAlicuotaGeneral = porIva.totalGeneral.format(),
            totalAlicuotaReducido = porIva.totalReducido.format(),
            totalAlicuotaExento = porIva.totalExento.format(),
            totalIsc =
                ctx.detalles
                    .sumOf { it.importeIsc ?: BigDecimal.ZERO }
                    .takeIf { it > BigDecimal.ZERO }
                    ?.format(),
            totalAcarreo =
                ctx.detalles
                    .sumOf { it.importeAcarreo ?: BigDecimal.ZERO }
                    .takeIf { it > BigDecimal.ZERO }
                    ?.format(),
            totalSeguro =
                ctx.detalles
                    .sumOf { it.importeSeguro ?: BigDecimal.ZERO }
                    .takeIf { it > BigDecimal.ZERO }
                    ?.format(),
            totalMontoGravado = totalMontoGravado.format(),
            montoTotalFactura = montoTotalFacturaVES.format(),
            montoTotalMonedaSecundaria = moneda.montoTotalMonedaSecundaria?.format(),
            igtf =
                igtfCalculado?.let {
                    VenezuelaHkaIgtf(
                        baseImponible = it.baseImponible.format(),
                        porcentaje = it.porcentaje.format(),
                        montoIgtf = it.monto.format(),
                    )
                },
            listaFormaPago = buildFormasPago(ctx),
            totalValorRecibido = sumFormasPago(ctx).format(),
            vuelto = calcularVuelto(ctx)?.format(),
            tiempoPago = "1", // Contado - FASE 1 sólo soporta pago inmediato.
            nroItems = ctx.detalles.size.toString(),
            totalTodosItems = totalPrecioNeto.add(totalIva).format(),
            tasaCambio = if (moneda.multimoneda) moneda.tasa.format() else null,
            transaccionId = transaccionId,
            montoEnLetras = montoEnLetras(montoTotalFacturaVES),
        )
    }

    // ─── Formas de pago ────────────────────────────────────────────────────────

    private fun buildFormasPago(ctx: InvoiceVEContext): List<VenezuelaHkaFormaPago> =
        ctx.formasPago
            .map { fp -> buildFormaPago(ctx, fp) }
            .ifEmpty { listOf(VenezuelaHkaFormaPago(formaPagoFact = "01", montoPagado = BigDecimal.ZERO.format())) }

    private fun buildFormaPago(
        ctx: InvoiceVEContext,
        fp: VEFormaPagoData,
    ): VenezuelaHkaFormaPago {
        val cambio = calcularCambioFormaPago(fp)
        return VenezuelaHkaFormaPago(
            formaPagoFact = fp.formaPagoFact ?: "01",
            montoPagado = fp.monto.format(),
            descripcion = fp.descripcion.takeIf { it.isNotBlank() },
            // En pago en divisa se reporta el monto convertido a VES.
            montoMonedaSecundaria =
                if (fp.esDivisa) {
                    fp.monto.multiply(ctx.factura.tasa).format()
                } else {
                    null
                },
            montoRecibido = fp.montoRecibido?.takeIf { it > BigDecimal.ZERO }?.format(),
            cambio = cambio?.format(),
        )
    }

    private fun calcularCambioFormaPago(fp: VEFormaPagoData): BigDecimal? {
        val recibido = fp.montoRecibido?.takeIf { it > BigDecimal.ZERO } ?: return null
        val cambio = recibido.subtract(fp.monto)
        return cambio.takeIf { it > BigDecimal.ZERO }
    }

    private fun calcularVuelto(ctx: InvoiceVEContext): BigDecimal? {
        val totalRecibido = sumFormasPago(ctx)
        val vuelto = totalRecibido.subtract(ctx.factura.totalizarTotalGeneral.bigDecimalMoney())
        return vuelto.takeIf { it > BigDecimal.ZERO }
    }
}
