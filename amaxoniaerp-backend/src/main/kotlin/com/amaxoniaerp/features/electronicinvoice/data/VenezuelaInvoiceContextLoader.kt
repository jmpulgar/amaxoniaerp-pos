package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import com.amaxoniaerp.features.electronicinvoice.domain.VECajaData
import com.amaxoniaerp.features.electronicinvoice.domain.VECompradorData
import com.amaxoniaerp.features.electronicinvoice.domain.VEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.VEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.VEFormaPagoData
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val loaderLog = LoggerFactory.getLogger("VenezuelaInvoiceContextLoader")

// ─── Entornos The Factory HKA Venezuela (FASE 1.1 cleanup) ─────────
// La URL base del PAC NO se persiste en la base del tenant. Se deriva
// por configuración de aplicación desde `parametros_generales.tipo_entorno_ve`.

/** Código de entorno demo en `parametros_generales.tipo_entorno_ve`. */
private const val ENV_DEMO = 0

/** Código de entorno producción en `parametros_generales.tipo_entorno_ve`. */
private const val ENV_PROD = 1

/** URL base The Factory HKA Venezuela — demo. */
private const val URL_BASE_DEMO = "https://demoemission.thefactoryhka.com.ve/api"

/** URL base The Factory HKA Venezuela — producción. */
private const val URL_BASE_PROD = "https://emision.thefactoryhka.com.ve/api"

internal data class FacturaCargada(
    val facturaData: VEFacturaData,
    val idCaja: String,
    val idSucursal: Int,
    val idClienteComprador: String?,
)

