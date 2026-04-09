package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalRequest
import com.amaxoniaerp.features.creditnotes.domain.ConfirmCreditNoteFiscalResponse
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest
import com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalDocument
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSummary
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemExistenciaAlmacenTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTable
import com.amaxoniaerp.features.sales.data.SalesKardexDetalleTable
import com.amaxoniaerp.features.sales.data.SalesKardexTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CreditNoteRepository {

    fun listCreditNotes(
        limit: Int,
        offset: Long,
        search: String?,
        fechaInicio: LocalDate?,
        fechaFin: LocalDate?,
    ): Pair<List<CreditNoteSummary>, Long> {
        val query = CreditNoteHeaderTable
            .join(ClientsTable, JoinType.LEFT, CreditNoteHeaderTable.idCliente, ClientsTable.idCliente)
            .join(CreditNoteFacturaTable, JoinType.LEFT, CreditNoteHeaderTable.codFactura, CreditNoteFacturaTable.idFactura)
            .selectAll()

        if (fechaInicio != null && fechaFin != null) {
            query.andWhere { CreditNoteHeaderTable.fechaDevolucion.between(fechaInicio, fechaFin) }
        }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (CreditNoteHeaderTable.codDevolucion like term) or
                    (CreditNoteFacturaTable.codFactura like term) or
                    (ClientsTable.nombre like term) or
                    (ClientsTable.apellido like term) or
                    (ClientsTable.rif like term)
            }
        }

        val total = query.count()
        val data = query
            .orderBy(CreditNoteHeaderTable.fechaCreacion to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map(::mapSummaryRow)

        return data to total
    }

    fun getCreditNoteDetail(id: String): CreditNoteDetailResponse? {
        val headerRow = CreditNoteHeaderTable
            .join(ClientsTable, JoinType.LEFT, CreditNoteHeaderTable.idCliente, ClientsTable.idCliente)
            .join(CreditNoteFacturaTable, JoinType.LEFT, CreditNoteHeaderTable.codFactura, CreditNoteFacturaTable.idFactura)
            .selectAll()
            .where { CreditNoteHeaderTable.idDevolucion eq id }
            .limit(1)
            .firstOrNull()
            ?: return null

        val detailRows = CreditNoteDetailTable
            .join(CreditNoteFacturaDetalleTable, JoinType.LEFT, CreditNoteDetailTable.idDetalleFactura, CreditNoteFacturaDetalleTable.idDetalleFactura)
            .selectAll()
            .where { CreditNoteDetailTable.idDevolucion eq id }
            .toList()

        val lines = detailRows.map(::mapDetailLine)
        val header = mapHeaderContext(headerRow)
        return buildDetailResponse(header, lines)
    }

    fun listEligibleInvoices(limit: Int, offset: Long, search: String?): Pair<List<CreditNoteSourceInvoiceSummary>, Long> {
        val query = CreditNoteFacturaTable
            .join(ClientsTable, JoinType.LEFT, CreditNoteFacturaTable.idCliente, ClientsTable.idCliente)
            .selectAll()
            .where { (CreditNoteFacturaTable.codEstatus neq 3) and (CreditNoteFacturaTable.totalTotalFactura greater BigDecimal.ZERO) }

        if (!search.isNullOrBlank()) {
            val term = "%$search%"
            query.andWhere {
                (CreditNoteFacturaTable.codFactura like term) or
                    (CreditNoteFacturaTable.numeroDocumentoFiscal like term) or
                    (ClientsTable.nombre like term) or
                    (ClientsTable.apellido like term) or
                    (ClientsTable.rif like term)
            }
        }

        val invoiceRows = query
            .orderBy(CreditNoteFacturaTable.fechaCreacion to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()

        val summaries = invoiceRows.mapNotNull { row ->
            val invoiceId = row[CreditNoteFacturaTable.idFactura]
            val source = buildSourceInvoiceDetail(invoiceId) ?: return@mapNotNull null
            if (source.remainingAmount <= 0.0) {
                null
            } else {
                CreditNoteSourceInvoiceSummary(
                    id = source.id,
                    codigo = source.codigo,
                    codigoFiscal = source.codigoFiscal,
                    numeroDocumentoFiscal = source.numeroDocumentoFiscal,
                    fecha = source.fecha,
                    clienteNombre = source.clienteNombre,
                    clienteIdentificacion = source.clienteIdentificacion,
                    total = source.totalOriginal,
                    remainingAmount = source.remainingAmount,
                    items = source.lines.count { it.cantidadDisponible > 0.0 },
                    moneda = source.moneda,
                )
            }
        }

        return summaries to summaries.size.toLong()
    }

    fun getSourceInvoiceDetail(invoiceId: String): CreditNoteSourceInvoiceDetailResponse? {
        return buildSourceInvoiceDetail(invoiceId)
    }

    fun create(request: CreateCreditNoteRequest, username: String): CreateCreditNoteResponse {
        if (request.idCajaSecuencia.isBlank()) {
            throw CreditNoteValidationException("La nota de crédito requiere una caja secuencia activa")
        }
        if (request.settlementType == CreditNoteSettlementType.REINTEGRO && request.idFormaPagoReintegro == null) {
            throw CreditNoteValidationException("Debes indicar la forma de pago de reintegro")
        }

        val invoice = loadInvoiceHeader(request.idFactura)
            ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
        val client = loadClient(invoice.idCliente)
        val invoiceLines = loadInvoiceLines(invoice.idFactura)
        if (invoiceLines.isEmpty()) {
            throw CreditNoteValidationException("La factura origen no tiene líneas disponibles")
        }

        val requestedLines = normalizeRequestedLines(request, invoiceLines)
        val processedLines = buildProcessedLines(invoiceLines, requestedLines)
        val allReturnedAfterOperation = invoiceLines.all { sourceLine ->
            val returnedInThisRequest = processedLines
                .firstOrNull { it.sourceLine.idDetalleFactura == sourceLine.idDetalleFactura }
                ?.quantity
                ?: BigDecimal.ZERO
            sourceLine.availableQuantity.subtract(returnedInThisRequest).isEffectivelyZero()
        }

        if (request.anular && !allReturnedAfterOperation) {
            throw CreditNoteValidationException("Para anular la factura debes devolver la totalidad de las líneas restantes")
        }

        val creditNoteDate = parseDate(request.fecha)
        val now = LocalDateTime.now()
        val cajaContext = resolveCajaContext(request.idCajaSecuencia)
        val nextCorrelative = advanceCreditNoteCorrelative(cajaContext.idCaja)
        val creditNoteId = UUID.randomUUID().toString()
        val creditNoteCode = buildCreditNoteCode(cajaContext.codigoCaja, nextCorrelative)
        val totals = calculateTotals(processedLines)

        CreditNoteHeaderTable.insert {
            it[idDevolucion] = creditNoteId
            it[codDevolucion] = creditNoteCode
            it[codFactura] = invoice.idFactura
            it[fechaDevolucion] = creditNoteDate
            it[codDevolucionFiscal] = PENDING_FISCAL_CODE
            it[nroz] = ""
            it[impresoraSerial] = ""
            it[observacion] = request.observacion.take(MAX_OBSERVATION_LENGTH)
            it[idCliente] = invoice.idCliente
            it[codVendedor] = invoice.codVendedor
            it[fechaFactura] = invoice.fechaFactura
            it[subtotal] = totals.subtotal
            it[impuesto] = totals.tax
            it[total] = totals.total
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[periodoDevolucion] = request.periodo.take(MAX_PERIOD_LENGTH)
            it[contabilizado] = 0
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = creditNoteDate
            it[idCajaSecuencia] = request.idCajaSecuencia
            it[serieSucursal] = cajaContext.serieSucursal
            it[cajaSecuencia] = cajaContext.cajaSecuencia
            it[idSucursal] = cajaContext.idSucursal
            it[idCaja] = cajaContext.idCaja
            it[codigoCaja] = cajaContext.codigoCaja
            it[codCliente] = client.codigoCliente
            it[descuentoGlobal] = BigDecimal.ZERO.setScale(2)
            it[pdescuentoGlobal] = BigDecimal.ZERO.setScale(2)
            it[numeroDocumentoFiscal] = ""
        }

        processedLines.forEach { line ->
            CreditNoteDetailTable.insert {
                it[idDevolucionDetalle] = UUID.randomUUID().toString()
                it[idDevolucion] = creditNoteId
                it[idDetalleFactura] = line.sourceLine.idDetalleFactura
                it[idItem] = line.sourceLine.idItem
                it[itemAlmacen] = line.sourceLine.almacen
                it[itemCantidad] = line.quantity.setScale(3, RoundingMode.HALF_UP)
                it[itemPrecioSinIva] = line.sourceLine.precioSinIva
                it[itemDescuento] = line.sourceLine.descuentoPorcentaje
                it[itemMontoDescuento] = line.discountAmount
                it[itemPIva] = line.sourceLine.pIva
                it[itemTotalSinIva] = line.totalSinIva
                it[itemTotalConIva] = line.totalConIva
                it[codVendedor] = line.sourceLine.codVendedor
                it[itemCodigo] = line.sourceLine.codigo
                it[itemReferencia] = line.sourceLine.referencia
            }

            val lineFullyCancelled = line.sourceLine.availableQuantity.subtract(line.quantity).isEffectivelyZero()
            if (lineFullyCancelled) {
                CreditNoteFacturaDetalleTable.update({ CreditNoteFacturaDetalleTable.idDetalleFactura eq line.sourceLine.idDetalleFactura }) {
                    it[anulado] = true
                }
            }
        }

        if (allReturnedAfterOperation) {
            cancelInvoiceAndOriginalCash(invoice.idFactura, username, creditNoteDate)
        } else {
            registerPartialCreditNoteOnOriginalCash(invoice = invoice, creditNoteId = creditNoteId, creditNoteCode = creditNoteCode, total = totals.total, username = username, now = now)
        }

        if (request.devolverStock) {
            restoreInventory(invoice = invoice, creditNoteId = creditNoteId, creditNoteCode = creditNoteCode, lines = processedLines, username = username, date = creditNoteDate, now = now, idSucursal = cajaContext.idSucursal)
        }

        when (request.settlementType) {
            CreditNoteSettlementType.NINGUNO -> Unit
            CreditNoteSettlementType.REINTEGRO -> registerRefundCashEgress(
                invoice = invoice,
                creditNoteId = creditNoteId,
                creditNoteCode = creditNoteCode,
                total = totals.total,
                idFormaPago = request.idFormaPagoReintegro ?: throw CreditNoteValidationException("Forma de pago de reintegro requerida"),
                username = username,
                now = now,
                date = creditNoteDate,
                cajaContext = cajaContext,
            )
            CreditNoteSettlementType.ABONO -> registerAbono(
                creditNoteId = creditNoteId,
                total = totals.total,
                client = client,
                username = username,
                now = now,
                date = creditNoteDate,
                cajaContext = cajaContext,
            )
            CreditNoteSettlementType.CERTIFICADO_REGALO -> registerGiftCertificate(
                creditNoteId = creditNoteId,
                total = totals.total,
                client = client,
                username = username,
                now = now,
                cajaContext = cajaContext,
            )
        }

        val responseHeader = CreditNoteHeaderContext(
            id = creditNoteId,
            codigo = creditNoteCode,
            facturaId = invoice.idFactura,
            facturaCodigo = invoice.codFactura,
            fecha = creditNoteDate,
            fechaCreacion = now,
            periodo = request.periodo,
            observacion = request.observacion,
            clienteNombre = client.nombreCompleto,
            clienteIdentificacion = client.identificacion,
            clienteDireccion = client.direccion,
            clienteTelefono = client.telefono,
            subtotal = totals.subtotal,
            impuesto = totals.tax,
            total = totals.total,
            fiscalStatus = CreditNoteFiscalStatus.PENDIENTE,
            fiscalNumber = "",
            printerSerial = "",
            originalFiscalNumber = request.numeroFiscalElectronico.ifBlank {
                invoice.numeroDocumentoFiscal.ifBlank { invoice.codFacturaFiscal }
            },
            originalInvoiceDate = invoice.fechaFactura,
            anulaFacturaCompleta = allReturnedAfterOperation,
        )
        val responseLines = processedLines.map { it.toDetailLine() }
        val detail = buildDetailResponse(responseHeader, responseLines)

        return CreateCreditNoteResponse(
            success = true,
            id = creditNoteId,
            codigo = creditNoteCode,
            subtotal = totals.subtotal.toDouble(),
            impuesto = totals.tax.toDouble(),
            total = totals.total.toDouble(),
            fiscalStatus = CreditNoteFiscalStatus.PENDIENTE,
            detail = detail,
        )
    }

    fun confirmFiscal(id: String, request: ConfirmCreditNoteFiscalRequest): ConfirmCreditNoteFiscalResponse {
        val header = CreditNoteHeaderTable
            .selectAll()
            .where { CreditNoteHeaderTable.idDevolucion eq id }
            .limit(1)
            .firstOrNull()
            ?: throw CreditNoteNotFoundException("Nota de crédito no encontrada")

        val normalizedFiscalCode = request.codDevolucionFiscal.trim().ifBlank { PENDING_FISCAL_CODE }
        val normalizedDocumentNumber = request.numeroDocumentoFiscal.trim().ifBlank { normalizedFiscalCode }

        CreditNoteHeaderTable.update({ CreditNoteHeaderTable.idDevolucion eq id }) {
            it[codDevolucionFiscal] = normalizedFiscalCode
            it[numeroDocumentoFiscal] = normalizedDocumentNumber
            it[impresoraSerial] = request.printerSerial.trim()
            it[nroz] = request.nroz.trim()
        }

        return ConfirmCreditNoteFiscalResponse(
            success = true,
            id = id,
            codigo = header[CreditNoteHeaderTable.codDevolucion],
            fiscalStatus = CreditNoteFiscalStatus.CONFIRMADA,
            codDevolucionFiscal = normalizedFiscalCode,
            numeroDocumentoFiscal = normalizedDocumentNumber,
            printerSerial = request.printerSerial.trim(),
        )
    }

    private fun buildSourceInvoiceDetail(invoiceId: String): CreditNoteSourceInvoiceDetailResponse? {
        val invoice = loadInvoiceHeader(invoiceId) ?: return null
        val client = loadClient(invoice.idCliente)
        val lines = loadInvoiceLines(invoiceId)
        if (lines.isEmpty()) return null

        val remainingAmount = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.availableTotalConIva }
        return CreditNoteSourceInvoiceDetailResponse(
            id = invoice.idFactura,
            codigo = invoice.codFactura,
            codigoFiscal = invoice.codFacturaFiscal,
            numeroDocumentoFiscal = invoice.numeroDocumentoFiscal,
            fecha = formatDate(invoice.fechaFactura),
            clienteId = invoice.idCliente,
            clienteNombre = client.nombreCompleto,
            clienteIdentificacion = client.identificacion,
            clienteDireccion = client.direccion,
            clienteTelefono = client.telefono,
            codVendedor = invoice.codVendedor,
            totalOriginal = invoice.totalTotalFactura.toDouble(),
            subtotalOriginal = invoice.totalizarSubTotal.toDouble(),
            impuestoOriginal = invoice.totalizarMontoIva.toDouble(),
            remainingAmount = remainingAmount.toDouble(),
            moneda = invoice.moneda,
            lines = lines.map { it.toResponseLine() },
        )
    }

    private fun loadInvoiceHeader(invoiceId: String): InvoiceHeader? {
        val row = CreditNoteFacturaTable
            .selectAll()
            .where { CreditNoteFacturaTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?: return null

        return InvoiceHeader(
            idFactura = row[CreditNoteFacturaTable.idFactura],
            codFactura = row[CreditNoteFacturaTable.codFactura],
            codFacturaFiscal = row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty(),
            numeroDocumentoFiscal = row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty(),
            idCliente = row[CreditNoteFacturaTable.idCliente],
            codVendedor = row[CreditNoteFacturaTable.codVendedor],
            codEstatus = row[CreditNoteFacturaTable.codEstatus] ?: 0,
            fechaFactura = row[CreditNoteFacturaTable.fechaFactura],
            subtotal = row[CreditNoteFacturaTable.subtotal],
            totalizarSubTotal = row[CreditNoteFacturaTable.totalizarSubTotal],
            totalizarMontoIva = row[CreditNoteFacturaTable.totalizarMontoIva],
            totalTotalFactura = row[CreditNoteFacturaTable.totalTotalFactura],
            formaPago = row[CreditNoteFacturaTable.formaPago],
            idCajaSecuencia = row[CreditNoteFacturaTable.idCajaSecuencia],
            idCaja = row[CreditNoteFacturaTable.idCaja],
            idSucursal = row[CreditNoteFacturaTable.idSucursal],
            serieSucursal = row[CreditNoteFacturaTable.serieSucursal],
            codigoCaja = row[CreditNoteFacturaTable.codigoCaja],
            facturarA = row[CreditNoteFacturaTable.facturarA],
            facturarARuc = row[CreditNoteFacturaTable.facturarARuc],
            facturarADireccion = row[CreditNoteFacturaTable.facturarADireccion],
            facturarATelefono = row[CreditNoteFacturaTable.facturarATelefono],
            moneda = row[CreditNoteFacturaTable.abrMonedaBase].orEmpty().ifBlank { "USD" },
        )
    }

    private fun loadClient(idCliente: String): ClientContext {
        val row = ClientsTable
            .selectAll()
            .where { ClientsTable.idCliente eq idCliente }
            .limit(1)
            .firstOrNull()

        val nombre = row?.get(ClientsTable.nombre).orEmpty()
        val apellido = row?.get(ClientsTable.apellido).orEmpty()
        return ClientContext(
            idCliente = idCliente,
            codigoCliente = row?.get(ClientsTable.codCliente).orEmpty().ifBlank { idCliente },
            nombreCompleto = "$nombre $apellido".trim().ifBlank { "CONSUMIDOR FINAL" },
            identificacion = row?.get(ClientsTable.rif).orEmpty().ifBlank { "CF" },
            direccion = row?.get(ClientsTable.direccion).orEmpty(),
            telefono = row?.get(ClientsTable.telefonos).orEmpty(),
        )
    }

    private fun loadInvoiceLines(invoiceId: String): List<SourceInvoiceLine> {
        val detailRows = CreditNoteFacturaDetalleTable
            .selectAll()
            .where { CreditNoteFacturaDetalleTable.idFactura eq invoiceId }
            .orderBy(CreditNoteFacturaDetalleTable.idDetalleFactura)
            .toList()
        if (detailRows.isEmpty()) return emptyList()

        val detailIds = detailRows.map { it[CreditNoteFacturaDetalleTable.idDetalleFactura] }
        val returnedByDetail = CreditNoteDetailTable
            .selectAll()
            .where { CreditNoteDetailTable.idDetalleFactura inList detailIds }
            .groupBy { it[CreditNoteDetailTable.idDetalleFactura] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, row -> acc + row[CreditNoteDetailTable.itemCantidad].setScale(3, RoundingMode.HALF_UP) }
            }

        return detailRows.map { row ->
            val quantityOriginal = row[CreditNoteFacturaDetalleTable.itemCantidadTotal].setScale(3, RoundingMode.HALF_UP)
            val returned = returnedByDetail[row[CreditNoteFacturaDetalleTable.idDetalleFactura]] ?: BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
            val available = quantityOriginal.subtract(returned).coerceAtLeastZero(3)
            val unitTotalSinIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalSinIva], quantityOriginal, 6)
            val unitTotalConIva = divideSafe(row[CreditNoteFacturaDetalleTable.itemTotalConIva], quantityOriginal, 6)
            val unitDiscount = divideSafe(row[CreditNoteFacturaDetalleTable.itemMontoDescuento], quantityOriginal, 6)

            SourceInvoiceLine(
                idDetalleFactura = row[CreditNoteFacturaDetalleTable.idDetalleFactura],
                idItem = row[CreditNoteFacturaDetalleTable.idItem],
                descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
                codigo = row[CreditNoteFacturaDetalleTable.itemCodigo].orEmpty(),
                referencia = row[CreditNoteFacturaDetalleTable.itemReferencia].orEmpty(),
                quantityOriginal = quantityOriginal,
                returnedQuantity = returned,
                availableQuantity = available,
                precioSinIva = row[CreditNoteFacturaDetalleTable.itemPrecioSinIva],
                descuentoPorcentaje = row[CreditNoteFacturaDetalleTable.itemDescuento],
                descuentoMontoTotal = row[CreditNoteFacturaDetalleTable.itemMontoDescuento],
                pIva = row[CreditNoteFacturaDetalleTable.itemPIva],
                totalSinIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalSinIva],
                totalConIvaOriginal = row[CreditNoteFacturaDetalleTable.itemTotalConIva],
                availableTotalSinIva = unitTotalSinIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
                availableTotalConIva = unitTotalConIva.multiply(available).setScale(2, RoundingMode.HALF_UP),
                unitDiscountAmount = unitDiscount,
                almacen = row[CreditNoteFacturaDetalleTable.itemAlmacen],
                codVendedor = row[CreditNoteFacturaDetalleTable.codVendedor],
            )
        }
    }

    private fun normalizeRequestedLines(request: CreateCreditNoteRequest, invoiceLines: List<SourceInvoiceLine>): Map<String, BigDecimal> {
        val requestLines = if (request.detalle.isEmpty() && request.anular) {
            invoiceLines
                .filter { it.availableQuantity > BigDecimal.ZERO }
                .map { source -> RequestedLine(source.idDetalleFactura, source.availableQuantity.toDouble()) }
        } else {
            request.detalle.map { RequestedLine(it.idDetalleFactura, it.cantidad) }
        }

        if (requestLines.isEmpty()) {
            throw CreditNoteValidationException("Debes indicar al menos una línea a devolver")
        }

        return requestLines
            .groupBy { it.idDetalleFactura }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { acc, line ->
                    val quantity = BigDecimal.valueOf(line.cantidad).setScale(3, RoundingMode.HALF_UP)
                    if (quantity <= BigDecimal.ZERO) {
                        throw CreditNoteValidationException("Las cantidades a devolver deben ser mayores a cero")
                    }
                    acc + quantity
                }
            }
    }

    private fun buildProcessedLines(
        invoiceLines: List<SourceInvoiceLine>,
        requestedLines: Map<String, BigDecimal>,
    ): List<ProcessedLine> {
        val linesById = invoiceLines.associateBy { it.idDetalleFactura }
        return requestedLines.map { (idDetalleFactura, quantity) ->
            val sourceLine = linesById[idDetalleFactura]
                ?: throw CreditNoteValidationException("La línea $idDetalleFactura no pertenece a la factura origen")
            if (quantity > sourceLine.availableQuantity) {
                throw CreditNoteValidationException(
                    "La cantidad a devolver para ${sourceLine.descripcion} excede lo disponible (${sourceLine.availableQuantity.toDouble()})"
                )
            }

            val unitTotalSinIva = divideSafe(sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, 6)
            val unitTotalConIva = divideSafe(sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, 6)

            ProcessedLine(
                sourceLine = sourceLine,
                quantity = quantity,
                discountAmount = sourceLine.unitDiscountAmount.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                totalSinIva = unitTotalSinIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                totalConIva = unitTotalConIva.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
            )
        }.sortedBy { it.sourceLine.idDetalleFactura }
    }

    private fun calculateTotals(lines: List<ProcessedLine>): CreditNoteTotals {
        val subtotal = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalSinIva }
        val total = lines.fold(BigDecimal.ZERO.setScale(2)) { acc, line -> acc + line.totalConIva }
        val tax = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP)
        return CreditNoteTotals(subtotal = subtotal, tax = tax, total = total)
    }

    private fun resolveCajaContext(idCajaSecuencia: String): CajaContext {
        val row = CreditNoteCajaSecuenciaTable
            .join(CreditNoteCajaTable, JoinType.INNER, CreditNoteCajaSecuenciaTable.idCaja, CreditNoteCajaTable.idCaja)
            .selectAll()
            .where { CreditNoteCajaSecuenciaTable.idCajaSecuencia eq idCajaSecuencia }
            .limit(1)
            .firstOrNull()
            ?: throw CreditNoteValidationException("No se encontró la caja secuencia indicada")

        return CajaContext(
            idCaja = row[CreditNoteCajaTable.idCaja],
            idSucursal = row[CreditNoteCajaTable.idSucursal] ?: 1,
            codigoCaja = row[CreditNoteCajaTable.codigo].orEmpty().ifBlank { "NC" },
            serieSucursal = row[CreditNoteCajaSecuenciaTable.serieSucursal]
                ?: row[CreditNoteCajaTable.serieCaja]
                ?: "00001",
            cajaSecuencia = row[CreditNoteCajaSecuenciaTable.secuencia].orEmpty().ifBlank { "000001" },
            impresoraModelo = row[CreditNoteCajaTable.impresoraModelo].orEmpty(),
        )
    }

    private fun advanceCreditNoteCorrelative(idCaja: String): Int {
        val current = CreditNoteCajaTable
            .select(CreditNoteCajaTable.notacreditoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.notacreditoCorrelativo)
            ?: 0

        val next = current + 1
        val updated = CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq idCaja }) {
            it[notacreditoCorrelativo] = next
        }
        if (updated != 1) {
            throw CreditNoteValidationException("No se pudo avanzar el correlativo de nota de crédito")
        }
        return next
    }

    private fun buildCreditNoteCode(codigoCaja: String, nextCorrelative: Int): String {
        return "${codigoCaja.takeIf { it.isNotBlank() } ?: "NC"}-${nextCorrelative.toString().padStart(5, '0')}"
    }

    private fun cancelInvoiceAndOriginalCash(invoiceId: String, username: String, date: LocalDate) {
        CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq invoiceId }) {
            it[codEstatus] = 3
        }

        val originalCajas = SalesCajaNuevaTable
            .selectAll()
            .where { SalesCajaNuevaTable.idFactura eq invoiceId }
            .toList()

        originalCajas.forEach { cajaRow ->
            val cajaId = cajaRow[SalesCajaNuevaTable.cajaId]
            SalesCajaNuevaTable.update({ SalesCajaNuevaTable.cajaId eq cajaId }) {
                it[status] = CajaStatus.Anulada
            }

            val reciboIds = SalesCajaNuevaDetalleTable
                .select(SalesCajaNuevaDetalleTable.cajaReciboId)
                .where { SalesCajaNuevaDetalleTable.cajaId eq cajaId }
                .map { it[SalesCajaNuevaDetalleTable.cajaReciboId] }
                .filter { it.isNotBlank() }

            if (reciboIds.isNotEmpty()) {
                SalesCajaNuevaReciboTable.update({ SalesCajaNuevaReciboTable.cajaReciboId inList reciboIds }) {
                    it[status] = "AN"
                    it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
                    it[fechaCreacion] = LocalDateTime.of(date, LocalDateTime.now().toLocalTime())
                }
            }
        }
    }

    private fun registerPartialCreditNoteOnOriginalCash(
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        total: BigDecimal,
        username: String,
        now: LocalDateTime,
    ) {
        val originalCaja = SalesCajaNuevaTable
            .selectAll()
            .where { SalesCajaNuevaTable.idFactura eq invoice.idFactura }
            .orderBy(SalesCajaNuevaTable.fechaCreacion to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?: return

        val cajaId = originalCaja[SalesCajaNuevaTable.cajaId]
        val cajaReciboId = SalesCajaNuevaDetalleTable
            .select(SalesCajaNuevaDetalleTable.cajaReciboId)
            .where { SalesCajaNuevaDetalleTable.cajaId eq cajaId }
            .limit(1)
            .firstOrNull()
            ?.get(SalesCajaNuevaDetalleTable.cajaReciboId)
            .orEmpty()

        SalesCajaNuevaDetalleTable.insert {
            it[cajaDetalleId] = UUID.randomUUID().toString()
            it[this.cajaId] = cajaId
            it[idFormaPago] = CREDIT_NOTE_PAYMENT_FORM_ID
            it[idTransaccion] = originalCaja[SalesCajaNuevaTable.idTransaccion]
            it[this.cajaReciboId] = cajaReciboId
            it[monto] = total
            it[montoOriginal] = total
            it[concepto] = "Nota de credito $creditNoteCode"
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[retencionTipo] = ""
            it[retencionPorcentaje] = ""
            it[numero] = ""
            it[observacion] = "Nota de credito aplicada sobre factura ${invoice.codFactura}"
            it[retencionBaseCalculo] = ""
            it[serieSucursal] = invoice.serieSucursal
            it[cajaSecuencia] = invoice.idCajaSecuencia
            it[numeroControl] = ""
            it[numeroComprobante] = creditNoteCode
            it[retencionMonto] = ""
            it[retencionDetalleJson] = ""
            it[montoRecibido] = total
            it[montoMonedaPrincipal] = total
        }

        SalesCajaNuevaTable.update({ SalesCajaNuevaTable.cajaId eq cajaId }) {
            val current = originalCaja[SalesCajaNuevaTable.monto] ?: BigDecimal.ZERO.setScale(2)
            it[monto] = current.subtract(total).setScale(2, RoundingMode.HALF_UP)
            it[idNotaCredito] = creditNoteId
        }
    }

    private fun restoreInventory(
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        lines: List<ProcessedLine>,
        username: String,
        date: LocalDate,
        now: LocalDateTime,
        idSucursal: Int,
    ) {
        if (lines.isEmpty()) return

        val kardexId = UUID.randomUUID().toString()
        SalesKardexTable.insert {
            it[idTransaccion] = kardexId
            it[tipoMovimientoAlmacen] = 14
            it[autorizadoPor] = username.take(MAX_USERNAME_LENGTH)
            it[observacion] = "Entrada por nota de crédito $creditNoteCode"
            it[fecha] = date
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[estado] = "Procesado"
            it[idDocumento] = creditNoteId
            it[codProveedor] = 0
            it[comprobante] = creditNoteCode
            it[anio] = date.year
            it[tipoCosto] = "PEPS"
            it[estatus] = 1
            it[entregadoACodigo] = invoice.facturarARuc.take(10)
            it[entregadoANombre] = invoice.facturarA.take(30)
            it[codDocumento] = creditNoteCode
            it[subtipoMovimientoAlmacen] = 0
            it[contabilizado] = 0
            it[fechaContabilizacion] = date
            it[usuarioContabilizacion] = username.take(MAX_USERNAME_LENGTH)
            it[idAlmacenSalida] = lines.first().sourceLine.almacen
            it[SalesKardexTable.idSucursal] = idSucursal
            it[validadoFecha] = date
            it[validadoUsuario] = username.take(MAX_USERNAME_LENGTH)
            it[validadoObservacion] = "Entrada por devolucion"
        }

        lines.forEach { line ->
            val quantity = line.quantity.setScale(2, RoundingMode.HALF_UP)
            SalesKardexDetalleTable.insert {
                it[idTransaccionDetalle] = UUID.randomUUID().toString()
                it[idTransaccion] = kardexId
                it[idAlmacenEntrada] = line.sourceLine.almacen
                it[idAlmacenSalida] = 0
                it[idItem] = line.sourceLine.idItem
                it[cantidad] = quantity.toFloat()
                it[cantidadDistribuida] = 0
                it[precio] = line.sourceLine.precioSinIva
                it[cantidadMuestra] = 0
                it[unidadBulto] = "UNIDAD"
                it[cantidadBulto] = BigDecimal.ONE.setScale(2)
                it[unidadEmpaque] = "UNIDAD"
                it[cantidadTotal] = quantity
                it[costo] = BigDecimal.ZERO.setScale(2)
            }

            val stockRow = ItemExistenciaAlmacenTable
                .selectAll()
                .where {
                    (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                        (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
                }
                .limit(1)
                .firstOrNull()
            if (stockRow == null) {
                ItemExistenciaAlmacenTable.insertIgnore {
                    it[idItem] = line.sourceLine.idItem
                    it[codAlmacen] = line.sourceLine.almacen
                    it[cantidad] = quantity
                    it[cantidadMuestra] = BigDecimal.ZERO.setScale(4)
                    it[minimo] = BigDecimal.ZERO.setScale(4)
                    it[maximo] = BigDecimal.ZERO.setScale(4)
                }
            } else {
                val currentQuantity = stockRow[ItemExistenciaAlmacenTable.cantidad] ?: BigDecimal.ZERO.setScale(4)
                ItemExistenciaAlmacenTable.update({
                    (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                        (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
                }) {
                    it[cantidad] = currentQuantity.add(quantity).setScale(4, RoundingMode.HALF_UP)
                }
            }

            restoreLotAvailability(line)
        }
    }

    private fun restoreLotAvailability(line: ProcessedLine) {
        var remaining = line.quantity.setScale(0, RoundingMode.DOWN).toInt()
        if (remaining <= 0) return

        val lotRows = FacturaDetalleProductoLoteTable
            .selectAll()
            .where { FacturaDetalleProductoLoteTable.idDetalleFactura eq line.sourceLine.idDetalleFactura }
            .orderBy(FacturaDetalleProductoLoteTable.id)
            .toList()

        lotRows.forEach { row ->
            if (remaining <= 0) return@forEach
            val restoreQty = minOf(remaining, row[FacturaDetalleProductoLoteTable.cantidad])
            if (restoreQty <= 0) return@forEach

            val lotId = row[FacturaDetalleProductoLoteTable.idLoteItem]
            val lotRow = ItemLoteTable
                .selectAll()
                .where { ItemLoteTable.idLoteItem eq lotId }
                .limit(1)
                .firstOrNull()
            if (lotRow != null) {
                val currentAvailability = lotRow[ItemLoteTable.disponibilidad]
                ItemLoteTable.update({ ItemLoteTable.idLoteItem eq lotId }) {
                    it[disponibilidad] = currentAvailability.add(BigDecimal.valueOf(restoreQty.toLong()).setScale(2))
                }
            }
            remaining -= restoreQty
        }
    }

    private fun registerRefundCashEgress(
        invoice: InvoiceHeader,
        creditNoteId: String,
        creditNoteCode: String,
        total: BigDecimal,
        idFormaPago: Int,
        username: String,
        now: LocalDateTime,
        date: LocalDate,
        cajaContext: CajaContext,
    ) {
        val cajaId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val detalleId = UUID.randomUUID().toString()
        val concepto = "Reintegro por nota de crédito $creditNoteCode / factura ${invoice.codFactura}"

        SalesCajaNuevaTable.insert {
            it[this.cajaId] = cajaId
            it[idTransaccion] = transactionId
            it[fecha] = date
            it[ingEg] = CajaIngresoEgreso.E
            it[monto] = total
            it[comprobante] = "NC"
            it[comprobanteNumero] = creditNoteCode
            it[idFactura] = invoice.idFactura
            it[idCliente] = invoice.idCliente
            it[status] = CajaStatus.Pagada
            it[sucursalId] = cajaContext.idSucursal
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[idCompra] = ""
            it[idProveedor] = ""
            it[SalesCajaNuevaTable.concepto] = concepto
            it[idOrdenPago] = ""
            it[serieSucursal] = cajaContext.serieSucursal
            it[idCajaSecuencia] = invoice.idCajaSecuencia
            it[idPedido] = ""
            it[idAbono] = ""
            it[idNotaCredito] = creditNoteId
        }

        SalesCajaNuevaDetalleTable.insert {
            it[cajaDetalleId] = detalleId
            it[this.cajaId] = cajaId
            it[this.idFormaPago] = idFormaPago
            it[idTransaccion] = transactionId
            it[cajaReciboId] = ""
            it[monto] = total
            it[montoOriginal] = total
            it[SalesCajaNuevaDetalleTable.concepto] = concepto
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[retencionTipo] = ""
            it[retencionPorcentaje] = ""
            it[numero] = ""
            it[observacion] = ""
            it[retencionBaseCalculo] = ""
            it[serieSucursal] = cajaContext.serieSucursal
            it[cajaSecuencia] = cajaContext.cajaSecuencia
            it[numeroControl] = ""
            it[numeroComprobante] = creditNoteCode
            it[retencionMonto] = ""
            it[retencionDetalleJson] = ""
            it[montoRecibido] = total
            it[montoMonedaPrincipal] = total
        }

        SalesCajaNuevaDetalleFormaPagoTable.insert {
            it[cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
            it[this.cajaId] = cajaId
            it[this.cajaDetalleId] = detalleId
            it[tipoMovimiento] = "OT"
            it[this.idFormaPago] = idFormaPago
            it[comprobante] = "NC"
            it[SalesCajaNuevaDetalleFormaPagoTable.concepto] = concepto
            it[monto] = total
            it[montoOriginal] = total
            it[tdcProveedor] = ""
            it[tdcNumero] = ""
            it[tdcTitular] = ""
            it[tdcVencimiento] = ""
            it[tdcCvv] = ""
            it[codigoVerificacion] = ""
            it[idAbonoDetalle] = ""
            it[efectivoCambio] = BigDecimal.ZERO.setScale(2)
        }
    }

    private fun registerAbono(
        creditNoteId: String,
        total: BigDecimal,
        client: ClientContext,
        username: String,
        now: LocalDateTime,
        date: LocalDate,
        cajaContext: CajaContext,
    ) {
        val current = CreditNoteCajaTable
            .select(CreditNoteCajaTable.abonoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq cajaContext.idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.abonoCorrelativo)
            ?: 0
        val next = current + 1
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cajaContext.idCaja }) {
            it[abonoCorrelativo] = next
        }

        CreditNoteAbonoTable.insert {
            it[idAbono] = UUID.randomUUID().toString()
            it[codAbono] = "AB-${cajaContext.codigoCaja}-${next.toString().padStart(5, '0')}"
            it[fecha] = date
            it[fechaCreacion] = now
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[idCliente] = client.idCliente
            it[idCaja] = cajaContext.idCaja
            it[monto] = total
            it[saldo] = total
            it[estatus] = 1
            it[descripcion] = "Abono generado por nota de crédito $creditNoteId"
            it[tipo] = "nota_credito"
            it[idOperacion] = creditNoteId
        }
    }

    private fun registerGiftCertificate(
        creditNoteId: String,
        total: BigDecimal,
        client: ClientContext,
        username: String,
        now: LocalDateTime,
        cajaContext: CajaContext,
    ) {
        val current = CreditNoteCajaTable
            .select(CreditNoteCajaTable.certificadoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq cajaContext.idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.certificadoCorrelativo)
            ?: 0
        val next = current + 1
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cajaContext.idCaja }) {
            it[certificadoCorrelativo] = next
        }

        CreditNoteGiftCertificateTable.insert {
            it[idCertificado] = UUID.randomUUID().toString()
            it[codigo] = "CG-${cajaContext.codigoCaja}-${next.toString().padStart(5, '0')}"
            it[monto] = total
            it[saldo] = total
            it[idCliente] = client.idCliente
            it[idCaja] = cajaContext.idCaja
            it[estatus] = 1
            it[usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
            it[fechaCreacion] = now
            it[idTransaccion] = creditNoteId
        }
    }

    private fun buildDetailResponse(header: CreditNoteHeaderContext, lines: List<CreditNoteDetailLine>): CreditNoteDetailResponse {
        return CreditNoteDetailResponse(
            id = header.id,
            codigo = header.codigo,
            facturaId = header.facturaId,
            facturaCodigo = header.facturaCodigo,
            fecha = formatDate(header.fecha),
            periodo = header.periodo,
            observacion = header.observacion,
            clienteNombre = header.clienteNombre,
            clienteIdentificacion = header.clienteIdentificacion,
            subtotal = header.subtotal.toDouble(),
            impuesto = header.impuesto.toDouble(),
            total = header.total.toDouble(),
            fiscalStatus = header.fiscalStatus,
            fiscalNumber = header.fiscalNumber,
            printerSerial = header.printerSerial,
            anulaFacturaCompleta = header.anulaFacturaCompleta,
            lines = lines,
            fiscalDocument = CreditNoteFiscalDocument(
                creditNoteId = header.id,
                creditNoteCode = header.codigo,
                date = formatDate(header.fecha),
                customerName = header.clienteNombre,
                customerIdentifier = header.clienteIdentificacion,
                customerAddress = header.clienteDireccion,
                customerPhone = header.clienteTelefono,
                originalInvoiceCode = header.facturaCodigo,
                originalFiscalNumber = header.originalFiscalNumber,
                originalInvoiceDate = formatDate(header.originalInvoiceDate),
                printerSerial = header.printerSerial,
                comment = header.observacion,
                lines = lines.map { line ->
                    CreditNoteFiscalLine(
                        description = line.descripcion,
                        quantity = line.cantidad,
                        totalWithTax = line.totalConIva,
                        taxRate = line.pIva,
                    )
                },
            ),
        )
    }

    private fun mapSummaryRow(row: ResultRow): CreditNoteSummary {
        val clienteNombre = listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "CONSUMIDOR FINAL" }
        val fiscalNumber = row[CreditNoteHeaderTable.codDevolucionFiscal].orEmpty().ifBlank {
            row[CreditNoteHeaderTable.numeroDocumentoFiscal].orEmpty()
        }

        return CreditNoteSummary(
            id = row[CreditNoteHeaderTable.idDevolucion],
            codigo = row[CreditNoteHeaderTable.codDevolucion],
            facturaId = row[CreditNoteHeaderTable.codFactura],
            facturaCodigo = row[CreditNoteFacturaTable.codFactura],
            fecha = formatDate(row[CreditNoteHeaderTable.fechaDevolucion]),
            fechaCreacion = formatDateTime(row[CreditNoteHeaderTable.fechaCreacion]),
            clienteNombre = clienteNombre,
            clienteIdentificacion = row[ClientsTable.rif],
            total = row[CreditNoteHeaderTable.total].toDouble(),
            subtotal = row[CreditNoteHeaderTable.subtotal].toDouble(),
            impuesto = row[CreditNoteHeaderTable.impuesto].toDouble(),
            fiscalStatus = resolveFiscalStatus(
                codDevolucionFiscal = row[CreditNoteHeaderTable.codDevolucionFiscal].orEmpty(),
                numeroDocumentoFiscal = row[CreditNoteHeaderTable.numeroDocumentoFiscal].orEmpty(),
            ),
            fiscalNumber = fiscalNumber,
            printerSerial = row[CreditNoteHeaderTable.impresoraSerial].orEmpty(),
            observacion = row[CreditNoteHeaderTable.observacion].orEmpty(),
        )
    }

    private fun mapHeaderContext(row: ResultRow): CreditNoteHeaderContext {
        val clienteNombre = listOf(row[ClientsTable.nombre], row[ClientsTable.apellido].orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "CONSUMIDOR FINAL" }

        val codDevolucionFiscal = row[CreditNoteHeaderTable.codDevolucionFiscal].orEmpty()
        val numeroDocumentoFiscal = row[CreditNoteHeaderTable.numeroDocumentoFiscal].orEmpty()

        return CreditNoteHeaderContext(
            id = row[CreditNoteHeaderTable.idDevolucion],
            codigo = row[CreditNoteHeaderTable.codDevolucion],
            facturaId = row[CreditNoteHeaderTable.codFactura],
            facturaCodigo = row[CreditNoteFacturaTable.codFactura],
            fecha = row[CreditNoteHeaderTable.fechaDevolucion],
            fechaCreacion = row[CreditNoteHeaderTable.fechaCreacion] ?: LocalDateTime.now(),
            periodo = row[CreditNoteHeaderTable.periodoDevolucion].orEmpty(),
            observacion = row[CreditNoteHeaderTable.observacion].orEmpty(),
            clienteNombre = clienteNombre,
            clienteIdentificacion = row[ClientsTable.rif],
            clienteDireccion = row[CreditNoteFacturaTable.facturarADireccion],
            clienteTelefono = row[CreditNoteFacturaTable.facturarATelefono],
            subtotal = row[CreditNoteHeaderTable.subtotal],
            impuesto = row[CreditNoteHeaderTable.impuesto],
            total = row[CreditNoteHeaderTable.total],
            fiscalStatus = resolveFiscalStatus(codDevolucionFiscal, numeroDocumentoFiscal),
            fiscalNumber = codDevolucionFiscal.ifBlank { numeroDocumentoFiscal },
            printerSerial = row[CreditNoteHeaderTable.impresoraSerial].orEmpty(),
            originalFiscalNumber = row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty().ifBlank {
                row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty()
            },
            originalInvoiceDate = row[CreditNoteFacturaTable.fechaFactura],
            anulaFacturaCompleta = row[CreditNoteHeaderTable.total].compareTo(row[CreditNoteFacturaTable.totalTotalFactura]) == 0,
        )
    }

    private fun mapDetailLine(row: ResultRow): CreditNoteDetailLine {
        return CreditNoteDetailLine(
            id = row[CreditNoteDetailTable.idDevolucionDetalle],
            idDetalleFactura = row[CreditNoteDetailTable.idDetalleFactura],
            idItem = row[CreditNoteDetailTable.idItem],
            descripcion = row[CreditNoteFacturaDetalleTable.itemDescripcion],
            codigo = row[CreditNoteDetailTable.itemCodigo].orEmpty(),
            referencia = row[CreditNoteDetailTable.itemReferencia].orEmpty(),
            cantidad = row[CreditNoteDetailTable.itemCantidad].toDouble(),
            precioSinIva = row[CreditNoteDetailTable.itemPrecioSinIva].toDouble(),
            descuentoPorcentaje = row[CreditNoteDetailTable.itemDescuento].toDouble(),
            descuentoMonto = row[CreditNoteDetailTable.itemMontoDescuento].toDouble(),
            pIva = row[CreditNoteDetailTable.itemPIva].toDouble(),
            totalSinIva = row[CreditNoteDetailTable.itemTotalSinIva].toDouble(),
            totalConIva = row[CreditNoteDetailTable.itemTotalConIva].toDouble(),
        )
    }

    private fun resolveFiscalStatus(codDevolucionFiscal: String, numeroDocumentoFiscal: String): CreditNoteFiscalStatus {
        val hasFiscalCode = codDevolucionFiscal.isNotBlank() && codDevolucionFiscal != PENDING_FISCAL_CODE
        val hasDocumentNumber = numeroDocumentoFiscal.isNotBlank() && numeroDocumentoFiscal != PENDING_FISCAL_CODE
        return if (hasFiscalCode || hasDocumentNumber) CreditNoteFiscalStatus.CONFIRMADA else CreditNoteFiscalStatus.PENDIENTE
    }

    private fun parseDate(value: String): LocalDate {
        return runCatching { LocalDate.parse(value) }
            .getOrElse { throw CreditNoteValidationException("Fecha inválida, usa formato yyyy-MM-dd") }
    }

    private fun formatDate(value: LocalDate?): String {
        if (value == null) return ""
        return value.format(DATE_FORMATTER)
    }

    private fun formatDateTime(value: LocalDateTime?): String {
        if (value == null) return ""
        return value.format(DATE_TIME_FORMATTER)
    }

    private fun divideSafe(value: BigDecimal, divisor: BigDecimal, scale: Int): BigDecimal {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        return value.divide(divisor, scale, RoundingMode.HALF_UP)
    }

    private fun ProcessedLine.toDetailLine(): CreditNoteDetailLine {
        return CreditNoteDetailLine(
            id = "",
            idDetalleFactura = sourceLine.idDetalleFactura,
            idItem = sourceLine.idItem,
            descripcion = sourceLine.descripcion,
            codigo = sourceLine.codigo,
            referencia = sourceLine.referencia,
            cantidad = quantity.toDouble(),
            precioSinIva = sourceLine.precioSinIva.toDouble(),
            descuentoPorcentaje = sourceLine.descuentoPorcentaje.toDouble(),
            descuentoMonto = discountAmount.toDouble(),
            pIva = sourceLine.pIva.toDouble(),
            totalSinIva = totalSinIva.toDouble(),
            totalConIva = totalConIva.toDouble(),
        )
    }

    private fun SourceInvoiceLine.toResponseLine(): CreditNoteSourceInvoiceLine {
        return CreditNoteSourceInvoiceLine(
            idDetalleFactura = idDetalleFactura,
            idItem = idItem,
            descripcion = descripcion,
            codigo = codigo,
            referencia = referencia,
            cantidadOriginal = quantityOriginal.toDouble(),
            cantidadDevuelta = returnedQuantity.toDouble(),
            cantidadDisponible = availableQuantity.toDouble(),
            precioSinIva = precioSinIva.toDouble(),
            descuentoPorcentaje = descuentoPorcentaje.toDouble(),
            descuentoMontoTotal = descuentoMontoTotal.toDouble(),
            pIva = pIva.toDouble(),
            totalSinIvaOriginal = totalSinIvaOriginal.toDouble(),
            totalConIvaOriginal = totalConIvaOriginal.toDouble(),
            totalSinIvaDisponible = availableTotalSinIva.toDouble(),
            totalConIvaDisponible = availableTotalConIva.toDouble(),
            almacen = almacen,
        )
    }

    private data class RequestedLine(
        val idDetalleFactura: String,
        val cantidad: Double,
    )

    private data class CreditNoteTotals(
        val subtotal: BigDecimal,
        val tax: BigDecimal,
        val total: BigDecimal,
    )

    private data class ClientContext(
        val idCliente: String,
        val codigoCliente: String,
        val nombreCompleto: String,
        val identificacion: String,
        val direccion: String,
        val telefono: String,
    )

    private data class InvoiceHeader(
        val idFactura: String,
        val codFactura: String,
        val codFacturaFiscal: String,
        val numeroDocumentoFiscal: String,
        val idCliente: String,
        val codVendedor: Int,
        val codEstatus: Int,
        val fechaFactura: LocalDate?,
        val subtotal: BigDecimal,
        val totalizarSubTotal: BigDecimal,
        val totalizarMontoIva: BigDecimal,
        val totalTotalFactura: BigDecimal,
        val formaPago: String,
        val idCajaSecuencia: String,
        val idCaja: String,
        val idSucursal: Int,
        val serieSucursal: String,
        val codigoCaja: String,
        val facturarA: String,
        val facturarARuc: String,
        val facturarADireccion: String,
        val facturarATelefono: String,
        val moneda: String,
    )

    private data class SourceInvoiceLine(
        val idDetalleFactura: String,
        val idItem: Int,
        val descripcion: String,
        val codigo: String,
        val referencia: String,
        val quantityOriginal: BigDecimal,
        val returnedQuantity: BigDecimal,
        val availableQuantity: BigDecimal,
        val precioSinIva: BigDecimal,
        val descuentoPorcentaje: BigDecimal,
        val descuentoMontoTotal: BigDecimal,
        val pIva: BigDecimal,
        val totalSinIvaOriginal: BigDecimal,
        val totalConIvaOriginal: BigDecimal,
        val availableTotalSinIva: BigDecimal,
        val availableTotalConIva: BigDecimal,
        val unitDiscountAmount: BigDecimal,
        val almacen: Int,
        val codVendedor: Int,
    )

    private data class ProcessedLine(
        val sourceLine: SourceInvoiceLine,
        val quantity: BigDecimal,
        val discountAmount: BigDecimal,
        val totalSinIva: BigDecimal,
        val totalConIva: BigDecimal,
    )

    private data class CajaContext(
        val idCaja: String,
        val idSucursal: Int,
        val codigoCaja: String,
        val serieSucursal: String,
        val cajaSecuencia: String,
        val impresoraModelo: String,
    )

    private data class CreditNoteHeaderContext(
        val id: String,
        val codigo: String,
        val facturaId: String,
        val facturaCodigo: String,
        val fecha: LocalDate?,
        val fechaCreacion: LocalDateTime,
        val periodo: String,
        val observacion: String,
        val clienteNombre: String,
        val clienteIdentificacion: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val subtotal: BigDecimal,
        val impuesto: BigDecimal,
        val total: BigDecimal,
        val fiscalStatus: CreditNoteFiscalStatus,
        val fiscalNumber: String,
        val printerSerial: String,
        val originalFiscalNumber: String,
        val originalInvoiceDate: LocalDate?,
        val anulaFacturaCompleta: Boolean,
    )

    private companion object {
        const val CREDIT_NOTE_PAYMENT_FORM_ID = 30
        const val MAX_OBSERVATION_LENGTH = 300
        const val MAX_PERIOD_LENGTH = 20
        const val MAX_USERNAME_LENGTH = 50
        const val PENDING_FISCAL_CODE = "00000000"
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_DATE
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

private fun BigDecimal.coerceAtLeastZero(scale: Int): BigDecimal {
    return if (this < BigDecimal.ZERO) BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP) else setScale(scale, RoundingMode.HALF_UP)
}

private fun BigDecimal.isEffectivelyZero(): Boolean = setScale(3, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)) == 0
