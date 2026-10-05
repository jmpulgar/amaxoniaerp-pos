package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaTableFactory
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.kiosk.application.dispatch.KioskDispatchConfig
import com.amaxoniaerp.features.kiosk.application.dispatch.OrderDispatchPolicyFactory
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskParametrosTable
import com.amaxoniaerp.features.kiosk.domain.KioskInvoiceInfo
import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskReceipt
import com.amaxoniaerp.features.kiosk.domain.KioskReceiptLine
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.SalesFacturaTableFactory
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class PlaceKioskOrderService(
    private val kioskOrderRepository: KioskOrderRepository = KioskOrderRepository(),
    private val cajaSessionWorkflow: CajaSessionWorkflow? = null,
    private val processSaleUseCase: ProcessSaleUseCase? = null,
    private val dispatchPolicyFactory: OrderDispatchPolicyFactory = OrderDispatchPolicyFactory(),
) {
    private val logger = LoggerFactory.getLogger(PlaceKioskOrderService::class.java)

    suspend fun payOrder(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
        request: KioskPaymentRequest,
    ): KioskPayResponse {
        val order = kioskOrderRepository.findOrderRecordById(database, orderId)
            ?: throw IllegalArgumentException("Pedido $orderId no encontrado")

        if (order.idDispositivo != kioskContext.deviceId) {
            throw IllegalArgumentException("El pedido no pertenece a este dispositivo")
        }

        // Idempotencia: si ya fue procesado, retornar la respuesta existente
        if (order.estado == "FACTURADO" || order.estado == "PAGADO_SIN_FACTURA") {
            return buildExistingPayResponse(database, kioskContext, order)
        }

        if (order.estado != "COTIZADO") {
            throw IllegalStateException("El pedido se encuentra en estado ${order.estado}")
        }

        val now = LocalDateTime.now()
        if (now.isAfter(order.quoteExpiraEn)) {
            throw IllegalStateException("La cotización del pedido ha expirado")
        }

        val reqAmount = BigDecimal(request.amount).setScale(2, RoundingMode.HALF_UP)
        val orderTotal = order.total.setScale(2, RoundingMode.HALF_UP)
        if (reqAmount.compareTo(orderTotal) != 0) {
            throw IllegalArgumentException("El monto enviado ($reqAmount) no coincide con el total del pedido ($orderTotal)")
        }

        // 1. Asegurar secuencia de caja abierta para hoy (auto-close si había una de un día anterior)
        val activeSecuencia = ensureOpenSessionForToday(database, kioskContext)

        // 2. Procesar venta y facturación fiscal vía ProcessSaleUseCase
        val saleResult = executeSale(database, kioskContext, order, activeSecuencia, request)

        // 3. Determinar estado final y actualizar pedido
        val isPendingInvoice = saleResult.feError != null
        val finalEstado = if (isPendingInvoice) "PAGADO_SIN_FACTURA" else "FACTURADO"
        val statusResponse = if (isPendingInvoice) "PAID_PENDING_INVOICE" else "FACTURADO"

        kioskOrderRepository.updateOrderPaymentStatus(
            database = database,
            orderId = orderId,
            estado = finalEstado,
            idFactura = saleResult.idFactura,
            pagoReferencia = request.reference,
            pagoAutorizacion = request.authCode,
            pagoUltimos4 = request.last4,
            pagoMarca = request.brand,
            motivoRechazo = saleResult.feError,
        )

        // 4. Ejecutar política de despacho configurada (Retiro en mostrador, Impresora cocina o Mesas)
        runCatching {
            val (destino, cocinaIp) = dbQuery(database) {
                val row = KioskParametrosTable
                    .select(KioskParametrosTable.kioscoDestinoPedido, KioskParametrosTable.kioscoImpresoraCocinaIp)
                    .limit(1)
                    .singleOrNull()
                val dest = row?.get(KioskParametrosTable.kioscoDestinoPedido) ?: "RETIRO_MOSTRADOR"
                val ip = row?.get(KioskParametrosTable.kioscoImpresoraCocinaIp)
                Pair(dest, ip)
            }
            val policy = dispatchPolicyFactory.getPolicy(destino)
            val dispatchConfig = KioskDispatchConfig(kitchenPrinterIp = cocinaIp)
            val itemNames = dbQuery(database) {
                val itemsTable = ItemsTableFactory.getTableForCountry(kioskContext.countryCode)
                val itemIds = order.items.map { it.idItem }.distinct()
                if (itemIds.isNotEmpty()) {
                    itemsTable.select(itemsTable.idItem, itemsTable.descripcion1)
                        .where { itemsTable.idItem inList itemIds }
                        .associate { it[itemsTable.idItem] to (it[itemsTable.descripcion1] ?: "") }
                } else emptyMap()
            }
            policy.dispatch(order, dispatchConfig, itemNames)
        }.onFailure { err ->
            logger.warn("Dispatch execution failed for order {}: {}", order.codigoPedido, err.message)
        }

        // 5. Construir comprobante (recibo 80mm) y respuesta
        return buildPayResponse(
            database = database,
            kioskContext = kioskContext,
            order = order,
            saleResult = saleResult,
            request = request,
            status = statusResponse,
        )
    }

    private suspend fun ensureOpenSessionForToday(
        database: Database,
        kioskContext: KioskRequestContext,
    ): CajaSecuencia {
        if (cajaSessionWorkflow == null) {
            return dummyCajaSecuencia(kioskContext)
        }

        val openSecuencia = cajaSessionWorkflow.status(database, kioskContext.companyDb, kioskContext.idCaja)
        val now = BusinessClock.nowForCountry(kioskContext.countryCode)
        val today = now.toLocalDate()

        return if (openSecuencia != null && isOpenToday(openSecuencia.fechaApertura, today)) {
            openSecuencia
        } else {
            val sucursalSerie = dbQuery(database) {
                SucursalTable
                    .select(SucursalTable.serie)
                    .where { SucursalTable.idSucursal eq kioskContext.idSucursal }
                    .firstOrNull()?.get(SucursalTable.serie)?.trim()?.ifBlank { "01" } ?: "01"
            }
            val aperturaReq = AperturaRequest(
                idCaja = kioskContext.idCaja,
                montoApertura = 0.0,
                idVendedor = kioskContext.codVendedor,
                serieSucursal = sucursalSerie,
                idSucursal = kioskContext.idSucursal,
            )
            cajaSessionWorkflow.open(
                database = database,
                countryCode = kioskContext.countryCode,
                dbName = kioskContext.companyDb,
                request = aperturaReq,
                username = "KIOSK",
            ).getOrThrow()
        }
    }

    private fun isOpenToday(fechaAperturaStr: String, today: LocalDate): Boolean {
        if (fechaAperturaStr.isBlank()) return false
        val datePart = fechaAperturaStr.split(" ").firstOrNull() ?: return false
        return if (datePart.contains("-")) {
            datePart == today.toString()
        } else if (datePart.contains("/")) {
            val parts = datePart.split("/")
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                val year = parts[2].toIntOrNull()
                day == today.dayOfMonth && month == today.monthValue && year == today.year
            } else false
        } else false
    }

    private fun dummyCajaSecuencia(kioskContext: KioskRequestContext): CajaSecuencia =
        CajaSecuencia(
            idCajaSecuencia = "SEC-${kioskContext.idCaja}",
            idCaja = kioskContext.idCaja,
            fechaApertura = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            montoApertura = 0.0,
            estatus = 1,
            usuarioApertura = "KIOSK",
            serieSucursal = "01",
            idSucursal = kioskContext.idSucursal,
        )

    private suspend fun executeSale(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        activeSecuencia: CajaSecuencia,
        paymentRequest: KioskPaymentRequest,
    ): ProcessSaleResponse {
        if (processSaleUseCase == null) {
            val fakeInvoiceId = UUID.randomUUID().toString()
            return ProcessSaleResponse(
                success = true,
                idFactura = fakeInvoiceId,
                codFactura = "F-${order.codigoPedido}",
                codEstatus = 2,
            )
        }

        val saleRequest = dbQuery(database) {
            buildProcessSaleRequest(database, kioskContext, order, activeSecuencia, paymentRequest)
        }

        return processSaleUseCase.execute(database, kioskContext.countryCode, saleRequest)
    }

    private fun buildProcessSaleRequest(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        activeSecuencia: CajaSecuencia,
        paymentRequest: KioskPaymentRequest,
    ): ProcessSaleRequest {
        val itemsTable = ItemsTableFactory.getTableForCountry(kioskContext.countryCode)
        val itemIds = order.items.map { it.idItem }.distinct()
        val itemsMap = if (itemIds.isNotEmpty()) {
            itemsTable
                .selectAll()
                .where { itemsTable.idItem inList itemIds }
                .associateBy { it[itemsTable.idItem] }
        } else {
            emptyMap()
        }

        val paramsTable = ParametrosGeneralesTableFactory.forCountry(kioskContext.countryCode)
        val paramsRow = paramsTable.select(paramsTable.porcentajeImpuestoPrincipal).limit(1).singleOrNull()
        val defaultTaxRate = paramsRow?.get(paramsTable.porcentajeImpuestoPrincipal) ?: BigDecimal("7.00")

        val cajaTable = CajaTableFactory.forCountry(kioskContext.countryCode)
        val codigoCaja = cajaTable
            .select(cajaTable.codCaja, cajaTable.serieCaja)
            .where { cajaTable.idCaja eq kioskContext.idCaja }
            .firstOrNull()?.let { it[cajaTable.codCaja] ?: it[cajaTable.serieCaja] } ?: "CAJA1"

        val clientRow = ClientsTable
            .selectAll()
            .where { (ClientsTable.idCliente eq order.idCliente) or (ClientsTable.codCliente eq order.idCliente) }
            .limit(1)
            .singleOrNull()

        val customerName = clientRow?.get(ClientsTable.nombre)?.trim()?.takeIf { it.isNotBlank() } ?: "CONSUMIDOR FINAL"
        val customerRif = clientRow?.get(ClientsTable.rif)?.trim()?.takeIf { it.isNotBlank() } ?: "CF"
        val customerAddress = clientRow?.get(ClientsTable.direccion)?.trim() ?: ""
        val customerPhone = clientRow?.get(ClientsTable.telefonos)?.trim() ?: ""

        val formaPagoId = runCatching {
            CajaFormaPagoTable
                .select(CajaFormaPagoTable.idFormaPago)
                .where { (CajaFormaPagoTable.siglas eq "TDC") or (CajaFormaPagoTable.siglas eq "TARJETA") }
                .map { it[CajaFormaPagoTable.idFormaPago] }
                .firstOrNull()
        }.getOrNull() ?: 2

        var totalSubtotal = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO

        val saleItems = order.items.map { itemRecord ->
            val itemRow = itemsMap[itemRecord.idItem]
            val itemDesc = itemRow?.get(itemsTable.descripcion1)?.trim() ?: "Item ${itemRecord.idItem}"
            val modNames = itemRecord.modifiers.map { it.nombre }
            val fullDesc = if (modNames.isNotEmpty()) {
                "$itemDesc (${modNames.joinToString(", ")})"
            } else {
                itemDesc
            }

            val taxRate = when {
                itemRow == null -> defaultTaxRate
                itemRow[itemsTable.montoExento] -> BigDecimal.ZERO
                itemRow[itemsTable.iva] > BigDecimal.ZERO -> itemRow[itemsTable.iva]
                else -> defaultTaxRate
            }

            val lineSubtotal = (itemRecord.precioUnitario * itemRecord.cantidad).setScale(2, RoundingMode.HALF_UP)
            val lineTax = (lineSubtotal * taxRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            val lineTotal = lineSubtotal + lineTax

            totalSubtotal += lineSubtotal
            totalTax += lineTax

            SaleItemInput(
                idItem = itemRecord.idItem,
                itemAlmacen = kioskContext.idAlmacen,
                itemDescripcion = fullDesc,
                itemCantidad = itemRecord.cantidad.toDouble(),
                itemPrecioSinIva = itemRecord.precioUnitario.setScale(2, RoundingMode.HALF_UP).toDouble(),
                itemPIva = taxRate.toDouble(),
                itemTotalSinIva = lineSubtotal.toDouble(),
                itemTotalConIva = lineTotal.toDouble(),
                itemCantidadTotal = itemRecord.cantidad.toDouble(),
            )
        }

        val overallTotalDouble = order.total.setScale(2, RoundingMode.HALF_UP).toDouble()
        val overallSubtotalDouble = totalSubtotal.setScale(2, RoundingMode.HALF_UP).toDouble()
        val overallTaxDouble = totalTax.setScale(2, RoundingMode.HALF_UP).toDouble()

        val invoiceInput = SaleInvoiceInput(
            idCliente = order.idCliente,
            codCliente = clientRow?.get(ClientsTable.codCliente) ?: "CF",
            codVendedor = kioskContext.codVendedor,
            idShop = kioskContext.idSucursal,
            idSucursal = kioskContext.idSucursal,
            idCaja = kioskContext.idCaja,
            codigoCaja = codigoCaja,
            idCajaSecuencia = activeSecuencia.idCajaSecuencia,
            serieSucursal = activeSecuencia.serieSucursal,
            formaPago = "TDC",
            codEstatus = 2,
            subtotal = overallSubtotalDouble,
            descuentosItemFactura = 0.0,
            ivaTotalFactura = overallTaxDouble,
            totalTotalFactura = overallTotalDouble,
            montoItemsFactura = overallSubtotalDouble,
            totalizarSubTotal = overallSubtotalDouble,
            totalizarDescuentoParcial = 0.0,
            totalizarTotalOperacion = overallSubtotalDouble,
            totalizarPDescuentoGlobal = 0.0,
            totalizarDescuentoGlobal = 0.0,
            totalizarBaseImponible = overallSubtotalDouble,
            totalizarMontoIva = overallTaxDouble,
            totalizarTotalGeneral = overallTotalDouble,
            usuarioCreacion = "KIOSK",
            facturarA = customerName,
            facturarARuc = customerRif,
            facturarADireccion = customerAddress,
            facturarATelefono = customerPhone,
        )

        return ProcessSaleRequest(
            procesar = 1,
            factura = invoiceInput,
            items = saleItems,
            pagoResumen = SalePaymentSummaryInput(
                totalizarMontoCancelar = overallTotalDouble,
                totalizarMontoEfectivo = 0.0,
                totalizarCambio = 0.0,
                totalizarSaldoPendiente = 0.0,
                montosPorTipo = mapOf("TDC" to overallTotalDouble),
            ),
            pagos = listOf(
                SalePaymentInput(
                    idFormaPago = formaPagoId,
                    tipoMovimiento = "ING",
                    monto = overallTotalDouble,
                    montoRecibido = overallTotalDouble,
                    tdcProveedor = paymentRequest.brand,
                    tdcNumero = paymentRequest.last4,
                    codigoVerificacion = paymentRequest.authCode,
                ),
            ),
            moneda = SaleCurrencyInput(
                multiMoneda = if (kioskContext.countryCode == "VE") "SI" else "NO",
                monedaBase = 1,
                abrMonedaBase = "USD",
                monedaSecundaria = if (kioskContext.countryCode == "VE") 2 else 1,
                abrMonedaSecundaria = if (kioskContext.countryCode == "VE") "VES" else "USD",
            ),
        )
    }

    private suspend fun buildPayResponse(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        saleResult: ProcessSaleResponse,
        request: KioskPaymentRequest,
        status: String,
    ): KioskPayResponse = dbQuery(database) {
        val sucursalRow = SucursalTable
            .selectAll()
            .where { SucursalTable.idSucursal eq kioskContext.idSucursal }
            .limit(1)
            .singleOrNull()

        val companyName = sucursalRow?.get(SucursalTable.sucursal)?.trim()?.takeIf { it.isNotBlank() } ?: "Amaxonia"
        val companyAddress = sucursalRow?.get(SucursalTable.descripcion)?.trim() ?: ""

        val paramsTable = ParametrosGeneralesTableFactory.forCountry(kioskContext.countryCode)
        val paramsRow = paramsTable.select(paramsTable.rif).limit(1).singleOrNull()
        val companyRif = paramsRow?.get(paramsTable.rif)

        val clientRow = ClientsTable
            .selectAll()
            .where { (ClientsTable.idCliente eq order.idCliente) or (ClientsTable.codCliente eq order.idCliente) }
            .limit(1)
            .singleOrNull()

        val customerName = clientRow?.get(ClientsTable.nombre)?.trim()?.takeIf { it.isNotBlank() } ?: "CONSUMIDOR FINAL"
        val customerRif = clientRow?.get(ClientsTable.rif)?.trim()?.takeIf { it.isNotBlank() } ?: "CF"

        val itemsTable = ItemsTableFactory.getTableForCountry(kioskContext.countryCode)
        val itemIds = order.items.map { it.idItem }.distinct()
        val itemsMap = if (itemIds.isNotEmpty()) {
            itemsTable
                .selectAll()
                .where { itemsTable.idItem inList itemIds }
                .associateBy { it[itemsTable.idItem] }
        } else {
            emptyMap()
        }

        var totalSubtotal = BigDecimal.ZERO
        val receiptLines = order.items.map { item ->
            val itemDesc = itemsMap[item.idItem]?.get(itemsTable.descripcion1)?.trim() ?: "Item ${item.idItem}"
            val lineTotal = (item.precioUnitario * item.cantidad).setScale(2, RoundingMode.HALF_UP)
            totalSubtotal += lineTotal
            KioskReceiptLine(
                qty = item.cantidad.toInt(),
                description = itemDesc,
                price = item.precioUnitario.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                total = lineTotal.toPlainString(),
                modifiers = item.modifiers.map { it.nombre },
            )
        }

        val total = order.total.setScale(2, RoundingMode.HALF_UP)
        val tax = (total - totalSubtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val destinoPedido = KioskParametrosTable
            .select(KioskParametrosTable.kioscoDestinoPedido)
            .limit(1)
            .singleOrNull()
            ?.get(KioskParametrosTable.kioscoDestinoPedido) ?: "RETIRO_MOSTRADOR"

        val invoiceInfo = KioskInvoiceInfo(
            codFactura = saleResult.codFactura,
            cufe = saleResult.cufe,
            qr = saleResult.qr,
            fechaRecepcionDGI = saleResult.fechaRecepcionDGI,
            numeroDocumentoFiscal = saleResult.numeroDocumentoFiscal,
            numeroControl = saleResult.numeroControlThka,
        )

        val receipt = KioskReceipt(
            companyName = companyName,
            ruc = companyRif,
            dv = clientRow?.get(ClientsTable.dv),
            address = companyAddress,
            orderNumber = order.codigoPedido,
            diningMode = order.modalidad,
            tableTent = order.portamesa,
            customerName = customerName,
            customerId = customerRif,
            date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lines = receiptLines,
            subtotal = totalSubtotal.toPlainString(),
            tax = tax.toPlainString(),
            total = total.toPlainString(),
            paymentBrand = request.brand,
            paymentLast4 = request.last4,
            paymentAuthCode = request.authCode,
            paymentReference = request.reference,
            invoiceNumber = saleResult.codFactura,
            cufe = saleResult.cufe,
            qr = saleResult.qr,
        )

        KioskPayResponse(
            orderNumber = order.codigoPedido,
            invoice = invoiceInfo,
            dispatch = destinoPedido,
            receipt = receipt,
            status = status,
        )
    }

    private suspend fun buildExistingPayResponse(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
    ): KioskPayResponse = dbQuery(database) {
        val sucursalRow = SucursalTable
            .selectAll()
            .where { SucursalTable.idSucursal eq kioskContext.idSucursal }
            .limit(1)
            .singleOrNull()

        val companyName = sucursalRow?.get(SucursalTable.sucursal)?.trim()?.takeIf { it.isNotBlank() } ?: "Amaxonia"
        val companyAddress = sucursalRow?.get(SucursalTable.descripcion)?.trim() ?: ""

        val paramsTable = ParametrosGeneralesTableFactory.forCountry(kioskContext.countryCode)
        val paramsRow = paramsTable.select(paramsTable.rif).limit(1).singleOrNull()
        val companyRif = paramsRow?.get(paramsTable.rif)

        val clientRow = ClientsTable
            .selectAll()
            .where { (ClientsTable.idCliente eq order.idCliente) or (ClientsTable.codCliente eq order.idCliente) }
            .limit(1)
            .singleOrNull()

        val customerName = clientRow?.get(ClientsTable.nombre)?.trim()?.takeIf { it.isNotBlank() } ?: "CONSUMIDOR FINAL"
        val customerRif = clientRow?.get(ClientsTable.rif)?.trim()?.takeIf { it.isNotBlank() } ?: "CF"

        val itemsTable = ItemsTableFactory.getTableForCountry(kioskContext.countryCode)
        val itemIds = order.items.map { it.idItem }.distinct()
        val itemsMap = if (itemIds.isNotEmpty()) {
            itemsTable
                .selectAll()
                .where { itemsTable.idItem inList itemIds }
                .associateBy { it[itemsTable.idItem] }
        } else {
            emptyMap()
        }

        var totalSubtotal = BigDecimal.ZERO
        val receiptLines = order.items.map { item ->
            val itemDesc = itemsMap[item.idItem]?.get(itemsTable.descripcion1)?.trim() ?: "Item ${item.idItem}"
            val lineTotal = (item.precioUnitario * item.cantidad).setScale(2, RoundingMode.HALF_UP)
            totalSubtotal += lineTotal
            KioskReceiptLine(
                qty = item.cantidad.toInt(),
                description = itemDesc,
                price = item.precioUnitario.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                total = lineTotal.toPlainString(),
                modifiers = item.modifiers.map { it.nombre },
            )
        }

        val total = order.total.setScale(2, RoundingMode.HALF_UP)
        val tax = (total - totalSubtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        val destinoPedido = KioskParametrosTable
            .select(KioskParametrosTable.kioscoDestinoPedido)
            .limit(1)
            .singleOrNull()
            ?.get(KioskParametrosTable.kioscoDestinoPedido) ?: "RETIRO_MOSTRADOR"

        val facturaTable = SalesFacturaTableFactory.forCountry(kioskContext.countryCode)
        val facturaRow = if (order.idFactura != null) {
            facturaTable
                .selectAll()
                .where { facturaTable.idFactura eq order.idFactura }
                .singleOrNull()
        } else null

        val codFactura = facturaRow?.get(facturaTable.codFactura) ?: order.idFactura ?: ""
        val statusResponse = if (order.estado == "PAGADO_SIN_FACTURA") "PAID_PENDING_INVOICE" else "FACTURADO"

        val invoiceInfo = KioskInvoiceInfo(
            codFactura = codFactura,
            cufe = null,
            qr = null,
        )

        val receipt = KioskReceipt(
            companyName = companyName,
            ruc = companyRif,
            dv = clientRow?.get(ClientsTable.dv),
            address = companyAddress,
            orderNumber = order.codigoPedido,
            diningMode = order.modalidad,
            tableTent = order.portamesa,
            customerName = customerName,
            customerId = customerRif,
            date = order.actualizadoEn.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            lines = receiptLines,
            subtotal = totalSubtotal.toPlainString(),
            tax = tax.toPlainString(),
            total = total.toPlainString(),
            paymentBrand = order.pagoMarca ?: "VISA",
            paymentLast4 = order.pagoUltimos4 ?: "0000",
            paymentAuthCode = order.pagoAutorizacion ?: "",
            paymentReference = order.pagoReferencia ?: "",
            invoiceNumber = codFactura,
            cufe = null,
            qr = null,
        )

        KioskPayResponse(
            orderNumber = order.codigoPedido,
            invoice = invoiceInfo,
            dispatch = destinoPedido,
            receipt = receipt,
            status = statusResponse,
        )
    }
}