internal fun loadFactura(invoiceId: String): FacturaCargada {
    val row =
        VEFacturaReadTable
            .selectAll()
            .where { VEFacturaReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?: throw com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException(
                "Factura no encontrada: $invoiceId",
            )

    val factura =
        VEFacturaData(
            idFactura = row[VEFacturaReadTable.idFactura],
            codFactura = row[VEFacturaReadTable.codFactura],
            numeroDocumentoFiscal = row[VEFacturaReadTable.numeroDocumentoFiscal],
            numeroControlThka = row[VEFacturaReadTable.numeroControlThka],
            tipoDocumento = row[VEFacturaReadTable.tipoDocumento] ?: "01",
            fechaFactura = row[VEFacturaReadTable.fechaFactura],
            fechaCreacion = row[VEFacturaReadTable.fechaCreacion],
            facturarANombre = row[VEFacturaReadTable.facturarANombre],
            facturarARuc = row[VEFacturaReadTable.facturarARuc],
            facturarADireccion = row[VEFacturaReadTable.facturarADireccion],
            facturarATelefono = row[VEFacturaReadTable.facturarATelefono],
            totalTotalFactura = row[VEFacturaReadTable.totalTotalFactura],
            ivaTotalFactura = row[VEFacturaReadTable.ivaTotalFactura],
            descuentosItemFactura = row[VEFacturaReadTable.descuentosItemFactura],
            totalizarBaseImponible = row[VEFacturaReadTable.totalizarBaseImponible],
            totalizarMontoIva = row[VEFacturaReadTable.totalizarMontoIva],
            totalizarTotalGeneral = row[VEFacturaReadTable.totalizarTotalGeneral],
            montoItemsFactura = row[VEFacturaReadTable.montoItemsFactura],
            multiMoneda = row[VEFacturaReadTable.multiMoneda],
            tasa = row[VEFacturaReadTable.tasa].toBigDecimal(),
            monedaBase = row[VEFacturaReadTable.monedaBase],
            abrMonedaBase = row[VEFacturaReadTable.abrMonedaBase],
            monedaSecundaria = row[VEFacturaReadTable.monedaSecundaria],
            abrMonedaSecundaria = row[VEFacturaReadTable.abrMonedaSecundaria],
        )
    return FacturaCargada(
        facturaData = factura,
        idCaja = row[VEFacturaReadTable.idCaja],
        idSucursal = row[VEFacturaReadTable.idSucursal],
        idClienteComprador = row[VEFacturaReadTable.idCliente],
    )
}

/**
 * Traduce [VEParametrosReadTable.tipoEntornoVe] a la URL base del PAC
 * Venezuela (FASE 1.1 — cleanup).
 *
 * - `0` → entorno demo (`demoemision.thefactoryhka.com.ve`).
 * - `1` → entorno producción (`emision.thefactoryhka.com.ve`).
 * - cualquier otro valor → [FEConfigurationException] para evitar usar
 *   accidentalmente el entorno equivocado.
 */
private fun baseUrlParaEntorno(tipoEntorno: Int): String =
    when (tipoEntorno) {
        ENV_DEMO -> URL_BASE_DEMO
        ENV_PROD -> URL_BASE_PROD
        else -> throw FEConfigurationException(
            "tipo_entorno_ve=$tipoEntorno inválido. Use 0=demo, 1=producción.",
        )
    }

private fun requireConfigValue(
    value: String?,
    errorMessage: String,
): String = value ?: throw FEConfigurationException(errorMessage)

internal fun loadConfig(): VEConfigData {
    val row =
        VEParametrosReadTable
            .selectAll()
            .orderBy(VEParametrosReadTable.codEmpresa)
            .limit(1)
            .firstOrNull()
            ?: throw FEConfigurationException("parametros_generales sin filas para FE VE")

    val tipoFact = row[VEParametrosReadTable.tipoFacturacion]
    val tokenE = requireConfigValue(row[VEParametrosReadTable.tokenEmpresa], "Falta token_empresa (FE VE)")
    val tokenP = requireConfigValue(row[VEParametrosReadTable.tokenPassword], "Falta token_password (FE VE)")
    val tipoEntorno = row[VEParametrosReadTable.tipoEntornoVe]
    val rif = requireConfigValue(row[VEParametrosReadTable.rif], "Falta rif en parametros_generales (FE VE)")

    // FASE 1.1 (cleanup): baseUrl se deriva por configuración de aplicación
    // desde `tipo_entorno_ve` (0=demo, 1=producción). NO se lee de la base
    // del tenant porque la columna `api_thefactoryhka` NO existe en el
    // esquema real. Ver [baseUrlParaEntorno].
    val baseUrl = baseUrlParaEntorno(tipoEntorno)

    // Log sin secretos.
    loaderLog.info(
        "[VE-FE] config cod_empresa={} tipo_facturacion={} tipo_entorno_ve={} igtf={} baseUrl host={}",
        row[VEParametrosReadTable.codEmpresa],
        tipoFact,
        tipoEntorno,
        row[VEParametrosReadTable.igtf],
        runCatching { java.net.URI(baseUrl).host }.getOrDefault("?"),
    )

    return VEConfigData(
        tipoFacturacion = tipoFact,
        tipoEntornoVe = tipoEntorno,
        tokenEmpresa = tokenE,
        tokenPassword = tokenP,
        baseUrl = baseUrl,
        rif = rif,
        nombreEmpresa = row[VEParametrosReadTable.nombreEmpresa],
        direccion = row[VEParametrosReadTable.direccion],
        telefonos = row[VEParametrosReadTable.telefonos],
        igtf = row[VEParametrosReadTable.igtf] ?: BigDecimal.ZERO,
        procesoGeneracion = row[VEParametrosReadTable.procesoGeneracion] ?: "1",
        tipoEmision = row[VEParametrosReadTable.tipoEmision] ?: "01",
        codigoSucursalEmisorFallback = row[VEParametrosReadTable.codigoSucursalEmisor] ?: "0000",
        puntoFacturacionFiscalFallback = row[VEParametrosReadTable.puntoFacturacionFiscal] ?: "001",
    )
}

internal fun loadComprador(
    idClienteComprador: String?,
    factura: VEFacturaData,
): VECompradorData {
    if (idClienteComprador.isNullOrBlank()) {
        // Sin cliente asociado: usar facturar_a* como fallback nominal.
        return consumidorFinalFallback(factura)
    }
    val row =
        VEClientesReadTable
            .selectAll()
            .where { VEClientesReadTable.idCliente eq idClienteComprador }
            .limit(1)
            .firstOrNull()
    return if (row == null) {
        consumidorFinalFallback(factura)
    } else {
        VECompradorData(
            nombreRazonSocial = row[VEClientesReadTable.nombre].ifBlank { factura.facturarANombre },
            rif = row[VEClientesReadTable.rif].ifBlank { factura.facturarARuc },
            direccion = row[VEClientesReadTable.direccion].takeIf { it.isNotBlank() },
            telefono = row[VEClientesReadTable.telefonos].takeIf { it.isNotBlank() },
            email = row[VEClientesReadTable.email].takeIf { it.isNotBlank() },
        )
    }
}

private fun consumidorFinalFallback(factura: VEFacturaData): VECompradorData =
    VECompradorData(
        nombreRazonSocial = factura.facturarANombre.ifBlank { "CONSUMIDOR FINAL" },
        rif = factura.facturarARuc.ifBlank { "V000000000" },
        direccion = factura.facturarADireccion.takeIf { it.isNotBlank() },
        telefono = factura.facturarATelefono.takeIf { it.isNotBlank() },
        email = null,
    )

internal fun loadDetalles(invoiceId: String): List<VEDetalleData> =
    VEFacturaDetalleReadTable
        .selectAll()
        .where { VEFacturaDetalleReadTable.idFactura eq invoiceId }
        .map { row ->
            VEDetalleData(
                descripcion = row[VEFacturaDetalleReadTable.itemDescripcion],
                codigo = row[VEFacturaDetalleReadTable.itemCodigo],
                referencia = row[VEFacturaDetalleReadTable.itemReferencia],
                unidadEmpaque = row[VEFacturaDetalleReadTable.itemUnidadEmpaque],
                cantidad = row[VEFacturaDetalleReadTable.itemCantidad],
                precioSinIva = row[VEFacturaDetalleReadTable.itemPrecioSinIva],
                descuento = row[VEFacturaDetalleReadTable.itemDescuento],
                montoDescuento = row[VEFacturaDetalleReadTable.itemMontoDescuento],
                piva = row[VEFacturaDetalleReadTable.itemPiva],
                totalSinIva = row[VEFacturaDetalleReadTable.itemTotalSinIva],
                totalConIva = row[VEFacturaDetalleReadTable.itemTotalConIva],
                importeIsc = row[VEFacturaDetalleReadTable.importeIsc],
                porcentajeIsc = row[VEFacturaDetalleReadTable.porcentajeIsc],
                importeOti = row[VEFacturaDetalleReadTable.importeOti],
                importeAcarreo = row[VEFacturaDetalleReadTable.importeAcarreo],
                importeSeguro = row[VEFacturaDetalleReadTable.importeSeguro],
            )
        }

/**
 * Carga formas de pago desde `caja_nueva_detalle` + `caja_forma_pago`.
 *
 * `esDivisa` se deduce de `tipo_moneda`: las formas en moneda secundaria
 * (divisa) tienen `tipo_moneda='D'` (u otro distinto de la base).
 */
internal fun loadFormasPago(
    cajaId: String,
    invoiceId: String,
): List<VEFormaPagoData> {
    // 1. Buscar la caja_nueva asociada a la factura.
    val cajaNueva =
        FECajaNuevaReadTable
            .selectAll()
            .where { FECajaNuevaReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?: run {
                loaderLog.warn("[VE-FE] No se encontró caja_nueva para invoiceId={}", invoiceId)
                return emptyList()
            }
    val cajaNuevaId = cajaNueva[FECajaNuevaReadTable.cajaId]

    // 2. JOIN caja_nueva_detalle + caja_forma_pago.
    return FECajaNuevaDetalleReadTable
        .join(
            CajaFormaPagoTable,
            JoinType.LEFT,
            onColumn = FECajaNuevaDetalleReadTable.idFormaPago,
            otherColumn = CajaFormaPagoTable.idFormaPago,
        ).selectAll()
        .where { FECajaNuevaDetalleReadTable.cajaId eq cajaNuevaId }
        .mapNotNull { row ->
            val monto = row[FECajaNuevaDetalleReadTable.monto] ?: return@mapNotNull null
            if (monto <= BigDecimal.ZERO) return@mapNotNull null
            val siglas = row.getOrNull(CajaFormaPagoTable.siglas)
            val formaPagoFact = row.getOrNull(CajaFormaPagoTable.formaPagoFact)
            val descripcion = row.getOrNull(CajaFormaPagoTable.descripcion) ?: "Pago"
            val tipoMoneda = row.getOrNull(CajaFormaPagoTable.tipoMoneda)
            // Heurística: en VE, formas con tipo_moneda='D' (Divisa) son USD/EUR.
            val esDivisa = tipoMoneda?.equals("D", ignoreCase = true) == true
            VEFormaPagoData(
                idFormaPago = row.getOrNull(FECajaNuevaDetalleReadTable.idFormaPago) ?: 0,
                descripcion = descripcion,
                siglas = siglas,
                formaPagoFact = formaPagoFact,
                monto = monto,
                esDivisa = esDivisa,
                montoRecibido = null,
                tipoMoneda = tipoMoneda,
            )
        }
}

internal fun loadCaja(
    cajaId: String,
    idSucursal: Int,
    config: VEConfigData,
): VECajaData {
    val row =
        VECajaReadTable
            .selectAll()
            .where { VECajaReadTable.id eq cajaId }
            .limit(1)
            .firstOrNull()
    val codigoSucursal =
        row?.getOrNull(VECajaReadTable.codigoSucursalEmisor)?.takeIf { it.isNotBlank() }
            ?: config.codigoSucursalEmisorFallback
    val puntoFact =
        row?.getOrNull(VECajaReadTable.puntoFacturacionFiscal)?.takeIf { it.isNotBlank() }
            ?: config.puntoFacturacionFiscalFallback
    val serieCaja = row?.get(VECajaReadTable.serieCaja)
    val serieSucursal =
        row?.get(VECajaReadTable.serieSucursal)
            ?: VESucursalReadTable
                .selectAll()
                .where { VESucursalReadTable.id eq idSucursal }
                .limit(1)
                .firstOrNull()
                ?.get(VESucursalReadTable.serie)
    return VECajaData(
        idCaja = cajaId,
        serieCaja = serieCaja ?: "",
        serieSucursal = serieSucursal,
        codigoSucursalEmisor = codigoSucursal,
        puntoFacturacionFiscal = puntoFact,
    )
}
