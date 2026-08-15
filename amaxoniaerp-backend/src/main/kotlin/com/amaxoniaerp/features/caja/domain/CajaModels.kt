package com.amaxoniaerp.features.caja.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Caja(
    val idCaja: String,
    val codCaja: String?,
    val caja: String? = null,
    val descripcion: String?,
    val estatus: Int,
    val idSucursal: Int?,
    val codAlmacen: Int? = null,
    @SerialName("default_warehouse_id")
    val defaultWarehouseId: Int? = null,
    @SerialName("default_vendedor_id")
    val defaultSellerId: Int? = null,
    @SerialName("default_vendedor_name")
    val defaultSellerName: String? = null,
    @SerialName("available_sellers")
    val availableSellers: List<SellerSummary> = emptyList(),
    @SerialName("serie_sucursal")
    val serieSucursal: String? = null,
    @SerialName("default_tax_rate")
    val defaultTaxRate: Double? = null,
    @SerialName("default_forma_pago_id")
    val defaultFormaPagoId: Int? = null,
    val currency: CurrencyConfig? = null,
    val serieCaja: String,
    val sucursalNombre: String? = null,
    val sucursalCodigo: String? = null,
    val codigoSucursalEmisor: String? = null,
)

@Serializable
data class CurrencyConfig(
    @SerialName("multi_moneda")
    val multiMoneda: String,
    val tasa: Double,
    @SerialName("id_tasa")
    val idTasa: Int,
    @SerialName("moneda_base")
    val monedaBase: Int,
    @SerialName("abr_moneda_base")
    val abrMonedaBase: String,
    @SerialName("moneda_secundaria")
    val monedaSecundaria: Int,
    @SerialName("abr_moneda_secundaria")
    val abrMonedaSecundaria: String,
)

@Serializable
data class SellerSummary(
    val id: Int,
    val nombre: String,
)

@Serializable
data class CajaSecuencia(
    val idCajaSecuencia: String,
    val idCaja: String,
    val fechaApertura: String,
    val montoApertura: Double,
    val fechaCierre: String? = null,
    val montoCierre: Double? = null,
    val estatus: Int,
    val usuarioApertura: String,
    val usuarioCierre: String? = null,
    val serieSucursal: String,
    val idSucursal: Int?,
)

@Serializable
data class AperturaRequest(
    val idCaja: String,
    val montoApertura: Double,
    val idVendedor: Int? = null,
    val secuencia: String? = null,
    val serieSucursal: String,
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
    val isOpen: Boolean,
    val cajaSecuencia: CajaSecuencia? = null,
)

@Serializable
data class CajaSecuenciaResumenResponse(
    val isOpen: Boolean,
    val summary: CajaCierreSummary? = null,
    val error: String? = null,
)

@Serializable
data class CajaCierreSummary(
    val idCajaSecuencia: String,
    val idCaja: String,
    val cajaName: String,
    val vendedorName: String? = null,
    val openedAt: String,
    val openAmount: Double,
    val totalSales: Double,
    val transactionCount: Int,
    val totalCash: Double,
    val totalCard: Double,
    val totalOther: Double,
    val totalIncome: Double,
    val totalExpense: Double,
    val totalCancelled: Double,
    val expectedClose: Double,
    val formasPago: List<CajaFormaPagoTotal> = emptyList(),
)

@Serializable
data class CajaFormaPagoTotal(
    val idFormaPago: Int? = null,
    val siglas: String? = null,
    val descripcion: String? = null,
    val total: Double,
)

@Serializable
data class CajaSecuenciaGetResponse(
    val success: Boolean,
    val data: CajaSecuenciaData? = null,
    val error: String? = null,
)

