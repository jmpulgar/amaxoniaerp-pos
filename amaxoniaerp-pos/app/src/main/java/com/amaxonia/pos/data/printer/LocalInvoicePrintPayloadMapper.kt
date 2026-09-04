package com.amaxonia.pos.data.printer

import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.sales.ClientePrintDto
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.PagoPrintDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProductoPrintDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PERCENT_DIVISOR = 100.0

object LocalInvoicePrintPayloadMapper {
    fun fromRequest(
        request: ProcessSaleRequestDto,
        company: CompanyDetailsSnapshot? = null,
        caja: Caja? = null,
    ): FacturaPrintPayloadDto {
        val invoice = request.factura
        val items = mapRequestItems(request)
        val pagos = mapRequestPagos(request)
        val dateIso =
            invoice.fechaFactura?.takeIf { it.isNotBlank() }
                ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val isMultiCurrency = request.moneda?.multiMoneda.equals("SI", ignoreCase = true)

        return FacturaPrintPayloadDto(
            facturaId = request.idFactura.orEmpty(),
            numeroFactura = request.codFactura ?: invoice.serieSucursal.ifBlank { invoice.idCajaSecuencia },
            fecha = dateIso,
            empresa = buildEmpresaDto(company, caja, invoice.idSucursal, invoice.codigoCaja),
            cliente = buildClienteDto(invoice.facturarA, invoice.facturarARuc, invoice.facturarADireccion),
            vendedor = caja?.defaultSellerName ?: invoice.codVendedor.toString(),
            productos = items,
            subtotal = formatMoney(invoice.subtotal),
            montoExento = "0.00",
            descuento = formatMoney(invoice.descuentosItemFactura),
            totalImpuesto = formatMoney(invoice.ivaTotalFactura),
            total = formatMoney(invoice.totalTotalFactura),
            pagos = pagos,
            cambio = if (request.pagoResumen.totalizarCambio > 0.0) formatMoney(request.pagoResumen.totalizarCambio) else null,
            qrUrl = null,
            cufe = null,
            fechaRecepcionDgi = null,
            proveedorAutorizado = null,
            numeroDocumentoFiscal = null,
            puntoFacturacionFiscal = caja?.serieCaja,
            codigoSucursal = caja?.codigoSucursalEmisor ?: caja?.sucursalCodigo,
            protocoloAutorizacion = null,
            numeroControlThka = null,
            igtfMonto = null,
            igtfBaseImponible = null,
            igtfTasa = null,
            tasaCambioBs = if (isMultiCurrency) request.moneda?.tasa?.let { formatMoney(it) } else null,
            abrMonedaBase = request.moneda?.abrMonedaBase,
            abrMonedaSecundaria = request.moneda?.abrMonedaSecundaria,
            totalDivisa = if (isMultiCurrency) request.moneda?.totalRef?.let { formatMoney(it) } else null,
        )
    }

    fun fromTransaction(
        transaction: Transaction,
        company: CompanyDetailsSnapshot? = null,
        caja: Caja? = null,
    ): FacturaPrintPayloadDto {
        val products = mapTransactionProducts(transaction)
        val payments = mapTransactionPayments(transaction)
        val totalTax = products.sumOf { it.impuesto.toDoubleOrNull() ?: 0.0 }
        val subtotal = products.sumOf { (it.precioUnitario.toDoubleOrNull() ?: 0.0) * (it.cantidad.toDoubleOrNull() ?: 1.0) }

        return FacturaPrintPayloadDto(
            facturaId = transaction.id,
            numeroFactura = transaction.invoiceNumber,
            fecha = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            empresa =
                EmpresaPrintDto(
                    nombre = company?.name?.ifBlank { null } ?: "AMAXONIA",
                    ruc = company?.rif?.takeIf { it.isNotBlank() },
                    tienda = caja?.sucursalNombre ?: caja?.sucursalCodigo,
                    caja = caja?.descripcion ?: caja?.codCaja,
                ),
            cliente =
                if (transaction.clienteNombre.isNotBlank()) {
                    ClientePrintDto(
                        nombre = transaction.clienteNombre,
                        documento = transaction.clienteIdentificacion.takeIf { it.isNotBlank() },
                    )
                } else {
                    null
                },
            vendedor = caja?.defaultSellerName,
            productos = products,
            subtotal = formatMoney(subtotal),
            montoExento = "0.00",
            descuento = "0.00",
            totalImpuesto = formatMoney(totalTax),
            total = formatMoney(transaction.amount),
            pagos = payments,
            cambio = null,
            qrUrl = null,
            cufe = null,
            fechaRecepcionDgi = null,
            proveedorAutorizado = null,
            numeroDocumentoFiscal = null,
            puntoFacturacionFiscal = caja?.serieCaja,
            codigoSucursal = caja?.codigoSucursalEmisor ?: caja?.sucursalCodigo,
            protocoloAutorizacion = null,
            numeroControlThka = null,
            igtfMonto = null,
            igtfBaseImponible = null,
            igtfTasa = null,
            tasaCambioBs = null,
            abrMonedaBase = transaction.currency,
            abrMonedaSecundaria = transaction.abrMonedaSecundaria,
            totalDivisa = transaction.totalRef?.let { formatMoney(it) },
        )
    }

