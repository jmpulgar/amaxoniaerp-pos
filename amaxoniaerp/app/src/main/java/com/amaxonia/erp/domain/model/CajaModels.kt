package com.amaxonia.erp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Caja(
    val idCaja: String,
    val codCaja: String? = null,
    val caja: String? = null,
    val descripcion: String? = null,
    val estatus: Int = 0,
    val idSucursal: Int? = null,
    val codAlmacen: Int? = null,
    @SerialName("default_warehouse_id") val defaultWarehouseId: Int? = null,
    @SerialName("default_vendedor_id") val defaultSellerId: Int? = null,
    @SerialName("default_vendedor_name") val defaultSellerName: String? = null,
    @SerialName("available_sellers") val availableSellers: List<SellerSummary> = emptyList(),
    @SerialName("serie_sucursal") val serieSucursal: String? = null,
    @SerialName("default_tax_rate") val defaultTaxRate: Double? = null,
    @SerialName("default_forma_pago_id") val defaultFormaPagoId: Int? = null,
    val serieCaja: String = "",
    val sucursalNombre: String? = null,
    val sucursalCodigo: String? = null,
    val codigoSucursalEmisor: String? = null,
    @SerialName("almacen_nombre") val almacenNombre: String? = null,
) {
    val displayName: String
        get() = caja?.takeIf { it.isNotBlank() } ?: descripcion?.takeIf { it.isNotBlank() } ?: "Caja $idCaja"
}

@Serializable
data class SellerSummary(
    val id: Int,
    val nombre: String,
)

@Serializable
data class AperturaRequest(
    val idCaja: String,
    val montoApertura: Double,
    val idVendedor: Int? = null,
    val secuencia: String? = null,
    val serieSucursal: String = "",
    val idSucursal: Int? = null,
    val facturaInicial: Int = 0,
    val notacreditoInicial: Int = 0,
    val devolucionInicial: Int = 0,
    val zInicial: Int = 0,
)

@Serializable
data class CajaSecuenciaCodigoResponse(
    val codigo: String,
)

@Serializable
data class CajaStatusResponse(
    val isOpen: Boolean = false,
    val cajaSecuencia: CajaSecuencia? = null,
)

@Serializable
data class CajaSecuencia(
    val idCajaSecuencia: String = "",
    val idCaja: String = "",
    val fechaApertura: String = "",
    val montoApertura: Double = 0.0,
    val fechaCierre: String? = null,
    val montoCierre: Double? = null,
    val estatus: Int = 1,
    val usuarioApertura: String = "",
    val usuarioCierre: String? = null,
    val serieSucursal: String = "",
    val idSucursal: Int? = null,
)

@Serializable
data class CajaSecuenciaGetResponse(
    val success: Boolean,
    val data: CajaSecuenciaDataDto? = null,
    val error: String? = null,
)

@Serializable
data class CajaSecuenciaDataDto(
    val id: String,
    @SerialName("id_caja") val id_caja: String,
    @SerialName("ffecha_apertura") val ffecha_apertura: String = "",
    @SerialName("monto_efectivo_apertura") val monto_efectivo_apertura: Double = 0.0,
    @SerialName("monto_efectivo_ventas") val monto_efectivo_ventas: Double = 0.0,
    @SerialName("monto_efectivo_entrada") val monto_efectivo_entrada: Double = 0.0,
    @SerialName("monto_efectivo_salida") val monto_efectivo_salida: Double = 0.0,
    @SerialName("monto_efectivo_total") val monto_efectivo_total: Double = 0.0,
    @SerialName("monto_efectivo_cierre") val monto_efectivo_cierre: Double = 0.0,
    @SerialName("monto_efectivo_diferencia") val monto_efectivo_diferencia: Double = 0.0,
    @SerialName("monto_otros_total") val monto_otros_total: Double = 0.0,
    @SerialName("monto_otros_cierre") val monto_otros_cierre: Double = 0.0,
    @SerialName("monto_otros_diferencia") val monto_otros_diferencia: Double = 0.0,
    @SerialName("monto_total") val monto_total: Double = 0.0,
    @SerialName("monto_cierre") val monto_cierre: Double = 0.0,
    @SerialName("monto_diferencia") val monto_diferencia: Double = 0.0,
    @SerialName("total_ventas") val total_ventas: Double = 0.0,
    @SerialName("cantidad_transacciones") val cantidad_transacciones: Int = 0,
    val caja: String? = null,
    val vendedor: String? = null,
    @SerialName("forma_pago") val forma_pago: List<CajaFormaPagoLineaDto> = emptyList(),
    @SerialName("verificar_facturas_temporales") val verificar_facturas_temporales: Int = 0,
    val inventario: List<CajaInventarioLineaDto> = emptyList(),
)

