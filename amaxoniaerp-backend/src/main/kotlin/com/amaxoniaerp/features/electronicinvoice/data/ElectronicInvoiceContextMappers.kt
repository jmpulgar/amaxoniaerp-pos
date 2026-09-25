package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.ResultRow

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
    val nombreFromCliente =
        buildString {
            append(row.getOrNull(FECientesReadTable.nombre)?.trim().orEmpty())
            val apellido = row.getOrNull(FECientesReadTable.apellido)?.trim().orEmpty()
            if (apellido.isNotBlank()) append(" ").append(apellido)
        }.trim()

    val facturarA = row.getOrNull(FEFacturaReadTable.facturarA)?.trim().orEmpty()
    val nombreCompleto =
        when {
            nombreFromCliente.isNotBlank() -> nombreFromCliente
            facturarA.isNotBlank() -> facturarA
            else -> "CONSUMIDOR FINAL"
        }

    val paisIso = row.getOrNull(paisLocal[FEPaisesReadTable.iso]) ?: "PA"
    val paisExtIso = row.getOrNull(paisExtranjero[FEPaisesReadTable.iso])

    val rawRif =
        row.getOrNull(FECientesReadTable.rif)?.takeIf { it.isNotBlank() }
            ?: row.getOrNull(FEFacturaReadTable.facturarARuc)?.takeIf { it.isNotBlank() }
            ?: ""

    val rawDireccion =
        row.getOrNull(FECientesReadTable.direccion)?.takeIf { it.isNotBlank() }
            ?: row.getOrNull(FEFacturaReadTable.facturarADireccion)?.takeIf { it.isNotBlank() }

    val rawTelefono =
        row.getOrNull(FECientesReadTable.telefonos)?.takeIf { it.isNotBlank() }
            ?: row.getOrNull(FEFacturaReadTable.facturarATelefono)?.takeIf { it.isNotBlank() }

    return FEClienteData(
        tipoClienteFE = row.getOrNull(FETipoClienteReadTable.tipoClienteFE) ?: "02",
        tipoContribuyente = row.getOrNull(FECientesReadTable.tipoContribuyente)?.toString() ?: "1",
        identificacion = rawRif,
        dv = row.getOrNull(FECientesReadTable.dv) ?: "",
        nombre = nombreCompleto,
        codigoUbicacion = row.getOrNull(FECientesReadTable.direccionNivel3),
        telefono = rawTelefono,
        correo = row.getOrNull(FECientesReadTable.email),
        direccion = rawDireccion,
        paisIso = paisIso,
        paisExtranjeroIso = paisExtIso,
    )
}
