package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceVEContext
import com.amaxoniaerp.features.electronicinvoice.domain.VECorrelativoReservado
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFormaPagoData
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

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
 *    total en VES (base del documento) y se Angregage `montoTotalMonedaSecundaria`
 *    (la divisa) más `tasaCambio`.
 * 7. NO se incluyen campos inventados: solo los del Swagger VE.
 */
open class VenezuelaHkaPayloadBuilder {

    companion object {
        private const val ALICUOTA_GENERAL_PCT = "16.00"
        private const val ALICUOTA_REDUCIDO_PCT = "8.00"
        private const val ALICUOTA_EXENTO_PCT = "0.00"
        private const val MONEY_SCALE = 2
        private const val QTY_SCALE = 3
    }

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

        val items = context.detalles.map { buildItem(it) }
        val totalPrecioNeto = sumPrecioNeto(context)
        val totalIva = context.factura.ivaTotalFactura.bigDecimalMoney()
        val totalDescuento = context.factura.descuentosItemFactura.bigDecimalMoney()

        // El documento SIEMPRE se emite en VES (moneda base del PAC VE).
        val montoTotalFacturaVES = context.factura.totalizarTotalGeneral.bigDecimalMoney()

        // Multimoneda: si aplica, se reporta el total en la divisa secundaria.
        val multimoneda = context.factura.multiMoneda.equals("SI", ignoreCase = true)
        val tasa = context.factura.tasa.takeIf { it > BigDecimal.ONE } ?: BigDecimal.ONE
        val montoTotalMonedaSecundaria = if (multimoneda && tasa > BigDecimal.ONE) {
            montoTotalFacturaVES.divide(tasa, MONEY_SCALE, RoundingMode.HALF_UP)
        } else null

        val transaccionId = transaccionIdDeterminista(
            idFactura = context.factura.idFactura,
            numeroFormateado = numeroDocumentoFiscalFinal,
        )

        // Total Monto Gravado = base imponible gravada (general + reducido).
        val totalMontoGravado = porIva.totalGeneral
            .add(porIva.totalReducido)
            .bigDecimalMoney()

