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
    @SerialName("id_caja") val idCaja: String,
    @SerialName("id_vendedor") val idVendedor: Int? = null,
    val secuencia: String? = null,
    @SerialName("fecha_apertura") val fechaApertura: String? = null,
    @SerialName("fecha_cierre") val fechaCierre: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null,
    val usuario: String? = null,
    @SerialName("observacion_apertura") val observacionApertura: String? = null,
    @SerialName("observacion_cierre") val observacionCierre: String? = null,
    @SerialName("monto_efectivo_apertura") val montoEfectivoApertura: Double = 0.0,
    @SerialName("monto_efectivo_ventas") val montoEfectivoVentas: Double = 0.0,
    @SerialName("monto_efectivo_entrada") val montoEfectivoEntrada: Double = 0.0,
    @SerialName("monto_efectivo_salida") val montoEfectivoSalida: Double = 0.0,
    @SerialName("monto_efectivo_total") val montoEfectivoTotal: Double = 0.0,
    @SerialName("monto_efectivo_cierre") val montoEfectivoCierre: Double = 0.0,
    @SerialName("monto_efectivo_diferencia") val montoEfectivoDiferencia: Double = 0.0,
    @SerialName("monto_otros_total") val montoOtrosTotal: Double = 0.0,
    @SerialName("monto_otros_cierre") val montoOtrosCierre: Double = 0.0,
    @SerialName("monto_otros_diferencia") val montoOtrosDiferencia: Double = 0.0,
    @SerialName("monto_total") val montoTotal: Double = 0.0,
    @SerialName("monto_cierre") val montoCierre: Double = 0.0,
    @SerialName("monto_diferencia") val montoDiferencia: Double = 0.0,
    @SerialName("total_ventas") val totalVentas: Double = 0.0,
    @SerialName("cantidad_transacciones") val cantidadTransacciones: Int = 0,
    @SerialName("numero_cierre_fiscal") val numeroCierreFiscal: String? = null,
    @SerialName("serie_sucursal") val serieSucursal: String? = null,
    @SerialName("serial_fiscal") val serialFiscal: String? = null,
    val contabilizado: Int = 0,
    @SerialName("ffecha_apertura") val ffechaApertura: String = "",
    @SerialName("ffecha_cierre") val ffechaCierre: String = "",
    @SerialName("caja_codigo") val cajaCodigo: String? = null,
    val caja: String? = null,
    @SerialName("fondo_apertura") val fondoApertura: Double = 0.0,
    @SerialName("nombre_modelo") val nombreModelo: String? = null,
    val vendedor: String? = null,
    @SerialName("detalle_apertura") val detalleApertura: List<CajaDetalleAperturaItem> = emptyList(),
    @SerialName("forma_pago") val formaPago: List<CajaFormaPagoItem> = emptyList(),
    @SerialName("forma_pago_devolucion") val formaPagoDevolucion: List<CajaFormaPagoDevolucionItem> = emptyList(),
    @SerialName("total_anulado") val totalAnulado: Double = 0.0,
    @SerialName("verificar_facturas_temporales") val verificarFacturasTemporales: Int = 0,
    val inventario: List<CajaInventarioItem> = emptyList(),
)

@Serializable
data class CajaInventarioItem(
    val codigo: String,
    val descripcion: String,
    @SerialName("existencia_inicial") val existenciaInicial: Double,
    @SerialName("cantidad_vendida") val cantidadVendida: Double,
    @SerialName("existencia_disponible") val existenciaDisponible: Double,
)

@Serializable
data class CajaDetalleAperturaItem(
    val id: String,
    @SerialName("id_secuencia") val idSecuencia: String,
    @SerialName("id_moneda_denominacion") val idMonedaDenominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
    val denominacion: String? = null,
)

@Serializable
data class CajaFormaPagoItem(
    val id: Int,
    @SerialName("forma_pago") val formaPago: String? = null,
    val siglas: String? = null,
    val grupo: Int? = null,
    val imagen: String? = null,
    @SerialName("id_caja_tp_concepto") val idCajaTpConcepto: Int? = null,
    @SerialName("tipo_moneda") val tipoMoneda: String? = null,
    val estatus: Int = 0,
    @SerialName("grupo_nombre") val grupoNombre: String? = null,
    @SerialName("grupo_imagen") val grupoImagen: String? = null,
    @SerialName("grupo_orden") val grupoOrden: Int? = null,
    @SerialName("grupo_activo") val grupoActivo: Int? = null,
    val monto: Double = 0.0,
)

@Serializable
data class CajaFormaPagoDevolucionItem(
    @SerialName("id_forma_pago") val idFormaPago: Int,
    val siglas: String? = null,
    val descripcion: String? = null,
    val monto: Double = 0.0,
)

@Serializable
data class CajaCierreSaveRequest(
    val id: String,
    @SerialName("monto_efectivo_ventas") val montoEfectivoVentas: Double,
    @SerialName("monto_efectivo_entrada") val montoEfectivoEntrada: Double,
    @SerialName("monto_efectivo_salida") val montoEfectivoSalida: Double,
    @SerialName("monto_efectivo_total") val montoEfectivoTotal: Double,
    @SerialName("monto_efectivo_cierre") val montoEfectivoCierre: Double,
    @SerialName("monto_efectivo_diferencia") val montoEfectivoDiferencia: Double,
    @SerialName("monto_otros_total") val montoOtrosTotal: Double,
    @SerialName("monto_otros_cierre") val montoOtrosCierre: Double,
    @SerialName("monto_otros_diferencia") val montoOtrosDiferencia: Double,
    @SerialName("monto_total") val montoTotal: Double,
    @SerialName("monto_cierre") val montoCierre: Double,
    @SerialName("monto_diferencia") val montoDiferencia: Double,
    val detalle: List<CajaCierreDetalleRequest> = emptyList(),
    @SerialName("detalle_formapago") val detalleFormaPago: List<CajaCierreFormaPagoRequest> = emptyList(),
    @SerialName("observacion_cierre") val observacionCierre: String? = null,
    @SerialName("numero_cierre_fiscal") val numeroCierreFiscal: String? = null,
)

@Serializable
data class CajaCierreDetalleRequest(
    @SerialName("id_moneda_denominacion") val idMonedaDenominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
)

@Serializable
data class CajaCierreFormaPagoRequest(
    @SerialName("id_forma_pago") val idFormaPago: Int,
    val monto: Double,
    @SerialName("monto_cierre") val montoCierre: Double,
    @SerialName("monto_diferencia") val montoDiferencia: Double,
)

@Serializable
data class CajaCierreSaveResponse(
    val success: Boolean,
    val message: String,
    val id: String? = null,
    val error: String? = null,
)
