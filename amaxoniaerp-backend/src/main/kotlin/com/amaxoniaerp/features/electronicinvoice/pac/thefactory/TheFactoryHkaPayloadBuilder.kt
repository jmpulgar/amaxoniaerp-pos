package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builder Pattern: transforma el [InvoiceFEContext] (datos crudos de la DB)
 * en un [TheFactoryHkaDocumentoWrapper] listo para enviar al API de The Factory HKA.
 *
 * Toda la lógica de negocio de transformación vive aquí:
 * - Mapeo de tasas ITBMS (7→01, 10→02, 15→03, otro→00)
 * - Validación/normalización de teléfono, correo, RUC
 * - Manejo de clientes extranjeros (tipo 04)
 * - Cálculo de descuentos por unidad
 * - Normalización de descripciones (mínimo 5 caracteres)
 * - Mapeo de formas de pago al catálogo The Factory
 * - Manejo de ISC y OTI
 */
class TheFactoryHkaPayloadBuilder {
    companion object {
        private val PHONE_PATTERN = Regex("^\\d{4}-\\d{4}$")
        private val EMAIL_PATTERN = Regex("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")
        private const val DEFAULT_PHONE = "9999-9999"
        private const val DEFAULT_EMAIL = "email@correo.com"
        private const val DEFAULT_RUC = "00000"
        private const val DEFAULT_CPBS = "5411"
        private const val DEFAULT_CPBS_ABREV = "54"
        private const val MIN_DESCRIPTION_LENGTH = 5
        private const val MIN_FORMA_PAGO_DESC_LENGTH = 10
        private const val MIN_IDENTIFICATION_LENGTH = 5
        private const val EMAIL_MIN_LENGTH = 7
        private const val ADDRESS_MIN_LENGTH = 4
        private const val DISCOUNT_SCALE = 4
        private const val QUANTITY_SCALE = 3
        private const val ITBMS_RATE_7 = 7.0
        private const val ITBMS_RATE_10 = 10.0
        private const val ITBMS_RATE_15 = 15.0
        private const val ISO_DATE_LENGTH = 10

        // Siglas de formas de pago a ignorar
        private val IGNORED_PAYMENT_SIGLAS = setOf("CRED", "NC", "RETITBMSINGRE")
    }

    /**
     * Construye el payload completo a partir del contexto de la factura.
     */
    fun build(context: InvoiceFEContext): TheFactoryHkaDocumentoWrapper = build(context, null)

    /**
     * Variante interna para documentos que referencian otro documento fiscal.
     * El camino público de factura mantiene exactamente el payload anterior al
     * pasar una lista de referencias nula.
     */
    internal fun build(
        context: InvoiceFEContext,
        documentosFiscalesReferenciados: List<TheFactoryHkaDocFiscalRef>?,
    ): TheFactoryHkaDocumentoWrapper =
        TheFactoryHkaDocumentoWrapper(
            documento =
                TheFactoryHkaDocumento(
                    codigoSucursalEmisor = context.codigoSucursalEmisor,
                    datosTransaccion = buildDatosTransaccion(context, documentosFiscalesReferenciados),
                    listaItems = buildItems(context.detalles, normalizeTipoClienteFE(context.cliente.tipoClienteFE)),
                    totalesSubTotales = buildTotales(context),
                ),
        )

    // ─── Datos de Transacción ────────────────────────────────────────────────

    private fun buildDatosTransaccion(
        ctx: InvoiceFEContext,
        documentosFiscalesReferenciados: List<TheFactoryHkaDocFiscalRef>?,
    ): TheFactoryHkaDatosTransaccion {
        val factura = ctx.factura
        val config = ctx.config

        // tipo_documento: se concatena "0" al frente (ej. "1" -> "01", "3" -> "03")
        val tipoDocumento = normalizeToTwoDigits(factura.tipoDocumento)

        // Si tipoDocumento es "03" (Exportación), forzar destinoOperacion a "2"
        val destinoOperacion = if (tipoDocumento == "03") "2" else config.destinoOperacion

        // Fecha emisión en formato ISO 8601
        val fechaEmision = formatFechaEmisionForPayload(factura.fechaFactura)

        // Contingencia: solo aplica si tipoEmision es "02" o "04".
        // En modo "02" el flujo legacy usa la fecha actual y motivo fijo.
        val esContingencia = config.tipoEmision == "02" || config.tipoEmision == "04"
        val fechaInicioContingencia = if (config.tipoEmision == "02") fechaEmision else config.fechaInicioContingencia
        val motivoContingencia = if (config.tipoEmision == "02") "Problemas de comunicación interna." else config.motivoContingencia

        return TheFactoryHkaDatosTransaccion(
            tipoEmision = config.tipoEmision,
            tipoDocumento = tipoDocumento,
            numeroDocumentoFiscal = factura.numeroDocumentoFiscal,
            puntoFacturacionFiscal = ctx.puntoFacturacionFiscal,
            fechaEmision = fechaEmision,
            naturalezaOperacion = factura.naturalezaOperacion,
            tipoOperacion = factura.tipoOperacion,
            destinoOperacion = destinoOperacion,
            formatoCAFE = factura.formatoCAFE,
            entregaCAFE = factura.entregaCAFE,
            envioContenedor = factura.envioContenedor,
            procesoGeneracion = config.procesoGeneracion,
            tipoVenta = factura.tipoVenta,
            informacionInteres = factura.observacion?.takeIf { it.isNotBlank() },
            fechaInicioContingencia = if (esContingencia) fechaInicioContingencia else null,
            motivoContingencia = if (esContingencia) motivoContingencia else null,
            cliente = buildCliente(ctx),
            listaDocsFiscalReferenciados = documentosFiscalesReferenciados,
        )
    }

