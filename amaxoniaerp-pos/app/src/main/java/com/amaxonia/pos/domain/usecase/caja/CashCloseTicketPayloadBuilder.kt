package com.amaxonia.pos.domain.usecase.caja

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CashCloseInventoryLine
import com.amaxonia.pos.domain.model.caja.CashClosePromotionLine
import com.amaxonia.pos.domain.model.caja.CashCloseTicketFormatter
import com.amaxonia.pos.domain.model.caja.CashCloseTicketPayload
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.PendingSalesReader
import com.amaxonia.pos.domain.repository.ProductCatalogReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CashCloseTicketPayloadBuilder(
    private val contextReader: CashCloseContextReader,
    private val productRepository: ProductCatalogReader,
    private val pendingSalesReader: PendingSalesReader,
    private val ticketFormatter: CashCloseTicketFormatter,
) {
    suspend fun build(
        summary: CierreCajaSummary,
        caja: Caja?,
    ): CashCloseTicketPayload {
        val company = contextReader.currentCompany()
        val branchName = caja?.sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal"
        val sales = readPeriodSales(summary)

        return CashCloseTicketPayload(
            companyName = company?.name?.takeIf(String::isNotBlank) ?: "EMPRESA",
            companyRuc = company?.rif.orEmpty(),
            companyAddress = branchName,
            companyPhone = "",
            sellerName = summary.vendedorName,
            cashRegisterName = summary.cajaName,
            branchName = branchName,
            summary = summary,
            paymentAmounts = buildPaymentAmounts(summary),
            generalPayments = 0.0,
            promotionLines = buildPromotionLines(sales),
            inventoryLines = summary.inventoryLines.ifEmpty { buildInventoryLines(sales) },
            qrPayload =
                listOf(
                    "tipo=CIERRE_CAJA",
                    "ruc=${company?.rif.orEmpty()}",
                    "empresa=${company?.name.orEmpty()}",
                    "secuencia=${summary.idCajaSecuencia}",
                    "caja=${summary.idCaja}",
                    "total=${formatQrAmount(summary.montoCierre)}",
                    "ventas=${formatQrAmount(summary.totalSales)}",
                    "transacciones=${summary.transactionCount}",
                    "vendedor=${summary.vendedorName}",
                    "sucursal=$branchName",
                ).joinToString("|"),
        )
    }

    private fun buildPaymentAmounts(summary: CierreCajaSummary): Map<String, Double> {
        val amounts = ticketFormatter.paymentLabels.associateWith { 0.0 }.toMutableMap()
        summary.paymentLines.forEach { line ->
            val label = normalizePaymentLabel(line.label, line.siglas)
            amounts[label] = amounts.getValue(label) + line.amount
        }
        if (summary.paymentLines.isEmpty()) {
            amounts["EFECTIVO"] = summary.totalCash
            amounts["TARJETA DE DEBITO"] = summary.totalCard
        }
        return amounts
    }

    private fun normalizePaymentLabel(
        label: String,
        siglas: String,
    ): String {
        val value =
            "$siglas $label"
                .uppercase(Locale.ROOT)
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("Á", "A")
        return when {
            value.contains("YAPPY") -> "YAPPY"
            value.contains("MASTER") -> "MASTERCARD"
            value.contains("VISA") -> "VISA"
            value.contains("ACH") || value.contains("IBAN") -> "ACH / IBAN"
            value.contains("REFER") -> "REFERENCIA"
            value.contains("DEPOS") -> "DEPOSITO"
            value.contains("CUENTA") || value.contains("COBRAR") || value.contains("CXC") -> "CUENTAS POR COBRAR"
            value.contains("NOTA") || value.contains("DEVOL") -> "NOTAS DE CREDITO / DEVOLUCIONES"
            value.contains("CERTIFIC") || value.contains("REGALO") -> "CERTIFICADO DE REGALO"
            value.contains("PUNTO") -> "PUNTOS"
            value.contains("DEBIT") || value.contains("TDD") -> "TARJETA DE DEBITO"
            value.contains("ABONO") -> "ABONOS APLICADOS"
            value.contains("CASH") || value.contains("EFECT") || value == "EF" || value == "EFE" -> "EFECTIVO"
            else -> "REFERENCIA"
        }
    }

    private suspend fun buildInventoryLines(sales: List<ProcessSaleRequestDto>): List<CashCloseInventoryLine> {
        val soldByProduct =
            sales
                .flatMap { it.items }
                .groupBy { it.idItem.toString() }
                .mapValues { (_, items) -> items.sumOf { it.itemCantidadTotal } }

        return soldByProduct
            .mapNotNull { (productId, soldQuantity) ->
                val product = productRepository.getProductById(productId).getOrNull()
                val availableQuantity =
                    productRepository.getProductStock(productId).getOrNull()?.stockTotalDisponible
                        ?: return@mapNotNull null
                CashCloseInventoryLine(
                    productCode = product?.code?.takeIf(String::isNotBlank) ?: productId,
                    productName = product?.description?.takeIf(String::isNotBlank) ?: "Producto $productId",
                    initialQuantity = availableQuantity + soldQuantity,
                    soldQuantity = soldQuantity,
                    realQuantity = availableQuantity,
                )
            }.sortedBy { it.productName }
    }

    private fun buildPromotionLines(sales: List<ProcessSaleRequestDto>): List<CashClosePromotionLine> =
        sales
            .flatMap { it.items }
            .filter { it.promocionId.isNotBlank() }
            .groupBy { it.promocionId }
            .map { (promotionId, items) ->
                val first = items.first()
                val soldTimes =
                    items
                        .groupBy { it.promocionDetalleId.ifBlank { it.idItem.toString() } }
                        .values
                        .firstOrNull()
                        ?.sumOf { it.promocionCantidad.takeIf { quantity -> quantity > 0.0 } ?: 1.0 }
                        ?: 0.0
                CashClosePromotionLine(
                    promotionCode = first.promocionCodigo.ifBlank { promotionId },
                    promotionName = first.promocionNombre.ifBlank { first.promocionCodigo.ifBlank { "Promocion $promotionId" } },
                    soldTimes = soldTimes,
                )
            }.filter { it.soldTimes > 0.0 }
            .sortedBy { it.promotionName }

    private suspend fun readPeriodSales(summary: CierreCajaSummary): List<ProcessSaleRequestDto> {
        val zone = ZoneId.systemDefault()
        val start =
            parseOpenedAt(summary.openedAt)
                ?.atZone(zone)
                ?.toInstant()
                ?.toEpochMilli()
                ?: LocalDate
                    .now(zone)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
        val end = System.currentTimeMillis()
        return pendingSalesReader
            .createdBetween(start, end)
            .filter { it.factura.idCajaSecuencia == summary.idCajaSecuencia }
    }

    private fun parseOpenedAt(value: String): LocalDateTime? =
        DATE_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value.trim(), formatter) }.getOrNull()
        }

    private fun formatQrAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        val DATE_TIME_FORMATTERS =
            listOf(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            )
    }
}
