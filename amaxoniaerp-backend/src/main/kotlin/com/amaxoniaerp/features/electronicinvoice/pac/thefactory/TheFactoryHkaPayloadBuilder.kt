package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext

/**
 * Builder Pattern: transforma el [InvoiceFEContext] (datos crudos de la DB)
 * en un [TheFactoryHkaDocumentoWrapper] listo para enviar al API de The Factory HKA.
 *
 * Toda la lógica de negocio de transformación vive aquí y en sus archivos
 * satélite (TheFactoryHkaClienteBuilder.kt / TheFactoryHkaPayloadUtils.kt):
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
        private const val DEFAULT_CPBS = "5411"
        private const val DEFAULT_CPBS_ABREV = "54"
        private const val MIN_DESCRIPTION_LENGTH = 5
        private const val MIN_FORMA_PAGO_DESC_LENGTH = 10
        private const val DISCOUNT_SCALE = 6
        private const val QUANTITY_SCALE = 3

        // Siglas de formas de pago a ignorar (retenciones y notas de crédito)
        private val IGNORED_PAYMENT_SIGLAS = setOf("NC", "RETITBMSINGRE")
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
        val motivoContingencia =
            if (config.tipoEmision == "02") {
                "Problemas de comunicación interna."
            } else {
                config.motivoContingencia
            }

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
            val codigoCPBSAbrev =
                if (esGobierno) det.codigoCPBSAbrev?.takeIf { it.isNotBlank() } else DEFAULT_CPBS_ABREV

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
            val listaOTI = buildItemOTI(det)

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

        // tiempoPago: "1" contado, "2" crédito, "3" gobierno
        val creditoTotal =
            ctx.formasPago
                .filter { it.siglas?.uppercase()?.trim() in setOf("CXC", "CRED", "CREDITO") || it.formaPagoFact == "01" }
                .sumOf { it.monto }
        val inmediatoTotal =
            ctx.formasPago
                .filter { it.siglas?.uppercase()?.trim() !in setOf("CXC", "CRED", "CREDITO") && it.formaPagoFact != "01" }
                .sumOf { it.monto }

        val tiempoPago =
            when {
                ctx.factura.tipoVenta == "2" && inmediatoTotal == 0.0 -> "2" // Plazo (100% crédito)
                creditoTotal > 0.0 && inmediatoTotal > 0.0 -> "3" // Mixto
                creditoTotal > 0.0 -> "2" // Plazo (100% crédito)
                ctx.factura.tipoVenta == "2" -> "2" // Plazo
                else -> "1" // Inmediato (Contado)
            }

        // Formas de pago: filtrar las ignoradas y mapear al catálogo
        val formasPago = buildFormasPago(ctx.formasPago, ctx.vuelto, tiempoPago, factura.totalTotalFactura)

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

        // listaPagoPlazo requerida por DGI Panamá en ventas a crédito ("2") y mixtas ("3")
        val listaPagoPlazo =
            if (tiempoPago in setOf("2", "3")) {
                val fechaVence = formatFechaVencimientoForPayload(factura.fechaFactura)
                val valorCuota =
                    if (tiempoPago == "3") {
                        creditoTotal.formatDecimals(2)
                    } else {
                        factura.totalTotalFactura.formatDecimals(2)
                    }
                listOf(
                    TheFactoryHkaPagoPlazo(
                        fechaVenceCuota = fechaVence,
                        valorCuota = valorCuota,
                        infoPagoCuota = "CUOTA 1 DE 1 - CREDITO 30 DIAS",
                    ),
                )
            } else {
                null
            }

        // Invariante PAC: totalValorRecibido == Σ(valorCuotaPagada de listaFormaPago)
        val totalValorRecibido = formasPago.sumOf { it.valorCuotaPagada.toDouble() }.formatDecimals(2)

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
            totalValorRecibido = totalValorRecibido,
            vuelto = ctx.vuelto?.formatDecimals(2),
            tiempoPago = tiempoPago,
            nroItems = ctx.detalles.size.toString(),
            totalTodosItems = (factura.totalTotalFactura + factura.totalizarDescuentoGlobal).formatDecimals(2),
            listaFormaPago = formasPago,
            listaDescBonificacion = bonificaciones,
            retencion = buildRetencion(ctx.retencion),
            listaPagoPlazo = listaPagoPlazo,
            listaTotalOTI = totalOTI,
        )
    }

    private fun buildRetencion(
        retencion: com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData?,
    ): TheFactoryHkaRetencion? =
        retencion?.let {
            TheFactoryHkaRetencion(
                codigoRetencion = it.codigoRetencion,
                montoRetencion = it.montoRetencion.formatDecimals(2),
            )
        }

    private fun buildFormasPago(
        formasPago: List<FEFormaPagoData>,
        vuelto: Double?,
        tiempoPago: String,
        totalFactura: Double,
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
                val siglas = fp.siglas?.uppercase()?.trim().orEmpty()
                // Mapear al catálogo The Factory (01 a 09), si no existe enviar "99"
                // 01 = Crédito, 02 = Efectivo, 03 = Tarjeta Crédito, 04 = Tarjeta Débito, 08 = Transf, 09 = Cheque
                val formaPagoFact =
                    when {
                        !fp.formaPagoFact.isNullOrBlank() -> fp.formaPagoFact
                        siglas in setOf("CXC", "CRED", "CREDITO") -> "01"
                        fp.isCashPayment() -> "02"
                        else -> "99"
                    }

                // Descripción: obligatorio ÚNICAMENTE si formaPagoFact == "99" (mínimo 10 caracteres)
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
                val defaultForma = if (tiempoPago == "2") "01" else "99"
                val defaultDesc = if (tiempoPago == "2") "Crédito" else "Otro medio de pago"
                listOf(
                    TheFactoryHkaFormaPago(
                        formaPagoFact = defaultForma,
                        descFormaPago = if (defaultForma == "99") defaultDesc else null,
                        valorCuotaPagada = totalFactura.formatDecimals(2),
                    ),
                )
            }
    }

    private fun buildTotalOTI(detalles: List<FEDetalleData>): List<TheFactoryHkaTotalOTI>? {
        val otiMap = mutableMapOf<Int, Double>()
        for (det in detalles) {
            val hasValidOti = det.idOti != null && det.idOti > 0
            val hasImporteOti = det.importeOti != null && det.importeOti > 0
            if (hasValidOti && hasImporteOti) {
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

    private fun buildItemOTI(det: FEDetalleData): List<TheFactoryHkaItemOTI>? {
        val hasValidOti = det.idOti != null && det.idOti > 0
        val hasImporteOti = det.importeOti != null && det.importeOti > 0
        return if (hasValidOti && hasImporteOti) {
            listOf(
                TheFactoryHkaItemOTI(
                    tasaOTI = det.idOti.toString(),
                    valorTasa = det.importeOti.formatDecimals(2),
                ),
            )
        } else {
            null
        }
    }
}