    // ─── Cliente ─────────────────────────────────────────────────────────────

    private fun buildCliente(ctx: InvoiceFEContext): TheFactoryHkaCliente {
        val cliente = ctx.cliente

        // TipoClienteFE: si viene "0" o vacío, forzar a "02" (Consumidor Final)
        val tipoClienteFE = normalizeTipoClienteFE(cliente.tipoClienteFE)

        val esExtranjero = tipoClienteFE == "04"

        val tipoContribuyente =
            when {
                esExtranjero -> null
                tipoClienteFE == "02" && cliente.tipoContribuyente in setOf("", "0", "2") -> "1"
                tipoClienteFE == "02" -> "1"
                else -> cliente.tipoContribuyente
            }

        // RUC: si es extranjero, vaciar. Si tiene menos de 5 chars, enviar "00000"
        val ruc =
            when {
                esExtranjero -> null
                cliente.identificacion.length < MIN_IDENTIFICATION_LENGTH -> DEFAULT_RUC
                else -> cliente.identificacion
            }

        val dv = if (esExtranjero) null else cliente.dv

        // Teléfono: validar formato "9999-9999"
        val telefono =
            cliente.telefono?.let {
                if (it.matches(PHONE_PATTERN)) it else DEFAULT_PHONE
            } ?: DEFAULT_PHONE

        val correoBase = cliente.correo?.takeIf { it.isNotBlank() } ?: DEFAULT_EMAIL
        val correo =
            if (ctx.factura.tipoFactura == "factura_pos") {
                null
            } else {
                val validated = if (correoBase.matches(EMAIL_PATTERN)) correoBase else DEFAULT_EMAIL
                validated.padStart(EMAIL_MIN_LENGTH, '0')
            }
        val direccion = (cliente.direccion?.takeIf { it.isNotBlank() } ?: " ").padStart(ADDRESS_MIN_LENGTH, '-')

        return TheFactoryHkaCliente(
            tipoClienteFE = tipoClienteFE,
            tipoContribuyente = tipoContribuyente,
            numeroRUC = ruc,
            digitoVerificadorRUC = dv,
            razonSocial = cliente.nombre,
            direccion = direccion,
            codigoUbicacion = cliente.codigoUbicacion,
            telefono1 = telefono,
            correoElectronico1 = correo,
            tipoIdentificacion = if (esExtranjero) "01" else null,
            nroIdentificacionExtranjero = if (esExtranjero) cliente.identificacion else null,
            pais = if (esExtranjero) null else cliente.paisIso,
            paisExtranjero = if (esExtranjero) cliente.paisExtranjeroIso else null,
        )
    }

    // ─── Items ───────────────────────────────────────────────────────────────