@Serializable
data class CajaInventarioLineaDto(
    val codigo: String,
    val descripcion: String,
    @SerialName("existencia_inicial") val existencia_inicial: Double,
    @SerialName("cantidad_vendida") val cantidad_vendida: Double,
    @SerialName("existencia_disponible") val existencia_disponible: Double,
)

@Serializable
data class CajaFormaPagoLineaDto(
    val id: Int,
    @SerialName("forma_pago") val forma_pago: String? = null,
    val siglas: String? = null,
    val monto: Double = 0.0,
)

@Serializable
data class CierreCajaRequest(
    val id: String,
    @SerialName("monto_efectivo_ventas") val monto_efectivo_ventas: Double,
    @SerialName("monto_efectivo_entrada") val monto_efectivo_entrada: Double = 0.0,
    @SerialName("monto_efectivo_salida") val monto_efectivo_salida: Double = 0.0,
    @SerialName("monto_efectivo_total") val monto_efectivo_total: Double,
    @SerialName("monto_efectivo_cierre") val monto_efectivo_cierre: Double,
    @SerialName("monto_efectivo_diferencia") val monto_efectivo_diferencia: Double,
    @SerialName("monto_otros_total") val monto_otros_total: Double = 0.0,
    @SerialName("monto_otros_cierre") val monto_otros_cierre: Double = 0.0,
    @SerialName("monto_otros_diferencia") val monto_otros_diferencia: Double = 0.0,
    @SerialName("monto_total") val monto_total: Double,
    @SerialName("monto_cierre") val monto_cierre: Double,
    @SerialName("monto_diferencia") val monto_diferencia: Double,
    val detalle: List<CierreCajaDetalleItem> = emptyList(),
    @SerialName("detalle_formapago") val detalle_formapago: List<CierreCajaFormaPagoItem> = emptyList(),
    @SerialName("observacion_cierre") val observacion_cierre: String? = null,
    @SerialName("numero_cierre_fiscal") val numero_cierre_fiscal: String? = null,
)

@Serializable
data class CierreCajaDetalleItem(
    @SerialName("id_moneda_denominacion") val id_moneda_denominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
)

@Serializable
data class CierreCajaFormaPagoItem(
    @SerialName("id_forma_pago") val id_forma_pago: Int,
    val monto: Double,
    @SerialName("monto_cierre") val monto_cierre: Double,
    @SerialName("monto_diferencia") val monto_diferencia: Double,
)

@Serializable
data class CierreCajaResponse(
    val success: Boolean = true,
    val message: String? = null,
    val id: String? = null,
    val error: String? = null,
)

data class CierreCajaPaymentLine(
    val idFormaPago: Int,
    val label: String,
    val siglas: String,
    val amount: Double,
)

data class CierreCajaSummary(
    val idCajaSecuencia: String = "",
    val idCaja: String = "",
    val cajaName: String = "",
    val vendedorName: String = "",
    val openedAt: String = "",
    val openAmount: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalCash: Double = 0.0,
    val totalCard: Double = 0.0,
    val totalOther: Double = 0.0,
    val transactionCount: Int = 0,
    val expectedClose: Double = 0.0,
    val montoEfectivoVentas: Double = 0.0,
    val montoEfectivoEntrada: Double = 0.0,
    val montoEfectivoSalida: Double = 0.0,
    val montoEfectivoTotal: Double = 0.0,
    val montoEfectivoCierre: Double = 0.0,
    val montoEfectivoDiferencia: Double = 0.0,
    val montoOtrosTotal: Double = 0.0,
    val montoOtrosCierre: Double = 0.0,
    val montoOtrosDiferencia: Double = 0.0,
    val montoTotal: Double = 0.0,
    val montoCierre: Double = 0.0,
    val montoDiferencia: Double = 0.0,
    val paymentLines: List<CierreCajaPaymentLine> = emptyList(),
)

enum class CajaSessionStatus {
    VERIFICANDO,
    SIN_CAJA,
    PENDIENTE_APERTURA,
    ABIERTA,
}

@Serializable
data class SaveCajaRequest(
    val id: String? = null,
    val codigo: String? = null,
    val caja: String,
    val descripcion: String? = null,
    val idSucursal: Int? = null,
    val serieCaja: String = "1",
    val fondoApertura: Double? = null,
    val impresoraModelo: String? = null,
    val codigoSucursalEmisor: String? = null,
    val puntoFacturacionFiscal: String? = null,
    val codAlmacen: Int? = null,
    val activo: Int = 1,
)

@Serializable
data class SaveCajaResponse(
    val success: Boolean = true,
    val data: Caja,
    val message: String? = null,
)
