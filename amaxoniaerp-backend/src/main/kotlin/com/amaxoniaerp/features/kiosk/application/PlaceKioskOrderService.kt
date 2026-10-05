package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.kiosk.application.dispatch.KioskDispatchConfig
import com.amaxoniaerp.features.kiosk.application.dispatch.OrderDispatchPolicyFactory
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.domain.KioskInvoiceInfo
import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskReceipt
import com.amaxoniaerp.features.kiosk.domain.KioskReceiptLine
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import com.amaxoniaerp.features.kiosk.domain.KioskSalePrerequisites
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.amaxoniaerp.features.sales.domain.SaleCurrencyInput
import com.amaxoniaerp.features.sales.domain.SaleInvoiceInput
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import com.amaxoniaerp.features.sales.domain.SalePaymentInput
import com.amaxoniaerp.features.sales.domain.SalePaymentSummaryInput
import org.jetbrains.exposed.sql.Database
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

        val prereqs = kioskOrderRepository.loadSalePrerequisites(database, kioskContext, order)

        // 1. Asegurar secuencia de caja abierta para hoy (auto-close si había una de un día anterior)
        val activeSecuencia = ensureOpenSessionForToday(database, kioskContext, prereqs.branchSerie)

        // 2. Procesar venta y facturación fiscal vía ProcessSaleUseCase
        val saleResult = executeSale(database, kioskContext, order, activeSecuencia, request, prereqs)

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
            val policy = dispatchPolicyFactory.getPolicy(prereqs.dispatchDestination)
            val dispatchConfig = KioskDispatchConfig(kitchenPrinterIp = prereqs.kitchenPrinterIp)
            val itemNames = prereqs.itemDetails.mapValues { it.value.description }
            policy.dispatch(order, dispatchConfig, itemNames)
        }.onFailure { err ->
            logger.warn("Dispatch execution failed for order {}: {}", order.codigoPedido, err.message)
        }

        // 5. Construir comprobante (recibo 80mm) y respuesta
        return buildPayResponse(
            order = order,
            saleResult = saleResult,
            request = request,
            prereqs = prereqs,
            status = statusResponse,
        )
    }

    private suspend fun ensureOpenSessionForToday(
        database: Database,
        kioskContext: KioskRequestContext,
        branchSerie: String,
    ): CajaSecuencia {
        if (cajaSessionWorkflow == null) {
            return dummyCajaSecuencia(kioskContext, branchSerie)
        }

        val openSecuencia = cajaSessionWorkflow.status(database, kioskContext.companyDb, kioskContext.idCaja)
        val now = BusinessClock.nowForCountry(kioskContext.countryCode)
        val today = now.toLocalDate()

        return if (openSecuencia != null && isOpenToday(openSecuencia.fechaApertura, today)) {
            openSecuencia
        } else {
            val aperturaReq = AperturaRequest(
                idCaja = kioskContext.idCaja,
                montoApertura = 0.0,
                idVendedor = kioskContext.codVendedor,
                serieSucursal = branchSerie,
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

    private fun dummyCajaSecuencia(kioskContext: KioskRequestContext, branchSerie: String): CajaSecuencia =
        CajaSecuencia(
            idCajaSecuencia = "SEC-${kioskContext.idCaja}",
            idCaja = kioskContext.idCaja,
            fechaApertura = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            montoApertura = 0.0,
            estatus = 1,
            usuarioApertura = "KIOSK",
            serieSucursal = branchSerie,
            idSucursal = kioskContext.idSucursal,
        )

    private suspend fun executeSale(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        activeSecuencia: CajaSecuencia,
        paymentRequest: KioskPaymentRequest,
        prereqs: KioskSalePrerequisites,
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

        val saleRequest = buildProcessSaleRequest(kioskContext, order, activeSecuencia, paymentRequest, prereqs)
        return processSaleUseCase.execute(database, kioskContext.countryCode, saleRequest)
    }

    private fun buildProcessSaleRequest(
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        activeSecuencia: CajaSecuencia,
        paymentRequest: KioskPaymentRequest,
        prereqs: KioskSalePrerequisites,
    ): ProcessSaleRequest {
        var totalSubtotal = BigDecimal.ZERO
        var totalTax = BigDecimal.ZERO

        val saleItems = order.items.map { itemRecord ->
            val itemTaxInfo = prereqs.itemDetails[itemRecord.idItem]
            val itemDesc = itemTaxInfo?.description ?: "Item ${itemRecord.idItem}"
            val modNames = itemRecord.modifiers.map { it.nombre }
            val fullDesc = if (modNames.isNotEmpty()) {
                "$itemDesc (${modNames.joinToString(", ")})"
            } else {
                itemDesc
            }

            val taxRate = when {
                itemTaxInfo == null -> prereqs.defaultTaxRate
                itemTaxInfo.isExempt -> BigDecimal.ZERO
                itemTaxInfo.ivaRate > BigDecimal.ZERO -> itemTaxInfo.ivaRate
                else -> prereqs.defaultTaxRate
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
            codCliente = prereqs.customerCod,
            codVendedor = kioskContext.codVendedor,
            idShop = kioskContext.idSucursal,
            idSucursal = kioskContext.idSucursal,
            idCaja = kioskContext.idCaja,
            codigoCaja = prereqs.cajaCode,
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
            facturarA = prereqs.customerName,
            facturarARuc = prereqs.customerRif,
            facturarADireccion = prereqs.customerAddress,
            facturarATelefono = prereqs.customerPhone,
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
                    idFormaPago = prereqs.paymentMethodId,
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

    private fun buildPayResponse(
        order: KioskOrderRecord,
        saleResult: ProcessSaleResponse,
        request: KioskPaymentRequest,
        prereqs: KioskSalePrerequisites,
        status: String,
    ): KioskPayResponse {
        var totalSubtotal = BigDecimal.ZERO
        val receiptLines = order.items.map { item ->
            val itemDesc = prereqs.itemDetails[item.idItem]?.description ?: "Item ${item.idItem}"
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

        val invoiceInfo = KioskInvoiceInfo(
            codFactura = saleResult.codFactura,
            cufe = saleResult.cufe,
            qr = saleResult.qr,
            fechaRecepcionDGI = saleResult.fechaRecepcionDGI,
            numeroDocumentoFiscal = saleResult.numeroDocumentoFiscal,
            numeroControl = saleResult.numeroControlThka,
        )

        val receipt = KioskReceipt(
            companyName = prereqs.branchName,
            ruc = prereqs.companyRif,
            dv = prereqs.customerDv,
            address = prereqs.branchAddress,
            orderNumber = order.codigoPedido,
            diningMode = order.modalidad,
            tableTent = order.portamesa,
            customerName = prereqs.customerName,
            customerId = prereqs.customerRif,
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

        return KioskPayResponse(
            orderNumber = order.codigoPedido,
            invoice = invoiceInfo,
            dispatch = prereqs.dispatchDestination,
            receipt = receipt,
            status = status,
        )
    }

    private suspend fun buildExistingPayResponse(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
    ): KioskPayResponse {
        val prereqs = kioskOrderRepository.loadSalePrerequisites(database, kioskContext, order)
        val codFactura = kioskOrderRepository.getInvoiceCode(database, kioskContext.countryCode, order.idFactura) ?: order.idFactura ?: ""

        var totalSubtotal = BigDecimal.ZERO
        val receiptLines = order.items.map { item ->
            val itemDesc = prereqs.itemDetails[item.idItem]?.description ?: "Item ${item.idItem}"
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
        val statusResponse = if (order.estado == "PAGADO_SIN_FACTURA") "PAID_PENDING_INVOICE" else "FACTURADO"

        val invoiceInfo = KioskInvoiceInfo(
            codFactura = codFactura,
            cufe = null,
            qr = null,
        )

        val receipt = KioskReceipt(
            companyName = prereqs.branchName,
            ruc = prereqs.companyRif,
            dv = prereqs.customerDv,
            address = prereqs.branchAddress,
            orderNumber = order.codigoPedido,
            diningMode = order.modalidad,
            tableTent = order.portamesa,
            customerName = prereqs.customerName,
            customerId = prereqs.customerRif,
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

        return KioskPayResponse(
            orderNumber = order.codigoPedido,
            invoice = invoiceInfo,
            dispatch = prereqs.dispatchDestination,
            receipt = receipt,
            status = statusResponse,
        )
    }
}
