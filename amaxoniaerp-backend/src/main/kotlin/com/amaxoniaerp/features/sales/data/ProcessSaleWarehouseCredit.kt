package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class WarehouseContext(
    val defaultWarehouseId: Int,
    val allowedWarehouseIds: Set<Int>,
    val idSucursal: Int?,
    val serieSucursal: String?,
)

internal data class CreditDecision(
    val formaPago: String,
    val totalGeneral: BigDecimal,
    val totalPagos: BigDecimal,
    val totalCxc: BigDecimal,
    val totalPagadoReal: BigDecimal,
    val saldoEsperado: BigDecimal,
    val isCredit: Boolean,
    val fechaVencimiento: LocalDate?,
)

/** Totales derivados de los pagos para decidir el carácter contado/crédito. */
private class CreditAmounts(
    val totalGeneral: BigDecimal,
    val totalPagos: BigDecimal,
    val totalCxc: BigDecimal,
    val totalPagadoReal: BigDecimal,
    val saldoEsperado: BigDecimal,
    val saldoDeclarado: BigDecimal,
    val isCredit: Boolean,
    val hasNegativePayment: Boolean,
)

internal fun resolveWarehouseContext(
    countryCode: String,
    cajaId: String,
): WarehouseContext {
    val isVE = countryCode.equals("VE", ignoreCase = true)
    val columns =
        if (isVE) {
            listOf(SalesCajaTable.idSucursal, SalesCajaTable.codAlmacen)
        } else {
            listOf(SalesCajaTable.idSucursal)
        }
    val caja =
        SalesCajaTable
            .select(columns)
            .where { SalesCajaTable.id eq cajaId }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$cajaId")

    val cajaWarehouseId =
        if (isVE) {
            caja.getOrNull(SalesCajaTable.codAlmacen)?.takeIf { it > 0 }
        } else {
            null
        }
    val cajaSucursalId = caja[SalesCajaTable.idSucursal]
    val serieSucursal = cajaSucursalId?.let(::serieDeSucursal)
    val globalWarehouseId = globalWarehouse(countryCode)

    val defaultWarehouseId =
        cajaWarehouseId ?: sucursalDefaultWarehouse(cajaSucursalId) ?: globalWarehouseId
            ?: throw InvalidSaleRequestException(
                "No se pudo resolver almacén por defecto para caja=$cajaId (caja/sucursal/parámetros generales)",
            )

    return WarehouseContext(
        defaultWarehouseId = defaultWarehouseId,
        allowedWarehouseIds =
            allowedWarehouses(cajaWarehouseId, globalWarehouseId, cajaSucursalId, defaultWarehouseId),
        idSucursal = cajaSucursalId,
        serieSucursal = serieSucursal,
    )
}

private fun serieDeSucursal(sucursalId: Int): String? =
    SalesSucursalTable
        .select(SalesSucursalTable.serie)
        .where { SalesSucursalTable.id eq sucursalId }
        .limit(1)
        .firstOrNull()
        ?.get(SalesSucursalTable.serie)
        ?.takeIf { it.isNotBlank() }

private fun sucursalDefaultWarehouse(cajaSucursalId: Int?): Int? =
    cajaSucursalId?.let { sucursalId ->
        SalesSucursalAlmacenTable
            .select(SalesSucursalAlmacenTable.idAlmacen)
            .where {
                (SalesSucursalAlmacenTable.idSucursal eq sucursalId) and
                    (SalesSucursalAlmacenTable.defaultVentas eq 1)
            }.limit(1)
            .firstOrNull()
            ?.get(SalesSucursalAlmacenTable.idAlmacen)
            ?.takeIf { it > 0 }
    }

private fun globalWarehouse(countryCode: String): Int? {
    val pgTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
    return pgTable
        .select(pgTable.codAlmacen)
        .orderBy(pgTable.codEmpresa)
        .limit(1)
        .firstOrNull()
        ?.get(pgTable.codAlmacen)
        ?.let { kotlin.math.abs(it) }
        ?.takeIf { it > 0 }
}

private fun allowedWarehouses(
    cajaWarehouseId: Int?,
    globalWarehouseId: Int?,
    cajaSucursalId: Int?,
    defaultWarehouseId: Int,
): Set<Int> {
    val allowed = mutableSetOf<Int>()
    if (cajaWarehouseId != null) {
        allowed += cajaWarehouseId
    }
    if (globalWarehouseId != null) {
        allowed += globalWarehouseId
    }
    if (cajaSucursalId != null) {
        allowed +=
            SalesSucursalAlmacenTable
                .select(SalesSucursalAlmacenTable.idAlmacen)
                .where { SalesSucursalAlmacenTable.idSucursal eq cajaSucursalId }
                .mapNotNull { row -> row[SalesSucursalAlmacenTable.idAlmacen].takeIf { it > 0 } }
    }
    allowed += defaultWarehouseId
    return allowed
}

internal fun validateWarehouseOwnership(
    request: ProcessSaleRequest,
    normalizedItems: List<SaleItemInput>,
    context: WarehouseContext,
) {
    val invalidWarehouses =
        normalizedItems
            .map { it.itemAlmacen }
            .filter { it !in context.allowedWarehouseIds }
            .distinct()

    if (invalidWarehouses.isNotEmpty()) {
        throw InvalidSaleRequestException(
            "Almacen(es) no permitidos para caja=${request.factura.idCaja}: ${invalidWarehouses.joinToString(
                ",",
            )}. Permitidos: ${context.allowedWarehouseIds.sorted().joinToString(",")}",
        )
    }
}

