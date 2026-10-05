package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.items.data.ItemsTableFactory
import com.amaxoniaerp.features.kiosk.domain.KioskOrderItemRecord
import com.amaxoniaerp.features.kiosk.domain.KioskOrderModifierRecord
import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteLineModifierResponse
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteLineRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteLineResponse
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteResponse
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class KioskOrderRepository {

    suspend fun findQuoteById(
        database: Database,
        countryCode: String,
        orderId: String,
    ): KioskQuoteResponse? = dbQuery(database) {
        loadQuoteById(orderId, countryCode)
    }

    private fun loadQuoteById(
        orderId: String,
        countryCode: String,
    ): KioskQuoteResponse? {
        val orderRow = KioskOrderTable
            .selectAll()
            .where { KioskOrderTable.id eq orderId }
            .singleOrNull() ?: return null

        val itemsTable = ItemsTableFactory.getTableForCountry(countryCode)

        val itemRows = KioskOrderItemTable
            .selectAll()
            .where { KioskOrderItemTable.idPedido eq orderId }
            .orderBy(KioskOrderItemTable.linea to SortOrder.ASC)
            .toList()

        val itemIds = itemRows.map { it[KioskOrderItemTable.idItem] }.distinct()
        val itemDetailsMap = if (itemIds.isNotEmpty()) {
            itemsTable
                .selectAll()
                .where { itemsTable.idItem inList itemIds }
                .associateBy { it[itemsTable.idItem] }
        } else {
            emptyMap()
        }

        val modRows = KioskOrderItemModifierTable
            .selectAll()
            .where { KioskOrderItemModifierTable.idPedido eq orderId }
            .orderBy(
                KioskOrderItemModifierTable.linea to SortOrder.ASC,
                KioskOrderItemModifierTable.idModificador to SortOrder.ASC,
            )
            .toList()
            .groupBy { it[KioskOrderItemModifierTable.linea] }

        val linesResponse = itemRows.map { itemRow ->
            val linea = itemRow[KioskOrderItemTable.linea]
            val itemId = itemRow[KioskOrderItemTable.idItem]
            val qty = itemRow[KioskOrderItemTable.cantidad].toInt()
            val unitPrice = itemRow[KioskOrderItemTable.precioUnitario]
            val nota = itemRow[KioskOrderItemTable.nota]

            val itemDetail = itemDetailsMap[itemId]
            val name = itemDetail?.get(itemsTable.descripcion1)?.trim() ?: "Item $itemId"

            val lineSubtotal = (unitPrice * BigDecimal.valueOf(qty.toLong())).setScale(2, RoundingMode.HALF_UP)

            val taxRate = when {
                itemDetail == null -> BigDecimal("7.00")
                itemDetail[itemsTable.montoExento] -> BigDecimal.ZERO
                itemDetail[itemsTable.iva] > BigDecimal.ZERO -> itemDetail[itemsTable.iva]
                else -> BigDecimal("7.00")
            }
            val lineTax = (lineSubtotal * taxRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            val lineTotal = lineSubtotal + lineTax

            val lineModifiers = modRows[linea]?.map { mRow ->
                KioskQuoteLineModifierResponse(
                    id = mRow[KioskOrderItemModifierTable.idModificador],
                    name = mRow[KioskOrderItemModifierTable.nombre],
                    extraPrice = mRow[KioskOrderItemModifierTable.precioAdicional].setScale(2, RoundingMode.HALF_UP).toPlainString(),
                )
            } ?: emptyList()

            KioskQuoteLineResponse(
                line = linea,
                itemId = itemId,
                name = name,
                qty = qty,
                unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                subtotal = lineSubtotal.toPlainString(),
                tax = lineTax.toPlainString(),
                total = lineTotal.toPlainString(),
                note = nota,
                modifiers = lineModifiers,
            )
        }

        val subtotal = linesResponse.sumOf { BigDecimal(it.subtotal) }.setScale(2, RoundingMode.HALF_UP)
        val tax = linesResponse.sumOf { BigDecimal(it.tax) }.setScale(2, RoundingMode.HALF_UP)
        val total = orderRow[KioskOrderTable.total].setScale(2, RoundingMode.HALF_UP)

        return KioskQuoteResponse(
            orderId = orderRow[KioskOrderTable.id].trim(),
            formattedOrderNumber = orderRow[KioskOrderTable.codigoPedido],
            subtotal = subtotal.toPlainString(),
            tax = tax.toPlainString(),
            total = total.toPlainString(),
            expiresAt = orderRow[KioskOrderTable.quoteExpiraEn].format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            diningMode = orderRow[KioskOrderTable.modalidad],
            tableTent = orderRow[KioskOrderTable.portamesa],
            customerId = orderRow[KioskOrderTable.idCliente].trim(),
            lines = linesResponse,
        )
    }

    suspend fun createQuote(
        database: Database,
        kioskContext: KioskRequestContext,
        idempotencyKey: String,
        request: KioskQuoteRequest,
    ): KioskQuoteResponse = dbQuery(database) {
        val existing = loadQuoteById(idempotencyKey, kioskContext.countryCode)
        if (existing != null) {
            return@dbQuery existing
        }

        // 1. Validaciones básicas de solicitud
        val diningMode = request.diningMode.trim().uppercase()
        if (diningMode != "COMER_AQUI" && diningMode != "PARA_LLEVAR") {
            throw IllegalArgumentException("Modalidad de pedido inválida. Debe ser COMER_AQUI o PARA_LLEVAR")
        }

        if (request.lines.isEmpty()) {
            throw IllegalArgumentException("El pedido debe contener al menos un producto")
        }

        for (line in request.lines) {
            if (line.qty < 1) {
                throw IllegalArgumentException("La cantidad debe ser mayor a 0 para el producto ${line.itemId}")
            }
        }

        val customerId = request.customerId?.trim()?.takeIf { it.isNotBlank() } ?: kioskContext.idClienteGenerico
        val tableTent = request.tableTent?.trim()?.takeIf { it.isNotBlank() }

        // 2. Parámetros generales
        val paramsTable = ParametrosGeneralesTableFactory.forCountry(kioskContext.countryCode)
        val paramsRow = paramsTable
            .select(paramsTable.validarStock, paramsTable.porcentajeImpuestoPrincipal)
            .limit(1)
            .singleOrNull()
        val validarStock = paramsRow?.get(paramsTable.validarStock)?.trim()?.equals("SI", ignoreCase = true) == true
        val defaultTax = paramsRow?.get(paramsTable.porcentajeImpuestoPrincipal) ?: BigDecimal("7.00")

        val itemsTable = ItemsTableFactory.getTableForCountry(kioskContext.countryCode)

        // 3. Cargar items del pedido
        val requestedItemIds = request.lines.map { it.itemId }.distinct()
        val itemsMap = itemsTable
            .selectAll()
            .where { (itemsTable.idItem inList requestedItemIds) and (itemsTable.estatus eq "A") }
            .associateBy { it[itemsTable.idItem] }

        // 4. Validar y calcular cada línea
        data class CalculatedModifier(val id: Int, val nombre: String, val precioAdicional: BigDecimal)
        data class CalculatedLine(
            val line: Int,
            val itemId: Int,
            val name: String,
            val qty: Int,
            val unitPrice: BigDecimal,
            val subtotal: BigDecimal,
            val tax: BigDecimal,
            val total: BigDecimal,
            val note: String?,
            val modifiers: List<CalculatedModifier>,
        )

        val calculatedLines = mutableListOf<CalculatedLine>()

        for ((index, lineReq) in request.lines.withIndex()) {
            val itemRow = itemsMap[lineReq.itemId]
                ?: throw IllegalArgumentException("Producto ${lineReq.itemId} no encontrado o inactivo")

            if (validarStock && itemRow[itemsTable.existenciaTotal] <= 0) {
                throw IllegalArgumentException("Producto '${itemRow[itemsTable.descripcion1].trim()}' se encuentra agotado")
            }

            // Grupos de modificadores asociados al item
            val groupsAssigned = (ItemModifierRelationTable innerJoin ItemModifierGroupTable)
                .selectAll()
                .where {
                    (ItemModifierRelationTable.idItem eq lineReq.itemId) and
                        (ItemModifierGroupTable.activo eq true)
                }
                .toList()

            val groupMap = groupsAssigned.associateBy { it[ItemModifierGroupTable.id] }
            val assignedGroupIds = groupMap.keys

            // Opciones de modificadores disponibles para los grupos asignados
            val availableModifiers = if (assignedGroupIds.isNotEmpty()) {
                ItemModifierTable
                    .selectAll()
                    .where {
                        (ItemModifierTable.idGrupo inList assignedGroupIds) and
                            (ItemModifierTable.activo eq true)
                    }
                    .associateBy { it[ItemModifierTable.id] }
            } else {
                emptyMap()
            }

            // Validar que los modificadores enviados pertenezcan a los grupos del item
            val selectedModifiers = mutableListOf<CalculatedModifier>()
            val selectedByGroupId = mutableMapOf<Int, MutableList<Int>>()

            for (modId in lineReq.modifiers) {
                val modRow = availableModifiers[modId]
                    ?: throw IllegalArgumentException("Modificador $modId no válido para el producto '${itemRow[itemsTable.descripcion1].trim()}'")

                val groupId = modRow[ItemModifierTable.idGrupo]
                selectedByGroupId.getOrPut(groupId) { mutableListOf() }.add(modId)

                // Si tiene item asociado y se valida stock, verificar disponibilidad
                val associatedItemId = modRow[ItemModifierTable.idItemAsociado]
                if (validarStock && associatedItemId != null && associatedItemId > 0) {
                    val associatedItem = itemsTable
                        .select(itemsTable.idItem, itemsTable.existenciaTotal)
                        .where { itemsTable.idItem eq associatedItemId }
                        .singleOrNull()
                    if (associatedItem != null && associatedItem[itemsTable.existenciaTotal] <= 0) {
                        throw IllegalArgumentException("Modificador '${modRow[ItemModifierTable.nombre]}' se encuentra agotado")
                    }
                }

                selectedModifiers.add(
                    CalculatedModifier(
                        id = modId,
                        nombre = modRow[ItemModifierTable.nombre],
                        precioAdicional = modRow[ItemModifierTable.precioAdicional],
                    ),
                )
            }

            // Validar mínimos y máximos por grupo
            for (groupRow in groupsAssigned) {
                val groupId = groupRow[ItemModifierGroupTable.id]
                val groupName = groupRow[ItemModifierGroupTable.nombre]
                val esObligatorio = groupRow[ItemModifierGroupTable.esObligatorio]
                val minSeleccion = groupRow[ItemModifierGroupTable.minSeleccion]
                val maxSeleccion = groupRow[ItemModifierGroupTable.maxSeleccion]

                val effectiveMin = if (esObligatorio && minSeleccion == 0) 1 else minSeleccion
                val selectedCount = selectedByGroupId[groupId]?.size ?: 0

                if (selectedCount < effectiveMin) {
                    throw IllegalArgumentException("El grupo '$groupName' requiere al menos $effectiveMin selección(es)")
                }
                if (maxSeleccion > 0 && selectedCount > maxSeleccion) {
                    throw IllegalArgumentException("El grupo '$groupName' permite un máximo de $maxSeleccion selección(es)")
                }
            }

            // Cálculo en Money / BigDecimal
            val basePrice = itemRow[itemsTable.precio1].setScale(2, RoundingMode.HALF_UP)
            val modifiersExtra = selectedModifiers
                .sumOf { it.precioAdicional }
                .setScale(2, RoundingMode.HALF_UP)

            val unitPrice = basePrice + modifiersExtra
            val qtyBd = BigDecimal.valueOf(lineReq.qty.toLong())
            val lineSubtotal = (unitPrice * qtyBd).setScale(2, RoundingMode.HALF_UP)

            val taxRate = when {
                itemRow[itemsTable.montoExento] -> BigDecimal.ZERO
                itemRow[itemsTable.iva] > BigDecimal.ZERO -> itemRow[itemsTable.iva]
                else -> defaultTax
            }
            val lineTax = (lineSubtotal * taxRate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
            val lineTotal = lineSubtotal + lineTax

            calculatedLines.add(
                CalculatedLine(
                    line = index + 1,
                    itemId = lineReq.itemId,
                    name = itemRow[itemsTable.descripcion1].trim(),
                    qty = lineReq.qty,
                    unitPrice = unitPrice,
                    subtotal = lineSubtotal,
                    tax = lineTax,
                    total = lineTotal,
                    note = lineReq.note?.trim()?.takeIf { it.isNotBlank() },
                    modifiers = selectedModifiers,
                ),
            )
        }

        // 5. Totales generales
        val overallSubtotal = calculatedLines.sumOf { it.subtotal }.setScale(2, RoundingMode.HALF_UP)
        val overallTax = calculatedLines.sumOf { it.tax }.setScale(2, RoundingMode.HALF_UP)
        val overallTotal = overallSubtotal + overallTax

        // 6. Asignar número de pedido diario con prefijo de kiosco
        val today = LocalDate.now()
        val maxTodayExpr = KioskOrderTable.numeroPedidoDiario.max()
        val maxToday = KioskOrderTable
            .select(maxTodayExpr)
            .where {
                (KioskOrderTable.idDispositivo eq kioskContext.deviceId) and
                    (KioskOrderTable.fecha eq today)
            }
            .singleOrNull()
            ?.get(maxTodayExpr) ?: 0

        val nextSeq = maxToday + 1
        val formattedOrderNumber = "${kioskContext.prefix}-${nextSeq.toString().padStart(3, '0')}"
        val now = LocalDateTime.now()
        val expiresAt = now.plusMinutes(10)

        // 7. Insertar en kiosco_pedido
        KioskOrderTable.insert {
            it[id] = idempotencyKey
            it[idDispositivo] = kioskContext.deviceId
            it[numeroPedidoDiario] = nextSeq
            it[codigoPedido] = formattedOrderNumber
            it[fecha] = today
            it[estado] = "COTIZADO"
            it[modalidad] = diningMode
            it[portamesa] = tableTent
            it[this.idCliente] = customerId
            it[total] = overallTotal.setScale(4, RoundingMode.HALF_UP)
            it[quoteExpiraEn] = expiresAt
            it[creadoEn] = now
            it[actualizadoEn] = now
        }

        // 8. Insertar líneas e modificadores
        for (line in calculatedLines) {
            KioskOrderItemTable.insert {
                it[idPedido] = idempotencyKey
                it[this.linea] = line.line
                it[idItem] = line.itemId
                it[cantidad] = BigDecimal.valueOf(line.qty.toLong()).setScale(4, RoundingMode.HALF_UP)
                it[precioUnitario] = line.unitPrice.setScale(4, RoundingMode.HALF_UP)
                it[nota] = line.note
            }

            for (mod in line.modifiers) {
                KioskOrderItemModifierTable.insert {
                    it[idPedido] = idempotencyKey
                    it[this.linea] = line.line
                    it[idModificador] = mod.id
                    it[nombre] = mod.nombre
                    it[precioAdicional] = mod.precioAdicional.setScale(4, RoundingMode.HALF_UP)
                }
            }
        }

        // 9. Construir respuesta
        KioskQuoteResponse(
            orderId = idempotencyKey,
            formattedOrderNumber = formattedOrderNumber,
            subtotal = overallSubtotal.toPlainString(),
            tax = overallTax.toPlainString(),
            total = overallTotal.toPlainString(),
            expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            diningMode = diningMode,
            tableTent = tableTent,
            customerId = customerId,
            lines = calculatedLines.map { line ->
                KioskQuoteLineResponse(
                    line = line.line,
                    itemId = line.itemId,
                    name = line.name,
                    qty = line.qty,
                    unitPrice = line.unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    subtotal = line.subtotal.toPlainString(),
                    tax = line.tax.toPlainString(),
                    total = line.total.toPlainString(),
                    note = line.note,
                    modifiers = line.modifiers.map { m ->
                        KioskQuoteLineModifierResponse(
                            id = m.id,
                            name = m.nombre,
                            extraPrice = m.precioAdicional.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        )
                    },
                )
            },
        )
    }

    suspend fun findOrderRecordById(
        database: Database,
        orderId: String,
    ): KioskOrderRecord? = dbQuery(database) {
        val orderRow = KioskOrderTable
            .selectAll()
            .where { KioskOrderTable.id eq orderId }
            .singleOrNull() ?: return@dbQuery null

        val itemRows = KioskOrderItemTable
            .selectAll()
            .where { KioskOrderItemTable.idPedido eq orderId }
            .orderBy(KioskOrderItemTable.linea to SortOrder.ASC)
            .toList()

        val modRows = KioskOrderItemModifierTable
            .selectAll()
            .where { KioskOrderItemModifierTable.idPedido eq orderId }
            .orderBy(
                KioskOrderItemModifierTable.linea to SortOrder.ASC,
                KioskOrderItemModifierTable.idModificador to SortOrder.ASC,
            )
            .toList()
            .groupBy { it[KioskOrderItemModifierTable.linea] }

        val items = itemRows.map { iRow ->
            val linea = iRow[KioskOrderItemTable.linea]
            val mods = modRows[linea]?.map { mRow ->
                KioskOrderModifierRecord(
                    idPedido = orderId,
                    linea = linea,
                    idModificador = mRow[KioskOrderItemModifierTable.idModificador],
                    nombre = mRow[KioskOrderItemModifierTable.nombre],
                    precioAdicional = mRow[KioskOrderItemModifierTable.precioAdicional],
                )
            } ?: emptyList()

            KioskOrderItemRecord(
                idPedido = orderId,
                linea = linea,
                idItem = iRow[KioskOrderItemTable.idItem],
                cantidad = iRow[KioskOrderItemTable.cantidad],
                precioUnitario = iRow[KioskOrderItemTable.precioUnitario],
                nota = iRow[KioskOrderItemTable.nota],
                modifiers = mods,
            )
        }

        KioskOrderRecord(
            id = orderRow[KioskOrderTable.id].trim(),
            idDispositivo = orderRow[KioskOrderTable.idDispositivo].trim(),
            numeroPedidoDiario = orderRow[KioskOrderTable.numeroPedidoDiario],
            codigoPedido = orderRow[KioskOrderTable.codigoPedido],
            fecha = orderRow[KioskOrderTable.fecha],
            estado = orderRow[KioskOrderTable.estado],
            modalidad = orderRow[KioskOrderTable.modalidad],
            portamesa = orderRow[KioskOrderTable.portamesa],
            idCliente = orderRow[KioskOrderTable.idCliente].trim(),
            total = orderRow[KioskOrderTable.total],
            quoteExpiraEn = orderRow[KioskOrderTable.quoteExpiraEn],
            pagoReferencia = orderRow[KioskOrderTable.pagoReferencia],
            pagoAutorizacion = orderRow[KioskOrderTable.pagoAutorizacion],
            pagoUltimos4 = orderRow[KioskOrderTable.pagoUltimos4],
            pagoMarca = orderRow[KioskOrderTable.pagoMarca],
            idFactura = orderRow[KioskOrderTable.idFactura],
            motivoRechazo = orderRow[KioskOrderTable.motivoRechazo],
            creadoEn = orderRow[KioskOrderTable.creadoEn],
            actualizadoEn = orderRow[KioskOrderTable.actualizadoEn],
            items = items,
        )
    }

    suspend fun updateOrderPaymentStatus(
        database: Database,
        orderId: String,
        estado: String,
        idFactura: String?,
        pagoReferencia: String?,
        pagoAutorizacion: String?,
        pagoUltimos4: String?,
        pagoMarca: String?,
        motivoRechazo: String?,
    ): Unit = dbQuery(database) {
        val now = LocalDateTime.now()
        KioskOrderTable.update({ KioskOrderTable.id eq orderId }) {
            it[KioskOrderTable.estado] = estado
            if (idFactura != null) it[KioskOrderTable.idFactura] = idFactura
            if (pagoReferencia != null) it[KioskOrderTable.pagoReferencia] = pagoReferencia
            if (pagoAutorizacion != null) it[KioskOrderTable.pagoAutorizacion] = pagoAutorizacion
            if (pagoUltimos4 != null) it[KioskOrderTable.pagoUltimos4] = pagoUltimos4
            if (pagoMarca != null) it[KioskOrderTable.pagoMarca] = pagoMarca
            it[KioskOrderTable.motivoRechazo] = motivoRechazo
            it[KioskOrderTable.actualizadoEn] = now
        }
    }
}
