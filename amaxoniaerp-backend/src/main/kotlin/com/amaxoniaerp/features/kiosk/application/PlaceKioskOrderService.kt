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
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentMethod
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
    private val yappyPaymentVerifier: KioskYappyPaymentVerifier? = null,
) {
    private val logger = LoggerFactory.getLogger(PlaceKioskOrderService::class.java)

    suspend fun payOrder(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
        request: KioskPaymentRequest,
    ): KioskPayResponse {
        val order =
            kioskOrderRepository.findOrderRecordById(database, orderId)
                ?: throw IllegalArgumentException("Pedido $orderId no encontrado")

        require(order.idDispositivo == kioskContext.deviceId) { "El pedido no pertenece a este dispositivo" }

        // Idempotencia: si ya fue procesado, retornar la respuesta existente
        if (order.estado == "FACTURADO" || order.estado == "PAGADO_SIN_FACTURA") {
            return buildExistingPayResponse(database, kioskContext, order)
        }

        val method = normalizePaymentMethod(request.method)
        validatePayable(database, kioskContext, order, request, method)

        val prereqs = kioskOrderRepository.loadSalePrerequisites(database, kioskContext, order)
        val payment =
            resolveSalePayment(method, request, prereqs)
                ?: return completeYappyWithoutPaymentMethod(database, order, request, prereqs)

        // 1. Asegurar secuencia de caja abierta para hoy (auto-close si había una de un día anterior)
        val activeSecuencia = ensureOpenSessionForToday(database, kioskContext, prereqs.branchSerie)

        // 2. Procesar venta y facturación fiscal vía ProcessSaleUseCase
        val saleResult = executeSale(database, kioskContext, order, activeSecuencia, payment, prereqs)

        // 3. Determinar estado final y actualizar pedido
        val isPendingInvoice = saleResult.feError != null
        val finalEstado = if (isPendingInvoice) "PAGADO_SIN_FACTURA" else "FACTURADO"
        val statusResponse = if (isPendingInvoice) "PAID_PENDING_INVOICE" else "FACTURADO"

        persistPayment(database, order.id, finalEstado, saleResult.idFactura, payment, saleResult.feError)

        // 4. Ejecutar política de despacho configurada (Retiro en mostrador, Impresora cocina o Mesas)
        dispatchOrder(order, prereqs)

        // 5. Construir comprobante (recibo 80mm) y respuesta
        return buildPayResponse(
            order = order,
            saleResult = saleResult,
            receiptPayment = payment.receipt,
            prereqs = prereqs,
            status = statusResponse,
        )
    }

    private fun normalizePaymentMethod(raw: String): String {
        val method = raw.trim().uppercase().ifBlank { KioskPaymentMethod.CARD }
        require(method == KioskPaymentMethod.CARD || method == KioskPaymentMethod.YAPPY) {
            "Método de pago no soportado: $raw"
        }
        return method
    }

    private suspend fun validatePayable(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        request: KioskPaymentRequest,
        method: String,
    ) {
        check(order.estado == "COTIZADO") { "El pedido se encuentra en estado ${order.estado}" }

        // Yappy: el cliente ya pagó el QR generado con la cotización vigente; una expiración
        // posterior de la cotización no puede impedir confirmar un pago ya cobrado.
        if (method == KioskPaymentMethod.CARD) {
            check(!LocalDateTime.now().isAfter(order.quoteExpiraEn)) { "La cotización del pedido ha expirado" }
            // pago_marca = 'YAPPY' identifica los pedidos Yappy: una tarjeta no puede usar esa marca.
            require(!request.brand.trim().equals(KioskPaymentMethod.YAPPY, ignoreCase = true)) {
                "Marca de tarjeta inválida: ${request.brand}"
            }
        }

        val reqAmount = BigDecimal(request.amount).setScale(2, RoundingMode.HALF_UP)
        val orderTotal = order.total.setScale(2, RoundingMode.HALF_UP)
        require(reqAmount.compareTo(orderTotal) == 0) {
            "El monto enviado ($reqAmount) no coincide con el total del pedido ($orderTotal)"
        }

        if (method == KioskPaymentMethod.YAPPY) {
            val confirmed =
                yappyPaymentVerifier?.isPaymentCompleted(
                    database = database,
                    kioskContext = kioskContext,
                    order = order,
                    transactionId = request.transactionId.trim(),
                ) == true
            check(confirmed) { "Pago Yappy no confirmado" }
        }
    }

    /** null cuando el pago es Yappy y la empresa no tiene forma de pago YAPPY en caja_forma_pago. */
    private fun resolveSalePayment(
        method: String,
        request: KioskPaymentRequest,
        prereqs: KioskSalePrerequisites,
    ): KioskSalePayment? =
        if (method == KioskPaymentMethod.YAPPY) {
            prereqs.yappyPaymentMethodId?.let { KioskSalePayment.yappy(request.transactionId.trim(), it) }
        } else {
            KioskSalePayment.card(request, prereqs.paymentMethodId)
        }

    /**
     * El cliente ya pagó con Yappy pero no hay forma de pago YAPPY para registrar la venta:
     * no se factura ni se recobra (D11); el pedido queda PAGADO_SIN_FACTURA para conciliación.
     */
    private suspend fun completeYappyWithoutPaymentMethod(
        database: Database,
        order: KioskOrderRecord,
        request: KioskPaymentRequest,
        prereqs: KioskSalePrerequisites,
    ): KioskPayResponse {
        val transactionId = request.transactionId.trim()
        val payment = KioskSalePayment.yappy(transactionId, idFormaPago = 0)
        val motivo = "Pago Yappy $transactionId confirmado sin forma de pago YAPPY en caja_forma_pago; facturar manualmente"
        logger.warn("Pedido {} pagado con Yappy sin forma de pago YAPPY configurada", order.codigoPedido)

        persistPayment(database, order.id, "PAGADO_SIN_FACTURA", null, payment, motivo.take(MOTIVO_MAX_LENGTH))
        dispatchOrder(order, prereqs)

        return buildPayResponse(
            order = order,
            saleResult = null,
            receiptPayment = payment.receipt,
            prereqs = prereqs,
            status = "PAID_PENDING_INVOICE",
        )
    }

    private suspend fun persistPayment(
        database: Database,
        orderId: String,
        estado: String,
        idFactura: String?,
        payment: KioskSalePayment,
        motivo: String?,
    ) {
        kioskOrderRepository.updateOrderPaymentStatus(
            database = database,
            orderId = orderId,
            estado = estado,
            idFactura = idFactura,
            pagoReferencia = payment.storedReference,
            pagoAutorizacion = payment.storedAuthorization,
            pagoUltimos4 = payment.storedLast4,
            pagoMarca = payment.receipt.brand,
            motivoRechazo = motivo,
        )
    }

    private suspend fun dispatchOrder(
        order: KioskOrderRecord,
        prereqs: KioskSalePrerequisites,
    ) {
        runCatching {
            val policy = dispatchPolicyFactory.getPolicy(prereqs.dispatchDestination)
            val dispatchConfig = KioskDispatchConfig(kitchenPrinterIp = prereqs.kitchenPrinterIp)
            val itemNames = prereqs.itemDetails.mapValues { it.value.description }
            policy.dispatch(order, dispatchConfig, itemNames)
        }.onFailure { err ->
            logger.warn("Dispatch execution failed for order {}: {}", order.codigoPedido, err.message)
        }
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
        payment: KioskSalePayment,
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

        val saleRequest = buildProcessSaleRequest(kioskContext, order, activeSecuencia, payment, prereqs)
        return processSaleUseCase.execute(database, kioskContext.countryCode, saleRequest)
    }

    private fun buildProcessSaleRequest(
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        activeSecuencia: CajaSecuencia,
        payment: KioskSalePayment,
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
            formaPago = payment.formaPago,
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
                montosPorTipo = mapOf(payment.montosPorTipoKey to overallTotalDouble),
            ),
            pagos = listOf(
                SalePaymentInput(
                    idFormaPago = payment.idFormaPago,
                    tipoMovimiento = payment.tipoMovimiento,
                    monto = overallTotalDouble,
                    montoRecibido = overallTotalDouble,
                    tdcProveedor = payment.tdcProveedor,
                    tdcNumero = payment.tdcNumero,
                    codigoVerificacion = payment.codigoVerificacion,
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
        saleResult: ProcessSaleResponse?,
        receiptPayment: KioskReceiptPayment,
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

        val invoiceInfo =
            saleResult?.let {
                KioskInvoiceInfo(
                    codFactura = it.codFactura,
                    cufe = it.cufe,
                    qr = it.qr,
                    fechaRecepcionDGI = it.fechaRecepcionDGI,
                    numeroDocumentoFiscal = it.numeroDocumentoFiscal,
                    numeroControl = it.numeroControlThka,
                )
            }

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
            paymentBrand = receiptPayment.brand,
            paymentLast4 = receiptPayment.last4,
            paymentAuthCode = receiptPayment.authCode,
            paymentReference = receiptPayment.reference,
            invoiceNumber = saleResult?.codFactura,
            cufe = saleResult?.cufe,
            qr = saleResult?.qr,
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
        val existingPayment = KioskReceiptPayment.fromStoredOrder(order)

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
            paymentBrand = existingPayment.brand,
            paymentLast4 = existingPayment.last4,
            paymentAuthCode = existingPayment.authCode,
            paymentReference = existingPayment.reference,
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

private const val MOTIVO_MAX_LENGTH = 255
private const val REFERENCE_MAX_LENGTH = 64
private const val AUTHORIZATION_MAX_LENGTH = 32

/** Datos de pago que se imprimen en el recibo del kiosco. */
internal data class KioskReceiptPayment(
    val brand: String,
    val last4: String,
    val authCode: String,
    val reference: String,
) {
    companion object {
        fun fromStoredOrder(order: KioskOrderRecord): KioskReceiptPayment =
            if (order.pagoMetodo == KioskPaymentMethod.YAPPY) {
                // Yappy no tiene columna propia: la transacción vive en pago_referencia.
                val transactionId = order.yappyTransactionId.orEmpty()
                KioskReceiptPayment(
                    brand = KioskPaymentMethod.YAPPY,
                    last4 = "",
                    authCode = transactionId,
                    reference = transactionId,
                )
            } else {
                KioskReceiptPayment(
                    brand = order.pagoMarca ?: "VISA",
                    last4 = order.pagoUltimos4 ?: "0000",
                    authCode = order.pagoAutorizacion ?: "",
                    reference = order.pagoReferencia ?: "",
                )
            }
    }
}

/**
 * Cómo se registra el cobro del kiosco en la venta (`ProcessSaleRequest`) y en `kiosco_pedido`.
 *
 * Tarjeta conserva el registro previo (TDC / "ING"). Yappy replica el POS: forma de pago con
 * siglas YAPPY, `tipoMovimiento` = siglas y sin datos de tarjeta, por lo que la venta lo
 * clasifica como "otros", igual que el POS y el PHP (`55 => 16 // YAPPY -> Otros`).
 */
internal data class KioskSalePayment(
    val method: String,
    val idFormaPago: Int,
    val formaPago: String,
    val tipoMovimiento: String,
    val montosPorTipoKey: String,
    val tdcProveedor: String?,
    val tdcNumero: String?,
    val codigoVerificacion: String?,
    val storedReference: String?,
    val storedAuthorization: String?,
    val storedLast4: String?,
    val receipt: KioskReceiptPayment,
) {
    companion object {
        fun card(
            request: KioskPaymentRequest,
            idFormaPago: Int,
        ): KioskSalePayment =
            KioskSalePayment(
                method = KioskPaymentMethod.CARD,
                idFormaPago = idFormaPago,
                formaPago = "TDC",
                tipoMovimiento = "ING",
                montosPorTipoKey = "TDC",
                tdcProveedor = request.brand,
                tdcNumero = request.last4,
                codigoVerificacion = request.authCode,
                storedReference = request.reference,
                storedAuthorization = request.authCode,
                storedLast4 = request.last4,
                receipt =
                    KioskReceiptPayment(
                        brand = request.brand,
                        last4 = request.last4,
                        authCode = request.authCode,
                        reference = request.reference,
                    ),
            )

        fun yappy(
            transactionId: String,
            idFormaPago: Int,
        ): KioskSalePayment =
            KioskSalePayment(
                method = KioskPaymentMethod.YAPPY,
                idFormaPago = idFormaPago,
                formaPago = KioskPaymentMethod.YAPPY,
                tipoMovimiento = KioskPaymentMethod.YAPPY,
                montosPorTipoKey = KioskPaymentMethod.YAPPY,
                tdcProveedor = null,
                tdcNumero = null,
                codigoVerificacion = null,
                storedReference = transactionId.take(REFERENCE_MAX_LENGTH),
                storedAuthorization = transactionId.take(AUTHORIZATION_MAX_LENGTH),
                storedLast4 = null,
                receipt =
                    KioskReceiptPayment(
                        brand = KioskPaymentMethod.YAPPY,
                        last4 = "",
                        authCode = transactionId,
                        reference = transactionId,
                    ),
            )
    }
}