    private fun mapRequestItems(request: ProcessSaleRequestDto): List<ProductoPrintDto> =
        request.items.map { item ->
            val taxAmount = (item.itemTotalConIva - item.itemTotalSinIva).coerceAtLeast(0.0)
            ProductoPrintDto(
                nombre = item.itemDescripcion,
                cantidad = formatQuantity(item.itemCantidadTotal),
                unidad = item.itemUnidadEmpaque.ifBlank { "UND" },
                precioUnitario = formatMoney(item.itemPrecioSinIva),
                descuento = formatMoney(item.itemMontoDescuento),
                impuesto = formatMoney(taxAmount),
                total = formatMoney(item.itemTotalConIva),
                codigo = item.itemCodigo.takeIf { it.isNotBlank() },
                tasaImpuesto = formatTaxRate(item.itemPIva),
            )
        }

    private fun mapRequestPagos(request: ProcessSaleRequestDto): List<PagoPrintDto> =
        request.pagos.map { pago ->
            PagoPrintDto(
                metodo = pago.tipoMovimiento,
                monto = formatMoney(pago.monto),
            )
        }

    private fun mapTransactionProducts(transaction: Transaction): List<ProductoPrintDto> =
        transaction.fiscalItems.map { item ->
            val totalSinIva = item.quantity * item.unitPriceWithoutTax
            val taxAmount = totalSinIva * (item.iva / PERCENT_DIVISOR)
            val totalConIva = totalSinIva + taxAmount
            ProductoPrintDto(
                nombre = item.description,
                cantidad = formatQuantity(item.quantity),
                unidad = "UND",
                precioUnitario = formatMoney(item.unitPriceWithoutTax),
                descuento = "0.00",
                impuesto = formatMoney(taxAmount),
                total = formatMoney(totalConIva),
                codigo = null,
                tasaImpuesto = formatTaxRate(item.iva),
            )
        }

    private fun mapTransactionPayments(transaction: Transaction): List<PagoPrintDto> =
        if (transaction.paymentMethods.isNotEmpty()) {
            transaction.paymentMethods.map { pm ->
                PagoPrintDto(
                    metodo = pm.sigla.ifBlank { pm.description.ifBlank { "PAGO" } },
                    monto = formatMoney(pm.amount),
                )
            }
        } else {
            listOf(PagoPrintDto(metodo = transaction.formaPago.ifBlank { "CONTADO" }, monto = formatMoney(transaction.amount)))
        }

    private fun buildEmpresaDto(
        company: CompanyDetailsSnapshot?,
        caja: Caja?,
        idSucursal: Int,
        codigoCaja: String,
    ): EmpresaPrintDto =
        EmpresaPrintDto(
            nombre = company?.name?.ifBlank { null } ?: "AMAXONIA",
            ruc = company?.rif?.takeIf { it.isNotBlank() },
            direccion = null,
            telefono = null,
            tienda = caja?.sucursalNombre ?: caja?.sucursalCodigo ?: "Sucursal $idSucursal",
            caja = caja?.descripcion ?: caja?.codCaja ?: "Caja $codigoCaja",
        )

    private fun buildClienteDto(
        facturarA: String,
        facturarARuc: String,
        facturarADireccion: String,
    ): ClientePrintDto? =
        if (facturarA.isNotBlank()) {
            ClientePrintDto(
                nombre = facturarA,
                documento = facturarARuc.takeIf { it.isNotBlank() && it != "CF" },
                sucursal = null,
                sucursalDireccion = facturarADireccion.takeIf { it.isNotBlank() },
                digitoVerificador = null,
                tipoReceptor = null,
            )
        } else {
            null
        }

    private fun formatMoney(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    private fun formatQuantity(quantity: Double): String =
        if (quantity % 1.0 == 0.0) quantity.toLong().toString() else String.format(Locale.US, "%.2f", quantity)

    private fun formatTaxRate(rate: Double): String =
        if (rate % 1.0 == 0.0) rate.toLong().toString() else String.format(Locale.US, "%.2f", rate)
}