@Serializable
data class CajaSecuenciaData(
    val id: String,
    val id_caja: String,
    val id_vendedor: Int? = null,
    val secuencia: String? = null,
    val fecha_apertura: String? = null,
    val fecha_cierre: String? = null,
    val fecha_creacion: String? = null,
    val usuario: String? = null,
    val observacion_apertura: String? = null,
    val observacion_cierre: String? = null,
    val monto_efectivo_apertura: Double = 0.0,
    val monto_efectivo_ventas: Double = 0.0,
    val monto_efectivo_entrada: Double = 0.0,
    val monto_efectivo_salida: Double = 0.0,
    val monto_efectivo_total: Double = 0.0,
    val monto_efectivo_cierre: Double = 0.0,
    val monto_efectivo_diferencia: Double = 0.0,
    val monto_otros_total: Double = 0.0,
    val monto_otros_cierre: Double = 0.0,
    val monto_otros_diferencia: Double = 0.0,
    val monto_total: Double = 0.0,
    val monto_cierre: Double = 0.0,
    val monto_diferencia: Double = 0.0,
    val total_ventas: Double = 0.0,
    val cantidad_transacciones: Int = 0,
    val numero_cierre_fiscal: String? = null,
    val serie_sucursal: String? = null,
    val serial_fiscal: String? = null,
    val contabilizado: Int = 0,
    val ffecha_apertura: String = "",
    val ffecha_cierre: String = "",
    val caja_codigo: String? = null,
    val caja: String? = null,
    val fondo_apertura: Double = 0.0,
    val nombre_modelo: String? = null,
    val vendedor: String? = null,
    val detalle_apertura: List<CajaDetalleAperturaItem> = emptyList(),
    val forma_pago: List<CajaFormaPagoItem> = emptyList(),
    val forma_pago_devolucion: List<CajaFormaPagoDevolucionItem> = emptyList(),
    val total_anulado: Double = 0.0,
    val verificar_facturas_temporales: Int = 0,
    val inventario: List<CajaInventarioItem> = emptyList(),
)

@Serializable
data class CajaInventarioItem(
    val codigo: String,
    val descripcion: String,
    val existencia_inicial: Double,
    val cantidad_vendida: Double,
    val existencia_disponible: Double,
)

@Serializable
data class CajaDetalleAperturaItem(
    val id: String,
    val id_secuencia: String,
    val id_moneda_denominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
    val denominacion: String? = null,
)

@Serializable
data class CajaFormaPagoItem(
    val id: Int,
    val forma_pago: String? = null,
    val siglas: String? = null,
    val grupo: Int? = null,
    val imagen: String? = null,
    val id_caja_tp_concepto: Int? = null,
    val tipo_moneda: String? = null,
    val estatus: Int = 0,
    val grupo_nombre: String? = null,
    val grupo_imagen: String? = null,
    val grupo_orden: Int? = null,
    val grupo_activo: Int? = null,
    val monto: Double = 0.0,
)

@Serializable
data class CajaFormaPagoDevolucionItem(
    val id_forma_pago: Int,
    val siglas: String? = null,
    val descripcion: String? = null,
    val monto: Double = 0.0,
)

@Serializable
data class CajaCierreSaveRequest(
    val id: String,
    val monto_efectivo_ventas: Double,
    val monto_efectivo_entrada: Double,
    val monto_efectivo_salida: Double,
    val monto_efectivo_total: Double,
    val monto_efectivo_cierre: Double,
    val monto_efectivo_diferencia: Double,
    val monto_otros_total: Double,
    val monto_otros_cierre: Double,
    val monto_otros_diferencia: Double,
    val monto_total: Double,
    val monto_cierre: Double,
    val monto_diferencia: Double,
    val detalle: List<CajaCierreDetalleRequest> = emptyList(),
    val detalle_formapago: List<CajaCierreFormaPagoRequest> = emptyList(),
    val observacion_cierre: String? = null,
    val numero_cierre_fiscal: String? = null,
)

@Serializable
data class CajaCierreDetalleRequest(
    val id_moneda_denominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
)

@Serializable
data class CajaCierreFormaPagoRequest(
    val id_forma_pago: Int,
    val monto: Double,
    val monto_cierre: Double,
    val monto_diferencia: Double,
)

@Serializable
data class CajaCierreSaveResponse(
    val success: Boolean,
    val message: String,
    val id: String? = null,
    val error: String? = null,
)
