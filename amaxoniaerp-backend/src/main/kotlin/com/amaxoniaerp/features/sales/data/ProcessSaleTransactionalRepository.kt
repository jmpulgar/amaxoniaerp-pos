package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTable
import com.amaxoniaerp.features.companies.data.TasasCambioTable
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.UUID

class ProcessSaleTransactionalRepository {

    fun process(request: ProcessSaleRequest): ProcessSaleResponse {
        val preparedRequest = prepareRequestWithWarehouses(request)
        val monetaryContext = resolveMonetaryContext(preparedRequest)

        validateDuplicateInvoice(preparedRequest)
        if (!monetaryContext.validarStock.equals("NO", ignoreCase = true)) {
            validateStock(preparedRequest)
        }

        val invoiceId = preparedRequest.idFactura?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val invoiceCode = resolveInvoiceCode(preparedRequest)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        insertFactura(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
        insertFacturaDetalle(preparedRequest, invoiceId, now, monetaryContext)
        insertFacturaImpuestos(preparedRequest, invoiceId, now, monetaryContext)
        insertFacturaDetalleFormaPago(preparedRequest, invoiceId, now, monetaryContext)

        val shouldAffectInventory = (preparedRequest.procesar == 1 || preparedRequest.factura.codEstatus == 2) && !preparedRequest.esCobroCreditoPrevio
        if (shouldAffectInventory) {
            updateInventoryAndKardex(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
            insertCajaEntries(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
        }

        if (monetaryContext.multiMoneda == "SI" && monetaryContext.idTasa > 0) {
            TasasCambioTable.update({ TasasCambioTable.id eq monetaryContext.idTasa.toLong() }) {
                it[TasasCambioTable.facturado] = "S"
            }
        }

        return ProcessSaleResponse(
            success = true,
            idFactura = invoiceId,
            codFactura = invoiceCode,
            codEstatus = preparedRequest.factura.codEstatus,
        )
    }

    private fun prepareRequestWithWarehouses(request: ProcessSaleRequest): ProcessSaleRequest {
        val context = resolveWarehouseContext(request.factura.idCaja)

        val normalizedItems = request.items.map { item ->
            val resolvedWarehouse = item.itemAlmacen.takeIf { it > 0 } ?: context.defaultWarehouseId
            item.copy(itemAlmacen = resolvedWarehouse)
        }

        val normalizedFactura = request.factura.copy(
            idSucursal = context.idSucursal ?: request.factura.idSucursal,
            serieSucursal = context.serieSucursal
                ?: request.factura.serieSucursal.take(10)
        )
        val normalizedPayments = request.pagos.map { payment ->
            val normalizedAmount = if (payment.monto > 0.0) payment.monto else payment.montoRecibido
            payment.copy(
                tipoMovimiento = normalizeTipoMovimiento(payment.tipoMovimiento),
                monto = normalizedAmount,
            )
        }

        validateWarehouseOwnership(
            request = request,
            normalizedItems = normalizedItems,
            context = context,
        )

        return request.copy(
            factura = normalizedFactura,
            items = normalizedItems,
            pagos = normalizedPayments,
        )
    }

    private fun resolveWarehouseContext(cajaId: String): WarehouseContext {
        val caja = SalesCajaTable
            .select(SalesCajaTable.idSucursal, SalesCajaTable.codAlmacen)
            .where { SalesCajaTable.id eq cajaId }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$cajaId")

        val cajaWarehouseId = caja[SalesCajaTable.codAlmacen]?.takeIf { it > 0 }
        val cajaSucursalId = caja[SalesCajaTable.idSucursal]
        val serieSucursal = cajaSucursalId?.let { sucursalId ->
            SalesSucursalTable
                .select(SalesSucursalTable.serie)
                .where { SalesSucursalTable.id eq sucursalId }
                .limit(1)
                .firstOrNull()
                ?.get(SalesSucursalTable.serie)
                ?.takeIf { it.isNotBlank() }
        }

        val globalWarehouseId = ParametrosGeneralesTable
            .select(ParametrosGeneralesTable.codAlmacen)
            .orderBy(ParametrosGeneralesTable.codEmpresa)
            .limit(1)
            .firstOrNull()
            ?.get(ParametrosGeneralesTable.codAlmacen)
            ?.let { kotlin.math.abs(it) }
            ?.takeIf { it > 0 }

        val sucursalDefaultWarehouse = cajaSucursalId?.let { sucursalId ->
            SalesSucursalAlmacenTable
                .select(SalesSucursalAlmacenTable.idAlmacen)
                .where {
                    (SalesSucursalAlmacenTable.idSucursal eq sucursalId) and
                        (SalesSucursalAlmacenTable.defaultVentas eq 1)
                }
                .limit(1)
                .firstOrNull()
                ?.get(SalesSucursalAlmacenTable.idAlmacen)
                ?.takeIf { it > 0 }
        }

        val defaultWarehouseId = cajaWarehouseId ?: sucursalDefaultWarehouse ?: globalWarehouseId
            ?: throw InvalidSaleRequestException(
                "No se pudo resolver almacén por defecto para caja=$cajaId (caja/sucursal/parámetros generales)"
            )

        val allowedWarehouseIds = mutableSetOf<Int>()
        if (cajaWarehouseId != null) {
            allowedWarehouseIds += cajaWarehouseId
        }
        if (globalWarehouseId != null) {
            allowedWarehouseIds += globalWarehouseId
        }
        if (cajaSucursalId != null) {
            allowedWarehouseIds += SalesSucursalAlmacenTable
                .select(SalesSucursalAlmacenTable.idAlmacen)
                .where { SalesSucursalAlmacenTable.idSucursal eq cajaSucursalId }
                .mapNotNull { row -> row[SalesSucursalAlmacenTable.idAlmacen].takeIf { it > 0 } }
        }
        allowedWarehouseIds += defaultWarehouseId

        return WarehouseContext(
            defaultWarehouseId = defaultWarehouseId,
            allowedWarehouseIds = allowedWarehouseIds,
            idSucursal = cajaSucursalId,
            serieSucursal = serieSucursal,
        )
    }

    private fun validateWarehouseOwnership(
        request: ProcessSaleRequest,
        normalizedItems: List<SaleItemInput>,
        context: WarehouseContext,
    ) {
        val invalidWarehouses = normalizedItems
            .map { it.itemAlmacen }
            .filter { it !in context.allowedWarehouseIds }
            .distinct()

        if (invalidWarehouses.isNotEmpty()) {
            throw InvalidSaleRequestException(
                "Almacen(es) no permitidos para caja=${request.factura.idCaja}: ${invalidWarehouses.joinToString(",")}. Permitidos: ${context.allowedWarehouseIds.sorted().joinToString(",")}" 
            )
        }
    }

    private data class WarehouseContext(
        val defaultWarehouseId: Int,
        val allowedWarehouseIds: Set<Int>,
        val idSucursal: Int?,
        val serieSucursal: String?,
    )

    private fun resolveMonetaryContext(request: ProcessSaleRequest): MonetaryContext {
        val params = ParametrosGeneralesTable
            .select(
                ParametrosGeneralesTable.multiMoneda,
                ParametrosGeneralesTable.monedaBase,
                ParametrosGeneralesTable.abrMonedaBase,
                ParametrosGeneralesTable.monedaSecundaria,
                ParametrosGeneralesTable.abrMonedaSecundaria,
                ParametrosGeneralesTable.validarStock,
                ParametrosGeneralesTable.porcentajeImpuestoPrincipal,
                ParametrosGeneralesTable.defaultIdFormaPagoFactura,
                ParametrosGeneralesTable.diasVencimiento,
            )
            .orderBy(ParametrosGeneralesTable.codEmpresa)
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró parametros_generales")

        val paramsMulti = params[ParametrosGeneralesTable.multiMoneda].equals("Si", ignoreCase = true)
        val multiMoneda = if (paramsMulti) "SI" else "NO"

        val monedaBase = params[ParametrosGeneralesTable.monedaBase] ?: 1
        val abrMonedaBase = params[ParametrosGeneralesTable.abrMonedaBase].take(10)
        val monedaSecundaria = params[ParametrosGeneralesTable.monedaSecundaria]
        val abrMonedaSecundaria = params[ParametrosGeneralesTable.abrMonedaSecundaria].take(10)

        val providedMoneda = request.moneda
        val tasaFromRequest = providedMoneda?.tasa?.takeIf { it > 0.0 }
        val idTasaFromRequest = providedMoneda?.idTasa?.takeIf { it > 0 }

        val tasaRow = if (paramsMulti && (tasaFromRequest == null || idTasaFromRequest == null)) {
            TasasCambioTable
                .select(TasasCambioTable.id, TasasCambioTable.tasaInversa)
                .where {
                    (TasasCambioTable.divisa eq monedaSecundaria) and
                        (TasasCambioTable.monedabase eq monedaBase)
                }
                .orderBy(TasasCambioTable.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        } else {
            null
        }

        val tasa = if (paramsMulti) {
            tasaFromRequest
                ?: tasaRow?.get(TasasCambioTable.tasaInversa)?.toDouble()
                ?: throw InvalidSaleRequestException("No se encontró tasa de cambio vigente")
        } else {
            1.0
        }

        val idTasa = if (paramsMulti) {
            idTasaFromRequest
                ?: tasaRow?.get(TasasCambioTable.id)?.toInt()
                ?: throw InvalidSaleRequestException("No se encontró id de tasa vigente")
        } else {
            0
        }

        return MonetaryContext(
            multiMoneda = multiMoneda,
            tasa = BigDecimal.valueOf(tasa).setScale(8, RoundingMode.HALF_UP),
            idTasa = idTasa,
            monedaBase = monedaBase,
            abrMonedaBase = abrMonedaBase,
            monedaSecundaria = monedaSecundaria,
            abrMonedaSecundaria = abrMonedaSecundaria,
            totalRef = providedMoneda?.totalRef ?: request.factura.totalTotalFactura,
            validarStock = params[ParametrosGeneralesTable.validarStock],
            defaultTaxRate = params[ParametrosGeneralesTable.porcentajeImpuestoPrincipal].toDouble(),
            defaultFormaPagoId = params[ParametrosGeneralesTable.defaultIdFormaPagoFactura],
            diasVencimiento = params[ParametrosGeneralesTable.diasVencimiento],
        )
    }

    private data class MonetaryContext(
        val multiMoneda: String,
        val tasa: BigDecimal,
        val idTasa: Int,
        val monedaBase: Int,
        val abrMonedaBase: String,
        val monedaSecundaria: Int,
        val abrMonedaSecundaria: String,
        val totalRef: Double,
        val validarStock: String,
        val defaultTaxRate: Double,
        val defaultFormaPagoId: Int,
        val diasVencimiento: Int,
    ) {
        fun toBase(amountRef: Double): BigDecimal {
            val normalizedRef = BigDecimal.valueOf(amountRef)
            return if (multiMoneda == "SI") {
                normalizedRef.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
            } else {
                normalizedRef.setScale(2, RoundingMode.HALF_UP)
            }
        }
    }

    private fun validateDuplicateInvoice(request: ProcessSaleRequest) {
        val idFactura = request.idFactura?.takeIf { it.isNotBlank() }
        if (idFactura == null) return

        val existing = SalesFacturaTable
            .select(SalesFacturaTable.idFactura, SalesFacturaTable.codFactura, SalesFacturaTable.codEstatus)
            .where {
                SalesFacturaTable.idFactura eq idFactura
            }
            .limit(1)
            .firstOrNull()
            ?: return

        val status = existing[SalesFacturaTable.codEstatus] ?: 0
        if (status == 2) {
            throw DuplicateInvoiceException("La factura ya existe y está procesada (cod_estatus=2)")
        }
        throw DuplicateInvoiceException("La factura ya existe con estado pendiente, no se puede reprocesar")
    }

    private fun validateStock(request: ProcessSaleRequest) {
        val requiredByItemWarehouse = request.items
            .filter { it.esProductoFisico }
            .groupBy { it.idItem to it.itemAlmacen }
            .mapValues { (_, lines) -> lines.sumOf { it.itemCantidadTotal }.toScaledBigDecimal(2) }

        if (requiredByItemWarehouse.isEmpty()) return

        val itemIds = requiredByItemWarehouse.keys.map { it.first }.distinct()
        val almacenes = requiredByItemWarehouse.keys.map { it.second }.distinct()

        val available = SalesStockTable
            .selectAll()
            .where {
                (SalesStockTable.idItem inList itemIds) and
                    (SalesStockTable.codAlmacen inList almacenes)
            }
            .associate { row ->
                (row[SalesStockTable.idItem] to row[SalesStockTable.codAlmacen]) to
                    row[SalesStockTable.cantidad].toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            }

        val failures = mutableListOf<String>()
        requiredByItemWarehouse.forEach { (key, required) ->
            val availableQty = available[key] ?: BigDecimal.ZERO.setScale(2)
            if (availableQty < required) {
                failures.add(
                    "item=${key.first}, almacen=${key.second}, solicitado=$required, disponible=$availableQty"
                )
            }
        }

        if (failures.isNotEmpty()) {
            throw InsufficientStockException("Stock insuficiente: ${failures.joinToString(" | ")}")
        }
    }

    private fun resolveInvoiceCode(request: ProcessSaleRequest): String {
        val idCaja = request.factura.idCaja.trim()
        if (idCaja.isBlank()) {
            throw InvalidSaleRequestException("idCaja es obligatorio para generar cod_factura desde caja")
        }

        var invoiceCode = getNextCodePreviewFromCaja(idCaja, request.factura.codigoCaja)
        var jumpedDuplicate = false

        while (invoiceCodeExists(invoiceCode)) {
            jumpedDuplicate = true
            invoiceCode = consumeAndGetNextCodeFromCaja(idCaja, request.factura.codigoCaja)
        }

        if (!jumpedDuplicate) {
            consumeCorrelativoCaja(idCaja)
        }

        return invoiceCode
    }

    private fun invoiceCodeExists(code: String): Boolean {
        return SalesFacturaTable
            .select(SalesFacturaTable.idFactura)
            .where { SalesFacturaTable.codFactura eq code }
            .limit(1)
            .any()
    }

    private fun getNextCodePreviewFromCaja(idCaja: String, fallbackCodigoCaja: String): String {
        val row = SalesCajaTable
            .select(SalesCajaTable.codigo, SalesCajaTable.facturaCorrelativo)
            .where { SalesCajaTable.id eq idCaja }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

        val codigoCaja = row[SalesCajaTable.codigo]?.takeIf { it.isNotBlank() } ?: fallbackCodigoCaja
        val correlativo = row[SalesCajaTable.facturaCorrelativo] + 1
        return formatLegacyInvoiceCode(codigoCaja, correlativo)
    }

    private fun consumeAndGetNextCodeFromCaja(idCaja: String, fallbackCodigoCaja: String): String {
        repeat(10) {
            val row = SalesCajaTable
                .select(SalesCajaTable.codigo, SalesCajaTable.facturaCorrelativo)
                .where { SalesCajaTable.id eq idCaja }
                .limit(1)
                .firstOrNull()
                ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

            val current = row[SalesCajaTable.facturaCorrelativo]
            val next = current + 1
            val updated = SalesCajaTable.update({
                (SalesCajaTable.id eq idCaja) and (SalesCajaTable.facturaCorrelativo eq current)
            }) {
                it[facturaCorrelativo] = facturaCorrelativo.plus(1)
            }

            if (updated == 1) {
                val codigoCaja = row[SalesCajaTable.codigo]?.takeIf { it.isNotBlank() } ?: fallbackCodigoCaja
                return formatLegacyInvoiceCode(codigoCaja, next)
            }
        }

        throw InvalidSaleRequestException("No se pudo avanzar factura_correlativo para caja=$idCaja")
    }

    private fun consumeCorrelativoCaja(idCaja: String) {
        repeat(10) {
            val current = SalesCajaTable
                .select(SalesCajaTable.facturaCorrelativo)
                .where { SalesCajaTable.id eq idCaja }
                .limit(1)
                .firstOrNull()
                ?.get(SalesCajaTable.facturaCorrelativo)
                ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

            val updated = SalesCajaTable.update({
                (SalesCajaTable.id eq idCaja) and (SalesCajaTable.facturaCorrelativo eq current)
            }) {
                it[facturaCorrelativo] = facturaCorrelativo.plus(1)
            }

            if (updated == 1) return
        }

        throw InvalidSaleRequestException("No se pudo consumir correlativo de caja para id_caja=$idCaja")
    }

    private fun formatLegacyInvoiceCode(codigoCaja: String, correlativo: Int): String {
        if (codigoCaja.isBlank()) {
            throw InvalidSaleRequestException("codigo de caja inválido para construir cod_factura")
        }
        return "${codigoCaja.trim()}-${correlativo.toString().padStart(5, '0')}"
    }

    private fun insertFactura(
        request: ProcessSaleRequest,
        invoiceId: String,
        invoiceCode: String,
        now: LocalDateTime,
        today: LocalDate,
        monetaryContext: MonetaryContext,
    ) {
        val f = request.factura
        val subtotalBase = monetaryContext.toBase(f.subtotal)
        val ivaTotalBase = monetaryContext.toBase(f.ivaTotalFactura)
        val totalGeneralBase = monetaryContext.toBase(f.totalTotalFactura)
        val fechaVencimientoFactura = today.plusDays(monetaryContext.diasVencimiento.toLong())
        val totalBultosQty = request.items.sumOf { it.itemCantidadTotal }
        val serieSucursalValue = f.serieSucursal.take(10)
        val cajaSecuenciaValue = resolveCajaSecuenciaCodigo(f.idCajaSecuencia)

        SalesFacturaTable.insert {
            it[idFactura] = invoiceId
            it[codFactura] = invoiceCode
            it[codFacturaFiscal] = f.codFacturaFiscal
            it[nroz] = f.nroz
            it[impresoraSerial] = f.impresoraSerial
            it[idCliente] = f.idCliente
            it[codVendedor] = f.codVendedor
            it[fechaFactura] = parseDateOrToday(f.fechaFactura, today)
            it[subtotal] = subtotalBase
            it[descuentosItemFactura] = BigDecimal.ZERO.setScale(2)
            it[montoItemsFactura] = subtotalBase
            it[ivaTotalFactura] = ivaTotalBase
            it[totalTotalFactura] = totalGeneralBase
            it[cantidadItems] = request.items.size
            it[totalizarSubTotal] = subtotalBase
            it[totalizarDescuentoParcial] = BigDecimal.ZERO.setScale(2)
            it[totalizarTotalOperacion] = subtotalBase
            it[totalizarPDescuentoGlobal] = BigDecimal.ZERO.setScale(2)
            it[totalizarDescuentoGlobal] = BigDecimal.ZERO.setScale(2)
            it[totalizarBaseImponible] = subtotalBase
            it[totalizarMontoIva] = ivaTotalBase
            it[totalizarTotalGeneral] = totalGeneralBase
            it[totalizarTotalRetencion] = BigDecimal.ZERO.setScale(2)
            it[formaPago] = "contado"
            it[codEstatus] = f.codEstatus
            it[totalBultos] = totalBultosQty.toMoney()
            it[fechaCreacion] = now
            it[usuarioCreacion] = f.usuarioCreacion
            it[tipoFactura] = "factura_pos"
            it[modeloFactura] = "pos"
            it[terminoPagoId] = monetaryContext.defaultFormaPagoId.takeIf { id -> id > 0 } ?: 3
            it[facturarA] = f.facturarA
            it[facturarARuc] = f.facturarARuc
            it[facturarADireccion] = f.facturarADireccion
            it[facturarATelefono] = f.facturarATelefono
            it[validarStock] = monetaryContext.validarStock
            it[idShop] = f.idShop
            it[servicioPeriodo] = ""
            it[servicioOrden] = ""
            it[observacion] = ""
            it[fechaVencimiento] = fechaVencimientoFactura
            it[servicioAnio] = today.year
            it[servicioMes] = today.monthValue.toString().padStart(2, '0')
            it[idCajaSecuencia] = f.idCajaSecuencia
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = today
            it[serieSucursal] = serieSucursalValue
            it[cajaSecuencia] = cajaSecuenciaValue
            it[idSucursal] = f.idSucursal
            it[idCaja] = f.idCaja
            it[codigoCaja] = f.codigoCaja
            it[codCliente] = f.codCliente
            it[multiMoneda] = monetaryContext.multiMoneda
            it[tasa] = monetaryContext.tasa.toFloat()
            it[idTasa] = monetaryContext.idTasa
            it[monedaBase] = monetaryContext.monedaBase
            it[abrMonedaBase] = monetaryContext.abrMonedaBase
            it[monedaSecundaria] = monetaryContext.monedaSecundaria
            it[abrMonedaSecundaria] = monetaryContext.abrMonedaSecundaria
            it[totalRef] = monetaryContext.totalRef.toFloat()
        }
    }

    private fun insertFacturaDetalle(
        request: ProcessSaleRequest,
        invoiceId: String,
        now: LocalDateTime,
        monetaryContext: MonetaryContext,
    ) {
        val vendedorPorDefecto = request.factura.codVendedor
        val usuario = request.factura.usuarioCreacion.take(32)

        request.items.forEach { item ->
            val vendedorLinea = item.codVendedor?.takeIf { it > 0 } ?: vendedorPorDefecto
            val itemTaxRate = item.itemPIva.takeIf { it > 0.0 } ?: monetaryContext.defaultTaxRate
            val itemTotalSinIvaBase = monetaryContext.toBase(item.itemTotalSinIva)
            val itemTotalConIvaBase = monetaryContext.toBase(item.itemTotalConIva)
            val qty = item.itemCantidadTotal.toScaledBigDecimal(3)
            val itemPriceSinIvaBase = if (qty.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO.setScale(2)
            } else {
                itemTotalSinIvaBase
                    .divide(qty, 6, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP)
            }

            SalesFacturaDetalleTable.insert {
                it[idDetalleFactura] = UUID.randomUUID().toString()
                it[idFactura] = invoiceId
                it[idItem] = item.idItem
                it[itemAlmacen] = item.itemAlmacen
                it[itemDescripcion] = item.itemDescripcion
                it[itemCantidad] = item.itemCantidad.toScaledBigDecimal(3)
                it[itemPrecioSinIva] = itemPriceSinIvaBase
                it[itemPiva] = itemTaxRate.toMoney()
                it[itemTotalSinIva] = itemTotalSinIvaBase
                it[itemTotalConIva] = itemTotalConIvaBase
                it[cantidadBulto] = 1
                it[gananciaItemIndividual] = itemTotalSinIvaBase
                it[porcentajeGanancia] = BigDecimal.valueOf(100.0).setScale(2)
                it[poseeSerial] = "NO"
                it[serialesSeleccionados] = ""
                it[usuarioCreacion] = usuario
                it[fechaCreacion] = now
                it[itemListaPrecio] = "BASE"
                it[itemUnidadEmpaque] = "UNIDAD"
                it[itemCantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(0)
                it[promocionTipo] = ""
                it[promocionCodigo] = ""
                it[promocionNombre] = ""
                it[promocionGrupo] = ""
                it[promocionDetalleId] = ""
                it[grupo] = 1
                it[descuentoAutorizacion] = ""
                it[codVendedor] = vendedorLinea
                it[itemCodigo] = item.itemCodigo
                it[itemReferencia] = item.itemReferencia
            }
        }
    }

    private fun insertFacturaImpuestos(
        request: ProcessSaleRequest,
        invoiceId: String,
        now: LocalDateTime,
        monetaryContext: MonetaryContext,
    ) {
        request.impuestos.forEach { tax ->
            SalesFacturaImpuestosTable.insert {
                it[idFacturaImpuestos] = UUID.randomUUID().toString()
                it[idFactura] = invoiceId
                it[totalizarBaseRetencion] = monetaryContext.toBase(tax.totalizarBaseRetencion)
                it[codImpuestoIva] = tax.codImpuestoIva
                it[totalizarMontoIva2] = monetaryContext.toBase(tax.totalizarMontoIva2)
                it[usuarioCreacion] = request.factura.usuarioCreacion
                it[fechaCreacion] = now
            }
        }
    }

    private fun insertFacturaDetalleFormaPago(
        request: ProcessSaleRequest,
        invoiceId: String,
        now: LocalDateTime,
        monetaryContext: MonetaryContext,
    ) {
        val resumen = request.pagoResumen
        val montosPorTipo = request.pagos
            .groupBy { it.tipoMovimiento.trim().uppercase() }
            .mapValues { (_, list) -> list.sumOf { it.monto } }

        fun amountOf(vararg keys: String): Double = keys.sumOf { key -> montosPorTipo[key] ?: 0.0 }

        val montoEfectivo = amountOf("CASH", "EF", "EFE", "EFECTIVO")
        val montoCheque = amountOf("CH", "CHEQUE")
        val montoTarjeta = amountOf("TDC", "TARJETA", "PV", "POS", "NEQ")
        val montoDeposito = amountOf("DE", "DEPOSITO")
        val montoTransferencia = amountOf("TR", "TRANSFERENCIA", "PM")
        val montoCredito = amountOf("CR", "CREDITO")
        val montoDebito = amountOf("DB", "DEBITO")
        val montoCertificado = amountOf("CERT", "CERTIFICADO")
        val montoCxc = amountOf("CXC")

        val knownCodes = setOf(
            "CASH", "EF", "EFE", "EFECTIVO",
            "CH", "CHEQUE",
            "TDC", "TARJETA", "PV", "POS", "NEQ",
            "DE", "DEPOSITO",
            "TR", "TRANSFERENCIA", "PM",
            "CR", "CREDITO",
            "DB", "DEBITO",
            "CERT", "CERTIFICADO",
            "CXC",
            "OT", "MB"
        )

        val montoOtros = amountOf("OT", "MB") + montosPorTipo
            .filterKeys { it !in knownCodes }
            .values
            .sum()

        SalesFacturaDetalleFormaPagoTable.insert {
            it[codFacturaDetalleFormaPago] = UUID.randomUUID().toString()
            it[idFactura] = invoiceId
            it[totalizarMontoCancelar] = monetaryContext.toBase(resumen.totalizarMontoCancelar)
            it[totalizarSaldoPendiente] = monetaryContext.toBase(resumen.totalizarSaldoPendiente)
            it[totalizarCambio] = monetaryContext.toBase(resumen.totalizarCambio)
            it[totalizarMontoEfectivo] = monetaryContext.toBase(montoEfectivo)
            it[optCheque] = if (montoCheque > 0.0) 1 else 0
            it[totalizarMontoCheque] = monetaryContext.toBase(montoCheque)
            it[totalizarNroCheque] = BigDecimal.ZERO.setScale(2)
            it[totalizarNombreBanco] = 0
            it[optTarjeta] = if (montoTarjeta > 0.0) 1 else 0
            it[totalizarMontoTarjeta] = monetaryContext.toBase(montoTarjeta)
            it[totalizarNroTarjeta] = BigDecimal.ZERO.setScale(2)
            it[totalizarTipoTarjeta] = 0
            it[optDeposito] = if (montoDeposito > 0.0) 1 else 0
            it[totalizarMontoDeposito] = monetaryContext.toBase(montoDeposito)
            it[totalizarNroDeposito] = BigDecimal.ZERO.setScale(2)
            it[totalizarBancoDeposito] = 0
            it[fechaVencimiento] = null
            it[observacion] = ""
            it[personaContacto] = ""
            it[telefono] = ""
            it[optOtroDocumento] = if (montoOtros > 0.0) 1 else 0
            it[totalizarTipoOtroDocumento] = 0
            it[totalizarMontoOtroDocumento] = monetaryContext.toBase(montoOtros)
            it[totalizarNroOtroDocumento] = 0
            it[totalizarBancoOtroDocumento] = 0
            it[fechaCreacion] = now
            it[usuarioCreacion] = request.factura.usuarioCreacion.take(60)
            it[totalizarMontoCredito] = monetaryContext.toBase(montoCredito)
            it[totalizarMontoDebito] = monetaryContext.toBase(montoDebito)
            it[totalizarMontoTransferencia] = monetaryContext.toBase(montoTransferencia)
            it[totalizarMontoCertificado] = monetaryContext.toBase(montoCertificado)
            it[totalizarMontoCxc] = monetaryContext.toBase(montoCxc)
            it[totalizarMontoOtros] = monetaryContext.toBase(montoOtros)
            it[totalizarMontoDivisa] = BigDecimal.ZERO.setScale(2)
        }
    }

    private fun updateInventoryAndKardex(
        request: ProcessSaleRequest,
        invoiceId: String,
        invoiceCode: String,
        now: LocalDateTime,
        today: LocalDate,
        monetaryContext: MonetaryContext,
    ) {
        val physicalItems = request.items.filter { it.esProductoFisico }
        if (physicalItems.isEmpty()) return

        val kardexId = UUID.randomUUID().toString()
        val documentCode = "FACT-$invoiceCode"

        physicalItems.forEach { item ->
            val requested = item.itemCantidadTotal.toScaledBigDecimal(2)
            val updated = SalesStockTable.update({
                (SalesStockTable.idItem eq item.idItem) and
                    (SalesStockTable.codAlmacen eq item.itemAlmacen) and
                    (SalesStockTable.cantidad greaterEq requested.toFloat())
            }) {
                it.update(cantidad, cantidad.minus(requested.toFloat()))
            }

            if (updated != 1) {
                throw InsufficientStockException(
                    "No se pudo descontar stock para item=${item.idItem}, almacen=${item.itemAlmacen}"
                )
            }
        }

        SalesKardexTable.insert {
            it[idTransaccion] = kardexId
            it[tipoMovimientoAlmacen] = 2
            it[autorizadoPor] = request.factura.usuarioCreacion
            it[observacion] = "Salida por Ventas"
            it[fecha] = today
            it[usuarioCreacion] = request.factura.usuarioCreacion
            it[fechaCreacion] = now
            it[estado] = "Procesado"
            it[idDocumento] = invoiceId
            it[codProveedor] = 0
            it[comprobante] = "FACT"
            it[anio] = Year.now().value % 100
            it[tipoCosto] = "PROM"
            it[estatus] = 1
            it[entregadoACodigo] = "POS"
            it[entregadoANombre] = "VENTA"
            it[codDocumento] = documentCode
            it[subtipoMovimientoAlmacen] = 0
            it[contabilizado] = 0
            it[fechaContabilizacion] = today
            it[usuarioContabilizacion] = ""
            it[idAlmacenSalida] = physicalItems.first().itemAlmacen
            it[idSucursal] = request.factura.idSucursal
            it[validadoFecha] = today
            it[validadoUsuario] = request.factura.usuarioCreacion.take(20)
            it[validadoObservacion] = "Salida por Ventas"
        }

        physicalItems.forEach { item ->
            SalesKardexDetalleTable.insert {
                it[idTransaccionDetalle] = UUID.randomUUID().toString()
                it[idTransaccion] = kardexId
                it[idAlmacenEntrada] = 0
                it[idAlmacenSalida] = item.itemAlmacen
                it[idItem] = item.idItem
                it[cantidad] = item.itemCantidadTotal.toFloat()
                it[cantidadDistribuida] = 0
                it[precio] = monetaryContext.toBase(item.itemPrecioSinIva)
                it[cantidadMuestra] = 0
                it[unidadBulto] = "UNIDAD"
                it[cantidadBulto] = BigDecimal.ONE.setScale(2)
                it[unidadEmpaque] = "UNIDAD"
                it[cantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(2)
                it[costo] = BigDecimal.ZERO.setScale(2)
            }
        }
    }

    private fun insertCajaEntries(
        request: ProcessSaleRequest,
        invoiceId: String,
        invoiceCode: String,
        now: LocalDateTime,
        today: LocalDate,
        monetaryContext: MonetaryContext,
    ) {
        val cajaId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val cajaReciboId = UUID.randomUUID().toString()
        val resumen = request.pagoResumen
        val totalBase = monetaryContext.toBase(resumen.totalizarMontoCancelar)
        val clienteNombre = request.factura.facturarA.ifBlank { "CLIENTE MOSTRADOR" }
        val fechaTexto = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val montoTexto = totalBase.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val conceptoCaja = "Ingreso por Factura #$invoiceCode, Fecha: $fechaTexto, Cliente: $clienteNombre, Monto: $montoTexto."

        SalesCajaNuevaTable.insert {
            it[this.cajaId] = cajaId
            it[idTransaccion] = transactionId
            it[fecha] = today
            it[ingEg] = CajaIngresoEgreso.I
            it[monto] = totalBase
            it[comprobante] = "FACT"
            it[comprobanteNumero] = invoiceCode
            it[idFactura] = invoiceId
            it[idCliente] = request.factura.idCliente
            it[concepto] = conceptoCaja
            it[status] = CajaStatus.Pagada
            it[sucursalId] = request.factura.idSucursal
            it[usuarioCreacion] = request.factura.usuarioCreacion.take(20)
            it[fechaCreacion] = now
            it[idCompra] = ""
            it[idProveedor] = ""
            it[idOrdenPago] = ""
            it[serieSucursal] = request.factura.serieSucursal
            it[idCajaSecuencia] = request.factura.idCajaSecuencia
            it[idPedido] = ""
            it[idAbono] = ""
            it[idNotaCredito] = ""
        }

        SalesCajaNuevaReciboTable.insert {
            it[this.cajaReciboId] = cajaReciboId
            it[tipoRecibo] = "ICC"
            it[nroRecibo] = "FACT/$invoiceCode"
            it[fecha] = today
            it[monto] = totalBase
            it[observacion] = "Ingreso por Factura #$invoiceCode, Fecha: $fechaTexto, Cliente: $clienteNombre"
            it[codVendedor] = request.factura.codVendedor
            it[idCliente] = request.factura.idCliente
            it[idProveedor] = ""
            it[usuarioCreacion] = request.factura.usuarioCreacion.take(20)
            it[fechaCreacion] = now
            it[status] = "AC"
            it[contabilizado] = 0
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = today
            it[idFactura] = invoiceId
            it[idPedido] = ""
            it[idAbono] = ""
            it[idTransaccion] = ""
            it[nroReferencia] = ""
            it[tipoPagoSubtipo] = 0
        }

        request.pagos.forEach { pago ->
            val detalleId = UUID.randomUUID().toString()
            val montoPagoBase = monetaryContext.toBase(pago.monto)
            val montoRecibidoBase = monetaryContext.toBase(pago.montoRecibido)

            SalesCajaNuevaDetalleTable.insert {
                it[cajaDetalleId] = detalleId
                it[this.cajaId] = cajaId
                it[idFormaPago] = pago.idFormaPago
                it[idTransaccion] = transactionId
                it[SalesCajaNuevaDetalleTable.cajaReciboId] = cajaReciboId
                it[monto] = montoPagoBase
                it[montoOriginal] = BigDecimal.ZERO.setScale(2)
                it[concepto] = null
                it[usuarioCreacion] = request.factura.usuarioCreacion.take(20)
                it[fechaCreacion] = now
                it[retencionTipo] = ""
                it[retencionPorcentaje] = ""
                it[numero] = ""
                it[observacion] = ""
                it[retencionBaseCalculo] = ""
                it[serieSucursal] = ""
                it[cajaSecuencia] = ""
                it[numeroControl] = ""
                it[numeroComprobante] = ""
                it[retencionMonto] = ""
                it[retencionDetalleJson] = ""
                it[montoRecibido] = montoRecibidoBase
                it[montoMonedaPrincipal] = montoPagoBase
            }

            SalesCajaNuevaDetalleFormaPagoTable.insert {
                it[cajaDetalleFormaPagoId] = UUID.randomUUID().toString()
                it[this.cajaId] = cajaId
                it[cajaDetalleId] = detalleId
                it[tipoMovimiento] = pago.tipoMovimiento
                it[idFormaPago] = pago.idFormaPago
                it[comprobante] = "FACT"
                it[concepto] = "Ingreso por venta"
                it[monto] = montoPagoBase
                it[montoOriginal] = montoPagoBase
                it[tdcProveedor] = ""
                it[tdcNumero] = ""
                it[tdcTitular] = ""
                it[tdcVencimiento] = ""
                it[tdcCvv] = ""
                it[codigoVerificacion] = ""
                it[idAbonoDetalle] = ""
                it[efectivoCambio] = monetaryContext.toBase(pago.efectivoCambio)
            }
        }
    }

    private fun normalizeTipoMovimiento(value: String): String {
        val normalized = value.trim().uppercase()
        val allowed = setOf("DE", "TR", "CH", "MB", "OT", "TDC", "NEQ", "ABONO", "CASH")
        if (normalized in allowed) return normalized

        return when (normalized) {
            "EF", "EFE", "EFECTIVO" -> "CASH"
            "CK", "CK2", "CHEQUE" -> "CH"
            "DP", "DEP", "DEPOSITO" -> "DE"
            "TRANSFERENCIA", "TRANSF", "PM" -> "TR"
            "TARJETA", "POS", "PV", "DB", "DEBITO", "DEBIT", "CR", "CRED", "CREDITO", "PT", "PUNTO" -> "TDC"
            "NEQUI" -> "NEQ"
            "GC", "CXC", "OTRO", "OTROS" -> "OT"
            else -> "OT"
        }
    }

    private fun parseDateOrToday(value: String?, defaultDate: LocalDate): LocalDate {
        if (value.isNullOrBlank()) return defaultDate
        return runCatching { LocalDate.parse(value) }.getOrDefault(defaultDate)
    }

    private fun resolveCajaSecuenciaCodigo(idCajaSecuencia: String): String {
        return SalesCajaSecuenciaTable
            .select(SalesCajaSecuenciaTable.secuencia)
            .where { SalesCajaSecuenciaTable.id eq idCajaSecuencia }
            .limit(1)
            .firstOrNull()
            ?.get(SalesCajaSecuenciaTable.secuencia)
            ?.takeIf { it.isNotBlank() }
            ?.take(10)
            ?: "000001"
    }

    private fun Double.toMoney(): BigDecimal = toScaledBigDecimal(2)

    private fun Double.toScaledBigDecimal(scale: Int): BigDecimal =
        BigDecimal.valueOf(this).setScale(scale, RoundingMode.HALF_UP)
}
