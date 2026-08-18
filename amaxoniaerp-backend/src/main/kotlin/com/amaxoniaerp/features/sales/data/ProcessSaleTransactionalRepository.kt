package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.clients.data.ClientSucursalTable
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.mesas.data.CuentaMesaRepository
import com.amaxoniaerp.features.mesas.data.validarVentaEnTransaccion
import com.amaxoniaerp.features.sales.domain.DuplicateInvoiceException
import com.amaxoniaerp.features.sales.domain.InsufficientStockException
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private const val SHORT_CODE_LENGTH = 10
private const val CLIENT_CODE_LENGTH = 9

/**
 * Repositorio transaccional de procesamiento de ventas.
 *
 * **FASE 1.1 — Brief item 8 (deuda técnica anotada):** esta clase se mantiene
 * `open` únicamente para que los tests de `ProcessSaleUseCaseSelectionTest`
 * puedan inyectar fakes (`FakeSaleRepo`, `DuplicateAwareSaleRepo`) sin tocar la
 * DB real. La opción preferida por el brief sería extraer un puerto
 * `interface SaleProcessor { fun process(...): ProcessSaleResponse }` y dejar
 * esta clase final, pero esa refactorización queda fuera del alcance de FASE 1.1
 * (el brief prohíbe refactor general).
 *
 * Cuando se aborde, mover estos overrides a una clase propia de tests y cerrar
 * esta clase.
 *
 * Los contextos (warehouse/monetario/crédito) viven en
 * ProcessSaleWarehouseCredit.kt / ProcessSaleMonetary.kt; el código de factura
 * y correlativos en ProcessSaleInvoiceCode.kt; las escrituras de factura en
 * ProcessSaleInvoiceWrites.kt y las de inventario/caja en
 * ProcessSaleInventoryCajaWrites.kt.
 */
open class ProcessSaleTransactionalRepository(
    private val cuentaMesaRepository: CuentaMesaRepository? = null,
) {
    open fun process(
        countryCode: String,
        request: ProcessSaleRequest,
    ): ProcessSaleResponse {
        val preparedRequest = prepareRequestWithWarehouses(countryCode, request)
        val monetaryContext = resolveMonetaryContext(countryCode, preparedRequest)

        validateDuplicateInvoice(monetaryContext.countryCode, preparedRequest)
        validateClientSucursalIfRequired(monetaryContext.countryCode, preparedRequest)
        if (monetaryContext.shouldValidateStock()) {
            validateStock(preparedRequest)
        }

        val now = BusinessClock.nowForCountry(countryCode)
        val today = now.toLocalDate()
        val creditDecision = resolveCreditDecision(preparedRequest, today)

        val invoiceId = preparedRequest.idFactura?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val cuentaValidada =
            preparedRequest.cuentaMesa?.let { context ->
                val repository =
                    cuentaMesaRepository
                        ?: throw InvalidSaleRequestException("La integración de cuenta de mesa no está configurada")
                repository.validarVentaEnTransaccion(context, preparedRequest, invoiceId)
            }
        val invoiceCode = resolveInvoiceCode(countryCode, preparedRequest)

        val writeContext =
            SaleWriteContext(
                request = preparedRequest,
                invoiceId = invoiceId,
                invoiceCode = invoiceCode,
                now = now,
                today = today,
                monetaryContext = monetaryContext,
                creditDecision = creditDecision,
            )

        insertFactura(writeContext)
        val detalleIds = insertFacturaDetalle(writeContext)
        processLotTracking(writeContext, detalleIds)
        insertFacturaImpuestos(writeContext)
        insertFacturaDetalleFormaPago(writeContext)

        val shouldAffectInventory =
            (preparedRequest.procesar == 1 || preparedRequest.factura.codEstatus == 2) &&
                !preparedRequest.esCobroCreditoPrevio
        if (shouldAffectInventory) {
            updateInventoryAndKardex(writeContext)
            insertCajaEntries(writeContext)
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

    private fun prepareRequestWithWarehouses(
        countryCode: String,
        request: ProcessSaleRequest,
    ): ProcessSaleRequest {
        val context = resolveWarehouseContext(countryCode, request.factura.idCaja)

        val normalizedItems =
            request.items.map { item ->
                val resolvedWarehouse = item.itemAlmacen.takeIf { it > 0 } ?: context.defaultWarehouseId
                item.copy(itemAlmacen = resolvedWarehouse)
            }

        val normalizedFactura =
            request.factura.copy(
                idSucursal = context.idSucursal ?: request.factura.idSucursal,
                serieSucursal =
                    context.serieSucursal
                        ?: request.factura.serieSucursal.take(SHORT_CODE_LENGTH),
            )
        val normalizedPayments =
            request.pagos.map { payment ->
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

    private fun validateDuplicateInvoice(
        countryCode: String,
        request: ProcessSaleRequest,
    ) {
        val idFactura = request.idFactura?.takeIf { it.isNotBlank() }
        if (idFactura == null) return

        val t = SalesFacturaTableFactory.forCountry(countryCode)
        val existing =
            t
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
        val requiredByItemWarehouse =
            request.items
                .filter { it.esProductoFisico }
                .groupBy { it.idItem to it.itemAlmacen }
                .mapValues { (_, lines) -> lines.sumOf { it.itemCantidadTotal }.toScaledBigDecimal(2) }

        if (requiredByItemWarehouse.isEmpty()) return

        val itemIds = requiredByItemWarehouse.keys.map { it.first }.distinct()
        val almacenes = requiredByItemWarehouse.keys.map { it.second }.distinct()

        val available =
            SalesStockTable
                .selectAll()
                .where {
                    (SalesStockTable.idItem inList itemIds) and
                        (SalesStockTable.codAlmacen inList almacenes)
                }.associate { row ->
                    (row[SalesStockTable.idItem] to row[SalesStockTable.codAlmacen]) to
                        row[SalesStockTable.cantidad].toBigDecimal().setScale(2, RoundingMode.HALF_UP)
                }

        val failures = mutableListOf<String>()
        requiredByItemWarehouse.forEach { (key, required) ->
            val availableQty = available[key] ?: BigDecimal.ZERO.setScale(2)
            if (availableQty < required) {
                failures.add(
                    "item=${key.first}, almacen=${key.second}, solicitado=$required, disponible=$availableQty",
                )
            }
        }

        if (failures.isNotEmpty()) {
            throw InsufficientStockException("Stock insuficiente: ${failures.joinToString(" | ")}")
        }
    }

    private fun validateClientSucursalIfRequired(
        countryCode: String,
        request: ProcessSaleRequest,
    ) = run {
        if (!countryCode.equals("PA", ignoreCase = true)) return
        val clientCode = request.factura.codCliente.take(CLIENT_CODE_LENGTH)
        if (clientCode.isBlank()) return

        val sucursales =
            ClientSucursalTable
                .select(ClientSucursalTable.sucursalId)
                .where { ClientSucursalTable.clienteCodigo eq clientCode }
                .map { it[ClientSucursalTable.sucursalId] }
        if (sucursales.isEmpty()) return

        val selectedSucursalId =
            request.factura.clienteSucursalId
                ?: throw InvalidSaleRequestException("Debes seleccionar la sucursal del cliente")
        if (selectedSucursalId !in sucursales) {
            throw InvalidSaleRequestException("La sucursal seleccionada no pertenece al cliente")
        }
    }
}