internal fun resolveCreditDecision(
    request: ProcessSaleRequest,
    today: LocalDate,
): CreditDecision {
    val amounts = computeCreditAmounts(request)
    validateSaleAmounts(amounts)
    validatePaymentCoherence(request, amounts)
    if (amounts.isCredit) validateCreditRules(amounts) else validateCashRules(amounts)

    val diasCredito = if (amounts.isCredit) resolveDiasCredito(request) else null

    return CreditDecision(
        formaPago = if (amounts.isCredit) "credito" else "contado",
        totalGeneral = amounts.totalGeneral,
        totalPagos = amounts.totalPagos,
        totalCxc = amounts.totalCxc,
        totalPagadoReal = amounts.totalPagadoReal,
        saldoEsperado = amounts.saldoEsperado,
        isCredit = amounts.isCredit,
        fechaVencimiento = diasCredito?.let { today.plusDays(it.toLong()) },
    )
}

private fun computeCreditAmounts(request: ProcessSaleRequest): CreditAmounts {
    val totalGeneral = request.factura.totalTotalFactura.toScaledBigDecimal(2)
    val pagos =
        request.pagos.map { payment ->
            payment.tipoMovimiento.trim().uppercase() to payment.monto.toScaledBigDecimal(2)
        }
    val totalPagos = pagos.fold(BigDecimal.ZERO.setScale(2)) { total, (_, amount) -> total + amount }
    val totalCxc =
        pagos
            .filter { (tipoMovimiento, _) -> tipoMovimiento == "CXC" }
            .fold(BigDecimal.ZERO.setScale(2)) { total, (_, amount) -> total + amount }
    val totalPagadoReal = totalPagos - totalCxc
    val saldoEsperado =
        totalGeneral
            .subtract(totalPagadoReal)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
    val saldoDeclarado = request.pagoResumen.totalizarSaldoPendiente.toScaledBigDecimal(2)
    val formaPagoSolicitada =
        request.factura.formaPago
            .trim()
            .lowercase()
    val isCredit =
        formaPagoSolicitada == "credito" ||
            totalCxc > BigDecimal.ZERO ||
            saldoDeclarado > BigDecimal.ZERO

    return CreditAmounts(
        totalGeneral,
        totalPagos,
        totalCxc,
        totalPagadoReal,
        saldoEsperado,
        saldoDeclarado,
        isCredit,
        pagos.any { (_, amount) -> amount < BigDecimal.ZERO },
    )
}

private fun validateSaleAmounts(amounts: CreditAmounts) {
    if (amounts.totalGeneral < BigDecimal.ZERO || amounts.hasNegativePayment) {
        throw InvalidSaleRequestException("Los montos de la venta no pueden ser negativos")
    }
    if (amounts.saldoDeclarado < BigDecimal.ZERO) {
        throw InvalidSaleRequestException("El saldo pendiente no puede ser negativo")
    }
    if (amounts.totalCxc > amounts.totalGeneral) {
        throw InvalidSaleRequestException("El monto CXC no puede exceder el total de la venta")
    }
}

private fun validatePaymentCoherence(
    request: ProcessSaleRequest,
    amounts: CreditAmounts,
) {
    val excesoPago = amounts.totalPagadoReal - amounts.totalGeneral
    if (excesoPago > BigDecimal.ZERO) {
        val cambioDeclarado = request.pagoResumen.totalizarCambio.toScaledBigDecimal(2)
        if (cambioDeclarado != excesoPago.setScale(2, RoundingMode.HALF_UP)) {
            throw InvalidSaleRequestException("Los pagos exceden el total sin un cambio de efectivo coherente")
        }
    }
}

private fun validateCashRules(amounts: CreditAmounts) {
    if (amounts.totalCxc > BigDecimal.ZERO ||
        amounts.saldoEsperado > BigDecimal.ZERO ||
        amounts.saldoDeclarado > BigDecimal.ZERO
    ) {
        throw InvalidSaleRequestException("Una venta de contado debe quedar totalmente pagada y sin CXC")
    }
}

private fun validateCreditRules(amounts: CreditAmounts) {
    if (amounts.totalCxc > BigDecimal.ZERO && amounts.totalCxc != amounts.saldoEsperado) {
        throw InvalidSaleRequestException("El monto CXC no coincide con el saldo esperado")
    }
    if (amounts.saldoDeclarado > BigDecimal.ZERO && amounts.saldoDeclarado != amounts.saldoEsperado) {
        throw InvalidSaleRequestException("El saldo pendiente no coincide con los pagos recibidos")
    }
    if (amounts.totalCxc == BigDecimal.ZERO &&
        amounts.saldoDeclarado == BigDecimal.ZERO &&
        amounts.saldoEsperado > BigDecimal.ZERO
    ) {
        throw InvalidSaleRequestException("Debe indicar el saldo pendiente de la venta a crédito")
    }
}

private fun resolveDiasCredito(request: ProcessSaleRequest): Int? {
    val client =
        ClientsTable
            .select(ClientsTable.permiteCredito, ClientsTable.dias)
            .where { ClientsTable.idCliente eq request.factura.idCliente }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró el cliente para la venta a crédito")

    if (!client[ClientsTable.permiteCredito]) {
        throw InvalidSaleRequestException("El cliente no permite ventas a crédito")
    }

    return client[ClientsTable.dias].also { dias ->
        if (dias < 0) {
            throw InvalidSaleRequestException(
                "La configuración de días de crédito del cliente es inválida",
            )
        }
    }
}