        return VenezuelaHkaDocumentoWrapper(
            documento = VenezuelaHkaDocumento(
                codigoSucursalEmisor = context.caja.codigoSucursalEmisor,
                datosTransaccion = VenezuelaHkaDatosTransaccion(
                    tipoEmision = context.config.tipoEmision,
                    tipoDocumento = context.factura.tipoDocumento,
                    numeroDocumentoFiscal = numeroDocumentoFiscalFinal,
                    puntoFacturacionFiscal = context.caja.puntoFacturacionFiscal,
                    fechaEmision = formatFechaEmision(context.factura.fechaFactura),
                    procesoGeneracion = context.config.procesoGeneracion,
                    transaccionId = transaccionId,
                    cliente = buildCliente(context),
                    serie = serie,
                    sucursal = context.caja.serieSucursal,
                ),
                listaItems = items,
                totalesSubTotales = VenezuelaHkaTotalesSubTotales(
                    totalPrecioNeto = totalPrecioNeto.format(),
                    totalIva = totalIva.format(),
                    totalDescuento = totalDescuento.format(),
                    totalAlicuotaGeneral = porIva.totalGeneral.format(),
                    totalAlicuotaReducido = porIva.totalReducido.format(),
                    totalAlicuotaExento = porIva.totalExento.format(),
                    totalIsc = context.detalles.sumOf { it.importeIsc ?: BigDecimal.ZERO }
                        .takeIf { it > BigDecimal.ZERO }?.format(),
                    totalAcarreo = context.detalles.sumOf { it.importeAcarreo ?: BigDecimal.ZERO }
                        .takeIf { it > BigDecimal.ZERO }?.format(),
                    totalSeguro = context.detalles.sumOf { it.importeSeguro ?: BigDecimal.ZERO }
                        .takeIf { it > BigDecimal.ZERO }?.format(),
                    totalMontoGravado = totalMontoGravado.format(),
                    montoTotalFactura = montoTotalFacturaVES.format(),
                    montoTotalMonedaSecundaria = montoTotalMonedaSecundaria?.format(),
                    igtf = igtfCalculado?.let {
                        VenezuelaHkaIgtf(
                            baseImponible = it.baseImponible.format(),
                            porcentaje = it.porcentaje.format(),
                            montoIgtf = it.monto.format(),
                        )
                    },
                    listaFormaPago = buildFormasPago(context),
                    totalValorRecibido = sumFormasPago(context).format(),
                    vuelto = calcularVuelto(context)?.format(),
                    tiempoPago = "1", // Contado - FASE 1 sólo soporta pago inmediato.
                    nroItems = context.detalles.size.toString(),
                    totalTodosItems = totalPrecioNeto.add(totalIva).format(),
                    tasaCambio = if (multimoneda) tasa.format() else null,
                    transaccionId = transaccionId,
                    montoEnLetras = montoEnLetras(montoTotalFacturaVES),
                ),
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
    open fun numeroPacFormateado(reservado: VECorrelativoReservado, remoto: Int?): String {
        val candidato = maxOf(reservado.numero, (remoto ?: 0) + 1)
        return candidato.toString().padStart(reservado.formato.coerceAtLeast(1), '0')
    }

    // ─── Cliente (comprador) ───────────────────────────────────────────────────

    private fun buildCliente(ctx: InvoiceVEContext): VenezuelaHkaCliente =
        ctx.comprador.let { c ->
            VenezuelaHkaCliente(
                nombreRazonSocial = c.nombreRazonSocial.ifBlank { "CONSUMIDOR FINAL" },
                numeroRif = c.rif.ifBlank { "V000000000" },
                direccion = c.direccion?.takeIf { it.isNotBlank() },
                telefono = c.telefono?.takeIf { it.isNotBlank() },
                correoElectronico = c.email?.takeIf { it.isNotBlank() },
            )
        }

    // ─── Items ─────────────────────────────────────────────────────────────────

    private fun buildItem(det: VEDetalleData): VenezuelaHkaItem {
        val valorIva = det.totalConIva.subtract(det.totalSinIva).max(BigDecimal.ZERO)
        return VenezuelaHkaItem(
            descripcion = det.descripcion,
            codigo = det.codigo,
            referencia = det.referencia?.takeIf { it.isNotBlank() },
            unidadMedida = det.unidadEmpaque?.takeIf { it.isNotBlank() } ?: "UND",
            cantidad = det.cantidad.setScale(QTY_SCALE, RoundingMode.HALF_UP).toPlainString(),
            precioUnitario = det.precioSinIva.format(),
            precioUnitarioDescuento = if (det.cantidad > BigDecimal.ZERO && det.montoDescuento > BigDecimal.ZERO) {
                det.montoDescuento.divide(det.cantidad, MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()
            } else null,
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

    // ─── IVA por alícuota ──────────────────────────────────────────────────────

    private data class IvaPorAlicuota(
        val totalGeneral: BigDecimal,
        val totalReducido: BigDecimal,
        val totalExento: BigDecimal,
    )

    private fun agruparIvaPorAlicuota(detalles: List<VEDetalleData>): IvaPorAlicuota {
        var general = BigDecimal.ZERO
        var reducido = BigDecimal.ZERO
        var exento = BigDecimal.ZERO
        for (det in detalles) {
            val base = det.totalSinIva.bigDecimalMoney()
            when {
                isAprox(det.piva, BigDecimal("16")) -> general = general.add(base)
                isAprox(det.piva, BigDecimal("8")) -> reducido = reducido.add(base)
                else -> exento = exento.add(base)
            }
        }
        return IvaPorAlicuota(
            general.bigDecimalMoney(),
            reducido.bigDecimalMoney(),
            exento.bigDecimalMoney(),
        )
    }

    private fun alicuotaCodigo(piva: BigDecimal): String = when {
        isAprox(piva, BigDecimal("16")) -> ALICUOTA_GENERAL_PCT
        isAprox(piva, BigDecimal("8")) -> ALICUOTA_REDUCIDO_PCT
        else -> ALICUOTA_EXENTO_PCT
    }

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
    private fun calcularIgtf(ctx: InvoiceVEContext): IgtfResult? {
        val pct = ctx.config.igtf.takeIf { it > BigDecimal.ZERO } ?: return null
        val tasa = ctx.factura.tasa.takeIf { it > BigDecimal.ONE } ?: BigDecimal.ONE
        val baseEnVes = ctx.formasPago
            .filter { it.esDivisa }
            .fold(BigDecimal.ZERO) { acc, fp -> acc.add(fp.monto.multiply(tasa)) }
            .bigDecimalMoney()
        if (baseEnVes <= BigDecimal.ZERO) return null

        val monto = baseEnVes
            .multiply(pct.divide(BigDecimal("100"), 6, RoundingMode.HALF_UP))
            .bigDecimalMoney()
        return IgtfResult(
            baseImponible = baseEnVes,
            porcentaje = pct.bigDecimalMoney(),
            monto = monto,
        )
    }

    // ─── Formas de pago ────────────────────────────────────────────────────────

    private fun buildFormasPago(ctx: InvoiceVEContext): List<VenezuelaHkaFormaPago> =
        ctx.formasPago.map { fp -> buildFormaPago(ctx, fp) }
            .ifEmpty { listOf(VenezuelaHkaFormaPago(formaPagoFact = "01", montoPagado = BigDecimal.ZERO.format())) }

    private fun buildFormaPago(ctx: InvoiceVEContext, fp: VEFormaPagoData): VenezuelaHkaFormaPago {
        val cambio = calcularCambioFormaPago(fp)
        return VenezuelaHkaFormaPago(
            formaPagoFact = fp.formaPagoFact ?: "01",
            montoPagado = fp.monto.format(),
            descripcion = fp.descripcion.takeIf { it.isNotBlank() },
            // En pago en divisa se reporta el monto convertido a VES.
            montoMonedaSecundaria = if (fp.esDivisa) {
                fp.monto.multiply(ctx.factura.tasa).format()
            } else null,
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

    // ─── Utilidades ────────────────────────────────────────────────────────────

    private fun sumFormasPago(ctx: InvoiceVEContext): BigDecimal =
        ctx.formasPago.fold(BigDecimal.ZERO) { acc, fp -> acc.add(fp.monto) }
            .bigDecimalMoney()

    private fun sumPrecioNeto(ctx: InvoiceVEContext): BigDecimal =
        ctx.detalles.fold(BigDecimal.ZERO) { acc, d -> acc.add(d.totalSinIva) }
            .bigDecimalMoney()

    private fun isAprox(a: BigDecimal, b: BigDecimal): Boolean =
        a.setScale(2, RoundingMode.HALF_UP) == b.setScale(2, RoundingMode.HALF_UP)

    private fun BigDecimal.format(): String = setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()

    /** Normaliza cualquier BigDecimal a escala monetaria estándar (2). */
    private fun BigDecimal.bigDecimalMoney(): BigDecimal = setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    private fun formatFechaEmision(fecha: String?): String {
        if (fecha.isNullOrBlank()) {
            return java.time.LocalDate.now().toString() + "T00:00:00"
        }
        return try {
            val d = java.time.LocalDate.parse(fecha.trim().take(10))
            d.toString() + "T00:00:00"
        } catch (_: Exception) {
            java.time.LocalDate.now().toString() + "T00:00:00"
        }
    }

    /**
     * `transaccionId` determinista: SHA-256(idFactura + numeroFormateado),
     * hex truncado a 32 caracteres. Permite idempotencia y trazabilidad sin
     * exponer el idFactura en el PAC.
     */
    private fun transaccionIdDeterminista(idFactura: String, numeroFormateado: String): String {
        val seed = (idFactura + "|" + numeroFormateado).toByteArray(Charsets.UTF_8)
        val sha = MessageDigest.getInstance("SHA-256").digest(seed)
        // Top 16 bytes → 32 chars hex.
        return java.util.HexFormat.of().formatHex(sha.copyOfRange(0, 16))
    }

    /**
     * Conversión del monto a palabras en español (convención venezolana).
     *
     * Sustituye al placeholder "MONTO EN LETRAS: X" que existía antes. El nodo
     * HKA `montoEnLetras` es **obligatorio** según el DTO
     * `VenezuelaHkaTotalesSubTotales`. Esta implementaciσn cubre:
     *
     *   - Enteros 0..999 999 999 999 (billones no soportados; el PAC VE no los
     *     admite con la escala monetaria usada).
     *   - Centavos siempre con "/100" (formato venezolano usado por SENIAT).
     *   - Singular/plural de "millón/millones", "mil" (invariable), "bolívares"
     *     (la moneda siempre se expresa en plural, salvo "UN BOLÍVAR" exacto).
     *
     * Ejemplos:
     *   -    0.00 → "CERO BOLÍVARES CON 00/100"
     *   -    1.00 → "UN BOLÍVAR CON 00/100"
     *   -   16.50 → "DIECISÉIS BOLÍVARES CON 50/100"
     *   -  100.00 → "CIEN BOLÍVARES CON 00/100"
     *   -  116.00 → "CIENTO DIECISÉIS BOLÍVARES CON 00/100"
     *   - 1000.00 → "MIL BOLÍVARES CON 00/100"
     *   - 1500000.00 → "UN MILLÓN QUINIENTOS MIL BOLÍVARES CON 00/100"
     */
    private fun montoEnLetras(total: BigDecimal): String {
        val escala = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        val partes = escala.toPlainString().split('.')
        val entero = partes[0].toBigInteger()
        val centavos = partes.getOrElse(1) { "00" }

        val enteroLetras = when {
            entero == java.math.BigInteger.ZERO -> "CERO"
            entero == java.math.BigInteger.ONE -> "UN"
            else -> enterosALetras(entero)
        }
        val moneda = if (entero == java.math.BigInteger.ONE) "BOLÍVAR" else "BOLÍVARES"
        return "$enteroLetras $moneda CON $centavos/100"
    }

    /** Conversión de enteros (1..999 999 999 999) a palabras en español (VE). */
    private fun enterosALetras(n: java.math.BigInteger): String {
        require(n >= java.math.BigInteger.ZERO) { "Solo se soportan enteros no negativos" }
        val unidades = arrayOf(
            "", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE",
            "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
        )
        val decenas = arrayOf(
            "", "", "VEINTI", "TREINTA", "CUARENTA", "CINCUENTA",
            "SESENTA", "SETENTA", "OCHENTA", "NOVENTA",
        )
        val centenas = arrayOf(
            "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS",
        )

        val millones = java.math.BigInteger("1000000")
        val mil = java.math.BigInteger("1000")
        val cien = java.math.BigInteger("100")

        if (n == java.math.BigInteger.ZERO) return "CERO"
        if (n == cien) return "CIEN" // excepción: 100 → "CIEN", no "CIENTO"

        val parts = mutableListOf<String>()

        // Millones (1..999)
        if (n >= millones) {
            val mm = n.divide(millones).mod(millones)
            if (mm.signum() > 0) {
                parts += when (mm) {
                    java.math.BigInteger.ONE -> "UN MILLÓN"
                    else -> "${grupoTres(mm.toInt(), unidades, decenas, centenas)} MILLONES"
                }
            }
        }
        // Billones no soportados: si n >= 10^12, dejamos al PAC que lo rechace;
        // no ocurre con escala monetaria VE (DECIMAL 20,2).

        // Miles (1..999 999)
        val restoTrasMillon = n.mod(millones)
        val miles = restoTrasMillon.divide(mil)
        if (miles.signum() > 0) {
            // "MIL" es invariable; "UN MIL" esprefrible evitar (se omite "UN").
            val milesLetras = if (miles == java.math.BigInteger.ONE) {
                "MIL"
            } else {
                "${grupoTres(miles.toInt(), unidades, decenas, centenas)} MIL"
            }
            parts += milesLetras
        }

        // Unidad final (1..999)
        val unidad = restoTrasMillon.mod(mil)
        if (unidad.signum() > 0) {
            parts += grupoTres(unidad.toInt(), unidades, decenas, centenas)
        }

        return parts.joinToString(" ").trim()
    }

    /** Convierte un bloque de 3 dígitos (0..999) a letras. */
    private fun grupoTres(
        n: Int,
        unidades: Array<String>,
        decenas: Array<String>,
        centenas: Array<String>,
    ): String {
        if (n == 0) return ""
        if (n == 100) return "CIEN"
        val sb = StringBuilder()
        var resto = n
        if (resto >= 100) {
            sb.append(centenas[resto / 100]).append(' ')
            resto %= 100
        }
        when {
            resto < 20 -> sb.append(unidades[resto])
            resto == 20 -> sb.append("VEINTE")
            resto in 21..29 -> sb.append(decenas[2]).append(unidades[resto - 20]) // VEINTIUNO..VEINTINUEVE
            else -> {
                val d = resto / 10
                val u = resto % 10
                sb.append(decenas[d])
                if (u > 0) sb.append(" Y ").append(unidades[u])
            }
        }
        return sb.toString().trim()
    }
}
