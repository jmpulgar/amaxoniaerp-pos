package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
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

class ProcessSaleTransactionalRepository(
    private val cuentaMesaRepository: CuentaMesaRepository? = null,
) {

    fun process(countryCode: String, request: ProcessSaleRequest): ProcessSaleResponse {
        val preparedRequest = prepareRequestWithWarehouses(countryCode, request)
        val monetaryContext = resolveMonetaryContext(countryCode, preparedRequest)

        validateDuplicateInvoice(monetaryContext.countryCode, preparedRequest)
        validateClientSucursalIfRequired(monetaryContext.countryCode, preparedRequest)
        if (monetaryContext.shouldValidateStock()) {
            validateStock(preparedRequest)
        }

        val invoiceId = preparedRequest.idFactura?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val cuentaValidada =
            preparedRequest.cuentaMesa?.let { context ->
                val repository =
                    cuentaMesaRepository
                        ?: throw InvalidSaleRequestException("La integración de cuenta de mesa no está configurada")
                repository.validarVentaEnTransaccion(context, preparedRequest, invoiceId)
            }
        val invoiceCode = resolveInvoiceCode(countryCode, preparedRequest)
        val now = BusinessClock.nowForCountry(countryCode)
        val today = now.toLocalDate()

        insertFactura(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
        val detalleIds = insertFacturaDetalle(preparedRequest, invoiceId, now, monetaryContext)
        processLotTracking(preparedRequest, detalleIds)
        insertFacturaImpuestos(preparedRequest, invoiceId, now, monetaryContext)
        insertFacturaDetalleFormaPago(preparedRequest, invoiceId, now, monetaryContext)

        val shouldAffectInventory = (preparedRequest.procesar == 1 || preparedRequest.factura.codEstatus == 2) && !preparedRequest.esCobroCreditoPrevio
        if (shouldAffectInventory) {
            updateInventoryAndKardex(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
            insertCajaEntries(preparedRequest, invoiceId, invoiceCode, now, today, monetaryContext)
        }

        if (monetaryContext.multiMoneda == "SI" && monetaryContext.idTasa > 0) {
            // Solo VE tiene el campo `facturado` en tasas_cambio
            val tasasTableVE = TasasCambioTableFactory.forCountry(countryCode)
            if (tasasTableVE is TasasCambioTableVE) {
                tasasTableVE.update({ tasasTableVE.id eq monetaryContext.idTasa.toLong() }) {
                    it[tasasTableVE.facturado] = "S"
                }
            }
        }

        val sesionMesaCerrada =
            cuentaValidada?.let {
                checkNotNull(cuentaMesaRepository).confirmarVentaEnTransaccion(it, invoiceId, invoiceCode)
            } ?: false

        return ProcessSaleResponse(
            success = true,
            idFactura = invoiceId,
            codFactura = invoiceCode,
            codEstatus = preparedRequest.factura.codEstatus,
            sesionMesaCerrada = sesionMesaCerrada,
        )
    }

    private fun prepareRequestWithWarehouses(countryCode: String, request: ProcessSaleRequest): ProcessSaleRequest {
        val context = resolveWarehouseContext(countryCode, request.factura.idCaja)

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

    private fun resolveWarehouseContext(countryCode: String, cajaId: String): WarehouseContext {
        val isVE = countryCode.equals("VE", ignoreCase = true)
        val columns = if (isVE) {
            listOf(SalesCajaTable.idSucursal, SalesCajaTable.codAlmacen)
        } else {
            listOf(SalesCajaTable.idSucursal)
        }
        val caja = SalesCajaTable
            .select(columns)
            .where { SalesCajaTable.id eq cajaId }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$cajaId")

        val cajaWarehouseId = if (isVE) {
            caja.getOrNull(SalesCajaTable.codAlmacen)?.takeIf { it > 0 }
        } else null
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

        val pgTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
        val globalWarehouseId = pgTable
            .select(pgTable.codAlmacen)
            .orderBy(pgTable.codEmpresa)
            .limit(1)
            .firstOrNull()
            ?.get(pgTable.codAlmacen)
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

    private fun resolveMonetaryContext(countryCode: String, request: ProcessSaleRequest): MonetaryContext {
        val pgTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
        val params = pgTable
            .selectAll()
            .orderBy(pgTable.codEmpresa)
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró parametros_generales")

        // multiMoneda y monedaSecundaria solo existen en VE
        val paramsMulti = if (pgTable is ParametrosGeneralesTableVE) {
            params[pgTable.multiMoneda].equals("Si", ignoreCase = true)
        } else {
            false
        }
        val multiMoneda = if (paramsMulti) "SI" else "NO"

        val monedaBase = params[pgTable.monedaBase] ?: 1
        val abrMonedaBase = params[pgTable.abrMonedaBase].take(10)

        val monedaSecundaria = if (pgTable is ParametrosGeneralesTableVE) {
            params[pgTable.monedaSecundaria]
        } else {
            monedaBase
        }
        val abrMonedaSecundaria = if (pgTable is ParametrosGeneralesTableVE) {
            params[pgTable.abrMonedaSecundaria].take(10)
        } else {
            abrMonedaBase
        }

        val providedMoneda = request.moneda
        val tasaFromRequest = providedMoneda?.tasa?.takeIf { it > 0.0 }
        val idTasaFromRequest = providedMoneda?.idTasa?.takeIf { it > 0 }

        val tasasTable = TasasCambioTableFactory.forCountry(countryCode)
        val tasaRow = if (paramsMulti && (tasaFromRequest == null || idTasaFromRequest == null) && tasasTable is TasasCambioTableVE) {
            tasasTable
                .select(tasasTable.id, tasasTable.tasaInversa)
                .where {
                    (tasasTable.divisa eq monedaSecundaria) and
                        (tasasTable.monedabase eq monedaBase)
                }
                .orderBy(tasasTable.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        } else {
            null
        }

        val tasa = if (paramsMulti) {
            tasaFromRequest
                ?: tasaRow?.get(tasasTable.tasaInversa)?.toDouble()
                ?: throw InvalidSaleRequestException("No se encontró tasa de cambio vigente")
        } else {
            1.0
        }

        val idTasa = if (paramsMulti) {
            idTasaFromRequest
                ?: tasaRow?.get(tasasTable.id)?.toInt()
                ?: throw InvalidSaleRequestException("No se encontró id de tasa vigente")
        } else {
            0
        }

        return MonetaryContext(
            countryCode = countryCode,
            multiMoneda = multiMoneda,
            tasa = BigDecimal.valueOf(tasa).setScale(8, RoundingMode.HALF_UP),
            idTasa = idTasa,
            monedaBase = monedaBase,
            abrMonedaBase = abrMonedaBase,
            monedaSecundaria = monedaSecundaria,
            abrMonedaSecundaria = abrMonedaSecundaria,
            totalRef = providedMoneda?.totalRef ?: request.factura.totalTotalFactura,
            validarStock = params[pgTable.validarStock],
            defaultTaxRate = params[pgTable.porcentajeImpuestoPrincipal].toDouble(),
            defaultFormaPagoId = params[pgTable.defaultIdFormaPagoFactura],
            diasVencimiento = params[pgTable.diasVencimiento],
        )
    }

    private data class MonetaryContext(
        val countryCode: String,
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

        fun shouldValidateStock(): Boolean = validarStock.trim().equals("SI", ignoreCase = true)
    }

    private fun validateDuplicateInvoice(countryCode: String, request: ProcessSaleRequest) {
        val idFactura = request.idFactura?.takeIf { it.isNotBlank() }
        if (idFactura == null) return

        val t = SalesFacturaTableFactory.forCountry(countryCode)
        val existing = t
            .select(t.idFactura, t.codFactura, t.codEstatus)
            .where { t.idFactura eq idFactura }
            .limit(1)
            .firstOrNull()
            ?: return

        val status = existing[t.codEstatus] ?: 0
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

    private fun validateClientSucursalIfRequired(countryCode: String, request: ProcessSaleRequest) {
        if (!countryCode.equals("PA", ignoreCase = true)) return
        val clientCode = request.factura.codCliente.take(9)
        if (clientCode.isBlank()) return

        val sucursales = ClientSucursalTable
            .select(ClientSucursalTable.sucursalId)
            .where { ClientSucursalTable.clienteCodigo eq clientCode }
            .map { it[ClientSucursalTable.sucursalId] }
        if (sucursales.isEmpty()) return

        val selectedSucursalId = request.factura.clienteSucursalId
            ?: throw InvalidSaleRequestException("Debes seleccionar la sucursal del cliente")
        if (selectedSucursalId !in sucursales) {
            throw InvalidSaleRequestException("La sucursal seleccionada no pertenece al cliente")
        }
    }

    private fun resolveInvoiceCode(countryCode: String, request: ProcessSaleRequest): String {
        val idCaja = request.factura.idCaja.trim()
        if (idCaja.isBlank()) {
            throw InvalidSaleRequestException("idCaja es obligatorio para generar cod_factura desde caja")
        }

        var invoiceCode = getNextCodePreviewFromCaja(idCaja, request.factura.codigoCaja)
        var jumpedDuplicate = false

        while (invoiceCodeExists(countryCode, invoiceCode)) {
            jumpedDuplicate = true
            invoiceCode = consumeAndGetNextCodeFromCaja(idCaja, request.factura.codigoCaja)
        }

        if (!jumpedDuplicate) {
            consumeCorrelativoCaja(idCaja)
        }

        return invoiceCode
    }

    private fun invoiceCodeExists(countryCode: String, code: String): Boolean {
        val t = SalesFacturaTableFactory.forCountry(countryCode)
        return t
            .select(t.idFactura)
            .where { t.codFactura eq code }
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
        return formatInvoiceCode(codigoCaja, correlativo)
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
                return formatInvoiceCode(codigoCaja, next)
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

    private fun formatInvoiceCode(codigoCaja: String, correlativo: Int): String {
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
        val descuentosItemsBase = monetaryContext.toBase(
            f.descuentosItemFactura.takeIf { it > 0.0 }
                ?: request.items.sumOf { it.itemMontoDescuento }
        )
        val montoItemsBase = monetaryContext.toBase(
            f.montoItemsFactura.takeIf { it > 0.0 }
                ?: (f.subtotal - f.descuentosItemFactura).coerceAtLeast(0.0)
        )
        val ivaTotalBase = monetaryContext.toBase(f.ivaTotalFactura)
        val totalGeneralBase = monetaryContext.toBase(f.totalTotalFactura)
        val fechaVencimientoFactura = today.plusDays(monetaryContext.diasVencimiento.toLong())
        val totalBultosQty = request.items.sumOf { it.itemCantidadTotal }
        val serieSucursalValue = f.serieSucursal.take(10)
        val cajaSecuenciaValue = resolveCajaSecuenciaCodigo(f.idCajaSecuencia)

        val facturaTable = SalesFacturaTableFactory.forCountry(monetaryContext.countryCode)
        facturaTable.insert {
            it[facturaTable.idFactura] = invoiceId
            it[facturaTable.codFactura] = invoiceCode
            it[facturaTable.codFacturaFiscal] = f.codFacturaFiscal
            it[facturaTable.idCliente] = f.idCliente
            it[facturaTable.codVendedor] = f.codVendedor
            it[facturaTable.fechaFactura] = parseDateOrToday(f.fechaFactura, today)
            it[facturaTable.subtotal] = subtotalBase
            it[facturaTable.descuentosItemFactura] = descuentosItemsBase
            it[facturaTable.montoItemsFactura] = montoItemsBase
            it[facturaTable.ivaTotalFactura] = ivaTotalBase
            it[facturaTable.totalTotalFactura] = totalGeneralBase
            it[facturaTable.cantidadItems] = request.items.size
            it[facturaTable.totalizarSubTotal] = monetaryContext.toBase(f.totalizarSubTotal)
            it[facturaTable.totalizarDescuentoParcial] = monetaryContext.toBase(f.totalizarDescuentoParcial)
            it[facturaTable.totalizarTotalOperacion] = monetaryContext.toBase(f.totalizarTotalOperacion)
            it[facturaTable.totalizarPDescuentoGlobal] = monetaryContext.toBase(f.totalizarPDescuentoGlobal)
            it[facturaTable.totalizarDescuentoGlobal] = monetaryContext.toBase(f.totalizarDescuentoGlobal)
            it[facturaTable.totalizarBaseImponible] = monetaryContext.toBase(f.totalizarBaseImponible)
            it[facturaTable.totalizarMontoIva] = monetaryContext.toBase(f.totalizarMontoIva)
            it[facturaTable.totalizarTotalGeneral] = monetaryContext.toBase(f.totalizarTotalGeneral)
            it[facturaTable.totalizarTotalRetencion] = BigDecimal.ZERO.setScale(2)
            it[facturaTable.formaPago] = "contado"
            it[facturaTable.codEstatus] = f.codEstatus
            it[facturaTable.totalBultos] = totalBultosQty.toMoney()
            it[facturaTable.fechaCreacion] = now
            it[facturaTable.usuarioCreacion] = f.usuarioCreacion
            it[facturaTable.tipoFactura] = "factura_pos"
            it[facturaTable.modeloFactura] = "pos"
            it[facturaTable.terminoPagoId] = monetaryContext.defaultFormaPagoId.takeIf { id -> id > 0 } ?: 3
            it[facturaTable.facturarA] = f.facturarA
            it[facturaTable.facturarARuc] = f.facturarARuc
            it[facturaTable.facturarADireccion] = f.facturarADireccion
            it[facturaTable.facturarATelefono] = f.facturarATelefono
            it[facturaTable.validarStock] = monetaryContext.validarStock
            it[facturaTable.idShop] = f.idShop
            it[facturaTable.servicioPeriodo] = ""
            it[facturaTable.servicioOrden] = ""
            it[facturaTable.observacion] = ""
            it[facturaTable.fechaVencimiento] = fechaVencimientoFactura
            it[facturaTable.servicioAnio] = today.year
            it[facturaTable.servicioMes] = today.monthValue.toString().padStart(2, '0')
            it[facturaTable.idCajaSecuencia] = f.idCajaSecuencia
            it[facturaTable.numcomContabilizado] = 0
            it[facturaTable.fechaContabilizado] = today
            it[facturaTable.serieSucursal] = serieSucursalValue
            it[facturaTable.cajaSecuencia] = cajaSecuenciaValue
            it[facturaTable.idSucursal] = f.idSucursal
            it[facturaTable.idCaja] = f.idCaja
            it[facturaTable.codigoCaja] = f.codigoCaja
            it[facturaTable.codCliente] = f.codCliente
            // Campos exclusivos de Venezuela
            if (facturaTable is SalesFacturaTableVE) {
                it[facturaTable.nroz] = f.nroz
                it[facturaTable.impresoraSerial] = f.impresoraSerial
                it[facturaTable.multiMoneda] = monetaryContext.multiMoneda
                it[facturaTable.tasa] = monetaryContext.tasa.toFloat()
                it[facturaTable.idTasa] = monetaryContext.idTasa
                it[facturaTable.monedaBase] = monetaryContext.monedaBase
                it[facturaTable.abrMonedaBase] = monetaryContext.abrMonedaBase
                it[facturaTable.monedaSecundaria] = monetaryContext.monedaSecundaria
                it[facturaTable.abrMonedaSecundaria] = monetaryContext.abrMonedaSecundaria
                it[facturaTable.totalRef] = monetaryContext.totalRef.toFloat()
            } else if (facturaTable is SalesFacturaTablePA) {
                it[facturaTable.nroz] = ""
                it[facturaTable.impresoraSerial] = ""
                it[facturaTable.multiMoneda] = "0"
                it[facturaTable.tasa] = 0f
                it[facturaTable.idTasa] = 0
                it[facturaTable.monedaBase] = monetaryContext.monedaBase
                it[facturaTable.abrMonedaBase] = monetaryContext.abrMonedaBase
                it[facturaTable.monedaSecundaria] = 0
                it[facturaTable.abrMonedaSecundaria] = ""
                it[facturaTable.totalRef] = 0f
                it[facturaTable.clienteSucursalId] = f.clienteSucursalId
            }
        }
    }

    /** Retorna lista de (detalleId, itemIndex) para vincular con lotes */
    private fun insertFacturaDetalle(
        request: ProcessSaleRequest,
        invoiceId: String,
        now: LocalDateTime,
        monetaryContext: MonetaryContext,
    ): List<String> {
        val vendedorPorDefecto = request.factura.codVendedor
        val usuario = request.factura.usuarioCreacion.take(32)
        val detalleIds = mutableListOf<String>()

        request.items.forEach { item ->
            val vendedorLinea = item.codVendedor?.takeIf { it > 0 } ?: vendedorPorDefecto
            val itemTaxRate = item.itemPIva
            val itemTotalSinIvaBase = monetaryContext.toBase(item.itemTotalSinIva)
            val itemTotalConIvaBase = monetaryContext.toBase(item.itemTotalConIva)
            val itemPriceSinIvaBase = monetaryContext.toBase(item.itemPrecioSinIva)

            val detalleId = UUID.randomUUID().toString()
            detalleIds.add(detalleId)

            SalesFacturaDetalleTable.insert {
                it[idDetalleFactura] = detalleId
                it[idFactura] = invoiceId
                it[idItem] = item.idItem
                it[itemAlmacen] = item.itemAlmacen
                it[itemDescripcion] = item.itemDescripcion
                it[itemCantidad] = item.itemCantidad.toScaledBigDecimal(3)
                it[itemPrecioSinIva] = itemPriceSinIvaBase
                it[itemDescuento] = item.itemDescuento.toMoney()
                it[itemMontoDescuento] = monetaryContext.toBase(item.itemMontoDescuento)
                it[itemPiva] = itemTaxRate.toMoney()
                it[itemTotalSinIva] = itemTotalSinIvaBase
                it[itemTotalConIva] = itemTotalConIvaBase
                it[cantidadBulto] = item.cantidadBulto.coerceAtLeast(1)
                it[gananciaItemIndividual] = itemTotalSinIvaBase
                it[porcentajeGanancia] = BigDecimal.valueOf(100.0).setScale(2)
                it[poseeSerial] = "NO"
                it[serialesSeleccionados] = ""
                it[usuarioCreacion] = usuario
                it[fechaCreacion] = now
                it[itemListaPrecio] = "BASE"
                it[itemUnidadEmpaque] = item.itemUnidadEmpaque.take(15).ifBlank { "UNIDAD" }
                it[itemCantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(0)
                it[promocionId] = item.promocionId.take(36)
                it[promocionTipo] = item.promocionTipo.take(20)
                it[promocionCodigo] = item.promocionCodigo.take(15)
                it[promocionNombre] = item.promocionNombre.take(200)
                it[promocionGrupo] = item.promocionGrupo.take(36)
                it[promocionDetalleId] = item.promocionDetalleId.take(36)
                it[promocionCantidad] = item.promocionCantidad.toScaledBigDecimal(3)
                it[grupo] = 1
                it[descuentoAutorizacion] = ""
                it[codVendedor] = vendedorLinea
                it[itemCodigo] = item.itemCodigo
                it[itemReferencia] = item.itemReferencia
                it[idSegmento] = item.idSegmento
                it[idFamilia] = item.idFamilia
            }
        }
        return detalleIds
    }

    /**
     * Inserta trazabilidad por lote y descuenta disponibilidad en item_lote.
     * Solo aplica a items con poseeConfiguracionLote == "si" y codigosLote no vacio.
     */
    private fun processLotTracking(
        request: ProcessSaleRequest,
        detalleIds: List<String>,
    ) {
        request.items.forEachIndexed { index, item ->
            if (item.poseeConfiguracionLote.equals("si", ignoreCase = true) && item.codigosLote.isNotEmpty()) {
                val detalleId = detalleIds.getOrNull(index) ?: return@forEachIndexed

                item.codigosLote.forEach { lote ->
                    // Insertar registro de trazabilidad
                    FacturaDetalleProductoLoteTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[idDetalleFactura] = detalleId
                        it[idItem] = item.idItem
                        it[idLoteItem] = lote.idLoteItem
                        it[cantidad] = lote.cantidad
                    }

                    // Descontar disponibilidad y registrar venta en item_lote
                    val loteCantidad = BigDecimal.valueOf(lote.cantidad.toLong())
                    ItemLoteTable.update({ ItemLoteTable.idLoteItem eq lote.idLoteItem }) {
                        it.update(disponibilidad, disponibilidad.minus(loteCantidad))
                        it.update(procesamiento, procesamiento.plus(loteCantidad))
                        it.update(venta, venta.plus(loteCantidad))
                    }
                }
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

        val fpgTable = SalesFacturaDetalleFormaPagoTableFactory.forCountry(monetaryContext.countryCode)
        fpgTable.insert {
            it[fpgTable.codFacturaDetalleFormaPago] = UUID.randomUUID().toString()
            it[fpgTable.idFactura] = invoiceId
            it[fpgTable.totalizarMontoCancelar] = monetaryContext.toBase(resumen.totalizarMontoCancelar)
            it[fpgTable.totalizarSaldoPendiente] = monetaryContext.toBase(resumen.totalizarSaldoPendiente)
            it[fpgTable.totalizarCambio] = monetaryContext.toBase(resumen.totalizarCambio)
            it[fpgTable.totalizarMontoEfectivo] = monetaryContext.toBase(montoEfectivo)
            it[fpgTable.optCheque] = if (montoCheque > 0.0) 1 else 0
            it[fpgTable.totalizarMontoCheque] = monetaryContext.toBase(montoCheque)
            it[fpgTable.totalizarNroCheque] = BigDecimal.ZERO.setScale(2)
            it[fpgTable.totalizarNombreBanco] = 0
            it[fpgTable.optTarjeta] = if (montoTarjeta > 0.0) 1 else 0
            it[fpgTable.totalizarMontoTarjeta] = monetaryContext.toBase(montoTarjeta)
            it[fpgTable.totalizarNroTarjeta] = BigDecimal.ZERO.setScale(2)
            it[fpgTable.totalizarTipoTarjeta] = 0
            it[fpgTable.optDeposito] = if (montoDeposito > 0.0) 1 else 0
            it[fpgTable.totalizarMontoDeposito] = monetaryContext.toBase(montoDeposito)
            it[fpgTable.totalizarNroDeposito] = BigDecimal.ZERO.setScale(2)
            it[fpgTable.totalizarBancoDeposito] = 0
            it[fpgTable.fechaVencimiento] = null
            it[fpgTable.observacion] = ""
            it[fpgTable.personaContacto] = ""
            it[fpgTable.telefono] = ""
            it[fpgTable.optOtroDocumento] = if (montoOtros > 0.0) 1 else 0
            it[fpgTable.totalizarTipoOtroDocumento] = 0
            it[fpgTable.totalizarMontoOtroDocumento] = monetaryContext.toBase(montoOtros)
            it[fpgTable.totalizarNroOtroDocumento] = 0
            it[fpgTable.totalizarBancoOtroDocumento] = 0
            it[fpgTable.fechaCreacion] = now
            it[fpgTable.usuarioCreacion] = request.factura.usuarioCreacion.take(60)
            it[fpgTable.totalizarMontoCredito] = monetaryContext.toBase(montoCredito)
            it[fpgTable.totalizarMontoDebito] = monetaryContext.toBase(montoDebito)
            it[fpgTable.totalizarMontoTransferencia] = monetaryContext.toBase(montoTransferencia)
            it[fpgTable.totalizarMontoCertificado] = monetaryContext.toBase(montoCertificado)
            it[fpgTable.totalizarMontoCxc] = monetaryContext.toBase(montoCxc)
            it[fpgTable.totalizarMontoOtros] = monetaryContext.toBase(montoOtros)
            if (fpgTable is SalesFacturaDetalleFormaPagoTableVE) {
                it[fpgTable.totalizarMontoDivisa] = BigDecimal.ZERO.setScale(2)
            } else if (fpgTable is SalesFacturaDetalleFormaPagoTablePA) {
                it[fpgTable.codigoRetencion] = ""
                it[fpgTable.totalizarMontoRetencion] = BigDecimal.ZERO.setScale(2)
            }
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
        val shouldValidateStock = monetaryContext.shouldValidateStock()

        physicalItems.forEach { item ->
            val requested = item.itemCantidadTotal.toScaledBigDecimal(2)
            val updated = if (shouldValidateStock) {
                SalesStockTable.update({
                    (SalesStockTable.idItem eq item.idItem) and
                        (SalesStockTable.codAlmacen eq item.itemAlmacen) and
                        (SalesStockTable.cantidad greaterEq requested.toFloat())
                }) {
                    it.update(cantidad, cantidad.minus(requested.toFloat()))
                }
            } else {
                SalesStockTable.update({
                    (SalesStockTable.idItem eq item.idItem) and
                        (SalesStockTable.codAlmacen eq item.itemAlmacen)
                }) {
                    it.update(cantidad, cantidad.minus(requested.toFloat()))
                }
            }

            when {
                updated == 1 -> Unit
                shouldValidateStock -> {
                    throw InsufficientStockException(
                        "No se pudo descontar stock para item=${item.idItem}, almacen=${item.itemAlmacen}"
                    )
                }
                else -> {
                    SalesStockTable.insert {
                        it[idItem] = item.idItem
                        it[codAlmacen] = item.itemAlmacen
                        it[cantidad] = requested.negate().toFloat()
                        if (monetaryContext.countryCode.uppercase() == "PA") {
                            it[cantidadMuestra] = BigDecimal.ZERO.setScale(4)
                            it[minimo] = 0L
                            it[maximo] = 0L
                        }
                    }
                }
            }
        }

        val kardexTable = SalesKardexTableFactory.forCountry(monetaryContext.countryCode)
        val kardexDetalleTable = SalesKardexDetalleTableFactory.forCountry(monetaryContext.countryCode)

        kardexTable.insert {
            it[kardexTable.idTransaccion] = kardexId
            it[kardexTable.tipoMovimientoAlmacen] = 2
            it[kardexTable.autorizadoPor] = request.factura.usuarioCreacion
            it[kardexTable.observacion] = "Salida por Ventas"
            it[kardexTable.fecha] = today
            it[kardexTable.usuarioCreacion] = request.factura.usuarioCreacion
            it[kardexTable.fechaCreacion] = now
            it[kardexTable.estado] = "Procesado"
            it[kardexTable.idDocumento] = invoiceId
            it[kardexTable.codProveedor] = 0
            it[kardexTable.comprobante] = "FACT"
            it[kardexTable.anio] = Year.from(today).value % 100
            it[kardexTable.tipoCosto] = "PROM"
            it[kardexTable.estatus] = 1
            it[kardexTable.entregadoACodigo] = "POS"
            it[kardexTable.entregadoANombre] = "VENTA"
            it[kardexTable.codDocumento] = documentCode
            it[kardexTable.subtipoMovimientoAlmacen] = 0
            it[kardexTable.contabilizado] = 0
            it[kardexTable.fechaContabilizacion] = today
            it[kardexTable.usuarioContabilizacion] = ""
            it[kardexTable.idAlmacenSalida] = physicalItems.first().itemAlmacen
            it[kardexTable.idSucursal] = request.factura.idSucursal
            it[kardexTable.validadoFecha] = today
            it[kardexTable.validadoUsuario] = request.factura.usuarioCreacion.take(20)
            it[kardexTable.validadoObservacion] = "Salida por Ventas"
            if (kardexTable is SalesKardexTablePA) {
                it[kardexTable.controlaStock] = 0
            }
        }

        physicalItems.forEach { item ->
            kardexDetalleTable.insert {
                it[kardexDetalleTable.idTransaccionDetalle] = UUID.randomUUID().toString()
                it[kardexDetalleTable.idTransaccion] = kardexId
                it[kardexDetalleTable.idAlmacenEntrada] = 0
                it[kardexDetalleTable.idAlmacenSalida] = item.itemAlmacen
                it[kardexDetalleTable.idItem] = item.idItem
                it[kardexDetalleTable.cantidad] = item.itemCantidadTotal.toFloat()
                it[kardexDetalleTable.cantidadDistribuida] = 0
                it[kardexDetalleTable.precio] = monetaryContext.toBase(item.itemPrecioSinIva)
                it[kardexDetalleTable.cantidadMuestra] = 0
                it[kardexDetalleTable.unidadBulto] = "UNIDAD"
                it[kardexDetalleTable.cantidadBulto] = BigDecimal.ONE.setScale(2)
                it[kardexDetalleTable.unidadEmpaque] = "UNIDAD"
                it[kardexDetalleTable.cantidadTotal] = item.itemCantidadTotal.toScaledBigDecimal(2)
                it[kardexDetalleTable.costo] = BigDecimal.ZERO.setScale(2)
                if (kardexDetalleTable is SalesKardexDetalleTablePA) {
                    it[kardexDetalleTable.idCentroCosto] = 0
                    it[kardexDetalleTable.idLoteItem] = 0
                }
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

        val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(monetaryContext.countryCode)
        val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(monetaryContext.countryCode)

        cajaNuevaTable.insert {
            it[cajaNuevaTable.cajaId] = cajaId
            it[cajaNuevaTable.idTransaccion] = transactionId
            it[cajaNuevaTable.fecha] = today
            it[cajaNuevaTable.ingEg] = CajaIngresoEgreso.I
            it[cajaNuevaTable.monto] = totalBase
            it[cajaNuevaTable.comprobante] = "FACT"
            it[cajaNuevaTable.comprobanteNumero] = invoiceCode
            it[cajaNuevaTable.idFactura] = invoiceId
            it[cajaNuevaTable.idCliente] = request.factura.idCliente
            it[cajaNuevaTable.concepto] = conceptoCaja
            it[cajaNuevaTable.status] = CajaStatus.Pagada
            it[cajaNuevaTable.sucursalId] = request.factura.idSucursal
            it[cajaNuevaTable.usuarioCreacion] = request.factura.usuarioCreacion.take(20)
            it[cajaNuevaTable.fechaCreacion] = now
            it[cajaNuevaTable.idCompra] = ""
            it[cajaNuevaTable.idProveedor] = ""
            it[cajaNuevaTable.idOrdenPago] = ""
            it[cajaNuevaTable.serieSucursal] = request.factura.serieSucursal
            it[cajaNuevaTable.idCajaSecuencia] = request.factura.idCajaSecuencia
            it[cajaNuevaTable.idPedido] = ""
            it[cajaNuevaTable.idAbono] = ""
            it[cajaNuevaTable.idNotaCredito] = ""
        }

        val cajaReciboTable = SalesCajaNuevaReciboTableFactory.forCountry(monetaryContext.countryCode)
        cajaReciboTable.insert {
            it[cajaReciboTable.cajaReciboId] = cajaReciboId
            it[cajaReciboTable.tipoRecibo] = "ICC"
            it[cajaReciboTable.nroRecibo] = "FACT/$invoiceCode"
            it[cajaReciboTable.fecha] = today
            it[cajaReciboTable.monto] = totalBase
            it[cajaReciboTable.observacion] = "Ingreso por Factura #$invoiceCode, Fecha: $fechaTexto, Cliente: $clienteNombre"
            it[cajaReciboTable.codVendedor] = request.factura.codVendedor
            it[cajaReciboTable.idCliente] = request.factura.idCliente
            it[cajaReciboTable.idProveedor] = ""
            it[cajaReciboTable.usuarioCreacion] = request.factura.usuarioCreacion.take(20)
            it[cajaReciboTable.fechaCreacion] = now
            it[cajaReciboTable.status] = "AC"
            it[cajaReciboTable.contabilizado] = 0
            it[cajaReciboTable.numcomContabilizado] = 0
            it[cajaReciboTable.fechaContabilizado] = today
            it[cajaReciboTable.idFactura] = invoiceId
            it[cajaReciboTable.idPedido] = ""
            it[cajaReciboTable.idAbono] = ""
            it[cajaReciboTable.idTransaccion] = ""
            it[cajaReciboTable.nroReferencia] = ""
            it[cajaReciboTable.tipoPagoSubtipo] = 0
            if (cajaReciboTable is SalesCajaNuevaReciboTableVE) {
                it[cajaReciboTable.idConsignacion] = ""
            }
        }

        request.pagos.forEach { pago ->
            val detalleId = UUID.randomUUID().toString()
            val montoPagoBase = monetaryContext.toBase(pago.monto)
            val montoRecibidoBase = monetaryContext.toBase(pago.montoRecibido)

            cajaNuevaDetalleTable.insert {
                it[cajaNuevaDetalleTable.cajaDetalleId] = detalleId
                it[cajaNuevaDetalleTable.cajaId] = cajaId
                it[cajaNuevaDetalleTable.idFormaPago] = pago.idFormaPago
                it[cajaNuevaDetalleTable.idTransaccion] = transactionId
                it[cajaNuevaDetalleTable.cajaReciboId] = cajaReciboId
                it[cajaNuevaDetalleTable.monto] = montoPagoBase
                it[cajaNuevaDetalleTable.montoOriginal] = BigDecimal.ZERO.setScale(2)
                it[cajaNuevaDetalleTable.concepto] = null
                it[cajaNuevaDetalleTable.usuarioCreacion] = request.factura.usuarioCreacion.take(20)
                it[cajaNuevaDetalleTable.fechaCreacion] = now
                it[cajaNuevaDetalleTable.retencionTipo] = ""
                it[cajaNuevaDetalleTable.retencionPorcentaje] = ""
                it[cajaNuevaDetalleTable.numero] = ""
                it[cajaNuevaDetalleTable.observacion] = ""
                it[cajaNuevaDetalleTable.retencionBaseCalculo] = ""
                it[cajaNuevaDetalleTable.serieSucursal] = ""
                it[cajaNuevaDetalleTable.cajaSecuencia] = ""
                it[cajaNuevaDetalleTable.numeroControl] = ""
                it[cajaNuevaDetalleTable.numeroComprobante] = ""
                it[cajaNuevaDetalleTable.retencionMonto] = ""
                it[cajaNuevaDetalleTable.retencionDetalleJson] = ""
                // Campos exclusivos de Venezuela
                if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
                    it[cajaNuevaDetalleTable.montoRecibido] = montoRecibidoBase
                    it[cajaNuevaDetalleTable.montoMonedaPrincipal] = montoPagoBase
                }
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
