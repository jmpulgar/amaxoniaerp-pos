package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaDetalleAperturaItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoDevolucionItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoItem
import com.amaxoniaerp.features.caja.domain.CajaInventarioItem
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.isCashSigla
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.pos.data.CajaFormaTable
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
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

internal fun readCajaSecuenciaData(
    countryCode: String,
    idSecuencia: String,
    verifyFacturasTemporales: Boolean,
): CajaSecuenciaData {
    val header = loadCajaSecuenciaHeader(idSecuencia)
    val secuenciaRow = header.row

    val detalleApertura = loadDetalleApertura(idSecuencia)
    val montosPorForma = loadMontosPorForma(countryCode, idSecuencia)
    val formaPagoItems = loadFormaPagoItems(header.idCaja, montosPorForma)
    val montoEntrada = loadMovimientoTotal(idSecuencia, "E")
    val montoSalida = loadMovimientoTotal(idSecuencia, "S")
    appendEntradasSalidasItems(formaPagoItems, montoEntrada, montoSalida)

    val formaPagoDevolucion = loadFormaPagoDevolucion(idSecuencia)
    applyDevolucionesToFormaPago(formaPagoItems, formaPagoDevolucion)

    val totalAnulado = loadTotalAnulado(countryCode, idSecuencia)
    val (totalVentas, cantidadTransacciones) = loadVentasDeSecuencia(countryCode, idSecuencia)
    val inventario = loadInventarioVentas(countryCode, idSecuencia)
    val verificarTemporales =
        if (verifyFacturasTemporales) countFacturasTemporales(countryCode, idSecuencia) else 0

    return buildCajaSecuenciaData(
        CajaSecuenciaContext(
            header = header,
            secuenciaRow = secuenciaRow,
            details =
                CajaSecuenciaDetails(
                    detalleApertura = detalleApertura,
                    formaPagoItems = formaPagoItems,
                    formaPagoDevolucion = formaPagoDevolucion,
                    inventario = inventario,
                ),
            totals =
                CajaSecuenciaTotals(
                    montoEntrada = montoEntrada,
                    montoSalida = montoSalida,
                    totalAnulado = totalAnulado,
                    totalVentas = totalVentas,
                    cantidadTransacciones = cantidadTransacciones,
                    verificarTemporales = verificarTemporales,
                ),
        ),
    )
}

private class CajaCalculatedFinancials(
    val montoEfectivoApertura: Double,
    val montoEfectivoVentasCalc: Double,
    val montoEfectivoTotalCalc: Double,
    val montoOtrosTotalCalc: Double,
    val montoTotalCalc: Double,
    val montoCierreCalc: Double,
)

private class CajaSecuenciaContext(
    val header: CajaSecuenciaHeader,
    val secuenciaRow: ResultRow,
    val details: CajaSecuenciaDetails,
    val totals: CajaSecuenciaTotals,
)

private class CajaSecuenciaDetails(
    val detalleApertura: List<CajaDetalleAperturaItem>,
    val formaPagoItems: MutableList<CajaFormaPagoItem>,
    val formaPagoDevolucion: List<CajaFormaPagoDevolucionItem>,
    val inventario: List<CajaInventarioItem>,
)

private class CajaSecuenciaTotals(
    val montoEntrada: Double,
    val montoSalida: Double,
    val totalAnulado: Double,
    val totalVentas: Double,
    val cantidadTransacciones: Int,
    val verificarTemporales: Int,
)

private fun calculateFinancials(ctx: CajaSecuenciaContext): CajaCalculatedFinancials {
    val secuenciaRow = ctx.secuenciaRow
    val formaPagoItems = ctx.details.formaPagoItems
    val montoEntrada = ctx.totals.montoEntrada
    val montoSalida = ctx.totals.montoSalida
    val totalVentas = ctx.totals.totalVentas
    val totalAnulado = ctx.totals.totalAnulado
    val montoEfectivoVentasCalc =
        formaPagoItems
            .filter { it.id > 0 && isCashSigla(it.siglas) }
            .sumOf { it.monto }
    val montoOtrosTotalCalc =
        formaPagoItems
            .filter { it.id > 0 && !isCashSigla(it.siglas) }
            .sumOf { it.monto }
    val montoEfectivoApertura = secuenciaRow[CajaSecuenciaTable.montoEfectivoApertura].toDouble()
    val montoEfectivoTotalCalc = montoEfectivoApertura + montoEfectivoVentasCalc + montoEntrada - montoSalida
    val montoTotalCalc = montoEfectivoTotalCalc + montoOtrosTotalCalc
    val montoCierreCalc = montoEfectivoApertura + totalVentas + montoEntrada - montoSalida - totalAnulado
    return CajaCalculatedFinancials(
        montoEfectivoApertura = montoEfectivoApertura,
        montoEfectivoVentasCalc = montoEfectivoVentasCalc,
        montoEfectivoTotalCalc = montoEfectivoTotalCalc,
        montoOtrosTotalCalc = montoOtrosTotalCalc,
        montoTotalCalc = montoTotalCalc,
        montoCierreCalc = montoCierreCalc,
    )
}

