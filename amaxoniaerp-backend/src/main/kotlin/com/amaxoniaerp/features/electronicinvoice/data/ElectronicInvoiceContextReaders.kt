package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

private val readersLog = LoggerFactory.getLogger("ElectronicInvoiceContextReaders")

internal const val SQL_DATETIME_TEXT_LENGTH = 19

/** Cabecera de factura + aliases de países usados en el JOIN (mismas instancias para lectura). */
internal class FacturaHeaderRow(
    val row: ResultRow,
    val paisLocal: Alias<FEPaisesReadTable>,
    val paisExtranjero: Alias<FEPaisesReadTable>,
)

/** Lee la cabecera de factura con JOIN a clientes, tipo_cliente y paises. */
internal fun loadFacturaHeaderRow(invoiceId: String): FacturaHeaderRow {
    val paisLocal = paisLocalAlias()
    val paisExtranjero = paisExtranjeroAlias()

    val facturaRow =
        FEFacturaReadTable
            .join(FECientesReadTable, JoinType.LEFT, FEFacturaReadTable.idCliente, FECientesReadTable.idCliente)
            .join(
                FETipoClienteReadTable,
                JoinType.LEFT,
                FECientesReadTable.codTipoCliente,
                FETipoClienteReadTable.codTipoCliente,
            ).join(paisLocal, JoinType.LEFT, FECientesReadTable.pais, paisLocal[FEPaisesReadTable.id])
            .join(
                paisExtranjero,
                JoinType.LEFT,
                FECientesReadTable.paisExtranjero,
                paisExtranjero[FEPaisesReadTable.id],
            ).selectAll()
            .where { FEFacturaReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?: throw FEInvoiceNotFoundException("Factura no encontrada: $invoiceId")
    return FacturaHeaderRow(facturaRow, paisLocal, paisExtranjero)
}

internal fun paisLocalAlias(): Alias<FEPaisesReadTable> = FEPaisesReadTable.alias("pais_local")

internal fun paisExtranjeroAlias(): Alias<FEPaisesReadTable> = FEPaisesReadTable.alias("pais_ext")

internal fun loadDetallesFE(invoiceId: String): List<FEDetalleData> =
    FEFacturaDetalleReadTable
        .join(FEItemReadTable, JoinType.LEFT, FEFacturaDetalleReadTable.idItem, FEItemReadTable.idItem)
        .join(
            FEUnidadEmpaquesReadTable,
            JoinType.LEFT,
            FEItemReadTable.unidadMedida,
            FEUnidadEmpaquesReadTable.codUnidad,
        ).selectAll()
        .where { FEFacturaDetalleReadTable.idFactura eq invoiceId }
        .map { row ->
            val (codigoCPBS, codigoCPBSAbrev) =
                resolverCpbs(
                    idFamiliaDetalle = row.getOrNull(FEFacturaDetalleReadTable.idFamilia),
                    idSegmentoDetalle = row.getOrNull(FEFacturaDetalleReadTable.idSegmento),
                    idFamiliaGobItem = row.getOrNull(FEItemReadTable.idFamiliaGob),
                    idSegmentoGobItem = row.getOrNull(FEItemReadTable.idSegmentoGob),
                )
            FEDetalleData(
                descripcion = row[FEFacturaDetalleReadTable.itemDescripcion],
                codigo = row[FEFacturaDetalleReadTable.itemCodigo],
                unidadMedida =
                    row
                        .getOrNull(FEUnidadEmpaquesReadTable.simbolo)
                        ?.takeIf { it.isNotBlank() } ?: "und",
                codigoCPBS = codigoCPBS,
                codigoCPBSAbrev = codigoCPBSAbrev,
                cantidad = row[FEFacturaDetalleReadTable.itemCantidad].toDouble(),
                precioSinIva = row[FEFacturaDetalleReadTable.itemPrecioSinIva].toDouble(),
                montoDescuento = row[FEFacturaDetalleReadTable.itemMontoDescuento].toDouble(),
                piva = row[FEFacturaDetalleReadTable.itemPiva].toDouble(),
                totalSinIva = row[FEFacturaDetalleReadTable.itemTotalSinIva].toDouble(),
                totalConIva = row[FEFacturaDetalleReadTable.itemTotalConIva].toDouble(),
                porcentajeIsc = row[FEFacturaDetalleReadTable.porcentajeIsc]?.toDouble(),
                importeIsc = row[FEFacturaDetalleReadTable.importeIsc]?.toDouble(),
                idOti = row[FEFacturaDetalleReadTable.idOti],
                importeOti = row[FEFacturaDetalleReadTable.importeOti]?.toDouble(),
            )
        }

/**
 * Q8: CPBS fiscal de un ítem. Fuente primaria `factura_detalle.id_familia` /
 * `id_segmento`; si la venta no los guardó (facturas del POS), fallback a
 * `item.id_familia_gob` / `id_segmento_gob`.
 */
internal fun resolverCpbs(
    idFamiliaDetalle: Int?,
    idSegmentoDetalle: Int?,
    idFamiliaGobItem: Int?,
    idSegmentoGobItem: Int?,
): Pair<String?, String?> =
    (idFamiliaDetalle?.toString() ?: idFamiliaGobItem?.toString()) to
        (idSegmentoDetalle?.toString() ?: idSegmentoGobItem?.toString())

internal fun resolveCodigoSucursalYPuntoFacturacion(
    cajaId: String,
    idSucursal: Int,
    codigoSucursalFallback: String,
    puntoFacturacionFallback: String,
): Pair<String, String> {
    // 1. Intentar leer de la caja
    val cajaRow =
        FECajaReadTable
            .selectAll()
            .where { FECajaReadTable.id eq cajaId }
            .limit(1)
            .firstOrNull()

    if (cajaRow == null) {
        readersLog.warn("[FE] No se encontró caja con id=$cajaId")
    }

    val codigoFromCaja =
        cajaRow
            ?.get(FECajaReadTable.codigoSucursalEmisor)
            ?.takeIf { it.isNotBlank() }
    val puntoFromCaja =
        cajaRow
            ?.get(FECajaReadTable.puntoFacturacionFiscal)
            ?.takeIf { it.isNotBlank() }

    readersLog.info("[FE] caja lookup: codigoFromCaja=$codigoFromCaja puntoFromCaja=$puntoFromCaja")

    // 2. Si la caja no lo tiene, intentar desde sucursal
    val codigoFromSucursal =
        if (codigoFromCaja == null) {
            FESucursalReadTable
                .select(FESucursalReadTable.codigoSucursalEmisor)
                .where { FESucursalReadTable.id eq idSucursal }
                .limit(1)
                .firstOrNull()
                ?.get(FESucursalReadTable.codigoSucursalEmisor)
                ?.takeIf { it.isNotBlank() }
                .also { readersLog.info("[FE] sucursal lookup id=$idSucursal -> codigoSucursalEmisor=$it") }
        } else {
            null
        }

    // 3. Fallback a parametros_generales
    val codigoFinal = codigoFromCaja ?: codigoFromSucursal ?: codigoSucursalFallback
    val puntoFinal = puntoFromCaja ?: puntoFacturacionFallback

    if (codigoFromCaja == null && codigoFromSucursal == null) {
        readersLog.warn(
            "[FE] codigoSucursalEmisor no encontrado ni en caja ni en sucursal, " +
                "usando fallback de parametros_generales: $codigoSucursalFallback",
        )
    }
    if (puntoFromCaja == null) {
        readersLog.warn(
            "[FE] puntoFacturacionFiscal no encontrado en caja, " +
                "usando fallback de parametros_generales: $puntoFacturacionFallback",
        )
    }

    return codigoFinal to puntoFinal
}

internal fun resolveNumeroDocumentoFiscal(): String {
    val row =
        FECorrelativosTable
            .selectAll()
            .where { FECorrelativosTable.campo eq "numeroDocumentoFiscal" }
            .limit(1)
            .firstOrNull()

    if (row == null) {
        readersLog.warn("No se encontró registro de correlativos para 'numeroDocumentoFiscal', usando 1")
        return "1"
    }

    return (row[FECorrelativosTable.contador] + 1).toString()
}

/**
 * Q1: en un reintento la factura reutiliza su numeroDocumentoFiscal
 * persistido (el CUFE es determinista y la DGI deduplica sin emitir 1513);
 * el correlativo sólo se consume cuando la factura nunca recibió número.
 * [siguienteCorrelativo] es lazy para no leer `correlativos` de más.
 */
internal fun resolverNumeroDocumentoFiscal(
    persistido: String?,
    siguienteCorrelativo: () -> String,
): String = persistido?.takeIf { it.isNotBlank() } ?: siguienteCorrelativo()

internal fun String?.safeIntOrZero(): Int =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toIntOrNull()
        ?: 0

internal fun String?.safeDoubleOrZero(): Double =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toDoubleOrNull()
        ?: 0.0
