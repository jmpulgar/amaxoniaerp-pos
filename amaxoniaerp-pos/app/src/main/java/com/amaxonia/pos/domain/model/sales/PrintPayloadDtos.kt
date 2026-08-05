package com.amaxonia.pos.domain.model.sales

import kotlinx.serialization.Serializable

@Serializable
data class FacturaPrintPayloadDto(
    val facturaId: String,
    val numeroFactura: String,
    val fecha: String,
    val empresa: EmpresaPrintDto,
    val cliente: ClientePrintDto? = null,
    val vendedor: String? = null,
    val productos: List<ProductoPrintDto>,
    val subtotal: String,
    val montoExento: String? = null,
    val totalImpuesto: String,
    val total: String,
    val pagos: List<PagoPrintDto>,
    val cambio: String? = null,
    val qrUrl: String? = null,
    val cufe: String? = null,
    val fechaRecepcionDgi: String? = null,
    val proveedorAutorizado: String? = null,
    val numeroDocumentoFiscal: String? = null,
    val puntoFacturacionFiscal: String? = null,
    val codigoSucursal: String? = null,
    val protocoloAutorizacion: String? = null,
    /**
     * FASE 2.3b — Venezuela digital.
     *
     * Número de control HKA persistido en `factura.numero_control_thka` tras
     * emisión exitosa por The Factory HKA. Solo aplica a Venezuela; en Panamá
     * se mantiene `null`.
     */
    val numeroControlThka: String? = null,
    /**
     * FASE 2.3b — Venezuela: adicional al IVA (IGTF) cuando el pago es en
     * divisa extranjera. Se imprime únicamente si el tenant lo aplica y el
     * monto es > 0.
     */
    val igtfMonto: String? = null,
    /** Base imponible sobre la que se calcula el IGTF (monto en divisa). */
    val igtfBaseImponible: String? = null,
    /** Tasa del IGTF (porcentaje), p.ej. "3.0". */
    val igtfTasa: String? = null,
    /**
     * Venezuela multimoneda: tasa de cambio referencia (USD→Bs) usada para
     * mostrar el total equivalente en bolívares. Solo se imprime si está
     * presente y el pago está en divisa.
     */
    val tasaCambioBs: String? = null,
    /** Abreviatura de la moneda base (p.ej. "Bs"). Defaults implícitos en null. */
    val abrMonedaBase: String? = null,
    /** Abreviatura de la moneda secundaria (p.ej. "USD"). */
    val abrMonedaSecundaria: String? = null,
    /** Total convertido a la moneda secundaria (divisa), si aplica. */
    val totalDivisa: String? = null,
)

@Serializable
data class EmpresaPrintDto(
    val nombre: String,
    val ruc: String? = null,
    val direccion: String? = null,
    val telefono: String? = null,
    val tienda: String? = null,
    val caja: String? = null,
)

@Serializable
data class ClientePrintDto(
    val nombre: String,
    val documento: String? = null,
    val sucursal: String? = null,
    val sucursalDireccion: String? = null,
    val digitoVerificador: String? = null,
    val tipoReceptor: String? = null,
)

@Serializable
data class ProductoPrintDto(
    val nombre: String,
    val cantidad: String,
    val unidad: String? = null,
    val precioUnitario: String,
    val descuento: String,
    val impuesto: String,
    val total: String,
    val codigo: String? = null,
    val tasaImpuesto: String? = null,
)

@Serializable
data class PagoPrintDto(
    val metodo: String,
    val monto: String,
)