private fun buildCajaSecuenciaData(ctx: CajaSecuenciaContext): CajaSecuenciaData {
    val fin =
        calculateFinancials(ctx)
    val fechaApertura = ctx.secuenciaRow[CajaSecuenciaTable.fechaApertura]
    val fechaCierre = ctx.secuenciaRow[CajaSecuenciaTable.fechaCierre]
    val fechaCreacion = ctx.secuenciaRow[CajaSecuenciaTable.fechaCreacion]
    val cajaRow = ctx.header.cajaRow

    return CajaSecuenciaData(
        id = ctx.secuenciaRow[CajaSecuenciaTable.idCajaSecuencia],
        idCaja = ctx.header.idCaja,
        idVendedor = ctx.secuenciaRow[CajaSecuenciaTable.idVendedor],
        secuencia = ctx.secuenciaRow[CajaSecuenciaTable.secuencia],
        fechaApertura = formatCajaDateTime(fechaApertura),
        fechaCierre = formatCajaDateTime(fechaCierre),
        fechaCreacion = formatCajaDateTime(fechaCreacion),
        usuario = ctx.secuenciaRow[CajaSecuenciaTable.usuario],
        observacionApertura = ctx.secuenciaRow[CajaSecuenciaTable.observacionApertura],
        observacionCierre = ctx.secuenciaRow[CajaSecuenciaTable.observacionCierre],
        montoEfectivoApertura = fin.montoEfectivoApertura,
        montoEfectivoVentas = fin.montoEfectivoVentasCalc,
        montoEfectivoEntrada = ctx.totals.montoEntrada,
        montoEfectivoSalida = ctx.totals.montoSalida,
        montoEfectivoTotal = fin.montoEfectivoTotalCalc,
        montoEfectivoCierre = fin.montoEfectivoTotalCalc,
        montoEfectivoDiferencia = 0.0,
        montoOtrosTotal = fin.montoOtrosTotalCalc,
        montoOtrosCierre = fin.montoOtrosTotalCalc,
        montoOtrosDiferencia = 0.0,
        montoTotal = fin.montoTotalCalc,
        montoCierre = fin.montoCierreCalc,
        montoDiferencia = 0.0,
        totalVentas = ctx.totals.totalVentas,
        cantidadTransacciones = ctx.totals.cantidadTransacciones,
        numeroCierreFiscal = ctx.secuenciaRow[CajaSecuenciaTable.numeroCierreFiscal],
        serieSucursal = ctx.secuenciaRow[CajaSecuenciaTable.serieSucursal],
        serialFiscal = ctx.secuenciaRow[CajaSecuenciaTable.serialFiscal],
        contabilizado = ctx.secuenciaRow[CajaSecuenciaTable.contabilizado],
        ffechaApertura = fechaApertura?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
        ffechaCierre = fechaCierre?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "",
        cajaCodigo = cajaRow?.get(CajaTable.codCaja),
        caja = cajaRow?.get(CajaTable.caja) ?: cajaRow?.get(CajaTable.descripcion),
        fondoApertura = cajaRow?.get(CajaTable.fondoApertura)?.toDouble() ?: 0.0,
        nombreModelo = cajaRow?.get(CajaTable.impresoraModelo),
        vendedor = ctx.header.vendedorNombre,
        detalleApertura = ctx.details.detalleApertura,
        formaPago = ctx.details.formaPagoItems,
        formaPagoDevolucion = ctx.details.formaPagoDevolucion,
        totalAnulado = ctx.totals.totalAnulado,
        verificarFacturasTemporales = ctx.totals.verificarTemporales,
        inventario = ctx.details.inventario,
    )
}

internal fun formatCajaDateTime(value: LocalDateTime?): String? = value?.format(CAJA_DT_FORMAT)
