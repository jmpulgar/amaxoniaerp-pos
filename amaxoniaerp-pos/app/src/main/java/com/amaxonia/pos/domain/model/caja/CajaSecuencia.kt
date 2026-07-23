package com.amaxonia.pos.domain.model.caja

import kotlinx.serialization.Serializable

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
data class CierreCajaRequest(
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
    val detalle: List<CierreCajaDetalleItem> = emptyList(),
    val detalle_formapago: List<CierreCajaFormaPagoItem> = emptyList(),
    val observacion_cierre: String? = null,
    val numero_cierre_fiscal: String? = null,
)

@Serializable
data class CierreCajaDetalleItem(
    val id_moneda_denominacion: Int? = null,
    val cantidad: Int = 0,
    val valor: Double = 0.0,
    val monto: Double = 0.0,
)

@Serializable
data class CierreCajaFormaPagoItem(
    val id_forma_pago: Int,
    val monto: Double,
    val monto_cierre: Double,
    val monto_diferencia: Double,
)

@Serializable
data class CierreCajaResponse(
    val success: Boolean,
    val message: String,
    val id: String? = null,
    val error: String? = null,
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
    val id_caja: String,
    val ffecha_apertura: String = "",
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
    val caja: String? = null,
    val vendedor: String? = null,
    val forma_pago: List<CajaFormaPagoLineaDto> = emptyList(),
    val verificar_facturas_temporales: Int = 0,
    val inventario: List<CajaInventarioLineaDto> = emptyList(),
)

@Serializable
data class CajaInventarioLineaDto(
    val codigo: String,
    val descripcion: String,
    val existencia_inicial: Double,
    val cantidad_vendida: Double,
    val existencia_disponible: Double,
)

@Serializable
data class CajaFormaPagoLineaDto(
    val id: Int,
    val forma_pago: String? = null,
    val siglas: String? = null,
    val monto: Double = 0.0,
)

/** Summary shown in the close-register UI. */
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
    val detalle: List<CierreCajaDetalleItem> = emptyList(),
    val detalleFormaPago: List<CierreCajaFormaPagoItem> = emptyList(),
    val paymentLines: List<CierreCajaPaymentLine> = emptyList(),
    val inventoryLines: List<CashCloseInventoryLine> = emptyList(),
)

data class CierreCajaPaymentLine(
    val idFormaPago: Int,
    val label: String,
    val siglas: String,
    val amount: Double,
)