    private fun buildItems(
        detalles: List<FEDetalleData>,
        tipoClienteFE: String,
    ): List<TheFactoryHkaItem> {
        val esGobierno = tipoClienteFE == "03"

        return detalles.map { det ->
            // Descripción: rellenar con puntos si tiene menos de 5 caracteres
            val descripcion =
                if (det.descripcion.length < MIN_DESCRIPTION_LENGTH) {
                    det.descripcion.padEnd(MIN_DESCRIPTION_LENGTH, '.')
                } else {
                    det.descripcion
                }
            val codigoCPBS = if (esGobierno) det.codigoCPBS?.takeIf { it.isNotBlank() } else DEFAULT_CPBS
            val codigoCPBSAbrev = if (esGobierno) det.codigoCPBSAbrev?.takeIf { it.isNotBlank() } else DEFAULT_CPBS_ABREV

            // Tasa ITBMS: mapear porcentaje a código catálogo
            val tasaITBMS = mapTasaITBMS(det.piva)

            // Descuento por unidad: montoDescuento / cantidad (4 decimales)
            val precioUnitarioDescuento =
                if (det.cantidad > 0 && det.montoDescuento > 0) {
                    (det.montoDescuento / det.cantidad).formatDecimals(DISCOUNT_SCALE)
                } else {
                    null
                }

            // Valor ITBMS: diferencia entre totalConIva y totalSinIva
            val valorITBMS =
                (det.totalConIva - det.totalSinIva)
                    .coerceAtLeast(0.0)
                    .formatDecimals(2)

            // ISC
            val tasaISC = det.porcentajeIsc?.takeIf { it > 0 }?.formatDecimals(2)
            val valorISC = det.importeIsc?.takeIf { it > 0 }?.formatDecimals(2)

            // OTI
            val listaOTI =
                if (det.idOti != null && det.idOti > 0 && det.importeOti != null && det.importeOti > 0) {
                    listOf(
                        TheFactoryHkaItemOTI(
                            tasaOTI = det.idOti.toString(),
                            valorTasa = det.importeOti.formatDecimals(2),
                        ),
                    )
                } else {
                    null
                }

            TheFactoryHkaItem(
                descripcion = descripcion,
                codigo = det.codigo,
                unidadMedida = det.unidadMedida?.takeIf { it.isNotBlank() } ?: "und",
                cantidad = det.cantidad.formatDecimals(QUANTITY_SCALE),
                precioUnitario = det.precioSinIva.formatDecimals(2),
                precioUnitarioDescuento = precioUnitarioDescuento,
                precioItem = det.totalSinIva.formatDecimals(2),
                valorTotal = det.totalConIva.formatDecimals(2),
                tasaITBMS = tasaITBMS,
                valorITBMS = valorITBMS,
                tasaISC = tasaISC,
                valorISC = valorISC,
                unidadMedidaCPBS = if (esGobierno) "und" else null,
                codigoCPBS = codigoCPBS,
                codigoCPBSAbrev = codigoCPBSAbrev,
                listaItemOTI = listaOTI,
            )
        }
    }

    // ─── Totales y Subtotales ────────────────────────────────────────────────

    private fun buildTotales(ctx: InvoiceFEContext): TheFactoryHkaTotalesSubTotales {
        val factura = ctx.factura

        // Formas de pago: filtrar las ignoradas y mapear al catálogo
        val formasPago = buildFormasPago(ctx.formasPago, ctx.vuelto)

        // Bonificaciones globales
        val bonificaciones =
            if (factura.totalizarDescuentoGlobal > 0) {
                listOf(
                    TheFactoryHkaDescBonificacion(
                        descDescuento = "Descuento Global",
                        montoDescuento = factura.totalizarDescuentoGlobal.formatDecimals(2),
                    ),
                )
            } else {
                null
            }

        // Sumarizar OTI globales
        val totalOTI = buildTotalOTI(ctx.detalles)

        // totalISC: suma de ISC de todos los items
        val totalISC = ctx.detalles.sumOf { it.importeIsc ?: 0.0 }

        // totalMontoGravado = ITBMS + ISC + OTI
        val totalOTISum = ctx.detalles.sumOf { it.importeOti ?: 0.0 }
        val totalMontoGravado = factura.ivaTotalFactura + totalISC + totalOTISum

        // tiempoPago: "1" contado, "2" crédito, "3" gobierno
        val tiempoPago =
            when {
                ctx.factura.tipoVenta == "2" -> "2" // Crédito
                else -> "1" // Contado
            }

        return TheFactoryHkaTotalesSubTotales(
            totalPrecioNeto = factura.montoItemsFactura.formatDecimals(2),
            totalITBMS = factura.ivaTotalFactura.formatDecimals(2),
            totalISC = if (totalISC > 0) totalISC.formatDecimals(2) else null,
            totalMontoGravado = totalMontoGravado.formatDecimals(2),
            totalDescuento =
                factura.totalizarDescuentoGlobal.let {
                    if (it > 0) it.formatDecimals(2) else "0.00"
                },
            totalFactura = factura.totalTotalFactura.formatDecimals(2),
            totalValorRecibido = ((ctx.montoCancelar ?: factura.totalTotalFactura) + (ctx.vuelto ?: 0.0)).formatDecimals(2),
            vuelto = ctx.vuelto?.formatDecimals(2),
            tiempoPago = tiempoPago,
            nroItems = ctx.detalles.size.toString(),
            totalTodosItems = (factura.totalTotalFactura + factura.totalizarDescuentoGlobal).formatDecimals(2),
            listaFormaPago = formasPago,
            listaDescBonificacion = bonificaciones,
            retencion = buildRetencion(ctx.retencion),
            listaTotalOTI = totalOTI,
        )
    }

