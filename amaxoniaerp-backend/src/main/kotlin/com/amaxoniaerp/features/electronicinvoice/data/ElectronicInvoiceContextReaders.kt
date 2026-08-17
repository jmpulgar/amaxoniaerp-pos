package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException
import com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

private val readersLog = LoggerFactory.getLogger("ElectronicInvoiceContextReaders")

private const val SQL_DATETIME_TEXT_LENGTH = 19

/** Cabecera de factura + aliases de paÃ­ses usados en el JOIN (mismas instancias para lectura). */
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

internal fun mapConfig(row: ResultRow): FEConfigData {
    val tokenEmpresa = requireConfigColumn(row[FEParametrosReadTable.tokenEmpresa], "token_empresa")
    val tokenPassword = requireConfigColumn(row[FEParametrosReadTable.tokenPassword], "token_password")
    val apiTheFactoryHka = requireConfigColumn(row[FEParametrosReadTable.api_thefactoryhka], "api_thefactoryhka")

    return FEConfigData(
        tokenEmpresa = tokenEmpresa,
        tokenPassword = tokenPassword,
        apiTheFactoryHka = apiTheFactoryHka.trimEnd('/'),
        tipoEmision = row[FEParametrosReadTable.tipoEmision] ?: "01",
        destinoOperacion = row[FEParametrosReadTable.destinoOperacion] ?: "1",
        procesoGeneracion = row[FEParametrosReadTable.procesoGeneracion] ?: "01",
        codigoSucursalEmisorFallback = row[FEParametrosReadTable.codigoSucursalEmisor] ?: "0000",
        puntoFacturacionFiscalFallback = row[FEParametrosReadTable.puntoFacturacionFiscal] ?: "001",
        fechaInicioContingencia = row[FEParametrosReadTable.fechaInicioContingencia],
        motivoContingencia = row[FEParametrosReadTable.motivoContingencia],
        tipoFacturacion = row[FEParametrosReadTable.tipoFacturacion],
    )
}

private fun requireConfigColumn(
    value: String?,
    name: String,
): String = value ?: throw FEConfigurationException("$name no configurado en parametros_generales")

internal fun mapFactura(
    row: ResultRow,
    numeroDocFiscal: String,
): FEFacturaData =
    FEFacturaData(
        idFactura = row[FEFacturaReadTable.idFactura],
        codFactura = row[FEFacturaReadTable.codFactura],
        numeroDocumentoFiscal = numeroDocFiscal,
        fechaFactura = row[FEFacturaReadTable.fechaFactura],
        tipoDocumento = row[FEFacturaReadTable.tipoDocumento] ?: "01",
        naturalezaOperacion = row[FEFacturaReadTable.naturalezaOperacion] ?: "01",
        tipoOperacion = row[FEFacturaReadTable.tipoOperacion] ?: "1",
        formatoCAFE = row[FEFacturaReadTable.formatoCAFE] ?: "1",
        entregaCAFE = row[FEFacturaReadTable.entregaCAFE] ?: "1",
        envioContenedor = row[FEFacturaReadTable.envioContenedor] ?: "1",
        tipoVenta = row[FEFacturaReadTable.tipoVenta] ?: "1",
        tipoFactura = row[FEFacturaReadTable.tipoFactura],
        observacion = row[FEFacturaReadTable.observacion],
        montoItemsFactura = row[FEFacturaReadTable.montoItemsFactura].toDouble(),
        ivaTotalFactura = row[FEFacturaReadTable.ivaTotalFactura].toDouble(),
        totalTotalFactura = row[FEFacturaReadTable.totalTotalFactura].toDouble(),
        totalizarDescuentoGlobal = row[FEFacturaReadTable.totalizarDescuentoGlobal].toDouble(),
        cajaId = row[FEFacturaReadTable.idCaja],
    )

internal fun mapCliente(
    row: ResultRow,
    paisLocal: Alias<FEPaisesReadTable>,
    paisExtranjero: Alias<FEPaisesReadTable>,
): FEClienteData {
    val nombreCompleto =
        buildString {
            append(row.getOrNull(FECientesReadTable.nombre)?.trim().orEmpty())
            val apellido = row.getOrNull(FECientesReadTable.apellido)?.trim().orEmpty()
            if (apellido.isNotBlank()) append(" ").append(apellido)
        }.ifBlank { "CONSUMIDOR FINAL" }

    val paisIso = row.getOrNull(paisLocal[FEPaisesReadTable.iso]) ?: "PA"
    val paisExtIso = row.getOrNull(paisExtranjero[FEPaisesReadTable.iso])

    return FEClienteData(
        tipoClienteFE = row.getOrNull(FETipoClienteReadTable.tipoClienteFE) ?: "02",
        tipoContribuyente = row.getOrNull(FECientesReadTable.tipoContribuyente)?.toString() ?: "1",
        identificacion = row.getOrNull(FECientesReadTable.rif) ?: "",
        dv = row.getOrNull(FECientesReadTable.dv) ?: "",
        nombre = nombreCompleto,
        codigoUbicacion = row.getOrNull(FECientesReadTable.direccionNivel3),
        telefono = row.getOrNull(FECientesReadTable.telefonos),
        correo = row.getOrNull(FECientesReadTable.email),
        direccion = row.getOrNull(FECientesReadTable.direccion),
        paisIso = paisIso,
        paisExtranjeroIso = paisExtIso,
    )
}

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
            FEDetalleData(
                descripcion = row[FEFacturaDetalleReadTable.itemDescripcion],
                codigo = row[FEFacturaDetalleReadTable.itemCodigo],
                unidadMedida =
                    row
                        .getOrNull(FEUnidadEmpaquesReadTable.simbolo)
                        ?.takeIf { it.isNotBlank() } ?: "und",
                codigoCPBS = row.getOrNull(FEFacturaDetalleReadTable.idFamilia)?.toString(),
                codigoCPBSAbrev = row.getOrNull(FEFacturaDetalleReadTable.idSegmento)?.toString(),
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
        readersLog.warn("[FE] No se encontrÃ³ caja con id=$cajaId")
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
        readersLog.warn("No se encontrÃ³ registro de correlativos para 'numeroDocumentoFiscal', usando 1")
        return "1"
    }

    return (row[FECorrelativosTable.contador] + 1).toString()
}

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
