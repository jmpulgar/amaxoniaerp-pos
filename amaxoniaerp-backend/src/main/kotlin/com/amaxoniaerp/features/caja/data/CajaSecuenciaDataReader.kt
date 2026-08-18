package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaDetalleAperturaItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoDevolucionItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoItem
import com.amaxoniaerp.features.caja.domain.CajaInventarioItem
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.SalesFacturaDetalleTable
import com.amaxoniaerp.features.sales.data.SalesFacturaTableFactory
import com.amaxoniaerp.features.sales.data.SalesStockTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val CAJA_DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** Contexto de la secuencia leído de la fila cabecera + caja + vendedor. */
internal class CajaSecuenciaHeader(
    val row: ResultRow,
    val idCaja: String,
    val cajaRow: ResultRow?,
    val vendedorNombre: String?,
)

internal fun loadCajaSecuenciaHeader(idSecuencia: String): CajaSecuenciaHeader {
    val secuenciaRow =
        CajaSecuenciaTable
            .selectAll()
            .where { CajaSecuenciaTable.idCajaSecuencia eq idSecuencia }
            .limit(1)
            .firstOrNull()
            ?: error("Secuencia de caja no encontrada")

    val idCaja = secuenciaRow[CajaSecuenciaTable.idCaja]
    val idVendedor = secuenciaRow[CajaSecuenciaTable.idVendedor]

    val cajaRow =
        CajaTable
            .select(
                CajaTable.idCaja,
                CajaTable.codCaja,
                CajaTable.descripcion,
                CajaTable.caja,
                CajaTable.fondoApertura,
                CajaTable.impresoraModelo,
            ).where { CajaTable.idCaja eq idCaja }
            .limit(1)
            .firstOrNull()

    val vendedorNombre =
        idVendedor?.let { vendedorId ->
            VendedorTable
                .select(VendedorTable.nombre)
                .where { VendedorTable.idVendedor eq vendedorId }
                .limit(1)
                .firstOrNull()
                ?.get(VendedorTable.nombre)
        }

    return CajaSecuenciaHeader(secuenciaRow, idCaja, cajaRow, vendedorNombre)
}

internal fun loadDetalleApertura(idSecuencia: String): List<CajaDetalleAperturaItem> =
    CajaDetalleAperturaTable
        .leftJoin(MonedaDenominacionTable, { idMonedaDenominacion }, { MonedaDenominacionTable.id })
        .selectAll()
        .where { CajaDetalleAperturaTable.idCajaSecuencia eq idSecuencia }
        .map { row ->
            CajaDetalleAperturaItem(
                id = row[CajaDetalleAperturaTable.idDetalleApertura],
                idSecuencia = row[CajaDetalleAperturaTable.idCajaSecuencia],
                idMonedaDenominacion = row[CajaDetalleAperturaTable.idMonedaDenominacion],
                cantidad = row[CajaDetalleAperturaTable.cantidad],
                valor = row[CajaDetalleAperturaTable.valor].toDouble(),
                monto = row[CajaDetalleAperturaTable.monto].toDouble(),
                denominacion = row[MonedaDenominacionTable.denominacion],
            )
        }

internal fun loadMontosPorForma(
    countryCode: String,
    idSecuencia: String,
): Map<Int?, Double> {
    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
    return cajaNuevaDetalleTable
        .join(cajaNuevaTable, JoinType.INNER, cajaNuevaDetalleTable.cajaId, cajaNuevaTable.cajaId)
        .select(cajaNuevaDetalleTable.idFormaPago, cajaNuevaDetalleTable.monto)
        .where {
            (cajaNuevaTable.idCajaSecuencia eq idSecuencia) and
                (cajaNuevaTable.status neq CajaStatus.Anulada)
        }.groupBy { it[cajaNuevaDetalleTable.idFormaPago] }
        .mapValues { (_, rows) -> rows.sumOf { it[cajaNuevaDetalleTable.monto]?.toDouble() ?: 0.0 } }
}