    private fun buildRetencion(retencion: com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData?): TheFactoryHkaRetencion? =
        retencion?.let {
            TheFactoryHkaRetencion(
                codigoRetencion = it.codigoRetencion,
                montoRetencion = it.montoRetencion.formatDecimals(2),
            )
        }

    private fun buildFormasPago(
        formasPago: List<FEFormaPagoData>,
        vuelto: Double?,
    ): List<TheFactoryHkaFormaPago> {
        val cambio = vuelto?.takeIf { it > 0 } ?: 0.0
        val formasPagoFiltradas =
            formasPago
                .filter { fp ->
                    // Ignorar siglas específicas
                    val siglas = fp.siglas?.uppercase()?.trim() ?: ""
                    siglas !in IGNORED_PAYMENT_SIGLAS
                }

        val cashIndex = if (cambio > 0) formasPagoFiltradas.indexOfFirst { it.isCashPayment() } else -1

        return formasPagoFiltradas
            .mapIndexed { index, fp ->
                // Mapear al catálogo The Factory (01 a 08), si no existe enviar "99"
                val formaPagoFact = fp.formaPagoFact?.takeIf { it.isNotBlank() } ?: "99"

                // Descripción: si es menor a 10 caracteres, concatenar consigo misma
                val descripcion =
                    fp.descripcion.let {
                        if (it.length < MIN_FORMA_PAGO_DESC_LENGTH) "$it $it" else it
                    }

                TheFactoryHkaFormaPago(
                    formaPagoFact = formaPagoFact,
                    descFormaPago = descripcion.takeIf { formaPagoFact == "99" },
                    valorCuotaPagada = (fp.monto + if (index == cashIndex) cambio else 0.0).formatDecimals(2),
                )
            }.ifEmpty {
                // Fallback: al menos una forma de pago debe existir
                listOf(
                    TheFactoryHkaFormaPago(
                        formaPagoFact = "99",
                        descFormaPago = "Otro medio de pago",
                        valorCuotaPagada = "0.00",
                    ),
                )
            }
    }

    private fun FEFormaPagoData.isCashPayment(): Boolean {
        val siglas = siglas?.uppercase()?.trim()
        return esCash || siglas in setOf("EF", "CASH", "EFECTIVO") || formaPagoFact == "02"
    }

    private fun buildTotalOTI(detalles: List<FEDetalleData>): List<TheFactoryHkaTotalOTI>? {
        val otiMap = mutableMapOf<Int, Double>()
        for (det in detalles) {
            if (det.idOti != null && det.idOti > 0 && det.importeOti != null && det.importeOti > 0) {
                otiMap[det.idOti] = (otiMap[det.idOti] ?: 0.0) + det.importeOti
            }
        }

        return if (otiMap.isEmpty()) {
            null
        } else {
            otiMap.map { (codigo, valor) ->
                TheFactoryHkaTotalOTI(
                    codigoTotalOTI = codigo.toString(),
                    valorTotalOTI = valor.formatDecimals(2),
                )
            }
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Mapea el porcentaje de IVA/ITBMS al código del catálogo The Factory.
     * 7% → "01", 10% → "02", 15% → "03", otro → "00" (exento).
     */
    private fun mapTasaITBMS(piva: Double): String =
        when {
            piva == ITBMS_RATE_7 || isApprox(piva, ITBMS_RATE_7) -> "01"
            piva == ITBMS_RATE_10 || isApprox(piva, ITBMS_RATE_10) -> "02"
            piva == ITBMS_RATE_15 || isApprox(piva, ITBMS_RATE_15) -> "03"
            else -> "00"
        }

    private fun isApprox(
        a: Double,
        b: Double,
        epsilon: Double = 0.01,
    ): Boolean = kotlin.math.abs(a - b) < epsilon

    private fun normalizeTipoClienteFE(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.isBlank() || trimmed == "0") "02" else trimmed
    }

    /**
     * Normaliza un código a 2 dígitos con cero al frente (ej. "1" → "01").
     */
    private fun normalizeToTwoDigits(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length == 1) "0$trimmed" else trimmed
    }

    /**
     * Formatea la fecha de la factura al formato ISO 8601 (yyyy-MM-dd'T'HH:mm:ss).
     * La fecha viene en formato "yyyy-MM-dd" desde la DB.
     */
    internal fun formatFechaEmisionForPayload(fecha: String?): String {
        if (fecha.isNullOrBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
        }
        return try {
            val localDate = LocalDate.parse(fecha.trim().take(ISO_DATE_LENGTH))
            localDate.format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
        } catch (_: Exception) {
            LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "T00:00:00-05:00"
        }
    }

    /**
     * Extensión para formatear Double a String con N decimales exactos.
     */
    private fun Double.formatDecimals(scale: Int): String =
        BigDecimal
            .valueOf(this)
            .setScale(scale, RoundingMode.HALF_UP)
            .toPlainString()
}