internal fun loadFormaPagoItems(
    countryCode: String,
    idCaja: String,
    montosPorForma: Map<Int?, Double>,
): MutableList<CajaFormaPagoItem> {
    val formasActivas =
        CajaFormaTable
            .leftJoin(CajaFormaPagoTable, { idFormaPago }, { CajaFormaPagoTable.idFormaPago })
            .leftJoin(CajaFormaPagoGrupoTable, { CajaFormaPagoTable.grupo }, { CajaFormaPagoGrupoTable.id })
            .selectAll()
            .where { (CajaFormaTable.idCaja eq idCaja) and (CajaFormaTable.activo eq 1) }
            .associateBy { it[CajaFormaTable.idFormaPago] }

    val formaPagoIds = (montosPorForma.keys.filterNotNull() + formasActivas.keys).distinct()

    val catalogoFormas =
        if (formaPagoIds.isEmpty()) {
            emptyMap()
        } else {
            CajaFormaPagoTable
                .leftJoin(CajaFormaPagoGrupoTable, { grupo }, { CajaFormaPagoGrupoTable.id })
                .selectAll()
                .where { CajaFormaPagoTable.idFormaPago inList formaPagoIds }
                .associateBy { it.getOrNull(CajaFormaPagoTable.idFormaPago) }
                .filterKeys { it != null }
                .mapKeys { it.key!! }
        }

    return formaPagoIds
        .mapNotNull { idForma ->
            val row = formasActivas[idForma] ?: catalogoFormas[idForma] ?: return@mapNotNull null
            CajaFormaPagoItem(
                id = idForma,
                formaPago = row.getOrNull(CajaFormaPagoTable.descripcion),
                siglas = row.getOrNull(CajaFormaPagoTable.siglas),
                grupo = row.getOrNull(CajaFormaPagoTable.grupo),
                imagen = row.getOrNull(CajaFormaPagoTable.imagen)?.takeIf { it.isNotBlank() },
                idCajaTpConcepto = row.getOrNull(CajaFormaPagoTable.idCajaTpConcepto),
                tipoMoneda = row.getOrNull(CajaFormaPagoTable.tipoMoneda),
                estatus = row.getOrNull(CajaFormaPagoTable.activo) ?: 0,
                grupoNombre = row.getOrNull(CajaFormaPagoGrupoTable.grupo),
                grupoImagen = row.getOrNull(CajaFormaPagoGrupoTable.imagen),
                grupoOrden = row.getOrNull(CajaFormaPagoGrupoTable.orden),
                grupoActivo = row.getOrNull(CajaFormaPagoGrupoTable.activo),
                monto = montosPorForma[idForma] ?: 0.0,
            )
        }.toMutableList()
}

internal fun loadMovimientoTotal(
    idSecuencia: String,
    tipo: String,
): Double =
    runCatching {
        CajaMovimientoTable
            .select(CajaMovimientoTable.total)
            .where {
                (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                    (CajaMovimientoTable.tipo eq tipo)
            }.sumOf { it[CajaMovimientoTable.total].toDouble() }
    }.getOrDefault(0.0)

internal fun appendEntradasSalidasItems(
    formaPagoItems: MutableList<CajaFormaPagoItem>,
    montoEntrada: Double,
    montoSalida: Double,
) {
    if (montoEntrada != 0.0) {
        formaPagoItems +=
            CajaFormaPagoItem(
                id = -100,
                formaPago = "ENTRADAS",
                siglas = "E",
                monto = montoEntrada,
                estatus = 1,
            )
    }
    if (montoSalida != 0.0) {
        formaPagoItems +=
            CajaFormaPagoItem(
                id = -101,
                formaPago = "SALIDAS",
                siglas = "S",
                monto = montoSalida,
                estatus = 1,
            )
    }
}

internal fun loadFormaPagoDevolucion(idSecuencia: String): List<CajaFormaPagoDevolucionItem> =
    runCatching {
        val devolucionRows =
            FacturaDevolucionTable
                .selectAll()
                .where { FacturaDevolucionTable.idCajaSecuencia eq idSecuencia }
                .toList()

        val devolucionesPorForma =
            devolucionRows
                .groupBy { it[FacturaDevolucionTable.idFormaPago] ?: RETURN_PAYMENT_FORM_FALLBACK }
                .mapValues { (_, rows) ->
                    rows.sumOf { row ->
                        row[FacturaDevolucionTable.totalTotalFactura]?.toDouble() ?: 0.0
                    }
                }

        if (devolucionesPorForma.isEmpty()) {
            emptyList()
        } else {
            val ids = devolucionesPorForma.keys.toList()
            val meta =
                CajaFormaPagoTable
                    .select(
                        CajaFormaPagoTable.idFormaPago,
                        CajaFormaPagoTable.siglas,
                        CajaFormaPagoTable.descripcion,
                    ).where { CajaFormaPagoTable.idFormaPago inList ids }
                    .associateBy { it[CajaFormaPagoTable.idFormaPago] }

            devolucionesPorForma.map { (idForma, monto) ->
                val row = meta[idForma]
                CajaFormaPagoDevolucionItem(
                    idFormaPago = idForma,
                    siglas = row?.get(CajaFormaPagoTable.siglas) ?: if (idForma == 30) "NC" else null,
                    descripcion =
                        row?.get(CajaFormaPagoTable.descripcion) ?: if (idForma ==
                            30
                        ) {
                            "NOTA DE CREDITO"
                        } else {
                            null
                        },
                    monto = monto,
                )
            }
        }
    }.getOrDefault(emptyList())

internal fun applyDevolucionesToFormaPago(
    formaPagoItems: MutableList<CajaFormaPagoItem>,
    formaPagoDevolucion: List<CajaFormaPagoDevolucionItem>,
) {
    formaPagoDevolucion.forEach { devolucion ->
        val index = formaPagoItems.indexOfFirst { it.id == devolucion.idFormaPago }
        if (index >= 0) {
            val current = formaPagoItems[index]
            formaPagoItems[index] = current.copy(monto = current.monto + devolucion.monto)
        } else {
            formaPagoItems +=
                CajaFormaPagoItem(
                    id = devolucion.idFormaPago,
                    formaPago = devolucion.descripcion ?: "NOTA DE CREDITO",
                    siglas = devolucion.siglas ?: "NC",
                    estatus = 1,
                    monto = devolucion.monto,
                )
        }
    }
}

internal fun loadVentasDeSecuencia(
    countryCode: String,
    idSecuencia: String,
): Pair<Double, Int> {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    val facturasValidas =
        facturaTable
            .select(facturaTable.idFactura, facturaTable.totalTotalFactura, facturaTable.codEstatus)
            .where { facturaTable.idCajaSecuencia eq idSecuencia }
            .filter { row -> (row[facturaTable.codEstatus] ?: 0) != ANNULLED_INVOICE_STATUS }
    return facturasValidas.sumOf { it[facturaTable.totalTotalFactura].toDouble() } to facturasValidas.size
}

internal fun loadTotalAnulado(
    countryCode: String,
    idSecuencia: String,
): Double {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    return facturaTable
        .select(facturaTable.totalTotalFactura)
        .where {
            (facturaTable.idCajaSecuencia eq idSecuencia) and
                (facturaTable.codEstatus eq ANNULLED_INVOICE_STATUS)
        }.sumOf { it[facturaTable.totalTotalFactura].toDouble() }
}

internal fun countFacturasTemporales(
    countryCode: String,
    idSecuencia: String,
): Int {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    return facturaTable
        .select(facturaTable.formaPago)
        .where {
            (facturaTable.idCajaSecuencia eq idSecuencia) and
                (facturaTable.codEstatus eq 1)
        }.count { row -> !row[facturaTable.formaPago].equals("credito", ignoreCase = true) }
}

internal fun loadInventarioVentas(
    countryCode: String,
    idSecuencia: String,
): List<CajaInventarioItem> {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    val facturasValidas =
        facturaTable
            .select(facturaTable.idFactura)
            .where { facturaTable.idCajaSecuencia eq idSecuencia }
            .filter { row -> (row[facturaTable.codEstatus] ?: 0) != ANNULLED_INVOICE_STATUS }
    val facturaIds = facturasValidas.map { it[facturaTable.idFactura] }
    val detalleVentas =
        if (facturaIds.isEmpty()) {
            emptyList()
        } else {
            SalesFacturaDetalleTable
                .select(
                    SalesFacturaDetalleTable.idItem,
                    SalesFacturaDetalleTable.itemCodigo,
                    SalesFacturaDetalleTable.itemDescripcion,
                    SalesFacturaDetalleTable.itemCantidadTotal,
                ).where { SalesFacturaDetalleTable.idFactura inList facturaIds }
                .toList()
        }
    val itemIds = detalleVentas.map { it[SalesFacturaDetalleTable.idItem] }.distinct()
    val stockDisponible =
        if (itemIds.isEmpty()) {
            emptyMap()
        } else {
            SalesStockTable
                .select(SalesStockTable.idItem, SalesStockTable.cantidad)
                .where { SalesStockTable.idItem inList itemIds }
                .groupBy { it[SalesStockTable.idItem] }
                .mapValues { (_, rows) -> rows.sumOf { it[SalesStockTable.cantidad].toDouble() } }
        }
    return detalleVentas
        .groupBy { it[SalesFacturaDetalleTable.idItem] }
        .map { (itemId, rows) ->
            val first = rows.first()
            val sold = rows.sumOf { it[SalesFacturaDetalleTable.itemCantidadTotal].toDouble() }
            val available = stockDisponible[itemId] ?: 0.0
            CajaInventarioItem(
                codigo = first[SalesFacturaDetalleTable.itemCodigo].ifBlank { itemId.toString() },
                descripcion =
                    first[SalesFacturaDetalleTable.itemDescripcion].ifBlank { "Producto $itemId" },
                existenciaInicial = available + sold,
                cantidadVendida = sold,
                existenciaDisponible = available,
            )
        }.sortedBy { it.descripcion }
}

internal fun formatCajaDateTime(value: LocalDateTime?): String? = value?.format(CAJA_DT_FORMAT)
